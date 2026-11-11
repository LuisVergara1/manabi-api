package dev.luisvergara.manabi.entity.vocabulary;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;

@Entity
@Table(name = "vocabulary_category")
@Getter @Setter @NoArgsConstructor
public class VocabularyCategory {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @NotBlank @Pattern(regexp = "[A-Z][A-Z0-9_]{0,39}")
    @Column(nullable = false, unique = true, updatable = false, length = 40)
    private String code;
    @NotBlank @Size(max = 120) @Column(nullable = false, length = 120)
    private String name;
    @Size(max = 16) @Column(length = 16)
    private String symbol;
    @Column(name = "show_in_catalog", nullable = false)
    private boolean showInCatalog;
    @NotNull @PositiveOrZero @Max(9007199254740991L) @Column(name = "display_order", nullable = false)
    private Long displayOrder;
    @Version @Column(nullable = false)
    private Long version;

}
