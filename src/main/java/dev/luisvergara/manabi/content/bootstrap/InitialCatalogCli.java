package dev.luisvergara.manabi.content.bootstrap;

import java.nio.file.*;
import org.springframework.boot.*;
import dev.luisvergara.manabi.ManabiApiApplication;
import dev.luisvergara.manabi.content.publication.contract.CanonicalJson;

/** Manual invocation only. Migrations must already be applied. Default is read-only dry-run; never registered as an application runner. */
public final class InitialCatalogCli {
    public static void main(String[] args)throws Exception {
        String report="target/bootstrap/dry-run.json",confirmation=null;boolean load=false;
        for(String arg:args) {
            if(arg.equals("--dry-run"))continue;
            else if(arg.equals("--load"))load=true;
            else if(arg.startsWith("--confirm-plan="))confirmation=arg.substring(15);
            else if(arg.startsWith("--report="))report=arg.substring(9);
            else throw new IllegalArgumentException("Unsupported argument "+arg);
        }
        if(load&&(confirmation==null||!confirmation.matches("[a-f0-9]{64}")))throw new IllegalArgumentException("--load requires --confirm-plan=<approved dry-run digest>");
        System.setProperty("spring.devtools.restart.enabled","false");
        var app=new SpringApplication(ManabiApiApplication.class);app.setWebApplicationType(WebApplicationType.NONE);
        try(var context=app.run("--spring.main.banner-mode=off","--spring.flyway.enabled=false")){
            var source=new InitialCatalogReader().readResources();var service=context.getBean(InitialCatalogLoader.class);
            var plan=service.dryRun(source);var json=new CanonicalJson();Path destination=Path.of(report).toAbsolutePath();
            Files.createDirectories(destination.getParent());Files.write(destination,json.bytes(plan.json()));
            System.out.println("DRY_RUN_REPORT="+destination+" PLAN_SHA256="+plan.sha256()+" "+plan.details().get("summary"));
            if(load)System.out.println("LOAD_RESULT="+service.apply(source,confirmation));
            else System.out.println("NO_CONTENT_WRITTEN; explicit authorization is required for --load");
        }
    }
}
