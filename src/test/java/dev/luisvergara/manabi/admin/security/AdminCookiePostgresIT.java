package dev.luisvergara.manabi.admin.security;
import static org.junit.jupiter.api.Assertions.*;
import java.net.*;
import java.net.http.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.annotation.DirtiesContext;
import dev.luisvergara.manabi.content.bootstrap.TemporaryEditorialDatabase;
@EnabledIfEnvironmentVariable(named="MANABI_RUN_BOOTSTRAP_IT",matches="true")
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={"server.address=127.0.0.1","server.servlet.session.cookie.secure=true","manabi.admin.bootstrap.username=","manabi.admin.bootstrap.password="})
@ContextConfiguration(initializers=TemporaryEditorialDatabase.class)
@DirtiesContext
class AdminCookiePostgresIT {
 @LocalServerPort int port;
 @Test void realServletCookieIsHttpOnlySecureAndSameSiteWithoutPublicActuator()throws Exception {
  var client=HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();
  var csrf=client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api/admin/csrf")).GET().build(),HttpResponse.BodyHandlers.discarding());
  assertEquals(200,csrf.statusCode());String cookie=String.join(";",csrf.headers().allValues("set-cookie")).toLowerCase(java.util.Locale.ROOT);
  assertTrue(cookie.contains("httponly"));assertTrue(cookie.contains("secure"));assertTrue(cookie.contains("samesite=lax"));
  var health=client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/actuator/health")).GET().build(),HttpResponse.BodyHandlers.discarding());
  assertEquals(401,health.statusCode());assertEquals("nosniff",health.headers().firstValue("x-content-type-options").orElseThrow());
 }
}
