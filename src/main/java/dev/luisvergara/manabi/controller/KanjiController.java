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
import dev.luisvergara.manabi.enums.kanjis.JlptLevel;
import dev.luisvergara.manabi.service.KanjiService;
import org.springframework.web.bind.annotation.RequestBody;
import dev.luisvergara.manabi.dto.kanjis.KanjiResponse;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/kanji")
@RequiredArgsConstructor
public class KanjiController {

    private final KanjiService kanjiService;

    @GetMapping
    public List<KanjiResponse> find(
            @RequestParam(required = false) List<JlptLevel> levels) {

        return kanjiService.responses(levels);
    }

    @GetMapping("/{id}")
    public KanjiResponse findById(@PathVariable Long id) {
        return kanjiService.response(id);
    }

    @PostMapping
    public KanjiResponse save(@RequestBody KanjiRequest request) {
        return kanjiService.save(request);
    }

    @org.springframework.web.bind.annotation.PutMapping("/{id}")
    public KanjiResponse update(@PathVariable Long id, @RequestBody KanjiRequest r) {
        return kanjiService.updateResponse(id, r);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        kanjiService.delete(id);
    }
}
