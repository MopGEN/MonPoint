package app;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import utils.SesionUsuario;
import utils.ThemeManager;
import entidades.Usuario;

import java.net.URL;

public class MainApp extends Application {

    @Override
    public void start(Stage primaryStage) {
        try {
            Parent root;
            if (SesionUsuario.existeSesionGuardada()) {
                Usuario sesion = SesionUsuario.recuperarSesion();
                if (sesion != null) {
                    SesionUsuario.iniciarSesion(sesion);
                    FXMLLoader loader = new FXMLLoader(getClass().getResource("/vista/dashboard.fxml"));
                    root = loader.load();
                } else {
                    root = new FXMLLoader(getClass().getResource("/vista/loginview.fxml")).load();
                }
            } else {
                root = new FXMLLoader(getClass().getResource("/vista/loginview.fxml")).load();
            }

            Scene scene = new Scene(root);

            URL cssLight = getClass().getResource("/estilos/light-theme.css");
            if (cssLight != null) scene.getStylesheets().add(cssLight.toExternalForm());

            URL cssDark = getClass().getResource("/estilos/dark-theme.css");
            if (cssDark != null && ThemeManager.estaModoOscuro()) scene.getStylesheets().add(cssDark.toExternalForm());

            ThemeManager.aplicarTema(scene, ThemeManager.estaModoOscuro());

            primaryStage.setScene(scene);
            primaryStage.setTitle("MonPoint - Sistema de Ventas");
            primaryStage.setMaximized(true);
            SesionUsuario.setStagePrincipal(primaryStage);

            primaryStage.show();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
