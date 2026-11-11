package dev.luisvergara.manabi.content.publication;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import lombok.RequiredArgsConstructor;
import dev.luisvergara.manabi.admin.security.AdminAudit;
import dev.luisvergara.manabi.content.publication.contract.CanonicalJson;

/** Official storage/export provenance; revocation is independent from later editorial edits. No activation. */
@Service @RequiredArgsConstructor
@Transactional(readOnly=true)
public class PublicationArtifactLedger {
 public record Job(String jobId,String releaseId,PublicationSnapshotService.Profile profile,String snapshot,String manifestSha) {}
 public record Stored(String releaseId,String jobId,String manifestSha,String storedAt,boolean revoked,boolean exported) {}
 public record Export(String exportId,String releaseId,PublicationStore.ExportFile file) {}
 public record ExportPlan(Export export,String inventory,List<String> origins) {public ExportPlan {origins=List.copyOf(origins);}}
 private final JdbcTemplate jdbc;
 private final AdminAudit audit;
 private final PublicationSnapshotService snapshots;
 private final CanonicalJson json=new CanonicalJson();
 private void id(String release){if(release==null||!release.matches("sha256:[a-f0-9]{64}"))throw new IllegalArgumentException("Invalid release ID");}
 public Job readyJob(String jobId){
  if(jobId==null||!jobId.matches("[a-f0-9-]{36}"))throw new IllegalArgumentException("Invalid job ID");
  var rows=jdbc.queryForList("select j.job_id,j.release_id,j.profile,j.snapshot_fingerprint,c.manifest_sha256 from publication_job j join publication_candidate c on c.release_id=j.release_id where j.job_id=? and j.status='READY' and j.mode='DISTRIBUTABLE'",jobId);
  if(rows.size()!=1)throw new IllegalStateException("No original READY job");
  var r=rows.getFirst();String release=(String)r.get("release_id");assertNotRevoked(release);
  return new Job(jobId,release,PublicationSnapshotService.Profile.valueOf((String)r.get("profile")),(String)r.get("snapshot_fingerprint"),(String)r.get("manifest_sha256"));
 }
 public void assertNotRevoked(String release){
  id(release);if(jdbc.queryForObject("select count(*) from publication_revocation where release_id=?",Integer.class,release)>0)throw new IllegalStateException("Distribution explicitly revoked");
 }
 public List<Stored> history(){
  return jdbc.query("select s.release_id,s.job_id,s.manifest_sha256,s.stored_at,exists(select 1 from publication_revocation r where r.release_id=s.release_id) as revoked,exists(select 1 from publication_export e where e.release_id=s.release_id) as exported from publication_storage s order by s.release_id",
   (r,n)->new Stored(r.getString(1),r.getString(2),r.getString(3),r.getString(4),r.getBoolean(5),r.getBoolean(6)));
 }
 public Stored requireStored(String release){
  assertNotRevoked(release);
  var item=history().stream().filter(s->s.releaseId().equals(release)).findFirst().orElseThrow(()->new IllegalStateException("Release not STORED"));
  var job=readyJob(item.jobId());
  if(!job.releaseId().equals(release)||!job.manifestSha().equals(item.manifestSha()))throw new IllegalStateException("Stored provenance mismatch");
  return item;
 }
 private void lock(String release){
  id(release);
  if(jdbc.queryForList("select release_id from publication_candidate where release_id=? for update",release).size()!=1)throw new IllegalStateException("Unknown candidate");
 }
 private void current(Job job){
  var now=snapshots.capture(job.profile());
  if(!now.fingerprint().equals(job.snapshot())||!now.editorialIssues().isEmpty())throw new IllegalStateException("Editorial snapshot changed or unapproved");
 }
 @Transactional(isolation=Isolation.REPEATABLE_READ,propagation=Propagation.REQUIRES_NEW)
 public Stored stored(Job original,String manifestSha){
  lock(original.releaseId());var job=readyJob(original.jobId());current(job);
  if(!job.equals(original)||!job.manifestSha().equals(manifestSha))throw new IllegalStateException("Candidate changed");
  jdbc.update("insert into publication_storage(release_id,job_id,manifest_sha256,stored_at) values (?,?,?,?) on conflict (release_id) do nothing",
   job.releaseId(),job.jobId(),manifestSha,Timestamp.from(Instant.now()));
  audit.publication("STORE_PUBLICATION",job.releaseId());
  return requireStored(job.releaseId());
 }
 @Transactional(propagation=Propagation.REQUIRES_NEW)
 public Export exported(String release,PublicationStore.ExportFile file,String inventory,List<String> origins){
  var ordered=origins.stream().distinct().sorted().toList();
  if(!ordered.contains(release))throw new IllegalStateException("Missing target provenance");
  for(String id:ordered){lock(id);requireStored(id);}
  String exportId=UUID.randomUUID().toString();
  jdbc.update("insert into publication_export(export_id,release_id,zip_sha256,inventory_sha256,byte_length,storage_key,source_releases,exported_at) values (?,?,?,?,?,?,?,?)",
   exportId,release,file.sha256(),inventory,file.byteLength(),file.key(),new String(json.bytes(ordered),java.nio.charset.StandardCharsets.UTF_8),Timestamp.from(Instant.now()));
  audit.publication("EXPORT_PUBLICATION",exportId);
  return new Export(exportId,release,file);
 }
 @SuppressWarnings("unchecked")
 public Export export(String exportId){return plan(exportId).export();}
 public ExportPlan plan(String exportId){
  if(exportId==null||!exportId.matches("[a-f0-9-]{36}"))throw new IllegalArgumentException("Invalid export ID");
  var rows=jdbc.queryForList("select * from publication_export where export_id=?",exportId);
  if(rows.size()!=1)throw new IllegalStateException("Unknown export");var r=rows.getFirst();
  var origins=((List<Object>)json.parse(((String)r.get("source_releases")).getBytes(java.nio.charset.StandardCharsets.UTF_8))).stream().map(String.class::cast).toList();
  for(String source:origins)requireStored(source);
  String release=(String)r.get("release_id");requireStored(release);
  return new ExportPlan(new Export(exportId,release,new PublicationStore.ExportFile((String)r.get("storage_key"),(String)r.get("zip_sha256"),((Number)r.get("byte_length")).longValue())),(String)r.get("inventory_sha256"),origins);
 }
 /** Internal trusted control only; no HTTP/admin UI. No un-revoke or artifact deletion. */
 @Transactional(propagation=Propagation.REQUIRED)
 void revoke(String release,String reason){
  if(reason==null||reason.isBlank())throw new IllegalArgumentException("Revocation reason required");
  lock(release);
  // MVCC fence: an RR transaction waiting on this candidate must retry with a new snapshot.
  // SELECT FOR UPDATE alone does not change its tuple version.
  jdbc.update("update publication_candidate set created_at=created_at where release_id=?",release);
  jdbc.update("insert into publication_revocation(release_id,reason,revoked_at) values (?,?,?) on conflict (release_id) do nothing",release,reason,Timestamp.from(Instant.now()));
 }
}
