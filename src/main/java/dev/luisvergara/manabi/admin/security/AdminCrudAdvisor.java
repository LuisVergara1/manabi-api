package dev.luisvergara.manabi.admin.security;
import java.lang.reflect.Method;
import java.util.*;
import org.aopalliance.intercept.*;
import org.springframework.aop.support.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
/** Protects the existing mutating services without rewriting their business logic or DTOs. */
@Configuration
public class AdminCrudAdvisor {
 private static final Set<String> TYPES=Set.of("KanaService","KanjiService","VocabularyService","PhraseService","PhraseReferenceService");
 private static final Set<String> WRITES=Set.of("save","update","updateResponse","delete","updateCategory","createParticle","updateParticle","deleteParticle");
 @Bean @org.springframework.context.annotation.Role(org.springframework.beans.factory.config.BeanDefinition.ROLE_INFRASTRUCTURE)
 static org.springframework.aop.Advisor protectedEducationalWrites(ObjectProvider<AdminAudit> audit,ObjectProvider<PlatformTransactionManager> manager,ObjectProvider<org.springframework.jdbc.core.JdbcTemplate> jdbc){
  var pointcut=new StaticMethodMatcherPointcut(){
   @Override public boolean matches(Method m,Class<?> type){return type.getPackageName().equals("dev.luisvergara.manabi.service")&&TYPES.contains(type.getSimpleName())&&WRITES.contains(m.getName());}
  };
  var advisor=new DefaultPointcutAdvisor(pointcut,(MethodInterceptor)invocation->{
   var auth=SecurityContextHolder.getContext().getAuthentication();
   if(auth==null||auth.getAuthorities().stream().noneMatch(a->Set.of("ROLE_ADMIN","ROLE_EDITOR").contains(a.getAuthority())))throw new AccessDeniedException("Editor permission required");
   String actor=AdminIdentity.actor();var tx=new TransactionTemplate(manager.getObject());
   try{return tx.execute(status->{
    try{
     String service=invocation.getThis().getClass().getSimpleName().split("\\$\\$")[0];
     String method=invocation.getMethod().getName();
     String table=switch(service){
      case "KanaService"->"kana";case "KanjiService"->"kanji";
      case "VocabularyService"->method.equals("updateCategory")?"vocabulary_category":"vocabulary";
      case "PhraseService"->"phrase";case "PhraseReferenceService"->method.equals("updateCategory")?"phrase_category":"particle";
      default->throw new IllegalStateException("Unknown audited service");
     };
     String column=table.equals("vocabulary_category")?"code":"public_id";
     String identity=null;
     if(invocation.getArguments().length>0&&invocation.getArguments()[0] instanceof Long id){
      var ids=jdbc.getObject().queryForList("select "+column+" from "+table+" where id=?",String.class,id);
      if(ids.size()==1)identity=ids.getFirst();
     }
     Object result=invocation.proceed();
     if(identity==null&&result!=null){
      for(String accessor:List.of("publicId","getPublicId","code","getCode"))try{
       Object value=result.getClass().getMethod(accessor).invoke(result);
       if(value instanceof String text){identity=text;break;}
      }catch(NoSuchMethodException absent){}catch(ReflectiveOperationException invalid){throw new IllegalStateException("Cannot identify audited aggregate",invalid);}
     }
     if(identity==null)throw new IllegalStateException("Missing audited aggregate identity");
     audit.getObject().record(actor,"CRUD_"+invocation.getMethod().getName().toUpperCase(Locale.ROOT),table+":"+identity,"SUCCESS");
     return result;
    }catch(RuntimeException e){throw e;}catch(Error e){throw e;}catch(Throwable e){throw new IllegalStateException("Educational operation failed");}
   });}catch(RuntimeException failure){throw failure;}
  });
  advisor.setOrder(0);return advisor;
 }
}
