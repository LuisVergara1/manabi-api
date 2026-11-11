package dev.luisvergara.manabi.controller;
import java.util.List;
import org.springframework.web.bind.annotation.*;
import dev.luisvergara.manabi.dto.kana.*;
import dev.luisvergara.manabi.entity.kana.Kana;
import dev.luisvergara.manabi.enums.kana.*;
import dev.luisvergara.manabi.service.*;
import lombok.RequiredArgsConstructor;

@RestController @RequestMapping("/api/kana") @RequiredArgsConstructor
public class KanaController {
    private final KanaService service;
    private final ModelValidation validation;
    private Kana entity(KanaRequest r) {
        validation.valid(r);
        Kana k = new Kana(); k.setCharacter(r.character()); k.setRomaji(r.romaji());
        k.setType(r.type()); k.setGroup(r.group()); k.setDisplayOrder(r.displayOrder()); k.setVersion(r.version());
        return k;
    }
    @GetMapping public List<KanaResponse> find(@RequestParam(required=false) KanaType type,
            @RequestParam(required=false) KanaGroup group) {
        return service.find(type, group).stream().map(KanaResponse::from).toList();
    }
    @GetMapping("/{id}") public KanaResponse findById(@PathVariable Long id) { return KanaResponse.from(service.findById(id)); }
    @GetMapping("/type/{type}") public List<KanaResponse> type(@PathVariable KanaType type) {
        return service.findByType(type).stream().map(KanaResponse::from).toList();
    }
    @GetMapping("/group/{group}") public List<KanaResponse> group(@PathVariable KanaGroup group) {
        return service.findByGroup(group).stream().map(KanaResponse::from).toList();
    }
    @PostMapping public KanaResponse save(@RequestBody KanaRequest r) { return KanaResponse.from(service.save(entity(r))); }
    @PutMapping("/{id}") public KanaResponse update(@PathVariable Long id, @RequestBody KanaRequest r) {
        return KanaResponse.from(service.update(id, entity(r)));
    }
    @DeleteMapping("/{id}") public void delete(@PathVariable Long id) { service.delete(id); }
}
