package dev.luisvergara.manabi.content.publication.contract;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import static dev.luisvergara.manabi.content.publication.contract.PublicFixtures.*;

class PublicContractValidatorTest {
    final PublicContractValidator validator=new PublicContractValidator();
    Map<String,Object> first(Map<String,Object> b){return rows(b.get("records")).getFirst();}
    Map<String,Object> expression(Map<String,Object> b){return rows(b.get("phrases")).getFirst();}
    void invalid(String slot,Consumer<Map<String,Object>> mutation) {
        var source=core();source.putAll(phrases());var b=deepCopy(source.get(slot));mutation.accept(b);
        assertThrows(ContentContractException.class,()->validator.validateBody(slot,b),slot+" "+b.keySet());
    }
    @Test void allExistingCoreAndTechnicalPhraseContractsAreStructurallyValid() {
        var source=core();source.putAll(phrases());validator.validateRelations(source);
        for(var e:source.entrySet())assertTrue(validator.validateBody(e.getKey(),e.getValue())>=0);
    }
    @Test void kanaRequiresClosedFieldsValidTypesGroupsNfcIdentitiesAndOrder() {
        invalid("kana",b->first(b).remove("character"));
        invalid("kana",b->first(b).put("id",9999999));
        invalid("kana",b->first(b).put("group",null));
        invalid("kana",b->first(b).put("type","OTHER"));
        invalid("kana",b->first(b).put("group","EXTENDED"));
        invalid("kana",b->first(b).put("character","か\u3099"));
        invalid("kana",b->rows(b.get("records")).get(1).put("editorialId",first(b).get("editorialId")));
        invalid("kana",b->Collections.reverse(rows(b.get("records"))));
        invalid("kana",b->b.put("records",List.of()));
        invalid("kana",b->b.put("records",Collections.nCopies(100001,first(b))));
    }
    @Test void unicodeUsesCodePointsNotUtf16AndJavascriptTrimWithoutChangingTexts() {
        String astral=new String(Character.toChars(0x20000));var b=deepCopy(core().get("kana"));
        first(b).put("character",astral.repeat(32));validator.validateBody("kana",b);
        invalid("kana",x->first(x).put("character",astral.repeat(33)));
        for(String bad:List.of("\u00a0\ufeff",String.valueOf((char)0),"\ufffd",String.valueOf((char)0xd800)))
            invalid("kana",x->first(x).put("romaji",bad));
        first(b).put("romaji","\u0085");assertDoesNotThrow(()->validator.validateBody("kana",b));
    }
    @Test void unsafeIntegersNullAndAdministrativeFieldsAreRejected() {
        for(Object n:Arrays.asList(-1,9007199254740992L,1.5d,"1",null))
            invalid("kana",b->first(b).put("displayOrder",n));
        for(String field:List.of("version","review","reviewedAt","publicId"))
            invalid("vocabulary",b->first(b).put(field,1));
    }
    @Test void kanjiKeepsExplicitNullableFieldsAndNeverInfersPrincipalOrExamples() {
        invalid("kanji-n5",b->first(b).remove("radicalName"));
        invalid("kanji-n5",b->{first(b).put("radicalCharacter",null);first(b).put("radicalName","__FIXTURE__");});
        invalid("kanji-n5",b->rows(first(b).get("readings")).forEach(r->r.put("primary",false)));
        invalid("kanji-n5",b->rows(first(b).get("readings")).forEach(r->{r.put("primary",true);r.put("romaji","__FIXTURE__");}));
        invalid("kanji-n5",b->rows(first(b).get("readings")).getFirst().put("romaji",null));
        invalid("kanji-n5",b->rows(first(b).get("readings")).getFirst().put("primary","true"));
        invalid("kanji-n5",b->rows(first(b).get("readings")).getFirst().put("readingType",null));
        invalid("kanji-n5",b->first(b).put("examples",List.of()));
        invalid("kanji-n5",b->first(b).put("jlptLevel","N4"));
        invalid("kanji-n5",b->rows(first(b).get("readings")).add(deepCopy(rows(first(b).get("readings")).getFirst())));
    }
    @Test void vocabularyProfileMembershipsAndNullableSymbolsRemainExact() {
        invalid("vocabulary",b->first(b).put("categories",List.of("ABSENT")));
        invalid("vocabulary",b->first(b).put("categories",List.of("FOOD","FOOD")));
        invalid("vocabulary",b->rows(b.get("categories")).getFirst().put("symbol",null));
        invalid("vocabulary",b->rows(b.get("categories")).getLast().remove("symbol"));
        invalid("vocabulary",b->obj(b.get("categoryMappings")).put("colors","FOOD"));
        invalid("vocabulary",b->rows(b.get("categories")).getLast().put("showInCatalog",true));
        invalid("vocabulary",b->first(b).put("jlptLevel","N5"));
        var b=deepCopy(core().get("vocabulary"));var second=rows(b.get("records")).get(1);
        second.put("japanese",first(b).get("japanese"));assertDoesNotThrow(()->validator.validateBody("vocabulary",b));
    }
    @Test void phrasesRejectInvalidKindRouteNullFieldsLimitsAndIncompatibleFields() {
        invalid("phrases-fixed",b->expression(b).put("kind","PATTERN"));
        invalid("phrases-fixed",b->expression(b).put("pattern",Map.of()));
        invalid("phrases-pattern",b->expression(b).remove("pattern"));
        invalid("phrases-pattern",b->expression(b).put("meanings",List.of()));
        for(String id:List.of(".","..","a/b","a\\b","a b","%2e","a?b","a#b"))
            invalid("phrases-fixed",b->expression(b).put("id",id));
        invalid("phrases-fixed",b->expression(b).put("usageNote",null));
        invalid("phrases-fixed",b->expression(b).put("keyPoints",List.of("a","b","c","d")));
        invalid("phrases-fixed",b->expression(b).put("examples",Collections.nCopies(5,rows(expression(b).get("examples")).getFirst())));
        invalid("phrases-fixed",b->expression(b).put("iconType","<svg>"));
        invalid("phrases-pattern",b->obj(expression(b).get("pattern")).put("slots",List.of(Map.of("id","a","label","a"))));
    }
    @Test void keysAreExclusiveAndAuxiliaryExampleDoesNotConsumeCentralLimit() {
        var b=deepCopy(phrases().get("phrases-pattern"));var p=expression(b);
        assertEquals(4,rows(p.get("examples")).size());assertEquals(3,rows(p.get("learningKeys")).size());
        validator.validateBody("phrases-pattern",b);
        invalid("phrases-pattern",x->rows(expression(x).get("learningKeys")).getFirst().put("title","override"));
        invalid("phrases-pattern",x->rows(expression(x).get("learningKeys")).get(1).remove("explanation"));
        invalid("phrases-pattern",x->rows(expression(x).get("learningKeys")).get(1).put("example",Map.of("japanese","__FIXTURE__")));
        invalid("phrases-pattern",x->expression(x).put("learningKeys",Collections.nCopies(4,rows(expression(x).get("learningKeys")).getFirst())));
    }
    @Test void referencesAndGlobalIdentitiesAreValidatedAcrossTheWholeSet() {
        var bodies=phrases();bodies.remove("particles");var missingFile=bodies;assertThrows(ContentContractException.class,()->validator.validateRelations(missingFile));
        bodies=phrases();expression(bodies.get("phrases-fixed")).put("categoryIds",List.of("absent"));
        var missingCategory=bodies;assertThrows(ContentContractException.class,()->validator.validateRelations(missingCategory));
        bodies=phrases();rows(expression(bodies.get("phrases-pattern")).get("learningKeys")).getFirst().put("particleId","absent");
        var missingParticle=bodies;assertThrows(ContentContractException.class,()->validator.validateRelations(missingParticle));
        bodies=phrases();var id=rows(expression(bodies.get("phrases-fixed")).get("examples")).getFirst().get("id");
        rows(expression(bodies.get("phrases-pattern")).get("examples")).getFirst().put("id",id);
        var duplicatedExample=bodies;assertThrows(ContentContractException.class,()->validator.validateRelations(duplicatedExample));
        bodies=core();first(bodies.get("kanji-n4")).put("editorialId",first(bodies.get("kanji-n5")).get("editorialId"));
        var duplicatedKanji=bodies;assertThrows(ContentContractException.class,()->validator.validateRelations(duplicatedKanji));
        bodies=core();first(bodies.get("kanji-n4")).put("character",first(bodies.get("kanji-n5")).get("character"));
        var duplicatedWriting=bodies;assertThrows(ContentContractException.class,()->validator.validateRelations(duplicatedWriting));
    }
    @Test void structuralConformanceNeverMeansEditorialApprovalOrFixturePermission() {
        var bodies=phrases();
        assertDoesNotThrow(()->validator.validateRelations(bodies));
        assertThrows(ContentContractException.class,()->validator.rejectTechnicalFixtures(NS,bodies.get("phrases-fixed")));
        assertThrows(ContentContractException.class,()->validator.rejectTechnicalFixtures("fixture.local",Map.of()));
        assertThrows(ContentContractException.class,()->validator.rejectTechnicalFixtures(NS,Map.of("text","__FIXTURE_ONLY__")));
    }
}
