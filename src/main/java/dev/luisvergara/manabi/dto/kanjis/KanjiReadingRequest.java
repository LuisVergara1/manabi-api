package dev.luisvergara.manabi.dto.kanjis;

import dev.luisvergara.manabi.enums.kanjis.ReadingType;

public record KanjiReadingRequest(
        String reading,
        ReadingType readingType) {
    
}
