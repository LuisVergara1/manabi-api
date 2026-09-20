package dev.luisvergara.manabi.service;

import java.util.List;

import org.springframework.stereotype.Service;

import dev.luisvergara.manabi.dto.kanjis.KanjiRequest;
import dev.luisvergara.manabi.entity.kanjis.Kanji;
import dev.luisvergara.manabi.enums.kanjis.JlptLevel;
import dev.luisvergara.manabi.repository.KanjiRepository;
import dev.luisvergara.manabi.service.mapper.KanjiMapper;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class KanjiService {
    
    private final KanjiRepository kanjiRepository;
    private final KanjiMapper kanjiMapper;

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
    Kanji kanji = kanjiMapper.toEntity(request);
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
