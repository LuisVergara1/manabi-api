package dev.luisvergara.manabi.dto.phrase;
import jakarta.validation.constraints.*;
public record ParticleRequest(@NotBlank String japanese,@NotBlank String title,@NotBlank String explanation,Long version) {}
