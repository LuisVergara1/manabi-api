package dev.luisvergara.manabi.service;

import java.util.List;

import org.springframework.stereotype.Service;

import dev.luisvergara.manabi.dto.kanjis.KanjiRequest;
import dev.luisvergara.manabi.entity.kanjis.Kanji;
import dev.luisvergara.manabi.entity.kanjis.KanjiExample;
import dev.luisvergara.manabi.entity.kanjis.KanjiReading;
import dev.luisvergara.manabi.enums.kanjis.JlptLevel;
import dev.luisvergara.manabi.repository.KanjiRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class KanjiService {
    
    private final KanjiRepository kanjiRepository;

    public List<Kanji> find(List<JlptLevel> levels) {

        if (levels == null || levels.isEmpty()) {
            return kanjiRepository.findAll();
        }

        return kanjiRepository.findByJlptLevelIn(levels);
    }

    public Kanji findById(Long id) {
        return kanjiRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Kanji no encontrado"));
    }

    public Kanji save(KanjiRequest request) {

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
                reading.setReadingType(readingRequest.readingType());
                reading.setKanji(kanji);

                return reading;
            })
            .toList();

    List<KanjiExample> examples = request.examples()
            .stream()
            .map(exampleRequest -> {

                KanjiExample example = new KanjiExample();

                example.setJapanese(exampleRequest.japanese());
                example.setReading(exampleRequest.reading());
                example.setMeaning(exampleRequest.meaning());
                example.setKanji(kanji);

                return example;
            })
            .toList();

    kanji.setReadings(readings);
    kanji.setExamples(examples);

    return kanjiRepository.save(kanji);
}

    public Kanji update(Long id, Kanji kanji) {

        Kanji existingKanji = kanjiRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Kanji no encontrado"));

        existingKanji.setCharacter(kanji.getCharacter());
        existingKanji.setMeaning(kanji.getMeaning());
        existingKanji.setStrokeCount(kanji.getStrokeCount());
        existingKanji.setJlptLevel(kanji.getJlptLevel());
        existingKanji.setReadings(kanji.getReadings());
        existingKanji.setExamples(kanji.getExamples());

        return kanjiRepository.save(existingKanji);
    }

    public void delete(Long id) {
        kanjiRepository.deleteById(id);
    }
}
