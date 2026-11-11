package dev.luisvergara.manabi.content.publication;
import java.io.*;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.zip.*;
import java.nio.charset.StandardCharsets;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import lombok.RequiredArgsConstructor;
import tools.jackson.databind.json.JsonMapper;
import dev.luisvergara.manabi.content.publication.contract.*;
import dev.luisvergara.manabi.content.publication.contract.PublicProtocol.Descriptor;

/** Self-contained deterministic ZIPs. Historical approved storage is independent from current editorial edits. */
@Service @RequiredArgsConstructor @Transactional(propagation=Propagation.NOT_SUPPORTED)
public class PublicationExportService {
 private final PublicationStore store;
 private final java.util.concurrent.Semaphore exportPermit=new java.util.concurrent.Semaphore(1);
 @org.springframework.beans.factory.annotation.Value("${manabi.publication.export.max-uncompressed-bytes:134217728}")
 private long maximumBytes;
 private long budgetLimit(){
  if(maximumBytes<1048576L||maximumBytes>500L*1024*1024)throw new IllegalStateException("Invalid export memory budget");
  return maximumBytes;
 }
 private final PublicationArtifactLedger ledger;
 private final CanonicalJson json=new CanonicalJson();
 private final PublicContractValidator validator=new PublicContractValidator();
 private final JsonMapper mapper=JsonMapper.builder().build();
 private record Tree(byte[] manifest,Map<String,byte[]> files) {}
 @SuppressWarnings("unchecked")
 private Tree load(PublicationArtifactLedger.Stored stored)throws IOException {
  byte[] manifest=store.readManifest(stored.releaseId());
  if(!stored.manifestSha().equals(ContentDigest.sha256(manifest)))throw new IOException("Stored manifest hash differs from READY provenance");
  var value=(Map<String,Object>)json.parse(manifest);var files=new LinkedHashMap<String,byte[]>();
  long loaded=manifest.length;
  for(Object raw:(List<?>)value.get("packages")){
   var d=mapper.convertValue(raw,Descriptor.class);byte[] bytes=store.readPackage(d);loaded+=bytes.length;if(loaded>budgetLimit())throw new IOException("Release exceeds export memory budget");files.put(d.url(),bytes);
  }
  validator.validateRelease(value,files);return new Tree(manifest,files);
 }
 public PublicationArtifactLedger.Export export(String release)throws IOException {
  ledger.requireStored(release);
  var origins=ledger.history().stream().filter(h->!h.revoked()).map(PublicationArtifactLedger.Stored::releaseId).toList();
  return build(release,origins,null);
 }
 public PublicationArtifactLedger.Export rebuild(String exportId)throws IOException {
  var plan=ledger.plan(exportId);
  return build(plan.export().releaseId(),plan.origins(),plan);
 }
 private PublicationArtifactLedger.Export build(String release,List<String> origins,PublicationArtifactLedger.ExportPlan recovery)throws IOException {
  if(!exportPermit.tryAcquire())throw new IllegalStateException("Export already in progress");
  try{return buildExclusive(release,origins,recovery);}finally{exportPermit.release();}
 }
 private PublicationArtifactLedger.Export buildExclusive(String release,List<String> origins,PublicationArtifactLedger.ExportPlan recovery)throws IOException {
  var target=ledger.requireStored(release);
  if(origins.size()>1024)throw new IOException("Historical inventory limit; no history was silently removed");
  var entries=new TreeMap<String,byte[]>();entries.put("educational-content/manifest.json",load(target).manifest());
  long budget=entries.get("educational-content/manifest.json").length;
  for(String origin:origins){
   var tree=load(ledger.requireStored(origin));
   for(var file:tree.files().entrySet()){
    String name="educational-content/"+file.getKey();byte[] previous=entries.putIfAbsent(name,file.getValue());
    if(previous!=null&&!Arrays.equals(previous,file.getValue()))throw new IOException("Historical package path collision");
    if(previous==null){budget+=file.getValue().length;if(budget>budgetLimit())throw new IOException("ZIP historical inventory exceeds size budget");}
   }
  }
  if(!origins.contains(release))throw new IllegalStateException("Historical inventory changed");
  if(budget>budgetLimit())throw new IOException("ZIP historical inventory exceeds size budget");
  String inventory=ContentDigest.sha256(json.bytes(entries.entrySet().stream().map(e->Map.of("path",e.getKey(),"sha256",ContentDigest.sha256(e.getValue()),"bytes",e.getValue().length)).toList()));
  if(recovery!=null&&!recovery.inventory().equals(inventory))throw new IOException("Historical export inventory differs");
  Path staged=store.stage();
  try{
   try(var out=new ZipOutputStream(Files.newOutputStream(staged,StandardOpenOption.WRITE,StandardOpenOption.TRUNCATE_EXISTING,LinkOption.NOFOLLOW_LINKS),StandardCharsets.UTF_8)){
    for(var e:entries.entrySet()){
     var crc=new CRC32();crc.update(e.getValue());var entry=new ZipEntry(e.getKey());
     entry.setMethod(ZipEntry.STORED);entry.setSize(e.getValue().length);entry.setCompressedSize(e.getValue().length);entry.setCrc(crc.getValue());
     entry.setTimeLocal(LocalDateTime.of(1980,1,1,0,0));out.putNextEntry(entry);out.write(e.getValue());out.closeEntry();
    }
   }
   verifyZip(staged,entries);
   String expected;try(var in=Files.newInputStream(staged,LinkOption.NOFOLLOW_LINKS)){expected=LocalPublicationStore.digest(in);}
   if(recovery!=null&&!recovery.export().file().sha256().equals(expected))throw new IOException("Historical ZIP bytes differ");
   for(String origin:origins)ledger.requireStored(origin);
   var file=store.completeExport(release,staged);
   if(!expected.equals(file.sha256()))throw new IOException("ZIP changed after validation");
   // Atomic ledger authorization rechecks all origins, including explicit revocations racing ZIP generation.
   if(recovery!=null){
    var again=ledger.plan(recovery.export().exportId());
    if(!again.equals(recovery)||!file.equals(recovery.export().file()))throw new IOException("Historical export provenance changed");
    return recovery.export();
   }
   return ledger.exported(release,file,inventory,origins);
  }finally{store.discardStage(staged);}
 }
 static void verifyZip(Path path,Map<String,byte[]> expected)throws IOException {
  var seen=new HashSet<String>();
  try(var zip=new ZipFile(path.toFile(),StandardCharsets.UTF_8)){
   var entries=zip.entries();
   while(entries.hasMoreElements()){
    var entry=entries.nextElement();String name=entry.getName();
    if(!seen.add(name)||!expected.containsKey(name)||name.contains("\\")||name.contains("..")||!name.startsWith("educational-content/")||entry.isDirectory())
     throw new IOException("Unsafe, duplicate or administrative ZIP entry");
    byte[] bytes=expected.get(name);var crc=new CRC32();crc.update(bytes);
    if(entry.getMethod()!=ZipEntry.STORED||entry.getSize()!=bytes.length||entry.getCompressedSize()!=bytes.length||entry.getCrc()!=crc.getValue())throw new IOException("ZIP metadata mismatch");
    try(var in=zip.getInputStream(entry)){if(!Arrays.equals(bytes,in.readNBytes(bytes.length+1)))throw new IOException("ZIP contents mismatch");}
   }
  }
  if(!seen.equals(expected.keySet()))throw new IOException("Incomplete ZIP");
 }
 public InputStream open(String exportId)throws IOException {return store.openExport(ledger.export(exportId).file());}
 public List<PublicationArtifactLedger.Stored> history(){return ledger.history();}
}
