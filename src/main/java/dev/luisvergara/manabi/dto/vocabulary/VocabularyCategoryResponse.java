package dev.luisvergara.manabi.dto.vocabulary;
import dev.luisvergara.manabi.entity.vocabulary.VocabularyCategory;
public record VocabularyCategoryResponse(Long id,String code,String name,String symbol,Long displayOrder,boolean showInCatalog,Long version){
 public static VocabularyCategoryResponse from(VocabularyCategory c){return new VocabularyCategoryResponse(c.getId(),c.getCode(),c.getName(),c.getSymbol(),c.getDisplayOrder(),c.isShowInCatalog(),c.getVersion());}
}
