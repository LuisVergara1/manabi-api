package dev.luisvergara.manabi.admin.web;
import java.util.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.CsrfToken;
import dev.luisvergara.manabi.admin.security.AdminPrincipal;
@RestController @RequestMapping("/api/admin")
public class AdminSessionController {
 @GetMapping("/csrf") public Map<String,String> csrf(CsrfToken csrf){return Map.of("headerName",csrf.getHeaderName(),"token",csrf.getToken());}
 @GetMapping("/session") public Map<String,Object> session(Authentication auth){
  var p=(AdminPrincipal)auth.getPrincipal();return Map.of("username",p.getUsername(),"roles",p.roles());
 }
}
