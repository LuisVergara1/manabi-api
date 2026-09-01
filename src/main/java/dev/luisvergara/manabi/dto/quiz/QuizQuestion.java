package dev.luisvergara.manabi.dto.quiz;

import java.util.List;

import dev.luisvergara.manabi.enums.quizz.QuestionFormat;
import dev.luisvergara.manabi.enums.quizz.QuestionType;

public record QuizQuestion(
        String question,
        List<String> options,
        String correctAnswer,
        QuestionType questionType,
        QuestionFormat questionFormat) {
    
}
