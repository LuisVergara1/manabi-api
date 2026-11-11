package dev.luisvergara.manabi.content.publication;
import static org.junit.jupiter.api.Assertions.*;
import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.FileTime;
import java.util.*;
import java.util.zip.*;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.condition.*;
import dev.luisvergara.manabi.content.publication.contract.*;

class LocalPublicationStoreTest {
 @TempDir Path temp;
 LocalPublicationStore store()throws Exception{return new LocalPublicationStore(temp.resolve("store").toString());}
 PublicationCompiler.Compiled core(){return new PublicationCompiler().compile(new PublicationSnapshotService.Snapshot(PublicationSnapshotService.Profile.CORE,PublicationTestFixtures.core(),List.of(),"0".repeat(64)),false);}
 @Test void immutablePackageAndManifestReuseDoesNotRewriteFiles()throws Exception {
  var s=store();var c=core();for(var p:c.packages())s.putPackage(p.descriptor(),p.bytes());s.putManifest(c.releaseId(),c.manifest());
  var p=c.packages().getFirst();Path file=s.root().resolve(p.descriptor().url());var old=FileTime.fromMillis(946684800000L);Files.setLastModifiedTime(file,old);
  s.putPackage(p.descriptor(),p.bytes());s.putManifest(c.releaseId(),c.manifest());
  assertEquals(old,Files.getLastModifiedTime(file));assertArrayEquals(c.manifest(),s.readManifest(c.releaseId()));
  assertArrayEquals(p.bytes(),s.readPackage(p.descriptor()));assertFalse(Files.exists(s.root().resolve("manifest.json")));
 }
 @Test void corruptExistingPackageIsRejectedAndNeverOverwritten()throws Exception {
  var s=store();var p=core().packages().getFirst();s.putPackage(p.descriptor(),p.bytes());Path path=s.root().resolve(p.descriptor().url());Files.write(path,new byte[]{1,2});
  assertThrows(IOException.class,()->s.putPackage(p.descriptor(),p.bytes()));
  assertThrows(ContentContractException.class,()->s.readPackage(p.descriptor()));assertArrayEquals(new byte[]{1,2},Files.readAllBytes(path));
 }
 @Test void corruptHistoricalManifestIsNotReplaced()throws Exception {
  var s=store();var c=core();s.putManifest(c.releaseId(),c.manifest());Path p=s.root().resolve("manifests/"+c.releaseId().substring(7)+"/manifest.json");Files.write(p,new byte[]{0});
  assertThrows(IOException.class,()->s.putManifest(c.releaseId(),c.manifest()));assertThrows(IllegalArgumentException.class,()->s.readManifest(c.releaseId()));assertEquals(1,Files.size(p));
 }
 @Test void invalidRootLocationsFailClosed()throws Exception {
  Path repo=Files.createDirectories(temp.resolve("repo"));Files.createDirectories(repo.resolve(".git"));
  assertThrows(IOException.class,()->new LocalPublicationStore(repo.resolve("data").toString()));
  assertThrows(IOException.class,()->new LocalPublicationStore(temp.resolve("target/data").toString()));
  assertThrows(IOException.class,()->new LocalPublicationStore(temp.resolve("src/main/resources/data").toString()));
 }
 @Test void unsafeReleaseAndForeignStageCannotEscapeRoot()throws Exception {
  var s=store();Path foreign=temp.resolve("foreign");Files.writeString(foreign,"preserve");
  assertThrows(IOException.class,()->s.putManifest("../escape",core().manifest()));
  assertThrows(IOException.class,()->s.completeExport(core().releaseId(),foreign));assertThrows(IOException.class,()->s.discardStage(foreign));
  assertEquals("preserve",Files.readString(foreign));
 }
 @Test void interruptedPrivateStageDoesNotAppearAsAHistoricalManifest()throws Exception {
  var s=store();Path stage=s.stage();Files.writeString(stage,"partial");
  assertThrows(IOException.class,()->s.readManifest(core().releaseId()));assertTrue(Files.exists(stage));s.discardStage(stage);
 }
 @Test void concurrentWritersReuseOneImmutablePackage()throws Exception {
  var s=store();var other=new LocalPublicationStore(s.root().toString());var p=core().packages().getFirst();
  var a=java.util.concurrent.CompletableFuture.runAsync(()->{try{s.putPackage(p.descriptor(),p.bytes());}catch(IOException e){throw new UncheckedIOException(e);}});
  var b=java.util.concurrent.CompletableFuture.runAsync(()->{try{other.putPackage(p.descriptor(),p.bytes());}catch(IOException e){throw new UncheckedIOException(e);}});
  java.util.concurrent.CompletableFuture.allOf(a,b).join();assertArrayEquals(p.bytes(),s.readPackage(p.descriptor()));
 }
 @Test @EnabledOnOs(OS.WINDOWS) void windowsJunctionCannotRedirectPackagesOutsideRoot()throws Exception {
  var s=store();Path outside=Files.createDirectories(temp.resolve("outside"));Path link=s.root().resolve("packages/kana");
  var process=new ProcessBuilder("cmd.exe","/c","mklink","/J",link.toString(),outside.toString()).redirectErrorStream(true).start();
  String output=new String(process.getInputStream().readAllBytes(),StandardCharsets.UTF_8);assertEquals(0,process.waitFor(),output);
  var p=core().packages().getFirst();assertThrows(IOException.class,()->s.putPackage(p.descriptor(),p.bytes()));
  try(var stream=Files.list(outside)){assertEquals(0,stream.count());}
 }
 @Test void storageRejectsTechnicalPhraseFixtures()throws Exception {
  var s=store();var p=new PublicPackageCodec().encode(PublicationSnapshotService.NAMESPACE,PublicationTestFixtures.phrases().stream().filter(value->value.slot().equals("phrases-fixed")).findFirst().orElseThrow());
  assertThrows(ContentContractException.class,()->s.putPackage(p.descriptor(),p.bytes()));
 }
 @Test void zipVerifierRejectsZipSlipAndAdministrativeEntries()throws Exception {
  for(String name:List.of("../escape","educational-content/../escape",".private/secret")){
   Path zip=temp.resolve(UUID.randomUUID()+".zip");writeZip(zip,List.of(name));
   assertThrows(IOException.class,()->PublicationExportService.verifyZip(zip,Map.of("educational-content/manifest.json",new byte[]{1})));
  }
 }
 @Test void zipVerifierRejectsDuplicateEntriesAndPartialFiles()throws Exception {
  Path zip=temp.resolve("duplicate.zip");String first="educational-content/manifest.json",second="educational-content/manifesz.json";
  writeZip(zip,List.of(first,second));byte[] data=Files.readAllBytes(zip);
  Files.write(zip,new String(data,StandardCharsets.ISO_8859_1).replace(second,first).getBytes(StandardCharsets.ISO_8859_1));
  assertThrows(IOException.class,()->PublicationExportService.verifyZip(zip,Map.of(first,new byte[]{1})));
  Files.write(zip,new byte[]{1,2});assertThrows(IOException.class,()->PublicationExportService.verifyZip(zip,Map.of(first,new byte[]{1})));
 }
 private void writeZip(Path file,List<String> names)throws Exception {
  try(var out=new ZipOutputStream(Files.newOutputStream(file))){for(String name:names){
   var entry=new ZipEntry(name);entry.setMethod(ZipEntry.STORED);entry.setSize(1);entry.setCompressedSize(1);var crc=new CRC32();crc.update(new byte[]{1});entry.setCrc(crc.getValue());out.putNextEntry(entry);out.write(1);out.closeEntry();
  }}
 }
}
