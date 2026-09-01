package dev.luisvergara.manabi.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import dev.luisvergara.manabi.entity.kanjis.Kanji;
import dev.luisvergara.manabi.enums.kanjis.JlptLevel;

public interface KanjiRepository extends JpaRepository<Kanji,Long> {

     Optional<Kanji> findByCharacter(String character);

    List<Kanji> findByJlptLevelIn(List<JlptLevel> levels);
    
}
