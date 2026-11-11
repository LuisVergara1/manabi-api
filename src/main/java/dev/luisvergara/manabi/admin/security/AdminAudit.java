package dev.luisvergara.manabi.admin.security;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
/** Fixed-field private audit only. Never accepts request bodies, credentials, cookies or token values. */
@Service @RequiredArgsConstructor
public class AdminAudit {
 private final JdbcTemplate jdbc;
 @Transactional(propagation=org.springframework.transaction.annotation.Propagation.MANDATORY)
 public void publication(String action,String target){
  String actor;
  try{actor=AdminIdentity.actor();}catch(org.springframework.security.access.AccessDeniedException internal){actor="internal";}
  record(actor,action,target,"SUCCESS");
 }
 @Transactional
 public void record(String actor,String action,String target,String outcome){
  jdbc.update("insert into admin_audit(actor,action,target,outcome,occurred_at) values (?,?,?,?,?)",
   safe(actor,64),safe(action,48),safe(target,256),outcome,Timestamp.from(Instant.now()));
 }
 private String safe(String value,int max){
  String s=value==null?"anonymous":value.replaceAll("[\\p{Cntrl}]","?");
  return s.codePointCount(0,s.length())<=max?s:s.substring(0,s.offsetByCodePoints(0,max));
 }
 public List<Map<String,Object>> recent(int limit,long before){
  if(limit<1||limit>100||before<0)throw new IllegalArgumentException("Invalid audit cursor");
  return jdbc.queryForList("select id,actor,action,target,outcome,occurred_at from admin_audit where (?=0 or id<?) order by id desc limit ?",before,before,limit);
 }
}
