package dev.luisvergara.manabi.admin.web;
import java.util.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;
import lombok.RequiredArgsConstructor;
import dev.luisvergara.manabi.admin.AdminUserService;
import dev.luisvergara.manabi.admin.security.AdminRole;
@RestController @RequestMapping("/api/admin") @RequiredArgsConstructor
public class AdminUsersController {
 public record Create(@NotBlank @Size(max=64) String username,@NotBlank @Size(min=16,max=72) String password,@NotEmpty Set<@NotNull AdminRole> roles){}
 public record Update(@NotNull Boolean enabled,@NotEmpty Set<@NotNull AdminRole> roles,@Size(min=16,max=72) String password){}
 private final AdminUserService users;
 @GetMapping("/users") public List<AdminUserService.View> users(@RequestParam(defaultValue="20")int limit,@RequestParam(defaultValue="0")int offset){return users.users(limit,offset);}
 @PostMapping("/users") public AdminUserService.View create(@Valid @RequestBody Create r){return users.create(r.username(),r.password(),r.roles());}
 @PutMapping("/users/{username}") public AdminUserService.View update(@PathVariable String username,@Valid @RequestBody Update r){return users.update(username,r.enabled(),r.roles(),r.password());}
 @GetMapping("/audit") public List<Map<String,Object>> audit(@RequestParam(defaultValue="20")int limit,@RequestParam(defaultValue="0")long before){return users.audit(limit,before);}
}
