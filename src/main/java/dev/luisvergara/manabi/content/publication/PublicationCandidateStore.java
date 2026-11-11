package dev.luisvergara.manabi.content.publication;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import lombok.RequiredArgsConstructor;
import dev.luisvergara.manabi.admin.security.AdminAudit;
import dev.luisvergara.manabi.content.publication.contract.*;

/** Private metadata ledger. Never stores package/manifest bodies or exposes an activation/export operation. */
@Service @RequiredArgsConstructor
public class PublicationCandidateStore {
    private final JdbcTemplate jdbc;
 private final AdminAudit audit;
    private final PublicationSnapshotService snapshots;
    private final CanonicalJson json=new CanonicalJson();
    public record Recorded(String jobId,PublicationGenerator.Status status,List<String> issues) {
        public Recorded {issues=List.copyOf(issues);}
    }
    @Transactional(isolation=Isolation.REPEATABLE_READ,propagation=Propagation.REQUIRES_NEW)
    Recorded record(PublicationSnapshotService.Snapshot snapshot,PublicationCompiler.Compiled compiled,boolean technical,List<String> failures){
        var issues=new ArrayList<>(failures);
        PublicationGenerator.Status status;
        if(!issues.isEmpty())status=PublicationGenerator.Status.INVALID;
        else if(technical)status=PublicationGenerator.Status.TECHNICAL;
        else {
            issues.addAll(snapshot.editorialIssues());
            // Approval may change while encoding. Recheck in this metadata transaction before recording READY.
            if(issues.isEmpty()){
                var current=snapshots.capture(snapshot.profile());
                if(!current.fingerprint().equals(snapshot.fingerprint()))issues.add("SNAPSHOT_CHANGED_DURING_COMPILATION");
                issues.addAll(current.editorialIssues());
            }
            status=issues.isEmpty()?PublicationGenerator.Status.READY:PublicationGenerator.Status.BLOCKED;
        }
        String release=compiled==null?null:compiled.releaseId();
        if(status==PublicationGenerator.Status.READY){
            String manifestHash=ContentDigest.sha256(compiled.manifest());
            int reader=compiled.packages().stream().mapToInt(p->p.descriptor().minimumReaderVersion()).max().orElseThrow();
            jdbc.update("insert into publication_candidate(release_id,namespace,manifest_sha256,minimum_reader_version,created_at) values (?,?,?,?,?) on conflict (release_id) do nothing",
                release,PublicationSnapshotService.NAMESPACE,manifestHash,reader,Timestamp.from(Instant.now()));
            var existing=jdbc.queryForMap("select namespace,manifest_sha256,minimum_reader_version from publication_candidate where release_id=?",release);
            if(!manifestHash.equals(existing.get("manifest_sha256"))||!PublicationSnapshotService.NAMESPACE.equals(existing.get("namespace"))
                ||reader!=((Number)existing.get("minimum_reader_version")).intValue())throw new IllegalStateException("Release identity collision");
            for(var p:compiled.packages()){
                var d=p.descriptor();
                jdbc.update("insert into publication_package(release_id,package_id,sha256,byte_length,record_count,schema_version) values (?,?,?,?,?,?) on conflict (release_id,package_id) do nothing",
                    release,d.packageId(),d.sha256(),d.byteLength(),d.recordCount(),d.schemaVersion());
                var previous=jdbc.queryForMap("select sha256,byte_length,record_count,schema_version from publication_package where release_id=? and package_id=?",release,d.packageId());
                if(!d.sha256().equals(previous.get("sha256"))||d.byteLength()!=((Number)previous.get("byte_length")).longValue()
                    ||d.recordCount()!=((Number)previous.get("record_count")).longValue()||d.schemaVersion()!=((Number)previous.get("schema_version")).intValue())
                    throw new IllegalStateException("Package identity collision");
            }
        }
        String job=UUID.randomUUID().toString();
        jdbc.update("insert into publication_job(job_id,mode,status,profile,snapshot_fingerprint,source_revisions,validation_results,release_id,computed_release_id,created_at) values (?,?,?,?,?,?,?,?,?,?)",
            job,technical?"TECHNICAL":"DISTRIBUTABLE",status.name(),snapshot.profile().name(),snapshot.fingerprint(),
            new String(json.bytes(snapshot.sources().stream().map(PublicationSnapshotService.Source::json).toList()),java.nio.charset.StandardCharsets.UTF_8),
            new String(json.bytes(issues),java.nio.charset.StandardCharsets.UTF_8),status==PublicationGenerator.Status.READY?release:null,release,Timestamp.from(Instant.now()));
        audit.publication("GENERATE_PUBLICATION",job);
        return new Recorded(job,status,issues);
    }
}
