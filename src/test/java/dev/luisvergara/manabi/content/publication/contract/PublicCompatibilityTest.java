package dev.luisvergara.manabi.content.publication.contract;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import java.nio.file.*;
import java.io.*;
import org.junit.jupiter.api.Test;
import static dev.luisvergara.manabi.content.publication.contract.PublicFixtures.*;

class PublicCompatibilityTest {
    final PublicContractValidator validator=new PublicContractValidator();
    final PublicPackageCodec codec=new PublicPackageCodec();
    @Test void explicitPublicDtosProduceEveryCurrentGoldenPackageExactly() {
        var bodies=core();bodies.putAll(phrases());
        for(var e:bodies.entrySet()) {
            var encoded=codec.encode(NS,projection(e.getKey(),e.getValue()));
            assertArrayEquals(JSON.bytes(e.getValue()),encoded.bytes(),e.getKey());
            assertEquals(PublicProtocol.PHRASE_SLOTS.contains(e.getKey())?2:1,encoded.descriptor().schemaVersion());
            assertEquals(PublicProtocol.PHRASE_SLOTS.contains(e.getKey())?3:1,encoded.descriptor().minimumReaderVersion());
        }
    }
    @Test void integrityDescriptorAndManifestFailuresAreRejected() {
        var m=tree("core/manifest.json");var d=rows(m.get("packages")).getFirst();
        byte[] bytes=resource("core/"+s(d,"url"));validator.validatePackage(d,bytes);
        byte[] bad=bytes.clone();bad[0]^=1;assertThrows(ContentContractException.class,()->validator.validatePackage(d,bad));
        for(String key:List.of("byteLength","recordCount","schemaVersion","minimumReaderVersion")) {
            var modified=deepCopy(d);modified.put(key,999999);
            assertThrows(ContentContractException.class,()->validator.validatePackage(modified,bytes),key);
        }
        var unsafe=deepCopy(d);unsafe.put("url","../outside.json");assertThrows(ContentContractException.class,()->validator.validateDescriptor(unsafe));
        var changed=deepCopy(m);changed.put("releaseId","sha256:"+"0".repeat(64));var wrongRelease=changed;assertThrows(ContentContractException.class,()->validator.validateManifest(wrongRelease));
        changed=deepCopy(m);changed.put("publishedAt","private");var extra=changed;assertThrows(ContentContractException.class,()->validator.validateManifest(extra));
        validator.validateManifestBytes(resource("core/manifest.json"));
        assertThrows(ContentContractException.class,()->validator.validateManifestBytes(new byte[1024*1024+1]));
    }
    @Test void immutableEncodedBytesCannotBeChangedAfterValidation() {
        var p=codec.encode(NS,projection("kana",core().get("kana")));byte[] original=p.bytes();byte[] copy=p.bytes();copy[0]=0;
        assertArrayEquals(original,p.bytes());
        assertThrows(UnsupportedOperationException.class,()->p.descriptor().dependencies().add("x"));
    }
    @Test void emptyFixedAndPatternShareHashWithoutCollapsingSlots() {
        var fixed=codec.encode(NS,new PublicContent.FixedPhrasesPackage(List.of()));
        var pattern=codec.encode(NS,new PublicContent.PatternPhrasesPackage(List.of()));
        assertArrayEquals(fixed.bytes(),pattern.bytes());assertEquals(fixed.descriptor().sha256(),pattern.descriptor().sha256());
        assertNotEquals(fixed.descriptor().url(),pattern.descriptor().url());
        assertThrows(ContentContractException.class,()->codec.validateCompleteSet(List.of(fixed,pattern)));
    }
    @Test void completeSetsCheckNamespaceAndCrossFileReferences() {
        var values=new ArrayList<PublicPackageCodec.EncodedPackage>();
        for(var e:core().entrySet())values.add(codec.encode(NS,projection(e.getKey(),e.getValue())));
        codec.validateCompleteSet(values);
        var nsBody=deepCopy(core().get("kana"));nsBody.put("namespace","different");
        values.set(0,codec.encode("different",projection("kana",nsBody)));
        assertThrows(ContentContractException.class,()->codec.validateCompleteSet(values));
    }
    @Test void javaConformanceArtifactsForExistingFrontendValidatorAreTestOnlyAndDeterministic() throws Exception {
        var projector=new PublicContentProjector();var coreValues=new ArrayList<PublicPackageCodec.EncodedPackage>();
        coreValues.add(codec.encode(NS,projector.kana(NS,kana())));
        coreValues.add(codec.encode(NS,projector.vocabulary(NS,vocabulary(),vocabularyCategories())));
        coreValues.add(codec.encode(NS,projector.kanji(NS,"N5",kanji("kanji-n5"))));
        coreValues.add(codec.encode(NS,projector.kanji(NS,"N4",kanji("kanji-n4"))));
        var manifest=artifact("core",coreValues);
        assertArrayEquals(resource("core/manifest.json"),JSON.bytes(manifest));
        var empty=new ArrayList<>(coreValues);
        empty.add(codec.encode(NS,new PublicContent.PhraseCategoriesPackage(List.of())));
        empty.add(codec.encode(NS,new PublicContent.FixedPhrasesPackage(List.of())));
        empty.add(codec.encode(NS,new PublicContent.PatternPhrasesPackage(List.of())));
        empty.add(codec.encode(NS,new PublicContent.ParticlesPackage(List.of())));
        artifact("empty",empty);
        var technical=new ArrayList<>(coreValues);
        technical.add(codec.encode(NS,projector.phraseCategories(phraseCategories())));
        technical.add(codec.encode(NS,projector.fixed(expressions("phrases-fixed"))));
        technical.add(codec.encode(NS,projector.patterns(expressions("phrases-pattern"))));
        technical.add(codec.encode(NS,projector.particles(particles())));
        artifact("technical",technical);
    }
    private Map<String,Object> artifact(String name,List<PublicPackageCodec.EncodedPackage> values) throws IOException {
        codec.validateCompleteSet(values);var m=new LinkedHashMap<String,Object>();
        m.put("manifestFormatVersion",1);m.put("minimumReaderVersion",values.stream().mapToInt(p->p.descriptor().minimumReaderVersion()).max().orElseThrow());
        m.put("packages",values.stream().map(p->p.descriptor().json()).toList());
        m.put("releaseId","sha256:"+ContentDigest.sha256(JSON.bytes(m)));
        var files=new LinkedHashMap<String,byte[]>();values.forEach(p->files.put(p.descriptor().url(),p.bytes()));
        validator.validateRelease(m,files);
        Path root=Path.of("target/public-contract-conformance").resolve(name);
        for(var e:files.entrySet()){Path target=root.resolve(e.getKey());Files.createDirectories(target.getParent());Files.write(target,e.getValue());}
        Files.write(root.resolve("manifest.json"),JSON.bytes(m));return m;
    }
}
