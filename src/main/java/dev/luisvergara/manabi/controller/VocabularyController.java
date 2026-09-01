package dev.luisvergara.manabi.controller;

import java.util.List;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import dev.luisvergara.manabi.entity.vocabulary.Vocabulary;
import dev.luisvergara.manabi.enums.vocabulary.VocabularyCategory;
import dev.luisvergara.manabi.service.VocabularyService;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/vocabulary")
@RequiredArgsConstructor
public class VocabularyController {
    
    private final VocabularyService vocabularyService;

    @GetMapping
    public List<Vocabulary> find(
            @RequestParam(required = false) List<VocabularyCategory> categories) {

        return vocabularyService.find(categories);
    }

    @GetMapping("/{id}")
    public Vocabulary findById(@PathVariable Long id) {
        return vocabularyService.findById(id);
    }

    @PostMapping
    public Vocabulary save(@RequestBody Vocabulary vocabulary) {
        return vocabularyService.save(vocabulary);
    }

    @PutMapping("/{id}")
    public Vocabulary update(
            @PathVariable Long id,
            @RequestBody Vocabulary vocabulary) {

        return vocabularyService.update(id, vocabulary);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        vocabularyService.delete(id);
    }
}
