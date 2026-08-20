package controladores;

import entidades.Usuario;
import enums.RolUsuario;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import utils.Animaciones;
import utils.SesionUsuario;
import utils.ThemeManager;

public class DashboardController {

    @FXML private Label lblBienvenida;
    @FXML private StackPane contenedorVistas;
    @FXML private Button btnClientes, btnProductos, btnProveedores, btnVentas, btnUsuarios, btnConfiguracion, btnSalir, btnCambiarTema;
    @FXML private TabPane tabPane; // <- asegúrate de que esté en dashboard.fxml
    @FXML private Tab tabVentas;

    @FXML
    private void initialize() {
        configurarBienvenida();
        configurarAccesosPorRol();
    }

    private void configurarBienvenida() {
        Usuario usuario = SesionUsuario.getUsuarioActual();
        if (usuario != null) {
            lblBienvenida.setText("Bienvenido/a, " + usuario.getNombre());
        } else {
            lblBienvenida.setText("Bienvenido/a al sistema");
        }
    }

    private void configurarAccesosPorRol() {
        String rolStr = SesionUsuario.getRolActual();
        if (rolStr != null) {
            try {
                RolUsuario rol = RolUsuario.valueOf(rolStr.toUpperCase());
                if (rol == RolUsuario.VENDEDOR) {
                    if (btnUsuarios != null) btnUsuarios.setVisible(false);
                    if (btnConfiguracion != null) btnConfiguracion.setVisible(false);
                    if (btnProveedores != null) btnProveedores.setVisible(false);
                }
            } catch (IllegalArgumentException e) {
                System.err.println("⚠️ Rol desconocido: " + rolStr);
            }
        }
    }

    @FXML
    private void cargarVistaClientes() {
        cargarVista("/vista/clientes.fxml");
    }

    @FXML
    private void cargarVistaProductos() {
        cargarVista("/vista/productos.fxml");
    }

    @FXML
    private void cargarVistaProveedores() {
        cargarVista("/vista/proveedores.fxml");
    }

    @FXML
    private void cargarVistaVentas() {
        cargarVista("/vista/ventas.fxml");
    }

    @FXML
    private void cargarVistaUsuarios() {
        cargarVista("/vista/usuarios.fxml");
    }

    @FXML
    private void cargarVistaConfiguracion() {
        cargarVista("/vista/configuracion.fxml");
    }

    @FXML
    private void cerrarSesion() {
        try {
            SesionUsuario.cerrarSesion();
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/vista/loginview.fxml"));
            Parent loginView = loader.load();

            Stage stage = SesionUsuario.getStagePrincipal();
            if (stage != null) {
                Scene escena = new Scene(loginView);
                ThemeManager.aplicarTema(escena, ThemeManager.estaModoOscuro());
                stage.setScene(escena);
                stage.setTitle("MonPoint - Inicio de Sesión");
                stage.show();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @FXML
    private void alternarTema() {
        try {
            Scene escena = contenedorVistas.getScene();
            if (escena != null) {
                ThemeManager.alternarTema(escena);
            } else {
                System.out.println("⚠️ No se pudo obtener la escena para aplicar el tema.");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void cargarVista(String rutaFXML) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(rutaFXML));
            Parent vista = loader.load();

            if (contenedorVistas.getScene() != null) {
                ThemeManager.aplicarTema(contenedorVistas.getScene(), ThemeManager.estaModoOscuro());
            }

            contenedorVistas.getChildren().setAll(vista);
            Animaciones.fadeIn(vista, 400);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // NUEVO: permite seleccionar la pestaña de ventas desde otros controladores
    public void mostrarPestaniaVentas() {
        if (tabPane != null && tabVentas != null) {
            tabPane.getSelectionModel().select(tabVentas);
            cargarVista("/vista/ventas.fxml");
        }
        
    }
}
