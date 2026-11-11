package dev.luisvergara.manabi.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import jakarta.validation.*;
import org.junit.jupiter.api.*;
import org.springframework.web.server.ResponseStatusException;
import dev.luisvergara.manabi.dto.kanjis.*;
import dev.luisvergara.manabi.dto.vocabulary.*;
import dev.luisvergara.manabi.entity.kana.*;
import dev.luisvergara.manabi.entity.kanjis.*;
import dev.luisvergara.manabi.entity.vocabulary.*;
import dev.luisvergara.manabi.enums.kana.*;
import dev.luisvergara.manabi.enums.kanjis.*;
import dev.luisvergara.manabi.repository.*;
import dev.luisvergara.manabi.service.mapper.*;
import jakarta.persistence.EntityManager;

class ContentIdentityTest {
    static ValidatorFactory factory;
    ModelValidation validation = new ModelValidation(factory.getValidator());
    @BeforeAll static void setup() { factory=Validation.buildDefaultValidatorFactory(); }
    @AfterAll static void close() { factory.close(); }
    Kana kana() {
        var k=new Kana(); k.setCharacter("__KA__"); k.setRomaji("__R__");
        k.setType(KanaType.HIRAGANA); k.setGroup(KanaGroup.NORMAL); k.setDisplayOrder(0L); return k;
    }
    @Test void kanaAssignsIdentityOnceAndPreservesItOnEdit() {
        var repo=mock(KanaRepository.class); var service=new KanaService(repo,validation);
        when(repo.saveAndFlush(any())).thenAnswer(x->x.getArgument(0));
        var saved=service.save(kana()); var identity=saved.getPublicId(); saved.setVersion(0L);
        assertTrue(identity.startsWith("kana-")); when(repo.findById(1L)).thenReturn(Optional.of(saved));
        var edit=kana(); edit.setCharacter("__EDIT__"); edit.setPublicId("injected"); edit.setVersion(0L);
        assertEquals(identity,service.update(1L,edit).getPublicId());
    }
    @Test void kanaCreateRejectsVersionAndMissingDeleteIs404() {
        var repo=mock(KanaRepository.class); var service=new KanaService(repo,validation);
        var k=kana(); k.setVersion(3L); assertThrows(ResponseStatusException.class,()->service.save(k));
        assertEquals(404,assertThrows(ResponseStatusException.class,()->service.delete(1L)).getStatusCode().value());
        verify(repo,never()).saveAndFlush(any());
    }
    @Test void ordersAcceptSafeIntegersBeyondInt32AndRejectUnsafeValues() {
        var k=kana(); k.setDisplayOrder(9007199254740991L); validation.valid(k);
        k.setDisplayOrder(9007199254740992L); assertThrows(ResponseStatusException.class,()->validation.valid(k));
        k.setDisplayOrder(-1L); assertThrows(ResponseStatusException.class,()->validation.valid(k));
    }
    @Test void vocabularyAssignsPublicIdentityAndReturnsSeparatePk() {
        var repo=mock(VocabularyRepository.class); var cats=mock(VocabularyCategoryRepository.class);
        var category=new VocabularyCategory(); category.setCode("FOOD");
        when(cats.findByCodeIn(Set.of("FOOD"))).thenReturn(List.of(category));
        when(repo.saveAndFlush(any())).thenAnswer(x->{Vocabulary v=x.getArgument(0);v.setId(9L);return v;});
        var result=new VocabularyService(repo,cats,validation).save(
            new VocabularyRequest("__JP__","__K__","__R__","__ES__",2147483648L,Set.of("FOOD"),null));
        assertEquals(9L,result.id()); assertTrue(result.publicId().startsWith("vocab-"));
        assertEquals(2147483648L,result.displayOrder());
    }
    @Test void kanjiAssignsIdentityAndDoesNotDeriveReadings() {
        var repo=mock(KanjiRepository.class);
        when(repo.saveAndFlush(any())).thenAnswer(x->x.getArgument(0));
        var service=new KanjiService(repo,new KanjiMapper(),validation,mock(EntityManager.class));
        var r=new KanjiRequest("__K__","__ES__",1,JlptLevel.N5,0L,null,null,
            List.of(new KanjiReadingRequest("__READ__",ReadingType.KUNYOMI,"__ROMAJI__",true)),List.of(),null);
        var response=service.save(r); assertTrue(response.publicId().startsWith("kanji-"));
        assertEquals("__ROMAJI__",response.readings().getFirst().romaji());
    }
    @Test void publicIdsAreImmutableMappingsIndependentOfNumericPrimaryKeys() throws Exception {
        for (var c:List.of(Kana.class,Kanji.class,Vocabulary.class)) {
            var col=c.getDeclaredField("publicId").getAnnotation(jakarta.persistence.Column.class);
            assertFalse(col.updatable()); assertTrue(col.unique()); assertFalse(col.nullable());
        }
    }
}
