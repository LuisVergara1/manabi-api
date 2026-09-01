package dev.luisvergara.manabi.service;

import java.util.List;

import org.springframework.stereotype.Service;

import dev.luisvergara.manabi.entity.phrase.Phrase;
import dev.luisvergara.manabi.enums.kanjis.JlptLevel;
import dev.luisvergara.manabi.enums.phrase.PhraseCategory;
import dev.luisvergara.manabi.repository.PhraseRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PhraseService {
    
     private final PhraseRepository phraseRepository;

    public List<Phrase> find(
            List<JlptLevel> levels,
            List<PhraseCategory> categories) {

        boolean hasLevels = levels != null && !levels.isEmpty();
        boolean hasCategories = categories != null && !categories.isEmpty();

        if (hasLevels && hasCategories) {
            return phraseRepository
                    .findDistinctByJlptLevelInAndCategoriesIn(
                            levels,
                            categories
                    );
        }

        if (hasLevels) {
            return phraseRepository.findByJlptLevelIn(levels);
        }

        if (hasCategories) {
            return phraseRepository
                    .findDistinctByCategoriesIn(categories);
        }

        return phraseRepository.findAll();
    }

    public Phrase findById(Long id) {
        return phraseRepository.findById(id)
                .orElseThrow(() ->
                        new RuntimeException("Frase no encontrada"));
    }

    public Phrase save(Phrase phrase) {
        return phraseRepository.save(phrase);
    }

    public Phrase update(Long id, Phrase phrase) {

        Phrase existingPhrase = phraseRepository.findById(id)
                .orElseThrow(() ->
                        new RuntimeException("Frase no encontrada"));

        existingPhrase.setJapanese(phrase.getJapanese());
        existingPhrase.setRomaji(phrase.getRomaji());
        existingPhrase.setMeaning(phrase.getMeaning());
        existingPhrase.setJlptLevel(phrase.getJlptLevel());
        existingPhrase.setCategories(phrase.getCategories());

        return phraseRepository.save(existingPhrase);
    }

    public void delete(Long id) {
        phraseRepository.deleteById(id);
    }
}
