package dev.luisvergara.manabi.dto.kanjis;
import jakarta.validation.constraints.*;
public record KanjiExampleRequest(@NotBlank @Size(max=512) String japanese,
    @NotBlank @Size(max=512) String reading, @NotBlank @Size(max=4000) String meaning,
    @NotNull @PositiveOrZero @Max(9007199254740991L) Long displayOrder) {}
