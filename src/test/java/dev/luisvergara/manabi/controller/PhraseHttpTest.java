package dev.luisvergara.manabi.controller;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.http.MediaType;
import org.springframework.dao.DataIntegrityViolationException;
import dev.luisvergara.manabi.dto.phrase.*;
import dev.luisvergara.manabi.enums.phrase.PhraseKind;
import dev.luisvergara.manabi.service.*;

/** Standalone MVC, no Spring application or datasource. */
class PhraseHttpTest {
    @Test void categoryReferencesAndExplicitTypeReachService() throws Exception {
        var service = mock(PhraseService.class);
        when(service.find(PhraseKind.PATTERN, List.of("comunicacion-basica"))).thenReturn(List.of());
        var mvc = MockMvcBuilders.standaloneSetup(new PhraseController(service)).build();
        mvc.perform(get("/api/phrases").param("kind", "PATTERN").param("categoryIds", "comunicacion-basica"))
            .andExpect(status().isOk()).andExpect(content().json("[]"));
        verify(service).find(PhraseKind.PATTERN, List.of("comunicacion-basica"));
        mvc.perform(get("/api/phrases").param("kind", "OTHER")).andExpect(status().isBadRequest());
    }
    @Test void requestIsDtoAndResponsesDoNotExposeJpaRelations() throws Exception {
        var service = mock(PhraseService.class);
        var response = new PhraseResponse(1L, "phr-stable", 0L, PhraseKind.FIXED, 0L,
            List.of("comunicacion-basica"), "__JP__", "__KANA__", "__ROMAJI__", "__ES__",
            "conversation", "__INTRO__", null, List.of(), List.of(), List.of(), List.of(), null);
        when(service.save(any())).thenReturn(response);
        var mvc = MockMvcBuilders.standaloneSetup(new PhraseController(service)).build();
        mvc.perform(post("/api/phrases").contentType(MediaType.APPLICATION_JSON).content("""
            {"kind":"FIXED","displayOrder":0,"categoryIds":["comunicacion-basica"],
             "japanese":"__JP__","kana":"__KANA__","romaji":"__ROMAJI__","meaning":"__ES__",
             "iconType":"conversation","introduction":"__INTRO__","examples":[]}
            """))
            .andExpect(status().isOk()).andExpect(jsonPath("$.publicId").value("phr-stable"))
            .andExpect(jsonPath("$.categories").doesNotExist()).andExpect(jsonPath("$.pattern").doesNotExist());
        verify(service).save(argThat(r -> r.kind() == PhraseKind.FIXED && r.examples().isEmpty()));
    }
    @Test void missingStaleAndIntegrityErrorsRemainControlled() throws Exception {
        var service = mock(PhraseService.class);
        var mvc = MockMvcBuilders.standaloneSetup(new PhraseController(service))
            .setControllerAdvice(new ModelErrorHandler()).build();
        when(service.findById(99L)).thenThrow(ModelValidation.missing());
        mvc.perform(get("/api/phrases/99")).andExpect(status().isNotFound());
        doThrow(new DataIntegrityViolationException("private SQL detail")).when(service).delete(1L);
        mvc.perform(delete("/api/phrases/1")).andExpect(status().isConflict())
            .andExpect(jsonPath("$.detail").value("La operacion incumple una restriccion del modelo"));
    }
    @Test void categoriesAndParticlesUseTheirOwnDtoEndpoints() throws Exception {
        var service = mock(PhraseReferenceService.class);
        when(service.categories()).thenReturn(List.of(new PhraseCategoryResponse(1L,
            "comunicacion-basica", "__NAME__", "__DESCRIPTION__", 0L, true, 0L)));
        when(service.particles()).thenReturn(List.of());
        var mvc = MockMvcBuilders.standaloneSetup(new PhraseReferenceController(service))
            .setControllerAdvice(new ModelErrorHandler()).build();
        mvc.perform(get("/api/phrase-categories")).andExpect(status().isOk())
            .andExpect(jsonPath("$[0].publicId").value("comunicacion-basica"));
        mvc.perform(get("/api/particles")).andExpect(status().isOk()).andExpect(content().json("[]"));
        doThrow(new DataIntegrityViolationException("referenced")).when(service).deleteParticle(7L);
        mvc.perform(delete("/api/particles/7")).andExpect(status().isConflict());
    }
}
