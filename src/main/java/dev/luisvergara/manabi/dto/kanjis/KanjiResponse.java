package dev.luisvergara.manabi.dto.kanjis;
import java.util.List;
import dev.luisvergara.manabi.entity.kanjis.*;
import dev.luisvergara.manabi.enums.kanjis.*;
public record KanjiResponse(Long id, String publicId, String character, String meaning, Integer strokeCount,
    JlptLevel jlptLevel, Long displayOrder, String radicalCharacter, String radicalName,
    Long version, List<Reading> readings, List<Example> examples) {
    public record Reading(Long id, String reading, ReadingType readingType, String romaji, boolean primary) {}
    public record Example(Long id, String japanese, String reading, String meaning, Long displayOrder) {}
    public static KanjiResponse from(Kanji k) {
        return new KanjiResponse(k.getId(), k.getPublicId(), k.getCharacter(), k.getMeaning(), k.getStrokeCount(),
            k.getJlptLevel(), k.getDisplayOrder(), k.getRadicalCharacter(), k.getRadicalName(), k.getVersion(),
            k.getReadings().stream().map(r -> new Reading(r.getId(), r.getReading(), r.getReadingType(),
                r.getRomaji(), r.isPrimary())).toList(),
            k.getExamples().stream().map(e -> new Example(e.getId(), e.getJapanese(), e.getReading(),
                e.getMeaning(), e.getDisplayOrder())).toList());
    }
}
