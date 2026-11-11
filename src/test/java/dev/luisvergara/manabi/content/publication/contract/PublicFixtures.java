package dev.luisvergara.manabi.content.publication.contract;

import java.util.*;
import java.io.*;
import tools.jackson.databind.json.JsonMapper;
import dev.luisvergara.manabi.entity.kana.Kana;
import dev.luisvergara.manabi.entity.kanjis.*;
import dev.luisvergara.manabi.entity.vocabulary.Vocabulary;
import dev.luisvergara.manabi.entity.phrase.*;
import dev.luisvergara.manabi.enums.kana.*;
import dev.luisvergara.manabi.enums.kanjis.*;
import dev.luisvergara.manabi.enums.phrase.PhraseKind;
import static dev.luisvergara.manabi.content.publication.contract.PublicContent.*;

/** TEST ONLY. Exact copied frontend goldens, no database loader and no publishable data source. */
final class PublicFixtures {
    static final String NS="manabi.frontend.v1";
    static final CanonicalJson JSON=new CanonicalJson();
    static final JsonMapper MAPPER=JsonMapper.builder().build();
    static byte[] resource(String name) {
        try(var in=PublicFixtures.class.getResourceAsStream("/public-contract-v1/"+name)) {
            if(in==null)throw new IllegalStateException(name);
            return in.readAllBytes();
        }catch(IOException e){throw new UncheckedIOException(e);}
    }
    @SuppressWarnings("unchecked") static Map<String,Object> obj(Object v){return (Map<String,Object>)v;}
    @SuppressWarnings("unchecked") static List<Map<String,Object>> rows(Object v){return (List<Map<String,Object>>)v;}
    @SuppressWarnings("unchecked") static List<String> strings(Object v){return (List<String>)v;}
    static Map<String,Object> tree(String resource){return obj(JSON.parse(resource(resource)));}
    static Map<String,Object> deepCopy(Map<String,Object> v){return obj(JSON.parse(JSON.bytes(v)));}
    static long number(Object n){return ((Number)n).longValue();}
    static String s(Map<String,Object> m,String key){return (String)m.get(key);}
    static Map<String,Map<String,Object>> core() {
        var out=new LinkedHashMap<String,Map<String,Object>>();
        for(var d:rows(tree("core/manifest.json").get("packages")))out.put(s(d,"packageId"),tree("core/"+s(d,"url")));
        return out;
    }
    static Map<String,Map<String,Object>> phrases() {
        var out=new LinkedHashMap<String,Map<String,Object>>();
        String[] names={"categories.json","phrases-fixed.json","phrases-pattern.json","particles.json"};
        for(int i=0;i<names.length;i++)out.put(PublicProtocol.PHRASE_SLOTS.get(i),tree("technical-phrases/"+names[i]));
        return out;
    }
    static <T> List<T> dto(Object value,Class<T> type) {
        return rows(value).stream().map(r->MAPPER.convertValue(r,type)).toList();
    }
    @SuppressWarnings("unchecked") static PublicContent.Package projection(String slot,Map<String,Object> b) {
        return switch(slot) {
            case "kana" -> new KanaPackage(s(b,"namespace"),dto(b.get("records"),KanaEntry.class));
            case "vocabulary" -> new VocabularyPackage(s(b,"namespace"),dto(b.get("records"),VocabularyEntry.class),
                dto(b.get("categories"),PublicContent.VocabularyCategory.class),(Map<String,String>)(Map<?,?>)b.get("categoryMappings"));
            case "phrase-categories" -> new PhraseCategoriesPackage(dto(b.get("categories"),PublicContent.PhraseCategory.class));
            case "phrases-fixed" -> new FixedPhrasesPackage(dto(b.get("phrases"),PhraseEntry.class));
            case "phrases-pattern" -> new PatternPhrasesPackage(dto(b.get("phrases"),PhraseEntry.class));
            case "particles" -> new ParticlesPackage(dto(b.get("particles"),PublicContent.Particle.class));
            default -> new KanjiPackage(s(b,"namespace"),s(b,"jlptLevel"),dto(b.get("records"),KanjiEntry.class));
        };
    }
    static List<Kana> kana() {
        return rows(core().get("kana").get("records")).stream().map(r->{
            var k=new Kana();k.setId(9999999L);k.setVersion(9999999L);k.setPublicId(s(r,"editorialId"));
            k.setCharacter(s(r,"character"));k.setRomaji(s(r,"romaji"));k.setType(KanaType.valueOf(s(r,"type")));
            k.setGroup(KanaGroup.valueOf(s(r,"group")));k.setDisplayOrder(number(r.get("displayOrder")));return k;
        }).collect(java.util.stream.Collectors.toCollection(ArrayList::new));
    }
    static List<Kanji> kanji(String slot) {
        return rows(core().get(slot).get("records")).stream().map(r->{
            var k=new Kanji();k.setId(9999999L);k.setVersion(9999999L);k.setPublicId(s(r,"editorialId"));
            k.setCharacter(s(r,"character"));k.setMeaning(s(r,"meaning"));k.setStrokeCount(((Number)r.get("strokeCount")).intValue());
            k.setJlptLevel(JlptLevel.valueOf(s(r,"jlptLevel")));k.setDisplayOrder(number(r.get("displayOrder")));
            k.setRadicalCharacter(s(r,"radicalCharacter"));k.setRadicalName(s(r,"radicalName"));
            for(var x:rows(r.get("readings"))) {
                var a=new KanjiReading();a.setId(9999999L);a.setReading(s(x,"reading"));a.setReadingType(ReadingType.valueOf(s(x,"readingType")));
                a.setRomaji(s(x,"romaji"));a.setPrimary((Boolean)x.get("primary"));k.addReading(a);
            }
            var privateExample=new KanjiExample();privateExample.setJapanese("__FIXTURE_NOT_PUBLIC__");k.addExample(privateExample);
            return k;
        }).collect(java.util.stream.Collectors.toCollection(ArrayList::new));
    }
    static List<dev.luisvergara.manabi.entity.vocabulary.VocabularyCategory> vocabularyCategories(){
        return rows(core().get("vocabulary").get("categories")).stream().map(r->{
            var c=new dev.luisvergara.manabi.entity.vocabulary.VocabularyCategory();c.setId(9999999L);c.setVersion(9999999L);
            c.setCode(s(r,"code"));c.setName(s(r,"name"));c.setSymbol(s(r,"symbol"));c.setDisplayOrder(number(r.get("displayOrder")));
            c.setShowInCatalog((Boolean)r.get("showInCatalog"));return c;
        }).collect(java.util.stream.Collectors.toCollection(ArrayList::new));
    }
    static List<Vocabulary> vocabulary(){
        var cats=new HashMap<String,dev.luisvergara.manabi.entity.vocabulary.VocabularyCategory>();
        vocabularyCategories().forEach(c->cats.put(c.getCode(),c));
        return rows(core().get("vocabulary").get("records")).stream().map(r->{
            var v=new Vocabulary();v.setId(9999999L);v.setVersion(9999999L);v.setPublicId(s(r,"editorialId"));
            v.setDisplayOrder(number(r.get("displayOrder")));v.setJapanese(s(r,"japanese"));v.setKana(s(r,"kana"));
            v.setRomaji(s(r,"romaji"));v.setMeaning(s(r,"meaning"));v.setCategories(new HashSet<>(strings(r.get("categories")).stream().map(cats::get).toList()));
            return v;
        }).collect(java.util.stream.Collectors.toCollection(ArrayList::new));
    }
    static List<dev.luisvergara.manabi.entity.phrase.PhraseCategory> phraseCategories(){
        return rows(phrases().get("phrase-categories").get("categories")).stream().map(r->{
            var c=new dev.luisvergara.manabi.entity.phrase.PhraseCategory();c.setId(9999999L);c.setVersion(9999999L);c.setPublicId(s(r,"id"));
            c.setName(s(r,"name"));c.setDescription(s(r,"description"));c.setDisplayOrder(number(r.get("displayOrder")));c.setEnabled((Boolean)r.get("enabled"));return c;
        }).collect(java.util.stream.Collectors.toCollection(ArrayList::new));
    }
    static List<dev.luisvergara.manabi.entity.phrase.Particle> particles(){
        return rows(phrases().get("particles").get("particles")).stream().map(r->{
            var p=new dev.luisvergara.manabi.entity.phrase.Particle();p.setId(9999999L);p.setVersion(9999999L);p.setPublicId(s(r,"id"));
            p.setJapanese(s(r,"japanese"));p.setTitle(s(r,"title"));p.setExplanation(s(r,"explanation"));return p;
        }).collect(java.util.stream.Collectors.toCollection(ArrayList::new));
    }
    static List<Phrase> expressions(String slot) {
        var cats=new HashMap<String,dev.luisvergara.manabi.entity.phrase.PhraseCategory>();phraseCategories().forEach(c->cats.put(c.getPublicId(),c));
        var particles=new HashMap<String,dev.luisvergara.manabi.entity.phrase.Particle>();particles().forEach(p->particles.put(p.getPublicId(),p));
        return rows(phrases().get(slot).get("phrases")).stream().map(r->{
            var p=new Phrase();p.setId(9999999L);p.setVersion(9999999L);p.setPublicId(s(r,"id"));p.setKind(PhraseKind.valueOf(s(r,"kind")));
            p.setDisplayOrder(number(r.get("displayOrder")));p.setJapanese(s(r,"japanese"));p.setKana(s(r,"kana"));p.setRomaji(s(r,"romaji"));
            p.setMeaning(s(r,"meaning"));p.setIconType(s(r,"iconType"));p.setIntroduction(s(r,"introduction"));p.setUsageNote(s(r,"usageNote"));
            strings(r.get("categoryIds")).forEach(id->p.getCategories().add(cats.get(id)));
            p.getKeyPoints().addAll(strings(r.getOrDefault("keyPoints",List.of())));
            for(var e:rows(r.get("examples"))) {
                var x=new PhraseExample();x.setId(9999999L);x.setPhrase(p);x.setPublicId(s(e,"id"));x.setDisplayOrder(number(e.get("displayOrder")));
                x.setJapanese(s(e,"japanese"));x.setKana(s(e,"kana"));x.setRomaji(s(e,"romaji"));x.setMeaning(s(e,"meaning"));x.setIconType(s(e,"iconType"));x.setSituation(s(e,"situation"));p.getExamples().add(x);
            }
            int pos=0;for(var m:rows(r.getOrDefault("meanings",List.of()))) {
                var x=new PhraseMeaning();x.setId(9999999L);x.setPhrase(p);x.setPublicId(s(m,"id"));x.setPosition(pos++);x.setLabel(s(m,"label"));x.setExplanation(s(m,"explanation"));x.setIconType(s(m,"iconType"));p.getMeanings().add(x);
            }
            if(r.containsKey("pattern")) {
                var pattern=obj(r.get("pattern"));p.setPattern(new PhrasePattern(s(pattern,"formula"),s(pattern,"explanation")));
                pos=0;for(var slotRow:rows(pattern.get("slots"))) {
                    var x=new PhraseSlot();x.setId(9999999L);x.setPhrase(p);x.setPublicId(s(slotRow,"id"));x.setPosition(pos++);x.setLabel(s(slotRow,"label"));x.setDescription(s(slotRow,"description"));p.getSlots().add(x);
                }
            }
            pos=0;for(var k:rows(r.getOrDefault("learningKeys",List.of()))) {
                var x=new PhraseLearningKey();x.setId(9999999L);x.setPhrase(p);x.setPublicId(s(k,"id"));x.setPosition(pos++);x.setIconType(s(k,"iconType"));
                x.setTitle(s(k,"title"));x.setExplanation(s(k,"explanation"));x.setParticle(particles.get(s(k,"particleId")));
                if(k.containsKey("example")) {var a=obj(k.get("example"));x.setExample(new PhraseAuxiliaryExample(s(a,"japanese"),s(a,"kana"),s(a,"romaji"),s(a,"meaning")));}
                p.getLearningKeys().add(x);
            }
            return p;
        }).collect(java.util.stream.Collectors.toCollection(ArrayList::new));
    }
}
