package dev.luisvergara.manabi.service.mapper;

import java.util.List;

import org.springframework.stereotype.Component;

import dev.luisvergara.manabi.dto.kanjis.KanjiRequest;
import dev.luisvergara.manabi.entity.kanjis.Kanji;
import dev.luisvergara.manabi.entity.kanjis.KanjiExample;
import dev.luisvergara.manabi.entity.kanjis.KanjiReading;

@Component 
public class KanjiMapper {
     public Kanji toEntity(KanjiRequest request) {
        Kanji kanji = new Kanji();

        kanji.setCharacter(request.character());
        kanji.setMeaning(request.meaning());
        kanji.setStrokeCount(request.strokeCount());
        kanji.setJlptLevel(request.jlptLevel());
        List<KanjiReading> readings = request.readings()
                .stream()
                .map(readingRequest -> {

                    KanjiReading reading = new KanjiReading();

                    reading.setReading(readingRequest.reading());
                    reading.setReadingType(
                            readingRequest.readingType()
                    );
                    reading.setKanji(kanji);

                    return reading;
                })
                .toList();
        List<KanjiExample> examples = request.examples()
                .stream()
                .map(exampleRequest -> {

                    KanjiExample example = new KanjiExample();

                    example.setJapanese(
                            exampleRequest.japanese()
                    );
                    example.setReading(
                            exampleRequest.reading()
                    );
                    example.setMeaning(
                            exampleRequest.meaning()
                    );
                    example.setKanji(kanji);

                    return example;
                })
                .toList();
        kanji.setReadings(readings);
        kanji.setExamples(examples);

        return kanji;
    }

}