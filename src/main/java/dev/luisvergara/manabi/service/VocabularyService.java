package dev.luisvergara.manabi.service;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Sort;
import dev.luisvergara.manabi.dto.vocabulary.*;
import dev.luisvergara.manabi.entity.vocabulary.*;
import dev.luisvergara.manabi.repository.*;
import lombok.RequiredArgsConstructor;
@Service @RequiredArgsConstructor @Transactional(readOnly=true)
public class VocabularyService {
    private final VocabularyRepository vocabularyRepository;
    private final VocabularyCategoryRepository categoryRepository;
    private final ModelValidation validation;
    private static final Sort ORDER = Sort.by("displayOrder", "publicId");
    public List<VocabularyCategoryResponse> categories(){
        return categoryRepository.findAll(Sort.by("displayOrder", "code")).stream().map(VocabularyCategoryResponse::from).toList();
    }
    @Transactional public VocabularyCategoryResponse updateCategory(Long id,VocabularyCategoryRequest request){
        validation.valid(request);var c=categoryRepository.findById(id).orElseThrow(ModelValidation::missing);
        validation.version(request.version(),c.getVersion());
        if(request.showInCatalog()&&(request.symbol()==null||request.symbol().isBlank()))throw ModelValidation.bad("Una categoria visible requiere simbolo");
        c.setName(request.name());c.setSymbol(request.symbol());c.setDisplayOrder(request.displayOrder());c.setShowInCatalog(request.showInCatalog());
        validation.valid(c);return VocabularyCategoryResponse.from(categoryRepository.saveAndFlush(c));
    }
    private Set<VocabularyCategory> resolve(Collection<String> codes) {
        var found = categoryRepository.findByCodeIn(codes);
        if (found.size() != new HashSet<>(codes).size()) throw ModelValidation.bad("Categoria desconocida");
        return new HashSet<>(found);
    }
    public List<Vocabulary> find(List<String> categories) {
        if (categories == null || categories.isEmpty()) return vocabularyRepository.findAll(ORDER);
        resolve(categories); // Includes hidden historical categories; OR, never AND.
        return vocabularyRepository.findDistinctByCategories_CodeIn(categories, ORDER);
    }
    public Vocabulary findById(Long id) {
        return vocabularyRepository.findById(id).orElseThrow(ModelValidation::missing);
    }
    public List<VocabularyResponse> responses(List<String> categories) {
        return find(categories).stream().map(VocabularyResponse::from).toList();
    }
    public VocabularyResponse response(Long id) { return VocabularyResponse.from(findById(id)); }
    private void apply(Vocabulary v, VocabularyRequest request) {
        validation.valid(request);
        v.setJapanese(request.japanese()); v.setKana(request.kana()); v.setRomaji(request.romaji());
        v.setMeaning(request.meaning()); v.setDisplayOrder(request.displayOrder());
        v.setCategories(resolve(request.categories())); validation.valid(v);
    }
    @Transactional public VocabularyResponse save(VocabularyRequest request) {
        if (request.version() != null) throw ModelValidation.bad("Un alta no tiene version");
        Vocabulary v = new Vocabulary(); apply(v, request); v.setPublicId(ContentPublicIds.create("vocab", v.getJapanese()));
        return VocabularyResponse.from(vocabularyRepository.saveAndFlush(v));
    }
    @Transactional public VocabularyResponse update(Long id, VocabularyRequest request) {
        validation.valid(request);
        Vocabulary v = findById(id); validation.version(request.version(), v.getVersion()); apply(v, request);
        return VocabularyResponse.from(vocabularyRepository.saveAndFlush(v));
    }
    @Transactional public void delete(Long id) { vocabularyRepository.delete(findById(id)); }
}
