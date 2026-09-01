package dev.luisvergara.manabi.service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import org.springframework.stereotype.Service;

import dev.luisvergara.manabi.dto.quiz.QuizQuestion;
import dev.luisvergara.manabi.entity.kana.Kana;
import dev.luisvergara.manabi.enums.kana.KanaGroup;
import dev.luisvergara.manabi.enums.kana.KanaType;
import dev.luisvergara.manabi.enums.quizz.QuestionType;
import dev.luisvergara.manabi.enums.quizz.QuizContentType;
import dev.luisvergara.manabi.service.facade.QuizContentFacade;
import dev.luisvergara.manabi.service.factory.KanaQuestionFactory;
import dev.luisvergara.manabi.service.strategy.QuizStrategy;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class QuizService {
    
    private final KanaQuestionFactory kanaQuestionFactory;
    private final QuizContentFacade quizContentFacade;
    private final List<QuizStrategy> quizStrategies;

    private final Random random = new Random();

    public List<QuizQuestion> generateQuestions(
            QuizContentType contentType,
            List<QuestionType> questionTypes,
            int amount,
            KanaType kanaType,
            KanaGroup kanaGroup) {

        if (amount <= 0 || amount > 50) {
            throw new IllegalArgumentException(
                    "La cantidad de preguntas debe estar entre 1 y 50"
            );
        }

        if (questionTypes == null || questionTypes.isEmpty()) {
            throw new IllegalArgumentException(
                    "Debe existir al menos un tipo de pregunta"
            );
        }

        /*
         * Kana tiene su propia generación porque permite
         * filtrar por KanaType y KanaGroup.
         */
        if (contentType == QuizContentType.KANA) {
            return generateKanaQuestions(
                    questionTypes,
                    amount,
                    kanaType,
                    kanaGroup
            );
        }

        List<QuizQuestion> questions = new ArrayList<>();

        for (int i = 0; i < amount; i++) {

            QuestionType questionType =
                    randomItem(questionTypes);

            questions.add(
                    generateQuestion(
                            contentType,
                            questionType
                    )
            );
        }

        return questions;
    }

    /*
     * =========================
     * KANA
     * =========================
     */

   private List<QuizQuestion> generateKanaQuestions(
        List<QuestionType> questionTypes,
        int amount,
        KanaType kanaType,
        KanaGroup kanaGroup) {

    List<Kana> kanaList =
            quizContentFacade.getKana(kanaType, kanaGroup);

    if (kanaList.isEmpty()) {
        throw new IllegalStateException(
                "No existen Kana para los filtros seleccionados"
        );
    }

    for (QuestionType questionType : questionTypes) {
        if (questionType != QuestionType.READING
                && questionType != QuestionType.CHARACTER) {

            throw new IllegalArgumentException(
                    "Tipo de pregunta no válido para Kana: "
                            + questionType
            );
        }
    }

    int maxCombinations =
            kanaList.size() * questionTypes.size();

    if (amount > maxCombinations) {
        throw new IllegalArgumentException(
                "Solo existen "
                        + maxCombinations
                        + " combinaciones distintas con los filtros seleccionados"
        );
    }

    List<QuizQuestion> questions = new ArrayList<>();
    Set<String> usedQuestions = new HashSet<>();

    while (questions.size() < amount) {

        Kana kana = randomItem(kanaList);
        QuestionType questionType =
                randomItem(questionTypes);

        String questionKey =
                kana.getId() + "-" + questionType;

        if (usedQuestions.contains(questionKey)) {
            continue;
        }

        usedQuestions.add(questionKey);

        questions.add(
                kanaQuestionFactory.createQuestion(
                        questionType,
                        kana,
                        kanaList
                )
        );
    }

    return questions;
}
    /*
     * =========================
     * UTILIDADES
     * =========================
     */
    private <T> T randomItem(
            List<T> items) {

        if (items == null || items.isEmpty()) {
            throw new IllegalStateException(
                    "No existen datos suficientes para generar el quiz"
            );
        }

        return items.get(
                random.nextInt(items.size())
        );
    }

    private QuizQuestion generateQuestion(
        QuizContentType contentType,
        QuestionType questionType) {

    QuizStrategy strategy = quizStrategies.stream()
            .filter(s -> s.supports(contentType))
            .findFirst()
            .orElseThrow(() ->
                    new IllegalArgumentException(
                            "Tipo de contenido no válido: " + contentType
                    )
            );

    return strategy.generateQuestion(questionType);
}
}

