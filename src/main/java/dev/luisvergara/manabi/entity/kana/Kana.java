package dev.luisvergara.manabi.entity.kana;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;
import dev.luisvergara.manabi.enums.kana.*;
@Entity
@Table(name = "kana")
@Getter @Setter @NoArgsConstructor
public class Kana {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name="public_id", nullable=false, unique=true, updatable=false, length=128)
    private String publicId;
    @NotBlank @Size(max = 32) @Column(name = "kana_character", nullable = false, length = 32)
    private String character;
    @NotBlank @Size(max = 64) @Column(nullable = false, length = 64)
    private String romaji;
    @NotNull @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16)
    private KanaType type;
    @NotNull @Enumerated(EnumType.STRING) @Column(name = "group_type", nullable = false, length = 16)
    private KanaGroup group;
    @NotNull @PositiveOrZero @Max(9007199254740991L) @Column(name = "display_order", nullable = false)
    private Long displayOrder;
    @Version @Column(nullable = false)
    private Long version;
    @AssertTrue(message = "EXTENDED solo es valido para KATAKANA")
    @com.fasterxml.jackson.annotation.JsonIgnore
    public boolean isExtendedGroupValid() {
        return group != KanaGroup.EXTENDED || type == KanaType.KATAKANA;
    }

}
