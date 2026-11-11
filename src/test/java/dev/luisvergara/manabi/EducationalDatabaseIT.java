package dev.luisvergara.manabi;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import javax.sql.DataSource;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import dev.luisvergara.manabi.service.*;
import dev.luisvergara.manabi.dto.phrase.*;
import dev.luisvergara.manabi.dto.kanjis.*;
import dev.luisvergara.manabi.dto.vocabulary.*;
import dev.luisvergara.manabi.entity.kana.Kana;
import dev.luisvergara.manabi.enums.kana.*;
import dev.luisvergara.manabi.enums.kanjis.*;
import dev.luisvergara.manabi.enums.phrase.PhraseKind;

/** Explicit post-reset acceptance only. Technical records roll back; never editorial content. */
@EnabledIfEnvironmentVariable(named="MANABI_RUN_EDUCATIONAL_IT",matches="true")
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.NONE)
@Transactional
@org.springframework.security.test.context.support.WithMockUser(roles="ADMIN")
class EducationalDatabaseIT {
    @Autowired DataSource dataSource;
    @Autowired EntityManager em;
    @Autowired KanaService kana;
    @Autowired KanjiService kanji;
    @Autowired VocabularyService vocabulary;
    @Autowired PhraseService phrases;
    @Autowired PhraseReferenceService references;
    @BeforeEach void onlyApprovedDatabase() throws Exception {
        try(var c=dataSource.getConnection();var s=c.createStatement();var r=s.executeQuery("select current_database()")) {
            r.next();assertEquals("manabi",r.getString(1));
        }
    }
    @Test void cleanSchemaAndFlywayAreRecognizedByHibernate() {
        assertEquals(27L, ((Number)em.createNativeQuery("select count(*) from information_schema.tables where table_schema='public' and table_name <> 'flyway_schema_history'").getSingleResult()).longValue());
        assertEquals(5L, ((Number)em.createNativeQuery("select count(*) from flyway_schema_history where success and version in ('1','2','3','4','5')").getSingleResult()).longValue());
        assertEquals(6,references.categories().size());
    }
    @Test void kanaCrudRetainsPublicIdentity() {
        var k=new Kana(); k.setCharacter("__KA__"); k.setRomaji("__R__");
        k.setType(KanaType.HIRAGANA); k.setGroup(KanaGroup.NORMAL); k.setDisplayOrder(2147483648L);
        var created=kana.save(k); var publicId=created.getPublicId(); Long id=created.getId();
        var edit=new Kana(); edit.setCharacter("__KE__"); edit.setRomaji("__R2__");
        edit.setType(k.getType());edit.setGroup(k.getGroup());edit.setDisplayOrder(0L);edit.setVersion(created.getVersion());
        assertEquals(publicId,kana.update(id,edit).getPublicId());kana.delete(id);em.flush();
        assertThrows(org.springframework.web.server.ResponseStatusException.class,()->kana.findById(id));
    }
    @Test void kanjiCrudOwnsReadingsAndExamples() {
        var read=new KanjiReadingRequest("__READ__",ReadingType.KUNYOMI,"__R__",true);
        var example=new KanjiExampleRequest("__JP__","__K__","__ES__",0L);
        var r=new KanjiRequest("__K__","__ES__",1,JlptLevel.N5,0L,null,null,List.of(read),List.of(example),null);
        var saved=kanji.save(r);
        var updated=kanji.updateResponse(saved.id(),new KanjiRequest("__K__","__EDIT__",1,JlptLevel.N5,0L,null,null,List.of(read),List.of(example),saved.version()));
        assertEquals(saved.publicId(),updated.publicId());kanji.delete(saved.id());em.flush();
        assertEquals(0L,((Number)em.createNativeQuery("select count(*) from kanji_reading where kanji_id=:id").setParameter("id",saved.id()).getSingleResult()).longValue());
    }
    @Test void vocabularyMembershipsDoNotDuplicateOrDeleteSharedCategories() {
        var saved=vocabulary.save(new VocabularyRequest("__JP__","__K__","__R__","__ES__",0L,Set.of("FOOD","HOME"),null));
        var updated=vocabulary.update(saved.id(),new VocabularyRequest("__JP__","__K__","__R__","__EDIT__",0L,Set.of("HOME"),saved.version()));
        assertEquals(saved.publicId(),updated.publicId());assertEquals(Set.of("HOME"),updated.categories());
        vocabulary.delete(saved.id());em.flush();
        assertEquals(20L,((Number)em.createNativeQuery("select count(*) from vocabulary_category").getSingleResult()).longValue());
    }
    PhraseRequest request(PhraseKind kind,List<PhraseRequest.Example> examples,PhraseRequest.Pattern pattern,List<PhraseRequest.LearningKey> keys,Long version) {
        return new PhraseRequest(kind,0L,List.of("comunicacion-basica"),"__JP__","__K__","__R__","__ES__",
            "conversation","__INTRO__",null,List.of("__POINT__"),keys,examples,null,pattern,version);
    }
    @Test void fixedFourExamplesSurviveReorderAndRootVersionChanges() {
        var examples=new ArrayList<PhraseRequest.Example>();
        for(long i=0;i<4;i++)examples.add(new PhraseRequest.Example(null,i,"__JP__","__K__","__R__","__ES__","examples","__SITUATION__"));
        var saved=phrases.save(request(PhraseKind.FIXED,examples,null,null,null));
        var reordered=new ArrayList<>(saved.examples());Collections.reverse(reordered);
        var updated=phrases.update(saved.id(),request(PhraseKind.FIXED,reordered,null,null,saved.version()));
        assertEquals(4,phrases.findById(saved.id()).examples().size());
        assertTrue(updated.version()>saved.version());assertEquals(saved.publicId(),updated.publicId());
        phrases.delete(saved.id());em.flush();
        assertEquals(0L,((Number)em.createNativeQuery("select count(*) from phrase_example where phrase_id=:id").setParameter("id",saved.id()).getSingleResult()).longValue());
    }
    @Test void patternResolvesSharedParticleAndAuxiliaryExample() {
        var particle=references.createParticle(new ParticleRequest("__PARTICLE__","__TITLE__","__EXPLANATION__",null));
        var pattern=new PhraseRequest.Pattern("__FORMULA__","__EXPLANATION__",List.of(new PhraseRequest.Slot(null,"__SLOT__","__DESC__")));
        var key=new PhraseRequest.LearningKey(null,"usage",particle.publicId(),null,null,new PhraseRequest.Text("__JP__","__K__","__R__","__ES__"));
        var saved=phrases.save(request(PhraseKind.PATTERN,List.of(),pattern,List.of(key),null));
        em.flush();em.clear();var found=phrases.findById(saved.id());
        assertEquals(particle.publicId(),found.learningKeys().getFirst().particleId());
        assertNotNull(found.learningKeys().getFirst().example());assertEquals(1,found.pattern().slots().size());
        phrases.delete(saved.id());em.flush();references.deleteParticle(particle.id());
    }
    @Test void referencedParticleCannotBeDeleted() {
        var particle=references.createParticle(new ParticleRequest("__PARTICLE__","__TITLE__","__EXPLANATION__",null));
        var pattern=new PhraseRequest.Pattern("__FORMULA__","__EXPLANATION__",List.of());
        var key=new PhraseRequest.LearningKey(null,"usage",particle.publicId(),null,null,null);
        phrases.save(request(PhraseKind.PATTERN,List.of(),pattern,List.of(key),null));
        assertEquals(409, assertThrows(org.springframework.web.server.ResponseStatusException.class,
            ()->references.deleteParticle(particle.id())).getStatusCode().value());
        em.flush(); em.clear();
        var failure = assertThrows(jakarta.persistence.PersistenceException.class, () ->
            em.createNativeQuery("delete from particle where id=:id").setParameter("id",particle.id()).executeUpdate());
        Throwable cause = failure;
        while(cause != null && !(cause instanceof java.sql.SQLException)) cause=cause.getCause();
        assertNotNull(cause, "The PostgreSQL FK must reject the actual SQL deletion");
        assertEquals("23001", ((java.sql.SQLException)cause).getSQLState());
        assertTrue(cause.getMessage().contains("phrase_learning_key_particle_id_fkey"));
    }
    @Test void fixedMeaningsCanReorderWithoutChangingTheirIdentities() {
        var base=request(PhraseKind.FIXED,List.of(),null,null,null);
        var meanings=List.of(new PhraseRequest.Meaning(null,"__ONE__","__EXPL1__","attention"),
            new PhraseRequest.Meaning(null,"__TWO__","__EXPL2__","conversation"));
        var saved=phrases.save(new PhraseRequest(base.kind(),base.displayOrder(),base.categoryIds(),base.japanese(),
            base.kana(),base.romaji(),base.meaning(),base.iconType(),base.introduction(),null,
            base.keyPoints(),null,List.of(),meanings,null,null));
        var reversed=new ArrayList<>(saved.meanings());Collections.reverse(reversed);
        phrases.update(saved.id(),new PhraseRequest(base.kind(),base.displayOrder(),base.categoryIds(),base.japanese(),
            base.kana(),base.romaji(),base.meaning(),base.iconType(),base.introduction(),null,
            base.keyPoints(),null,List.of(),reversed,null,saved.version()));
        em.flush();em.clear();
        assertEquals(reversed.stream().map(PhraseRequest.Meaning::id).toList(),
            phrases.findById(saved.id()).meanings().stream().map(PhraseRequest.Meaning::id).toList());
    }
    @Test void patternSlotsAndKeysCanReorderWithoutLosingChildren() {
        var slots=List.of(new PhraseRequest.Slot(null,"__ONE__","__DESC1__"),new PhraseRequest.Slot(null,"__TWO__","__DESC2__"));
        var keys=List.of(new PhraseRequest.LearningKey(null,"usage",null,"__ONE__","__EXPL1__",null),
            new PhraseRequest.LearningKey(null,"attention",null,"__TWO__","__EXPL2__",null));
        var saved=phrases.save(request(PhraseKind.PATTERN,List.of(),
            new PhraseRequest.Pattern("__FORMULA__","__EXPLANATION__",slots),keys,null));
        var nextSlots=new ArrayList<>(saved.pattern().slots());Collections.reverse(nextSlots);
        var nextKeys=new ArrayList<>(saved.learningKeys());Collections.reverse(nextKeys);
        phrases.update(saved.id(),request(PhraseKind.PATTERN,List.of(),
            new PhraseRequest.Pattern("__FORMULA__","__EXPLANATION__",nextSlots),nextKeys,saved.version()));
        em.flush();em.clear();var found=phrases.findById(saved.id());
        assertEquals(nextSlots.stream().map(PhraseRequest.Slot::id).toList(),found.pattern().slots().stream().map(PhraseRequest.Slot::id).toList());
        assertEquals(nextKeys.stream().map(PhraseRequest.LearningKey::id).toList(),found.learningKeys().stream().map(PhraseRequest.LearningKey::id).toList());
    }
    @Test void postgresRejectsTwoPrimaryKanjiReadingsEvenOutsideTheService() {
        var saved=kanji.save(new KanjiRequest("__K__","__ES__",1,JlptLevel.N5,0L,null,null,
            List.of(new KanjiReadingRequest("__READ__",ReadingType.KUNYOMI,"__R__",true)),List.of(),null));
        var failure=assertThrows(jakarta.persistence.PersistenceException.class,()->em.createNativeQuery(
            "insert into kanji_reading(kanji_id,reading,reading_type,romaji,is_primary,editorial_position) values (:id,'__SECOND__','ONYOMI','__R2__',true,1)")
            .setParameter("id",saved.id()).executeUpdate());
        Throwable cause=failure;
        while(cause!=null && !(cause instanceof java.sql.SQLException))cause=cause.getCause();
        assertNotNull(cause);assertEquals("23505",((java.sql.SQLException)cause).getSQLState());
        assertTrue(cause.getMessage().contains("uq_kanji_primary_reading"));
    }
}
