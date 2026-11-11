package dev.luisvergara.manabi.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import jakarta.validation.*;
import org.junit.jupiter.api.*;
import org.springframework.web.server.ResponseStatusException;
import dev.luisvergara.manabi.dto.phrase.*;
import dev.luisvergara.manabi.entity.phrase.*;
import dev.luisvergara.manabi.repository.*;
import dev.luisvergara.manabi.service.phrase.*;

class PhraseReferenceTest {
    static ValidatorFactory factory;
    ModelValidation versions = new ModelValidation(factory.getValidator());
    PhraseCategoryRepository categories = mock(PhraseCategoryRepository.class);
    ParticleRepository particles = mock(ParticleRepository.class);
    PhraseRepository phrases = mock(PhraseRepository.class);
    PhraseReferenceService service = new PhraseReferenceService(categories, particles, new PhraseValidation(versions), versions, phrases);
    @BeforeAll static void setup() { factory = Validation.buildDefaultValidatorFactory(); }
    @AfterAll static void close() { factory.close(); }

    @Test void categoryEditCannotChangeStableIdAndRejectsStaleVersion() {
        var c = new PhraseCategory(); c.setId(1L); c.setPublicId("comunicacion-basica"); c.setVersion(2L);
        when(categories.findById(1L)).thenReturn(Optional.of(c));
        when(categories.saveAndFlush(c)).thenReturn(c);
        assertThrows(ResponseStatusException.class, () -> service.updateCategory(1L,
            new PhraseCategoryRequest("__NAME__", "__DESCRIPTION__", 0L, true, 1L)));
        assertNull(c.getName());
        var response = service.updateCategory(1L, new PhraseCategoryRequest("__NAME__", "__DESCRIPTION__", 0L, false, 2L));
        assertEquals("comunicacion-basica", response.publicId()); assertFalse(response.enabled());
    }
    @Test void particleEditPreservesStableIdAndCreateDoesNotAcceptVersion() {
        var p = new Particle(); p.setId(3L); p.setPublicId("part-existing"); p.setVersion(1L);
        when(particles.findById(3L)).thenReturn(Optional.of(p));
        when(particles.saveAndFlush(p)).thenReturn(p);
        assertEquals("part-existing", service.updateParticle(3L, new ParticleRequest("__JP__", "__TITLE__", "__EXPLANATION__", 1L)).publicId());
        assertThrows(ResponseStatusException.class, () -> service.createParticle(new ParticleRequest("__JP__", "__TITLE__", "__EXPLANATION__", 0L)));
    }
    @Test void missingParticleDeleteIs404AndDoesNotFlush() {
        when(particles.findById(9L)).thenReturn(Optional.empty());
        assertEquals(404, assertThrows(ResponseStatusException.class, () -> service.deleteParticle(9L)).getStatusCode().value());
        verify(particles, never()).flush();
    }
    @Test void referencedParticleIs409BeforeRemovingManagedEntity() {
        var p = new Particle(); p.setId(7L);
        when(particles.findById(7L)).thenReturn(Optional.of(p));
        when(phrases.existsByLearningKeys_Particle_Id(7L)).thenReturn(true);
        assertEquals(409, assertThrows(ResponseStatusException.class, () -> service.deleteParticle(7L)).getStatusCode().value());
        verify(particles, never()).delete(any()); verify(particles, never()).flush();
    }
}
