package utils;

import java.util.regex.Pattern;

public class ValidadorUtil {

    public static boolean validarContrasenaSegura(String contrasena) {
        if (contrasena == null || contrasena.length() < 8) return false;

        boolean mayuscula = false, minuscula = false, numero = false, simbolo = false;

        for (char c : contrasena.toCharArray()) {
            if (Character.isUpperCase(c)) mayuscula = true;
            else if (Character.isLowerCase(c)) minuscula = true;
            else if (Character.isDigit(c)) numero = true;
            else simbolo = true;
        }

        return mayuscula && minuscula && numero && simbolo;
    }

    public static boolean validarCorreo(String correo) {
        if (correo == null) return false;
        String regex = "^[\\w.-]+@[\\w.-]+\\.\\w{2,}$";
        return Pattern.matches(regex, correo);
    }

    public static boolean validarRol(String rol) {
        if (rol == null) return false;
        try {
            enums.RolUsuario.valueOf(rol.trim().toUpperCase());
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
