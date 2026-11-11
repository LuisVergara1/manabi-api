package dev.luisvergara.manabi.admin.web;
import java.io.*;
import java.util.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
import org.springframework.core.io.InputStreamResource;
import lombok.RequiredArgsConstructor;
import dev.luisvergara.manabi.content.publication.*;
@RestController @RequestMapping("/api/admin/publications") @RequiredArgsConstructor
public class AdminPublicationController {
 public record Generate(@NotNull PublicationSnapshotService.Profile profile){}
 public record Job(@NotBlank @Pattern(regexp="[a-f0-9-]{36}")String jobId){}
 public record Release(@NotBlank @Pattern(regexp="sha256:[a-f0-9]{64}")String releaseId){}
 public record Export(@NotBlank @Pattern(regexp="[a-f0-9-]{36}")String exportId){}
 public record Revoke(@NotBlank @Pattern(regexp="sha256:[a-f0-9]{64}")String releaseId,@NotBlank @Size(max=1000)String reason){}
 private final PublicationAdministrationService publications;
 @PostMapping("/generate") public PublicationAdministrationService.Generated generate(@Valid @RequestBody Generate r){return publications.generate(r.profile());}
 @GetMapping("/jobs") public List<Map<String,Object>> jobs(@RequestParam(defaultValue="20")int limit,@RequestParam(defaultValue="0")int offset){return publications.jobs(limit,offset);}
 @GetMapping("/jobs/{jobId}") public Map<String,Object> job(@PathVariable String jobId){return publications.job(jobId);}
 @PostMapping("/materialize") public PublicationAdministrationService.Release store(@Valid @RequestBody Job r)throws IOException{return publications.materialize(r.jobId());}
 @GetMapping("/history") public List<PublicationAdministrationService.Release> history(@RequestParam(defaultValue="20")int limit,@RequestParam(defaultValue="0")int offset){return publications.history(limit,offset);}
 @GetMapping("/exports") public PublicationAdministrationService.ExportPage exports(@RequestParam(required=false)String releaseId,@RequestParam(defaultValue="20")int limit,@RequestParam(defaultValue="0")int offset){return publications.exports(releaseId,limit,offset);}
 @PostMapping("/export") public PublicationAdministrationService.Exported export(@Valid @RequestBody Release r)throws IOException{return publications.export(r.releaseId());}
 @PostMapping("/rebuild") public PublicationAdministrationService.Exported rebuild(@Valid @RequestBody Export r)throws IOException{return publications.rebuild(r.exportId());}
 @GetMapping("/exports/{exportId}/download") public ResponseEntity<InputStreamResource> download(@PathVariable String exportId)throws IOException{
  var in=publications.download(exportId);
  return ResponseEntity.ok().contentType(MediaType.parseMediaType("application/zip")).cacheControl(CacheControl.noStore())
   .header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.attachment().filename("manabi-"+exportId+".zip").build().toString()).body(new InputStreamResource(in));
 }
 @PostMapping("/revoke") public Map<String,String> revoke(@Valid @RequestBody Revoke r){publications.revoke(r.releaseId(),r.reason());return Map.of("state","REVOKED");}
}
