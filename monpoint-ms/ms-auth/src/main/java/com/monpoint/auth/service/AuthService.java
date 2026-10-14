package com.monpoint.auth.service;

import com.monpoint.auth.dto.request.LoginRequest;
import com.monpoint.auth.dto.response.AuthResponse;

/**
 * Autenticación y emisión de tokens (H-010).
 */
public interface AuthService {

    /**
     * @throws com.monpoint.auth.exception.CredencialesInvalidasException si el correo no existe,
     *         la contraseña no coincide o la cuenta está inactiva (siempre el mismo mensaje opaco)
     */
    AuthResponse login(LoginRequest request);
}
