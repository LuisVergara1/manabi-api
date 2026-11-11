package dev.luisvergara.manabi.service;
import java.util.*;
import java.text.Normalizer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import dev.luisvergara.manabi.entity.kana.Kana;
import dev.luisvergara.manabi.enums.kana.*;
import dev.luisvergara.manabi.repository.KanaRepository;
import lombok.RequiredArgsConstructor;
@Service @RequiredArgsConstructor @Transactional(readOnly=true)
public class KanaService {
    private final KanaRepository kanaRepository;
    private final ModelValidation validation;
    private List<Kana> ordered(List<Kana> values) {
        return values.stream().sorted(Comparator.comparing(Kana::getType)
            .thenComparing(Kana::getDisplayOrder).thenComparing(Kana::getPublicId)).toList();
    }
    public Kana findById(Long id) { return kanaRepository.findById(id).orElseThrow(ModelValidation::missing); }
    public List<Kana> findAll() { return ordered(kanaRepository.findAll()); }
    public List<Kana> findByType(KanaType type) { return ordered(kanaRepository.findByType(type)); }
    public List<Kana> findByGroup(KanaGroup group) { return ordered(kanaRepository.findByGroup(group)); }
    public List<Kana> find(KanaType type, KanaGroup group) {
        if (type != null && group != null) return ordered(kanaRepository.findByTypeAndGroup(type, group));
        if (type != null) return findByType(type);
        if (group != null) return findByGroup(group);
        return findAll();
    }
    private void validate(Kana kana) {
        validation.valid(kana);
        if (!Normalizer.isNormalized(kana.getCharacter(), Normalizer.Form.NFC))
            throw ModelValidation.bad("La escritura Kana requiere NFC revisado; no se transforma automaticamente");
    }
    @Transactional public Kana save(Kana kana) {
        validate(kana); validation.newId(kana.getId());
        if (kana.getVersion() != null) throw ModelValidation.bad("Un alta no tiene version"); kana.setVersion(null); kana.setPublicId(ContentPublicIds.create("kana", kana.getCharacter()));
        return kanaRepository.saveAndFlush(kana);
    }
    @Transactional public Kana update(Long id, Kana kana) {
        validate(kana);
        Kana existing = kanaRepository.findById(id).orElseThrow(ModelValidation::missing);
        validation.version(kana.getVersion(), existing.getVersion());
        existing.setCharacter(kana.getCharacter()); existing.setRomaji(kana.getRomaji());
        existing.setType(kana.getType()); existing.setGroup(kana.getGroup());
        existing.setDisplayOrder(kana.getDisplayOrder());
        return kanaRepository.saveAndFlush(existing);
    }
    @Transactional public void delete(Long id) { kanaRepository.delete(findById(id)); }
}
