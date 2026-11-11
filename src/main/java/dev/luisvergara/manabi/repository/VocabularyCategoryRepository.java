package dev.luisvergara.manabi.repository;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
import dev.luisvergara.manabi.entity.vocabulary.VocabularyCategory;
public interface VocabularyCategoryRepository extends JpaRepository<VocabularyCategory, Long> {
    List<VocabularyCategory> findByCodeIn(Collection<String> codes);
}
