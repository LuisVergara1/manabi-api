package dev.luisvergara.manabi.repository;
import java.util.*;
import dev.luisvergara.manabi.entity.phrase.PhraseCategory;
import org.springframework.data.jpa.repository.JpaRepository;
public interface PhraseCategoryRepository extends JpaRepository<PhraseCategory,Long> {
 List<PhraseCategory> findByPublicIdIn(Collection<String> ids);
}
