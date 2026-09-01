package dev.luisvergara.manabi.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import dev.luisvergara.manabi.entity.phrase.Phrase;
import dev.luisvergara.manabi.enums.kanjis.JlptLevel;
import dev.luisvergara.manabi.enums.phrase.PhraseCategory;

public interface PhraseRepository  extends JpaRepository<Phrase,Long>{

    List<Phrase> findByJlptLevelIn(
            List<JlptLevel> levels
    );

    List<Phrase> findDistinctByCategoriesIn(
            List<PhraseCategory> categories
    );

    List<Phrase> findDistinctByJlptLevelInAndCategoriesIn(
            List<JlptLevel> levels,
            List<PhraseCategory> categories
    );
    
}
