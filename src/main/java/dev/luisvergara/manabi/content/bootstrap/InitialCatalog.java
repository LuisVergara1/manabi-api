package dev.luisvergara.manabi.content.bootstrap;
import java.util.*;
import dev.luisvergara.manabi.content.publication.contract.*;
import dev.luisvergara.manabi.content.editorial.EditorialModels.*;
public record InitialCatalog(String namespace,Map<String,String> sourceHashes,
        List<PublicContent.KanaEntry> kana,List<PublicContent.KanjiEntry> kanji,
        List<PublicContent.VocabularyEntry> vocabulary,List<PublicContent.VocabularyCategory> categories) {
    public InitialCatalog {sourceHashes=Map.copyOf(sourceHashes);kana=List.copyOf(kana);kanji=List.copyOf(kanji);vocabulary=List.copyOf(vocabulary);categories=List.copyOf(categories);}
    public record Target(Key key,Map<String,Object> content) {}
    public List<Target> targets() {
        var out=new ArrayList<Target>();
        categories.forEach(c->out.add(new Target(new Key(namespace,Kind.VOCABULARY_CATEGORY,c.code()),c.json())));
        kana.forEach(c->out.add(new Target(new Key(namespace,Kind.KANA,c.editorialId()),c.json())));
        kanji.forEach(c->out.add(new Target(new Key(namespace,Kind.KANJI,c.editorialId()),c.json())));
        // Membership is a set, as in JPA and the stage-2.1 projector; ordering is not educational content.
        vocabulary.forEach(c->{var content=new LinkedHashMap<>(c.json());content.put("categories",c.categories().stream().sorted().toList());
            out.add(new Target(new Key(namespace,Kind.VOCABULARY,c.editorialId()),content));});
        out.sort(Comparator.comparing(t->t.key().label()));return List.copyOf(out);
    }
    public String identitiesHash() {
        return ContentDigest.sha256(new CanonicalJson().bytes(targets().stream().map(t->Map.of("target",t.key().label(),"content",t.content())).toList()));
    }
}
