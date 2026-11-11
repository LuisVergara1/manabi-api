package dev.luisvergara.manabi.dto.phrase;
import jakarta.validation.constraints.*;
public record PhraseCategoryRequest(@NotBlank String name,@NotBlank String description,
 @NotNull @Min(0) @Max(9007199254740991L) Long displayOrder,@NotNull Boolean enabled,@NotNull Long version) {}
