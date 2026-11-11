package dev.luisvergara.manabi.content.publication.contract;

import java.util.*;
import static dev.luisvergara.manabi.content.publication.contract.PublicContractValidator.*;
import static dev.luisvergara.manabi.content.publication.contract.PublicProtocol.*;

/** Pure encoding/integrity operations. No release job, endpoint, storage, database or editorial approval. */
public final class PublicPackageCodec {
    private final CanonicalJson json=new CanonicalJson();
    private final PublicContractValidator validator=new PublicContractValidator();
    public record EncodedPackage(Descriptor descriptor,byte[] bytes) {
        public EncodedPackage { bytes=bytes.clone(); }
        @Override public byte[] bytes(){return bytes.clone();}
    }
    public EncodedPackage encode(String namespace,PublicContent.Package value) {
        need(value!=null,"package","missing public projection");text(namespace,120,"namespace");
        var body=value.json();String slot=value.slot(),kind=kind(slot);
        long count=validator.validateBody(slot,body);
        if(!PHRASE_SLOTS.contains(slot))need(namespace.equals(body.get("namespace")),slot,"namespace mismatch");
        byte[] bytes=json.bytes(body);String sha=ContentDigest.sha256(bytes);
        boolean phrase=kind.equals("PHRASE");
        var d=new Descriptor(slot,kind,namespace,phrase?2:1,phrase?3:1,"sha256:"+sha,"packages/"+slot+"/"+sha+".json",sha,bytes.length,count,List.of(),
            kind.equals("KANJI")?(String)body.get("jlptLevel"):null);
        validator.validatePackage(d.json(),bytes);
        return new EncodedPackage(d,bytes);
    }
    public void validateCompleteSet(Collection<EncodedPackage> packages) {
        need(packages!=null,"release","missing packages");
        var bodies=new LinkedHashMap<String,Map<String,Object>>();var namespaces=new HashSet<String>();
        for(var p:packages) {
            need(p!=null,"release","null package");
            need(!bodies.containsKey(p.descriptor().packageId()),"release","duplicate slot");
            bodies.put(p.descriptor().packageId(),validator.validatePackage(p.descriptor().json(),p.bytes()));
            namespaces.add(p.descriptor().namespace());
        }
        need(namespaces.size()==1,"release","mixed/absent namespaces");
        need(bodies.size()>=2&&bodies.size()<=32&&bodies.keySet().containsAll(List.of("kana","vocabulary")),"release","missing core or too many packages");
        need(packages.stream().mapToLong(p->p.descriptor().byteLength()).sum()<=64L*1024*1024,"release","byte budget exceeded");
        validator.validateRelations(bodies);
    }
}
