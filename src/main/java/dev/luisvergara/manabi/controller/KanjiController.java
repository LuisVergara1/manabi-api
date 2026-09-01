package dev.luisvergara.manabi.controller;

import java.util.List;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import dev.luisvergara.manabi.dto.kanjis.KanjiRequest;
import dev.luisvergara.manabi.entity.kanjis.Kanji;
import dev.luisvergara.manabi.enums.kanjis.JlptLevel;
import dev.luisvergara.manabi.service.KanjiService;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/kanji")
@RequiredArgsConstructor
public class KanjiController {
    
    private final KanjiService kanjiService;

    @GetMapping
    public List<Kanji> find(
            @RequestParam(required = false) List<JlptLevel> levels) {

        return kanjiService.find(levels);
    }

    @GetMapping("/{id}")
    public Kanji findById(@PathVariable Long id) {
        return kanjiService.findById(id);
    }

    @PostMapping
    public Kanji save(@RequestBody KanjiRequest request) {
        return kanjiService.save(request);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        kanjiService.delete(id);
    }
}
