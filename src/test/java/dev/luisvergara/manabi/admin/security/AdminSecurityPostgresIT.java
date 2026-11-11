package dev.luisvergara.manabi.admin.security;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import java.util.*;
import java.nio.charset.StandardCharsets;
import jakarta.servlet.ServletContext;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import tools.jackson.databind.json.JsonMapper;
import dev.luisvergara.manabi.content.bootstrap.*;
import dev.luisvergara.manabi.content.editorial.*;
import dev.luisvergara.manabi.content.publication.*;
import static dev.luisvergara.manabi.content.editorial.EditorialModels.*;

@EnabledIfEnvironmentVariable(named="MANABI_RUN_BOOTSTRAP_IT",matches="true")
@SpringBootTest(properties={"spring.profiles.active=dev","manabi.admin.bootstrap.username=","manabi.admin.bootstrap.password=","manabi.security.allowed-origins=http://localhost:5174"})
@ContextConfiguration(initializers=TemporaryEditorialDatabase.class)
@DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class AdminSecurityPostgresIT {
 @Autowired WebApplicationContext context;
 @Autowired AdminAccounts accounts;
 @Autowired PasswordEncoder encoder;
 @Autowired JdbcTemplate jdbc;
 @Autowired InitialCatalogLoader loader;
 @Autowired PublicationSnapshotService snapshots;
 @Autowired EditorialReviewService reviews;
 MockMvc mvc;final JsonMapper json=JsonMapper.builder().build();
 final String admin="isolated-admin",password="Synthetic-Only!"+UUID.randomUUID();
 record Client(MockHttpSession session,String csrf,String peer){}
 @BeforeEach void prepare(){mvc=MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();}
 void bootstrap(){assertTrue(accounts.bootstrap(admin,password));}
 org.springframework.test.web.servlet.request.RequestPostProcessor peer(String value){return r->{r.setRemoteAddr(value);return r;};}
 @SuppressWarnings("unchecked") Map<String,Object> map(MvcResult r){return json.readValue(r.getResponse().getContentAsByteArray(),Map.class);}
 MvcResult expect(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request,int status)throws Exception {
  var result=mvc.perform(request).andReturn();assertEquals(status,result.getResponse().getStatus(),request.buildRequest(context.getServletContext()).getRequestURI());return result;
 }
 Client csrf(MockHttpSession session,String remote)throws Exception {
  var request=get("/api/admin/csrf").with(peer(remote));if(session!=null)request.session(session);
  var result=expect(request,200);return new Client((MockHttpSession)result.getRequest().getSession(),(String)map(result).get("token"),remote);
 }
 Client login(String user,String raw,String remote)throws Exception {
  var guest=csrf(null,remote);String old=guest.session().getId();
  var result=expect(post("/api/admin/login").with(peer(remote)).session(guest.session()).contentType(MediaType.APPLICATION_FORM_URLENCODED)
   .param("username",user).param("password",raw).header("X-CSRF-TOKEN",guest.csrf()),200);
  var session=(MockHttpSession)result.getRequest().getSession();assertNotEquals(old,session.getId());
  return csrf(session,remote);
 }
 MvcResult sendPost(Client c,String route,Object body,int status)throws Exception {
  return expect(post(route).with(peer(c.peer())).session(c.session()).header("X-CSRF-TOKEN",c.csrf()).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(body)),status);
 }
 void create(Client c,String name,String raw,List<String> roles)throws Exception {sendPost(c,"/api/admin/users",Map.of("username",name,"password",raw,"roles",roles),200);}
 void load(){var s=new InitialCatalogReader().readResources();loader.apply(s,loader.dryRun(s).sha256());}
 @Test void vocabularyCategoriesEndpointUsesRealRepositoryAndOrdersByDisplayOrderThenCode()throws Exception{
  bootstrap();var a=login(admin,password,"127.0.0.130");
  var initial=expect(get("/api/vocabulary/categories").session(a.session()),200);
  var initialRows=json.readValue(initial.getResponse().getContentAsByteArray(),List.class);
  assertEquals(20,initialRows.size());
  // TEMP rows only: force an order tie so the code tiebreaker is exercised.
  jdbc.update("update vocabulary_category set display_order=0 where code in ('CLOTHES','ANIMAL')");
  var result=expect(get("/api/vocabulary/categories").session(a.session()),200);
  var rows=json.readValue(result.getResponse().getContentAsByteArray(),List.class);
  assertEquals(20,rows.size());
  var codes=new ArrayList<String>();var orders=new ArrayList<Long>();
  for(Object row:rows){var item=(Map<?,?>)row;codes.add((String)item.get("code"));orders.add(((Number)item.get("displayOrder")).longValue());}
  assertEquals(20,new HashSet<>(codes).size());
  assertEquals(List.of("ANIMAL","CLOTHES"),codes.subList(0,2));
  for(int i=1;i<rows.size();i++){
   assertTrue(orders.get(i-1)<=orders.get(i));
   if(orders.get(i-1).equals(orders.get(i)))assertTrue(codes.get(i-1).compareTo(codes.get(i))<0);
  }
 }
 @Test void exportHistoryRecoversIdsAcrossSessionsAndPreservesOriginalZip()throws Exception{
  load();bootstrap();
  for(var source:snapshots.capture(PublicationSnapshotService.Profile.CORE).sources().stream().sorted(Comparator.comparing(s->s.kind().equals("VOCABULARY_CATEGORY")?0:1)).toList())
   reviews.approve(new Key(PublicationSnapshotService.NAMESPACE,Kind.valueOf(source.kind()),source.publicId()),source.fingerprint(),"isolated-history");
  var a=login(admin,password,"127.0.0.101");
  String publisherRaw="Synthetic-Publisher!"+UUID.randomUUID();create(a,"isolated-history-publisher",publisherRaw,List.of("PUBLISHER"));
  var c=login("isolated-history-publisher",publisherRaw,"127.0.0.102");
  var generated=map(sendPost(c,"/api/admin/publications/generate",Map.of("profile","CORE"),200));
  assertEquals("READY",generated.get("status"));String release=(String)generated.get("releaseId");
  sendPost(c,"/api/admin/publications/materialize",Map.of("jobId",generated.get("jobId")),200);
  var exported=map(sendPost(c,"/api/admin/publications/export",Map.of("releaseId",release),200));
  byte[] original=expect(get("/api/admin/publications/exports/"+exported.get("exportId")+"/download").session(c.session()),200).getResponse().getContentAsByteArray();
  sendPost(c,"/api/admin/publications/export",Map.of("releaseId",release),200);
  var fresh=login("isolated-history-publisher",publisherRaw,"127.0.0.103");
  var page=map(expect(get("/api/admin/publications/exports").session(fresh.session()).param("releaseId",release).param("limit","1"),200));
  assertEquals(2,((Number)page.get("total")).intValue());
  var first=(Map<?,?>)((List<?>)page.get("items")).getFirst();assertNotNull(first.get("exportId"));assertNotNull(first.get("exportedAt"));assertEquals(false,first.get("revoked"));assertFalse(first.containsKey("storageKey"));
  var second=map(expect(get("/api/admin/publications/exports").session(fresh.session()).param("releaseId",release).param("limit","1").param("offset","1"),200));
  assertEquals(1,((List<?>)second.get("items")).size());
  sendPost(fresh,"/api/admin/publications/rebuild",Map.of("exportId",exported.get("exportId")),200);
  assertArrayEquals(original,expect(get("/api/admin/publications/exports/"+exported.get("exportId")+"/download").session(fresh.session()),200).getResponse().getContentAsByteArray());
  expect(get("/api/admin/publications/exports").session(fresh.session()).param("limit","0"),400);
  sendPost(a,"/api/admin/publications/revoke",Map.of("releaseId",release,"reason","isolated history revocation"),200);
  var revoked=map(expect(get("/api/admin/publications/exports").session(fresh.session()).param("releaseId",release),200));
  assertTrue(((List<?>)revoked.get("items")).stream().allMatch(row->Boolean.TRUE.equals(((Map<?,?>)row).get("revoked"))));
  expect(get("/api/admin/publications/exports/"+exported.get("exportId")+"/download").session(fresh.session()),409);
 }
 @Test void categoryMetadataIsVersionedAuditedAndCannotChangeCode()throws Exception{
  load();bootstrap();var a=login(admin,password,"127.0.0.110");
  String raw="Synthetic-Categories!"+UUID.randomUUID();create(a,"isolated-category-editor",raw,List.of("EDITOR"));
  var c=login("isolated-category-editor",raw,"127.0.0.111");
  var identity=map(expect(get("/api/admin/editorial/identity").session(c.session()).param("kind","VOCABULARY_CATEGORY").param("publicId","DRINKS"),200));
  long id=((Number)identity.get("id")).longValue();Object version=identity.get("version");
  var body=new LinkedHashMap<String,Object>();body.put("name","Bebidas (prueba aislada)");body.put("symbol","茶");body.put("displayOrder",8);body.put("showInCatalog",true);body.put("version",version);
  var updated=map(sendPostCategory(c,id,body,200));assertEquals("DRINKS",updated.get("code"));assertEquals(20,jdbc.queryForObject("select count(*) from vocabulary_category",Integer.class));
  assertEquals(55,jdbc.queryForObject("select count(*) from vocabulary_category_membership",Integer.class));
  assertTrue(((Number)updated.get("version")).longValue()>((Number)version).longValue());
  sendPostCategory(c,id,body,409);
  body.put("code","REPLACEMENT");sendPostCategory(c,id,body,400);
  assertEquals(1,jdbc.queryForObject("select count(*) from admin_audit where actor='isolated-category-editor' and target='vocabulary_category:DRINKS' and outcome='SUCCESS'",Integer.class));
  assertEquals("CONTENT_NOT_FOUND",map(expect(get("/api/admin/editorial/identity").session(c.session()).param("kind","VOCABULARY").param("publicId","missing"),404)).get("code"));
  String reviewerRaw="Synthetic-Reviewer!"+UUID.randomUUID();create(a,"isolated-category-reviewer",reviewerRaw,List.of("REVIEWER"));
  var reviewer=login("isolated-category-reviewer",reviewerRaw,"127.0.0.112");
  body.remove("code");body.put("version",updated.get("version"));sendPostCategory(reviewer,id,body,403);
  expect(get("/api/admin/publications/exports").session(c.session()),403);
 }
 MvcResult sendPostCategory(Client c,long id,Object body,int status)throws Exception{
  return expect(put("/api/vocabulary/categories/"+id).with(peer(c.peer())).session(c.session()).header("X-CSRF-TOKEN",c.csrf()).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(body)),status);
 }
 @Test void catalogPagesUseBoundedEntityLoadsAndContainEditableIdentity()throws Exception{
  load();bootstrap();var a=login(admin,password,"127.0.0.120");
  var statistics=context.getBean(jakarta.persistence.EntityManagerFactory.class).unwrap(org.hibernate.SessionFactory.class).getStatistics();
  statistics.setStatisticsEnabled(true);statistics.clear();
  var page=map(expect(get("/api/admin/editorial/catalog").session(a.session()).param("kind","VOCABULARY").param("limit","3"),200));
  assertTrue(statistics.getEntityLoadCount()<40,"Three words must not load the entire educational catalog");
  assertTrue(statistics.getQueryExecutionCount()<10,"Page associations/reviews must be loaded in batches");
  statistics.setStatisticsEnabled(false);
  assertEquals(40,((Number)page.get("total")).intValue());assertEquals(3,((List<?>)page.get("items")).size());
  for(Object row:(List<?>)page.get("items")){var item=(Map<?,?>)row;assertNotNull(item.get("id"));assertNotNull(item.get("version"));assertEquals("PENDING",item.get("state"));}
  var empty=map(expect(get("/api/admin/editorial/catalog").session(a.session()).param("kind","VOCABULARY").param("limit","3").param("offset","40"),200));
  assertEquals(40,((Number)empty.get("total")).intValue());assertEquals(List.of(),empty.get("items"));
  var search=map(expect(get("/api/admin/editorial/catalog").session(a.session()).param("kind","VOCABULARY").param("q","Agua"),200));
  assertEquals(1,((Number)search.get("total")).intValue());
 }
 @Test void loginSessionFixationCsrfAndLogoutAreRealFilters()throws Exception {
  bootstrap();var guest=csrf(null,"127.0.0.2");
  expect(post("/api/admin/login").session(guest.session()).param("username",admin).param("password",password),403);
  var authenticated=login(admin,password,"127.0.0.3");
  var principal=(SecurityContext)authenticated.session().getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
  assertNull(principal.getAuthentication().getCredentials());assertNull(((AdminPrincipal)principal.getAuthentication().getPrincipal()).getPassword());
  var me=expect(get("/api/admin/session").session(authenticated.session()),200);assertEquals(admin,map(me).get("username"));assertFalse(me.getResponse().getContentAsString().contains(password));
  expect(post("/api/admin/users").session(authenticated.session()).contentType(MediaType.APPLICATION_JSON).content("{}"),403);
  expect(post("/api/admin/logout").session(authenticated.session()),403);
  expect(post("/api/admin/logout").session(authenticated.session()).header("X-CSRF-TOKEN",authenticated.csrf()),200);
  assertTrue(authenticated.session().isInvalid());expect(get("/api/admin/session"),401);
  String basic=Base64.getEncoder().encodeToString((admin+":"+password).getBytes(StandardCharsets.UTF_8));
  expect(get("/api/admin/session").header("Authorization","Basic "+basic),401);
 }
 @Test void allOldCrudRoutesAndUnknownRoutesAreClosedToAnonymous()throws Exception {
  for(String route:List.of("/api/kana","/api/kanji","/api/vocabulary","/api/phrases","/api/phrase-categories","/api/particles","/actuator/health","/api-docs","/api/admin/users","/api/admin/publications/history"))expect(get(route),401);
  bootstrap();var c=login(admin,password,"127.0.0.4");expect(get("/unregistered-route").session(c.session()),403);
  for(String route:List.of("/api/kana","/api/kanji","/api/vocabulary","/api/phrases","/api/phrase-categories","/api/particles"))expect(get(route).session(c.session()),200);
  expect(get("/api/admin/editorial/catalog").session(c.session()).param("limit","101"),400);
 }
 @Test void roleMatrixAndMultipleRolesPreserveServiceBoundaries()throws Exception {
  bootstrap();var a=login(admin,password,"127.0.0.5");
  int n=10;
  for(var roles:List.of(List.of("EDITOR"),List.of("REVIEWER"),List.of("PUBLISHER"),List.of("EDITOR","REVIEWER"))){
   String name="isolated-role-"+n,raw="Synthetic-Role!"+UUID.randomUUID();create(a,name,raw,roles);
   var c=login(name,raw,"127.0.0."+n++);expect(get("/api/kana").session(c.session()),200);expect(get("/api/admin/users").session(c.session()),403);
   expect(get("/api/admin/editorial/catalog").session(c.session()),200);
   boolean editor=roles.contains("EDITOR"),reviewer=roles.contains("REVIEWER"),publisher=roles.contains("PUBLISHER");
   sendPost(c,"/api/kana",Map.of(),editor?400:403);
   expect(get("/api/admin/editorial/review").session(c.session()).param("kind","PHRASE_CATEGORY").param("publicId","comunicacion-basica"),reviewer?200:403);
   sendPost(c,"/api/admin/publications/generate",Map.of("profile","CORE"),publisher?200:403);
   sendPost(c,"/api/admin/publications/revoke",Map.of("releaseId","sha256:"+"0".repeat(64),"reason","test"),403);
  }
 }
 @Test void reviewerUsesAuthenticatedIdentityAndDependenciesCannotBeBypassed()throws Exception {
  load();bootstrap();var a=login(admin,password,"127.0.0.20");String raw="Synthetic-Review!"+UUID.randomUUID();create(a,"isolated-reviewer",raw,List.of("REVIEWER"));
  var r=login("isolated-reviewer",raw,"127.0.0.21");
  var water=map(expect(get("/api/admin/editorial/review").session(r.session()).param("kind","VOCABULARY").param("publicId","water"),200));
  sendPost(r,"/api/admin/editorial/approve",Map.of("kind","VOCABULARY","publicId","water","fingerprint",water.get("fingerprint")),409);
  for(String code:List.of("DRINKS","NATURE")){
   var v=map(expect(get("/api/admin/editorial/review").session(r.session()).param("kind","VOCABULARY_CATEGORY").param("publicId",code),200));
   sendPost(r,"/api/admin/editorial/approve",Map.of("kind","VOCABULARY_CATEGORY","publicId",code,"fingerprint",v.get("fingerprint")),200);
  }
  var approved=sendPost(r,"/api/admin/editorial/approve",Map.of("kind","VOCABULARY","publicId","water","fingerprint",water.get("fingerprint")),200);
  assertEquals("APPROVED",map(approved).get("state"));assertEquals("isolated-reviewer",jdbc.queryForObject("select approved_by from editorial_review where entity_kind='VOCABULARY' and public_id='water'",String.class));
  sendPost(r,"/api/admin/editorial/pending",Map.of("kind","VOCABULARY","publicId","water","fingerprint",water.get("fingerprint")),200);
  sendPost(r,"/api/admin/editorial/approve",Map.of("kind","VOCABULARY","publicId","water","fingerprint","0".repeat(64)),409);
  sendPost(r,"/api/admin/editorial/approve",Map.of("kind","VOCABULARY","publicId","water","fingerprint",water.get("fingerprint"),"actor","forged"),400);
 }
 @Test void bootstrapIsIdempotentAndRejectsUnsafeInitialValues(){
  assertFalse(accounts.bootstrap("",""));assertThrows(IllegalArgumentException.class,()->accounts.bootstrap(admin,"short"));
  assertEquals(0,jdbc.queryForObject("select count(*) from admin_user",Integer.class));
  bootstrap();String hash=jdbc.queryForObject("select password_hash from admin_user where username=?",String.class,admin);
  assertTrue(hash.startsWith("$2"));assertNotEquals(password,hash);assertTrue(encoder.matches(password,hash));
  assertFalse(accounts.bootstrap(admin,"Other-Synthetic!"+UUID.randomUUID()));assertFalse(accounts.bootstrap("other-admin","Other-Synthetic!"+UUID.randomUUID()));
  assertEquals(hash,jdbc.queryForObject("select password_hash from admin_user where username=?",String.class,admin));assertEquals(1,jdbc.queryForObject("select count(*) from admin_user",Integer.class));
 }
 @Test void rolePasswordChangesInvalidateSessionsAndLastAdminIsProtected()throws Exception {
  bootstrap();var a=login(admin,password,"127.0.0.30");String raw="Synthetic-Editor!"+UUID.randomUUID();create(a,"isolated-editor",raw,List.of("EDITOR"));
  var editor=login("isolated-editor",raw,"127.0.0.31");
  expect(put("/api/admin/users/isolated-editor").session(a.session()).header("X-CSRF-TOKEN",a.csrf()).contentType(MediaType.APPLICATION_JSON)
   .content(json.writeValueAsBytes(Map.of("enabled",true,"roles",List.of("REVIEWER"),"password","Synthetic-Reset!"+UUID.randomUUID()))),200);
  expect(get("/api/admin/session").session(editor.session()),401);
  expect(put("/api/admin/users/"+admin).session(a.session()).header("X-CSRF-TOKEN",a.csrf()).contentType(MediaType.APPLICATION_JSON)
   .content(json.writeValueAsBytes(Map.of("enabled",false,"roles",List.of("EDITOR")))),409);
  var users=expect(get("/api/admin/users").session(a.session()),200);String body=users.getResponse().getContentAsString();assertFalse(body.contains("password"));assertFalse(body.contains("$2"));assertFalse(body.contains("auth_revision"));
 }
 @Test void corsAndLoginThrottlingAreRestrictive()throws Exception {
  bootstrap();
  expect(options("/api/admin/session").header("Origin","https://untrusted.example").header("Access-Control-Request-Method","GET"),403);
  var ok=expect(options("/api/admin/session").header("Origin","http://localhost:5174").header("Access-Control-Request-Method","GET"),200);
  assertEquals("http://localhost:5174",ok.getResponse().getHeader("Access-Control-Allow-Origin"));assertEquals("true",ok.getResponse().getHeader("Access-Control-Allow-Credentials"));
  var c=csrf(null,"127.0.0.40");
  for(int i=0;i<10;i++)expect(post("/api/admin/login").with(peer(c.peer())).session(c.session()).header("X-CSRF-TOKEN",c.csrf()).param("username",admin).param("password","Synthetic-Wrong!"+UUID.randomUUID()),401);
  expect(post("/api/admin/login").with(peer(c.peer())).session(c.session()).header("X-CSRF-TOKEN",c.csrf()).param("username",admin).param("password",password),429);
 }
 @Test void publicationZipDownloadAndRevocationRequireTheirRoles()throws Exception {
  load();bootstrap();var a=login(admin,password,"127.0.0.50");String raw="Synthetic-Publisher!"+UUID.randomUUID();create(a,"isolated-publisher",raw,List.of("PUBLISHER"));
  var p=login("isolated-publisher",raw,"127.0.0.51");
  var blocked=sendPost(p,"/api/admin/publications/generate",Map.of("profile","CORE"),200);assertEquals("BLOCKED",map(blocked).get("status"));
  sendPost(p,"/api/admin/publications/materialize",Map.of("jobId",map(blocked).get("jobId")),409);
  // Explicit isolated fixture review, never real public data or a runtime bypass.
  var sources=snapshots.capture(PublicationSnapshotService.Profile.CORE).sources().stream().sorted(Comparator.comparing(s->s.kind().equals("VOCABULARY_CATEGORY")?0:1)).toList();
  for(var s:sources)reviews.approve(new Key(PublicationSnapshotService.NAMESPACE,Kind.valueOf(s.kind()),s.publicId()),s.fingerprint(),"isolated-fixture");
  var generated=map(sendPost(p,"/api/admin/publications/generate",Map.of("profile","CORE"),200));assertEquals("READY",generated.get("status"));
  expect(get("/api/admin/publications/jobs").session(p.session()),200);
  expect(get("/api/admin/publications/jobs/"+generated.get("jobId")).session(p.session()),200);
  sendPost(p,"/api/admin/publications/materialize",Map.of("jobId",generated.get("jobId")),200);
  expect(get("/api/admin/publications/history").session(p.session()),200);
  expect(get("/api/admin/audit").session(a.session()),200);
  var exported=map(sendPost(p,"/api/admin/publications/export",Map.of("releaseId",generated.get("releaseId")),200));
  String id=(String)exported.get("exportId");assertFalse(exported.containsKey("key"));assertFalse(exported.containsKey("storage_key"));
  expect(get("/api/admin/publications/exports/"+id+"/download"),401);
  var zip=expect(get("/api/admin/publications/exports/"+id+"/download").session(p.session()),200);assertEquals("application/zip",zip.getResponse().getContentType());assertTrue(zip.getResponse().getHeader("Content-Disposition").startsWith("attachment;"));
  sendPost(p,"/api/admin/publications/rebuild",Map.of("exportId",id),200);
  sendPost(p,"/api/admin/publications/revoke",Map.of("releaseId",generated.get("releaseId"),"reason","isolated"),403);
  sendPost(a,"/api/admin/publications/revoke",Map.of("releaseId",generated.get("releaseId"),"reason",""),400);
  sendPost(a,"/api/admin/publications/revoke",Map.of("releaseId",generated.get("releaseId"),"reason","Explicit isolated revocation"),200);
  expect(get("/api/admin/publications/exports/"+id+"/download").session(p.session()),409);
  assertTrue(jdbc.queryForObject("select count(*) from admin_audit where actor='isolated-publisher' and action='EXPORT_PUBLICATION'",Integer.class)>0);
  assertEquals(1,jdbc.queryForObject("select count(*) from admin_audit where actor=? and action='REVOKE_DISTRIBUTION'",Integer.class,admin));
 }

 @Test void validCrudWritesAreAuthorizedAndAuditedInsideServiceTransaction()throws Exception {
  bootstrap();var a=login(admin,password,"127.0.0.60");String raw="Synthetic-Crud!"+UUID.randomUUID();create(a,"isolated-crud-editor",raw,List.of("EDITOR"));
  var c=login("isolated-crud-editor",raw,"127.0.0.61");
  var body=new LinkedHashMap<String,Object>();body.put("character","__FIXTURE_SECURITY__");body.put("romaji","__TEST__");body.put("type","HIRAGANA");body.put("group","NORMAL");body.put("displayOrder",0);
  var created=map(sendPost(c,"/api/kana",body,200));String id=created.get("id").toString();
  body.put("romaji","__EDIT__");body.put("version",created.get("version"));
  expect(put("/api/kana/"+id).session(c.session()).header("X-CSRF-TOKEN",c.csrf()).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(body)),200);
  expect(delete("/api/kana/"+id).session(c.session()).header("X-CSRF-TOKEN",c.csrf()),200);
  assertEquals(0,jdbc.queryForObject("select count(*) from kana",Integer.class));
  assertEquals(3,jdbc.queryForObject("select count(*) from admin_audit where actor='isolated-crud-editor' and action like 'CRUD_%' and outcome='SUCCESS'",Integer.class));
  var targets=jdbc.queryForList("select target from admin_audit where actor='isolated-crud-editor' and action like 'CRUD_%'",String.class);
  assertEquals(1,new HashSet<>(targets).size());assertTrue(targets.getFirst().startsWith("kana:"));
  assertThrows(org.springframework.security.access.AccessDeniedException.class,()->context.getBean(dev.luisvergara.manabi.service.KanaService.class).save(new dev.luisvergara.manabi.entity.kana.Kana()));
 }
}
