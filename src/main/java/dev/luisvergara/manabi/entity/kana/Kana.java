package dev.luisvergara.manabi.entity.kana;

import dev.luisvergara.manabi.enums.kana.KanaGroup;
import dev.luisvergara.manabi.enums.kana.KanaType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "kana")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Kana {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "kana_character",nullable = false)
    private String character;
    @Column(nullable = false)
    private String romaji;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private KanaType type;
    @Enumerated(EnumType.STRING)
    @Column(name = "group_type",nullable = false)
    private KanaGroup group;
}

