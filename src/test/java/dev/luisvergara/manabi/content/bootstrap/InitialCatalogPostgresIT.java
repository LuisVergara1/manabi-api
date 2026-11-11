package dev.luisvergara.manabi.content.bootstrap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import jakarta.persistence.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import dev.luisvergara.manabi.content.editorial.*;
import dev.luisvergara.manabi.content.publication.contract.*;
import dev.luisvergara.manabi.entity.kana.Kana;
import dev.luisvergara.manabi.entity.kanjis.Kanji;
import dev.luisvergara.manabi.entity.vocabulary.VocabularyCategory;
import static dev.luisvergara.manabi.content.editorial.EditorialModels.*;

@EnabledIfEnvironmentVariable(named="MANABI_RUN_BOOTSTRAP_IT",matches="true")
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.NONE)
@ContextConfiguration(initializers=TemporaryEditorialDatabase.class)
@DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class InitialCatalogPostgresIT {
    @Autowired InitialCatalogLoader loader;
    @MockitoSpyBean EditorialReviewService reviews;
    @Autowired EditorialSnapshotService snapshots;
    @Autowired EntityManager em;
    @Autowired PlatformTransactionManager manager;
    final InitialCatalog source=new InitialCatalogReader().readResources();
    void tx(Runnable work){new TransactionTemplate(manager).executeWithoutResult(s->work.run());}
    long count(String table){return new TransactionTemplate(manager).execute(s->((Number)em.createNativeQuery("select count(*) from "+table).getSingleResult()).longValue());}
    InitialCatalogLoader.Applied load(){var plan=loader.dryRun(source);assertFalse(plan.conflicts());return loader.apply(source,plan.sha256());}
    @Test void dryRunReadsOnlyAndShowsExactProposedFootprint() {
        var a=loader.dryRun(source);var b=loader.dryRun(source);
        assertEquals(a.sha256(),b.sha256());assertEquals(0,count("kana"));assertEquals(20,count("vocabulary_category"));
        assertEquals(267L,((Map<?,?>)a.details().get("summary")).get("inserts"));
        assertEquals(0,count("editorial_review"));assertEquals(0,count("editorial_bootstrap_load"));
    }
    @Test void adoptionPreservesUnicodeIdentitiesRelationsPendingStateAndPublicHashes() {
        var applied=load();assertEquals(267,applied.rootsInserted());assertEquals(0,applied.categoriesInserted());assertEquals(287,applied.reviewsInserted());
        assertEquals(222,count("kana"));assertEquals(5,count("kanji"));assertEquals(11,count("kanji_reading"));assertEquals(40,count("vocabulary"));assertEquals(55,count("vocabulary_category_membership"));assertEquals(20,count("vocabulary_category"));assertEquals(0,count("phrase"));
        tx(()->{
            assertEquals(222,em.createQuery("select k.publicId from Kana k",String.class).getResultList().stream().filter(source.kana().stream().map(PublicContent.KanaEntry::editorialId).collect(java.util.stream.Collectors.toSet())::contains).count());
            assertTrue(em.createQuery("select k from Kana k where k.publicId=:id",Kana.class).setParameter("id","hiragana-あ").getResultStream().findAny().isPresent());
            var projector=new PublicContentProjector();var codec=new PublicPackageCodec();
            var packages=new ArrayList<PublicPackageCodec.EncodedPackage>();
            packages.add(codec.encode(source.namespace(),projector.kana(source.namespace(),em.createQuery("select k from Kana k",Kana.class).getResultList())));
            packages.add(codec.encode(source.namespace(),projector.vocabulary(source.namespace(),
                em.createQuery("select v from Vocabulary v",dev.luisvergara.manabi.entity.vocabulary.Vocabulary.class).getResultList(),
                em.createQuery("select c from VocabularyCategory c",VocabularyCategory.class).getResultList())));
            for(String level:List.of("N5","N4")){
                var values=em.createQuery("select k from Kanji k where k.jlptLevel=:level",Kanji.class).setParameter("level",dev.luisvergara.manabi.enums.kanjis.JlptLevel.valueOf(level)).getResultList();
                var actual=codec.encode(source.namespace(),projector.kanji(source.namespace(),level,values));
                var expected=codec.encode(source.namespace(),new PublicContent.KanjiPackage(source.namespace(),level,source.kanji().stream().filter(k->k.jlptLevel().equals(level)).toList()));
                assertArrayEquals(expected.bytes(),actual.bytes());packages.add(actual);
            }
            // Compare every database round-trip byte with the unchanged stage-2.1 frontend goldens.
            for(var value:packages)try(var in=getClass().getResourceAsStream("/public-contract-v1/core/"+value.descriptor().url())){
                assertNotNull(in,value.descriptor().packageId());assertArrayEquals(in.readAllBytes(),value.bytes());
            }catch(java.io.IOException e){throw new java.io.UncheckedIOException(e);}
        });
        assertEquals(State.PENDING,reviews.status(new Key(source.namespace(),Kind.VOCABULARY,"water")).effectiveState());
        assertEquals(287,count("editorial_review"));
    }
    @Test void identicalRetryIsANoopIncludingReviewsVersionsAndReceipt() {
        var plan=loader.dryRun(source);loader.apply(source,plan.sha256());
        var after=loader.dryRun(source);assertFalse(after.conflicts(),after.details().toString());var reused=loader.apply(source,plan.sha256());
        assertTrue(reused.reused());assertEquals(0,reused.rootsInserted());assertEquals(after.sha256(),loader.dryRun(source).sha256());
        assertEquals(1,count("editorial_bootstrap_load"));assertEquals(287,count("editorial_review"));assertEquals(20,count("vocabulary_category"));
    }
    @Test void changedDatabaseInvalidatesApprovedDryRunAndConflictsAreNotOverwritten() {
        var plan=loader.dryRun(source);
        tx(()->{var c=em.createQuery("select c from VocabularyCategory c where c.code='FOOD'",VocabularyCategory.class).getSingleResult();c.setName("__TEST_CONFLICT__");});
        assertTrue(loader.dryRun(source).conflicts());
        assertThrows(IllegalStateException.class,()->loader.apply(source,plan.sha256()));
        assertEquals(0,count("kana"));assertEquals(0,count("editorial_bootstrap_load"));
    }
    @Test void missingApprovalDigestNeverWritesAndFailureRollsBackEveryRoot() {
        assertThrows(IllegalArgumentException.class,()->loader.apply(source,null));
        var plan=loader.dryRun(source);
        doThrow(new IllegalStateException("Injected test failure")).when(reviews).ensurePending(any());
        assertThrows(IllegalStateException.class,()->loader.apply(source,plan.sha256()));
        assertEquals(0,count("kana"));assertEquals(0,count("kanji"));assertEquals(0,count("vocabulary"));assertEquals(0,count("editorial_review"));assertEquals(20,count("vocabulary_category"));
    }
    @Test void approvalRequiresApprovedDependenciesAndTracksTheirSubsequentRevision() {
        load();var water=new Key(source.namespace(),Kind.VOCABULARY,"water");
        assertThrows(IllegalStateException.class,()->reviews.approve(water,reviews.status(water).fingerprint(),"test-reviewer"));
        for(String code:List.of("DRINKS","NATURE")){
            var key=new Key(source.namespace(),Kind.VOCABULARY_CATEGORY,code);reviews.approve(key,reviews.status(key).fingerprint(),"test-reviewer");
        }
        reviews.approve(water,reviews.status(water).fingerprint(),"test-reviewer");
        assertEquals(State.APPROVED,reviews.status(water).effectiveState());
        tx(()->{var c=em.createQuery("select c from VocabularyCategory c where c.code='NATURE'",VocabularyCategory.class).getSingleResult();c.setName("__TEST_REVISION__");});
        assertEquals(State.PENDING,reviews.status(water).effectiveState());assertTrue(reviews.status(water).stale());
    }
    @Test void revokedDependencyAndChangedRootCannotKeepAnEffectiveApproval() {
        load();var kana=new Key(source.namespace(),Kind.KANA,source.kana().getFirst().editorialId());
        var fingerprint=reviews.status(kana).fingerprint();
        tx(()->{var k=em.createQuery("select k from Kana k where k.publicId=:id",Kana.class).setParameter("id",kana.getPublicId()).getSingleResult();k.setRomaji("__TEST_CHANGED__");});
        assertThrows(IllegalStateException.class,()->reviews.approve(kana,fingerprint,"test-reviewer"));
        var category=new Key(source.namespace(),Kind.VOCABULARY_CATEGORY,"DRINKS");reviews.approve(category,reviews.status(category).fingerprint(),"test-reviewer");reviews.pending(category);
        assertEquals(State.PENDING,reviews.status(category).effectiveState());
    }

    @Test void publicIdentityCannotBeReassignedToAnExistingWriting() {
        tx(()->{var k=new Kana();k.setPublicId("__OTHER_ID__");k.setCharacter("あ");k.setRomaji("a");k.setType(dev.luisvergara.manabi.enums.kana.KanaType.HIRAGANA);k.setGroup(dev.luisvergara.manabi.enums.kana.KanaGroup.NORMAL);k.setDisplayOrder(0L);em.persist(k);});
        var plan=loader.dryRun(source);assertTrue(plan.conflicts());
        assertThrows(IllegalStateException.class,()->loader.apply(source,plan.sha256()));assertEquals(1,count("kana"));
    }
    @Test void rootEditsAreReportedAndNeverOverwrittenByAnIdenticalImport() {
        var plan=loader.dryRun(source);loader.apply(source,plan.sha256());
        tx(()->{var k=em.createQuery("select k from Kana k where k.publicId='hiragana-あ'",Kana.class).getSingleResult();k.setRomaji("__EDITORIAL_CHANGE__");});
        assertTrue(loader.dryRun(source).conflicts());
        assertThrows(IllegalStateException.class,()->loader.apply(source,plan.sha256()));assertEquals(222,count("kana"));
    }
    @Test void stalePlanIsRejectedEvenWithoutAnyContentConflict() {
        var plan=loader.dryRun(source);var key=new Key(source.namespace(),Kind.VOCABULARY_CATEGORY,"DRINKS");
        reviews.ensurePending(key);assertFalse(loader.dryRun(source).conflicts());
        assertThrows(IllegalStateException.class,()->loader.apply(source,plan.sha256()));assertEquals(0,count("kana"));
    }
    @Test void successfulRetryDoesNotRevokeAnExplicitEditorialApproval() {
        var plan=loader.dryRun(source);loader.apply(source,plan.sha256());
        var key=new Key(source.namespace(),Kind.KANA,"hiragana-あ");
        reviews.approve(key,reviews.status(key).fingerprint(),"test-reviewer");
        assertTrue(loader.apply(source,plan.sha256()).reused());
        assertEquals(State.APPROVED,reviews.status(key).effectiveState());
    }
}
