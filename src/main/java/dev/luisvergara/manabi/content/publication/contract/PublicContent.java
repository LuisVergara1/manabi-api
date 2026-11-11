package dev.luisvergara.manabi.content.publication.contract;

import java.util.*;

/** Explicit public vocabulary. No entity, SQL key, optimistic version or editorial metadata. */
public final class PublicContent {
    private PublicContent() {}
    public interface Value { Map<String,Object> json(); }
    public sealed interface Package extends Value permits KanaPackage,KanjiPackage,VocabularyPackage,
            PhraseCategoriesPackage,FixedPhrasesPackage,PatternPhrasesPackage,ParticlesPackage {
        String slot();
    }
    static Map<String,Object> object(Object... fields) {
        var out=new LinkedHashMap<String,Object>();
        for(int i=0;i<fields.length;i+=2)out.put((String)fields[i],fields[i+1]);
        return out;
    }
    static Object values(List<? extends Value> list) {
        return list==null?null:list.stream().map(v->v==null?null:v.json()).toList();
    }
    static void optional(Map<String,Object> out,String key,Object value) { if(value!=null)out.put(key,value); }
    static <T> List<T> copy(List<T> list) { return list==null?null:Collections.unmodifiableList(new ArrayList<>(list)); }
    static <K,V> Map<K,V> copy(Map<K,V> map) { return map==null?null:Collections.unmodifiableMap(new LinkedHashMap<>(map)); }

    public record KanaEntry(String editorialId,Long displayOrder,String character,String romaji,String type,String group) implements Value {
        public Map<String,Object> json(){return object("editorialId",editorialId,"displayOrder",displayOrder,"character",character,"romaji",romaji,"type",type,"group",group);}
    }
    public record Reading(String reading,String readingType,String romaji,Boolean primary) implements Value {
        public Map<String,Object> json(){return object("reading",reading,"readingType",readingType,"romaji",romaji,"primary",primary);}
    }
    public record KanjiEntry(String editorialId,Long displayOrder,String character,String meaning,Long strokeCount,
            String jlptLevel,String radicalCharacter,String radicalName,List<Reading> readings) implements Value {
        public KanjiEntry { readings=copy(readings); }
        public Map<String,Object> json(){return object("editorialId",editorialId,"displayOrder",displayOrder,"character",character,
            "meaning",meaning,"strokeCount",strokeCount,"jlptLevel",jlptLevel,"radicalCharacter",radicalCharacter,"radicalName",radicalName,"readings",values(readings));}
    }
    public record VocabularyEntry(String editorialId,Long displayOrder,String japanese,String kana,String romaji,String meaning,List<String> categories) implements Value {
        public VocabularyEntry { categories=copy(categories); }
        public Map<String,Object> json(){return object("editorialId",editorialId,"displayOrder",displayOrder,"japanese",japanese,"kana",kana,"romaji",romaji,"meaning",meaning,"categories",categories);}
    }
    public record VocabularyCategory(String code,String name,String symbol,Long displayOrder,Boolean showInCatalog) implements Value {
        public Map<String,Object> json(){return object("code",code,"name",name,"symbol",symbol,"displayOrder",displayOrder,"showInCatalog",showInCatalog);}
    }
    public record KanaPackage(String namespace,List<KanaEntry> records) implements Package {
        public KanaPackage { records=copy(records); }
        public String slot(){return "kana";}
        public Map<String,Object> json(){return object("packageId",slot(),"entityType","KANA","namespace",namespace,"schemaVersion",1,"records",values(records));}
    }
    public record KanjiPackage(String namespace,String jlptLevel,List<KanjiEntry> records) implements Package {
        public KanjiPackage { records=copy(records); }
        public String slot(){return "kanji-"+String.valueOf(jlptLevel).toLowerCase(Locale.ROOT);}
        public Map<String,Object> json(){return object("packageId",slot(),"entityType","KANJI","namespace",namespace,"schemaVersion",1,"jlptLevel",jlptLevel,"records",values(records));}
    }
    public record VocabularyPackage(String namespace,List<VocabularyEntry> records,List<VocabularyCategory> categories,Map<String,String> categoryMappings) implements Package {
        public VocabularyPackage { records=copy(records);categories=copy(categories);categoryMappings=copy(categoryMappings); }
        public String slot(){return "vocabulary";}
        public Map<String,Object> json(){return object("packageId",slot(),"entityType","VOCABULARY","namespace",namespace,"schemaVersion",1,
            "records",values(records),"categories",values(categories),"categoryMappings",categoryMappings);}
    }
    public record PhraseCategory(String id,String name,String description,Long displayOrder,Boolean enabled) implements Value {
        public Map<String,Object> json(){return object("id",id,"name",name,"description",description,"displayOrder",displayOrder,"enabled",enabled);}
    }
    public record Text(String japanese,String kana,String romaji,String meaning) implements Value {
        public Map<String,Object> json(){return object("japanese",japanese,"kana",kana,"romaji",romaji,"meaning",meaning);}
    }
    public record Example(String id,Long displayOrder,String japanese,String kana,String romaji,String meaning,String iconType,String situation) implements Value {
        public Map<String,Object> json(){var out=object("id",id,"displayOrder",displayOrder,"japanese",japanese,"kana",kana,"romaji",romaji,"meaning",meaning,"iconType",iconType);optional(out,"situation",situation);return out;}
    }
    public record Meaning(String id,String label,String explanation,String iconType) implements Value {
        public Map<String,Object> json(){return object("id",id,"label",label,"explanation",explanation,"iconType",iconType);}
    }
    public record Slot(String id,String label,String description) implements Value {
        public Map<String,Object> json(){return object("id",id,"label",label,"description",description);}
    }
    public record Pattern(String formula,String explanation,List<Slot> slots) implements Value {
        public Pattern { slots=copy(slots); }
        public Map<String,Object> json(){return object("formula",formula,"explanation",explanation,"slots",values(slots));}
    }
    public record LearningKey(String id,String iconType,String particleId,String title,String explanation,Text example) implements Value {
        public Map<String,Object> json(){var out=object("id",id,"iconType",iconType);optional(out,"particleId",particleId);
            optional(out,"title",title);optional(out,"explanation",explanation);optional(out,"example",example==null?null:example.json());return out;}
    }
    public record PhraseEntry(String id,String kind,Long displayOrder,List<String> categoryIds,String japanese,String kana,
            String romaji,String meaning,String iconType,String introduction,List<Example> examples,String usageNote,
            List<String> keyPoints,List<LearningKey> learningKeys,List<Meaning> meanings,Pattern pattern) implements Value {
        public PhraseEntry { categoryIds=copy(categoryIds);examples=copy(examples);keyPoints=copy(keyPoints);learningKeys=copy(learningKeys);meanings=copy(meanings); }
        public Map<String,Object> json(){
            var out=object("id",id,"kind",kind,"displayOrder",displayOrder,"categoryIds",categoryIds,
                "japanese",japanese,"kana",kana,"romaji",romaji,"meaning",meaning,"iconType",iconType,"introduction",introduction,"examples",values(examples));
            optional(out,"usageNote",usageNote);optional(out,"keyPoints",keyPoints);optional(out,"learningKeys",values(learningKeys));
            optional(out,"meanings",values(meanings));optional(out,"pattern",pattern==null?null:pattern.json());return out;
        }
    }
    public record Particle(String id,String japanese,String title,String explanation) implements Value {
        public Map<String,Object> json(){return object("id",id,"japanese",japanese,"title",title,"explanation",explanation);}
    }
    public record PhraseCategoriesPackage(List<PhraseCategory> categories) implements Package {
        public PhraseCategoriesPackage { categories=copy(categories); }
        public String slot(){return "phrase-categories";}
        public Map<String,Object> json(){return object("categories",values(categories));}
    }
    public record FixedPhrasesPackage(List<PhraseEntry> phrases) implements Package {
        public FixedPhrasesPackage { phrases=copy(phrases); }
        public String slot(){return "phrases-fixed";}
        public Map<String,Object> json(){return object("phrases",values(phrases));}
    }
    public record PatternPhrasesPackage(List<PhraseEntry> phrases) implements Package {
        public PatternPhrasesPackage { phrases=copy(phrases); }
        public String slot(){return "phrases-pattern";}
        public Map<String,Object> json(){return object("phrases",values(phrases));}
    }
    public record ParticlesPackage(List<Particle> particles) implements Package {
        public ParticlesPackage { particles=copy(particles); }
        public String slot(){return "particles";}
        public Map<String,Object> json(){return object("particles",values(particles));}
    }
}
