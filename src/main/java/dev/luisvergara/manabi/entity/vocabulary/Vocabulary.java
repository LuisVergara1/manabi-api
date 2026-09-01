package dev.luisvergara.manabi.entity.vocabulary;

import java.util.HashSet;
import java.util.Set;

import dev.luisvergara.manabi.enums.vocabulary.VocabularyCategory;
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
@Table(name = "vocabulary")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Vocabulary {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String japanese;

    @Column(nullable = false)
    private String reading;

    @Column(nullable = false)
    private String meaning;

    @ElementCollection
    @CollectionTable(
            name = "vocabulary_categories",
            joinColumns = @JoinColumn(name = "vocabulary_id")
    )
    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false)
    private Set<VocabularyCategory> categories = new HashSet<>();
}
