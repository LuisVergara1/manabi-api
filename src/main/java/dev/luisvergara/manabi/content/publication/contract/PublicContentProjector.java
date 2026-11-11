package dev.luisvergara.manabi.content.publication.contract;

import java.util.*;
import java.util.function.*;
import dev.luisvergara.manabi.entity.kana.Kana;
import dev.luisvergara.manabi.entity.kanjis.*;
import dev.luisvergara.manabi.entity.vocabulary.Vocabulary;
import dev.luisvergara.manabi.entity.phrase.*;
import static dev.luisvergara.manabi.content.publication.contract.PublicContent.*;
import static dev.luisvergara.manabi.content.publication.contract.PublicContractValidator.need;

/** In-memory projections only. Caller must prepare a coherent snapshot; no repository or lazy reads are orchestrated here. */
public final class PublicContentProjector {
    private static final Comparator<String> TEXT=Comparator.nullsFirst(String::compareTo);
    private static final Comparator<Long> ORDER=Comparator.nullsFirst(Long::compareTo);
    private static String name(Enum<?> value){return value==null?null:value.name();}
    private static <T> List<T> source(Collection<T> input) {
        need(input!=null,"projection","missing source collection");
        need(input.stream().allMatch(Objects::nonNull),"projection","null source entry");return new ArrayList<>(input);
    }
    public KanaPackage kana(String namespace,Collection<Kana> input) {
        var rows=source(input).stream().map(k->new KanaEntry(k.getPublicId(),k.getDisplayOrder(),k.getCharacter(),k.getRomaji(),name(k.getType()),name(k.getGroup())))
            .sorted(Comparator.comparing((KanaEntry k)->"HIRAGANA".equals(k.type())?0:1)
                .thenComparing(KanaEntry::displayOrder,ORDER).thenComparing(KanaEntry::editorialId,TEXT)).toList();
        return new KanaPackage(namespace,rows);
    }
    public KanjiPackage kanji(String namespace,String level,Collection<Kanji> input) {
        var rows=source(input).stream().map(k->{
            var readings=positioned(k.getReadings(),KanjiReading::getPosition).stream()
                .map(r->new Reading(r.getReading(),name(r.getReadingType()),r.getRomaji(),r.isPrimary())).toList();
            return new KanjiEntry(k.getPublicId(),k.getDisplayOrder(),k.getCharacter(),k.getMeaning(),
                k.getStrokeCount()==null?null:k.getStrokeCount().longValue(),name(k.getJlptLevel()),k.getRadicalCharacter(),k.getRadicalName(),readings);
        }).sorted(Comparator.comparing(KanjiEntry::displayOrder,ORDER).thenComparing(KanjiEntry::editorialId,TEXT)).toList();
        return new KanjiPackage(namespace,level,rows);
    }
    public VocabularyPackage vocabulary(String namespace,Collection<Vocabulary> input,
            Collection<dev.luisvergara.manabi.entity.vocabulary.VocabularyCategory> categories) {
        var rows=source(input).stream().map(v->new VocabularyEntry(v.getPublicId(),v.getDisplayOrder(),v.getJapanese(),v.getKana(),
                v.getRomaji(),v.getMeaning(),source(v.getCategories()).stream().map(c->c.getCode()).sorted(TEXT).toList()))
            .sorted(Comparator.comparing(VocabularyEntry::displayOrder,ORDER).thenComparing(VocabularyEntry::editorialId,TEXT)).toList();
        var cats=source(categories).stream().map(c->new PublicContent.VocabularyCategory(c.getCode(),c.getName(),c.getSymbol(),c.getDisplayOrder(),c.isShowInCatalog()))
            .sorted(Comparator.comparing(PublicContent.VocabularyCategory::displayOrder,ORDER).thenComparing(PublicContent.VocabularyCategory::code,TEXT)).toList();
        return new VocabularyPackage(namespace,rows,cats,PublicProtocol.VOCABULARY_MAPPINGS);
    }
    public PhraseCategoriesPackage phraseCategories(Collection<dev.luisvergara.manabi.entity.phrase.PhraseCategory> input) {
        return new PhraseCategoriesPackage(source(input).stream().map(c->new PublicContent.PhraseCategory(c.getPublicId(),c.getName(),c.getDescription(),c.getDisplayOrder(),c.isEnabled()))
            .sorted(Comparator.comparing(PublicContent.PhraseCategory::displayOrder,ORDER).thenComparing(PublicContent.PhraseCategory::id,TEXT)).toList());
    }
    public ParticlesPackage particles(Collection<dev.luisvergara.manabi.entity.phrase.Particle> input) {
        return new ParticlesPackage(source(input).stream().map(p->new PublicContent.Particle(p.getPublicId(),p.getJapanese(),p.getTitle(),p.getExplanation()))
            .sorted(Comparator.comparing(PublicContent.Particle::id,TEXT)).toList());
    }
    public FixedPhrasesPackage fixed(Collection<Phrase> input) {
        var rows=phrases(input);
        need(rows.stream().allMatch(p->"FIXED".equals(p.kind())),"projection","wrong FIXED source");
        return new FixedPhrasesPackage(rows);
    }
    public PatternPhrasesPackage patterns(Collection<Phrase> input) {
        var rows=phrases(input);
        need(rows.stream().allMatch(p->"PATTERN".equals(p.kind())),"projection","wrong PATTERN source");
        return new PatternPhrasesPackage(rows);
    }
    private List<PhraseEntry> phrases(Collection<Phrase> input) {
        return source(input).stream().map(this::phrase).sorted(Comparator.comparing(PhraseEntry::displayOrder,ORDER).thenComparing(PhraseEntry::id,TEXT)).toList();
    }
    private PhraseEntry phrase(Phrase p) {
        var examples=source(p.getExamples()).stream().map(e->new Example(e.getPublicId(),e.getDisplayOrder(),e.getJapanese(),e.getKana(),
            e.getRomaji(),e.getMeaning(),e.getIconType(),e.getSituation())).sorted(Comparator.comparing(Example::displayOrder,ORDER).thenComparing(Example::id,TEXT)).toList();
        var meanings=positioned(p.getMeanings(),PhraseMeaning::getPosition).stream()
            .map(m->new Meaning(m.getPublicId(),m.getLabel(),m.getExplanation(),m.getIconType())).toList();
        var slots=positioned(p.getSlots(),PhraseSlot::getPosition).stream().map(s->new Slot(s.getPublicId(),s.getLabel(),s.getDescription())).toList();
        var keys=positioned(p.getLearningKeys(),PhraseLearningKey::getPosition).stream().map(k->{
            var a=k.getExample();
            return new LearningKey(k.getPublicId(),k.getIconType(),k.getParticle()==null?null:k.getParticle().getPublicId(),k.getTitle(),k.getExplanation(),
                a==null?null:new Text(a.getJapanese(),a.getKana(),a.getRomaji(),a.getMeaning()));
        }).toList();
        need(p.getPattern()!=null||slots.isEmpty(),"projection","orphan pattern slots");
        var pattern=p.getPattern()==null?null:new Pattern(p.getPattern().getFormula(),p.getPattern().getExplanation(),slots);
        var points=source(p.getKeyPoints());
        return new PhraseEntry(p.getPublicId(),name(p.getKind()),p.getDisplayOrder(),
            source(p.getCategories()).stream().map(c->c.getPublicId()).sorted(TEXT).toList(),
            p.getJapanese(),p.getKana(),p.getRomaji(),p.getMeaning(),p.getIconType(),p.getIntroduction(),examples,p.getUsageNote(),
            points.isEmpty()?null:points,keys.isEmpty()?null:keys,meanings.isEmpty()?null:meanings,pattern);
    }
    private static <T> List<T> positioned(Collection<T> input,ToIntFunction<T> position) {
        var rows=source(input);var seen=new HashSet<Integer>();
        for(T row:rows)need(position.applyAsInt(row)>=0&&seen.add(position.applyAsInt(row)),"projection","invalid/duplicate editorial position");
        rows.sort(Comparator.comparingInt(position));return rows;
    }
}
