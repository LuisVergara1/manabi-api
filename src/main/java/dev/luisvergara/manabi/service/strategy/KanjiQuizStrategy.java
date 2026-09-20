package dev.luisvergara.manabi.service.strategy;

import java.util.List;
import java.util.Random;

import org.springframework.stereotype.Component;

import dev.luisvergara.manabi.dto.quiz.QuizQuestion;
import dev.luisvergara.manabi.dto.quiz.QuizRequest;
import dev.luisvergara.manabi.entity.kanjis.Kanji;
import dev.luisvergara.manabi.enums.quizz.QuestionType;
import dev.luisvergara.manabi.enums.quizz.QuizContentType;
import dev.luisvergara.manabi.service.facade.QuizContentFacade;
import dev.luisvergara.manabi.service.factory.KanjiQuestionFactory;
import dev.luisvergara.manabi.helper.QuizRandomSelector;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class KanjiQuizStrategy implements QuizStrategy {
    
    private final QuizContentFacade quizContentFacade;
    private final KanjiQuestionFactory kanjiQuestionFactory;
    private final QuizRandomSelector quizRandomSelector; 
    @Override
    public boolean supports(
            QuizContentType contentType) {
        return contentType == QuizContentType.KANJI;
    }
    @Override
    public QuizQuestion generateQuestion(
        QuestionType questionType,
        QuizRequest request) {

    List<Kanji> kanjiList =
            quizContentFacade.getKanji();

    Kanji correct =
            quizRandomSelector.select(kanjiList);

    return kanjiQuestionFactory.createQuestion(
            questionType,
            correct,
            kanjiList);
    }


}
