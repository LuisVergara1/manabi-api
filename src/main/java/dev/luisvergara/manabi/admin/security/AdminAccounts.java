package dev.luisvergara.manabi.admin.security;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import lombok.RequiredArgsConstructor;
/** Internal account repository and atomic mutations. No public registration or raw-password persistence. */
@Service @RequiredArgsConstructor @Transactional(readOnly=true)
public class AdminAccounts implements UserDetailsService {
 public record User(String username,boolean enabled,Set<AdminRole> roles,long revision){}
 private final JdbcTemplate jdbc;private final PasswordEncoder encoder;private final AdminAudit audit;
 public static void username(String value){
  if(value==null||!value.matches("[A-Za-z0-9][A-Za-z0-9._-]{2,63}"))throw new IllegalArgumentException("Invalid administrative username");
 }
 public static void password(String value){
  if(value==null||value.codePointCount(0,value.length())<16||value.getBytes(StandardCharsets.UTF_8).length>72
    ||value.indexOf('\0')>=0||value.codePoints().distinct().count()<8||value.isBlank()||value.matches("(.)\\1+")
    ||value.toLowerCase(Locale.ROOT).matches(".*(changeme|change-me|replace-me|password123|example-password).*"))
   throw new IllegalArgumentException("Administrative password does not satisfy policy");
 }
 private void roles(Set<AdminRole> roles){if(roles==null||roles.isEmpty()||roles.stream().anyMatch(Objects::isNull))throw new IllegalArgumentException("Roles required");}
 @Override public AdminPrincipal loadUserByUsername(String name)throws UsernameNotFoundException {
  var rows=jdbc.queryForList("select id,username,password_hash,enabled,auth_revision from admin_user where username=?",name);
  if(rows.size()!=1)throw new UsernameNotFoundException("Invalid credentials");
  var r=rows.getFirst();Set<AdminRole> roles=roles(((Number)r.get("id")).longValue());
  return new AdminPrincipal((String)r.get("username"),(String)r.get("password_hash"),((Number)r.get("auth_revision")).longValue(),(Boolean)r.get("enabled"),roles);
 }
 private Set<AdminRole> roles(long id){
  var out=EnumSet.noneOf(AdminRole.class);
  jdbc.queryForList("select role from admin_user_role where user_id=?",String.class,id).forEach(r->out.add(AdminRole.valueOf(r)));return out;
 }
 public boolean validSession(String name,long revision){
  return jdbc.queryForObject("select count(*) from admin_user where username=? and enabled and auth_revision=?",Integer.class,name,revision)==1;
 }
 public List<User> users(int limit,int offset){
  if(limit<1||limit>100||offset<0||offset>1000000)throw new IllegalArgumentException("Invalid pagination");
  var page=jdbc.queryForList("select id,username,enabled,auth_revision from admin_user order by username limit ? offset ?",limit,offset);
  if(page.isEmpty())return List.of();
  var rolesById=new HashMap<Long,Set<AdminRole>>();
  var ids=page.stream().map(r->((Number)r.get("id")).longValue()).toList();
  String marks=String.join(",",Collections.nCopies(ids.size(),"?"));
  jdbc.query("select user_id,role from admin_user_role where user_id in ("+marks+")",(org.springframework.jdbc.core.RowCallbackHandler)r->
   rolesById.computeIfAbsent(r.getLong(1),id->EnumSet.noneOf(AdminRole.class)).add(AdminRole.valueOf(r.getString(2))),ids.toArray());
  return page.stream().map(r->new User((String)r.get("username"),(Boolean)r.get("enabled"),Set.copyOf(rolesById.getOrDefault(((Number)r.get("id")).longValue(),Set.of())),((Number)r.get("auth_revision")).longValue())).toList();
 }
 private void lock(){jdbc.execute("select pg_advisory_xact_lock(71772501)");}
 private long id(String name){
  var rows=jdbc.queryForList("select id from admin_user where username=?",Long.class,name);
  if(rows.size()!=1)throw new IllegalStateException("Administrative account unavailable");return rows.getFirst();
 }
 private void insert(String name,String raw,Set<AdminRole> roles){
  var time=Timestamp.from(Instant.now());
  long id=jdbc.queryForObject("insert into admin_user(username,password_hash,enabled,created_at,updated_at) values (?,?,true,?,?) returning id",
   Long.class,name,encoder.encode(raw),time,time);
  roles.forEach(r->jdbc.update("insert into admin_user_role(user_id,role) values (?,?)",id,r.name()));
 }
 @Transactional
 public boolean bootstrap(String name,String raw){
  lock();
  if(jdbc.queryForObject("select count(*) from admin_user",Integer.class)>0)return false;
  boolean noName=name==null||name.isEmpty(),noPassword=raw==null||raw.isEmpty();
  if(noName&&noPassword)return false;
  username(name);password(raw);insert(name,raw,Set.of(AdminRole.ADMIN));audit.record(name,"BOOTSTRAP_ADMIN","administration","SUCCESS");return true;
 }
 @Transactional
 public User create(String name,String raw,Set<AdminRole> roles,String actor){
  username(name);password(raw);roles(roles);lock();
  if(jdbc.queryForObject("select count(*) from admin_user where username=?",Integer.class,name)!=0)throw new IllegalStateException("Administrative account conflict");
  insert(name,raw,roles);audit.record(actor,"CREATE_USER",name,"SUCCESS");return new User(name,true,Set.copyOf(roles),0);
 }
 @Transactional
 public User change(String name,boolean enabled,Set<AdminRole> roles,String raw,String actor){
  username(name);roles(roles);if(raw!=null)password(raw);lock();long id=id(name);
  boolean wasAdmin=jdbc.queryForObject("select count(*) from admin_user u join admin_user_role r on r.user_id=u.id where u.id=? and u.enabled and r.role='ADMIN'",Integer.class,id)>0;
  int admins=jdbc.queryForObject("select count(*) from admin_user u join admin_user_role r on r.user_id=u.id where u.enabled and r.role='ADMIN'",Integer.class);
  if(wasAdmin&&(!enabled||!roles.contains(AdminRole.ADMIN))&&admins<=1)throw new IllegalStateException("Cannot disable last administrator");
  if(raw!=null)jdbc.update("update admin_user set password_hash=? where id=?",encoder.encode(raw),id);
  jdbc.update("update admin_user set enabled=?,auth_revision=auth_revision+1,updated_at=? where id=?",enabled,Timestamp.from(Instant.now()),id);
  jdbc.update("delete from admin_user_role where user_id=?",id);
  roles.forEach(r->jdbc.update("insert into admin_user_role(user_id,role) values (?,?)",id,r.name()));
  audit.record(actor,"UPDATE_USER",name,"SUCCESS");
  long revision=jdbc.queryForObject("select auth_revision from admin_user where id=?",Long.class,id);return new User(name,enabled,Set.copyOf(roles),revision);
 }
}
