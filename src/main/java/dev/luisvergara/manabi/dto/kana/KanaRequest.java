package dev.luisvergara.manabi.dto.kana;
import jakarta.validation.constraints.*;
import dev.luisvergara.manabi.enums.kana.*;
public record KanaRequest(@NotBlank @Size(max=32) String character,
    @NotBlank @Size(max=64) String romaji, @NotNull KanaType type, @NotNull KanaGroup group,
    @NotNull @Min(0) @Max(9007199254740991L) Long displayOrder, Long version) {}
