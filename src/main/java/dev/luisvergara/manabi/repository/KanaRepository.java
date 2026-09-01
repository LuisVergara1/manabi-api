package dev.luisvergara.manabi.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import dev.luisvergara.manabi.entity.kana.Kana;
import dev.luisvergara.manabi.enums.kana.KanaGroup;
import dev.luisvergara.manabi.enums.kana.KanaType;

public interface KanaRepository extends JpaRepository<Kana,Long>{

    List<Kana> findByType(KanaType type);    
    List<Kana> findByGroup(KanaGroup group);
    List<Kana> findByTypeAndGroup(KanaType type , KanaGroup kanaGroup);

    
}
