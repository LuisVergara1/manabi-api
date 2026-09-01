package dev.luisvergara.manabi.service.factory;

import dev.luisvergara.manabi.dto.quiz.QuizQuestion;
import dev.luisvergara.manabi.entity.kanjis.Kanji;
import dev.luisvergara.manabi.enums.quizz.QuestionFormat;
import dev.luisvergara.manabi.enums.quizz.QuestionType;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

    @Component
    public class KanjiQuestionFactory
        implements QuizQuestionFactory<Kanji> {

            @Override
            public QuizQuestion createQuestion(
            QuestionType questionType,
            Kanji correct,
            List<Kanji> availableContent) {

        if (questionType == QuestionType.MEANING) {

            List<String> options = generateOptions(
                    correct.getMeaning(),
                    availableContent.stream()
                            .map(Kanji::getMeaning)
                            .toList()
            );

            return new QuizQuestion(
                    "¿Qué significa " + correct.getCharacter() + "?",
                    options,
                    correct.getMeaning(),
                    QuestionType.MEANING,
                    QuestionFormat.MULTIPLE_CHOICE
            );
        }

        if (questionType == QuestionType.CHARACTER) {

            List<String> options = generateOptions(
                    correct.getCharacter(),
                    availableContent.stream()
                            .map(Kanji::getCharacter)
                            .toList()
            );

            return new QuizQuestion(
                    "¿Cuál es el kanji de \"" + correct.getMeaning() + "\"?",
                    options,
                    correct.getCharacter(),
                    QuestionType.CHARACTER,
                    QuestionFormat.MULTIPLE_CHOICE
            );
        }

        if (questionType == QuestionType.STROKE_COUNT) {

            List<String> options = generateOptions(
                    correct.getStrokeCount().toString(),
                    availableContent.stream()
                            .map(kanji -> kanji.getStrokeCount().toString())
                            .toList()
            );

            return new QuizQuestion(
                    "¿Cuántos trazos tiene " + correct.getCharacter() + "?",
                    options,
                    correct.getStrokeCount().toString(),
                    QuestionType.STROKE_COUNT,
                    QuestionFormat.MULTIPLE_CHOICE
            );
        }

        throw new IllegalArgumentException(
                "Tipo de pregunta no válido para Kanji"
        );
    }

    private List<String> generateOptions(
            String correctAnswer,
            List<String> possibleAnswers) {

        List<String> wrongAnswers =
                possibleAnswers.stream()
                        .filter(answer ->
                                answer != null
                                        && !answer.equals(correctAnswer))
                        .distinct()
                        .collect(
                                ArrayList::new,
                                ArrayList::add,
                                ArrayList::addAll
                        );

        Collections.shuffle(wrongAnswers);

        List<String> options = new ArrayList<>();
        options.add(correctAnswer);

        wrongAnswers.stream()
                .limit(3)
                .forEach(options::add);

        Collections.shuffle(options);

        return options;
    }
}