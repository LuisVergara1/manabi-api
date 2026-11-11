package dev.luisvergara.manabi.entity.kanjis;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;
import dev.luisvergara.manabi.enums.kanjis.ReadingType;
@Entity
@Table(name = "kanji_reading")
@Getter @Setter @NoArgsConstructor
public class KanjiReading {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Min(0) @Column(name="editorial_position",nullable=false)
    private int position;
    @NotBlank @Size(max = 128) @Column(nullable = false, length = 128)
    private String reading;
    @NotNull @Enumerated(EnumType.STRING) @Column(name = "reading_type", nullable = false, length = 16)
    private ReadingType readingType;
    @Size(max = 256) @Column(length = 256)
    private String romaji;
    @Column(name = "is_primary", nullable = false)
    private boolean primary;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "kanji_id", nullable = false)
    private Kanji kanji;

}
