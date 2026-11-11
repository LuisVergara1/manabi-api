package dev.luisvergara.manabi.admin.security;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.crypto.password.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
@Configuration @EnableMethodSecurity
public class AdminCoreConfiguration {
 @Bean PasswordEncoder administrativePasswordEncoder(){return new BCryptPasswordEncoder(12);}
}
