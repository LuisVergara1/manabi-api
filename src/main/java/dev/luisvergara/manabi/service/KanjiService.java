package dev.luisvergara.manabi.service;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import dev.luisvergara.manabi.dto.kanjis.*;
import dev.luisvergara.manabi.entity.kanjis.*;
import dev.luisvergara.manabi.enums.kanjis.JlptLevel;
import dev.luisvergara.manabi.repository.KanjiRepository;
import dev.luisvergara.manabi.service.mapper.KanjiMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
@Service @RequiredArgsConstructor @Transactional(readOnly = true)
public class KanjiService {
    private final KanjiRepository kanjiRepository;
    private final KanjiMapper kanjiMapper;
    private final ModelValidation validation;
    private final EntityManager entityManager;
    public List<Kanji> find(List<JlptLevel> levels) {
        var values = levels == null || levels.isEmpty()
            ? kanjiRepository.findAll() : kanjiRepository.findByJlptLevelIn(levels);
        return values.stream().sorted(Comparator
            .comparingInt((Kanji k) -> -Integer.parseInt(k.getJlptLevel().name().substring(1)))
            .thenComparing(Kanji::getDisplayOrder).thenComparing(Kanji::getPublicId)).toList();
    }
    public Kanji findById(Long id) { return kanjiRepository.findById(id).orElseThrow(ModelValidation::missing); }
    public List<KanjiResponse> responses(List<JlptLevel> levels) { return find(levels).stream().map(KanjiResponse::from).toList(); }
    public KanjiResponse response(Long id) { return KanjiResponse.from(findById(id)); }
    public void validateAggregate(Kanji k) {
        validation.valid(k);
        if (k.getReadings().stream().anyMatch(Objects::isNull) ||
            k.getReadings().stream().filter(KanjiReading::isPrimary).count() != 1)
            throw ModelValidation.bad("Se requiere exactamente una lectura principal explicita");
        Set<List<Object>> keys = new HashSet<>();
        for (KanjiReading r : k.getReadings()) {
            if (!keys.add(List.of(r.getReadingType(), r.getReading())))
                throw ModelValidation.bad("Lectura duplicada por tipo");
            if (r.isPrimary() && (r.getRomaji() == null || r.getRomaji().isBlank()))
                throw ModelValidation.bad("La lectura principal requiere romaji editorial");
        }
        if (k.getRadicalName() != null && (k.getRadicalName().isBlank() ||
            k.getRadicalCharacter() == null || k.getRadicalCharacter().isBlank()))
            throw ModelValidation.bad("El nombre de radical requiere su caracter");
        if (k.getRadicalCharacter() != null && k.getRadicalCharacter().isBlank())
            throw ModelValidation.bad("El radical opcional no puede ser vacio");
    }
    @Transactional
    public KanjiResponse save(KanjiRequest request) {
        validation.valid(request);
        if (request.version() != null) throw ModelValidation.bad("Un alta no tiene version");
        Kanji k = kanjiMapper.toEntity(request); validateAggregate(k); k.setPublicId(ContentPublicIds.create("kanji", k.getCharacter()));
        return KanjiResponse.from(kanjiRepository.saveAndFlush(k));
    }
    @Transactional public KanjiResponse updateResponse(Long id, KanjiRequest r) {
        validation.valid(r); Kanji incoming = kanjiMapper.toEntity(r); incoming.setVersion(r.version());
        return KanjiResponse.from(update(id, incoming));
    }
    // Replace owned children in two flushes
    // so the immediate partial unique index never observes two primaries.
    @Transactional
    public Kanji update(Long id, Kanji incoming) {
        validateAggregate(incoming);
        Kanji k = kanjiRepository.findForUpdate(id).orElseThrow(ModelValidation::missing);
        validation.version(incoming.getVersion(), k.getVersion());
        k.getReadings().clear(); k.getExamples().clear(); kanjiRepository.flush();
        k.setCharacter(incoming.getCharacter()); k.setMeaning(incoming.getMeaning());
        k.setStrokeCount(incoming.getStrokeCount()); k.setJlptLevel(incoming.getJlptLevel());
        k.setDisplayOrder(incoming.getDisplayOrder()); k.setRadicalCharacter(incoming.getRadicalCharacter());
        k.setRadicalName(incoming.getRadicalName());
        for (KanjiReading r : incoming.getReadings()) { r.setId(null); k.addReading(r); }
        for (KanjiExample e : incoming.getExamples()) { e.setId(null); k.addExample(e); }
        entityManager.lock(k, LockModeType.PESSIMISTIC_FORCE_INCREMENT);
        return kanjiRepository.saveAndFlush(k);
    }
    @Transactional public void delete(Long id) { kanjiRepository.delete(findById(id)); }
}
