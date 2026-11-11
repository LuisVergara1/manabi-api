package dev.luisvergara.manabi.dto.vocabulary;
import jakarta.validation.constraints.*;
public record VocabularyCategoryRequest(@NotBlank @Size(max=120) String name,@Size(max=16) String symbol,
 @NotNull @Min(0) @Max(9007199254740991L) Long displayOrder,@NotNull Boolean showInCatalog,@NotNull Long version){}
