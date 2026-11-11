package dev.luisvergara.manabi.content.bootstrap;

import java.time.Instant;
import java.util.*;
import jakarta.persistence.*;
import org.hibernate.Session;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import lombok.RequiredArgsConstructor;
import dev.luisvergara.manabi.content.editorial.*;
import dev.luisvergara.manabi.content.publication.contract.*;
import dev.luisvergara.manabi.entity.kana.Kana;
import dev.luisvergara.manabi.entity.kanjis.*;
import dev.luisvergara.manabi.entity.vocabulary.*;
import dev.luisvergara.manabi.enums.kana.*;
import dev.luisvergara.manabi.enums.kanjis.*;
import static dev.luisvergara.manabi.content.editorial.EditorialModels.*;

/** One specific, atomic initial adoption. No synchronization, silent updates, publication or auto-approval. */
@Service @RequiredArgsConstructor
public class InitialCatalogLoader {
    private final EntityManager em;
    private final EditorialSnapshotService snapshots;
    private final EditorialReviewService reviews;
    private final CanonicalJson json=new CanonicalJson();
    public record Plan(Map<String,Object> details,String sha256) {
        public Map<String,Object> json(){var out=new LinkedHashMap<>(details);out.put("planSha256",sha256);return out;}
        public boolean conflicts(){return ((Number)((Map<?,?>)details.get("summary")).get("conflicts")).longValue()>0;}
    }
    public record Applied(boolean reused,long rootsInserted,long categoriesInserted,long reviewsInserted,String planSha256) {}
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Plan dryRun(InitialCatalog source){return plan(source);}
    private String loadId(InitialCatalog source){
        return ContentDigest.sha256(json.bytes(Map.of("namespace",source.namespace(),"manifest",source.sourceHashes().get("manifest.json"))));
    }
    private Map<String,Object> target() {
        Object[] row=(Object[])em.createNativeQuery("select current_database(),current_user,inet_server_addr()::text,inet_server_port(),current_schema(),(select system_identifier::text from pg_control_system())").getSingleResult();
        if(!"manabi".equals(row[0]))throw new IllegalStateException("Initial loader only supports database manabi");
        String schema=(String)row[4];if(!"public".equals(schema)&&!schema.startsWith("pg_temp_"))throw new IllegalStateException("Unexpected schema");
        return Map.of("database",row[0],"user",row[1],"serverAddress",row[2],"serverPort",row[3],"schema",schema,"instance",row[5]);
    }
    private Map<Key,Object> current(String ns) {
        var out=new LinkedHashMap<Key,Object>();
        em.createQuery("select e from Kana e",Kana.class).getResultList().forEach(e->out.put(new Key(ns,Kind.KANA,e.getPublicId()),e));
        em.createQuery("select distinct e from Kanji e left join fetch e.readings",Kanji.class).getResultList().forEach(e->out.put(new Key(ns,Kind.KANJI,e.getPublicId()),e));
        em.createQuery("select distinct e from Vocabulary e left join fetch e.categories",Vocabulary.class).getResultList().forEach(e->out.put(new Key(ns,Kind.VOCABULARY,e.getPublicId()),e));
        em.createQuery("select e from VocabularyCategory e",VocabularyCategory.class).getResultList().forEach(e->out.put(new Key(ns,Kind.VOCABULARY_CATEGORY,e.getCode()),e));
        return out;
    }
    private Plan plan(InitialCatalog source) {
        var destination=target();var entities=current(source.namespace());var actual=snapshots.capture(entities);
        var receipt=em.find(Load.class,loadId(source));
        var rows=new ArrayList<Map<String,Object>>();long inserts=0,unchanged=0,conflicts=0;
        for(var item:source.targets()) {
            var previous=actual.get(item.key());String action,reason="";
            if(previous!=null) {
                action=EditorialSnapshotService.contentHash(previous.content()).equals(EditorialSnapshotService.contentHash(item.content()))?"UNCHANGED":"CONFLICT";
                if(action.equals("CONFLICT"))reason="Same public identity has different content";
            } else {
                action="INSERT";
                if(receipt!=null){action="CONFLICT";reason="Previously adopted identity is missing";}
                else if(item.key().getKind()==Kind.KANA || item.key().getKind()==Kind.KANJI)for(var e:actual.values()) {
                    if(e.key().getKind()==item.key().getKind()&&Objects.equals(e.content().get("character"),item.content().get("character"))
                       &&(item.key().getKind()!=Kind.KANA||Objects.equals(e.content().get("type"),item.content().get("type")))){
                        action="CONFLICT";reason="Writing already belongs to another public identity: "+e.key().getPublicId();break;
                    }
                }
            }
            if(receipt!=null&&previous!=null&&em.find(Review.class,item.key())==null){action="CONFLICT";reason="Private review from previous adoption is missing";}
            if(action.equals("INSERT"))inserts++;else if(action.equals("UNCHANGED"))unchanged++;else conflicts++;
            rows.add(Map.of("kind",item.key().getKind().name(),"publicId",item.key().getPublicId(),"action",action,"reason",reason,
                "sourceContentSha256",EditorialSnapshotService.contentHash(item.content())));
        }
        var sourceCodes=source.categories().stream().map(PublicContent.VocabularyCategory::code).collect(java.util.stream.Collectors.toSet());
        for(var key:entities.keySet())if(key.getKind()==Kind.VOCABULARY_CATEGORY&&!sourceCodes.contains(key.getPublicId())) {
            conflicts++;rows.add(Map.of("kind",key.getKind().name(),"publicId",key.getPublicId(),"action","CONFLICT",
                "reason","Unexpected category outside the official twenty-category catalog"));
        }
        if(receipt!=null&&!receipt.getIdentitiesSha256().equals(source.identitiesHash()))throw new IllegalStateException("Stored adoption identity digest differs");
        var existing=actual.values().stream().sorted(Comparator.comparing(v->v.key().label())).map(v->{
            var review=em.find(Review.class,v.key());
            return Map.of("target",v.key().label(),"fingerprint",v.fingerprint(),
                "review",review==null?"ABSENT":review.getState()+":"+review.getRevisionFingerprint()+":"+review.getVersion());
        }).toList();
        var details=new LinkedHashMap<String,Object>();details.put("target",destination);details.put("namespace",source.namespace());
        details.put("sourceHashes",source.sourceHashes());details.put("identitiesSha256",source.identitiesHash());
        details.put("databaseStateSha256",ContentDigest.sha256(json.bytes(existing)));details.put("entries",rows);
        details.put("summary",Map.of("inserts",inserts,"unchanged",unchanged,"conflicts",conflicts,"sourceRoots",source.kana().size()+source.kanji().size()+source.vocabulary().size(),
            "sourceCategories",source.categories().size(),"readings",source.kanji().stream().mapToInt(k->k.readings().size()).sum(),
            "memberships",source.vocabulary().stream().mapToInt(v->v.categories().size()).sum()));
        return new Plan(Collections.unmodifiableMap(details),ContentDigest.sha256(json.bytes(details)));
    }
    @Transactional(isolation=Isolation.SERIALIZABLE)
    public Applied apply(InitialCatalog source,String explicitlyApprovedPlan) {
        if(explicitlyApprovedPlan==null||!explicitlyApprovedPlan.matches("[a-f0-9]{64}"))throw new IllegalArgumentException("An explicitly approved dry-run digest is mandatory");
        em.unwrap(Session.class).doWork(c->{try(var s=c.createStatement()){s.execute("select pg_advisory_xact_lock(71772201)");}});
        var fresh=plan(source);if(fresh.conflicts())throw new IllegalStateException("Dry-run conflicts; nothing written");
        var receipt=em.find(Load.class,loadId(source));
        if(receipt!=null) {
            if(!fresh.sha256().equals(explicitlyApprovedPlan)&&!receipt.getApprovedPlanSha256().equals(explicitlyApprovedPlan))
                throw new IllegalStateException("Dry-run changed");
            return new Applied(true,0,0,0,fresh.sha256());
        }
        if(!fresh.sha256().equals(explicitlyApprovedPlan))throw new IllegalStateException("Dry-run changed; review a new report before loading");
        var entities=current(source.namespace());var categories=new HashMap<String,VocabularyCategory>();long insertedCategories=0,roots=0;
        for(var row:source.categories()) {
            var key=new Key(source.namespace(),Kind.VOCABULARY_CATEGORY,row.code());var c=(VocabularyCategory)entities.get(key);
            if(c==null){c=new VocabularyCategory();c.setCode(row.code());c.setName(row.name());c.setSymbol(row.symbol());c.setDisplayOrder(row.displayOrder());c.setShowInCatalog(row.showInCatalog());em.persist(c);insertedCategories++;}
            categories.put(c.getCode(),c);
        }
        for(var row:source.kana())if(!entities.containsKey(new Key(source.namespace(),Kind.KANA,row.editorialId()))) {
            var k=new Kana();k.setPublicId(row.editorialId());k.setDisplayOrder(row.displayOrder());k.setCharacter(row.character());k.setRomaji(row.romaji());
            k.setType(KanaType.valueOf(row.type()));k.setGroup(KanaGroup.valueOf(row.group()));em.persist(k);roots++;
        }
        for(var row:source.kanji())if(!entities.containsKey(new Key(source.namespace(),Kind.KANJI,row.editorialId()))) {
            var k=new Kanji();k.setPublicId(row.editorialId());k.setDisplayOrder(row.displayOrder());k.setCharacter(row.character());k.setMeaning(row.meaning());
            k.setStrokeCount(Math.toIntExact(row.strokeCount()));k.setJlptLevel(JlptLevel.valueOf(row.jlptLevel()));k.setRadicalCharacter(row.radicalCharacter());k.setRadicalName(row.radicalName());
            for(var r:row.readings()){var reading=new KanjiReading();reading.setReading(r.reading());reading.setReadingType(ReadingType.valueOf(r.readingType()));reading.setRomaji(r.romaji());reading.setPrimary(r.primary());k.addReading(reading);}
            em.persist(k);roots++;
        }
        for(var row:source.vocabulary())if(!entities.containsKey(new Key(source.namespace(),Kind.VOCABULARY,row.editorialId()))) {
            var v=new Vocabulary();v.setPublicId(row.editorialId());v.setDisplayOrder(row.displayOrder());v.setJapanese(row.japanese());v.setKana(row.kana());v.setRomaji(row.romaji());v.setMeaning(row.meaning());
            v.setCategories(new HashSet<>(row.categories().stream().map(categories::get).toList()));em.persist(v);roots++;
        }
        em.flush();long pending=0;for(var row:source.targets())if(reviews.ensurePending(row.key()))pending++;
        var load=new Load();load.setId(loadId(source));load.setNamespace(source.namespace());load.setManifestSha256(source.sourceHashes().get("manifest.json"));
        load.setApprovedPlanSha256(explicitlyApprovedPlan);load.setIdentitiesSha256(source.identitiesHash());
        load.setSourceHashes(new String(json.bytes(source.sourceHashes()),java.nio.charset.StandardCharsets.UTF_8));load.setAppliedAt(Instant.now());em.persist(load);em.flush();
        return new Applied(false,roots,insertedCategories,pending,fresh.sha256());
    }
}
