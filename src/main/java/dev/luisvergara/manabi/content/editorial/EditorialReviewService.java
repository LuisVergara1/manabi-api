package dev.luisvergara.manabi.content.editorial;

import java.time.Instant;
import java.util.*;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import lombok.RequiredArgsConstructor;
import static dev.luisvergara.manabi.content.editorial.EditorialModels.*;

/** Trusted private service only: no HTTP approval or publication endpoint. Missing/stale approval is PENDING. */
@Service @RequiredArgsConstructor
@Transactional(readOnly=true)
public class EditorialReviewService {
    private final EntityManager em;
    private final EditorialSnapshotService snapshots;
    public record Status(Key target,State effectiveState,String fingerprint,boolean stale) {}
    public Status status(Key key) {return status(key,new HashSet<>());}
    private Status status(Key key,Set<Key> visiting){
        if(!visiting.add(key))throw new IllegalArgumentException("Editorial dependency cycle");
        var current=snapshots.read(key);var review=em.find(Review.class,key);
        boolean valid=approvedAt(review,current.fingerprint());
        if(valid)for(Key dependency:current.dependencies())if(status(dependency,visiting).effectiveState()!=State.APPROVED){valid=false;break;}
        visiting.remove(key);
        return new Status(key,valid?State.APPROVED:State.PENDING,current.fingerprint(),review!=null&&review.getState()==State.APPROVED&&!valid);
    }
    @Transactional(isolation=Isolation.SERIALIZABLE)
    public Status approve(Key key,String expectedFingerprint,String reviewer) {
        if(reviewer==null||reviewer.isBlank()||reviewer.codePointCount(0,reviewer.length())>160)throw new IllegalArgumentException("Reviewer required");
        var current=snapshots.read(key);
        if(!current.fingerprint().equals(expectedFingerprint))throw new IllegalStateException("Revision changed; review again");
        for(Key dependency:current.dependencies())if(status(dependency).effectiveState()!=State.APPROVED)
            throw new IllegalStateException("Dependency pending: "+dependency.label());
        var review=em.find(Review.class,key);boolean fresh=review==null;if(fresh){review=new Review();review.setId(key);}
        review.setState(State.APPROVED);review.setRevisionFingerprint(current.fingerprint());review.setApprovedBy(reviewer);review.setApprovedAt(Instant.now());if(fresh)em.persist(review);em.flush();
        return status(key);
    }
    @Transactional
    public void pending(Key key) {
        var current=snapshots.read(key);var review=em.find(Review.class,key);
        boolean fresh=review==null;if(fresh){review=new Review();review.setId(key);}
        review.setState(State.PENDING);review.setRevisionFingerprint(current.fingerprint());review.setApprovedBy(null);review.setApprovedAt(null);if(fresh)em.persist(review);em.flush();
    }
    /** Used inside the bootstrap transaction; never overwrites an existing approval. */
    @Transactional public boolean ensurePending(Key key) {
        if(em.find(Review.class,key)!=null)return false;
        var review=new Review();review.setId(key);review.setState(State.PENDING);review.setRevisionFingerprint(snapshots.read(key).fingerprint());em.persist(review);return true;
    }

    /** Batch evaluation of the same effective-approval rules over an already coherent snapshot. No SQL/lazy reads. */
    public Map<Key,Status> statuses(Map<Key,EditorialSnapshotService.Snapshot> captured,Collection<Review> rows){
        var byKey=new HashMap<Key,Review>();rows.forEach(r->byKey.put(r.getId(),r));
        var cache=new HashMap<Key,Status>();
        for(Key key:captured.keySet())batchStatus(key,captured,byKey,cache,new HashSet<>());
        return Map.copyOf(cache);
    }
    private Status batchStatus(Key key,Map<Key,EditorialSnapshotService.Snapshot> captured,Map<Key,Review> rows,
            Map<Key,Status> cache,Set<Key> visiting){
        if(cache.containsKey(key))return cache.get(key);
        if(!visiting.add(key))throw new IllegalArgumentException("Editorial dependency cycle");
        var current=Objects.requireNonNull(captured.get(key),"Missing editorial dependency "+key.label());
        var review=rows.get(key);
        boolean valid=approvedAt(review,current.fingerprint());
        if(valid)for(Key dependency:current.dependencies())if(batchStatus(dependency,captured,rows,cache,visiting).effectiveState()!=State.APPROVED){valid=false;break;}
        visiting.remove(key);
        var result=new Status(key,valid?State.APPROVED:State.PENDING,current.fingerprint(),review!=null&&review.getState()==State.APPROVED&&!valid);
        cache.put(key,result);return result;
    }
    private static boolean approvedAt(Review review,String fingerprint){
        return review!=null&&review.getState()==State.APPROVED&&review.getRevisionFingerprint().equals(fingerprint);
    }
}
