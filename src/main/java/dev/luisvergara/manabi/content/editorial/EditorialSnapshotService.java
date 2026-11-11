package dev.luisvergara.manabi.content.editorial;

import java.util.*;
import jakarta.persistence.*;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;
import dev.luisvergara.manabi.content.publication.contract.*;
import dev.luisvergara.manabi.entity.kana.Kana;
import dev.luisvergara.manabi.entity.kanjis.Kanji;
import dev.luisvergara.manabi.entity.vocabulary.*;
import dev.luisvergara.manabi.entity.phrase.*;
import static dev.luisvergara.manabi.content.editorial.EditorialModels.*;

/** Resolves private revision fingerprints, including the revision/content of shared dependencies. */
@Service @RequiredArgsConstructor
public class EditorialSnapshotService {
    private final EntityManager em;
    private final PublicContentProjector projector=new PublicContentProjector();
    private final CanonicalJson json=new CanonicalJson();
    public record Snapshot(Key key,Long revision,Map<String,Object> content,List<Key> dependencies,String fingerprint) {}
    public Snapshot read(Key key) { return read(key,new HashMap<>(),new HashSet<>(),null); }
    public Map<Key,Snapshot> capture(Map<Key,Object> entities) {
        var cache=new HashMap<Key,Snapshot>();
        for(Key key:entities.keySet())read(key,cache,new HashSet<>(),entities);
        return Map.copyOf(cache);
    }
    private Snapshot read(Key key,Map<Key,Snapshot> cache,Set<Key> visiting,Map<Key,Object> known) {
        if(cache.containsKey(key))return cache.get(key);
        if(!visiting.add(key))throw new IllegalArgumentException("Editorial dependency cycle");
        Class<?> type=switch(key.getKind()){
            case KANA -> Kana.class;case KANJI -> Kanji.class;case VOCABULARY -> Vocabulary.class;
            case VOCABULARY_CATEGORY -> VocabularyCategory.class;case PHRASE -> Phrase.class;
            case PHRASE_CATEGORY -> PhraseCategory.class;case PARTICLE -> Particle.class;
        };
        String field=key.getKind()==Kind.VOCABULARY_CATEGORY?"code":"publicId";
        Object entity=known!=null?Objects.requireNonNull(known.get(key),"Missing dependency "+key.label()):em.createQuery("select e from "+type.getSimpleName()+" e where e."+field+"=:id",type)
            .setParameter("id",key.getPublicId()).getResultStream().findFirst().orElseThrow(()->new IllegalArgumentException("Missing editorial target "+key.label()));
        Map<String,Object> content;Long revision;var dependencies=new ArrayList<Key>();
        String ns=key.getNamespace();
        if(entity instanceof Kana k){content=projector.kana(ns,List.of(k)).records().getFirst().json();revision=k.getVersion();}
        else if(entity instanceof Kanji k){content=projector.kanji(ns,k.getJlptLevel().name(),List.of(k)).records().getFirst().json();revision=k.getVersion();}
        else if(entity instanceof Vocabulary v){
            content=projector.vocabulary(ns,List.of(v),v.getCategories()).records().getFirst().json();revision=v.getVersion();
            v.getCategories().forEach(c->dependencies.add(new Key(ns,Kind.VOCABULARY_CATEGORY,c.getCode())));
        } else if(entity instanceof VocabularyCategory c){
            content=new PublicContent.VocabularyCategory(c.getCode(),c.getName(),c.getSymbol(),c.getDisplayOrder(),c.isShowInCatalog()).json();revision=c.getVersion();
        } else if(entity instanceof Phrase p){
            content=(p.getKind()==dev.luisvergara.manabi.enums.phrase.PhraseKind.FIXED?projector.fixed(List.of(p)).phrases():projector.patterns(List.of(p)).phrases()).getFirst().json();revision=p.getVersion();
            p.getCategories().forEach(c->dependencies.add(new Key(ns,Kind.PHRASE_CATEGORY,c.getPublicId())));
            p.getLearningKeys().stream().map(PhraseLearningKey::getParticle).filter(Objects::nonNull)
                .forEach(particle->dependencies.add(new Key(ns,Kind.PARTICLE,particle.getPublicId())));
        } else if(entity instanceof PhraseCategory c){content=projector.phraseCategories(List.of(c)).categories().getFirst().json();revision=c.getVersion();}
        else {var p=(Particle)entity;content=projector.particles(List.of(p)).particles().getFirst().json();revision=p.getVersion();}
        var orderedDependencies=dependencies.stream().distinct().sorted(Comparator.comparing(Key::label)).toList();
        var dependencyValues=new ArrayList<Map<String,Object>>();
        for(Key dependency:orderedDependencies){var s=read(dependency,cache,visiting,known);dependencyValues.add(Map.of("target",dependency.label(),"fingerprint",s.fingerprint()));}
        var value=Map.of("namespace",ns,"target",key.label(),"revision",String.valueOf(revision),"content",content,"dependencies",dependencyValues);
        var result=new Snapshot(key,revision,content,List.copyOf(orderedDependencies),ContentDigest.sha256(json.bytes(value)));
        cache.put(key,result);visiting.remove(key);return result;
    }
    public static String contentHash(Map<String,Object> value){return ContentDigest.sha256(new CanonicalJson().bytes(value));}
}
