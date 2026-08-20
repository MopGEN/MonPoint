package utils;

import entidades.Usuario;
import javafx.stage.Stage;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

public class SesionUsuario {

    private static Usuario usuarioActual;
    private static final String SESION_FILE = "sesion.dat";
    private static final String CLAVE_FILE = "clave.key";
    private static Stage stagePrincipal;

    public static void iniciarSesion(Usuario usuario) {
        usuarioActual = usuario;
    }

    public static Usuario getUsuarioActual() {
        return usuarioActual;
    }

    public static String getRolActual() {
        return usuarioActual != null ? usuarioActual.getRol().name() : null;
    }

    public static void cerrarSesion() {
        usuarioActual = null;
        eliminarSesionGuardada();
    }

    public static void setStagePrincipal(Stage stage) {
        stagePrincipal = stage;
    }

    public static Stage getStagePrincipal() {
        return stagePrincipal;
    }

    public static boolean existeSesionGuardada() {
        return new File(SESION_FILE).exists();
    }

    public static void guardarSesion(Usuario usuario) {
        try {
            SecretKey clave = ClaveAESUtil.obtenerClave();
            Cipher cipher = Cipher.getInstance("AES");
            cipher.init(Cipher.ENCRYPT_MODE, clave);

            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            ObjectOutputStream oos = new ObjectOutputStream(bos);
            oos.writeObject(usuario);
            oos.close();

            byte[] datosSerializados = bos.toByteArray();
            byte[] datosCifrados = cipher.doFinal(datosSerializados);

            Files.write(Path.of(SESION_FILE), Base64.getEncoder().encode(datosCifrados));
        } catch (Exception e) {
            System.err.println("⚠️ Error al guardar sesión cifrada: " + e.getMessage());
        }
    }

    public static Usuario recuperarSesion() {
        File file = new File(SESION_FILE);
        if (!file.exists()) return null;

        try {
            byte[] datosCifrados = Base64.getDecoder().decode(Files.readAllBytes(file.toPath()));
            SecretKey clave = ClaveAESUtil.obtenerClave();

            Cipher cipher = Cipher.getInstance("AES");
            cipher.init(Cipher.DECRYPT_MODE, clave);

            byte[] datosDescifrados = cipher.doFinal(datosCifrados);
            ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(datosDescifrados));
            Object obj = ois.readObject();

            if (obj instanceof Usuario usuario) {
                usuarioActual = usuario;
                return usuario;
            }
        } catch (Exception e) {
            System.err.println("⚠️ Error al recuperar sesión cifrada: " + e.getMessage());
            eliminarSesionGuardada(); // ⚠️ limpiar archivo corrupto o incompatible
        }
        return null;
    }

    public static void eliminarSesionGuardada() {
        File file = new File(SESION_FILE);
        if (file.exists()) file.delete();
    }

    public static Usuario recuperarSesionGuardada() {
        return recuperarSesion();
    }
}
