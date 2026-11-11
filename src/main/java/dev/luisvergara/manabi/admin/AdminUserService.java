package dev.luisvergara.manabi.admin;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.security.access.prepost.PreAuthorize;
import lombok.RequiredArgsConstructor;
import dev.luisvergara.manabi.admin.security.*;
@Service @RequiredArgsConstructor @PreAuthorize("hasRole('ADMIN')")
public class AdminUserService {
 public record View(String username,boolean enabled,Set<AdminRole> roles){}
 private final AdminAccounts accounts;private final AdminAudit audit;
 private View view(AdminAccounts.User u){return new View(u.username(),u.enabled(),u.roles());}
 public List<View> users(int limit,int offset){return accounts.users(limit,offset).stream().map(this::view).toList();}
 public View create(String name,String password,Set<AdminRole> roles){return view(accounts.create(name,password,roles,AdminIdentity.actor()));}
 public View update(String name,boolean enabled,Set<AdminRole> roles,String password){return view(accounts.change(name,enabled,roles,password,AdminIdentity.actor()));}
 public List<Map<String,Object>> audit(int limit,long before){return audit.recent(limit,before);}
}
