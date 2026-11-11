package dev.luisvergara.manabi.dto.kanjis;
import dev.luisvergara.manabi.enums.kanjis.ReadingType;
import jakarta.validation.constraints.*;
public record KanjiReadingRequest(@NotBlank @Size(max=128) String reading,
    @NotNull ReadingType readingType, @Size(max=256) String romaji, boolean primary) {}
