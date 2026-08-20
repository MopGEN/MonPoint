package controladores;

import dao.ProveedorDAO;
import dao.ProveedorDAOImpl;
import entidades.Proveedor;
import entidades.Usuario;
import enums.RolUsuario;
import javafx.application.Platform;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Stage;
import utils.Animaciones;
import utils.SesionUsuario;

public class ProveedoresController {

    @FXML private TableView<Proveedor> tablaProveedores;
    @FXML private TableColumn<Proveedor, Integer> colId;
    @FXML private TableColumn<Proveedor, String> colNombre;
    @FXML private TableColumn<Proveedor, String> colRuc;
    @FXML private TableColumn<Proveedor, String> colTelefono;
    @FXML private TableColumn<Proveedor, String> colDireccion;
    @FXML private TableColumn<Proveedor, String> colRazonSocial;

    @FXML private TextField txtNombre;
    @FXML private TextField txtRuc;
    @FXML private TextField txtTelefono;
    @FXML private TextField txtDireccion;
    @FXML private TextField txtRazonSocial;

    @FXML private Button btnGuardar;
    @FXML private Button btnActualizar;
    @FXML private Button btnEliminar;
    @FXML private Button btnLimpiar;

    private final ProveedorDAO proveedorDAO = new ProveedorDAOImpl();
    private final ObservableList<Proveedor> listaProveedores = FXCollections.observableArrayList();

    @FXML
    private void initialize() {
        if (!accesoPermitido()) {
            redireccionarAlDashboard();
            return;
        }

        configurarTabla();
        cargarProveedores();
        Platform.runLater(() -> Animaciones.fadeIn(tablaProveedores, 500));

        tablaProveedores.getSelectionModel().selectedItemProperty().addListener((obs, oldSel, newSel) -> {
            if (newSel != null) mostrarDetallesProveedor(newSel);
        });
    }

    private boolean accesoPermitido() {
        Usuario usuario = SesionUsuario.getUsuarioActual();
        if (usuario == null) return false;
        RolUsuario rol = usuario.getRol();
        return rol == RolUsuario.ADMIN || rol == RolUsuario.VENDEDOR;
    }

    private void redireccionarAlDashboard() {
        try {
            Parent dashboard = FXMLLoader.load(getClass().getResource("/vista/dashboard.fxml"));
            Stage stage = SesionUsuario.getStagePrincipal();
            if (stage != null) {
                stage.setScene(new Scene(dashboard));
                stage.setTitle("MonPoint - Panel Principal");
                stage.show();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void configurarTabla() {
        colId.setCellValueFactory(c -> new SimpleIntegerProperty(c.getValue().getId()).asObject());
        colNombre.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getNombre()));
        colRuc.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getRuc()));
        colTelefono.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getTelefono()));
        colDireccion.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getDireccion()));
        colRazonSocial.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getRazonSocial()));
    }

    private void cargarProveedores() {
        listaProveedores.clear();
        listaProveedores.addAll(proveedorDAO.listarTodos());
        tablaProveedores.setItems(listaProveedores);
    }

    private void mostrarDetallesProveedor(Proveedor p) {
        txtNombre.setText(p.getNombre());
        txtRuc.setText(p.getRuc());
        txtTelefono.setText(p.getTelefono());
        txtDireccion.setText(p.getDireccion());
        txtRazonSocial.setText(p.getRazonSocial());
    }

    @FXML
    private void guardarProveedor() {
        if (!validarCampos()) return;

        Proveedor p = new Proveedor();
        p.setNombre(txtNombre.getText().trim());
        p.setRuc(txtRuc.getText().trim());
        p.setTelefono(txtTelefono.getText().trim());
        p.setDireccion(txtDireccion.getText().trim());
        p.setRazonSocial(txtRazonSocial.getText().trim());

        proveedorDAO.guardar(p);
        cargarProveedores();
        limpiarCampos();
        Animaciones.zoomIn(tablaProveedores, 300);
    }

    @FXML
    private void actualizarProveedor() {
        Proveedor p = tablaProveedores.getSelectionModel().getSelectedItem();
        if (p == null) {
            mostrarAlerta("Selección requerida", "Selecciona un proveedor de la tabla.");
            return;
        }

        if (!validarCampos()) return;

        p.setNombre(txtNombre.getText().trim());
        p.setRuc(txtRuc.getText().trim());
        p.setTelefono(txtTelefono.getText().trim());
        p.setDireccion(txtDireccion.getText().trim());
        p.setRazonSocial(txtRazonSocial.getText().trim());

        proveedorDAO.actualizar(p);
        cargarProveedores();
        limpiarCampos();
        Animaciones.zoomIn(tablaProveedores, 300);
    }

    @FXML
    private void eliminarProveedor() {
        Proveedor p = tablaProveedores.getSelectionModel().getSelectedItem();
        if (p == null) {
            mostrarAlerta("Selección requerida", "Selecciona un proveedor de la tabla.");
            return;
        }

        proveedorDAO.eliminar(p);
        cargarProveedores();
        limpiarCampos();
        Animaciones.fadeOut(tablaProveedores, 300);
    }

    @FXML
    private void limpiarCampos() {
        txtNombre.clear();
        txtRuc.clear();
        txtTelefono.clear();
        txtDireccion.clear();
        txtRazonSocial.clear();
        tablaProveedores.getSelectionModel().clearSelection();
    }

    private boolean validarCampos() {
        String nombre = txtNombre.getText().trim();
        String ruc = txtRuc.getText().trim();
        String telefono = txtTelefono.getText().trim();
        String direccion = txtDireccion.getText().trim();
        String razonSocial = txtRazonSocial.getText().trim();

        if (nombre.length() < 2) {
            mostrarAlerta("Nombre inválido", "El nombre debe tener al menos 2 caracteres.");
            return false;
        }
        if (ruc.length() < 10) {
            mostrarAlerta("RUC inválido", "El RUC debe contener al menos 10 caracteres.");
            return false;
        }
        if (!telefono.matches("\\d{10}")) {
            mostrarAlerta("Teléfono inválido", "El teléfono debe tener exactamente 10 dígitos numéricos.");
            return false;
        }
        if (direccion.isEmpty()) {
            mostrarAlerta("Dirección requerida", "La dirección no puede estar vacía.");
            return false;
        }
        if (razonSocial.isEmpty()) {
            mostrarAlerta("Razón Social requerida", "La razón social no puede estar vacía.");
            return false;
        }
        return true;
    }

    private void mostrarAlerta(String titulo, String mensaje) {
        Alert alerta = new Alert(Alert.AlertType.WARNING);
        alerta.setTitle(titulo);
        alerta.setHeaderText(null);
        alerta.setContentText(mensaje);
        alerta.showAndWait();
    }
}
