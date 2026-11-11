package dev.luisvergara.manabi.admin;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.*;
import lombok.RequiredArgsConstructor;
import dev.luisvergara.manabi.admin.security.*;
import dev.luisvergara.manabi.content.editorial.*;
import dev.luisvergara.manabi.content.publication.*;
import dev.luisvergara.manabi.content.publication.contract.*;
import static dev.luisvergara.manabi.content.editorial.EditorialModels.*;

@Service @RequiredArgsConstructor
public class AdminEditorialService {
 public record Item(Kind kind,String publicId,State state,Map<String,Object> content,Long id,Long version){}
 public record Page(long total,int limit,int offset,List<Item> items){}
 public record ReviewView(Kind kind,String publicId,State state,boolean stale,String fingerprint,List<Map<String,Object>> dependencies){}
 private final AdminCatalogQuery catalogQuery;private final EditorialSnapshotService fingerprints;
 private final EditorialReviewService reviews;private final AdminAudit audit;
 private Key key(Kind kind,String id){
  if(kind==null||id==null||id.isBlank()||id.codePointCount(0,id.length())>128||id.codePoints().anyMatch(Character::isISOControl))throw new IllegalArgumentException("Invalid content identity");
  return new Key(PublicationSnapshotService.NAMESPACE,kind,id);
 }
 @PreAuthorize("hasAnyRole('ADMIN','EDITOR','REVIEWER','PUBLISHER')")
 public Page catalog(Kind filter,String query,int limit,int offset){
  return catalogQuery.page(filter,query,limit,offset);
 }
 @PreAuthorize("hasAnyRole('ADMIN','REVIEWER')")
 @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
 public ReviewView review(Kind kind,String id){
  var key=key(kind,id);var source=fingerprints.read(key);var status=reviews.status(key);
  var deps=source.dependencies().stream().map(d->{var s=reviews.status(d);return Map.<String,Object>of("kind",d.getKind().name(),"publicId",d.getPublicId(),"state",s.effectiveState().name());}).toList();
  return new ReviewView(kind,id,status.effectiveState(),status.stale(),status.fingerprint(),deps);
 }
 @PreAuthorize("hasAnyRole('ADMIN','REVIEWER')")
 @Transactional(isolation=Isolation.SERIALIZABLE)
 public ReviewView approve(Kind kind,String id,String expected){
  checkFingerprint(expected);reviews.approve(key(kind,id),expected,AdminIdentity.actor());audit.record(AdminIdentity.actor(),"APPROVE_CONTENT",kind+":"+id,"SUCCESS");return review(kind,id);
 }
 @PreAuthorize("hasAnyRole('ADMIN','REVIEWER')")
 @Transactional(isolation=Isolation.SERIALIZABLE)
 public ReviewView pending(Kind kind,String id,String expected){
  checkFingerprint(expected);var key=key(kind,id);if(!reviews.status(key).fingerprint().equals(expected))throw new IllegalStateException("Revision changed");
  reviews.pending(key);audit.record(AdminIdentity.actor(),"PENDING_CONTENT",kind+":"+id,"SUCCESS");return review(kind,id);
 }
 private void checkFingerprint(String value){if(value==null||!value.matches("[a-f0-9]{64}"))throw new IllegalArgumentException("Invalid review fingerprint");}
}
