package dev.luisvergara.manabi.service.mapper;
import org.springframework.stereotype.Component;
import dev.luisvergara.manabi.dto.kanjis.KanjiRequest;
import dev.luisvergara.manabi.entity.kanjis.*;
@Component
public class KanjiMapper {
    public Kanji toEntity(KanjiRequest request) {
        Kanji result = new Kanji();
        result.setCharacter(request.character()); result.setMeaning(request.meaning());
        result.setStrokeCount(request.strokeCount()); result.setJlptLevel(request.jlptLevel());
        result.setDisplayOrder(request.displayOrder());
        result.setRadicalCharacter(request.radicalCharacter()); result.setRadicalName(request.radicalName());
        request.readings().forEach(r -> {
            KanjiReading child = new KanjiReading();
            child.setReading(r.reading()); child.setReadingType(r.readingType());
            child.setRomaji(r.romaji()); child.setPrimary(r.primary()); result.addReading(child);
        });
        request.examples().forEach(e -> {
            KanjiExample child = new KanjiExample();
            child.setJapanese(e.japanese()); child.setReading(e.reading());
            child.setMeaning(e.meaning()); child.setDisplayOrder(e.displayOrder()); result.addExample(child);
        });
        return result;
    }
}
