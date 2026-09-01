package dev.luisvergara.manabi.service.strategy;

import java.util.List;
import java.util.Random;

import org.springframework.stereotype.Component;

import dev.luisvergara.manabi.dto.quiz.QuizQuestion;
import dev.luisvergara.manabi.entity.vocabulary.Vocabulary;
import dev.luisvergara.manabi.enums.quizz.QuestionType;
import dev.luisvergara.manabi.enums.quizz.QuizContentType;
import dev.luisvergara.manabi.service.facade.QuizContentFacade;
import dev.luisvergara.manabi.service.factory.VocabularyQuestionFactory;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Component
public class VocabularyQuizStrategy implements QuizStrategy {
    
    private final QuizContentFacade quizContentFacade;
    private final VocabularyQuestionFactory vocabularyQuestionFactory;

    private final Random random = new Random();

    @Override
    public boolean supports(QuizContentType contentType) {
        return contentType == QuizContentType.VOCABULARY;
    }

    @Override
    public QuizQuestion generateQuestion(
            QuestionType questionType) {

        List<Vocabulary> vocabularyList =
                quizContentFacade.getVocabulary();

        Vocabulary correct =
                randomItem(vocabularyList);

        return vocabularyQuestionFactory.createQuestion(
                questionType,
                correct,
                vocabularyList
        );
    }

    private <T> T randomItem(List<T> items) {

        if (items == null || items.isEmpty()) {
            throw new IllegalStateException(
                    "No existen datos suficientes para generar el quiz"
            );
        }

        return items.get(
                random.nextInt(items.size())
        );
    }
}
