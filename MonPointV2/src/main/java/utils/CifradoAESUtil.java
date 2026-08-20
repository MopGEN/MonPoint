package utils;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import java.security.SecureRandom;
import java.util.Arrays;

public class CifradoAESUtil {

    private static final String TRANSFORMACION = "AES/CBC/PKCS5Padding";
    private static final int TAMANO_IV = 16;

    public static byte[] cifrar(byte[] datos, SecretKey clave) throws Exception {
        Cipher cipher = Cipher.getInstance(TRANSFORMACION);
        byte[] iv = generarIV();
        cipher.init(Cipher.ENCRYPT_MODE, clave, new IvParameterSpec(iv));

        byte[] datosCifrados = cipher.doFinal(datos);

        byte[] combinado = new byte[TAMANO_IV + datosCifrados.length];
        System.arraycopy(iv, 0, combinado, 0, TAMANO_IV);
        System.arraycopy(datosCifrados, 0, combinado, TAMANO_IV, datosCifrados.length);
        return combinado;
    }

    public static byte[] descifrar(byte[] datosCombinados, SecretKey clave) throws Exception {
        byte[] iv = Arrays.copyOfRange(datosCombinados, 0, TAMANO_IV);
        byte[] datosCifrados = Arrays.copyOfRange(datosCombinados, TAMANO_IV, datosCombinados.length);

        Cipher cipher = Cipher.getInstance(TRANSFORMACION);
        cipher.init(Cipher.DECRYPT_MODE, clave, new IvParameterSpec(iv));

        return cipher.doFinal(datosCifrados);
    }

    private static byte[] generarIV() {
        byte[] iv = new byte[TAMANO_IV];
        new SecureRandom().nextBytes(iv);
        return iv;
    }
}
