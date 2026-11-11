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

import dev.luisvergara.manabi.dto.vocabulary.*;
import dev.luisvergara.manabi.service.VocabularyService;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/vocabulary")
@RequiredArgsConstructor
public class VocabularyController {

    private final VocabularyService vocabularyService;

    @GetMapping("/categories")
    public java.util.List<dev.luisvergara.manabi.dto.vocabulary.VocabularyCategoryResponse> categories(){return vocabularyService.categories();}
    @PutMapping("/categories/{id}")
    public dev.luisvergara.manabi.dto.vocabulary.VocabularyCategoryResponse updateCategory(@PathVariable Long id,@RequestBody dev.luisvergara.manabi.dto.vocabulary.VocabularyCategoryRequest request){return vocabularyService.updateCategory(id,request);}
    @GetMapping
    public List<VocabularyResponse> find(
            @RequestParam(required = false) List<String> categories) {

        return vocabularyService.responses(categories);
    }

    @GetMapping("/{id}")
    public VocabularyResponse findById(@PathVariable Long id) {
        return vocabularyService.response(id);
    }

    @PostMapping
    public VocabularyResponse save(@RequestBody VocabularyRequest vocabulary) {
        return vocabularyService.save(vocabulary);
    }

    @PutMapping("/{id}")
    public VocabularyResponse update(
            @PathVariable Long id,
            @RequestBody VocabularyRequest vocabulary) {

        return vocabularyService.update(id, vocabulary);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        vocabularyService.delete(id);
    }
}
