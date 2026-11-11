package dev.luisvergara.manabi.admin.security;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;import java.sql.ResultSet;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.*;
import org.springframework.security.crypto.password.PasswordEncoder;
class AdminAccountsPaginationTest{
 @Test void rolesAreBatchedWithoutMixingUsers()throws Exception{
  var jdbc=mock(JdbcTemplate.class);
  var service=new AdminAccounts(jdbc,mock(PasswordEncoder.class),mock(AdminAudit.class));
  String sql="select id,username,enabled,auth_revision from admin_user order by username limit ? offset ?";
  when(jdbc.queryForList(sql,2,0)).thenReturn(List.of(
   Map.of("id",1L,"username","alice","enabled",true,"auth_revision",2L),
   Map.of("id",2L,"username","bob","enabled",false,"auth_revision",5L)));
  doAnswer(call->{
   RowCallbackHandler callback=call.getArgument(1);
   for(var row:List.of(Map.entry(1L,"EDITOR"),Map.entry(2L,"ADMIN"),Map.entry(2L,"REVIEWER"))){
    var rs=mock(ResultSet.class);when(rs.getLong(1)).thenReturn(row.getKey());when(rs.getString(2)).thenReturn(row.getValue());callback.processRow(rs);
   }return null;
  }).when(jdbc).query(eq("select user_id,role from admin_user_role where user_id in (?,?)"),any(RowCallbackHandler.class),eq(1L),eq(2L));
  var users=service.users(2,0);assertEquals(2,users.size());assertEquals(Set.of(AdminRole.EDITOR),users.get(0).roles());
  assertEquals(Set.of(AdminRole.ADMIN,AdminRole.REVIEWER),users.get(1).roles());assertFalse(users.get(1).enabled());assertEquals(5,users.get(1).revision());
  verify(jdbc).queryForList(sql,2,0);
  verify(jdbc).query(eq("select user_id,role from admin_user_role where user_id in (?,?)"),any(RowCallbackHandler.class),eq(1L),eq(2L));
  verifyNoMoreInteractions(jdbc);
 }
 @Test void emptyPageDoesNotQueryRoles(){
  var jdbc=mock(JdbcTemplate.class);var service=new AdminAccounts(jdbc,mock(PasswordEncoder.class),mock(AdminAudit.class));
  when(jdbc.queryForList("select id,username,enabled,auth_revision from admin_user order by username limit ? offset ?",20,500)).thenReturn(List.of());
  assertEquals(List.of(),service.users(20,500));
  verify(jdbc).queryForList("select id,username,enabled,auth_revision from admin_user order by username limit ? offset ?",20,500);verifyNoMoreInteractions(jdbc);
 }
}
