package utils;

import javax.crypto.SecretKey;
import javax.crypto.KeyGenerator;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

public class ClaveAESUtil {

    private static final String NOMBRE_ARCHIVO = "clave.key";
    private static final String ALGORITMO = "AES";
    private static final int TAMANO_CLAVE = 128;

    public static SecretKey obtenerClave() {
        File archivo = new File(NOMBRE_ARCHIVO);

        if (archivo.exists()) {
            return cargarClaveDesdeArchivo(archivo);
        } else {
            SecretKey clave = generarClaveNueva();
            guardarClaveEnArchivo(clave, archivo);
            return clave;
        }
    }

    private static SecretKey generarClaveNueva() {
        try {
            KeyGenerator generador = KeyGenerator.getInstance(ALGORITMO);
            generador.init(TAMANO_CLAVE);
            return generador.generateKey();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("No se pudo generar la clave AES", e);
        }
    }

    private static void guardarClaveEnArchivo(SecretKey clave, File archivo) {
        try (FileWriter fw = new FileWriter(archivo)) {
            String claveCodificada = Base64.getEncoder().encodeToString(clave.getEncoded());
            fw.write(claveCodificada);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static SecretKey cargarClaveDesdeArchivo(File archivo) {
        try (BufferedReader br = new BufferedReader(new FileReader(archivo))) {
            String claveCodificada = br.readLine();
            byte[] bytesClave = Base64.getDecoder().decode(claveCodificada);
            return new SecretKeySpec(bytesClave, ALGORITMO);
        } catch (IOException e) {
            throw new RuntimeException("No se pudo cargar la clave AES desde el archivo", e);
        }
    }
}
