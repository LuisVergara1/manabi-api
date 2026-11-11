package dev.luisvergara.manabi.dto.vocabulary;
import java.util.Set;
import jakarta.validation.constraints.*;
public record VocabularyRequest(@NotBlank @Size(max=256) String japanese,
    @NotBlank @Size(max=256) String kana, @NotBlank @Size(max=512) String romaji,
    @NotBlank @Size(max=4000) String meaning, @NotNull @PositiveOrZero @Max(9007199254740991L) Long displayOrder,
    @NotEmpty Set<@NotBlank String> categories, Long version) {}
