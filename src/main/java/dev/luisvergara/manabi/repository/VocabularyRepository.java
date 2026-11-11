package dev.luisvergara.manabi.repository;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Sort;
import dev.luisvergara.manabi.entity.vocabulary.Vocabulary;
public interface VocabularyRepository extends JpaRepository<Vocabulary, Long> {
    List<Vocabulary> findDistinctByCategories_CodeIn(List<String> codes, Sort sort);
}
