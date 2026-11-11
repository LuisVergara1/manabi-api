package dev.luisvergara.manabi.content.publication;

import java.util.*;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import dev.luisvergara.manabi.content.publication.contract.*;

/** Internal pure compiler. Technical bytes are not a READY publication and cannot enter official persistence. */
final class PublicationCompiler {
    record Compiled(String releaseId,byte[] manifest,List<PublicPackageCodec.EncodedPackage> packages) {
        Compiled {manifest=manifest.clone();packages=List.copyOf(packages);}
        @Override public byte[] manifest(){return manifest.clone();}
        Map<String,byte[]> files(){var out=new LinkedHashMap<String,byte[]>();packages.forEach(p->out.put(p.descriptor().url(),p.bytes()));return out;}
    }
    Compiled compile(PublicationSnapshotService.Snapshot snapshot,boolean technical){
        if(TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("Serialization must run outside a transaction");
        var codec=new PublicPackageCodec();var validator=new PublicContractValidator();var json=new CanonicalJson();
        var packages=new ArrayList<PublicPackageCodec.EncodedPackage>();
        for(var value:snapshot.packages()){
            if(!technical)validator.rejectTechnicalFixtures(PublicationSnapshotService.NAMESPACE,value.json());
            packages.add(codec.encode(PublicationSnapshotService.NAMESPACE,value));
        }
        codec.validateCompleteSet(packages);
        var preimage=new LinkedHashMap<String,Object>();preimage.put("manifestFormatVersion",1);
        preimage.put("minimumReaderVersion",packages.stream().mapToInt(p->p.descriptor().minimumReaderVersion()).max().orElseThrow());
        preimage.put("packages",packages.stream().map(p->p.descriptor().json()).toList());
        String release="sha256:"+ContentDigest.sha256(json.bytes(preimage));
        var manifest=new LinkedHashMap<>(preimage);manifest.put("releaseId",release);
        byte[] bytes=json.bytes(manifest);
        var compiled=new Compiled(release,bytes,packages);
        validator.validateRelease(manifest,compiled.files());
        return compiled;
    }
}
