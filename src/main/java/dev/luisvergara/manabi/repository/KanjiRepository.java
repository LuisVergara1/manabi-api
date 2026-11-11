package dev.luisvergara.manabi.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import dev.luisvergara.manabi.entity.kanjis.Kanji;
import dev.luisvergara.manabi.enums.kanjis.JlptLevel;

public interface KanjiRepository extends JpaRepository<Kanji,Long> {

     Optional<Kanji> findByCharacter(String character);

    List<Kanji> findByJlptLevelIn(List<JlptLevel> levels);
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select k from Kanji k where k.id = :id")
    Optional<Kanji> findForUpdate(@org.springframework.data.repository.query.Param("id") Long id);
}
