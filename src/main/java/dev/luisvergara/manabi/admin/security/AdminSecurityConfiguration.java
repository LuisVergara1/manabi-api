package dev.luisvergara.manabi.admin.security;
import java.io.*;
import java.net.URI;
import java.util.*;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.*;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.config.ObjectPostProcessor;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.savedrequest.NullRequestCache;
import org.springframework.web.cors.*;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.json.JsonMapper;

@Configuration @EnableWebSecurity @ConditionalOnWebApplication(type=ConditionalOnWebApplication.Type.SERVLET)
public class AdminSecurityConfiguration {
 private static final JsonMapper JSON=JsonMapper.builder().build();
 public static void response(HttpServletResponse response,int status,String code)throws IOException{
  response.setStatus(status);response.setContentType("application/json");response.setCharacterEncoding("UTF-8");
  response.getWriter().write(JSON.writeValueAsString(Map.of("code",code)));
 }
 private static void denied(AdminAudit audit,HttpServletResponse response,String actor,String action,int status,String code)throws IOException{
  try{audit.record(actor,action,"administration","DENIED");}catch(RuntimeException unavailable){response(response,503,"ADMINISTRATION_UNAVAILABLE");return;}
  response(response,status,code);
 }
 @Bean CorsConfigurationSource administrativeCors(@Value("${manabi.security.allowed-origins:}")String configured,Environment env){
  var origins=configured.isBlank()?List.<String>of():Arrays.stream(configured.split(",")).map(String::trim).toList();
  boolean dev=Arrays.asList(env.getActiveProfiles()).contains("dev");
  for(String value:origins){
   URI uri;try{uri=URI.create(value);}catch(RuntimeException e){throw new IllegalArgumentException("Invalid administrative origin");}
   boolean local=Set.of("localhost","127.0.0.1","[::1]","::1").contains(String.valueOf(uri.getHost()));
   if(value.contains("*")||uri.getUserInfo()!=null||uri.getHost()==null||uri.getQuery()!=null||uri.getFragment()!=null
    ||!(uri.getPath()==null||uri.getPath().isEmpty())||!("https".equals(uri.getScheme())||(dev&&local&&"http".equals(uri.getScheme()))))
    throw new IllegalArgumentException("Administrative origins must be explicit HTTPS origins; local HTTP requires dev");
  }
  boolean secure=env.getProperty("server.servlet.session.cookie.secure",Boolean.class,true);
  String same=env.getProperty("server.servlet.session.cookie.same-site","lax");
  if((!secure&&!dev)||("none".equalsIgnoreCase(same)&&!secure))throw new IllegalArgumentException("Insecure administrative cookie configuration");
  var config=new CorsConfiguration();config.setAllowedOrigins(origins);config.setAllowCredentials(true);
  config.setAllowedMethods(List.of("GET","POST","PUT","PATCH","DELETE","OPTIONS"));
  config.setAllowedHeaders(List.of("Content-Type","X-CSRF-TOKEN"));config.setMaxAge(600L);
  var source=new UrlBasedCorsConfigurationSource();source.registerCorsConfiguration("/**",config);return source;
 }
 @Bean SecurityFilterChain administrativeSecurity(HttpSecurity http,AdminAccounts accounts,PasswordEncoder encoder,AdminAudit audit,LoginThrottle throttle,@Qualifier("administrativeCors") CorsConfigurationSource cors)throws Exception {
  var loginMatcher=PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST,"/api/admin/login");
  var provider=new DaoAuthenticationProvider(accounts);provider.setPasswordEncoder(encoder);
  String[] crud={"/api/kana","/api/kana/**","/api/kanji","/api/kanji/**","/api/vocabulary","/api/vocabulary/**","/api/phrases","/api/phrases/**","/api/phrase-categories","/api/phrase-categories/**","/api/particles","/api/particles/**"};
  http.authenticationProvider(provider).cors(c->c.configurationSource(cors));
  // Default HttpSession/XOR CSRF protection remains enabled, including login/logout.
  http.csrf(c->{});
  http.requestCache(c->c.requestCache(new NullRequestCache()));
  http.sessionManagement(c->c.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED).sessionFixation(s->s.changeSessionId()));
  http.authorizeHttpRequests(a->{
   a.requestMatchers(HttpMethod.GET,"/api/admin/csrf").permitAll();
   a.requestMatchers(HttpMethod.POST,"/api/admin/login").permitAll();
   a.requestMatchers("/api/admin/users/**","/api/admin/audit/**","/actuator/**","/api-docs/**","/swagger/**","/swagger-ui/**").hasRole("ADMIN");
   a.requestMatchers(HttpMethod.POST,"/api/admin/publications/revoke").hasRole("ADMIN");
   a.requestMatchers("/api/admin/publications/**").hasAnyRole("ADMIN","PUBLISHER");
   a.requestMatchers(HttpMethod.GET,"/api/admin/editorial/catalog","/api/admin/editorial/identity").hasAnyRole("ADMIN","EDITOR","REVIEWER","PUBLISHER");
   a.requestMatchers("/api/admin/editorial/**").hasAnyRole("ADMIN","REVIEWER");
   a.requestMatchers(HttpMethod.GET,"/api/admin/session").authenticated();
   a.requestMatchers(HttpMethod.POST,"/api/admin/logout").authenticated();
   a.requestMatchers(HttpMethod.GET,crud).hasAnyRole("ADMIN","EDITOR","REVIEWER","PUBLISHER");
   for(HttpMethod method:List.of(HttpMethod.POST,HttpMethod.PUT,HttpMethod.PATCH,HttpMethod.DELETE))a.requestMatchers(method,crud).hasAnyRole("ADMIN","EDITOR");
   a.anyRequest().denyAll();
  });
  http.exceptionHandling(e->e.authenticationEntryPoint((r,s,x)->denied(audit,s,"anonymous","AUTH_REQUIRED",401,"AUTHENTICATION_REQUIRED"))
    .accessDeniedHandler((r,s,x)->{var auth=SecurityContextHolder.getContext().getAuthentication();denied(audit,s,auth==null?"anonymous":auth.getName(),"ACCESS_DENIED",403,"ACCESS_DENIED");}));
  http.formLogin(f->f.loginPage("/api/admin/login").loginProcessingUrl("/api/admin/login")
   .withObjectPostProcessor(new ObjectPostProcessor<UsernamePasswordAuthenticationFilter>(){
    @Override public <O extends UsernamePasswordAuthenticationFilter> O postProcess(O filter){
     filter.setRequiresAuthenticationRequestMatcher(loginMatcher);return filter;
    }
   })
   .successHandler((r,s,a)->{
    try{audit.record(a.getName(),"LOGIN","session","SUCCESS");response(s,200,"AUTHENTICATED");}
    catch(RuntimeException unavailable){var session=r.getSession(false);if(session!=null)session.invalidate();SecurityContextHolder.clearContext();response(s,503,"ADMINISTRATION_UNAVAILABLE");}
   }).failureHandler((r,s,e)->denied(audit,s,"anonymous","LOGIN_FAILED",401,"INVALID_CREDENTIALS")));
  http.logout(l->l.logoutUrl("/api/admin/logout").invalidateHttpSession(true).clearAuthentication(true).deleteCookies("JSESSIONID")
   .logoutSuccessHandler((r,s,a)->{if(a==null){response(s,401,"AUTHENTICATION_REQUIRED");return;}try{audit.record(a.getName(),"LOGOUT","session","SUCCESS");response(s,200,"LOGGED_OUT");}catch(RuntimeException e){response(s,503,"ADMINISTRATION_UNAVAILABLE");}}));
  http.headers(h->h.frameOptions(f->f.deny()).contentSecurityPolicy(c->c.policyDirectives("default-src 'none'; frame-ancestors 'none'")));
  http.addFilterAfter(new OncePerRequestFilter(){
   @Override protected void doFilterInternal(HttpServletRequest r,HttpServletResponse s,FilterChain chain)throws ServletException,IOException {
    var auth=SecurityContextHolder.getContext().getAuthentication();
    if(auth!=null&&auth.isAuthenticated()&&!(auth instanceof org.springframework.security.authentication.AnonymousAuthenticationToken)){
     boolean valid;
     try{valid=auth.getPrincipal() instanceof AdminPrincipal p&&accounts.validSession(p.getUsername(),p.revision());}
     catch(RuntimeException unavailable){response(s,503,"ADMINISTRATION_UNAVAILABLE");return;}
     if(!valid){var session=r.getSession(false);if(session!=null)session.invalidate();SecurityContextHolder.clearContext();response(s,401,"SESSION_EXPIRED");return;}
    }
    chain.doFilter(r,s);
   }
  },SecurityContextHolderFilter.class);
  http.addFilterBefore(new OncePerRequestFilter(){
   @Override protected void doFilterInternal(HttpServletRequest r,HttpServletResponse s,FilterChain chain)throws ServletException,IOException {
    if(loginMatcher.matches(r)){
     String name=r.getParameter("username"),password=r.getParameter("password");
     if(name==null||name.length()>64||password==null||password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>72){denied(audit,s,"anonymous","LOGIN_FAILED",401,"INVALID_CREDENTIALS");return;}
     if(!throttle.allow(r.getRemoteAddr(),name.trim())){denied(audit,s,"anonymous","LOGIN_THROTTLED",429,"LOGIN_THROTTLED");return;}
    }
    chain.doFilter(r,s);
   }
  },UsernamePasswordAuthenticationFilter.class);
  return http.build();
 }
}
