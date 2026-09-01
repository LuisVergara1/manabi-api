package dev.luisvergara.manabi.service;

import dev.luisvergara.manabi.repository.KanaRepository;
import java.util.List;

import org.springframework.stereotype.Service;

import dev.luisvergara.manabi.entity.kana.Kana;
import dev.luisvergara.manabi.enums.kana.KanaGroup;
import dev.luisvergara.manabi.enums.kana.KanaType;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class KanaService {
    
    private final KanaRepository kanaRepository;

    public List<Kana> findAll(){
        return kanaRepository.findAll();
    }

    public List<Kana> findByType(KanaType type)
    {
        return kanaRepository.findByType(type);
    }

    public List<Kana> findByGroup(KanaGroup group){
        return kanaRepository.findByGroup(group);
    }
    public List<Kana> find(KanaType type, KanaGroup group) {

        if (type != null && group != null) {
            return kanaRepository.findByTypeAndGroup(type, group);
        }

        if (type != null) {
            return kanaRepository.findByType(type);
        }

        if (group != null) {
            return kanaRepository.findByGroup(group);
        }

        return kanaRepository.findAll();
    }

    public Kana save(Kana kana) {
        return kanaRepository.save(kana);
    }

    public Kana update(Long id, Kana kana) {
        Kana existingKana = kanaRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Kana no encontrado"));

        existingKana.setCharacter(kana.getCharacter());
        existingKana.setRomaji(kana.getRomaji());
        existingKana.setType(kana.getType());
        existingKana.setGroup(kana.getGroup());

        return kanaRepository.save(existingKana);
    }

    public void delete(Long id) {
        kanaRepository.deleteById(id);
    }
}
