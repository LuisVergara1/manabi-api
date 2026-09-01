package dev.luisvergara.manabi.entity.kanjis;

import java.util.ArrayList;
import java.util.List;

import dev.luisvergara.manabi.enums.kanjis.JlptLevel;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "kanji")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Kanji {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY )
    private Long id;

    @Column(name = "kanji_character",nullable = false)
    private String character;

    @Column(nullable = false)
    private String meaning;

    @Column(name = "stroke_count", nullable= false)
    private Integer strokeCount;

    @Enumerated(EnumType.STRING)
    @Column(name = "jlpt_level",nullable = false)
    private JlptLevel jlptLevel;

    @OneToMany(
        mappedBy = "kanji",
        cascade = CascadeType.ALL,
        orphanRemoval = true
    )
       private List<KanjiReading> readings = new ArrayList<>();

    @OneToMany(
            mappedBy = "kanji",
            cascade = CascadeType.ALL,
            orphanRemoval = true
    )
    private List<KanjiExample> examples = new ArrayList<>();
}
