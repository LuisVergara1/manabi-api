package dev.luisvergara.manabi.admin.web;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;
import lombok.RequiredArgsConstructor;
import dev.luisvergara.manabi.admin.AdminEditorialService;
import dev.luisvergara.manabi.content.editorial.EditorialModels.Kind;
@RestController @RequestMapping("/api/admin/editorial") @RequiredArgsConstructor
public class AdminEditorialController {
 public record Decision(@NotNull Kind kind,@NotBlank @Size(max=128)String publicId,@NotBlank @Pattern(regexp="[a-f0-9]{64}")String fingerprint){}
 private final AdminEditorialService editorial;
 private final dev.luisvergara.manabi.admin.AdminCatalogQuery catalog;
 @GetMapping("/catalog") public AdminEditorialService.Page catalog(@RequestParam(required=false)Kind kind,@RequestParam(defaultValue="")String q,@RequestParam(defaultValue="20")int limit,@RequestParam(defaultValue="0")int offset){return editorial.catalog(kind,q,limit,offset);}
 @GetMapping("/identity") public java.util.Map<String,Object> identity(@RequestParam Kind kind,@RequestParam String publicId){return catalog.identity(kind,publicId);}
 @GetMapping("/review") public AdminEditorialService.ReviewView review(@RequestParam Kind kind,@RequestParam String publicId){return editorial.review(kind,publicId);}
 @PostMapping("/approve") public AdminEditorialService.ReviewView approve(@Valid @RequestBody Decision d){return editorial.approve(d.kind(),d.publicId(),d.fingerprint());}
 @PostMapping("/pending") public AdminEditorialService.ReviewView pending(@Valid @RequestBody Decision d){return editorial.pending(d.kind(),d.publicId(),d.fingerprint());}
}
