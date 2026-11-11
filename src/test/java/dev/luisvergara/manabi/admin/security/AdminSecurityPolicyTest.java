package dev.luisvergara.manabi.admin.security;
import static org.junit.jupiter.api.Assertions.*;
import java.time.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
class AdminSecurityPolicyTest {
 @Test void passwordsRejectShortDefaultsRepeatedCharactersAndBcryptTruncation(){
  for(String value:new String[]{"short","aaaaaaaaaaaaaaaaaaaa","adminadminadminadmin","ChangeMe-Example-2026!","x".repeat(73),"語".repeat(25)})assertThrows(IllegalArgumentException.class,()->AdminAccounts.password(value));
  assertDoesNotThrow(()->AdminAccounts.password("Synthetic-Test-Only!"+java.util.UUID.randomUUID()));
 }
 @Test void usernamesAreExplicitBoundedIdentifiers(){
  for(String value:new String[]{"a","../admin"," admin","a b","user@example.com","x".repeat(65)})assertThrows(IllegalArgumentException.class,()->AdminAccounts.username(value));
  assertDoesNotThrow(()->AdminAccounts.username("test.admin-01"));
 }
 @Test void throttleBoundsAttemptsAcrossIpsAndAccounts(){
  var t=new LoginThrottle(Clock.fixed(Instant.EPOCH,ZoneOffset.UTC),2,1000);
  assertTrue(t.allow("peer-a","user-a"));assertTrue(t.allow("peer-b","user-a"));assertFalse(t.allow("peer-c","user-a"));
  assertTrue(t.allow("peer-a","user-b"));assertFalse(t.allow("peer-a","user-c"));
 }
 @Test void throttleWindowExpiresWithoutPermanentAccountLock(){
  var clock=new Clock(){long ticks;public ZoneId getZone(){return ZoneOffset.UTC;}public Clock withZone(ZoneId z){return this;}public Instant instant(){return Instant.ofEpochMilli(ticks);}public long millis(){return ticks+=1000;}};
  var t=new LoginThrottle(clock,1,500);
  assertTrue(t.allow("peer","user"));assertTrue(t.allow("peer","user"));
 }
 @Test void corsRejectsWildcardsCredentialsInUrlsAndUnsecuredRemoteOrigins(){
  var config=new AdminSecurityConfiguration();var env=new MockEnvironment();
  for(String origin:new String[]{"*","https://*.example.test","https://user:pass@example.test","https://example.test/path","http://example.test","https://example.test?x=1"})assertThrows(IllegalArgumentException.class,()->config.administrativeCors(origin,env));
  assertDoesNotThrow(()->config.administrativeCors("https://studio.example.test",env));
 }
 @Test void insecureCookieConfigurationCannotBecomeProductionDefault(){
  var c=new AdminSecurityConfiguration();var prod=new MockEnvironment().withProperty("server.servlet.session.cookie.secure","false");
  assertThrows(IllegalArgumentException.class,()->c.administrativeCors("",prod));
  var dev=new MockEnvironment().withProperty("server.servlet.session.cookie.secure","false");dev.setActiveProfiles("dev");
  assertDoesNotThrow(()->c.administrativeCors("http://localhost:5174",dev));
  dev.setProperty("server.servlet.session.cookie.same-site","none");assertThrows(IllegalArgumentException.class,()->c.administrativeCors("",dev));
 }
}
