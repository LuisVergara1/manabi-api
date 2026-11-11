package dev.luisvergara.manabi.service;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Sort;
import lombok.RequiredArgsConstructor;
import dev.luisvergara.manabi.dto.phrase.*;
import dev.luisvergara.manabi.entity.phrase.*;
import dev.luisvergara.manabi.repository.*;
import dev.luisvergara.manabi.service.phrase.*;
@Service @RequiredArgsConstructor @Transactional(readOnly=true)
public class PhraseReferenceService {
 private final PhraseCategoryRepository categories;private final ParticleRepository particles;
 private final PhraseValidation validation;private final ModelValidation versions;
 private final PhraseRepository phrases;
 private PhraseCategoryResponse category(PhraseCategory c){return new PhraseCategoryResponse(c.getId(),c.getPublicId(),c.getName(),c.getDescription(),c.getDisplayOrder(),c.isEnabled(),c.getVersion());}
 private ParticleResponse particle(Particle p){return new ParticleResponse(p.getId(),p.getPublicId(),p.getJapanese(),p.getTitle(),p.getExplanation(),p.getVersion());}
 public List<PhraseCategoryResponse> categories(){return categories.findAll(Sort.by("displayOrder","publicId")).stream().map(this::category).toList();}
 @Transactional public PhraseCategoryResponse updateCategory(Long id,PhraseCategoryRequest r) {
  validation.fields(r);var c=categories.findById(id).orElseThrow(ModelValidation::missing);versions.version(r.version(),c.getVersion());
  c.setName(r.name());c.setDescription(r.description());c.setDisplayOrder(r.displayOrder());c.setEnabled(r.enabled());
  return category(categories.saveAndFlush(c));
 }
 public List<ParticleResponse> particles(){return particles.findAll(Sort.by("publicId")).stream().map(this::particle).toList();}
 @Transactional public ParticleResponse createParticle(ParticleRequest r){
  validation.fields(r);if(r.version()!=null)throw ModelValidation.bad("Un alta no tiene version");
  var p=new Particle();p.setPublicId(ContentPublicIds.create("part",r.japanese()));apply(p,r);return particle(particles.saveAndFlush(p));
 }
 @Transactional public ParticleResponse updateParticle(Long id,ParticleRequest r){
  validation.fields(r);var p=particles.findById(id).orElseThrow(ModelValidation::missing);versions.version(r.version(),p.getVersion());apply(p,r);
  return particle(particles.saveAndFlush(p));
 }
 private void apply(Particle p,ParticleRequest r){p.setJapanese(r.japanese());p.setTitle(r.title());p.setExplanation(r.explanation());}
 @Transactional public void deleteParticle(Long id) {
  var particle = particles.findById(id).orElseThrow(ModelValidation::missing);
  if (phrases.existsByLearningKeys_Particle_Id(id))
   throw new org.springframework.web.server.ResponseStatusException(
    org.springframework.http.HttpStatus.CONFLICT, "La particula esta referenciada por una frase");
  // The FK still protects concurrent references created after this check.
  particles.delete(particle); particles.flush();
 }
}
