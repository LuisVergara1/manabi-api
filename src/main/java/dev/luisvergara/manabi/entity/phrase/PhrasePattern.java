package dev.luisvergara.manabi.entity.phrase;
import jakarta.persistence.*;
import lombok.*;
@Embeddable @Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class PhrasePattern {
 @Column(name="pattern_formula",columnDefinition="text") private String formula;
 @Column(name="pattern_explanation",columnDefinition="text") private String explanation;
}
