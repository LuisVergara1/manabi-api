package dev.luisvergara.manabi.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import dev.luisvergara.manabi.dto.quiz.QuizQuestion;
import dev.luisvergara.manabi.enums.kana.KanaGroup;
import dev.luisvergara.manabi.enums.kana.KanaType;
import dev.luisvergara.manabi.enums.quizz.QuestionType;
import dev.luisvergara.manabi.enums.quizz.QuizContentType;
import dev.luisvergara.manabi.service.QuizService;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/quiz")
@RequiredArgsConstructor
public class QuizController {

    private final QuizService quizService;

    @GetMapping
    public List<QuizQuestion> generateQuiz(
            @RequestParam QuizContentType contentType,
            @RequestParam List<QuestionType> questionTypes,
            @RequestParam(defaultValue = "10") int amount,
            @RequestParam(required = false) KanaType type,
            @RequestParam(required = false) KanaGroup group) {

        return quizService.generateQuestions(
                contentType,
                questionTypes,
                amount,
                type,
                group
        );}
    }
