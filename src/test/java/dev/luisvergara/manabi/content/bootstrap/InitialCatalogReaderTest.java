package dev.luisvergara.manabi.content.bootstrap;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import dev.luisvergara.manabi.content.publication.contract.*;

class InitialCatalogReaderTest {
    final CanonicalJson json=new CanonicalJson();
    byte[] resource(String name)throws Exception {try(var in=getClass().getResourceAsStream("/educational-content/"+name)){return in.readAllBytes();}}
    Map<String,byte[]> files()throws Exception {var out=new HashMap<String,byte[]>();for(String f:List.of("manifest.json","kana.json","kanji.json","vocabulary.json"))out.put(f,resource(f));return out;}
    @Test void officialCountsIdentitiesAndRelationsArePreservedExactly() {
        var c=new InitialCatalogReader().readResources();
        assertEquals(222,c.kana().size());assertEquals(5,c.kanji().size());assertEquals(40,c.vocabulary().size());assertEquals(20,c.categories().size());
        assertEquals(11,c.kanji().stream().mapToInt(k->k.readings().size()).sum());assertEquals(55,c.vocabulary().stream().mapToInt(v->v.categories().size()).sum());
        assertTrue(c.kana().stream().anyMatch(k->k.editorialId().equals("hiragana-あ")));
        assertEquals(287,c.targets().size());assertEquals(c.identitiesHash(),new InitialCatalogReader().readResources().identitiesHash());
    }
    @Test void tamperedOriginalBytesFailBeforeDatabaseWork()throws Exception {
        var files=files();files.get("kana.json")[3]^=1;
        assertThrows(IllegalArgumentException.class,()->new InitialCatalogReader().read(files::get));
    }
    @SuppressWarnings("unchecked") @Test void sourceCountsCannotDisagreeWithValidHashes()throws Exception {
        var files=files();var manifest=(Map<String,Object>)json.parse(files.get("manifest.json"));
        ((Map<String,Object>)manifest.get("counts")).put("kanji",6);files.put("manifest.json",json.bytes(manifest));
        assertThrows(IllegalArgumentException.class,()->new InitialCatalogReader().read(files::get));
    }
    @Test void repeatedReadsNeverGenerateNewPublicIdentities() {
        var a=new InitialCatalogReader().readResources();var b=new InitialCatalogReader().readResources();
        assertEquals(a.kana().stream().map(PublicContent.KanaEntry::editorialId).toList(),b.kana().stream().map(PublicContent.KanaEntry::editorialId).toList());
        assertEquals(a.identitiesHash(),b.identitiesHash());assertThrows(UnsupportedOperationException.class,()->a.kana().clear());
    }
}
