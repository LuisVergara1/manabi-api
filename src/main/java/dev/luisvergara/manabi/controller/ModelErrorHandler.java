package dev.luisvergara.manabi.controller;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import jakarta.persistence.OptimisticLockException;
@RestControllerAdvice(assignableTypes = {KanaController.class, KanjiController.class, VocabularyController.class, PhraseController.class, PhraseReferenceController.class})
public class ModelErrorHandler {
    @ExceptionHandler({OptimisticLockingFailureException.class, OptimisticLockException.class})
    public ResponseEntity<ProblemDetail> conflict(Exception ignored) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(
            ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "El contenido fue modificado; recargue su version"));
    }
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ProblemDetail> integrity(DataIntegrityViolationException ignored) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(
            ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "La operacion incumple una restriccion del modelo"));
    }
}
