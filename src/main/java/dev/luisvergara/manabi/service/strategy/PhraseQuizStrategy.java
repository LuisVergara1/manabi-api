package dev.luisvergara.manabi.service.strategy;

import java.util.List;
import java.util.Random;

import org.springframework.stereotype.Component;

import dev.luisvergara.manabi.dto.quiz.QuizQuestion;
import dev.luisvergara.manabi.entity.phrase.Phrase;
import dev.luisvergara.manabi.enums.quizz.QuestionType;
import dev.luisvergara.manabi.enums.quizz.QuizContentType;
import dev.luisvergara.manabi.service.facade.QuizContentFacade;
import dev.luisvergara.manabi.service.factory.PhraseQuestionFactory;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class PhraseQuizStrategy implements QuizStrategy {
     
     private final QuizContentFacade quizContentFacade;
    private final PhraseQuestionFactory phraseQuestionFactory;

    private final Random random = new Random();

    @Override
    public boolean supports(QuizContentType contentType) {
        return contentType == QuizContentType.PHRASE;
    }

    @Override
    public QuizQuestion generateQuestion(
            QuestionType questionType) {

        List<Phrase> phraseList =
                quizContentFacade.getPhrases();

        Phrase correct =
                randomItem(phraseList);

        return phraseQuestionFactory.createQuestion(
                questionType,
                correct,
                phraseList
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
