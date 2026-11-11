package dev.luisvergara.manabi.dto.kanjis;
import java.util.List;
import dev.luisvergara.manabi.enums.kanjis.JlptLevel;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
public record KanjiRequest(@NotBlank @Size(max=16) String character,
    @NotBlank @Size(max=4000) String meaning, @NotNull @Positive Integer strokeCount,
    @NotNull JlptLevel jlptLevel, @NotNull @Min(0) @Max(9007199254740991L) Long displayOrder,
    @Size(max=16) String radicalCharacter, @Size(max=120) String radicalName,
    @NotEmpty List<@NotNull @Valid KanjiReadingRequest> readings,
    @NotNull List<@NotNull @Valid KanjiExampleRequest> examples, Long version) {}
