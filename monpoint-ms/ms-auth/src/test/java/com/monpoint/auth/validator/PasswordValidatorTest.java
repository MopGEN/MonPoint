package com.monpoint.auth.validator;

import com.monpoint.auth.exception.PasswordValidationException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Paso 7.1 — Cada regla de la política de contraseñas (H-012) se reporta por separado.
 */
class PasswordValidatorTest {

    private final PasswordValidator validator = new PasswordValidator();

    @Test
    void validar_PasswordQueCumpleTodasLasReglas_NoLanza() {
        assertThatCode(() -> validator.validar("Admin123!")).doesNotThrowAnyException();
    }

    @Test
    void validar_SinMayuscula_ReportaSoloEsaRegla() {
        assertThat(errores("admin123!")).containsExactly("La contraseña debe contener al menos una letra mayúscula");
    }

    @Test
    void validar_SinMinuscula_ReportaSoloEsaRegla() {
        assertThat(errores("ADMIN123!")).containsExactly("La contraseña debe contener al menos una letra minúscula");
    }

    @Test
    void validar_SinNumero_ReportaSoloEsaRegla() {
        assertThat(errores("Admin!!!!")).containsExactly("La contraseña debe contener al menos un número");
    }

    @Test
    void validar_SinSimbolo_ReportaSoloEsaRegla() {
        assertThat(errores("Admin1234"))
                .containsExactly("La contraseña debe contener al menos un carácter especial (@$!%*?&)");
    }

    @Test
    void validar_ConMenosDe8Caracteres_ReportaSoloEsaRegla() {
        assertThat(errores("Ad1!")).containsExactly("La contraseña debe tener al menos 8 caracteres");
    }

    @Test
    void validar_ConSimboloFueraDelConjuntoPermitido_NoLoCuentaComoSimbolo() {
        assertThat(errores("Admin123#"))
                .containsExactly("La contraseña debe contener al menos un carácter especial (@$!%*?&)");
    }

    @Test
    void validar_ConMasDe72Bytes_ReportaElLimiteDeBCrypt() {
        String larga = "Aa1!" + "x".repeat(69);

        assertThat(errores(larga))
                .containsExactly("La contraseña no puede superar 72 caracteres (los acentos y la ñ cuentan doble)");
    }

    @Test
    void validar_ConEnieYAcentos_LosCuentaComoLetras() {
        assertThatCode(() -> validator.validar("Ñandú123!")).doesNotThrowAnyException();
    }

    @Test
    void validar_Null_ReportaTodasLasReglasDeUnaVez() {
        assertThat(errores(null)).hasSize(5);
    }

    private List<String> errores(String password) {
        return assertThrows(PasswordValidationException.class, () -> validator.validar(password)).getErrores();
    }
}
