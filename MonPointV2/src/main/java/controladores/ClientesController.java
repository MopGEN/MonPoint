package controladores;

import dao.ClienteDAO;
import dao.ClienteDAOImpl;
import entidades.Cliente;
import javafx.application.Platform;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.FileChooser;
import utils.Animaciones;
import utils.PDFUtil;

import java.io.File;
import java.time.LocalDateTime;
import java.util.List;

public class ClientesController {

    @FXML private TableView<Cliente> tablaClientes;
    @FXML private TableColumn<Cliente, Integer> colId;
    @FXML private TableColumn<Cliente, String> colNombre;
    @FXML private TableColumn<Cliente, String> colCorreo;
    @FXML private TableColumn<Cliente, String> colTelefono;
    @FXML private TableColumn<Cliente, String> colDireccion;

    @FXML private TextField txtNombre;
    @FXML private TextField txtCorreo;
    @FXML private TextField txtTelefono;
    @FXML private TextField txtDireccion;

    @FXML private Button btnGuardar;
    @FXML private Button btnActualizar;
    @FXML private Button btnEliminar;
    @FXML private Button btnLimpiar;
    @FXML private Button btnExportarClientes;

    private final ClienteDAO clienteDAO = new ClienteDAOImpl();
    private final ObservableList<Cliente> listaClientes = FXCollections.observableArrayList();

    @FXML
    private void initialize() {
        colId.setCellValueFactory(cell -> new SimpleIntegerProperty(cell.getValue().getId()).asObject());
        colNombre.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getNombre()));
        colCorreo.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getCorreo()));
        colTelefono.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getTelefono()));
        colDireccion.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getDireccion()));

        tablaClientes.setItems(listaClientes);
        cargarClientes();

        Platform.runLater(() -> Animaciones.fadeIn(tablaClientes, 500));

        tablaClientes.getSelectionModel().selectedItemProperty().addListener((obs, oldSel, newSel) -> {
            if (newSel != null) mostrarDetallesCliente(newSel);
        });
    }

    private void cargarClientes() {
        listaClientes.clear();
        listaClientes.addAll(clienteDAO.listarTodos());
    }

    private void mostrarDetallesCliente(Cliente cliente) {
        txtNombre.setText(cliente.getNombre());
        txtCorreo.setText(cliente.getCorreo());
        txtTelefono.setText(cliente.getTelefono());
        txtDireccion.setText(cliente.getDireccion());
    }

    @FXML
    private void guardarCliente() {
        if (!validarCampos()) return;

        Cliente cliente = new Cliente();
        cliente.setNombre(txtNombre.getText().trim());
        cliente.setCorreo(txtCorreo.getText().trim());
        cliente.setTelefono(txtTelefono.getText().trim());
        cliente.setDireccion(txtDireccion.getText().trim());
        cliente.setFechaRegistro(LocalDateTime.now());

        clienteDAO.guardar(cliente);
        cargarClientes();
        limpiarCampos();
        Animaciones.zoomIn(tablaClientes, 300);
    }

    @FXML
    private void actualizarCliente() {
        Cliente cliente = tablaClientes.getSelectionModel().getSelectedItem();
        if (cliente == null) {
            mostrarAlerta("Selección requerida", "Selecciona un cliente de la tabla.");
            return;
        }

        if (!validarCampos()) return;

        cliente.setNombre(txtNombre.getText().trim());
        cliente.setCorreo(txtCorreo.getText().trim());
        cliente.setTelefono(txtTelefono.getText().trim());
        cliente.setDireccion(txtDireccion.getText().trim());

        clienteDAO.actualizar(cliente);
        cargarClientes();
        limpiarCampos();
        Animaciones.zoomIn(tablaClientes, 300);
    }

    @FXML
    private void eliminarCliente() {
        Cliente cliente = tablaClientes.getSelectionModel().getSelectedItem();
        if (cliente == null) {
            mostrarAlerta("Selección requerida", "Selecciona un cliente de la tabla.");
            return;
        }

        clienteDAO.eliminar(cliente);
        cargarClientes();
        limpiarCampos();
        Animaciones.fadeOut(tablaClientes, 300);
    }

    @FXML
    private void limpiarCampos() {
        txtNombre.clear();
        txtCorreo.clear();
        txtTelefono.clear();
        txtDireccion.clear();
        tablaClientes.getSelectionModel().clearSelection();
    }

    @FXML
    private void exportarClientesPDF() {
        try {
            List<Cliente> clientes = clienteDAO.listarTodos();
            if (clientes.isEmpty()) {
                mostrarAlerta("Sin datos", "No hay clientes registrados para exportar.");
                return;
            }

            FileChooser chooser = new FileChooser();
            chooser.setTitle("Guardar listado de clientes");
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Archivo PDF", "*.pdf"));
            File destino = chooser.showSaveDialog(btnExportarClientes.getScene().getWindow());
            if (destino == null) return;

            PDFUtil.exportarClientes(destino, clientes);

            mostrarAlerta("Éxito", "PDF de clientes generado exitosamente:\n" + destino.getAbsolutePath());
        } catch (Exception ex) {
            ex.printStackTrace();
            mostrarAlerta("Error", "No se pudo generar el PDF:\n" + ex.getMessage());
        }
    }

    private boolean validarCampos() {
        String nombre = txtNombre.getText().trim();
        String correo = txtCorreo.getText().trim();
        String telefono = txtTelefono.getText().trim();
        String direccion = txtDireccion.getText().trim();

        if (nombre.length() < 3) {
            mostrarAlerta("Nombre inválido", "El nombre debe tener al menos 3 caracteres.");
            return false;
        }
        if (!correo.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$")) {
            mostrarAlerta("Correo inválido", "Ingresa un correo electrónico válido.");
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

        return true;
    }

    private void mostrarAlerta(String titulo, String mensaje) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle(titulo);
        alert.setHeaderText(null);
        alert.setContentText(mensaje);
        alert.showAndWait();
    }
}
