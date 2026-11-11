package dev.luisvergara.manabi.content.publication.contract;

import java.text.Normalizer;
import java.util.*;
import static dev.luisvergara.manabi.content.publication.contract.PublicProtocol.*;

/** Structural/publication-profile validation. Does not certify editorial approval. */
public final class PublicContractValidator {
    private static final Set<String> ICONS=Set.of("attention","apology","conversation","restaurant","shopping","transport","repeat","meanings","examples","key-points","usage");
    private static final Set<String> GROUPS=Set.of("NORMAL","DAKUTEN","HANDAKUTEN","COMBINATION","EXTENDED");
    private static final String[] LANGUAGE={"japanese","kana","romaji","meaning"};
    private final CanonicalJson json=new CanonicalJson();

    public long validateBody(String slot,Object input) {
        var b=map(input,slot);
        if(PHRASE_SLOTS.contains(slot))return phraseFile(slot,b);
        String kind=kind(slot);
        var names=new ArrayList<>(List.of("packageId","entityType","namespace","schemaVersion","records"));
        if(kind.equals("KANJI"))names.add("jlptLevel");
        if(kind.equals("VOCABULARY"))names.addAll(List.of("categories","categoryMappings"));
        fields(b,names,List.of(),slot,false);
        need(slot.equals(b.get("packageId"))&&kind.equals(b.get("entityType")),slot,"package identity mismatch");
        need(number(b.get("schemaVersion"),1,slot)==1,slot,"schema must be 1");
        text(b.get("namespace"),120,slot+".namespace");
        String level=kind.equals("KANJI")?slot.substring(6).toUpperCase(Locale.ROOT):null;
        if(level!=null)need(level.equals(b.get("jlptLevel")),slot,"level mismatch");
        Set<String> categories=kind.equals("VOCABULARY")?categories(b):Set.of();
        var rows=list(b.get("records"),1,100000,slot+".records");
        var ids=new HashSet<String>();var writings=new HashSet<Object>();
        Map<String,Object> previous=null;
        for(int i=0;i<rows.size();i++) {
            String path=slot+".records["+i+"]";var r=map(rows.get(i),path);
            var required=new ArrayList<>(List.of("editorialId","displayOrder"));
            required.addAll(kind.equals("KANA")?List.of("character","romaji","type","group"):
                kind.equals("KANJI")?List.of("character","meaning","strokeCount","jlptLevel","radicalCharacter","radicalName","readings"):
                List.of("japanese","kana","romaji","meaning","categories"));
            fields(r,required,List.of(),path,false);
            unique(ids,text(r.get("editorialId"),128,path),path);
            number(r.get("displayOrder"),0,path);
            if(kind.equals("KANA")) {
                String c=text(r.get("character"),32,path);text(r.get("romaji"),64,path);
                need(r.get("type") instanceof String && List.of("HIRAGANA","KATAKANA").contains(r.get("type")) && r.get("group") instanceof String && GROUPS.contains(r.get("group")),path,"invalid type/group");
                need(!"EXTENDED".equals(r.get("group"))||"KATAKANA".equals(r.get("type")),path,"EXTENDED only Katakana");
                need(Normalizer.isNormalized(c,Normalizer.Form.NFC),path,"Kana must already be NFC");
                need(writings.add(List.of(r.get("type"),c)),path,"duplicate Kana type/writing");
            } else if(kind.equals("KANJI")) {
                unique(writings,text(r.get("character"),16,path),path);
                text(r.get("meaning"),4000,path);number(r.get("strokeCount"),1,path);
                need(level.equals(r.get("jlptLevel")),path,"inconsistent level");
                nullable(r.get("radicalCharacter"),16,path);nullable(r.get("radicalName"),120,path);
                need(r.get("radicalName")==null||r.get("radicalCharacter")!=null,path,"radicalName requires character");
                var readings=list(r.get("readings"),1,100000,path+".readings");var seen=new HashSet<Object>();int primary=0;
                for(Object raw:readings) {
                    var reading=map(raw,path+".reading");
                    fields(reading,List.of("reading","readingType","romaji","primary"),List.of(),path,false);
                    String word=text(reading.get("reading"),128,path);nullable(reading.get("romaji"),256,path);
                    need(reading.get("readingType") instanceof String && List.of("KUNYOMI","ONYOMI").contains(reading.get("readingType")),path,"invalid reading type");
                    bool(reading.get("primary"),path);need(seen.add(List.of(reading.get("readingType"),word)),path,"duplicate reading");
                    if(Boolean.TRUE.equals(reading.get("primary"))) {primary++;need(reading.get("romaji")!=null,path,"primary romaji required");}
                }
                need(primary==1,path,"exactly one explicit primary required");
            } else {
                text(r.get("japanese"),256,path);text(r.get("kana"),256,path);
                text(r.get("romaji"),512,path);text(r.get("meaning"),4000,path);
                var memberships=list(r.get("categories"),1,100000,path+".categories");var seen=new HashSet<Object>();
                for(Object code:memberships)need(categories.contains(code)&&seen.add(code),path,"unknown/duplicate category");
            }
            if(previous!=null)need(compareRecords(previous,r,kind)<=0,path,"records not in educational order");
            previous=r;
        }
        return rows.size();
    }

    private Set<String> categories(Map<String,Object> body) {
        var rows=list(body.get("categories"),1,100000,"categories");var codes=new HashSet<String>();var visible=new HashSet<String>();var hidden=new HashSet<String>();
        for(Object raw:rows) {
            var c=map(raw,"category");fields(c,List.of("code","name","symbol","displayOrder","showInCatalog"),List.of(),"category",false);
            String code=text(c.get("code"),40,"category.code");need(code.matches("[A-Z][A-Z0-9_]{0,39}"),"category","invalid code");unique(codes,code,"category");
            text(c.get("name"),120,"category.name");nullable(c.get("symbol"),16,"category.symbol");number(c.get("displayOrder"),0,"category.order");bool(c.get("showInCatalog"),"category.visible");
            if(Boolean.TRUE.equals(c.get("showInCatalog"))) {need(c.get("symbol")!=null,"category","visible symbol required");visible.add(code);}else hidden.add(code);
        }
        var mappings=map(body.get("categoryMappings"),"categoryMappings");var targets=new HashSet<Object>();
        for(var e:mappings.entrySet()) {
            need(e.getKey().matches("[a-z][a-z0-9-]*"),"categoryMappings","invalid presentation ID");
            need(visible.contains(e.getValue())&&targets.add(e.getValue()),"categoryMappings","invalid/duplicate code");
        }
        need(targets.equals(visible),"categoryMappings","must cover visible categories");
        need(mappings.equals(VOCABULARY_MAPPINGS)&&hidden.equals(Set.of("OBJECT","DAILY")),"vocabulary","incompatible V1 category profile");
        return codes;
    }

    private long phraseFile(String slot,Map<String,Object> b) {
        String property=slot.equals("phrase-categories")?"categories":slot.equals("particles")?"particles":"phrases";
        fields(b,List.of(property),List.of(),slot,true);
        var rows=list(b.get(property),0,Integer.MAX_VALUE,slot);var ids=new HashSet<String>();
        for(Object raw:rows) {
            var r=map(raw,slot);unique(ids,text(r.get("id"),Integer.MAX_VALUE,slot+".id"),slot);
            if(property.equals("categories")) {
                fields(r,List.of("id","name","description","displayOrder","enabled"),List.of(),slot,true);
                route(r.get("id"),slot);text(r.get("name"),Integer.MAX_VALUE,slot);text(r.get("description"),Integer.MAX_VALUE,slot);
                number(r.get("displayOrder"),0,slot);bool(r.get("enabled"),slot);
            } else if(property.equals("particles")) {
                fields(r,List.of("id","japanese","title","explanation"),List.of(),slot,true);
                text(r.get("japanese"),Integer.MAX_VALUE,slot);text(r.get("title"),Integer.MAX_VALUE,slot);text(r.get("explanation"),Integer.MAX_VALUE,slot);
            } else phrase(r,slot.equals("phrases-fixed")?"FIXED":"PATTERN",slot);
        }
        return rows.size();
    }
    private void phrase(Map<String,Object> p,String kind,String path) {
        var required=new ArrayList<>(List.of("id","kind","displayOrder","categoryIds","japanese","kana","romaji","meaning","iconType","introduction","examples"));
        if(kind.equals("PATTERN"))required.add("pattern");
        var optional=new ArrayList<>(List.of("usageNote","keyPoints","learningKeys"));if(kind.equals("FIXED"))optional.add("meanings");
        fields(p,required,optional,path,true);route(p.get("id"),path);need(kind.equals(p.get("kind")),path,"wrong phrase kind");
        number(p.get("displayOrder"),0,path);linguistic(p,path);icon(p.get("iconType"),path);text(p.get("introduction"),Integer.MAX_VALUE,path);
        var categories=list(p.get("categoryIds"),1,Integer.MAX_VALUE,path);var seen=new HashSet<String>();
        for(Object id:categories)unique(seen,text(id,Integer.MAX_VALUE,path),path);
        if(p.containsKey("usageNote"))text(p.get("usageNote"),Integer.MAX_VALUE,path);
        if(p.containsKey("keyPoints"))for(Object v:list(p.get("keyPoints"),0,3,path))text(v,Integer.MAX_VALUE,path);
        if(p.containsKey("learningKeys")) {
            var keys=list(p.get("learningKeys"),0,3,path);var ids=new HashSet<String>();
            for(Object raw:keys) {
                var k=map(raw,path);fields(k,List.of("id","iconType"),List.of("particleId","title","explanation","example"),path,true);
                unique(ids,text(k.get("id"),Integer.MAX_VALUE,path),path);icon(k.get("iconType"),path);
                if(k.containsKey("particleId")) {
                    text(k.get("particleId"),Integer.MAX_VALUE,path);need(!k.containsKey("title")&&!k.containsKey("explanation"),path,"particle overrides forbidden");
                } else {text(k.get("title"),Integer.MAX_VALUE,path);text(k.get("explanation"),Integer.MAX_VALUE,path);}
                if(k.containsKey("example")){var a=map(k.get("example"),path);fields(a,List.of(LANGUAGE),List.of(),path,true);linguistic(a,path);}
            }
        }
        var ids=new HashSet<String>();
        for(Object raw:list(p.get("examples"),0,4,path)) {
            var e=map(raw,path);fields(e,List.of("id","displayOrder","japanese","kana","romaji","meaning","iconType"),List.of("situation"),path,true);
            unique(ids,text(e.get("id"),Integer.MAX_VALUE,path),path);number(e.get("displayOrder"),0,path);linguistic(e,path);icon(e.get("iconType"),path);
            if(e.containsKey("situation"))text(e.get("situation"),Integer.MAX_VALUE,path);
        }
        if(p.containsKey("meanings")) {
            ids.clear();for(Object raw:list(p.get("meanings"),0,Integer.MAX_VALUE,path)) {
                var m=map(raw,path);fields(m,List.of("id","label","explanation","iconType"),List.of(),path,true);
                unique(ids,text(m.get("id"),Integer.MAX_VALUE,path),path);text(m.get("label"),Integer.MAX_VALUE,path);text(m.get("explanation"),Integer.MAX_VALUE,path);icon(m.get("iconType"),path);
            }
        }
        if(kind.equals("PATTERN")) {
            var pattern=map(p.get("pattern"),path);fields(pattern,List.of("formula","explanation","slots"),List.of(),path,true);
            text(pattern.get("formula"),Integer.MAX_VALUE,path);text(pattern.get("explanation"),Integer.MAX_VALUE,path);ids.clear();
            for(Object raw:list(pattern.get("slots"),0,Integer.MAX_VALUE,path)) {
                var s=map(raw,path);fields(s,List.of("id","label","description"),List.of(),path,true);
                unique(ids,text(s.get("id"),Integer.MAX_VALUE,path),path);text(s.get("label"),Integer.MAX_VALUE,path);text(s.get("description"),Integer.MAX_VALUE,path);
            }
        }
    }

    /** Validate cross-file references; no old PHRASE schema is generated by this layer. */
    public void validateRelations(Map<String,? extends Map<String,Object>> bodies) {
        long count=PHRASE_SLOTS.stream().filter(bodies::containsKey).count();
        need(count==0||count==4,"release","incomplete four-file PHRASE set");
        var kanjiIds=new HashSet<String>();var kanjiWords=new HashSet<String>();
        for(var e:bodies.entrySet()) {
            validateBody(e.getKey(),e.getValue());
            if(e.getKey().startsWith("kanji-"))for(Object raw:list(e.getValue().get("records"),1,100000,"kanji")) {
                var k=map(raw,"kanji");unique(kanjiIds,(String)k.get("editorialId"),"release");unique(kanjiWords,(String)k.get("character"),"release");
            }
        }
        if(count==0)return;
        var cats=ids(bodies.get("phrase-categories").get("categories"));var particles=ids(bodies.get("particles").get("particles"));
        var phraseIds=new HashSet<String>();var exampleIds=new HashSet<String>();
        for(String slot:List.of("phrases-fixed","phrases-pattern"))for(Object raw:list(bodies.get(slot).get("phrases"),0,Integer.MAX_VALUE,slot)) {
            var p=map(raw,slot);unique(phraseIds,(String)p.get("id"),"release");
            for(Object id:list(p.get("categoryIds"),1,Integer.MAX_VALUE,slot))need(cats.contains(id),slot,"unknown category reference");
            for(Object k:list(p.getOrDefault("learningKeys",List.of()),0,3,slot)) {
                var key=map(k,slot);if(key.containsKey("particleId"))need(particles.contains(key.get("particleId")),slot,"unknown particle reference");
            }
            for(Object e:list(p.get("examples"),0,4,slot))unique(exampleIds,(String)map(e,slot).get("id"),"release examples");
        }
    }

    public void validateDescriptor(Object raw) {
        var p=map(raw,"descriptor");String slot=text(p.get("packageId"),128,"descriptor.packageId");String kind=kind(slot);
        var required=new ArrayList<>(List.of("packageId","entityType","namespace","schemaVersion","minimumReaderVersion","contentVersion","url","sha256","byteLength","recordCount","dependencies"));
        if(kind.equals("KANJI"))required.add("jlptLevel");fields(p,required,List.of(),"descriptor",false);
        boolean phrase=kind.equals("PHRASE");
        need(kind.equals(p.get("entityType")),"descriptor","wrong entity type");
        need(number(p.get("schemaVersion"),1,"descriptor")== (phrase?2:1),"descriptor","incompatible schema");
        need(number(p.get("minimumReaderVersion"),1,"descriptor")== (phrase?3:1),"descriptor","incompatible reader");
        text(p.get("namespace"),120,"descriptor.namespace");
        String hash=text(p.get("sha256"),64,"descriptor.sha256");need(hash.matches("[a-f0-9]{64}"),"descriptor","invalid hash");
        need(("sha256:"+hash).equals(p.get("contentVersion")),"descriptor","invalid content version");
        need(("packages/"+slot+"/"+hash+".json").equals(p.get("url")),"descriptor","invalid immutable URL");
        need(number(p.get("byteLength"),1,"descriptor")<=CanonicalJson.MAX_BYTES,"descriptor","byte limit exceeded");
        number(p.get("recordCount"),phrase?0:1,"descriptor");
        list(p.get("dependencies"),0,0,"descriptor.dependencies");
        if(kind.equals("KANJI"))need(slot.substring(6).toUpperCase(Locale.ROOT).equals(p.get("jlptLevel")),"descriptor","level mismatch");
    }
    public Map<String,Object> validatePackage(Object descriptor,byte[] bytes) {
        validateDescriptor(descriptor);var d=map(descriptor,"descriptor");
        need(bytes!=null&&bytes.length==number(d.get("byteLength"),1,"descriptor"),"package","byte size mismatch");
        need(ContentDigest.sha256(bytes).equals(d.get("sha256")),"package","SHA-256 mismatch");
        var body=map(json.parse(bytes),"package");String slot=(String)d.get("packageId");
        need(validateBody(slot,body)==number(d.get("recordCount"),0,"descriptor"),slot,"count mismatch");
        if(!PHRASE_SLOTS.contains(slot))need(d.get("namespace").equals(body.get("namespace")),slot,"namespace mismatch");
        return body;
    }
    public void validateManifestBytes(byte[] bytes) {
        need(bytes!=null&&bytes.length<=1024*1024,"manifest","manifest byte limit exceeded");
        validateManifest(json.parse(bytes));
    }
    public void validateManifest(Object raw) {
        need(json.bytes(raw).length<=1024*1024,"manifest","manifest byte limit exceeded");
        var m=map(raw,"manifest");fields(m,List.of("manifestFormatVersion","minimumReaderVersion","releaseId","packages"),List.of(),"manifest",false);
        need(number(m.get("manifestFormatVersion"),1,"manifest")==1,"manifest","incompatible manifest");
        var packages=list(m.get("packages"),2,32,"manifest.packages");var slots=new HashSet<String>();var namespaces=new HashSet<Object>();long bytes=0,reader=1;
        for(Object p:packages) {
            validateDescriptor(p);var d=map(p,"descriptor");unique(slots,(String)d.get("packageId"),"manifest");namespaces.add(d.get("namespace"));
            bytes+=number(d.get("byteLength"),1,"descriptor");reader=Math.max(reader,number(d.get("minimumReaderVersion"),1,"descriptor"));
        }
        need(slots.containsAll(List.of("kana","vocabulary"))&&namespaces.size()==1,"manifest","missing core or mixed namespace");
        need(bytes<=64L*1024*1024,"manifest","release byte budget exceeded");
        long n=PHRASE_SLOTS.stream().filter(slots::contains).count();need(n==0||n==4,"manifest","incomplete PHRASE file set");
        need(number(m.get("minimumReaderVersion"),1,"manifest")==reader,"manifest","reader mismatch");
        var preimage=new LinkedHashMap<>(m);preimage.remove("releaseId");
        need(("sha256:"+ContentDigest.sha256(json.bytes(preimage))).equals(m.get("releaseId")),"manifest","release ID mismatch");
    }
    public void validateRelease(Object manifest,Map<String,byte[]> files) {
        validateManifest(manifest);var bodies=new LinkedHashMap<String,Map<String,Object>>();
        for(Object raw:list(map(manifest,"manifest").get("packages"),2,32,"manifest")) {
            var d=map(raw,"descriptor");bodies.put((String)d.get("packageId"),validatePackage(d,files.get(d.get("url"))));
        }
        validateRelations(bodies);
    }
    /** Existing anti-fixture gate, separate from structural tests; does not replace future approval. */
    public void rejectTechnicalFixtures(String namespace,Object body) {
        need(namespace!=null&&!java.util.regex.Pattern.compile("\\bfixture\\b").matcher(namespace).find(),"publication","technical namespace");
        visitFixtures(body);
    }
    private void visitFixtures(Object v) {
        if(v instanceof String s)need(!s.contains("__FIXTURE_"),"publication","technical marker");
        else if(v instanceof Map<?,?> m)for(var e:m.entrySet()) {
            if(Set.of("id","editorialId").contains(e.getKey()) && e.getValue() instanceof String s)need(!s.startsWith("fixture-"),"publication","technical identity");
            visitFixtures(e.getValue());
        } else if(v instanceof List<?> l)l.forEach(this::visitFixtures);
    }
    static String kind(String slot) {
        need(slot!=null,"packageId","missing slot");
        if("kana".equals(slot))return "KANA";if("vocabulary".equals(slot))return "VOCABULARY";
        if(PHRASE_SLOTS.contains(slot))return "PHRASE";
        if(LEVELS.stream().anyMatch(l->("kanji-"+l.toLowerCase(Locale.ROOT)).equals(slot)))return "KANJI";
        throw new ContentContractException("packageId","unsupported slot");
    }
    private static Set<String> ids(Object raw) {
        var out=new HashSet<String>();for(Object v:list(raw,0,Integer.MAX_VALUE,"ids"))out.add((String)map(v,"id").get("id"));return out;
    }
    private static int compareRecords(Map<String,Object> a,Map<String,Object> b,String kind) {
        int c=kind.equals("KANA")?Integer.compare("HIRAGANA".equals(a.get("type"))?0:1,"HIRAGANA".equals(b.get("type"))?0:1):0;
        if(c==0)c=Long.compare(number(a.get("displayOrder"),0,"order"),number(b.get("displayOrder"),0,"order"));
        if(c==0)c=((String)a.get("editorialId")).compareTo((String)b.get("editorialId"));return c;
    }
    @SuppressWarnings("unchecked") static Map<String,Object> map(Object raw,String path) {
        need(raw instanceof Map<?,?>,path,"expected object");var m=(Map<?,?>)raw;
        need(m.keySet().stream().allMatch(String.class::isInstance),path,"non-string key");return (Map<String,Object>)m;
    }
    @SuppressWarnings("unchecked") private static List<Object> list(Object raw,int min,int max,String path) {
        need(raw instanceof List<?>,path,"expected array");var l=(List<?>)raw;
        need(l.size()>=min&&l.size()<=max,path,"array limit");return (List<Object>)l;
    }
    private static void fields(Map<String,Object> m,List<String> required,List<String> optional,String path,boolean noNull) {
        need(m.keySet().containsAll(required)&&m.keySet().stream().allMatch(k->required.contains(k)||optional.contains(k)),path,"missing or unknown fields");
        if(noNull)need(m.values().stream().allMatch(Objects::nonNull),path,"null fields must be omitted");
    }
    static String text(Object raw,int max,String path) {
        need(raw instanceof String,path,"expected text");String s=(String)raw;CanonicalJson.wellFormed(s,path);
        need(!s.isEmpty()&&s.codePoints().anyMatch(c->!space(c))&&s.codePointCount(0,s.length())<=max&&s.indexOf(0)<0&&s.indexOf('\ufffd')<0,path,"invalid text");return s;
    }
    private static boolean space(int c){return c==9||c==10||c==11||c==12||c==13||c==32||c==160||c==5760||c>=8192&&c<=8202||c==8232||c==8233||c==8239||c==8287||c==12288||c==65279;}
    private static void nullable(Object v,int max,String p){if(v!=null)text(v,max,p);}
    private static long number(Object v,long min,String p){long n=CanonicalJson.integer(v,p);need(n>=min,p,"integer out of range");return n;}
    private static void bool(Object v,String p){need(v instanceof Boolean,p,"expected boolean");}
    private static void route(Object v,String p){need(text(v,Integer.MAX_VALUE,p).matches("[A-Za-z0-9_-]+"),p,"unsafe public ID");}
    private static void linguistic(Map<String,Object> m,String p){for(String k:LANGUAGE)text(m.get(k),Integer.MAX_VALUE,p+"."+k);}
    private static void icon(Object v,String p){need(v instanceof String && ICONS.contains(v),p,"unregistered icon");}
    private static <T> void unique(Set<T> seen,T v,String p){need(seen.add(v),p,"duplicate identity");}
    static void need(boolean valid,String path,String message){if(!valid)throw new ContentContractException(path,message);}
}
