package dev.luisvergara.manabi.admin.security;
import static org.junit.jupiter.api.Assertions.*;
import java.net.*;import java.net.http.*;import java.nio.charset.StandardCharsets;import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.annotation.DirtiesContext;
import tools.jackson.databind.json.JsonMapper;
import dev.luisvergara.manabi.content.bootstrap.TemporaryEditorialDatabase;

@EnabledIfEnvironmentVariable(named="MANABI_RUN_BOOTSTRAP_IT",matches="true")
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={
 "spring.profiles.active=dev","server.address=127.0.0.1","server.servlet.session.cookie.secure=false",
 "manabi.admin.bootstrap.username=","manabi.admin.bootstrap.password="})
@ContextConfiguration(initializers=TemporaryEditorialDatabase.class)
@DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class AdminLoginRoutingPostgresIT {
 @LocalServerPort int port;@Autowired AdminAccounts accounts;
 final JsonMapper json=JsonMapper.builder().build();
 final String raw="Synthetic-Routing!"+UUID.randomUUID();
 HttpClient client(){return HttpClient.newBuilder().cookieHandler(new CookieManager(null,CookiePolicy.ACCEPT_ALL)).build();}
 String base(){return "http://127.0.0.1:"+port;}
 @SuppressWarnings("unchecked") Map<String,String> csrf(HttpClient c)throws Exception{
  var r=c.send(HttpRequest.newBuilder(URI.create(base()+"/api/admin/csrf")).GET().build(),HttpResponse.BodyHandlers.ofString());
  assertEquals(200,r.statusCode());return json.readValue(r.body(),Map.class);
 }
 int login(HttpClient c,String route,Map<String,String> csrf,String password)throws Exception{
  var b=HttpRequest.newBuilder(URI.create(base()+route)).header("Content-Type","application/x-www-form-urlencoded");
  if(csrf!=null)b.header(csrf.get("headerName"),csrf.get("token"));
  return c.send(b.POST(HttpRequest.BodyPublishers.ofString("username=isolated-routing&password="+URLEncoder.encode(password,StandardCharsets.UTF_8))).build(),HttpResponse.BodyHandlers.discarding()).statusCode();
 }
 @Test void equivalentEncodedRoutesShareThrottleIncludingValidPassword()throws Exception{
  assertTrue(accounts.bootstrap("isolated-routing",raw));var c=client();var token=csrf(c);
  assertEquals(403,login(c,"/api/admin/%6cogin",null,raw));
  String[] routes={"/api/admin/login","/api/admin/%6cogin","/api/admin/lo%67in","/api/admin/%6c%6f%67%69%6e"};
  for(int i=0;i<10;i++)assertEquals(401,login(c,routes[i%routes.length],token,"Synthetic-Wrong-Only!"));
  for(String route:routes)assertEquals(429,login(c,route,token,raw),route);
 }
 @Test void encodedLoginStillAuthenticatesAndSessionIsUsable()throws Exception{
  assertTrue(accounts.bootstrap("isolated-routing",raw));var c=client();var token=csrf(c);
  assertEquals(200,login(c,"/api/admin/%6cogin",token,raw));
  var session=c.send(HttpRequest.newBuilder(URI.create(base()+"/api/admin/session")).GET().build(),HttpResponse.BodyHandlers.ofString());
  assertEquals(200,session.statusCode());assertTrue(session.body().contains("isolated-routing"));assertFalse(session.body().contains(raw));
 }
}
