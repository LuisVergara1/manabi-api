package dev.luisvergara.manabi.content.publication;
import java.io.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import lombok.RequiredArgsConstructor;
import dev.luisvergara.manabi.content.publication.contract.*;

/** Reconstruct exactly the READY job; never use current bytes as a substitute for a historical release. */
@Service @RequiredArgsConstructor @Transactional(propagation=Propagation.NOT_SUPPORTED)
public class PublicationMaterializationService {
 private final PublicationArtifactLedger ledger;
 private final PublicationSnapshotService snapshots;
 private final PublicationStore store;
 public PublicationArtifactLedger.Stored materialize(String jobId)throws IOException {
  var job=ledger.readyJob(jobId);var snapshot=snapshots.capture(job.profile());
  if(!snapshot.fingerprint().equals(job.snapshot())||!snapshot.editorialIssues().isEmpty())throw new IllegalStateException("Candidate snapshot no longer current/approved");
  var compiled=new PublicationCompiler().compile(snapshot,false);
  if(!compiled.releaseId().equals(job.releaseId())||!ContentDigest.sha256(compiled.manifest()).equals(job.manifestSha()))throw new IllegalStateException("Original release bytes cannot be reconstructed");
  for(var p:compiled.packages())store.putPackage(p.descriptor(),p.bytes());
  // Verify the whole artifact set before making the historical manifest visible.
  var files=new LinkedHashMap<String,byte[]>();for(var p:compiled.packages())files.put(p.descriptor().url(),store.readPackage(p.descriptor()));
  new PublicContractValidator().validateRelease(new CanonicalJson().parse(compiled.manifest()),files);
  ledger.assertNotRevoked(job.releaseId());store.putManifest(job.releaseId(),compiled.manifest());
  // Final current-review + revocation check in the metadata transaction; failed FS work remains private/unlisted.
  return ledger.stored(job,ContentDigest.sha256(compiled.manifest()));
 }
 public List<PublicationArtifactLedger.Stored> history(){return ledger.history();}
}
