package dev.luisvergara.manabi.admin.security;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.access.AccessDeniedException;
public final class AdminIdentity {
 private AdminIdentity(){}
 public static String actor(){
  var a=SecurityContextHolder.getContext().getAuthentication();
  if(a==null||!a.isAuthenticated()||a instanceof AnonymousAuthenticationToken)throw new AccessDeniedException("Authentication required");
  return a.getName();
 }
}
