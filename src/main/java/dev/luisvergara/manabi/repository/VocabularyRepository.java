package dev.luisvergara.manabi.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import dev.luisvergara.manabi.entity.vocabulary.Vocabulary;
import dev.luisvergara.manabi.enums.vocabulary.VocabularyCategory;

public interface VocabularyRepository extends JpaRepository<Vocabulary,Long> {
    
    List<Vocabulary> findDistinctByCategoriesIn(
            List<VocabularyCategory> categories
    );
}
