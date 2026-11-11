package dev.luisvergara.manabi.content.bootstrap;

import java.io.*;
import java.util.*;
import java.util.function.Function;
import tools.jackson.databind.json.JsonMapper;
import dev.luisvergara.manabi.content.publication.contract.*;

/** Specific to the existing three official sources. No legacy importer, source generation or database access. */
public final class InitialCatalogReader {
    private final CanonicalJson json=new CanonicalJson();
    private final JsonMapper mapper=JsonMapper.builder().build();
    private final PublicContractValidator validator=new PublicContractValidator();
    public InitialCatalog readResources() {
        return read(name->{try(var in=getClass().getResourceAsStream("/educational-content/"+name)){
            if(in==null)throw new IllegalArgumentException("Missing source "+name);return in.readAllBytes();
        }catch(IOException e){throw new UncheckedIOException(e);}});
    }
    public InitialCatalog read(Function<String,byte[]> input) {
        byte[] manifest=input.apply("manifest.json");var m=object(json.parse(manifest));
        fields(m,Set.of("namespace","batchId","contentVersion","formatVersion","counts","files","categoryMappings","categoryCatalog","sources"));
        if(CanonicalJson.integer(m.get("formatVersion"),"formatVersion")!=1)fail("Unsupported source format");
        String ns=(String)m.get("namespace");if(!"manabi.frontend.v1".equals(ns))fail("Unexpected official namespace");
        if(!(m.get("batchId") instanceof String s)||s.isBlank()||!(m.get("contentVersion") instanceof String v)||v.isBlank())fail("Missing source provenance");
        var provenance=object(m.get("sources"));fields(provenance,Set.of("hiragana.ts","katakana.ts","kanji.ts","vocabulary.ts"));
        if(provenance.values().stream().anyMatch(v->!(v instanceof String hash)||!hash.matches("[a-f0-9]{64}")))fail("Invalid provenance hash");
        var files=object(m.get("files"));fields(files,Set.of("kana","kanji","vocabulary"));
        var hashes=new LinkedHashMap<String,String>();hashes.put("manifest.json",ContentDigest.sha256(manifest));
        var data=new HashMap<String,List<Map<String,Object>>>();
        for(String kind:List.of("kana","kanji","vocabulary")) {
            var d=object(files.get(kind));fields(d,Set.of("file","sha256"));
            if(!(kind+".json").equals(d.get("file")))fail("Unexpected source filename");
            byte[] bytes=input.apply(kind+".json");String hash=ContentDigest.sha256(bytes);
            if(!hash.equals(d.get("sha256")))fail("Original source SHA mismatch: "+kind);
            hashes.put(kind+".json",hash);data.put(kind,rows(json.parse(bytes)));
        }
        var catalogue=rows(m.get("categoryCatalog"));var mappings=object(m.get("categoryMappings"));
        var kanaBody=envelope(ns,"kana","KANA",data.get("kana"));
        validator.validateBody("kana",kanaBody);
        var vocabularyBody=envelope(ns,"vocabulary","VOCABULARY",data.get("vocabulary"));
        vocabularyBody.put("categories",catalogue);vocabularyBody.put("categoryMappings",mappings);validator.validateBody("vocabulary",vocabularyBody);
        var all=new LinkedHashMap<String,Map<String,Object>>();all.put("kana",kanaBody);all.put("vocabulary",vocabularyBody);
        for(String level:PublicProtocol.LEVELS) {
            var records=data.get("kanji").stream().filter(r->level.equals(r.get("jlptLevel"))).toList();
            if(records.isEmpty())continue;
            var body=envelope(ns,"kanji-"+level.toLowerCase(Locale.ROOT),"KANJI",records);body.put("jlptLevel",level);all.put((String)body.get("packageId"),body);
        }
        long represented=all.entrySet().stream().filter(e->e.getKey().startsWith("kanji-")).mapToLong(e->rows(e.getValue().get("records")).size()).sum();
        if(represented!=data.get("kanji").size())fail("Unsupported source Kanji level");
        validator.validateRelations(all);
        for(var row:data.get("kanji"))if(CanonicalJson.integer(row.get("strokeCount"),"strokeCount")>Integer.MAX_VALUE)
            fail("Stroke count exceeds PostgreSQL INTEGER");
        all.values().forEach(body->validator.rejectTechnicalFixtures(ns,body));
        var actual=Map.of("hiragana",data.get("kana").stream().filter(r->"HIRAGANA".equals(r.get("type"))).count(),
            "katakana",data.get("kana").stream().filter(r->"KATAKANA".equals(r.get("type"))).count(),
            "kanji",(long)data.get("kanji").size(),"kanjiReadings",data.get("kanji").stream().mapToLong(r->rows(r.get("readings")).size()).sum(),
            "vocabulary",(long)data.get("vocabulary").size(),"memberships",data.get("vocabulary").stream().mapToLong(r->((List<?>)r.get("categories")).size()).sum());
        var counts=object(m.get("counts"));fields(counts,actual.keySet());
        actual.forEach((key,value)->{if(CanonicalJson.integer(counts.get(key),key)!=value)fail("Source count mismatch: "+key);});
        return new InitialCatalog(ns,hashes,dto(data.get("kana"),PublicContent.KanaEntry.class),
            dto(data.get("kanji"),PublicContent.KanjiEntry.class),dto(data.get("vocabulary"),PublicContent.VocabularyEntry.class),
            dto(catalogue,PublicContent.VocabularyCategory.class));
    }
    private <T> List<T> dto(List<Map<String,Object>> rows,Class<T> type){return rows.stream().map(r->mapper.convertValue(r,type)).toList();}
    private static Map<String,Object> envelope(String ns,String slot,String kind,Object records) {
        var b=new LinkedHashMap<String,Object>();b.put("packageId",slot);b.put("entityType",kind);b.put("namespace",ns);b.put("schemaVersion",1);b.put("records",records);return b;
    }
    @SuppressWarnings("unchecked") private static Map<String,Object> object(Object o){if(!(o instanceof Map<?,?>))throw new IllegalArgumentException("Expected source object");return (Map<String,Object>)o;}
    @SuppressWarnings("unchecked") private static List<Map<String,Object>> rows(Object o){if(!(o instanceof List<?>))throw new IllegalArgumentException("Expected source array");return (List<Map<String,Object>>)o;}
    private static void fields(Map<String,Object> m,Set<String> fields){if(!m.keySet().equals(fields))fail("Missing/unknown source fields");}
    private static void fail(String message){throw new IllegalArgumentException(message);}
}
