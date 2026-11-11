package dev.luisvergara.manabi.content.publication;
import java.io.*;
import java.nio.*;
import java.nio.channels.*;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.security.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import dev.luisvergara.manabi.content.publication.contract.*;
import dev.luisvergara.manabi.content.publication.contract.PublicProtocol.Descriptor;
import static java.nio.file.LinkOption.NOFOLLOW_LINKS;
import static java.nio.file.StandardOpenOption.*;

/** Dedicated non-Git root. Immutable writes serialize threads/processes and require atomic moves. */
@Component
public class LocalPublicationStore implements PublicationStore {
 private static final long MAX_ZIP=512L*1024*1024;
 private static final ConcurrentHashMap<Path,ReentrantLock> LOCKS=new ConcurrentHashMap<>();
 private final Path root;
 private final ReentrantLock mutex;
 private final PublicContractValidator validator=new PublicContractValidator();
 private final CanonicalJson json=new CanonicalJson();
 public LocalPublicationStore(@Value("${manabi.publication.root:${user.home}/.manabi/publications}")String configured)throws IOException {
  root=Path.of(configured).toAbsolutePath().normalize();
  if(root.getParent()==null||root.equals(Path.of(System.getProperty("user.home")).toAbsolutePath().normalize()))throw new IOException("Dedicated root required");
  for(Path p=root;p!=null;p=p.getParent()){
   if(Files.exists(p.resolve(".git"),NOFOLLOW_LINKS))throw new IOException("Root must be outside Git");
   String name=p.getFileName()==null?"":p.getFileName().toString();
   if(name.equalsIgnoreCase("target")||name.equalsIgnoreCase(".git"))throw new IOException("Unsafe storage root");
  }
  if(root.toString().replace('\\','/').matches("(?i).*/src/main/resources(?:/.*)?"))throw new IOException("Resources are not artifact storage");
  directory(root);mutex=LOCKS.computeIfAbsent(root,p->new ReentrantLock());
  for(String dir:List.of(".private",".private/staging","packages","manifests","exports"))directory(root.resolve(dir));
 }
 public Path root(){return root;}
 private void directory(Path path)throws IOException {
  if(Files.exists(path,NOFOLLOW_LINKS)){
   var a=Files.readAttributes(path,BasicFileAttributes.class,NOFOLLOW_LINKS);
   if(!a.isDirectory()||a.isSymbolicLink()||a.isOther()||!path.toRealPath().equals(path.toAbsolutePath().normalize()))throw new IOException("Unsafe directory: "+path);
   Path parent=path.getParent();if(parent!=null)checkAncestors(parent);
  }else{
   Path parent=path.getParent();if(parent!=null)directory(parent);
   try{Files.createDirectory(path);}catch(FileAlreadyExistsException race){directory(path);return;}
   if(Files.getFileStore(path).supportsFileAttributeView("posix"))Files.setPosixFilePermissions(path,PosixFilePermissions.fromString("rwx------"));
  }
 }
 private void checkAncestors(Path path)throws IOException {
  for(Path p=path;p!=null;p=p.getParent()){
   var a=Files.readAttributes(p,BasicFileAttributes.class,NOFOLLOW_LINKS);
   if(!a.isDirectory()||a.isSymbolicLink()||a.isOther()||!p.toRealPath().equals(p.toAbsolutePath().normalize()))throw new IOException("Unsafe ancestor");
  }
 }
 private Path resolve(String key)throws IOException {
  Path rel=Path.of(key);
  if(rel.isAbsolute()||key.contains("\\")||key.contains(":")||rel.getNameCount()==0||!rel.normalize().equals(rel))throw new IOException("Unsafe relative path");
  for(Path part:rel)if(part.toString().equals("..")||part.toString().equals("."))throw new IOException("Unsafe component");
  Path path=root.resolve(rel).normalize();if(!path.startsWith(root)||path.equals(root))throw new IOException("Outside root");
  directory(root);Path cursor=root;
  for(Path part:rel){cursor=cursor.resolve(part);if(Files.exists(cursor,NOFOLLOW_LINKS)){
   var a=Files.readAttributes(cursor,BasicFileAttributes.class,NOFOLLOW_LINKS);
   if(a.isSymbolicLink()||a.isOther()||!cursor.toRealPath().equals(cursor.toAbsolutePath().normalize()))throw new IOException("Unsafe link/reparse point");
  }}
  return path;
 }
 private String hex(String release)throws IOException {
  if(release==null||!release.matches("sha256:[a-f0-9]{64}"))throw new IOException("Invalid release ID");return release.substring(7);
 }
 private String packageKey(Descriptor d)throws IOException {
  validator.validateDescriptor(d.json());String key="packages/"+d.packageId()+"/"+d.sha256()+".json";
  if(!key.equals(d.url()))throw new IOException("Noncanonical package path");return key;
 }
 private byte[] read(String key,long maximum)throws IOException {
  Path p=resolve(key);if(!Files.isRegularFile(p,NOFOLLOW_LINKS))throw new IOException("Missing/nonregular artifact");
  try(var ch=FileChannel.open(p,READ,NOFOLLOW_LINKS)){
   long size=ch.size();if(size<1||size>maximum)throw new IOException("Artifact size limit");
   ByteBuffer b=ByteBuffer.allocate(Math.toIntExact(size));while(b.hasRemaining())if(ch.read(b)<0)throw new EOFException("Partial artifact");
   if(ch.size()!=size)throw new IOException("Artifact changed during read");return b.array();
  }
 }
 private interface IO<T>{T run()throws IOException;}
 private <T>T locked(IO<T> action)throws IOException {
  mutex.lock();try{
   Path lock=resolve(".private/store.lock");
   try(var ch=FileChannel.open(lock,CREATE,WRITE,NOFOLLOW_LINKS);var ignored=ch.lock()){return action.run();}
  }finally{mutex.unlock();}
 }
 private void immutable(String key,byte[] bytes)throws IOException {
  locked(()->{
   Path destination=resolve(key);directory(destination.getParent());
   if(Files.exists(destination,NOFOLLOW_LINKS)){
    if(!Arrays.equals(read(key,Math.max(bytes.length,1)),bytes))throw new IOException("Immutable artifact differs/corrupt");return null;
   }
   Path staged=stage();
   try{
    try(var out=FileChannel.open(staged,WRITE,TRUNCATE_EXISTING,NOFOLLOW_LINKS)){ByteBuffer b=ByteBuffer.wrap(bytes);while(b.hasRemaining())out.write(b);out.force(true);}
    resolve(key);directory(destination.getParent());Files.move(staged,destination,StandardCopyOption.ATOMIC_MOVE);
   }finally{discardStage(staged);}
   return null;
  });
 }
 @Override public void putPackage(Descriptor d,byte[] bytes)throws IOException {
  String key=packageKey(d);validator.validatePackage(d.json(),bytes);validator.rejectTechnicalFixtures(PublicationSnapshotService.NAMESPACE,json.parse(bytes));immutable(key,bytes);
 }
 @Override public byte[] readPackage(Descriptor d)throws IOException {
  byte[] bytes=read(packageKey(d),20L*1024*1024);validator.validatePackage(d.json(),bytes);
  validator.rejectTechnicalFixtures(PublicationSnapshotService.NAMESPACE,json.parse(bytes));return bytes;
 }
 private void checkManifest(String release,byte[] bytes)throws IOException {
  validator.validateManifestBytes(bytes);
  @SuppressWarnings("unchecked")var m=(Map<String,Object>)json.parse(bytes);
  if(!release.equals(m.get("releaseId"))||!Arrays.equals(bytes,json.bytes(m)))throw new IOException("Manifest release/canonical mismatch");
 }
 @Override public void putManifest(String release,byte[] bytes)throws IOException {String id=hex(release);checkManifest(release,bytes);immutable("manifests/"+id+"/manifest.json",bytes);}
 @Override public byte[] readManifest(String release)throws IOException {byte[] b=read("manifests/"+hex(release)+"/manifest.json",1024*1024);checkManifest(release,b);return b;}
 @Override public Path stage()throws IOException {
  Path p=resolve(".private/staging/"+UUID.randomUUID()+".tmp");try(var ch=FileChannel.open(p,CREATE_NEW,WRITE,NOFOLLOW_LINKS)){ch.force(true);}return p;
 }
 private void checkStage(Path p)throws IOException {
  Path a=p.toAbsolutePath().normalize();
  if(!a.getParent().equals(root.resolve(".private/staging"))||!a.getFileName().toString().matches("[a-f0-9-]{36}\\.tmp")
    ||!resolve(root.relativize(a).toString().replace('\\','/')).equals(a))throw new IOException("Not a private stage file");
 }
 @Override public void discardStage(Path p)throws IOException {checkStage(p);Files.deleteIfExists(p);}
 static String digest(InputStream in)throws IOException {
  try{var d=MessageDigest.getInstance("SHA-256");byte[] b=new byte[65536];for(int n;(n=in.read(b))!=-1;)d.update(b,0,n);return HexFormat.of().formatHex(d.digest());}
  catch(NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}
 }
 @Override public ExportFile completeExport(String release,Path staged)throws IOException {
  String id=hex(release);checkStage(staged);
  return locked(()->{
   checkStage(staged);long length=Files.size(staged);if(length<1||length>MAX_ZIP)throw new IOException("ZIP size limit");
   String sha;try(var in=Files.newInputStream(staged,READ,NOFOLLOW_LINKS)){sha=digest(in);}
   String key="exports/"+id+"/"+sha+".zip";Path target=resolve(key);directory(target.getParent());
   if(Files.exists(target,NOFOLLOW_LINKS)){
    try(var in=Files.newInputStream(target,READ,NOFOLLOW_LINKS)){if(Files.size(target)!=length||!sha.equals(digest(in)))throw new IOException("Existing ZIP corrupt");}
   }else{
    try(var ch=FileChannel.open(staged,WRITE,NOFOLLOW_LINKS)){ch.force(true);}
    resolve(key);Files.move(staged,target,StandardCopyOption.ATOMIC_MOVE);
   }
   return new ExportFile(key,sha,length);
  });
 }
 @Override public InputStream openExport(ExportFile f)throws IOException {
  if(f==null||!f.sha256().matches("[a-f0-9]{64}")||!f.key().matches("exports/[a-f0-9]{64}/"+f.sha256()+"\\.zip"))throw new IOException("Invalid export reference");
  Path p=resolve(f.key());var channel=FileChannel.open(p,READ,NOFOLLOW_LINKS);
  try{
   if(channel.size()!=f.byteLength())throw new IOException("Export length mismatch");
   var in=Channels.newInputStream(channel);if(!f.sha256().equals(digest(in)))throw new IOException("Export hash mismatch");
   channel.position(0);return in;
  }catch(IOException|RuntimeException failure){channel.close();throw failure;}
 }
}
