package dev.luisvergara.manabi.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import dev.luisvergara.manabi.entity.kana.Kana;
import dev.luisvergara.manabi.enums.kana.KanaGroup;
import dev.luisvergara.manabi.enums.kana.KanaType;
import dev.luisvergara.manabi.service.KanaService;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import lombok.RequiredArgsConstructor;

import java.util.List;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;


@RestController
@RequestMapping("/api/kana")
@RequiredArgsConstructor
public class KanaController {

    private final KanaService kanaService;

    @GetMapping("/type/{type}")
    public List<Kana> findByType(@PathVariable KanaType type) {
        return kanaService.findByType(type);
    }
    @GetMapping("/group/{group}")
    public List<Kana> findByGroup(@PathVariable KanaGroup group) {
        return kanaService.findByGroup(group);
    }

    @GetMapping
    public List<Kana> find(
        @RequestParam(required = false) KanaType type,
        @RequestParam(required = false) KanaGroup group) {
           return kanaService.find(type, group);
}

@PostMapping
public Kana save(@RequestBody Kana kana) {
    return kanaService.save(kana);
}

@PutMapping("/{id}")
public Kana update(
        @PathVariable Long id,
        @RequestBody Kana kana) {

    return kanaService.update(id, kana);
}

@DeleteMapping("/{id}")
public void delete(@PathVariable Long id) {
    kanaService.delete(id);
}
    
}
