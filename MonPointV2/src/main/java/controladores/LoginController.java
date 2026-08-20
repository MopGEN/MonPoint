package controladores;

import config.SecurityConfig;
import dao.UsuarioDAO;
import dao.UsuarioDAOImpl;
import entidades.Usuario;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Stage;
import utils.Animaciones;
import utils.SesionUsuario;
import utils.ThemeManager;

import java.net.URL;

public class LoginController {

    @FXML private TextField txtCorreo;
    @FXML private PasswordField txtContrasena;
    @FXML private Button btnLogin;

    private final UsuarioDAO usuarioDAO = new UsuarioDAOImpl();

    @FXML
    private void initialize() {
        Platform.runLater(() -> {
            Animaciones.fadeIn(txtCorreo, 400);
            Animaciones.fadeIn(txtContrasena, 400);
            Animaciones.zoomIn(txtCorreo, 400);
            Animaciones.zoomIn(txtContrasena, 400);
            verificarSesionGuardada();
        });
    }

    private void verificarSesionGuardada() {
        try {
            Usuario recordado = SesionUsuario.recuperarSesionGuardada();
            if (recordado != null) {
                iniciarSesion(recordado, false);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @FXML
    private void handleLogin() {
        String correo = txtCorreo.getText().trim();
        String contrasena = txtContrasena.getText();

        if (correo.isEmpty() || contrasena.isEmpty()) {
            mostrarAlerta("Campos requeridos", "Correo y contraseña son obligatorios.");
            return;
        }

        Usuario usuario = usuarioDAO.buscarPorCorreo(correo);
        if (usuario == null) {
            mostrarAlerta("Acceso denegado", "Usuario no encontrado.");
            return;
        }

        String stored = usuario.getContrasena();
        boolean valido = false;

        if (stored != null && !stored.startsWith("$2a$")) {
            valido = contrasena.equals(stored);
            if (valido) {
                String newHash = SecurityConfig.hashPassword(contrasena);
                usuario.setContrasena(newHash);
                usuarioDAO.actualizar(usuario);
            }
        } else if (stored != null) {
            valido = SecurityConfig.checkPassword(contrasena, stored);
        }

        if (!valido) {
            mostrarAlerta("Acceso denegado", "Correo o contraseña incorrectos.");
            return;
        }

        iniciarSesion(usuario, true);
    }

    private void iniciarSesion(Usuario usuario, boolean guardarSesion) {
        try {
            SesionUsuario.iniciarSesion(usuario);
            if (guardarSesion) {
                SesionUsuario.guardarSesion(usuario);
            }

            FXMLLoader loader = new FXMLLoader(getClass().getResource("/vista/dashboard.fxml"));
            Parent root = loader.load();
            Scene escena = new Scene(root);

            URL cssLight = getClass().getResource("/estilos/light-theme.css");
            if (cssLight != null) escena.getStylesheets().add(cssLight.toExternalForm());
            URL cssDark = getClass().getResource("/estilos/dark-theme.css");
            if (cssDark != null && ThemeManager.estaModoOscuro()) escena.getStylesheets().add(cssDark.toExternalForm());

            ThemeManager.aplicarTema(escena, ThemeManager.estaModoOscuro());

            Stage stage = (Stage) txtCorreo.getScene().getWindow();
stage.setScene(escena);
stage.setTitle("MonPoint - Panel Principal");
stage.setMaximized(true); // ✅ inicia maximizado
stage.setResizable(true); // ✅ permite redimensionar libremente
stage.show();


            SesionUsuario.setStagePrincipal(stage);
        } catch (Exception e) {
            e.printStackTrace();
            mostrarAlerta("Error", "Error al cargar el panel principal.");
        }
    }

    private void mostrarAlerta(String titulo, String mensaje) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(titulo);
        alert.setHeaderText(null);
        alert.setContentText(mensaje);
        alert.showAndWait();
    }
}
