package controladores;

import dao.ProductoDAO;
import dao.ProductoDAOImpl;
import entidades.Producto;
import javafx.application.Platform;
import javafx.beans.property.SimpleDoubleProperty;
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

public class ProductosController {

    @FXML private TableView<Producto> tablaProductos;
    @FXML private TableColumn<Producto, Integer> colId;
    @FXML private TableColumn<Producto, String> colNombre;
    @FXML private TableColumn<Producto, Double> colPrecio;
    @FXML private TableColumn<Producto, Integer> colStock;
    @FXML private TableColumn<Producto, String> colDescripcion;

    @FXML private TextField txtNombre;
    @FXML private TextField txtPrecio;
    @FXML private TextField txtStock;
    @FXML private TextField txtDescripcion;

    @FXML private Button btnGuardar;
    @FXML private Button btnActualizar;
    @FXML private Button btnEliminar;
    @FXML private Button btnLimpiar;
    @FXML private Button btnExportarPDF;

    private final ProductoDAO productoDAO = new ProductoDAOImpl();
    private final ObservableList<Producto> listaProductos = FXCollections.observableArrayList();

    @FXML
    private void initialize() {
        colId.setCellValueFactory(cell -> new SimpleIntegerProperty(cell.getValue().getId()).asObject());
        colNombre.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getNombre()));
        colPrecio.setCellValueFactory(cell -> new SimpleDoubleProperty(cell.getValue().getPrecio()).asObject());
        colStock.setCellValueFactory(cell -> new SimpleIntegerProperty(cell.getValue().getStock()).asObject());
        colDescripcion.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getDescripcion()));

        tablaProductos.setItems(listaProductos);
        cargarProductos();

        Platform.runLater(() -> Animaciones.fadeIn(tablaProductos, 500));

        tablaProductos.getSelectionModel().selectedItemProperty().addListener((obs, oldSel, newSel) -> {
            if (newSel != null) mostrarDetallesProducto(newSel);
        });
    }

    private void cargarProductos() {
        listaProductos.clear();
        listaProductos.addAll(productoDAO.listarTodos());
    }

    private void mostrarDetallesProducto(Producto producto) {
        txtNombre.setText(producto.getNombre());
        txtPrecio.setText(String.valueOf(producto.getPrecio()));
        txtStock.setText(String.valueOf(producto.getStock()));
        txtDescripcion.setText(producto.getDescripcion());
    }

    @FXML
    private void guardarProducto() {
        if (!validarCampos()) return;
        try {
            Producto producto = new Producto();
            producto.setNombre(txtNombre.getText().trim());
            producto.setPrecio(Double.parseDouble(txtPrecio.getText().trim()));
            producto.setStock(Integer.parseInt(txtStock.getText().trim()));
            producto.setDescripcion(txtDescripcion.getText().trim());

            productoDAO.guardar(producto);
            cargarProductos();
            limpiarCampos();
            Animaciones.zoomIn(tablaProductos, 300);
        } catch (NumberFormatException ex) {
            mostrarAlerta("Formato inválido", "Precio y stock deben ser números válidos.");
        }
    }

    @FXML
    private void actualizarProducto() {
        Producto producto = tablaProductos.getSelectionModel().getSelectedItem();
        if (producto == null) {
            mostrarAlerta("Selección requerida", "Selecciona un producto de la tabla.");
            return;
        }
        if (!validarCampos()) return;

        try {
            producto.setNombre(txtNombre.getText().trim());
            producto.setPrecio(Double.parseDouble(txtPrecio.getText().trim()));
            producto.setStock(Integer.parseInt(txtStock.getText().trim()));
            producto.setDescripcion(txtDescripcion.getText().trim());

            productoDAO.actualizar(producto);
            cargarProductos();
            limpiarCampos();
            Animaciones.zoomIn(tablaProductos, 300);
        } catch (NumberFormatException ex) {
            mostrarAlerta("Formato inválido", "Precio y stock deben ser números válidos.");
        }
    }

 @FXML
private void eliminarProducto() {
    Producto producto = tablaProductos.getSelectionModel().getSelectedItem();
    if (producto == null) {
        mostrarAlerta("Selección requerida", "Selecciona un producto de la tabla.");
        return;
    }

    Alert confirmacion = new Alert(Alert.AlertType.CONFIRMATION);
    confirmacion.setTitle("Confirmar eliminación");
    confirmacion.setHeaderText(null);
    confirmacion.setContentText("¿Estás seguro de desactivar este producto?\n(No se eliminará si ya fue vendido)");
    
    confirmacion.showAndWait().ifPresent(respuesta -> {
        if (respuesta == ButtonType.OK) {
            try {
                productoDAO.eliminar(producto); // ahora desactiva en vez de borrar
                cargarProductos();
                limpiarCampos();
                mostrarAlerta("Producto desactivado", "El producto fue desactivado correctamente.");
                Animaciones.fadeOut(tablaProductos, 300);
            } catch (Exception ex) {
                ex.printStackTrace();
                mostrarAlerta("Error", "No se pudo desactivar el producto:\n" + ex.getMessage());
            }
        }
    });
}


    @FXML
    private void limpiarCampos() {
        txtNombre.clear();
        txtPrecio.clear();
        txtStock.clear();
        txtDescripcion.clear();
        tablaProductos.getSelectionModel().clearSelection();
    }

    @FXML
    private void exportarProductosPDF() {
        try {
            if (listaProductos.isEmpty()) {
                mostrarAlerta("Sin datos", "No hay productos registrados para exportar.");
                return;
            }

            FileChooser chooser = new FileChooser();
            chooser.setTitle("Guardar listado de productos");
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Archivo PDF", "*.pdf"));
            File destino = chooser.showSaveDialog(btnExportarPDF.getScene().getWindow());
            if (destino == null) return;

            PDFUtil.exportarProductos(destino, listaProductos);

            mostrarAlerta("Éxito", "PDF de productos generado exitosamente:\n" + destino.getAbsolutePath());
        } catch (Exception ex) {
            ex.printStackTrace();
            mostrarAlerta("Error", "No se pudo generar el PDF:\n" + ex.getMessage());
        }
    }

    private boolean validarCampos() {
        try {
            String nombre = txtNombre.getText().trim();
            String precio = txtPrecio.getText().trim();
            String stock = txtStock.getText().trim();
            String descripcion = txtDescripcion.getText().trim();

            if (nombre.length() < 2) {
                mostrarAlerta("Nombre inválido", "El nombre debe tener al menos 2 caracteres.");
                return false;
            }
            if (Double.parseDouble(precio) < 0) {
                mostrarAlerta("Precio inválido", "El precio no puede ser negativo.");
                return false;
            }
            if (Integer.parseInt(stock) < 0) {
                mostrarAlerta("Stock inválido", "El stock no puede ser negativo.");
                return false;
            }
            if (descripcion.isEmpty()) {
                mostrarAlerta("Descripción requerida", "La descripción no puede estar vacía.");
                return false;
            }

            return true;
        } catch (NumberFormatException ex) {
            mostrarAlerta("Formato inválido", "Precio y stock deben ser números válidos.");
            return false;
        }
    }

    private void mostrarAlerta(String titulo, String mensaje) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle(titulo);
        alert.setHeaderText(null);
        alert.setContentText(mensaje);
        alert.showAndWait();
    }
}
