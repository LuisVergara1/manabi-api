package dev.luisvergara.manabi.admin.security;
import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
class AdminConfigurationAssetsTest {
 @TempDir Path temp;
 @Test void environmentExampleHasNoInitialSecretsAndPrivateFilesAreIgnored()throws Exception{
  var lines=Files.readAllLines(Path.of(".env.example"));
  for(String key:List.of("DB_PASSWORD","MANABI_ADMIN_PASSWORD","DB_USERNAME","MANABI_ADMIN_USERNAME"))assertTrue(lines.contains(key+"="));
  String ignore=Files.readString(Path.of(".gitignore"));assertTrue(ignore.contains(".env.*"));assertTrue(ignore.contains("!.env.example"));
  assertTrue(Files.readString(Path.of(".dockerignore")).contains(".env"));
 }
 @Test void explicitEnvironmentLoaderDoesNotExecuteOrPrintValues()throws Exception{
  Path marker=temp.resolve("must-not-exist");Path env=temp.resolve("isolated.env");
  String literal="$(Set-Content -LiteralPath '"+marker+"' -Value bad)";
  Files.writeString(env,"DB_URL=jdbc:postgresql://invalid.test/isolated\nDB_USERNAME=synthetic\nDB_PASSWORD="+literal+"\nSPRING_PROFILES_ACTIVE=dev\n",StandardCharsets.UTF_8);
  var builder=new ProcessBuilder("powershell.exe","-NoProfile","-ExecutionPolicy","Bypass","-File",Path.of("scripts/start-dev.ps1").toAbsolutePath().toString(),"-EnvFile",env.toString(),"-Mode","validate").redirectErrorStream(true);
  for(String key:List.of("DB_URL","DB_USERNAME","DB_PASSWORD","MANABI_ADMIN_USERNAME","MANABI_ADMIN_PASSWORD"))builder.environment().remove(key);
  var process=builder.start();String output=new String(process.getInputStream().readAllBytes(),StandardCharsets.UTF_8);
  assertEquals(0,process.waitFor());assertFalse(Files.exists(marker));assertFalse(output.contains(literal));assertTrue(output.contains("Values are not printed"));
 }
 @Test void composeDoesNotManagePostgresqlAndExposesOnlyLoopback()throws Exception{
  var yaml=new org.yaml.snakeyaml.Yaml(new org.yaml.snakeyaml.constructor.SafeConstructor(new org.yaml.snakeyaml.LoaderOptions()));
  @SuppressWarnings("unchecked")var definition=(Map<String,Object>)yaml.load(Files.readString(Path.of("compose.yaml")));
  var services=(Map<?,?>)definition.get("services");assertEquals(Set.of("manabi-api"),services.keySet());
  var service=(Map<?,?>)services.get("manabi-api");assertEquals(List.of("127.0.0.1:8080:8080"),service.get("ports"));assertEquals(true,service.get("read_only"));
  var environment=(Map<?,?>)service.get("environment");assertTrue(((String)environment.get("DB_PASSWORD")).startsWith("${"));
  assertEquals("/var/lib/manabi/publications",environment.get("MANABI_PUBLICATION_ROOT"));
 }
}
