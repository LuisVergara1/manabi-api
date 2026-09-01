package dev.luisvergara.manabi.service.strategy;

import java.util.List;
import java.util.Random;

import org.springframework.stereotype.Component;

import dev.luisvergara.manabi.dto.quiz.QuizQuestion;
import dev.luisvergara.manabi.entity.kanjis.Kanji;
import dev.luisvergara.manabi.enums.quizz.QuestionType;
import dev.luisvergara.manabi.enums.quizz.QuizContentType;
import dev.luisvergara.manabi.service.facade.QuizContentFacade;
import dev.luisvergara.manabi.service.factory.KanjiQuestionFactory;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class KanjiQuizStrategy implements QuizStrategy {
    
    private final QuizContentFacade quizContentFacade;
    private final KanjiQuestionFactory kanjiQuestionFactory;
    private final Random random = new Random();
    @Override
    public boolean supports(
            QuizContentType contentType) {
        return contentType == QuizContentType.KANJI;
    }
    @Override
    public QuizQuestion generateQuestion(
            QuestionType questionType) {

        List<Kanji> kanjiList =
                quizContentFacade.getKanji();

        Kanji correct =
                randomItem(kanjiList);

        return kanjiQuestionFactory.createQuestion(
                questionType,
                correct,
                kanjiList
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
