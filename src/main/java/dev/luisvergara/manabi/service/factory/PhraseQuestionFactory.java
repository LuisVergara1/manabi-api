package dev.luisvergara.manabi.service.factory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.springframework.stereotype.Component;

import dev.luisvergara.manabi.dto.quiz.QuizQuestion;
import dev.luisvergara.manabi.entity.phrase.Phrase;
import dev.luisvergara.manabi.enums.quizz.QuestionFormat;
import dev.luisvergara.manabi.enums.quizz.QuestionType;

@Component
public class PhraseQuestionFactory implements QuizQuestionFactory<Phrase> {
    
    @Override
    public QuizQuestion createQuestion(
            QuestionType questionType,
            Phrase correct,
            List<Phrase> availableContent) {

        if (questionType == QuestionType.MEANING) {

            List<String> options = generateOptions(
                    correct.getMeaning(),
                    availableContent.stream()
                            .map(Phrase::getMeaning)
                            .toList()
            );

            return new QuizQuestion(
                    "¿Qué significa \"" + correct.getJapanese() + "\"?",
                    options,
                    correct.getMeaning(),
                    QuestionType.MEANING,
                    QuestionFormat.MULTIPLE_CHOICE
            );
        }

        if (questionType == QuestionType.READING) {

            List<String> options = generateOptions(
                    correct.getRomaji(),
                    availableContent.stream()
                            .map(Phrase::getRomaji)
                            .toList()
            );

            return new QuizQuestion(
                    "¿Cómo se lee \"" + correct.getJapanese() + "\"?",
                    options,
                    correct.getRomaji(),
                    QuestionType.READING,
                    QuestionFormat.MULTIPLE_CHOICE
            );
        }

        if (questionType == QuestionType.CHARACTER) {

            List<String> options = generateOptions(
                    correct.getJapanese(),
                    availableContent.stream()
                            .map(Phrase::getJapanese)
                            .toList()
            );

            return new QuizQuestion(
                    "¿Cómo se escribe \"" + correct.getMeaning() + "\"?",
                    options,
                    correct.getJapanese(),
                    QuestionType.CHARACTER,
                    QuestionFormat.MULTIPLE_CHOICE
            );
        }

        throw new IllegalArgumentException(
                "Tipo de pregunta no válido para Phrase"
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
