package dev.luisvergara.manabi.admin.security;
import java.io.Serializable;
import java.util.*;
import org.springframework.security.core.*;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
/** Session principal erases the BCrypt hash after authentication; never returned as an HTTP DTO. */
public final class AdminPrincipal implements UserDetails,CredentialsContainer,Serializable {
 private static final long serialVersionUID=1L;
 private final String username;private String hash;
 private final long revision;private final boolean enabled;private final Set<AdminRole> roles;
 public AdminPrincipal(String username,String hash,long revision,boolean enabled,Set<AdminRole> roles){
  this.username=username;this.hash=hash;this.revision=revision;this.enabled=enabled;this.roles=Set.copyOf(roles);
 }
 public long revision(){return revision;} public Set<AdminRole> roles(){return roles;}
 @Override public String getUsername(){return username;}
 @Override @com.fasterxml.jackson.annotation.JsonIgnore public String getPassword(){return hash;}
 @Override public boolean isEnabled(){return enabled;}
 @Override public Collection<? extends GrantedAuthority> getAuthorities(){return roles.stream().map(r->new SimpleGrantedAuthority("ROLE_"+r.name())).toList();}
 @Override public void eraseCredentials(){hash=null;}
 @Override public String toString(){return "AdministrativePrincipal";}
}
