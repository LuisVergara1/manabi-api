package dev.luisvergara.manabi.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import jakarta.persistence.*;
import jakarta.validation.*;
import org.junit.jupiter.api.*;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;
import dev.luisvergara.manabi.dto.phrase.*;
import dev.luisvergara.manabi.entity.phrase.*;
import dev.luisvergara.manabi.enums.phrase.PhraseKind;
import dev.luisvergara.manabi.repository.*;
import dev.luisvergara.manabi.service.mapper.PhraseMapper;
import dev.luisvergara.manabi.service.phrase.*;

/** Technical fixtures only. No Spring context, JDBC, migration execution or educational seeding. */
class PhraseModelTest {
    static ValidatorFactory factory;
    ModelValidation models = new ModelValidation(factory.getValidator());
    PhraseValidation validation = new PhraseValidation(models);
    PhraseMapper mapper = new PhraseMapper();
    PhraseRepository repository = mock(PhraseRepository.class);
    PhraseCategoryRepository categories = mock(PhraseCategoryRepository.class);
    ParticleRepository particles = mock(ParticleRepository.class);
    EntityManager em = mock(EntityManager.class);
    PhraseService service = new PhraseService(repository, categories, particles, validation, models, mapper, em);
    @BeforeAll static void setup() { factory = Validation.buildDefaultValidatorFactory(); }
    @AfterAll static void close() { factory.close(); }

    PhraseRequest.Example example(String id, long order) {
        return new PhraseRequest.Example(id, order, "__JP__", "__KANA__", "__ROMAJI__", "__ES__", "examples", "__SITUATION__");
    }
    PhraseRequest request(PhraseKind kind, List<PhraseRequest.Example> examples,
            List<PhraseRequest.Meaning> meanings, PhraseRequest.Pattern pattern,
            List<PhraseRequest.LearningKey> keys, Long version) {
        return new PhraseRequest(kind, 0L, List.of("comunicacion-basica"),
                "__JP__", "__KANA__", "__ROMAJI__", "__ES__", "conversation",
                "__INTRO__", null, List.of("__POINT__"), keys, examples, meanings, pattern, version);
    }
    PhraseRequest fixed(Long version) {
        return request(PhraseKind.FIXED, List.of(example(null, 0)), List.of(
                new PhraseRequest.Meaning(null, "__LABEL__", "__EXPLANATION__", "attention")), null, null, version);
    }
    PhraseRequest.Pattern pattern() {
        return new PhraseRequest.Pattern("__FORMULA__", "__EXPLANATION__",
                List.of(new PhraseRequest.Slot(null, "__SLOT__", "__DESCRIPTION__")));
    }
    PhraseCategory category() {
        var c = new PhraseCategory(); c.setId(9L); c.setPublicId("comunicacion-basica");
        c.setName("__CATEGORY__"); c.setDescription("__DESCRIPTION__"); c.setDisplayOrder(0L); c.setEnabled(true); return c;
    }
    Phrase aggregate(PhraseRequest r) {
        var p = new Phrase(); p.setId(1L); p.setPublicId("phr-existing"); p.setVersion(3L);
        mapper.apply(p, r, List.of(category()), Map.of()); return p;
    }
    @Test void fixedAndPatternWithEmptyExamplesRemainRepresentable() {
        validation.phrase(fixed(null));
        validation.phrase(request(PhraseKind.PATTERN, List.of(), null,
                new PhraseRequest.Pattern("__FORMULA__", "__EXPLANATION__", List.of()), null, null));
    }
    @Test void fourCentralExamplesPlusAnAuxiliaryExampleAreValid() {
        var key = new PhraseRequest.LearningKey(null, "usage", null, "__TITLE__", "__EXPLANATION__",
                new PhraseRequest.Text("__JP__", "__KANA__", "__ROMAJI__", "__ES__"));
        var r = request(PhraseKind.PATTERN, List.of(example(null, 0), example(null, 1), example(null, 2), example(null, 3)), null,
                pattern(), List.of(key), null);
        validation.phrase(r);
        var p = aggregate(r);
        assertEquals(4, mapper.response(p).examples().size());
        assertNotNull(mapper.response(p).learningKeys().getFirst().example());
    }
    @Test void fifthExampleAndFourthLearningKeyAreRejected() {
        assertThrows(ResponseStatusException.class, () -> validation.phrase(request(PhraseKind.FIXED,
                List.of(example(null, 0), example(null, 1), example(null, 2), example(null, 3), example(null, 4)), null, null, null, null)));
        var key = new PhraseRequest.LearningKey(null, "usage", null, "__TITLE__", "__EXPLANATION__", null);
        assertThrows(ResponseStatusException.class, () -> validation.phrase(request(PhraseKind.PATTERN,
                List.of(), null, pattern(), List.of(key, key, key, key), null)));
    }
    @Test void typeIncompatibleFieldsAreRejected() {
        assertThrows(ResponseStatusException.class, () -> validation.phrase(request(PhraseKind.FIXED, List.of(), null, pattern(), null, null)));
        assertThrows(ResponseStatusException.class, () -> validation.phrase(request(PhraseKind.PATTERN, List.of(), null, null, null, null)));
        assertThrows(ResponseStatusException.class, () -> validation.phrase(request(PhraseKind.PATTERN, List.of(), List.of(), pattern(), null, null)));
    }
    @Test void particleReferenceAndLocalExplanationAreExclusive() {
        for (var key : List.of(
                new PhraseRequest.LearningKey(null, "usage", null, null, null, null),
                new PhraseRequest.LearningKey(null, "usage", "part-ref", "__TITLE__", "__EXPLANATION__", null))) {
            assertThrows(ResponseStatusException.class, () -> validation.phrase(request(PhraseKind.PATTERN, List.of(), null, pattern(), List.of(key), null)));
        }
        validation.phrase(request(PhraseKind.PATTERN, List.of(), null, pattern(), List.of(
                new PhraseRequest.LearningKey(null, "usage", "part-ref", null, null, null)), null));
    }
    @Test void unknownIconsAndDuplicateChildrenAreRejected() {
        var bad = new PhraseRequest.Example(null, 0L, "__JP__", "__KANA__", "__ROMAJI__", "__ES__", "svg-code", null);
        assertThrows(ResponseStatusException.class, () -> validation.phrase(request(PhraseKind.FIXED, List.of(bad), null, null, null, null)));
        assertThrows(ResponseStatusException.class, () -> validation.phrase(request(PhraseKind.FIXED,
                List.of(example("duplicate", 0), example("duplicate", 1)), null, null, null, null)));
    }
    @Test void unsafeOrderBlankTextAndInvalidUnicodeAreRejected() {
        for (long order : List.of(-1L, 9007199254740992L)) {
            var e = example(null, order);
            assertThrows(ResponseStatusException.class, () -> validation.phrase(request(PhraseKind.FIXED, List.of(e), null, null, null, null)));
        }
        for (String value : List.of("　", "\uFFFD", String.valueOf((char) 0), String.valueOf((char) 0xD800))) {
            assertThrows(ResponseStatusException.class, () -> validation.fields(new ParticleRequest(value, "__TITLE__", "__EXPLANATION__", null)));
        }
    }
    @Test void mapperPreservesPublicAndInternalIdentitiesWhenReorderingChildren() {
        var p = aggregate(request(PhraseKind.FIXED, List.of(example(null, 0), example(null, 1)), null, null, null, null));
        var first = p.getExamples().getFirst(); first.setId(11L);
        var second = p.getExamples().getLast(); second.setId(12L);
        var rootId = p.getPublicId(); var firstId = first.getPublicId();
        mapper.apply(p, request(PhraseKind.FIXED, List.of(example(second.getPublicId(), 0), example(firstId, 1)),
                null, null, null, 3L), List.of(category()), Map.of());
        assertSame(second, p.getExamples().getFirst()); assertSame(first, p.getExamples().getLast());
        assertEquals(11L, first.getId()); assertEquals(firstId, first.getPublicId()); assertEquals(rootId, p.getPublicId());
        assertSame(p, first.getPhrase());
    }
    @Test void foreignChildIdentityCannotBeAdopted() {
        var p = aggregate(fixed(null));
        assertThrows(ResponseStatusException.class, () -> mapper.apply(p, request(PhraseKind.FIXED,
                List.of(example("owned-by-another-root", 0)), null, null, null, null), List.of(category()), Map.of()));
    }
    @Test void patternSlotsAndParticleReferencesHaveCorrectOwnershipAndNoCycleInDto() {
        var particle = new Particle(); particle.setPublicId("part-ref");
        var r = request(PhraseKind.PATTERN, List.of(example(null, 0)), null, pattern(), List.of(
                new PhraseRequest.LearningKey(null, "usage", "part-ref", null, null, null)), null);
        var p = new Phrase(); p.setPublicId("phr-existing");
        mapper.apply(p, r, List.of(category()), Map.of("part-ref", particle));
        assertSame(p, p.getSlots().getFirst().getPhrase());
        assertSame(p, p.getLearningKeys().getFirst().getPhrase());
        assertSame(particle, p.getLearningKeys().getFirst().getParticle());
        String json = JsonMapper.builder().build().writeValueAsString(mapper.response(p));
        assertTrue(json.contains("\"particleId\":\"part-ref\"")); assertFalse(json.contains("\"meanings\""));
        assertFalse(json.contains("\"phrase\":")); assertFalse(json.contains("\"usageNote\""));
    }
    @Test void serviceRejectsUnknownCategoryBeforeWriting() {
        when(categories.findByPublicIdIn(any())).thenReturn(List.of());
        assertThrows(ResponseStatusException.class, () -> service.save(fixed(null)));
        verify(repository, never()).saveAndFlush(any()); verifyNoInteractions(em);
    }
    @Test void serviceRejectsUnknownParticleBeforeWriting() {
        when(categories.findByPublicIdIn(any())).thenReturn(List.of(category()));
        when(particles.findByPublicId("missing")).thenReturn(Optional.empty());
        var r = request(PhraseKind.PATTERN, List.of(), null, pattern(), List.of(
                new PhraseRequest.LearningKey(null, "usage", "missing", null, null, null)), null);
        assertThrows(ResponseStatusException.class, () -> service.save(r));
        verify(repository, never()).saveAndFlush(any());
    }
    @Test void staleVersionDoesNotMutateAggregate() {
        var p = aggregate(fixed(null)); var first = p.getExamples().getFirst();
        when(repository.findById(1L)).thenReturn(Optional.of(p));
        assertThrows(ResponseStatusException.class, () -> service.update(1L, fixed(2L)));
        assertSame(first, p.getExamples().getFirst()); verifyNoInteractions(categories, particles, em);
    }
    @Test void childOnlyUpdateAlsoForcesAggregateVersionAndKeepsRootPublicId() {
        var p = aggregate(fixed(null)); when(repository.findById(1L)).thenReturn(Optional.of(p));
        when(categories.findByPublicIdIn(any())).thenReturn(List.of(category()));
        when(repository.saveAndFlush(p)).thenReturn(p);
        var response = service.update(1L, fixed(3L));
        assertEquals("phr-existing", response.publicId()); verify(em).lock(p, LockModeType.PESSIMISTIC_FORCE_INCREMENT);
    }
    @Test void newIdsAreDistinctOpaqueAndNeverDependOnDatabaseIdentity() {
        var a = ContentPublicIds.create("phr", "__SAME__"); var b = ContentPublicIds.create("phr", "__SAME__");
        assertNotEquals(a, b); assertTrue(a.matches("phr-[a-f0-9]{24}"));
    }
    @Test void removalAndTypeChangeClearOwnedCollectionsWithoutDeletingSharedReferences() {
        var p = aggregate(fixed(null)); var c = category();
        mapper.apply(p, request(PhraseKind.PATTERN, List.of(), null, pattern(), null, 3L), List.of(c), Map.of());
        assertTrue(p.getExamples().isEmpty()); assertTrue(p.getMeanings().isEmpty());
        assertEquals(1, p.getSlots().size()); assertSame(c, p.getCategories().iterator().next());
        assertNotNull(p.getPattern());
    }
    @Test void deleteUsesExistingAggregateAndMissingIdIs404() {
        when(repository.findById(1L)).thenReturn(Optional.empty());
        assertEquals(404, assertThrows(ResponseStatusException.class, () -> service.delete(1L)).getStatusCode().value());
        var p = aggregate(fixed(null)); when(repository.findById(1L)).thenReturn(Optional.of(p));
        service.delete(1L); verify(repository).delete(p);
    }
    @Test void missingOrDuplicateCategoriesAndFourthKeyPointAreRejected() {
        var base = fixed(null);
        for (var ids : List.<List<String>>of(List.of(), List.of("comunicacion-basica", "comunicacion-basica"))) {
            var r = new PhraseRequest(base.kind(), base.displayOrder(), ids, base.japanese(), base.kana(),
                base.romaji(), base.meaning(), base.iconType(), base.introduction(), base.usageNote(),
                base.keyPoints(), base.learningKeys(), base.examples(), base.meanings(), base.pattern(), null);
            assertThrows(ResponseStatusException.class, () -> validation.phrase(r));
        }
        var r = new PhraseRequest(base.kind(), base.displayOrder(), base.categoryIds(), base.japanese(), base.kana(),
            base.romaji(), base.meaning(), base.iconType(), base.introduction(), base.usageNote(),
            List.of("__A__", "__B__", "__C__", "__D__"), base.learningKeys(), base.examples(), base.meanings(), null, null);
        assertThrows(ResponseStatusException.class, () -> validation.phrase(r));
    }
    @Test void auxiliaryExampleRequiresAllFourTexts() {
        var key = new PhraseRequest.LearningKey(null, "usage", null, "__TITLE__", "__EXPLANATION__",
            new PhraseRequest.Text("__JP__", null, "__ROMAJI__", "__ES__"));
        assertThrows(ResponseStatusException.class, () -> validation.phrase(request(PhraseKind.PATTERN,
            List.of(), null, pattern(), List.of(key), null)));
    }
    @Test void jpaOwnershipCascadesOnlyThroughOwnedChildren() throws Exception {
        assertEquals(Object.class, Phrase.class.getSuperclass());
        for (String name : List.of("examples", "meanings", "slots", "learningKeys")) {
            var relation = Phrase.class.getDeclaredField(name).getAnnotation(OneToMany.class);
            assertTrue(relation.orphanRemoval()); assertEquals(List.of(CascadeType.ALL), List.of(relation.cascade()));
            assertEquals("phrase", relation.mappedBy());
        }
        assertEquals(0, Phrase.class.getDeclaredField("categories").getAnnotation(ManyToMany.class).cascade().length);
        assertEquals(0, PhraseLearningKey.class.getDeclaredField("particle").getAnnotation(ManyToOne.class).cascade().length);
    }
}
