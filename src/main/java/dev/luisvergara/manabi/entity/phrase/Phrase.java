package dev.luisvergara.manabi.entity.phrase;

import java.util.HashSet;
import java.util.Set;

import dev.luisvergara.manabi.enums.kanjis.JlptLevel;
import dev.luisvergara.manabi.enums.phrase.PhraseCategory;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "phrase")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Phrase {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String japanese;

    @Column(nullable = false)
    private String romaji;

    @Column(nullable = false)
    private String meaning;

    @Enumerated(EnumType.STRING)
    @Column(name = "jlpt_level", nullable = false)
    private JlptLevel jlptLevel;

    @ElementCollection
    @CollectionTable(
            name = "phrase_categories",
            joinColumns = @JoinColumn(name = "phrase_id")
    )
    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false)
    private Set<PhraseCategory> categories = new HashSet<>();
    
}