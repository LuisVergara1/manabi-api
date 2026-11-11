package dev.luisvergara.manabi.service;
import jakarta.validation.Validator;
import org.springframework.stereotype.Component;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import lombok.RequiredArgsConstructor;
import java.util.Objects;
@Component @RequiredArgsConstructor
public class ModelValidation {
    private final Validator validator;
    public <T> T valid(T value) {
        if (value == null) throw bad("Se requiere un cuerpo de solicitud");
        var violations = validator.validate(value);
        if (!violations.isEmpty()) throw bad(violations.stream()
            .map(v -> v.getPropertyPath() + ": " + v.getMessage()).sorted().toList().toString());
        return value;
    }
    public void newId(Long id) { if (id != null) throw bad("El ID de un alta debe ser nulo"); }
    public void version(Long supplied, Long stored) {
        if (supplied == null || !Objects.equals(supplied, stored))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Version ausente u obsoleta");
    }
    public static ResponseStatusException bad(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
    public static ResponseStatusException missing() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Contenido no encontrado");
    }
}
