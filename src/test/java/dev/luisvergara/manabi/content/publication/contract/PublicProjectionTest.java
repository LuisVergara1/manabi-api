package dev.luisvergara.manabi.content.publication.contract;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static dev.luisvergara.manabi.content.publication.contract.PublicFixtures.*;

class PublicProjectionTest {
    final PublicContentProjector projector=new PublicContentProjector();
    final PublicPackageCodec codec=new PublicPackageCodec();
    @Test void kanaAndVocabularyAreIdenticalToPublishedGoldensRegardlessOfSourceOrder() {
        var kana=kana();var words=vocabulary();var cats=vocabularyCategories();
        var core=core();
        for(int i=0;i<8;i++) {
            Collections.shuffle(kana,new Random(i));Collections.shuffle(words,new Random(i));Collections.shuffle(cats,new Random(i));
            assertArrayEquals(JSON.bytes(core.get("kana")),codec.encode(NS,projector.kana(NS,kana)).bytes());
            assertArrayEquals(JSON.bytes(core.get("vocabulary")),codec.encode(NS,projector.vocabulary(NS,words,cats)).bytes());
        }
    }
    @Test void kanjiReadingsAreDeterministicAndAllCurrentPackageBytesArePreserved() {
        for(String slot:List.of("kanji-n5","kanji-n4")) {
            var values=kanji(slot);
            for(int i=0;i<8;i++) {
                Collections.shuffle(values,new Random(i));for(var k:values)Collections.shuffle(k.getReadings(),new Random(i+8));
                byte[] actual=codec.encode(NS,projector.kanji(NS,slot.substring(6).toUpperCase(Locale.ROOT),values)).bytes();
                assertArrayEquals(JSON.bytes(core().get(slot)),actual);
                assertFalse(new String(actual,java.nio.charset.StandardCharsets.UTF_8).contains("__FIXTURE_NOT_PUBLIC__"));
            }
        }
    }
    @Test void fixedAndPatternPreserveEducationalFieldsAndPositionedChildren() {
        var fixed=expressions("phrases-fixed");var patterns=expressions("phrases-pattern");
        byte[] a=codec.encode(NS,projector.fixed(fixed)).bytes(),b=codec.encode(NS,projector.patterns(patterns)).bytes();
        for(int i=0;i<8;i++) {
            for(var p:fixed){Collections.shuffle(p.getExamples(),new Random(i));Collections.shuffle(p.getMeanings(),new Random(i));}
            for(var p:patterns){Collections.shuffle(p.getExamples(),new Random(i));Collections.shuffle(p.getSlots(),new Random(i));Collections.shuffle(p.getLearningKeys(),new Random(i));}
            assertArrayEquals(a,codec.encode(NS,projector.fixed(fixed)).bytes());
            assertArrayEquals(b,codec.encode(NS,projector.patterns(patterns)).bytes());
        }
        var fixedBody=obj(JSON.parse(a));var patternBody=obj(JSON.parse(b));
        assertEquals(4,rows(rows(fixedBody.get("phrases")).getFirst().get("examples")).size());
        assertEquals(4,rows(rows(patternBody.get("phrases")).getFirst().get("examples")).size());
        assertTrue(rows(rows(patternBody.get("phrases")).getFirst().get("learningKeys")).stream().anyMatch(k->k.containsKey("example")));
        assertFalse(rows(patternBody.get("phrases")).getFirst().containsKey("meanings"));
        assertFalse(rows(fixedBody.get("phrases")).getFirst().containsKey("pattern"));
        assertEquals(rows(phrases().get("phrases-pattern").get("phrases")).getFirst().get("pattern"),
            rows(patternBody.get("phrases")).getFirst().get("pattern"));
    }
    @Test void categoriesAndParticlesDoNotDependOnCollectionIterationOrder() {
        var categories=phraseCategories();var particles=particles();
        byte[] a=codec.encode(NS,projector.phraseCategories(categories)).bytes(),b=codec.encode(NS,projector.particles(particles)).bytes();
        Collections.reverse(categories);Collections.reverse(particles);
        assertArrayEquals(a,codec.encode(NS,projector.phraseCategories(categories)).bytes());
        assertArrayEquals(b,codec.encode(NS,projector.particles(particles)).bytes());
    }
    @Test void duplicatePositionsAndOrphanSlotsAreRejectedWithoutRenumbering() {
        var fixed=expressions("phrases-fixed");fixed.getFirst().getMeanings().get(1).setPosition(0);
        assertThrows(ContentContractException.class,()->projector.fixed(fixed));
        var patterns=expressions("phrases-pattern");patterns.getFirst().setPattern(null);
        assertThrows(ContentContractException.class,()->projector.patterns(patterns));
    }
    @Test void outputNeverContainsNumericIdsJpaVersionsOrPrivatePositionFields() {
        var values=List.of(projector.kana(NS,kana()),projector.kanji(NS,"N5",kanji("kanji-n5")),
            projector.vocabulary(NS,vocabulary(),vocabularyCategories()),projector.fixed(expressions("phrases-fixed")),
            projector.patterns(expressions("phrases-pattern")),projector.phraseCategories(phraseCategories()),projector.particles(particles()));
        for(var value:values) {
            byte[] bytes=codec.encode(NS,value).bytes();
            String text=new String(bytes,java.nio.charset.StandardCharsets.UTF_8);
            assertFalse(text.contains("9999999"));assertFalse(text.contains("\"version\""));assertFalse(text.contains("\"position\""));assertFalse(text.contains("\"publicId\""));
        }
        for(var type:PublicContent.class.getDeclaredClasses())if(type.isRecord())for(var c:type.getRecordComponents()) {
            assertFalse(Set.of("publicId","version","position","review","reviewedAt").contains(c.getName()));
            if(c.getName().equals("id"))assertEquals(String.class,c.getType(),"public IDs are strings");
        }
    }
    @Test void projectionDoesNotModifyTheEntitiesOrGenerateTheirIdentities() {
        var input=kana();input.getFirst().setPublicId(null);
        projector.kana(NS,input);
        assertNull(input.getFirst().getPublicId());assertEquals(9999999L,input.getFirst().getId());
        assertThrows(ContentContractException.class,()->codec.encode(NS,projector.kana(NS,input)));
    }
}
