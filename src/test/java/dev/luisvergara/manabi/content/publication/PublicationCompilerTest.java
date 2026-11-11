package dev.luisvergara.manabi.content.publication;
import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import dev.luisvergara.manabi.content.publication.contract.*;
import static dev.luisvergara.manabi.content.publication.contract.PublicationTestFixtures.*;

class PublicationCompilerTest {
 PublicationSnapshotService.Snapshot snapshot(List<PublicContent.Package> values){return new PublicationSnapshotService.Snapshot(PublicationSnapshotService.Profile.CORE,values,List.of(),"0".repeat(64));}
 @Test void reproducesCurrentCoreManifestAndEveryPackageByte(){
  var result=new PublicationCompiler().compile(snapshot(core()),false);
  assertArrayEquals(resource("core/manifest.json"),result.manifest());
  assertEquals("sha256:b167e6169a0a5da59633c784f40d30ad9385f7b64218373e4754e0a77d24a46c",result.releaseId());
  for(var p:result.packages())assertArrayEquals(resource("core/"+p.descriptor().url()),p.bytes());
 }
 @Test void determinismDoesNotIncludeJobsOrTimes(){
  var a=new PublicationCompiler().compile(snapshot(core()),false);var b=new PublicationCompiler().compile(snapshot(core()),false);
  assertArrayEquals(a.manifest(),b.manifest());assertEquals(a.releaseId(),b.releaseId());
 }
 @Test void vocabularyEditPreservesEveryOtherPackageHash(){
  var original=core();var changed=new ArrayList<>(original);
  var v=(PublicContent.VocabularyPackage)changed.stream().filter(p->p.slot().equals("vocabulary")).findFirst().orElseThrow();
  var records=new ArrayList<>(v.records());var r=records.getFirst();
  records.set(0,new PublicContent.VocabularyEntry(r.editorialId(),r.displayOrder(),r.japanese(),r.kana(),r.romaji(),r.meaning()+" (test)",r.categories()));
  changed.set(changed.indexOf(v),new PublicContent.VocabularyPackage(v.namespace(),records,v.categories(),v.categoryMappings()));
  var a=new PublicationCompiler().compile(snapshot(original),false);var b=new PublicationCompiler().compile(snapshot(changed),false);
  assertNotEquals(a.releaseId(),b.releaseId());
  for(int i=0;i<a.packages().size();i++)if(a.packages().get(i).descriptor().packageId().equals("vocabulary"))
   assertNotEquals(a.packages().get(i).descriptor().sha256(),b.packages().get(i).descriptor().sha256());
  else assertArrayEquals(a.packages().get(i).bytes(),b.packages().get(i).bytes());
 }
 @Test void completeEmptyPhraseUnitUsesOnlyTechnicalSchemaTwo(){
  var values=new ArrayList<>(core());values.add(new PublicContent.PhraseCategoriesPackage(List.of()));values.add(new PublicContent.FixedPhrasesPackage(List.of()));
  values.add(new PublicContent.PatternPhrasesPackage(List.of()));values.add(new PublicContent.ParticlesPackage(List.of()));
  var result=new PublicationCompiler().compile(snapshot(values),false);
  assertEquals(8,result.packages().size());
  result.packages().stream().filter(p->PublicProtocol.PHRASE_SLOTS.contains(p.descriptor().packageId())).forEach(p->{assertEquals(2,p.descriptor().schemaVersion());assertEquals(3,p.descriptor().minimumReaderVersion());});
 }
 @Test void populatedTechnicalPhraseFixturesAreNeverOfficial(){
  var values=new ArrayList<>(core());values.addAll(phrases());
  assertThrows(ContentContractException.class,()->new PublicationCompiler().compile(snapshot(values),false));
  assertEquals(8,new PublicationCompiler().compile(snapshot(values),true).packages().size());
 }
 @Test void incompletePhraseUnitIsRejectedEvenInDiagnostics(){
  var values=new ArrayList<>(core());values.add(new PublicContent.FixedPhrasesPackage(List.of()));
  assertThrows(ContentContractException.class,()->new PublicationCompiler().compile(snapshot(values),true));
 }
 @Test void serializationRequiresAClosedTransaction(){
  TransactionSynchronizationManager.setActualTransactionActive(true);
  try{assertThrows(IllegalStateException.class,()->new PublicationCompiler().compile(snapshot(core()),false));}
  finally{TransactionSynchronizationManager.setActualTransactionActive(false);}
 }
 @Test void generatedManifestIsDefensivelyCopied(){
  var result=new PublicationCompiler().compile(snapshot(core()),false);byte[] first=result.manifest();first[0]=0;
  assertNotEquals(0,result.manifest()[0]);
 }
}
