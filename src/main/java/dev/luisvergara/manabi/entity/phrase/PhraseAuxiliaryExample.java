package dev.luisvergara.manabi.entity.phrase;
import jakarta.persistence.*;
import lombok.*;
@Embeddable @Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class PhraseAuxiliaryExample {
 @Column(name="example_japanese",columnDefinition="text") private String japanese;
 @Column(name="example_kana",columnDefinition="text") private String kana;
 @Column(name="example_romaji",columnDefinition="text") private String romaji;
 @Column(name="example_meaning",columnDefinition="text") private String meaning;
}
