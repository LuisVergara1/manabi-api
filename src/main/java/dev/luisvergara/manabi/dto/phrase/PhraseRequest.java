package dev.luisvergara.manabi.dto.phrase;
import java.util.List;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import com.fasterxml.jackson.annotation.JsonInclude;
import dev.luisvergara.manabi.enums.phrase.PhraseKind;
public record PhraseRequest(@NotNull PhraseKind kind,
 @NotNull @Min(0) @Max(9007199254740991L) Long displayOrder,
 @NotEmpty List<@NotBlank String> categoryIds,
 @NotBlank String japanese,@NotBlank String kana,@NotBlank String romaji,@NotBlank String meaning,
 @NotBlank String iconType,@NotBlank String introduction,String usageNote,
 @Size(max=3) List<@NotBlank String> keyPoints,@Size(max=3) List<@NotNull @Valid LearningKey> learningKeys,
 @NotNull @Size(max=4) List<@NotNull @Valid Example> examples,
 List<@NotNull @Valid Meaning> meanings,@Valid Pattern pattern,Long version) {
 @JsonInclude(JsonInclude.Include.NON_NULL)
 public record Text(@NotBlank String japanese,@NotBlank String kana,@NotBlank String romaji,@NotBlank String meaning) {}
 @JsonInclude(JsonInclude.Include.NON_NULL)
 public record Example(String id,@NotNull @Min(0) @Max(9007199254740991L) Long displayOrder,
  @NotBlank String japanese,@NotBlank String kana,@NotBlank String romaji,@NotBlank String meaning,@NotBlank String iconType,String situation) {}
 public record Meaning(String id,@NotBlank String label,@NotBlank String explanation,@NotBlank String iconType) {}
 public record Slot(String id,@NotBlank String label,@NotBlank String description) {}
 public record Pattern(@NotBlank String formula,@NotBlank String explanation,@NotNull List<@NotNull @Valid Slot> slots) {}
 @JsonInclude(JsonInclude.Include.NON_NULL)
 public record LearningKey(String id,@NotBlank String iconType,String particleId,String title,String explanation,@Valid Text example) {}
}
