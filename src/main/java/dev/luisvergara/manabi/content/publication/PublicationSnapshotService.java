package dev.luisvergara.manabi.content.publication;

import java.util.*;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import lombok.RequiredArgsConstructor;
import dev.luisvergara.manabi.content.editorial.*;
import dev.luisvergara.manabi.content.publication.contract.*;
import dev.luisvergara.manabi.entity.kana.Kana;
import dev.luisvergara.manabi.entity.kanjis.Kanji;
import dev.luisvergara.manabi.entity.vocabulary.*;
import dev.luisvergara.manabi.entity.phrase.*;
import static dev.luisvergara.manabi.content.editorial.EditorialModels.*;

/** Captures DTOs and private revision evidence, never managed entities. No package encoding or file I/O. */
@Service @RequiredArgsConstructor
public class PublicationSnapshotService {
    public static final String NAMESPACE="manabi.frontend.v1";
    public enum Profile { CORE, WITH_PHRASES }
    public record Source(String kind,String publicId,Long revision,String fingerprint,State effectiveState,boolean stale) {
        public Map<String,Object> json(){return Map.of("kind",kind,"publicId",publicId,"revision",String.valueOf(revision),
            "fingerprint",fingerprint,"effectiveState",effectiveState.name(),"stale",stale);}
    }
    public record Snapshot(Profile profile,List<PublicContent.Package> packages,List<Source> sources,String fingerprint) {
        public Snapshot {packages=List.copyOf(packages);sources=List.copyOf(sources);}
        public List<String> editorialIssues(){return sources.stream().filter(s->s.effectiveState()!=State.APPROVED)
            .map(s->"PENDING_OR_STALE:"+s.kind()+":"+s.publicId()).toList();}
    }
    private final EntityManager em;
    private final EditorialSnapshotService fingerprints;
    private final EditorialReviewService reviews;
    private final PublicContentProjector projector=new PublicContentProjector();

    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Snapshot capture(Profile profile) {
        Objects.requireNonNull(profile);
        var kana=em.createQuery("select e from Kana e",Kana.class).getResultList();
        var kanji=em.createQuery("select distinct e from Kanji e left join fetch e.readings",Kanji.class).getResultList();
        var vocabulary=em.createQuery("select distinct e from Vocabulary e left join fetch e.categories",Vocabulary.class).getResultList();
        var categories=em.createQuery("select e from VocabularyCategory e",VocabularyCategory.class).getResultList();
        var entities=new LinkedHashMap<Key,Object>();
        kana.forEach(e->entities.put(new Key(NAMESPACE,Kind.KANA,e.getPublicId()),e));
        kanji.forEach(e->entities.put(new Key(NAMESPACE,Kind.KANJI,e.getPublicId()),e));
        vocabulary.forEach(e->entities.put(new Key(NAMESPACE,Kind.VOCABULARY,e.getPublicId()),e));
        categories.forEach(e->entities.put(new Key(NAMESPACE,Kind.VOCABULARY_CATEGORY,e.getCode()),e));
        var packages=new ArrayList<PublicContent.Package>();
        packages.add(projector.kana(NAMESPACE,kana));
        packages.add(projector.vocabulary(NAMESPACE,vocabulary,categories));
        for(String level:PublicProtocol.LEVELS){
            var rows=kanji.stream().filter(k->level.equals(k.getJlptLevel().name())).toList();
            if(!rows.isEmpty())packages.add(projector.kanji(NAMESPACE,level,rows));
        }
        if(profile==Profile.WITH_PHRASES){
            var phraseCategories=em.createQuery("select e from PhraseCategory e",PhraseCategory.class).getResultList();
            var particles=em.createQuery("select e from Particle e",Particle.class).getResultList();
            var phrases=em.createQuery("select e from Phrase e",Phrase.class).getResultList();
            // One collection per query: no multiple bag fetch, no cartesian product across collections.
            for(String association:List.of("categories","examples","meanings","slots","keyPoints"))
                em.createQuery("select distinct p from Phrase p left join fetch p."+association,Phrase.class).getResultList();
            em.createQuery("select distinct p from Phrase p left join fetch p.learningKeys k left join fetch k.particle",Phrase.class).getResultList();
            phraseCategories.forEach(e->entities.put(new Key(NAMESPACE,Kind.PHRASE_CATEGORY,e.getPublicId()),e));
            particles.forEach(e->entities.put(new Key(NAMESPACE,Kind.PARTICLE,e.getPublicId()),e));
            phrases.forEach(e->entities.put(new Key(NAMESPACE,Kind.PHRASE,e.getPublicId()),e));
            packages.add(projector.phraseCategories(phraseCategories));
            packages.add(projector.fixed(phrases.stream().filter(p->p.getKind()==dev.luisvergara.manabi.enums.phrase.PhraseKind.FIXED).toList()));
            packages.add(projector.patterns(phrases.stream().filter(p->p.getKind()==dev.luisvergara.manabi.enums.phrase.PhraseKind.PATTERN).toList()));
            packages.add(projector.particles(particles));
        }
        var captured=fingerprints.capture(entities);
        var privateReviews=em.createQuery("select r from EditorialReview r where r.id.namespace=:namespace",Review.class)
            .setParameter("namespace",NAMESPACE).getResultList();
        var effective=reviews.statuses(captured,privateReviews);
        var sources=captured.values().stream().sorted(Comparator.comparing(s->s.key().label())).map(s->{
            var state=effective.get(s.key());return new Source(s.key().getKind().name(),s.key().getPublicId(),s.revision(),
                s.fingerprint(),state.effectiveState(),state.stale());
        }).toList();
        String sha=ContentDigest.sha256(new CanonicalJson().bytes(Map.of("profile",profile.name(),"namespace",NAMESPACE,
            "sources",sources.stream().map(Source::json).toList())));
        return new Snapshot(profile,packages,sources,sha);
    }
}
