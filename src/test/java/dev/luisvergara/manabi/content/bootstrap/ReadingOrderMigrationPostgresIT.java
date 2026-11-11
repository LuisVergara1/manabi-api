package dev.luisvergara.manabi.content.bootstrap;

import static org.junit.jupiter.api.Assertions.*;
import java.sql.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.core.io.ByteArrayResource;

/** Executes the actual V1 -> V2 SQL on session-local tables, including nonempty readings. */
@EnabledIfEnvironmentVariable(named="MANABI_RUN_BOOTSTRAP_IT",matches="true")
class ReadingOrderMigrationPostgresIT {
    @Test void additiveMigrationPreservesIdentitiesAndThePreviousUtf16Comparator()throws Exception {
        assertEquals(IntegrationDatabaseTarget.expected("MANABI_IT_EXPECTED_DB_URL"),System.getenv("DB_URL"));
        assertEquals(IntegrationDatabaseTarget.expected("MANABI_IT_EXPECTED_DB_USERNAME"),System.getenv("DB_USERNAME"));
        try(var c=DriverManager.getConnection(System.getenv("DB_URL"),System.getenv("DB_USERNAME"),System.getenv("DB_PASSWORD"))) {
            try(var s=c.createStatement();var r=s.executeQuery("select current_database(),(select system_identifier::text from pg_control_system())")){
                r.next();assertEquals("manabi",r.getString(1));assertEquals(IntegrationDatabaseTarget.expected("MANABI_IT_EXPECTED_SYSTEM_ID"),r.getString(2));
            }
            try(var s=c.createStatement()){s.execute("set search_path to pg_temp");}
            script(c,"V1__educational_schema.sql");
            try(var s=c.createStatement()){
                s.execute("insert into kanji(public_id,kanji_character,meaning,stroke_count,jlpt_level,display_order) values ('__MIGRATION_ID__','__MIG__','__TEST__',1,'N5',0)");
            }
            var readings=List.of("\uE000","\uD800\uDC00","あ","い");
            for(int i=0;i<readings.size();i++)try(var s=c.prepareStatement("insert into kanji_reading(kanji_id,reading,reading_type,romaji,is_primary) select id,?,'KUNYOMI','__TEST__',? from kanji")){
                s.setString(1,readings.get(i));s.setBoolean(2,i==3);s.executeUpdate();
            }
            script(c,"V2__editorial_bootstrap_and_reviews.sql");
            var actual=new ArrayList<String>();
            try(var s=c.createStatement();var r=s.executeQuery("select reading from kanji_reading order by editorial_position")){
                while(r.next())actual.add(r.getString(1));
            }
            var expected=new ArrayList<>(readings);expected.sort(Comparator.<String,Boolean>comparing("い"::equals).reversed().thenComparing(Comparator.naturalOrder()));
            assertEquals(expected,actual);
            try(var s=c.createStatement();var r=s.executeQuery("select public_id from kanji")){r.next();assertEquals("__MIGRATION_ID__",r.getString(1));}
            try(var s=c.createStatement();var r=s.executeQuery("select count(*) from editorial_review")){r.next();assertEquals(0,r.getLong(1));}
            try(var s=c.createStatement();var r=s.executeQuery("select current_schema()")){r.next();assertTrue(r.getString(1).startsWith("pg_temp_"));}
        }
    }
    private void script(Connection c,String name)throws Exception {
        try(var in=getClass().getResourceAsStream("/db/migration/"+name)){
            String sql=new String(in.readAllBytes(),StandardCharsets.UTF_8).replaceAll("(?m)^CREATE TABLE ","CREATE TEMP TABLE ");
            ScriptUtils.executeSqlScript(c,new ByteArrayResource(sql.getBytes(StandardCharsets.UTF_8)));
        }
    }
}
