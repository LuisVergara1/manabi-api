package dev.luisvergara.manabi.service.factory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.springframework.stereotype.Component;

import dev.luisvergara.manabi.dto.quiz.QuizQuestion;
import dev.luisvergara.manabi.entity.kana.Kana;
import dev.luisvergara.manabi.enums.quizz.QuestionFormat;
import dev.luisvergara.manabi.enums.quizz.QuestionType;

@Component
public class KanaQuestionFactory 
        implements QuizQuestionFactory<Kana> {
    

           @Override
    public QuizQuestion createQuestion(
            QuestionType questionType,
            Kana correct,
            List<Kana> availableContent) {

        if (questionType == QuestionType.READING) {

            List<String> options = generateOptions(
                    correct.getRomaji(),
                    availableContent.stream()
                            .map(Kana::getRomaji)
                            .toList()
            );

            return new QuizQuestion(
                    "¿Cómo se lee " + correct.getCharacter() + "?",
                    options,
                    correct.getRomaji(),
                    QuestionType.READING,
                    QuestionFormat.MULTIPLE_CHOICE
            );
        }

        if (questionType == QuestionType.CHARACTER) {

            List<String> options = generateOptions(
                    correct.getCharacter(),
                    availableContent.stream()
                            .map(Kana::getCharacter)
                            .toList()
            );

            return new QuizQuestion(
                    "¿Cómo se escribe \"" + correct.getRomaji() + "\"?",
                    options,
                    correct.getCharacter(),
                    QuestionType.CHARACTER,
                    QuestionFormat.MULTIPLE_CHOICE
            );
        }

        throw new IllegalArgumentException(
                "Tipo de pregunta no válido para Kana"
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