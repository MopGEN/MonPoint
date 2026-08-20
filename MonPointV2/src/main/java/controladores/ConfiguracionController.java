package controladores;

import dao.ConfiguracionDAO;
import dao.ConfiguracionDAOImpl;
import entidades.Configuracion;
import entidades.Usuario;
import enums.RolUsuario;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import utils.SesionUsuario;

public class ConfiguracionController {

    @FXML private TextField txtNombreEmpresa;
    @FXML private TextField txtRfc;
    @FXML private TextField txtDireccion;
    @FXML private TextField txtTelefono;

    private final ConfiguracionDAO configuracionDAO = new ConfiguracionDAOImpl();
    private Configuracion configuracionActual;

    @FXML
    private void initialize() {
        if (!esAdmin()) {
            mostrarAccesoDenegado();
            return;
        }
        cargarConfiguracion();
    }

    private boolean esAdmin() {
        Usuario usuario = SesionUsuario.getUsuarioActual();
        return usuario != null && RolUsuario.ADMIN.equals(usuario.getRol());
    }

    private void mostrarAccesoDenegado() {
        Alert alerta = new Alert(Alert.AlertType.ERROR);
        alerta.setTitle("Acceso Denegado");
        alerta.setHeaderText(null);
        alerta.setContentText("No tienes permiso para acceder a este módulo.");
        alerta.showAndWait();

        try {
            Parent dashboard = FXMLLoader.load(getClass().getResource("/vista/dashboard.fxml"));
            Scene escena = new Scene(dashboard);

            Stage stage = SesionUsuario.getStagePrincipal();
            if (stage != null) {
                stage.setScene(escena);
                stage.setTitle("MonPoint - Panel Principal");
                stage.show();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @FXML
    private void guardarConfiguracion() {
        if (!camposValidos()) {
            return;
        }

        if (configuracionActual == null) {
            configuracionActual = new Configuracion();
        }

        configuracionActual.setNombreEmpresa(txtNombreEmpresa.getText().trim());
        configuracionActual.setRfc(txtRfc.getText().trim());
        configuracionActual.setDireccion(txtDireccion.getText().trim());
        configuracionActual.setTelefono(txtTelefono.getText().trim());

        if (configuracionActual.getId() == 0) {
            configuracionDAO.guardar(configuracionActual);
        } else {
            configuracionDAO.actualizar(configuracionActual);
        }

        mostrarAlerta("Éxito", "Datos de configuración guardados correctamente.");
    }

    private void cargarConfiguracion() {
        configuracionActual = configuracionDAO.obtener();

        if (configuracionActual != null) {
            txtNombreEmpresa.setText(configuracionActual.getNombreEmpresa());
            txtRfc.setText(configuracionActual.getRfc());
            txtDireccion.setText(configuracionActual.getDireccion());
            txtTelefono.setText(configuracionActual.getTelefono());
        }
    }

    private boolean camposValidos() {
        String nombreEmpresa = txtNombreEmpresa.getText().trim();
        String rfc = txtRfc.getText().trim();
        String direccion = txtDireccion.getText().trim();
        String telefono = txtTelefono.getText().trim();

        if (nombreEmpresa.isEmpty() || rfc.isEmpty() || direccion.isEmpty()) {
            mostrarAlerta("Campos requeridos", "Nombre de empresa, RFC y dirección son obligatorios.");
            return false;
        }

        if (rfc.length() < 12 || rfc.length() > 13) {
            mostrarAlerta("RFC inválido", "El RFC debe tener 12 o 13 caracteres.");
            return false;
        }

        if (!telefono.matches("\\d{10}")) {
            mostrarAlerta("Teléfono inválido", "El teléfono debe contener exactamente 10 dígitos numéricos.");
            return false;
        }

        return true;
    }

    private void mostrarAlerta(String titulo, String mensaje) {
        Alert alerta = new Alert(Alert.AlertType.INFORMATION);
        alerta.setTitle(titulo);
        alerta.setHeaderText(null);
        alerta.setContentText(mensaje);
        alerta.showAndWait();
    }
}
