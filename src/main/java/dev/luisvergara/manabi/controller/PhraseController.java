package dev.luisvergara.manabi.controller;
import java.util.List;
import org.springframework.web.bind.annotation.*;
import lombok.RequiredArgsConstructor;
import dev.luisvergara.manabi.dto.phrase.*;
import dev.luisvergara.manabi.enums.phrase.PhraseKind;
import dev.luisvergara.manabi.service.PhraseService;
@RestController @RequestMapping("/api/phrases") @RequiredArgsConstructor
public class PhraseController {
 private final PhraseService phrases;
 @GetMapping public List<PhraseResponse> find(@RequestParam(required=false) PhraseKind kind,@RequestParam(required=false) List<String> categoryIds){return phrases.find(kind,categoryIds);}
 @GetMapping("/{id}") public PhraseResponse findById(@PathVariable Long id){return phrases.findById(id);}
 @PostMapping public PhraseResponse save(@RequestBody PhraseRequest r){return phrases.save(r);}
 @PutMapping("/{id}") public PhraseResponse update(@PathVariable Long id,@RequestBody PhraseRequest r){return phrases.update(id,r);}
 @DeleteMapping("/{id}") public void delete(@PathVariable Long id){phrases.delete(id);}
}
