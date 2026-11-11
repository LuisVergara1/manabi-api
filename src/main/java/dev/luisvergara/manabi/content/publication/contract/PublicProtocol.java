package dev.luisvergara.manabi.content.publication.contract;
import java.util.*;

public final class PublicProtocol {
    private PublicProtocol() {}
    public static final List<String> PHRASE_SLOTS=List.of("phrase-categories","phrases-fixed","phrases-pattern","particles");
    public static final List<String> LEVELS=List.of("N5","N4","N3","N2","N1");
    public static final Map<String,String> VOCABULARY_MAPPINGS=Map.ofEntries(
        Map.entry("clothes","CLOTHES"),Map.entry("colors","COLOR"),Map.entry("numbers","NUMBERS"),
        Map.entry("greetings","GREETINGS"),Map.entry("family","FAMILY"),Map.entry("animals","ANIMAL"),
        Map.entry("food","FOOD"),Map.entry("drinks","DRINKS"),Map.entry("transport","TRANSPORT"),
        Map.entry("home","HOME"),Map.entry("nature","NATURE"),Map.entry("weather","WEATHER"),
        Map.entry("time","TIME"),Map.entry("places","PLACE"),Map.entry("school","SCHOOL"),
        Map.entry("work","WORK"),Map.entry("verbs","VERBS"),Map.entry("adjectives","ADJECTIVES"));
    public record Descriptor(String packageId,String entityType,String namespace,int schemaVersion,int minimumReaderVersion,
            String contentVersion,String url,String sha256,long byteLength,long recordCount,List<String> dependencies,String jlptLevel) implements PublicContent.Value {
        public Descriptor { dependencies=PublicContent.copy(dependencies); }
        public Map<String,Object> json(){
            var out=PublicContent.object("packageId",packageId,"entityType",entityType,"namespace",namespace,
                "schemaVersion",schemaVersion,"minimumReaderVersion",minimumReaderVersion,"contentVersion",contentVersion,
                "url",url,"sha256",sha256,"byteLength",byteLength,"recordCount",recordCount,"dependencies",dependencies);
            PublicContent.optional(out,"jlptLevel",jlptLevel);return out;
        }
    }
    public record Manifest(int manifestFormatVersion,int minimumReaderVersion,String releaseId,List<Descriptor> packages) implements PublicContent.Value {
        public Manifest { packages=PublicContent.copy(packages); }
        public Map<String,Object> json(){return PublicContent.object("manifestFormatVersion",manifestFormatVersion,
            "minimumReaderVersion",minimumReaderVersion,"releaseId",releaseId,"packages",PublicContent.values(packages));}
    }
}
