package dev.luisvergara.manabi.dto.vocabulary;
import java.util.Set;
import java.util.stream.Collectors;
import dev.luisvergara.manabi.entity.vocabulary.Vocabulary;
public record VocabularyResponse(Long id, String publicId, String japanese, String kana, String romaji,
    String meaning, Long displayOrder, Set<String> categories, Long version) {
    public static VocabularyResponse from(Vocabulary v) {
        return new VocabularyResponse(v.getId(), v.getPublicId(), v.getJapanese(), v.getKana(), v.getRomaji(),
            v.getMeaning(), v.getDisplayOrder(), v.getCategories().stream().map(c -> c.getCode())
                .collect(Collectors.toSet()), v.getVersion());
    }
}
