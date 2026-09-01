package dev.luisvergara.manabi.service.factory;

import java.util.List;

import dev.luisvergara.manabi.dto.quiz.QuizQuestion;
import dev.luisvergara.manabi.enums.quizz.QuestionType;

public interface QuizQuestionFactory<T> {

    QuizQuestion createQuestion(
        QuestionType questionType,
        T correct,
        List<T> availableContent
    );

}
