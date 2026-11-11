package dev.luisvergara.manabi.admin.web;
import java.io.IOException;
import java.util.Map;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.dao.DataAccessException;
import jakarta.persistence.PersistenceException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import lombok.RequiredArgsConstructor;
import dev.luisvergara.manabi.admin.security.*;
/** Never serializes exception messages, SQL, paths or traces. */
@RestControllerAdvice(basePackages="dev.luisvergara.manabi.admin.web") @RequiredArgsConstructor
public class AdminApiErrors {
 private final AdminAudit audit;
 private ResponseEntity<Map<String,String>> error(int status,String code){
  String actor;try{actor=AdminIdentity.actor();}catch(AccessDeniedException noUser){actor="anonymous";}
  try{audit.record(actor,"REQUEST_FAILED","administration",status==403?"DENIED":"FAILURE");}catch(RuntimeException unavailable){return ResponseEntity.status(503).body(Map.of("code","ADMINISTRATION_UNAVAILABLE"));}
  return ResponseEntity.status(status).body(Map.of("code",code));
 }
 @ExceptionHandler(org.springframework.web.server.ResponseStatusException.class)
 public ResponseEntity<?> domain(org.springframework.web.server.ResponseStatusException e){
  int status=e.getStatusCode().value();return error(status,status==404?"CONTENT_NOT_FOUND":status==409?"REVISION_CONFLICT":"INVALID_REQUEST");
 }
 @ExceptionHandler({org.springframework.dao.OptimisticLockingFailureException.class,org.springframework.dao.CannotAcquireLockException.class,jakarta.persistence.OptimisticLockException.class})
 public ResponseEntity<?> concurrent(){return error(409,"CONCURRENT_CHANGE");}
 @ExceptionHandler(AccessDeniedException.class) public ResponseEntity<?> forbidden(){return error(403,"ACCESS_DENIED");}
 @ExceptionHandler({MethodArgumentNotValidException.class,HttpMessageNotReadableException.class,MethodArgumentTypeMismatchException.class,IllegalArgumentException.class})
 public ResponseEntity<?> invalid(){return error(400,"INVALID_REQUEST");}
 @ExceptionHandler(IllegalStateException.class) public ResponseEntity<?> conflict(){return error(409,"OPERATION_REJECTED");}
 @ExceptionHandler(IOException.class) public ResponseEntity<?> artifact(){return error(409,"ARTIFACT_UNAVAILABLE");}
 @ExceptionHandler({DataAccessException.class,PersistenceException.class}) public ResponseEntity<?> storage(){return error(503,"ADMINISTRATION_UNAVAILABLE");}
 @ExceptionHandler(Exception.class) public ResponseEntity<?> unexpected(){return error(500,"ADMINISTRATION_ERROR");}
}
