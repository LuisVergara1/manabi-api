package dev.luisvergara.manabi.admin.security;
import org.springframework.boot.*;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;
/** Explicit external bootstrap, once only. Empty configuration leaves authentication locked, not defaulted. */
@Component
public class AdminBootstrap implements ApplicationRunner {
 private final AdminAccounts accounts;private final String name,password;
 public AdminBootstrap(AdminAccounts accounts,@Value("${manabi.admin.bootstrap.username:}")String name,@Value("${manabi.admin.bootstrap.password:}")String password){this.accounts=accounts;this.name=name;this.password=password;}
 @Override public void run(ApplicationArguments args){accounts.bootstrap(name,password);}
}
