package dev.luisvergara.manabi.content.publication;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.sql.*;import java.util.*;import java.util.concurrent.*;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.core.io.ByteArrayResource;
import dev.luisvergara.manabi.admin.security.AdminAudit;

/** Real two-session MVCC test. Refuses the shared Manabi server; only a local disposable database. */
@EnabledIfEnvironmentVariable(named="MANABI_DISPOSABLE_PG_URL",matches="jdbc:postgresql://.*")
class PublicationRevocationConcurrencyIT {
 @Test void waitingRepeatableReadStorageRetriesAndThenRejectsRevocation()throws Exception{
  String url=System.getenv("MANABI_DISPOSABLE_PG_URL");
  assertTrue(url.matches("jdbc:postgresql://(127\\.0\\.0\\.1|localhost):[0-9]+/manabi_stabilization_test"),"Disposable local database required");
  String user=System.getenv("MANABI_DISPOSABLE_PG_USER"),password=System.getenv("MANABI_DISPOSABLE_PG_PASSWORD");
  String schema="stage26_"+UUID.randomUUID().toString().replace("-","");
  try(var a=DriverManager.getConnection(url,user,password);var b=DriverManager.getConnection(url,user,password)){
   try(var s=a.createStatement();var rs=s.executeQuery("select current_database()")){rs.next();assertEquals("manabi_stabilization_test",rs.getString(1));}
   try(var s=a.createStatement()){s.execute("create schema "+schema);}
   a.setSchema(schema);b.setSchema(schema);try(var s=b.createStatement()){s.execute("set statement_timeout=15000");}
   try{
    for(String name:List.of("V3__publication_candidates.sql","V4__publication_artifacts.sql","V5__administrative_security.sql"))
     try(var in=getClass().getResourceAsStream("/db/migration/"+name)){ScriptUtils.executeSqlScript(a,new ByteArrayResource(in.readAllBytes()));}
    var ja=new JdbcTemplate(new SingleConnectionDataSource(a,true));var jb=new JdbcTemplate(new SingleConnectionDataSource(b,true));
    String release="sha256:"+"1".repeat(64),hash="2".repeat(64),job=UUID.randomUUID().toString();
    ja.update("insert into publication_candidate values (?,?,?,?,now())",release,"manabi.frontend.v1",hash,1);
    ja.update("insert into publication_job values (?,'DISTRIBUTABLE','READY','CORE',?,'[]','[]',?,?,now())",job,hash,release,release);
    var snapshot=new PublicationSnapshotService.Snapshot(PublicationSnapshotService.Profile.CORE,List.of(),List.of(),hash);
    var snapshots=mock(PublicationSnapshotService.class);when(snapshots.capture(any())).thenReturn(snapshot);
    var ledgerA=new PublicationArtifactLedger(ja,new AdminAudit(ja),snapshots);
    var ledgerB=new PublicationArtifactLedger(jb,new AdminAudit(jb),snapshots);
    a.setAutoCommit(false);
    ja.queryForList("select release_id from publication_candidate where release_id=? for update",release);
    b.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);b.setAutoCommit(false);
    int pid;try(var s=b.createStatement();var rs=s.executeQuery("select pg_backend_pid()")){rs.next();pid=rs.getInt(1);}
    var executor=Executors.newSingleThreadExecutor();
    try{
     var task=executor.submit(()->{
      try{
       ledgerB.stored(new PublicationArtifactLedger.Job(job,release,PublicationSnapshotService.Profile.CORE,hash,hash),hash);
       b.commit();return "COMMITTED";
      }catch(Exception failure){
       b.rollback();Throwable cause=failure;
       while(cause!=null){if(cause instanceof SQLException sql)return sql.getSQLState();cause=cause.getCause();}
       throw new RuntimeException(failure);
      }
     });
     // Wait for actual lock contention, not an assumed thread timing.
     long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);
     boolean waiting=false;
     while(System.nanoTime()<deadline){
      waiting=Boolean.TRUE.equals(ja.queryForObject("select exists(select 1 from pg_locks where pid=? and not granted)",Boolean.class,pid));
      if(waiting)break;Thread.sleep(10);
     }
     assertTrue(waiting,"Storage did not reach the candidate lock");
     ledgerA.revoke(release,"isolated concurrent revocation");a.commit();
     assertEquals("40001",task.get(10,TimeUnit.SECONDS),"Old RR snapshot must abort");
     assertThrows(IllegalStateException.class,()->ledgerB.stored(new PublicationArtifactLedger.Job(job,release,PublicationSnapshotService.Profile.CORE,hash,hash),hash));
     b.rollback();
     assertEquals(0,ja.queryForObject("select count(*) from publication_storage",Integer.class));
     assertEquals(1,ja.queryForObject("select count(*) from publication_revocation",Integer.class));
    }finally{a.rollback();executor.shutdownNow();assertTrue(executor.awaitTermination(20,TimeUnit.SECONDS),"Diagnostic worker did not terminate");}
   }finally{
    if(!a.getAutoCommit())a.rollback();if(!b.getAutoCommit())b.rollback();a.setAutoCommit(true);a.setSchema("public");
    if(!schema.matches("stage26_[a-f0-9]{32}"))throw new IllegalStateException("Unsafe diagnostic schema");
    try(var s=a.createStatement()){s.execute("drop schema "+schema+" cascade");}
   }
  }
 }
}
