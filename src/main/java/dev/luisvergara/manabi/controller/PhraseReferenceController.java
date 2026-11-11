package dev.luisvergara.manabi.controller;
import java.util.List;
import org.springframework.web.bind.annotation.*;
import lombok.RequiredArgsConstructor;
import dev.luisvergara.manabi.dto.phrase.*;
import dev.luisvergara.manabi.service.PhraseReferenceService;
@RestController @RequestMapping("/api") @RequiredArgsConstructor
public class PhraseReferenceController {
 private final PhraseReferenceService refs;
 @GetMapping("/phrase-categories") public List<PhraseCategoryResponse> categories(){return refs.categories();}
 @PutMapping("/phrase-categories/{id}") public PhraseCategoryResponse updateCategory(@PathVariable Long id,@RequestBody PhraseCategoryRequest r){return refs.updateCategory(id,r);}
 @GetMapping("/particles") public List<ParticleResponse> particles(){return refs.particles();}
 @PostMapping("/particles") public ParticleResponse createParticle(@RequestBody ParticleRequest r){return refs.createParticle(r);}
 @PutMapping("/particles/{id}") public ParticleResponse updateParticle(@PathVariable Long id,@RequestBody ParticleRequest r){return refs.updateParticle(id,r);}
 @DeleteMapping("/particles/{id}") public void deleteParticle(@PathVariable Long id){refs.deleteParticle(id);}
}
