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

import dev.luisvergara.manabi.entity.phrase.Phrase;
import dev.luisvergara.manabi.enums.kanjis.JlptLevel;
import dev.luisvergara.manabi.enums.phrase.PhraseCategory;
import dev.luisvergara.manabi.service.PhraseService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/phrases")
@RequiredArgsConstructor
public class PhraseController {
    

    private final PhraseService phraseService;

    @GetMapping
    public List<Phrase> find(
            @RequestParam(required = false) List<JlptLevel> levels,
            @RequestParam(required = false) List<PhraseCategory> categories) {

        return phraseService.find(levels, categories);
    }

    @GetMapping("/{id}")
    public Phrase findById(@PathVariable Long id) {
        return phraseService.findById(id);
    }

    @PostMapping
    public Phrase save(@RequestBody Phrase phrase) {
        return phraseService.save(phrase);
    }

    @PutMapping("/{id}")
    public Phrase update(
            @PathVariable Long id,
            @RequestBody Phrase phrase) {

        return phraseService.update(id, phrase);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        phraseService.delete(id);
    }
}
