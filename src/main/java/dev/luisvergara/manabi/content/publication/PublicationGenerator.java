package dev.luisvergara.manabi.content.publication;

import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import lombok.RequiredArgsConstructor;
import dev.luisvergara.manabi.content.publication.contract.PublicPackageCodec;

/** Internal generation only. No filesystem, HTTP, activation, official export or automatic approval. */
@Service @RequiredArgsConstructor @Transactional(propagation=Propagation.NOT_SUPPORTED)
public class PublicationGenerator {
    public enum Status { READY, BLOCKED, INVALID, TECHNICAL }
    public static final class ReadyPublication {
        private final PublicationCompiler.Compiled value;
        private ReadyPublication(PublicationCompiler.Compiled value){this.value=value;}
        public String releaseId(){return value.releaseId();}
        public byte[] manifest(){return value.manifest();}
        public List<PublicPackageCodec.EncodedPackage> packages(){return value.packages();}
    }
    public static final class Attempt {
        private final String jobId,computedReleaseId;
        private final Status status;
        private final List<String> issues;
        private final ReadyPublication ready;
        private final PublicationCompiler.Compiled technical;
        private Attempt(PublicationCandidateStore.Recorded job,PublicationCompiler.Compiled compiled,boolean diagnostic){
            jobId=job.jobId();status=job.status();issues=job.issues();computedReleaseId=compiled==null?null:compiled.releaseId();
            ready=status==Status.READY?new ReadyPublication(compiled):null;
            technical=diagnostic&&status==Status.TECHNICAL?compiled:null;
        }
        public String jobId(){return jobId;}
        public Status status(){return status;}
        public String computedReleaseId(){return computedReleaseId;}
        public List<String> issues(){return issues;}
        public Optional<ReadyPublication> ready(){return Optional.ofNullable(ready);}
        // Package-private: isolated diagnostics/tests. Never a ReadyPublication or an exportable candidate.
        PublicationCompiler.Compiled technical(){return technical;}
    }
    private final PublicationSnapshotService snapshots;
    private final PublicationCandidateStore store;
    public Attempt generate(PublicationSnapshotService.Profile profile){return generate(profile,false);}
    Attempt generateTechnical(PublicationSnapshotService.Profile profile){return generate(profile,true);}
    private Attempt generate(PublicationSnapshotService.Profile profile,boolean technical){
        var snapshot=snapshots.capture(profile);
        PublicationCompiler.Compiled compiled=null;var issues=new ArrayList<String>();
        try{compiled=new PublicationCompiler().compile(snapshot,technical);}
        catch(IllegalArgumentException e){issues.add("CONTRACT_INVALID:"+e.getMessage());}
        var recorded=store.record(snapshot,compiled,technical,issues);
        return new Attempt(recorded,compiled,technical);
    }
}
