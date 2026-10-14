package com.monpoint.auth.validator;

import com.monpoint.auth.exception.PasswordValidationException;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Política de contraseñas (H-012). Evalúa cada regla por separado y reporta todas las
 * que fallan en una sola respuesta, en lugar de un genérico "contraseña inválida".
 */
@Component
public class PasswordValidator {

    private static final int LONGITUD_MINIMA = 8;
    /** BCrypt solo procesa 72 bytes; Spring Security rechaza las más largas con una excepción. */
    private static final int BYTES_MAXIMOS = 72;
    private static final String SIMBOLOS = "@$!%*?&";

    public void validar(String password) {
        String valor = password == null ? "" : password;
        List<String> errores = new ArrayList<>();

        if (valor.length() < LONGITUD_MINIMA) {
            errores.add("La contraseña debe tener al menos 8 caracteres");
        }
        if (valor.getBytes(StandardCharsets.UTF_8).length > BYTES_MAXIMOS) {
            errores.add("La contraseña no puede superar 72 caracteres (los acentos y la ñ cuentan doble)");
        }
        if (valor.codePoints().noneMatch(Character::isUpperCase)) {
            errores.add("La contraseña debe contener al menos una letra mayúscula");
        }
        if (valor.codePoints().noneMatch(Character::isLowerCase)) {
            errores.add("La contraseña debe contener al menos una letra minúscula");
        }
        if (valor.codePoints().noneMatch(Character::isDigit)) {
            errores.add("La contraseña debe contener al menos un número");
        }
        if (valor.codePoints().noneMatch(c -> SIMBOLOS.indexOf(c) >= 0)) {
            errores.add("La contraseña debe contener al menos un carácter especial (@$!%*?&)");
        }

        if (!errores.isEmpty()) {
            throw new PasswordValidationException(errores);
        }
    }
}
