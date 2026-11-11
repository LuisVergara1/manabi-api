package dev.luisvergara.manabi.repository;
import dev.luisvergara.manabi.entity.phrase.Phrase;
import org.springframework.data.jpa.repository.*;
public interface PhraseRepository extends JpaRepository<Phrase,Long>,JpaSpecificationExecutor<Phrase> {
 boolean existsByLearningKeys_Particle_Id(Long particleId);
}
