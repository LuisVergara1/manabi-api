package dev.luisvergara.manabi.content.publication;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import jakarta.persistence.EntityManager;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.*;
import org.springframework.transaction.support.TransactionTemplate;
import dev.luisvergara.manabi.content.bootstrap.*;
import dev.luisvergara.manabi.content.editorial.*;
import dev.luisvergara.manabi.content.publication.contract.*;
import dev.luisvergara.manabi.entity.phrase.*;
import static dev.luisvergara.manabi.content.editorial.EditorialModels.*;

@EnabledIfEnvironmentVariable(named="MANABI_RUN_BOOTSTRAP_IT",matches="true")
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.NONE,properties="spring.jpa.properties.hibernate.generate_statistics=true")
@ContextConfiguration(initializers=TemporaryEditorialDatabase.class)
@DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class PublicationPipelinePostgresIT {
 @Autowired InitialCatalogLoader loader;
 @Autowired PublicationSnapshotService snapshots;
 @Autowired PublicationGenerator generator;
 @Autowired EditorialReviewService reviews;
 @Autowired JdbcTemplate jdbc;
 @Autowired EntityManager em;
 @Autowired PlatformTransactionManager transactions;
 @MockitoSpyBean PublicationCandidateStore store;
 void tx(Runnable r){new TransactionTemplate(transactions).executeWithoutResult(s->r.run());}
 long count(String table){return jdbc.queryForObject("select count(*) from "+table,Long.class);}
 void loadCore(){var c=new InitialCatalogReader().readResources();loader.apply(c,loader.dryRun(c).sha256());}
 void approveCore(){
  var sources=snapshots.capture(PublicationSnapshotService.Profile.CORE).sources().stream()
   .sorted(Comparator.comparing(s->s.kind().equals("VOCABULARY_CATEGORY")?0:1)).toList();
  for(var s:sources){var key=new Key(PublicationSnapshotService.NAMESPACE,Kind.valueOf(s.kind()),s.publicId());reviews.approve(key,s.fingerprint(),"isolated-test");}
 }
 @Test void pendingCatalogBlocksDistributionButInternalTechnicalJobCannotBecomeReady(){
  loadCore();var official=generator.generate(PublicationSnapshotService.Profile.CORE);
  assertEquals(PublicationGenerator.Status.BLOCKED,official.status());assertEquals(287,official.issues().size());assertTrue(official.ready().isEmpty());
  var technical=generator.generateTechnical(PublicationSnapshotService.Profile.CORE);
  assertEquals(PublicationGenerator.Status.TECHNICAL,technical.status());assertTrue(technical.ready().isEmpty());assertNotNull(technical.technical());
  assertEquals(0,count("publication_candidate"));assertEquals(0,count("publication_package"));assertEquals(2,count("publication_job"));
  assertEquals(287,jdbc.queryForObject("select count(*) from editorial_review where state='PENDING'",Integer.class));
  assertThrows(org.springframework.dao.DataIntegrityViolationException.class,()->jdbc.update("update publication_job set status='READY' where job_id=?",technical.jobId()));
 }
 @Test void approvedRepeatedGenerationDeduplicatesReleaseButKeepsIndependentJobs(){
  loadCore();approveCore();
  var a=generator.generate(PublicationSnapshotService.Profile.CORE);var b=generator.generate(PublicationSnapshotService.Profile.CORE);
  assertEquals(PublicationGenerator.Status.READY,a.status());assertEquals(a.computedReleaseId(),b.computedReleaseId());assertNotEquals(a.jobId(),b.jobId());
  assertEquals(1,count("publication_candidate"));assertEquals(4,count("publication_package"));assertEquals(2,count("publication_job"));
  assertArrayEquals(a.ready().orElseThrow().manifest(),b.ready().orElseThrow().manifest());
 }
 @Test void detachedSnapshotUsesFiveQueriesAndReproducesAllCurrentCoreBytes(){
  loadCore();var stats=em.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();stats.clear();
  var snapshot=snapshots.capture(PublicationSnapshotService.Profile.CORE);
  assertTrue(stats.getPrepareStatementCount()<=5,"N+1: "+stats.getPrepareStatementCount());
  assertEquals(287,snapshot.sources().size());assertEquals(287,snapshot.editorialIssues().size());
  var compiled=new PublicationCompiler().compile(snapshot,true);
  assertArrayEquals(PublicationTestFixtures.resource("core/manifest.json"),compiled.manifest());
  for(var p:compiled.packages())assertArrayEquals(PublicationTestFixtures.resource("core/"+p.descriptor().url()),p.bytes());
 }
 @Test void revokedCategoryInvalidatesPreviouslyApprovedDependentWords(){
  loadCore();approveCore();reviews.pending(new Key(PublicationSnapshotService.NAMESPACE,Kind.VOCABULARY_CATEGORY,"DRINKS"));
  var attempt=generator.generate(PublicationSnapshotService.Profile.CORE);
  assertEquals(PublicationGenerator.Status.BLOCKED,attempt.status());
  assertTrue(attempt.issues().contains("PENDING_OR_STALE:VOCABULARY:water"));assertEquals(0,count("publication_candidate"));
 }
 @Test void editorialChangeRequiresReviewAndOnlyVocabularyHashChangesAfterApproval(){
  loadCore();approveCore();var before=generator.generate(PublicationSnapshotService.Profile.CORE);
  jdbc.update("update vocabulary set meaning=meaning||' (test)',version=version+1 where public_id='water'");
  assertEquals(PublicationGenerator.Status.BLOCKED,generator.generate(PublicationSnapshotService.Profile.CORE).status());
  var key=new Key(PublicationSnapshotService.NAMESPACE,Kind.VOCABULARY,"water");reviews.approve(key,reviews.status(key).fingerprint(),"isolated-test");
  var after=generator.generate(PublicationSnapshotService.Profile.CORE);
  assertEquals(PublicationGenerator.Status.READY,after.status());assertNotEquals(before.computedReleaseId(),after.computedReleaseId());
  for(var a:before.ready().orElseThrow().packages()){
   var b=after.ready().orElseThrow().packages().stream().filter(p->p.descriptor().packageId().equals(a.descriptor().packageId())).findFirst().orElseThrow();
   if(a.descriptor().packageId().equals("vocabulary"))assertNotEquals(a.descriptor().sha256(),b.descriptor().sha256());else assertArrayEquals(a.bytes(),b.bytes());
  }
 }
 @Test void approvalChangedDuringCompilationCannotProduceReadyCandidate(){
  loadCore();approveCore();
  doAnswer(invocation->{reviews.pending(new Key(PublicationSnapshotService.NAMESPACE,Kind.VOCABULARY_CATEGORY,"DRINKS"));return invocation.callRealMethod();})
   .when(store).record(any(),any(),eq(false),any());
  var attempt=generator.generate(PublicationSnapshotService.Profile.CORE);
  assertEquals(PublicationGenerator.Status.BLOCKED,attempt.status());assertTrue(attempt.issues().contains("SNAPSHOT_CHANGED_DURING_COMPILATION"));assertEquals(0,count("publication_candidate"));
 }
 @Test void emptyPhrasesAreACompleteUnitWithoutTouchingCoreProfile(){
  loadCore();var technical=generator.generateTechnical(PublicationSnapshotService.Profile.WITH_PHRASES);
  assertEquals(PublicationGenerator.Status.TECHNICAL,technical.status());assertEquals(8,technical.technical().packages().size());
  assertEquals(0,count("phrase"));assertEquals(0,count("publication_candidate"));
  assertEquals(4,snapshots.capture(PublicationSnapshotService.Profile.CORE).packages().size());
 }
 @Test void populatedPhraseGraphsAreLoadedInBatchesAndFixturesStayTechnical(){
  loadCore();installPhraseFixtures();
  var stats=em.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();stats.clear();
  var snapshot=snapshots.capture(PublicationSnapshotService.Profile.WITH_PHRASES);
  assertTrue(stats.getPrepareStatementCount()<=14,"N+1: "+stats.getPrepareStatementCount());
  var compiled=new PublicationCompiler().compile(snapshot,true);assertEquals(8,compiled.packages().size());
  try{PublicationLivePostgresIT.writeArtifacts("technical",compiled);}catch(Exception e){throw new RuntimeException(e);}
  assertTrue(compiled.packages().stream().filter(p->p.descriptor().packageId().equals("phrases-pattern")).allMatch(p->p.descriptor().recordCount()>0));
  assertEquals(PublicationGenerator.Status.INVALID,generator.generate(PublicationSnapshotService.Profile.WITH_PHRASES).status());assertEquals(0,count("publication_candidate"));
 }
 void installPhraseFixtures(){
  tx(()->{
   var cats=new HashMap<String,PhraseCategory>();em.createQuery("select c from PhraseCategory c",PhraseCategory.class).getResultList().forEach(c->cats.put(c.getPublicId(),c));
   for(var c:PublicationTestFixtures.categories())if(!cats.containsKey(c.getPublicId())){c.setId(null);c.setVersion(null);em.persist(c);cats.put(c.getPublicId(),c);}
   var particles=new HashMap<String,Particle>();
   for(var p:PublicationTestFixtures.particles()){p.setId(null);p.setVersion(null);em.persist(p);particles.put(p.getPublicId(),p);}
   for(String slot:List.of("phrases-fixed","phrases-pattern"))for(var p:PublicationTestFixtures.expressions(slot)){
    p.setId(null);p.setVersion(null);p.setCategories(new LinkedHashSet<>(p.getCategories().stream().map(c->cats.get(c.getPublicId())).toList()));
    p.getExamples().forEach(e->e.setId(null));p.getMeanings().forEach(e->e.setId(null));p.getSlots().forEach(e->e.setId(null));
    p.getLearningKeys().forEach(k->{k.setId(null);if(k.getParticle()!=null)k.setParticle(particles.get(k.getParticle().getPublicId()));});
    em.persist(p);
   }
  });
 }

 @Test void failedJobInsertRollsBackCandidateAndPackageMetadata(){
  loadCore();approveCore();
  jdbc.execute("create function pg_temp.reject_publication_job() returns trigger language plpgsql as $$ begin raise exception 'isolated metadata failure'; end $$");
  jdbc.execute("create trigger reject_publication_job before insert on publication_job for each row execute function pg_temp.reject_publication_job()");
  assertThrows(org.springframework.dao.DataAccessException.class,()->generator.generate(PublicationSnapshotService.Profile.CORE));
  assertEquals(0,count("publication_candidate"));assertEquals(0,count("publication_package"));assertEquals(0,count("publication_job"));
 }
}
