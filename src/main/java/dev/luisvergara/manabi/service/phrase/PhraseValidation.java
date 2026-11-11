package dev.luisvergara.manabi.service.phrase;
import java.util.*;
import java.lang.reflect.*;
import org.springframework.stereotype.Component;
import lombok.RequiredArgsConstructor;
import dev.luisvergara.manabi.dto.phrase.PhraseRequest;
import dev.luisvergara.manabi.enums.phrase.PhraseKind;
import dev.luisvergara.manabi.service.ModelValidation;
@Component @RequiredArgsConstructor
public class PhraseValidation {
 private final ModelValidation beans;
 private static final Set<String> ICONS=Set.of("attention","apology","conversation","restaurant","shopping","transport","repeat","meanings","examples","key-points","usage");
 public <T> T fields(T value) {beans.valid(value); scan(value); return value;}
 public void phrase(PhraseRequest r) {
  fields(r); unique(r.categoryIds());
  icon(r.iconType());
  if(r.kind()==PhraseKind.FIXED && r.pattern()!=null) bad("FIXED no admite pattern");
  if(r.kind()==PhraseKind.PATTERN && (r.pattern()==null || r.meanings()!=null)) bad("PATTERN requiere pattern y omite meanings");
  ids(r.examples().stream().map(PhraseRequest.Example::id).toList()); r.examples().forEach(e->icon(e.iconType()));
  if(r.meanings()!=null) {ids(r.meanings().stream().map(PhraseRequest.Meaning::id).toList());r.meanings().forEach(m->icon(m.iconType()));}
  if(r.pattern()!=null) ids(r.pattern().slots().stream().map(PhraseRequest.Slot::id).toList());
  if(r.learningKeys()!=null) {
   ids(r.learningKeys().stream().map(PhraseRequest.LearningKey::id).toList());
   for(var k:r.learningKeys()) {
    icon(k.iconType());
    if(k.particleId()!=null) {if(k.title()!=null||k.explanation()!=null)bad("particleId no admite sobreescrituras");}
    else if(k.title()==null||k.explanation()==null) bad("Una clave local requiere title y explanation");
   }
  }
 }
 private static void ids(List<String> values) {unique(values.stream().filter(Objects::nonNull).toList());}
 private static void unique(List<String> values) {if(new HashSet<>(values).size()!=values.size())bad("Referencia o identidad duplicada");}
 private static void icon(String value) {if(!ICONS.contains(value))bad("Icono no registrado");}
 public static void bad(String message) {throw ModelValidation.bad(message);}
 private static void scan(Object o) {
  if(o==null)return;
  if(o instanceof String s) {
   if(s.replaceAll("[\\s\\u00a0\\u1680\\u2000-\\u200a\\u2028\\u2029\\u202f\\u205f\\u3000\\ufeff]","").isEmpty())bad("Texto vacio");
   for(int i=0;i<s.length();i++){char c=s.charAt(i);
    if(c==0||c=='\uFFFD')bad("Unicode invalido");
    if(Character.isHighSurrogate(c)){if(i+1>=s.length()||!Character.isLowSurrogate(s.charAt(++i)))bad("Unicode invalido");}
    else if(Character.isLowSurrogate(c))bad("Unicode invalido");
   }
  } else if(o instanceof Collection<?> c)c.forEach(PhraseValidation::scan);
  else if(o instanceof Record r)for(var c:r.getClass().getRecordComponents())try{scan(c.getAccessor().invoke(r));}catch(ReflectiveOperationException e){throw new IllegalStateException(e);}
 }
}
