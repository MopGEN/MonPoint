package config;



import org.mindrot.jbcrypt.BCrypt;

/**
 * provee utilidades para encriptar y validar contraseñas usando bcrypt.
 */
public class SecurityConfig {
    // fuerza de trabajo: entre 10 y 12 es recomendable para producción
    private static final int WORKLOAD = 12;

    /**
     * genera un hash de la contraseña en texto plano.
     * @param plainTextPassword la contraseña sin cifrar
     * @return el hash bcrypt a almacenar en BD
     */
    public static String hashPassword(String plainTextPassword) {
        String salt = BCrypt.gensalt(WORKLOAD);
        return BCrypt.hashpw(plainTextPassword, salt);
    }

    /**
     * verifica que la contraseña en texto plano coincida con el hash almacenado.
     * @param plainTextPassword la contraseña introducida por el usuario
     * @param storedHash el hash recuperado de la BD
     * @return true si coincide, false en caso contrario
     * @throws IllegalArgumentException si el hash almacenado no tiene formato bcrypt
     */
    public static boolean checkPassword(String plainTextPassword, String storedHash) {
        if (storedHash == null || !storedHash.startsWith("$2a$")) {
            throw new IllegalArgumentException("hash inválido para comparación");
        }
        return BCrypt.checkpw(plainTextPassword, storedHash);
    }
}
