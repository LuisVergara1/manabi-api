package dev.luisvergara.manabi.content.publication;

import dev.luisvergara.manabi.content.bootstrap.IntegrationDatabaseTarget;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import java.nio.file.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.jdbc.core.JdbcTemplate;
import dev.luisvergara.manabi.content.editorial.EditorialSnapshotService;
import dev.luisvergara.manabi.content.publication.contract.*;

/** Explicit live opt-in: reads the adopted catalog; writes TECHNICAL/BLOCKED private job metadata only. */
@EnabledIfEnvironmentVariable(named="MANABI_RUN_PUBLICATION_LIVE_IT",matches="true")
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.NONE)
class PublicationLivePostgresIT {
 @Autowired PublicationSnapshotService snapshots;
 @Autowired PublicationGenerator generator;
 @Autowired JdbcTemplate jdbc;
 @Autowired EntityManager em;
 @MockitoSpyBean EditorialSnapshotService fingerprints;
 void verifyTarget(){
  assertEquals(IntegrationDatabaseTarget.expected("MANABI_IT_EXPECTED_DB_URL"),System.getenv("DB_URL"));
  assertEquals("manabi",jdbc.queryForObject("select current_database()",String.class));
  assertEquals(IntegrationDatabaseTarget.expected("MANABI_IT_EXPECTED_SYSTEM_ID"),jdbc.queryForObject("select system_identifier::text from pg_control_system()",String.class));
 }
 @Test void adoptedCatalogRemainsPendingAndMatchesFrontendBytes()throws Exception {
  verifyTarget();var snapshot=snapshots.capture(PublicationSnapshotService.Profile.CORE);
  assertEquals(287,snapshot.sources().size());assertEquals(287,snapshot.editorialIssues().size());
  var blocked=generator.generate(PublicationSnapshotService.Profile.CORE);
  assertEquals(PublicationGenerator.Status.BLOCKED,blocked.status());assertTrue(blocked.ready().isEmpty());
  var core=generator.generateTechnical(PublicationSnapshotService.Profile.CORE);
  var empty=generator.generateTechnical(PublicationSnapshotService.Profile.WITH_PHRASES);
  assertEquals(PublicationGenerator.Status.TECHNICAL,core.status());assertEquals(PublicationGenerator.Status.TECHNICAL,empty.status());
  assertArrayEquals(PublicationTestFixtures.resource("core/manifest.json"),core.technical().manifest());
  for(var p:core.technical().packages())assertArrayEquals(PublicationTestFixtures.resource("core/"+p.descriptor().url()),p.bytes());
  writeArtifacts("core",core.technical());writeArtifacts("empty",empty.technical());
  assertEquals(287,jdbc.queryForObject("select count(*) from editorial_review where state='PENDING' and approved_by is null and approved_at is null",Integer.class));
  assertEquals(0,jdbc.queryForObject("select count(*) from publication_candidate",Integer.class));
  assertEquals(0,jdbc.queryForObject("select count(*) from phrase",Integer.class));
 }
 @Test void snapshotKeepsOnePostgresqlMvccViewAcrossAConcurrentCommit()throws Exception {
  verifyTarget();var once=new AtomicBoolean();
  doAnswer(invocation->{
   if(!once.compareAndSet(false,true))return invocation.callRealMethod();
   assertEquals("repeatable read",em.createNativeQuery("show transaction_isolation").getSingleResult());
   assertEquals("on",em.createNativeQuery("show transaction_read_only").getSingleResult());
   Object before=em.createNativeQuery("select pg_current_snapshot()::text").getSingleResult();
   long countBefore=((Number)em.createNativeQuery("select count(*) from publication_job").getSingleResult()).longValue();
   // A separate pooled connection commits a legitimate diagnostic job. No educational rows/reviews change.
   var writer=CompletableFuture.supplyAsync(()->generator.generateTechnical(PublicationSnapshotService.Profile.CORE));
   assertEquals(PublicationGenerator.Status.TECHNICAL,writer.get(30,TimeUnit.SECONDS).status());
   Object after=em.createNativeQuery("select pg_current_snapshot()::text").getSingleResult();
   assertEquals(before,after);
   assertEquals(countBefore,((Number)em.createNativeQuery("select count(*) from publication_job").getSingleResult()).longValue());
   return invocation.callRealMethod();
  }).when(fingerprints).capture(anyMap());
  assertEquals(287,snapshots.capture(PublicationSnapshotService.Profile.CORE).sources().size());assertTrue(once.get());
  assertEquals(287,jdbc.queryForObject("select count(*) from editorial_review where state='PENDING'",Integer.class));
 }
 static void writeArtifacts(String name,PublicationCompiler.Compiled compiled)throws Exception {
  Path root=Path.of("target/stage23-publication-conformance",name);Files.createDirectories(root);
  Files.writeString(root.resolve("INTERNAL_TECHNICAL_ONLY.txt"),"Test artifacts. Not READY, not public, not for deployment.");
  Files.write(root.resolve("manifest.json"),compiled.manifest());
  for(var p:compiled.packages()){Path file=root.resolve(p.descriptor().url());Files.createDirectories(file.getParent());Files.write(file,p.bytes());}
 }
}
