package dev.luisvergara.manabi.content.publication;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import dev.luisvergara.manabi.content.bootstrap.*;
import dev.luisvergara.manabi.content.editorial.*;
import static dev.luisvergara.manabi.content.editorial.EditorialModels.*;

@EnabledIfEnvironmentVariable(named="MANABI_RUN_BOOTSTRAP_IT",matches="true")
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.NONE)
@ContextConfiguration(initializers=TemporaryEditorialDatabase.class)
@DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class PublicationStoragePostgresIT {
 @Autowired InitialCatalogLoader loader;
 @Autowired PublicationSnapshotService snapshots;
 @Autowired PublicationGenerator generator;
 @Autowired EditorialReviewService reviews;
 @Autowired PublicationMaterializationService materializer;
 @Autowired PublicationExportService exporter;
 @Autowired JdbcTemplate jdbc;
 @MockitoSpyBean PublicationStore store;
 @MockitoSpyBean PublicationArtifactLedger ledger;
 @Value("${manabi.publication.root}") String root;
 void load(){var s=new InitialCatalogReader().readResources();loader.apply(s,loader.dryRun(s).sha256());}
 long count(String table){return jdbc.queryForObject("select count(*) from "+table,Long.class);}
 PublicationGenerator.Attempt ready(){
  load();var all=snapshots.capture(PublicationSnapshotService.Profile.CORE).sources().stream().sorted(Comparator.comparing(s->s.kind().equals("VOCABULARY_CATEGORY")?0:1)).toList();
  for(var s:all){var k=new Key(PublicationSnapshotService.NAMESPACE,Kind.valueOf(s.kind()),s.publicId());reviews.approve(k,s.fingerprint(),"isolated-storage-test");}
  return generator.generate(PublicationSnapshotService.Profile.CORE);
 }
 PublicationGenerator.Attempt editWord(){
  jdbc.update("update vocabulary set meaning=meaning||' (isolated test)',version=version+1 where public_id='water'");
  var k=new Key(PublicationSnapshotService.NAMESPACE,Kind.VOCABULARY,"water");reviews.approve(k,reviews.status(k).fingerprint(),"isolated-storage-test");
  return generator.generate(PublicationSnapshotService.Profile.CORE);
 }
 byte[] zip(PublicationArtifactLedger.Export e)throws Exception{try(var in=exporter.open(e.exportId())){return in.readAllBytes();}}
 Set<String> names(byte[] bytes)throws Exception{
  var names=new HashSet<String>();try(var in=new ZipInputStream(new ByteArrayInputStream(bytes))){for(ZipEntry e;(e=in.getNextEntry())!=null;)assertTrue(names.add(e.getName()));}return names;
 }
 void rejectAudit(String action){
  jdbc.execute("create function pg_temp.reject_publication_audit() returns trigger language plpgsql as $$ begin if NEW.action='"+action+"' then raise exception 'synthetic audit failure'; end if; return NEW; end $$");
  jdbc.execute("create trigger reject_publication_audit before insert on admin_audit for each row execute function pg_temp.reject_publication_audit()");
 }
 void allowAudit(){jdbc.execute("drop trigger reject_publication_audit on admin_audit");jdbc.execute("drop function pg_temp.reject_publication_audit()");}
 @Test void generationAuditFailureRollsBackJobAndRetryHasOneDurableEvent(){
  load();rejectAudit("GENERATE_PUBLICATION");
  assertThrows(RuntimeException.class,()->generator.generate(PublicationSnapshotService.Profile.CORE));
  assertEquals(0,count("publication_job"));assertEquals(0,count("publication_candidate"));
  allowAudit();var job=generator.generate(PublicationSnapshotService.Profile.CORE);
  assertEquals(1,count("publication_job"));
  assertEquals(1,jdbc.queryForObject("select count(*) from admin_audit where action='GENERATE_PUBLICATION' and target=?",Integer.class,job.jobId()));
 }
 @Test void storageAuditFailureRollsBackLedgerAndRetryReusesBytes()throws Exception{
  var a=ready();rejectAudit("STORE_PUBLICATION");
  assertThrows(RuntimeException.class,()->materializer.materialize(a.jobId()));assertEquals(0,count("publication_storage"));
  assertArrayEquals(a.ready().orElseThrow().manifest(),store.readManifest(a.computedReleaseId()));
  allowAudit();materializer.materialize(a.jobId());assertEquals(1,count("publication_storage"));
  assertEquals(1,jdbc.queryForObject("select count(*) from admin_audit where action='STORE_PUBLICATION'",Integer.class));
 }
 @Test void exportAuditFailureRollsBackReceiptAndRetryReusesZip()throws Exception{
  var a=ready();materializer.materialize(a.jobId());rejectAudit("EXPORT_PUBLICATION");
  assertThrows(RuntimeException.class,()->exporter.export(a.computedReleaseId()));assertEquals(0,count("publication_export"));
  allowAudit();var e=exporter.export(a.computedReleaseId());assertEquals(1,count("publication_export"));assertEquals(5,names(zip(e)).size());
  assertEquals(1,jdbc.queryForObject("select count(*) from admin_audit where action='EXPORT_PUBLICATION' and target=?",Integer.class,e.exportId()));
 }
 @Test void simultaneousExportIsRejectedWithoutAnotherZipAndRetryWorks()throws Exception{
  var a=ready();materializer.materialize(a.jobId());
  var entered=new java.util.concurrent.CountDownLatch(1);var release=new java.util.concurrent.CountDownLatch(1);
  doAnswer(invocation->{entered.countDown();if(!release.await(10,java.util.concurrent.TimeUnit.SECONDS))throw new IOException("test admission timeout");return invocation.callRealMethod();}).when(store).stage();
  var executor=java.util.concurrent.Executors.newSingleThreadExecutor();
  try{
   var first=executor.submit(()->exporter.export(a.computedReleaseId()));
   assertTrue(entered.await(10,java.util.concurrent.TimeUnit.SECONDS));
   assertThrows(IllegalStateException.class,()->exporter.export(a.computedReleaseId()));
   release.countDown();assertNotNull(first.get(10,java.util.concurrent.TimeUnit.SECONDS));
  }finally{release.countDown();executor.shutdownNow();reset(store);}
  assertEquals(1,count("publication_export"));assertNotNull(exporter.export(a.computedReleaseId()));assertEquals(2,count("publication_export"));
 }
 @Test void invalidMemoryBudgetRejectsBeforeWritingAndPermitIsReleased()throws Exception{
  var a=ready();materializer.materialize(a.jobId());
  org.springframework.test.util.ReflectionTestUtils.setField(exporter,"maximumBytes",1L);
  assertThrows(IllegalStateException.class,()->exporter.export(a.computedReleaseId()));assertEquals(0,count("publication_export"));
  org.springframework.test.util.ReflectionTestUtils.setField(exporter,"maximumBytes",134217728L);
  assertEquals(5,names(zip(exporter.export(a.computedReleaseId()))).size());
 }
 @Test void storesReadyAndExportsCompleteReproducibleZipWithoutActivation()throws Exception {
  var a=ready();var stored=materializer.materialize(a.jobId());assertEquals(a.computedReleaseId(),stored.releaseId());
  var e=exporter.export(stored.releaseId());var again=exporter.export(stored.releaseId());
  assertEquals(e.file().sha256(),again.file().sha256());assertArrayEquals(zip(e),zip(again));assertEquals(5,names(zip(e)).size());
  assertEquals(1,count("publication_storage"));assertEquals(2,count("publication_export"));assertTrue(exporter.history().getFirst().exported());
  assertFalse(Files.exists(Path.of(root,"manifest.json")));assertFalse(Files.exists(Path.of(root,"educational-content/manifest.json")));
  extractForCompatibility("core",zip(e));
 }
 @Test void blockedTechnicalInvalidAndUnknownJobsCannotMaterialize()throws Exception {
  load();var blocked=generator.generate(PublicationSnapshotService.Profile.CORE);var technical=generator.generateTechnical(PublicationSnapshotService.Profile.CORE);
  String invalid=UUID.randomUUID().toString();
  jdbc.update("insert into publication_job(job_id,mode,status,profile,snapshot_fingerprint,source_revisions,validation_results,created_at) values (?,'DISTRIBUTABLE','INVALID','CORE',?,'[]','[]',now())",invalid,"0".repeat(64));
  for(String id:List.of(blocked.jobId(),technical.jobId(),invalid,UUID.randomUUID().toString()))assertThrows(IllegalStateException.class,()->materializer.materialize(id));
  assertEquals(0,count("publication_storage"));assertEquals(0,count("publication_export"));assertEquals(287,jdbc.queryForObject("select count(*) from editorial_review where state='PENDING'",Integer.class));
 }
 @Test void obsoleteReadyCannotBeSilentlyReplacedByCurrentRelease()throws Exception {
  var a=ready();var b=editWord();assertNotEquals(a.computedReleaseId(),b.computedReleaseId());
  assertThrows(IllegalStateException.class,()->materializer.materialize(a.jobId()));assertEquals(0,count("publication_storage"));
  assertThrows(IOException.class,()->store.readManifest(a.computedReleaseId()));
  assertEquals(b.computedReleaseId(),materializer.materialize(b.jobId()).releaseId());
 }
 @Test void historicalReleaseReexportsOriginalBytesAfterLaterEditorialEdits()throws Exception {
  var a=ready();materializer.materialize(a.jobId());byte[] original=store.readManifest(a.computedReleaseId());var oldExport=exporter.export(a.computedReleaseId());
  var b=editWord();materializer.materialize(b.jobId());var latest=exporter.export(b.computedReleaseId());var historic=exporter.export(a.computedReleaseId());
  assertArrayEquals(original,store.readManifest(a.computedReleaseId()));assertEquals(6,names(zip(latest)).size());assertEquals(6,names(zip(historic)).size());
  assertEquals(5,names(zip(oldExport)).size());assertEquals(2,exporter.history().size());assertTrue(Files.exists(Path.of(root,oldExport.file().key())));
  extractForCompatibility("latest",zip(latest));extractForCompatibility("historical",zip(historic));
  byte[] oldBytes=zip(oldExport);Path lost=Path.of(root,oldExport.file().key()).toAbsolutePath().normalize();
  assertTrue(lost.startsWith(Path.of(root).toAbsolutePath().normalize()));Files.delete(lost);
  var restored=exporter.rebuild(oldExport.exportId());assertEquals(oldExport.file(),restored.file());assertArrayEquals(oldBytes,zip(restored));
 }
 @Test void explicitRevocationBlocksNewAndExistingDownloadsButRetainsAudit()throws Exception {
  var a=ready();materializer.materialize(a.jobId());var old=exporter.export(a.computedReleaseId());byte[] original=store.readManifest(a.computedReleaseId());
  ledger.revoke(a.computedReleaseId(),"isolated revocation");
  assertThrows(IllegalStateException.class,()->exporter.export(a.computedReleaseId()));assertThrows(IllegalStateException.class,()->exporter.open(old.exportId()));assertThrows(IllegalStateException.class,()->exporter.rebuild(old.exportId()));
  assertThrows(IllegalStateException.class,()->materializer.materialize(a.jobId()));assertArrayEquals(original,store.readManifest(a.computedReleaseId()));assertTrue(exporter.history().getFirst().revoked());
  var b=editWord();materializer.materialize(b.jobId());var current=exporter.export(b.computedReleaseId());assertEquals(5,names(zip(current)).size());
 }
 @Test void failedManifestWriteLeavesNoStoredReleaseAndRetryReusesPackages()throws Exception {
  var a=ready();doThrow(new IOException("isolated write failure")).when(store).putManifest(eq(a.computedReleaseId()),any());
  assertThrows(IOException.class,()->materializer.materialize(a.jobId()));assertEquals(0,count("publication_storage"));assertThrows(IllegalStateException.class,()->exporter.export(a.computedReleaseId()));
  var p=a.ready().orElseThrow().packages().getFirst();Path path=Path.of(root,p.descriptor().url());var modified=Files.getLastModifiedTime(path);
  reset(store);materializer.materialize(a.jobId());assertEquals(modified,Files.getLastModifiedTime(path));assertEquals(1,count("publication_storage"));
 }
 @Test void failedStorageMetadataLeavesUnlistedManifestAndRetryRecovers()throws Exception {
  var a=ready();doThrow(new IllegalStateException("isolated ledger failure")).when(ledger).stored(any(),any());
  assertThrows(IllegalStateException.class,()->materializer.materialize(a.jobId()));assertArrayEquals(a.ready().orElseThrow().manifest(),store.readManifest(a.computedReleaseId()));
  assertEquals(0,count("publication_storage"));assertThrows(IllegalStateException.class,()->exporter.export(a.computedReleaseId()));
  reset(ledger);materializer.materialize(a.jobId());assertEquals(1,count("publication_storage"));
 }
 @Test void corruptedPackageBlocksNewExportsWithoutOverwritingHistory()throws Exception {
  var a=ready();materializer.materialize(a.jobId());var p=a.ready().orElseThrow().packages().getFirst();Path path=Path.of(root,p.descriptor().url());Files.write(path,new byte[]{1,2});
  assertThrows(IllegalArgumentException.class,()->exporter.export(a.computedReleaseId()));assertEquals(0,count("publication_export"));
  assertThrows(IOException.class,()->materializer.materialize(a.jobId()));assertArrayEquals(new byte[]{1,2},Files.readAllBytes(path));
 }
 @Test void failedZipFinalizationIsPrivateAndRetrySucceeds()throws Exception {
  var a=ready();materializer.materialize(a.jobId());doThrow(new IOException("isolated ZIP failure")).when(store).completeExport(any(),any());
  assertThrows(IOException.class,()->exporter.export(a.computedReleaseId()));assertEquals(0,count("publication_export"));
  try(var files=Files.list(Path.of(root,".private/staging"))){assertEquals(0,files.count());}
  reset(store);var e=exporter.export(a.computedReleaseId());assertEquals(5,names(zip(e)).size());
 }
 @Test void failedExportMetadataDoesNotExposeZipAndRetryReusesImmutableFile()throws Exception {
  var a=ready();materializer.materialize(a.jobId());doThrow(new IllegalStateException("isolated export ledger failure")).when(ledger).exported(any(),any(),any(),any());
  assertThrows(IllegalStateException.class,()->exporter.export(a.computedReleaseId()));assertEquals(0,count("publication_export"));
  Path file;try(var files=Files.walk(Path.of(root,"exports"))){file=files.filter(p->p.toString().endsWith(".zip")).findFirst().orElseThrow();}
  var modified=Files.getLastModifiedTime(file);assertThrows(IllegalStateException.class,()->exporter.open(UUID.randomUUID().toString()));
  reset(ledger);var e=exporter.export(a.computedReleaseId());assertEquals(modified,Files.getLastModifiedTime(file));assertEquals(5,names(zip(e)).size());
 }
 static void extractForCompatibility(String scenario,byte[] bytes)throws Exception {
  Path target=Path.of("target/stage24-export-conformance",scenario);Files.createDirectories(target);
  Files.writeString(target.resolve("INTERNAL_TEST_ONLY.txt"),"Isolated PostgreSQL-approved test artifacts. Not production content.");
  try(var in=new ZipInputStream(new ByteArrayInputStream(bytes))){
   for(ZipEntry e;(e=in.getNextEntry())!=null;){
    String name=e.getName();if(!name.matches("educational-content/(manifest\\.json|packages/[a-z0-9-]+/[a-f0-9]{64}\\.json)"))throw new IOException("Unexpected test ZIP entry");
    Path path=target.resolve(name).normalize();if(!path.startsWith(target))throw new IOException("Unsafe test extraction");
    Files.createDirectories(path.getParent());Files.write(path,in.readAllBytes());
   }
  }
 }

 @Test void completeEmptyPhraseProfileStoresAndExportsAllFourPhraseFiles()throws Exception {
  ready();int created=0;
  for(var source:snapshots.capture(PublicationSnapshotService.Profile.WITH_PHRASES).sources())if(source.effectiveState()!=State.APPROVED){
   var key=new Key(PublicationSnapshotService.NAMESPACE,Kind.valueOf(source.kind()),source.publicId());
   if(created++%2==0)reviews.pending(key);
   reviews.approve(key,source.fingerprint(),"isolated-storage-test");
  }
  var attempt=generator.generate(PublicationSnapshotService.Profile.WITH_PHRASES);assertEquals(PublicationGenerator.Status.READY,attempt.status());
  materializer.materialize(attempt.jobId());var exported=exporter.export(attempt.computedReleaseId());
  assertEquals(9,names(zip(exported)).size());assertEquals(0,count("phrase"));
  extractForCompatibility("empty",zip(exported));
 }
}
