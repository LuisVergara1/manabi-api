package dev.luisvergara.manabi.dto.quiz;

import dev.luisvergara.manabi.enums.kana.KanaGroup;
import dev.luisvergara.manabi.enums.kana.KanaType;


public record QuizRequest(
     KanaType kanaType,
     KanaGroup kanaGroup
) {


}