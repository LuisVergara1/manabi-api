package dev.luisvergara.manabi.dto.kana;
import dev.luisvergara.manabi.entity.kana.Kana;
import dev.luisvergara.manabi.enums.kana.*;
public record KanaResponse(Long id, String publicId, String character, String romaji,
    KanaType type, KanaGroup group, Long displayOrder, Long version) {
    public static KanaResponse from(Kana k) {
        return new KanaResponse(k.getId(), k.getPublicId(), k.getCharacter(), k.getRomaji(),
            k.getType(), k.getGroup(), k.getDisplayOrder(), k.getVersion());
    }
}
