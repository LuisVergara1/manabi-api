package dev.luisvergara.manabi.entity.vocabulary;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;
import java.util.*;
@Entity
@Table(name = "vocabulary")
@Getter @Setter @NoArgsConstructor
public class Vocabulary {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name="public_id", nullable=false, unique=true, updatable=false, length=128)
    private String publicId;
    @NotBlank @Size(max = 256) @Column(nullable = false, length = 256)
    private String japanese;
    @NotBlank @Size(max = 256) @Column(nullable = false, length = 256)
    private String kana;
    @NotBlank @Size(max = 512) @Column(nullable = false, length = 512)
    private String romaji;
    @NotBlank @Size(max = 4000) @Column(nullable = false, columnDefinition = "text")
    private String meaning;
    @NotNull @PositiveOrZero @Max(9007199254740991L) @Column(name = "display_order", nullable = false)
    private Long displayOrder;
    @Version @Column(nullable = false)
    private Long version;
    @NotEmpty @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "vocabulary_category_membership",
        joinColumns = @JoinColumn(name = "vocabulary_id"),
        inverseJoinColumns = @JoinColumn(name = "category_id"))
    private Set<VocabularyCategory> categories = new HashSet<>();

}
