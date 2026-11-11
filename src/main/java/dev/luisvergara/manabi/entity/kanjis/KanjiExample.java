package dev.luisvergara.manabi.entity.kanjis;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;

@Entity
@Table(name = "kanji_example")
@Getter @Setter @NoArgsConstructor
public class KanjiExample {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @NotBlank @Size(max = 512) @Column(nullable = false, length = 512)
    private String japanese;
    @NotBlank @Size(max = 512) @Column(nullable = false, length = 512)
    private String reading;
    @NotBlank @Size(max = 4000) @Column(nullable = false, columnDefinition = "text")
    private String meaning;
    @NotNull @PositiveOrZero @Max(9007199254740991L) @Column(name = "display_order", nullable = false)
    private Long displayOrder;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "kanji_id", nullable = false)
    private Kanji kanji;

}
