package dev.luisvergara.manabi.entity.kanjis;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;
import java.util.*;
import jakarta.validation.Valid;
import dev.luisvergara.manabi.enums.kanjis.JlptLevel;
@Entity
@Table(name = "kanji")
@Getter @Setter @NoArgsConstructor
public class Kanji {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name="public_id", nullable=false, unique=true, updatable=false, length=128)
    private String publicId;
    @NotBlank @Size(max = 16) @Column(name = "kanji_character", nullable = false, length = 16)
    private String character;
    @NotBlank @Size(max = 4000) @Column(nullable = false, columnDefinition = "text")
    private String meaning;
    @NotNull @Positive @Column(name = "stroke_count", nullable = false)
    private Integer strokeCount;
    @NotNull @Enumerated(EnumType.STRING) @Column(name = "jlpt_level", nullable = false, length = 2)
    private JlptLevel jlptLevel;
    @Size(max = 16) @Column(name = "radical_character", length = 16)
    private String radicalCharacter;
    @Size(max = 120) @Column(name = "radical_name", length = 120)
    private String radicalName;
    @NotNull @PositiveOrZero @Max(9007199254740991L) @Column(name = "display_order", nullable = false)
    private Long displayOrder;
    @Version @Column(nullable = false)
    private Long version;
    @NotNull @OneToMany(mappedBy = "kanji", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position ASC")
    private List<@NotNull @Valid KanjiReading> readings = new ArrayList<>();
    @NotNull @OneToMany(mappedBy = "kanji", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("displayOrder ASC, id ASC")
    private List<@NotNull @Valid KanjiExample> examples = new ArrayList<>();
    public void addReading(KanjiReading reading) { reading.setPosition(readings.size()); readings.add(reading); reading.setKanji(this); }
    public void addExample(KanjiExample example) { examples.add(example); example.setKanji(this); }

}
