package dev.luisvergara.manabi.content.bootstrap;

import java.sql.*;
import java.nio.charset.StandardCharsets;
import javax.sql.DataSource;
import org.springframework.context.*;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.core.io.ByteArrayResource;
import java.util.Map;

/** Session-local TEMP tables in manabi; never migrations, data loads or resets against public. */
public final class TemporaryEditorialDatabase implements ApplicationContextInitializer<org.springframework.context.ConfigurableApplicationContext> {
    public void initialize(org.springframework.context.ConfigurableApplicationContext context) {
        if(!"true".equals(System.getenv("MANABI_RUN_BOOTSTRAP_IT")))throw new IllegalStateException("Explicit integration opt-in required");
        Connection connection=null;
        try {
            String url=System.getenv("DB_URL");
            if(!IntegrationDatabaseTarget.expected("MANABI_IT_EXPECTED_DB_URL").equals(url)||!IntegrationDatabaseTarget.expected("MANABI_IT_EXPECTED_DB_USERNAME").equals(System.getenv("DB_USERNAME")))
                throw new IllegalStateException("Unapproved integration target");
            var c=DriverManager.getConnection(url,System.getenv("DB_USERNAME"),System.getenv("DB_PASSWORD"));connection=c;
            try(var s=c.createStatement();var r=s.executeQuery("select current_database(),(select system_identifier::text from pg_control_system())")) {
                r.next();if(!"manabi".equals(r.getString(1))||!IntegrationDatabaseTarget.expected("MANABI_IT_EXPECTED_SYSTEM_ID").equals(r.getString(2)))throw new IllegalStateException("Wrong PostgreSQL instance");
            }
            try(var s=c.createStatement()){s.execute("set search_path to pg_temp");}
            for(String name:new String[]{"V1__educational_schema.sql","V2__editorial_bootstrap_and_reviews.sql","V3__publication_candidates.sql","V4__publication_artifacts.sql","V5__administrative_security.sql"}) {
                try(var in=getClass().getResourceAsStream("/db/migration/"+name)) {
                    String sql=new String(in.readAllBytes(),StandardCharsets.UTF_8).replaceAll("(?m)^CREATE TABLE ","CREATE TEMP TABLE ");
                    ScriptUtils.executeSqlScript(c,new ByteArrayResource(sql.getBytes(StandardCharsets.UTF_8)));
                }
            }
            String schema;try(var s=c.createStatement();var r=s.executeQuery("select current_schema()")){r.next();schema=r.getString(1);}
            if(!schema.startsWith("pg_temp_"))throw new IllegalStateException("Temporary schema was not selected");
            var dataSource=new SingleConnectionDataSource(c,true);
            var registry=(org.springframework.beans.factory.support.BeanDefinitionRegistry)context.getBeanFactory();
            var definition=new org.springframework.beans.factory.support.RootBeanDefinition(SingleConnectionDataSource.class,()->dataSource);
            definition.setDestroyMethodName("destroy");registry.registerBeanDefinition("dataSource",definition);
            context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("temporary-editorial-only",Map.of(
                "spring.flyway.enabled","false","spring.jpa.hibernate.ddl-auto","none","spring.jpa.properties.hibernate.default_schema",schema,
                "manabi.publication.root",java.nio.file.Files.createTempDirectory("manabi-publication-it-").toString())));
            System.out.println("TEMPORARY_EDITORIAL_SCHEMA="+schema+" PUBLIC_UNTOUCHED");
        }catch(Exception e){if(connection!=null)try{connection.close();}catch(SQLException close){e.addSuppressed(close);}
            throw new IllegalStateException("Cannot prepare isolated PostgreSQL session",e);}
    }
}
