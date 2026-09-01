package dev.luisvergara.manabi.service.strategy;

import dev.luisvergara.manabi.dto.quiz.QuizQuestion;
import dev.luisvergara.manabi.enums.quizz.QuestionType;
import dev.luisvergara.manabi.enums.quizz.QuizContentType;

public interface QuizStrategy {
    
    boolean supports(
            QuizContentType contentType
    );

    QuizQuestion generateQuestion(
            QuestionType questionType
    );
}
