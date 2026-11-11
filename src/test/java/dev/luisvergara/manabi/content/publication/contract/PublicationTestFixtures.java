package dev.luisvergara.manabi.content.publication.contract;
import java.util.*;
import dev.luisvergara.manabi.entity.phrase.*;
/** Test-only access to existing unchanged stage-2.1 vectors, never a runtime source. */
public final class PublicationTestFixtures {
 public static List<PublicContent.Package> core(){return PublicFixtures.core().entrySet().stream().map(e->PublicFixtures.projection(e.getKey(),e.getValue())).toList();}
 public static List<PublicContent.Package> phrases(){return PublicFixtures.phrases().entrySet().stream().map(e->PublicFixtures.projection(e.getKey(),e.getValue())).toList();}
 public static byte[] resource(String name){return PublicFixtures.resource(name);}
 public static List<PhraseCategory> categories(){return PublicFixtures.phraseCategories();}
 public static List<Particle> particles(){return PublicFixtures.particles();}
 public static List<Phrase> expressions(String slot){return PublicFixtures.expressions(slot);}
}
