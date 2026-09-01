package dev.luisvergara.manabi.service;

import java.util.List;

import org.springframework.stereotype.Service;

import dev.luisvergara.manabi.entity.vocabulary.Vocabulary;
import dev.luisvergara.manabi.enums.vocabulary.VocabularyCategory;
import dev.luisvergara.manabi.repository.VocabularyRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class VocabularyService {
    
    private final VocabularyRepository vocabularyRepository;

    public List<Vocabulary> find(List<VocabularyCategory> categories) {

        if (categories == null || categories.isEmpty()) {
            return vocabularyRepository.findAll();
        }

        return vocabularyRepository.findDistinctByCategoriesIn(categories);
    }

    public Vocabulary findById(Long id) {
        return vocabularyRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Vocabulario no encontrado"));
    }

    public Vocabulary save(Vocabulary vocabulary) {
        return vocabularyRepository.save(vocabulary);
    }

    public Vocabulary update(Long id, Vocabulary vocabulary) {

        Vocabulary existingVocabulary = vocabularyRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Vocabulario no encontrado"));

        existingVocabulary.setJapanese(vocabulary.getJapanese());
        existingVocabulary.setReading(vocabulary.getReading());
        existingVocabulary.setMeaning(vocabulary.getMeaning());
        existingVocabulary.setCategories(vocabulary.getCategories());

        return vocabularyRepository.save(existingVocabulary);
    }

    public void delete(Long id) {
        vocabularyRepository.deleteById(id);
    }

}
