package dev.luisvergara.manabi.dto.kanjis;

import java.util.List;

import dev.luisvergara.manabi.enums.kanjis.JlptLevel;

public record KanjiRequest( String character,
        String meaning,
        Integer strokeCount,
        JlptLevel jlptLevel,
        List<KanjiReadingRequest> readings,
        List<KanjiExampleRequest> examples) {
    
}
