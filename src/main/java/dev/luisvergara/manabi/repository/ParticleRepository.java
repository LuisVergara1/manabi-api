package dev.luisvergara.manabi.repository;
import java.util.Optional;
import dev.luisvergara.manabi.entity.phrase.Particle;
import org.springframework.data.jpa.repository.JpaRepository;
public interface ParticleRepository extends JpaRepository<Particle,Long> {
 Optional<Particle> findByPublicId(String id);
}
