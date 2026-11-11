package dev.luisvergara.manabi.admin;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import dev.luisvergara.manabi.content.editorial.*;
import dev.luisvergara.manabi.content.publication.PublicationSnapshotService;
import dev.luisvergara.manabi.entity.kana.Kana;
import dev.luisvergara.manabi.entity.kanjis.Kanji;
import dev.luisvergara.manabi.entity.vocabulary.*;
import dev.luisvergara.manabi.entity.phrase.*;
import static dev.luisvergara.manabi.content.editorial.EditorialModels.*;

/** SQL pagination first; project only the requested page and its shared dependencies. */
@Service @RequiredArgsConstructor
@org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('ADMIN','EDITOR','REVIEWER','PUBLISHER')")
public class AdminCatalogQuery {
 private final JdbcTemplate jdbc;private final EntityManager em;
 private final EditorialSnapshotService fingerprints;private final EditorialReviewService reviews;
 private static final String NS=PublicationSnapshotService.NAMESPACE;
 private static final String UNION="""
 select 'KANA' as kind,public_id,id,version,display_order,concat_ws(' ',public_id,kana_character,romaji) as search from kana
 union all select 'KANJI',public_id,id,version,display_order,concat_ws(' ',public_id,kanji_character,meaning,(select string_agg(concat_ws(' ',r.reading,r.romaji),' ') from kanji_reading r where r.kanji_id=kanji.id)) from kanji
 union all select 'VOCABULARY',public_id,id,version,display_order,concat_ws(' ',public_id,japanese,kana,romaji,meaning) from vocabulary
 union all select 'VOCABULARY_CATEGORY',code,id,version,display_order,concat_ws(' ',code,name) from vocabulary_category
 union all select 'PHRASE',public_id,id,version,display_order,concat_ws(' ',public_id,japanese,kana,romaji,meaning) from phrase
 union all select 'PHRASE_CATEGORY',public_id,id,version,display_order,concat_ws(' ',public_id,name,description) from phrase_category
 union all select 'PARTICLE',public_id,id,version,0,concat_ws(' ',public_id,japanese,title,explanation) from particle
 """;
 record Identity(Kind kind,String publicId,long id,long version){}
 @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
 public AdminEditorialService.Page page(Kind kind,String query,int limit,int offset){
  if(limit<1||limit>100||offset<0||offset>1000000||query==null||query.length()>128)throw new IllegalArgumentException("Invalid catalog query");
  String from=" from ("+UNION+") c where strpos(lower(search),?)>0"+(kind==null?"":" and kind=?");
  var args=new ArrayList<Object>();args.add(query.toLowerCase(Locale.ROOT));if(kind!=null)args.add(kind.name());
  long total=jdbc.queryForObject("select count(*)"+from,Long.class,args.toArray());args.add(limit);args.add(offset);
  var ids=jdbc.query("select kind,public_id,id,version"+from+" order by kind collate \"C\",display_order,public_id collate \"C\" limit ? offset ?",
   (rs,n)->new Identity(Kind.valueOf(rs.getString(1)),rs.getString(2),rs.getLong(3),rs.getLong(4)),args.toArray());
  if(ids.isEmpty())return new AdminEditorialService.Page(total,limit,offset,List.of());
  var entities=new LinkedHashMap<Key,Object>();
  for(Kind k:Kind.values()){
   var names=ids.stream().filter(i->i.kind()==k).map(Identity::publicId).toList();if(names.isEmpty())continue;
   String type=switch(k){case KANA->"Kana";case KANJI->"Kanji";case VOCABULARY->"Vocabulary";case VOCABULARY_CATEGORY->"VocabularyCategory";case PHRASE->"Phrase";case PHRASE_CATEGORY->"PhraseCategory";case PARTICLE->"Particle";};
   String field=k==Kind.VOCABULARY_CATEGORY?"code":"publicId";
   String fetch=k==Kind.KANJI?" left join fetch e.readings":k==Kind.VOCABULARY?" left join fetch e.categories":"";
   var rows=em.createQuery("select distinct e from "+type+" e"+fetch+" where e."+field+" in :ids").setParameter("ids",names).getResultList();
   if(k==Kind.PHRASE){
    for(String association:List.of("categories","examples","meanings","slots","keyPoints"))
     em.createQuery("select distinct p from Phrase p left join fetch p."+association+" where p.publicId in :ids",Phrase.class).setParameter("ids",names).getResultList();
    em.createQuery("select distinct p from Phrase p left join fetch p.learningKeys l left join fetch l.particle where p.publicId in :ids",Phrase.class).setParameter("ids",names).getResultList();
   }
   for(Object e:rows){
    String id=switch(e){case Kana v->v.getPublicId();case Kanji v->v.getPublicId();case Vocabulary v->v.getPublicId();case VocabularyCategory v->v.getCode();case Phrase v->v.getPublicId();case PhraseCategory v->v.getPublicId();case Particle v->v.getPublicId();default->throw new IllegalStateException();};
    entities.put(new Key(NS,k,id),e);
    if(e instanceof Vocabulary v)v.getCategories().forEach(c->entities.put(new Key(NS,Kind.VOCABULARY_CATEGORY,c.getCode()),c));
    if(e instanceof Phrase p){
     p.getCategories().forEach(c->entities.put(new Key(NS,Kind.PHRASE_CATEGORY,c.getPublicId()),c));
     p.getLearningKeys().stream().map(PhraseLearningKey::getParticle).filter(Objects::nonNull).forEach(particle->entities.put(new Key(NS,Kind.PARTICLE,particle.getPublicId()),particle));
    }
   }
  }
  var captured=fingerprints.capture(entities);
  var publicIds=entities.keySet().stream().map(Key::getPublicId).distinct().toList();
  var privateRows=em.createQuery("select r from EditorialReview r where r.id.namespace=:ns and r.id.publicId in :ids",Review.class).setParameter("ns",NS).setParameter("ids",publicIds).getResultList();
  var states=reviews.statuses(captured,privateRows);
  var items=ids.stream().map(i->{var key=new Key(NS,i.kind(),i.publicId());return new AdminEditorialService.Item(i.kind(),i.publicId(),states.get(key).effectiveState(),captured.get(key).content(),i.id(),i.version());}).toList();
  return new AdminEditorialService.Page(total,limit,offset,items);
 }
 @Transactional(readOnly=true)
 public Map<String,Object> identity(Kind kind,String publicId){
  if(kind==null||publicId==null||publicId.isBlank()||publicId.length()>256)throw new IllegalArgumentException("Invalid identity");
  var rows=jdbc.queryForList("select kind,public_id,id,version from ("+UNION+") c where kind=? and public_id=?",kind.name(),publicId);
  if(rows.size()!=1)throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND);
  var row=rows.getFirst();return Map.of("kind",kind,"publicId",publicId,"id",row.get("id"),"version",row.get("version"));
 }
}
