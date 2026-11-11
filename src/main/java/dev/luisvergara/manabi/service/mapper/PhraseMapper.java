package dev.luisvergara.manabi.service.mapper;
import java.util.*;
import dev.luisvergara.manabi.service.ContentPublicIds;
import java.util.function.*;
import org.springframework.stereotype.Component;
import dev.luisvergara.manabi.dto.phrase.*;
import dev.luisvergara.manabi.entity.phrase.*;
import dev.luisvergara.manabi.enums.phrase.PhraseKind;
import dev.luisvergara.manabi.service.phrase.*;
@Component
public class PhraseMapper {
 @FunctionalInterface private interface Fill<E,R>{void accept(E entity,R request,int position);}
 private static <E,R> void sync(List<E> entities,List<R> requests,Function<E,String> publicId,Function<R,String> requestedId,
   Supplier<E> create,BiConsumer<E,String> assignId,Fill<E,R> fill,String prefix) {
  Map<String,E> previous=new HashMap<>();entities.forEach(e->previous.put(publicId.apply(e),e));
  List<E> next=new ArrayList<>();
  for(int i=0;i<requests.size();i++) {
   R r=requests.get(i);String id=requestedId.apply(r);E e;
   if(id==null){e=create.get();assignId.accept(e,ContentPublicIds.create(prefix,String.valueOf(r)));}
   else {e=previous.get(id);if(e==null)throw dev.luisvergara.manabi.service.ModelValidation.bad("Identidad hija no pertenece a esta frase");}
   fill.accept(e,r,i);next.add(e);
  }
  entities.clear();entities.addAll(next);
 }
 public void apply(Phrase p,PhraseRequest r,Collection<PhraseCategory> categories,Map<String,Particle> particles) {
  p.setKind(r.kind());p.setDisplayOrder(r.displayOrder());p.setJapanese(r.japanese());p.setKana(r.kana());p.setRomaji(r.romaji());
  p.setMeaning(r.meaning());p.setIconType(r.iconType());p.setIntroduction(r.introduction());p.setUsageNote(r.usageNote());
  p.getCategories().clear();p.getCategories().addAll(categories);
  p.getKeyPoints().clear();p.getKeyPoints().addAll(r.keyPoints()==null?List.of():r.keyPoints());
  sync(p.getExamples(),r.examples(),PhraseExample::getPublicId,PhraseRequest.Example::id,PhraseExample::new,PhraseExample::setPublicId,
   (e,x,i)->{e.setPhrase(p);e.setDisplayOrder(x.displayOrder());e.setJapanese(x.japanese());e.setKana(x.kana());e.setRomaji(x.romaji());e.setMeaning(x.meaning());e.setIconType(x.iconType());e.setSituation(x.situation());},"pex");
  sync(p.getMeanings(),r.meanings()==null?List.of():r.meanings(),PhraseMeaning::getPublicId,PhraseRequest.Meaning::id,PhraseMeaning::new,PhraseMeaning::setPublicId,
   (e,x,i)->{e.setPhrase(p);e.setPosition(i);e.setLabel(x.label());e.setExplanation(x.explanation());e.setIconType(x.iconType());},"pm");
  p.setPattern(r.pattern()==null?null:new PhrasePattern(r.pattern().formula(),r.pattern().explanation()));
  sync(p.getSlots(),r.pattern()==null?List.of():r.pattern().slots(),PhraseSlot::getPublicId,PhraseRequest.Slot::id,PhraseSlot::new,PhraseSlot::setPublicId,
   (e,x,i)->{e.setPhrase(p);e.setPosition(i);e.setLabel(x.label());e.setDescription(x.description());},"pslot");
  sync(p.getLearningKeys(),r.learningKeys()==null?List.of():r.learningKeys(),PhraseLearningKey::getPublicId,PhraseRequest.LearningKey::id,PhraseLearningKey::new,PhraseLearningKey::setPublicId,
   (e,x,i)->{e.setPhrase(p);e.setPosition(i);e.setIconType(x.iconType());e.setParticle(x.particleId()==null?null:particles.get(x.particleId()));
    e.setTitle(x.title());e.setExplanation(x.explanation());var a=x.example();e.setExample(a==null?null:new PhraseAuxiliaryExample(a.japanese(),a.kana(),a.romaji(),a.meaning()));},"plk");
 }
 public PhraseResponse response(Phrase p) {
  var categories=p.getCategories().stream().sorted(Comparator.comparing(PhraseCategory::getDisplayOrder).thenComparing(PhraseCategory::getPublicId)).map(PhraseCategory::getPublicId).toList();
  var examples=p.getExamples().stream().sorted(Comparator.comparing(PhraseExample::getDisplayOrder).thenComparing(PhraseExample::getPublicId))
   .map(e->new PhraseRequest.Example(e.getPublicId(),e.getDisplayOrder(),e.getJapanese(),e.getKana(),e.getRomaji(),e.getMeaning(),e.getIconType(),e.getSituation())).toList();
  var meanings=p.getKind()==PhraseKind.FIXED?p.getMeanings().stream().sorted(Comparator.comparingInt(PhraseMeaning::getPosition))
   .map(e->new PhraseRequest.Meaning(e.getPublicId(),e.getLabel(),e.getExplanation(),e.getIconType())).toList():null;
  var slots=p.getSlots().stream().sorted(Comparator.comparingInt(PhraseSlot::getPosition)).map(e->new PhraseRequest.Slot(e.getPublicId(),e.getLabel(),e.getDescription())).toList();
  var keys=p.getLearningKeys().stream().sorted(Comparator.comparingInt(PhraseLearningKey::getPosition)).map(e->{
   var a=e.getExample();return new PhraseRequest.LearningKey(e.getPublicId(),e.getIconType(),e.getParticle()==null?null:e.getParticle().getPublicId(),e.getTitle(),e.getExplanation(),
    a==null?null:new PhraseRequest.Text(a.getJapanese(),a.getKana(),a.getRomaji(),a.getMeaning()));}).toList();
  var pattern=p.getPattern()==null?null:new PhraseRequest.Pattern(p.getPattern().getFormula(),p.getPattern().getExplanation(),slots);
  return new PhraseResponse(p.getId(),p.getPublicId(),p.getVersion(),p.getKind(),p.getDisplayOrder(),categories,p.getJapanese(),p.getKana(),p.getRomaji(),p.getMeaning(),p.getIconType(),
   p.getIntroduction(),p.getUsageNote(),List.copyOf(p.getKeyPoints()),keys,examples,meanings,pattern);
 }
}
