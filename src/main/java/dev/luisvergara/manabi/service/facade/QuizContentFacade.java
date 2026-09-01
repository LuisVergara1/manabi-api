package dev.luisvergara.manabi.service.facade;

import java.util.List;

import org.springframework.stereotype.Component;

import dev.luisvergara.manabi.entity.kana.Kana;
import dev.luisvergara.manabi.entity.kanjis.Kanji;
import dev.luisvergara.manabi.entity.phrase.Phrase;
import dev.luisvergara.manabi.entity.vocabulary.Vocabulary;
import dev.luisvergara.manabi.enums.kana.KanaGroup;
import dev.luisvergara.manabi.enums.kana.KanaType;
import dev.luisvergara.manabi.service.KanaService;
import dev.luisvergara.manabi.service.KanjiService;
import dev.luisvergara.manabi.service.PhraseService;
import dev.luisvergara.manabi.service.VocabularyService;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class QuizContentFacade { 
    private final KanaService kanaService;
    private final KanjiService kanjiService;
    private final VocabularyService vocabularyService;
    private final PhraseService phraseService;
    public List<Kana> getKana(
            KanaType kanaType,
            KanaGroup kanaGroup) {

        return kanaService.find(
                kanaType,
                kanaGroup
        );
    }
    public List<Kanji> getKanji() {
        return kanjiService.find(null);
    }
    public List<Vocabulary> getVocabulary() {
        return vocabularyService.find(null);
    }
    public List<Phrase> getPhrases() {
        return phraseService.find(null, null);
    }
}
