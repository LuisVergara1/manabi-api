package dev.luisvergara.manabi.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import dev.luisvergara.manabi.entity.kana.Kana;
import dev.luisvergara.manabi.entity.kanjis.*;
import dev.luisvergara.manabi.entity.vocabulary.*;
import dev.luisvergara.manabi.enums.kana.*;
import dev.luisvergara.manabi.enums.kanjis.*;
import dev.luisvergara.manabi.dto.kanjis.*;
import dev.luisvergara.manabi.dto.vocabulary.*;
import dev.luisvergara.manabi.repository.*;
import dev.luisvergara.manabi.service.mapper.KanjiMapper;
import jakarta.persistence.*;
import jakarta.validation.*;
import org.junit.jupiter.api.*;
import org.springframework.data.domain.Sort;
import org.springframework.data.repository.query.parser.PartTree;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;

class ModelV1Test {
    static ValidatorFactory factory;
    ModelValidation validation = new ModelValidation(factory.getValidator());
    KanjiRepository repository = mock(KanjiRepository.class);
    EntityManager em = mock(EntityManager.class);
    KanjiService service = new KanjiService(repository, new KanjiMapper(), validation, em);
    @BeforeAll static void setup() { factory = Validation.buildDefaultValidatorFactory(); }
    @AfterAll static void close() { factory.close(); }
    Kanji kanji() {
        Kanji k = new Kanji(); k.setCharacter("\u6c34"); k.setMeaning("Agua");
        k.setStrokeCount(4); k.setJlptLevel(JlptLevel.N5); k.setDisplayOrder(1L);
        KanjiReading r = new KanjiReading(); r.setReading("\u307f\u305a");
        r.setReadingType(ReadingType.KUNYOMI); r.setRomaji("mizu"); r.setPrimary(true); k.addReading(r);
        return k;
    }
    @Test void explicitPrimaryAccepted() { assertDoesNotThrow(() -> service.validateAggregate(kanji())); }
    @Test void noReadingsRejected() {
        Kanji k = kanji(); k.getReadings().clear();
        assertThrows(ResponseStatusException.class, () -> service.validateAggregate(k));
    }
    @Test void noImplicitPrincipalChosen() {
        Kanji k = kanji(); k.getReadings().getFirst().setPrimary(false);
        assertThrows(ResponseStatusException.class, () -> service.validateAggregate(k));
        assertFalse(k.getReadings().getFirst().isPrimary());
    }
    @Test void twoPrincipalsRejected() {
        Kanji k = kanji(); KanjiReading r = new KanjiReading(); r.setReading("\u30b9\u30a4");
        r.setReadingType(ReadingType.ONYOMI); r.setRomaji("sui"); r.setPrimary(true); k.addReading(r);
        assertThrows(ResponseStatusException.class, () -> service.validateAggregate(k));
    }
    @Test void primaryRequiresEditorialRomaji() {
        Kanji k = kanji(); k.getReadings().getFirst().setRomaji(null);
        assertThrows(ResponseStatusException.class, () -> service.validateAggregate(k));
    }
    @Test void secondaryRomajiMayBeMissing() {
        Kanji k = kanji(); KanjiReading r = new KanjiReading(); r.setReading("\u30b9\u30a4");
        r.setReadingType(ReadingType.ONYOMI); k.addReading(r);
        assertDoesNotThrow(() -> service.validateAggregate(k));
    }
    @Test void duplicateReadingRejected() {
        Kanji k = kanji(); KanjiReading r = new KanjiReading();
        r.setReading(k.getReadings().getFirst().getReading()); r.setReadingType(ReadingType.KUNYOMI); k.addReading(r);
        assertThrows(ResponseStatusException.class, () -> service.validateAggregate(k));
    }
    @Test void radicalNameNeedsSymbol() {
        Kanji k = kanji(); k.setRadicalName("Agua");
        assertThrows(ResponseStatusException.class, () -> service.validateAggregate(k));
    }
    @Test void staleAggregateRejectedBeforeClearingChildren() {
        Kanji incoming = kanji(); incoming.setVersion(1L);
        Kanji existing = kanji(); existing.setVersion(2L);
        when(repository.findForUpdate(7L)).thenReturn(Optional.of(existing));
        assertThrows(ResponseStatusException.class, () -> service.update(7L, incoming));
        assertEquals(1, existing.getReadings().size()); verify(repository, never()).flush();
    }
    @Test void replacementFlushesOldPrimaryBeforeAddingNewAndForcesVersion() {
        Kanji incoming = kanji(); incoming.setVersion(2L);
        Kanji existing = kanji(); existing.setVersion(2L);
        when(repository.findForUpdate(7L)).thenReturn(Optional.of(existing));
        doAnswer(inv -> { assertTrue(existing.getReadings().isEmpty()); return null; }).when(repository).flush();
        when(repository.saveAndFlush(existing)).thenReturn(existing);
        assertSame(existing, service.update(7L, incoming));
        assertSame(existing, existing.getReadings().getFirst().getKanji());
        var order = inOrder(repository, em);
        order.verify(repository).findForUpdate(7L); order.verify(repository).flush();
        order.verify(em).lock(existing, LockModeType.PESSIMISTIC_FORCE_INCREMENT);
        order.verify(repository).saveAndFlush(existing);
    }
    @Test void kanjiResponseSerializesWithoutParentCycle() {
        String json = JsonMapper.builder().build().writeValueAsString(KanjiResponse.from(kanji()));
        assertTrue(json.contains("\"primary\":true")); assertFalse(json.contains("\"kanji\":"));
    }
    @Test void kanaExtendedOnlyKatakana() {
        Kana k = new Kana(); k.setCharacter("\u30d5\u30a1"); k.setRomaji("fa"); k.setDisplayOrder(105L);
        k.setGroup(KanaGroup.EXTENDED); k.setType(KanaType.HIRAGANA);
        assertThrows(ResponseStatusException.class, () -> validation.valid(k));
        k.setType(KanaType.KATAKANA); assertDoesNotThrow(() -> validation.valid(k));
    }
    @Test void kanaHomophonesAreNotRejectedByApplicationValidation() {
        for (String c : List.of("\u3058", "\u3062")) {
            Kana k = new Kana(); k.setCharacter(c); k.setRomaji("ji"); k.setType(KanaType.HIRAGANA);
            k.setGroup(KanaGroup.DAKUTEN); k.setDisplayOrder(1L); assertDoesNotThrow(() -> validation.valid(k));
        }
    }
    @Test void kanaNonNfcRejectedWithoutSilentConversion() {
        Kana k = new Kana(); k.setCharacter("\u304b\u3099"); k.setRomaji("ga");
        k.setType(KanaType.HIRAGANA); k.setGroup(KanaGroup.DAKUTEN); k.setDisplayOrder(1L);
        KanaRepository repo = mock(KanaRepository.class);
        assertThrows(ResponseStatusException.class, () -> new KanaService(repo, validation).save(k));
        verifyNoInteractions(repo); assertEquals("\u304b\u3099", k.getCharacter());
    }
    VocabularyCategory category(String code) {
        VocabularyCategory c = new VocabularyCategory(); c.setCode(code); c.setName(code);
        c.setDisplayOrder(1L); return c;
    }
    @Test void categoryFilterUsesOrAndAcceptsHistoricalHiddenCodes() {
        VocabularyRepository repo = mock(VocabularyRepository.class);
        VocabularyCategoryRepository cats = mock(VocabularyCategoryRepository.class);
        var codes = List.of("OBJECT", "FOOD");
        when(cats.findByCodeIn(codes)).thenReturn(List.of(category("OBJECT"), category("FOOD")));
        when(repo.findDistinctByCategories_CodeIn(codes, Sort.by("displayOrder", "publicId"))).thenReturn(List.of());
        new VocabularyService(repo, cats, validation).find(codes);
        verify(repo).findDistinctByCategories_CodeIn(codes, Sort.by("displayOrder", "publicId"));
    }
    @Test void unknownCategoryRejected() {
        VocabularyRepository repo = mock(VocabularyRepository.class);
        VocabularyCategoryRepository cats = mock(VocabularyCategoryRepository.class);
        when(cats.findByCodeIn(List.of("MISSING"))).thenReturn(List.of());
        assertThrows(ResponseStatusException.class, () -> new VocabularyService(repo, cats, validation).find(List.of("MISSING")));
        verifyNoInteractions(repo);
    }
    @Test void vocabularyRequiresKanaAndRomajiAndCategory() {
        var req = new VocabularyRequest("\u732b", null, null, "Gato", 1L, Set.of(), null);
        assertThrows(ResponseStatusException.class, () -> validation.valid(req));
    }
    @Test void vocabularyResponseRetainsCodeArrayAndExplicitReadings() {
        Vocabulary v = new Vocabulary(); v.setJapanese("\u732b"); v.setKana("\u306d\u3053"); v.setRomaji("neko");
        v.setCategories(Set.of(category("ANIMAL"), category("HOME")));
        var dto = VocabularyResponse.from(v);
        assertEquals(Set.of("ANIMAL", "HOME"), dto.categories()); assertEquals("neko", dto.romaji());
        assertFalse(JsonMapper.builder().build().writeValueAsString(dto).contains("showInCatalog"));
    }
    @Test void derivedRepositoryCategoryPathIsResolvable() {
        var tree = new PartTree("findDistinctByCategories_CodeIn", Vocabulary.class);
        assertTrue(tree.isDistinct()); assertEquals("categories.code", tree.getParts().iterator().next().getProperty().toDotPath());
    }
    @Test void mapperOwnsBothSidesAndKeepsExplicitPrimary() {
        var req = new KanjiRequest("\u6c34", "Agua", 4, JlptLevel.N5, 1L, null, null,
            List.of(new KanjiReadingRequest("\u307f\u305a", ReadingType.KUNYOMI, "mizu", true)),
            List.of(new KanjiExampleRequest("\u6c34", "\u307f\u305a", "Agua", 1L)), null);
        Kanji k = new KanjiMapper().toEntity(req);
        assertSame(k, k.getReadings().getFirst().getKanji()); assertSame(k, k.getExamples().getFirst().getKanji());
        k.getReadings().clear(); assertTrue(k.getReadings().isEmpty());
    }
    @Test void hibernateBuildsAndValidatesAllEntityMetadataWithoutJdbc() {
        var registry = new org.hibernate.boot.registry.StandardServiceRegistryBuilder()
            .applySetting("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect")
            .applySetting("hibernate.boot.allow_jdbc_metadata_access", false)
            .build();
        try {
            var metadata = new org.hibernate.boot.MetadataSources(registry)
                .addAnnotatedClass(Kana.class).addAnnotatedClass(Kanji.class)
                .addAnnotatedClass(KanjiReading.class).addAnnotatedClass(KanjiExample.class)
                .addAnnotatedClass(Vocabulary.class).addAnnotatedClass(VocabularyCategory.class)
                .addAnnotatedClass(dev.luisvergara.manabi.entity.phrase.Phrase.class)
                .addAnnotatedClass(dev.luisvergara.manabi.entity.phrase.PhraseCategory.class)
                .addAnnotatedClass(dev.luisvergara.manabi.entity.phrase.Particle.class)
                .addAnnotatedClass(dev.luisvergara.manabi.entity.phrase.PhraseExample.class)
                .addAnnotatedClass(dev.luisvergara.manabi.entity.phrase.PhraseMeaning.class)
                .addAnnotatedClass(dev.luisvergara.manabi.entity.phrase.PhraseSlot.class)
                .addAnnotatedClass(dev.luisvergara.manabi.entity.phrase.PhraseLearningKey.class)
                .addAnnotatedClass(dev.luisvergara.manabi.content.editorial.EditorialModels.Review.class)
                .addAnnotatedClass(dev.luisvergara.manabi.content.editorial.EditorialModels.Load.class).buildMetadata();
            ((org.hibernate.boot.spi.MetadataImplementor) metadata).validate();
            assertNotNull(metadata.getEntityBinding(Vocabulary.class.getName()));
        } finally {
            org.hibernate.boot.registry.StandardServiceRegistryBuilder.destroy(registry);
        }
    }
    @Test void flywayPostgresqlParserReadsAllMigrationStatementsWithoutExecutingSql() throws Exception {
        // Flyway tokenization/delimiters only: this does NOT validate PostgreSQL execution or DDL semantics.
        var config = org.flywaydb.core.Flyway.configure().placeholderReplacement(false);
        for (String file : List.of("V1__educational_schema.sql","V2__editorial_bootstrap_and_reviews.sql")) {
            try (var stream = getClass().getResourceAsStream("/db/migration/" + file)) {
                assertNotNull(stream);
                String sql = new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                var parser = new org.flywaydb.database.postgresql.PostgreSQLParser(config,
                    new org.flywaydb.core.internal.parser.ParsingContext());
                int count = 0;
                try (var statements = parser.parse(new org.flywaydb.core.internal.resource.StringResource(sql))) {
                    while (statements.hasNext()) { assertNotNull(statements.next()); count++; }
                }
                assertTrue(count > 0, file);
                assertEquals(file.startsWith("V1")?16:2, sql.lines().filter(line -> line.startsWith("CREATE TABLE ")).count());
                assertFalse(sql.contains("legacy_archive")); assertFalse(sql.contains("educational_import"));
                assertFalse(sql.contains("CREATE TABLE phrase_categories "));

            }
        }
    }
}
