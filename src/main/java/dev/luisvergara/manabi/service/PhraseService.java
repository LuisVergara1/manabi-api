package dev.luisvergara.manabi.service;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import dev.luisvergara.manabi.dto.phrase.*;
import dev.luisvergara.manabi.entity.phrase.*;
import dev.luisvergara.manabi.enums.phrase.PhraseKind;
import dev.luisvergara.manabi.repository.*;
import dev.luisvergara.manabi.service.mapper.PhraseMapper;
import dev.luisvergara.manabi.service.phrase.*;
@Service @RequiredArgsConstructor @Transactional(readOnly=true)
public class PhraseService {
 private final PhraseRepository phrases;
 private final PhraseCategoryRepository categories;
 private final ParticleRepository particles;
 private final PhraseValidation validation;
 private final ModelValidation models;
 private final PhraseMapper mapper;
 private final EntityManager entityManager;
 private static final Sort ORDER=Sort.by("displayOrder","publicId");
 public List<PhraseResponse> find(PhraseKind kind,List<String> categoryIds) {
  Specification<Phrase> spec=(root,query,cb)->{
   var filters=new ArrayList<Predicate>();
   if(kind!=null)filters.add(cb.equal(root.get("kind"),kind));
   if(categoryIds!=null&&!categoryIds.isEmpty()){query.distinct(true);filters.add(root.join("categories").get("publicId").in(categoryIds));}
   return cb.and(filters.toArray(Predicate[]::new));
  };
  return phrases.findAll(spec,ORDER).stream().map(mapper::response).toList();
 }
 public PhraseResponse findById(Long id){return mapper.response(required(id));}
 private Phrase required(Long id){return phrases.findById(id).orElseThrow(ModelValidation::missing);}
 private void apply(Phrase p,PhraseRequest r) {
  var resolved=categories.findByPublicIdIn(r.categoryIds());
  if(resolved.size()!=r.categoryIds().size())throw ModelValidation.bad("Categoria de Frases inexistente");
  Map<String,Particle> refs=new HashMap<>();
  for(var k:r.learningKeys()==null?List.<PhraseRequest.LearningKey>of():r.learningKeys())
   if(k.particleId()!=null) refs.computeIfAbsent(k.particleId(),key->particles.findByPublicId(key).orElseThrow(()->ModelValidation.bad("Particula inexistente")));
  mapper.apply(p,r,resolved,refs);
 }
 @Transactional public PhraseResponse save(PhraseRequest r) {
  validation.phrase(r);if(r.version()!=null)throw ModelValidation.bad("Un alta no tiene version");
  Phrase p=new Phrase();p.setPublicId(ContentPublicIds.create("phr",r.japanese()));apply(p,r);
  return mapper.response(phrases.saveAndFlush(p));
 }
 @Transactional public PhraseResponse update(Long id,PhraseRequest r) {
  validation.phrase(r);Phrase p=required(id);models.version(r.version(),p.getVersion());apply(p,r);
  entityManager.lock(p,LockModeType.PESSIMISTIC_FORCE_INCREMENT);
  return mapper.response(phrases.saveAndFlush(p));
 }
 @Transactional public void delete(Long id){phrases.delete(required(id));}
}
