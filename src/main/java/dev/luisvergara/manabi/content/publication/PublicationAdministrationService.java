package dev.luisvergara.manabi.content.publication;
import java.io.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import lombok.RequiredArgsConstructor;
import dev.luisvergara.manabi.admin.security.*;
import dev.luisvergara.manabi.content.publication.contract.CanonicalJson;

/** Authenticated facade only. No technical-generation flag or raw filesystem/storage API. */
@Service @RequiredArgsConstructor @PreAuthorize("hasAnyRole('ADMIN','PUBLISHER')")
public class PublicationAdministrationService {
 public record Generated(String jobId,String status,String releaseId,int issueCount,List<String> issues){}
 public record Release(String releaseId,String jobId,String storedAt,String state,boolean revoked){}
 public record Exported(String exportId,String releaseId,String sha256,long byteLength,String state){}
 private final PublicationGenerator generator;private final PublicationMaterializationService materializer;
 private final PublicationExportService exporter;private final PublicationArtifactLedger ledger;
 private final AdminAudit audit;private final JdbcTemplate jdbc;
 private final CanonicalJson json=new CanonicalJson();
 public Generated generate(PublicationSnapshotService.Profile profile){
  Objects.requireNonNull(profile);var a=generator.generate(profile);
  return new Generated(a.jobId(),a.status().name(),a.computedReleaseId(),a.issues().size(),a.issues().stream().limit(1000).toList());
 }
 public Release materialize(String jobId)throws IOException {
  var s=materializer.materialize(jobId);return view(s);
 }
 public List<Release> history(int limit,int offset){
  page(limit,offset);return exporter.history().stream().skip(offset).limit(limit).map(this::view).toList();
 }
 public record ExportHistory(String exportId,String releaseId,String exportedAt,String sha256,long byteLength,String state,boolean revoked){}
 public record ExportPage(long total,int limit,int offset,List<ExportHistory> items){}
 @org.springframework.transaction.annotation.Transactional(readOnly=true,isolation=org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
 public ExportPage exports(String release,int limit,int offset){
  page(limit,offset);
  if(release!=null&&!release.matches("sha256:[a-f0-9]{64}"))throw new IllegalArgumentException("Invalid release ID");
  String where=release==null?"":" where e.release_id=?";
  Object[] args=release==null?new Object[]{}:new Object[]{release};
  long total=jdbc.queryForObject("select count(*) from publication_export e"+where,Long.class,args);
  var params=new ArrayList<Object>(Arrays.asList(args));params.add(limit);params.add(offset);
  var items=jdbc.query("select e.export_id,e.release_id,e.exported_at,e.zip_sha256,e.byte_length,exists(select 1 from publication_revocation r where r.release_id=e.release_id or r.release_id in (select jsonb_array_elements_text(e.source_releases::jsonb))) as revoked from publication_export e"+where+" order by e.exported_at desc,e.export_id limit ? offset ?",
   (r,n)->{boolean revoked=r.getBoolean("revoked");return new ExportHistory(r.getString(1),r.getString(2),r.getString(3),r.getString(4),r.getLong(5),revoked?"REVOKED":"EXPORTED",revoked);},params.toArray());
  return new ExportPage(total,limit,offset,items);
 }
 private Release view(PublicationArtifactLedger.Stored s){return new Release(s.releaseId(),s.jobId(),s.storedAt(),s.exported()?"EXPORTED":"STORED",s.revoked());}
 public Exported export(String release)throws IOException {var e=exporter.export(release);return view(e);}
 public Exported rebuild(String id)throws IOException {var e=exporter.rebuild(id);audit.record(AdminIdentity.actor(),"REBUILD_EXPORT",id,"SUCCESS");return view(e);}
 private Exported view(PublicationArtifactLedger.Export e){return new Exported(e.exportId(),e.releaseId(),e.file().sha256(),e.file().byteLength(),"EXPORTED");}
 public InputStream download(String id)throws IOException {
  var in=exporter.open(id);try{audit.record(AdminIdentity.actor(),"DOWNLOAD_EXPORT",id,"SUCCESS");return in;}catch(RuntimeException failure){in.close();throw failure;}
 }
 @PreAuthorize("hasRole('ADMIN')")
 @org.springframework.transaction.annotation.Transactional
 public void revoke(String release,String reason){
  if(reason==null||reason.isBlank()||reason.codePointCount(0,reason.length())>1000||reason.codePoints().anyMatch(c->Character.isISOControl(c)&&c!='\n'))throw new IllegalArgumentException("Revocation reason required");
  ledger.revoke(release,reason);audit.record(AdminIdentity.actor(),"REVOKE_DISTRIBUTION",release,"SUCCESS");
 }
 private void page(int limit,int offset){if(limit<1||limit>100||offset<0||offset>1000000)throw new IllegalArgumentException("Invalid pagination");}
 public List<Map<String,Object>> jobs(int limit,int offset){
  page(limit,offset);return jdbc.queryForList("select job_id,mode,status,profile,computed_release_id,created_at from publication_job order by created_at desc,job_id limit ? offset ?",limit,offset);
 }
 @SuppressWarnings("unchecked") public Map<String,Object> job(String id){
  if(id==null||!id.matches("[a-f0-9-]{36}"))throw new IllegalArgumentException("Invalid job ID");
  var rows=jdbc.queryForList("select job_id,mode,status,profile,computed_release_id,validation_results,created_at from publication_job where job_id=?",id);
  if(rows.size()!=1)throw new IllegalStateException("Unknown job");var result=new LinkedHashMap<>(rows.getFirst());
  var issues=(List<Object>)json.parse(((String)result.remove("validation_results")).getBytes(java.nio.charset.StandardCharsets.UTF_8));result.put("issueCount",issues.size());result.put("issues",issues.stream().limit(1000).toList());return result;
 }
}
