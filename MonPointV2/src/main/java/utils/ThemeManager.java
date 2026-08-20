package utils;

import javafx.scene.Scene;

public class ThemeManager {

    private static boolean modoOscuro = false;

    // Fíjate en la “s” de “estilos”
    private static final String LIGHT_THEME = "/estilos/light-theme.css";
    private static final String DARK_THEME  = "/estilos/dark-theme.css";

    public static void aplicarTema(Scene escena, boolean oscuro) {
        escena.getStylesheets().clear();
        // getResource ya no devolverá null
        var url = ThemeManager.class.getResource(oscuro ? DARK_THEME : LIGHT_THEME);
        if (url != null) {
            escena.getStylesheets().add(url.toExternalForm());
            modoOscuro = oscuro;
        } else {
            System.err.println("⚠️ CSS no encontrado: " + (oscuro ? DARK_THEME : LIGHT_THEME));
        }
    }

    public static void alternarTema(Scene escena) {
        aplicarTema(escena, !modoOscuro);
    }

    public static boolean estaModoOscuro() {
        return modoOscuro;
    }
}
