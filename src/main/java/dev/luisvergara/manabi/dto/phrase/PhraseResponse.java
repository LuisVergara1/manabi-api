package dev.luisvergara.manabi.dto.phrase;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonInclude;
import dev.luisvergara.manabi.enums.phrase.PhraseKind;
// Administrative DTO, not a public distribution body: id is the DB PK, publicId is stable.
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PhraseResponse(Long id,String publicId,Long version,PhraseKind kind,Long displayOrder,List<String> categoryIds,
 String japanese,String kana,String romaji,String meaning,String iconType,String introduction,String usageNote,
 List<String> keyPoints,List<PhraseRequest.LearningKey> learningKeys,List<PhraseRequest.Example> examples,
 List<PhraseRequest.Meaning> meanings,PhraseRequest.Pattern pattern) {}
