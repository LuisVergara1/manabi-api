package dev.luisvergara.manabi.content.publication;

import dev.luisvergara.manabi.content.bootstrap.IntegrationDatabaseTarget;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
/** Live opt-in: no approvals/materialization of pending content, only BLOCKED/TECHNICAL job records. */
@EnabledIfEnvironmentVariable(named="MANABI_RUN_PUBLICATION_LIVE_IT",matches="true")
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.NONE)
class PublicationStorageLivePostgresIT {
 @Autowired PublicationGenerator generator;
 @Autowired PublicationMaterializationService materializer;
 @Autowired PublicationExportService exporter;
 @Autowired JdbcTemplate jdbc;
 @Test void realPendingCatalogCannotReachStorageOrExport()throws Exception {
  assertEquals(IntegrationDatabaseTarget.expected("MANABI_IT_EXPECTED_DB_URL"),System.getenv("DB_URL"));
  assertEquals("manabi",jdbc.queryForObject("select current_database()",String.class));
  assertEquals(IntegrationDatabaseTarget.expected("MANABI_IT_EXPECTED_SYSTEM_ID"),jdbc.queryForObject("select system_identifier::text from pg_control_system()",String.class));
  var blocked=generator.generate(PublicationSnapshotService.Profile.CORE);
  var technical=generator.generateTechnical(PublicationSnapshotService.Profile.CORE);
  assertEquals(PublicationGenerator.Status.BLOCKED,blocked.status());assertEquals(PublicationGenerator.Status.TECHNICAL,technical.status());
  assertThrows(IllegalStateException.class,()->materializer.materialize(blocked.jobId()));assertThrows(IllegalStateException.class,()->materializer.materialize(technical.jobId()));
  assertThrows(IllegalStateException.class,()->exporter.export(blocked.computedReleaseId()));
  for(String table:java.util.List.of("publication_storage","publication_export","publication_revocation","publication_candidate"))
   assertEquals(0,jdbc.queryForObject("select count(*) from "+table,Integer.class));
  assertEquals(287,jdbc.queryForObject("select count(*) from editorial_review where state='PENDING' and approved_by is null and approved_at is null",Integer.class));
 }
}
