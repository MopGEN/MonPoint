package controladores;

import dao.*;
import entidades.*;
import javafx.application.Platform;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.StackPane;
import javafx.stage.FileChooser;
import org.hibernate.Hibernate;
import org.hibernate.Session;
import utils.Animaciones;
import utils.HibernateUtil;
import utils.PDFUtil;

import java.io.File;
import java.time.LocalDateTime;
import java.util.List;

public class VentasController {

    @FXML private TextField txtCodigoProducto, txtDescripcion, txtCantidad, txtIdCliente;
    @FXML private Label lblStock, lblClienteInfo, lblTotal;
    @FXML private Button btnAgregar, btnBuscarProducto, btnBuscarCliente, btnGenerarVenta;
    @FXML private Button btnVerHistorial, btnGenerarGrafica, btnExportarPDF, btnExportarHistorialPDF;
    @FXML private TableView<DetalleVenta> tablaVentaItems;
    @FXML private TableColumn<DetalleVenta, Integer> colIdItem;
    @FXML private TableColumn<DetalleVenta, String> colDescProd;
    @FXML private TableColumn<DetalleVenta, Integer> colCant;
    @FXML private TableColumn<DetalleVenta, Double> colPrecioItem;
    @FXML private TableColumn<DetalleVenta, Double> colSubtotal;
    @FXML private StackPane contenedorVentas;

    private final ProductoDAO productoDAO = new ProductoDAOImpl();
    private final ClienteDAO clienteDAO = new ClienteDAOImpl();
    private final VentaDAO ventaDAO = new VentaDAOImpl();
    private final DetalleVentaDAO detalleDAO = new DetalleVentaDAOImpl();

    private final ObservableList<DetalleVenta> listaItems = FXCollections.observableArrayList();
    private double total = 0.0;

    @FXML
    private void initialize() {
        colIdItem.setCellValueFactory(c -> new SimpleIntegerProperty(c.getValue().getId()).asObject());
        colDescProd.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getProducto().getNombre()));
        colCant.setCellValueFactory(c -> new SimpleIntegerProperty(c.getValue().getCantidad()).asObject());
        colPrecioItem.setCellValueFactory(c -> new SimpleDoubleProperty(c.getValue().getPrecioUnitario()).asObject());
        colSubtotal.setCellValueFactory(c -> new SimpleDoubleProperty(c.getValue().getCantidad() * c.getValue().getPrecioUnitario()).asObject());

        tablaVentaItems.setItems(listaItems);
        Platform.runLater(() -> Animaciones.fadeIn(tablaVentaItems, 500));
    }

    @FXML
    private void btnBuscarProductoAction() {
        try {
            int id = Integer.parseInt(txtCodigoProducto.getText().trim());
            Producto p = productoDAO.buscarPorId(id);
            if (p == null) throw new Exception("Producto no encontrado.");
            txtDescripcion.setText(p.getNombre());
            lblStock.setText(String.valueOf(p.getStock()));
        } catch (Exception ex) {
            mostrarAlerta("Error al buscar producto", ex.getMessage());
        }
    }

    @FXML
    private void btnBuscarClienteAction() {
        try {
            int id = Integer.parseInt(txtIdCliente.getText().trim());
            Cliente c = clienteDAO.buscarPorId(id);
            if (c == null) throw new Exception("Cliente no encontrado.");
            lblClienteInfo.setText(c.getNombre() + " - " + c.getDireccion());
        } catch (Exception ex) {
            mostrarAlerta("Error al buscar cliente", ex.getMessage());
        }
    }

    @FXML
    private void btnAgregarAction() {
        try {
            if (!camposProductoValidos()) {
                mostrarAlerta("Datos inválidos", "Código de producto y cantidad deben ser números válidos.");
                return;
            }

            int idProducto = Integer.parseInt(txtCodigoProducto.getText().trim());
            int cantidad = Integer.parseInt(txtCantidad.getText().trim());

            Producto p = productoDAO.buscarPorId(idProducto);
            if (p == null) throw new Exception("Producto no encontrado.");
            if (cantidad <= 0) throw new Exception("Cantidad debe ser mayor a cero.");
            if (cantidad > p.getStock()) throw new Exception("Stock insuficiente.");

            DetalleVenta dv = new DetalleVenta();
            dv.setProducto(p);
            dv.setCantidad(cantidad);
            dv.setPrecioUnitario(p.getPrecio());

            listaItems.add(dv);
            total += cantidad * p.getPrecio();
            lblTotal.setText(String.format("$ %.2f", total));

            Animaciones.zoomIn(tablaVentaItems, 300);
        } catch (Exception ex) {
            mostrarAlerta("Error al agregar producto", ex.getMessage());
        }
    }

    @FXML
    private void btnGenerarVentaAction() {
        if (listaItems.isEmpty()) {
            mostrarAlerta("Venta vacía", "Agrega al menos un producto a la venta.");
            return;
        }
        try {
            if (txtIdCliente.getText().trim().isEmpty()) {
                throw new Exception("Debes ingresar un ID de cliente.");
            }

            int idCliente = Integer.parseInt(txtIdCliente.getText().trim());
            Cliente cliente = clienteDAO.buscarPorId(idCliente);
            if (cliente == null) throw new Exception("Cliente no encontrado.");

            Venta venta = new Venta();
            venta.setCliente(cliente);
            venta.setFecha(LocalDateTime.now());
            venta.setTotal(total);
            ventaDAO.guardar(venta);

            for (DetalleVenta dv : listaItems) {
                dv.setVenta(venta);
                detalleDAO.guardar(dv);
                Producto producto = dv.getProducto();
                producto.setStock(producto.getStock() - dv.getCantidad());
                productoDAO.actualizar(producto);
            }

            mostrarAlerta("Éxito", "Venta registrada correctamente.");
            limpiarVenta();
        } catch (Exception ex) {
            mostrarAlerta("Error al registrar venta", ex.getMessage());
        }
    }

    @FXML
    private void btnVerHistorialAction() {
        try {
            Parent historial = FXMLLoader.load(getClass().getResource("/vista/historialventas.fxml"));
            contenedorVentas.getChildren().setAll(historial);
            Animaciones.fadeIn(historial, 400);
        } catch (Exception ex) {
            ex.printStackTrace();
            mostrarAlerta("Error", "No se pudo abrir el historial de ventas.");
        }
    }

    @FXML
    private void btnGenerarGraficaAction() {
        try {
            Parent grafica = FXMLLoader.load(getClass().getResource("/vista/graficaVentas.fxml"));
            contenedorVentas.getChildren().setAll(grafica);
            Animaciones.fadeIn(grafica, 400);
        } catch (Exception ex) {
            ex.printStackTrace();
            mostrarAlerta("Error", "No se pudo generar la gráfica de ventas.");
        }
    }

    @FXML
    private void btnExportarPDFAction() {
        try {
            List<Venta> ventas = ventaDAO.listarTodasVentas();
            if (ventas.isEmpty()) {
                mostrarAlerta("Sin ventas", "No hay ventas registradas para exportar.");
                return;
            }

            FileChooser chooser = new FileChooser();
            chooser.setTitle("Guardar Ticket de Venta");
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Archivo PDF", "*.pdf"));
            File destino = chooser.showSaveDialog(btnExportarPDF.getScene().getWindow());
            if (destino == null) return;

            Venta ultimaVenta = ventas.get(0);

            try (Session session = HibernateUtil.getSessionFactory().openSession()) {
                ultimaVenta = session.get(Venta.class, ultimaVenta.getId());
                Hibernate.initialize(ultimaVenta.getDetalleVentas());
            }

            PDFUtil.exportarVenta(destino, ultimaVenta);

            mostrarAlerta("Éxito", "Ticket de venta exportado exitosamente:\n" + destino.getAbsolutePath());
        } catch (Exception ex) {
            ex.printStackTrace();
            mostrarAlerta("Error", "No se pudo exportar el ticket de venta:\n" + ex.getMessage());
        }
    }

    @FXML
    private void btnExportarHistorialPDFAction() {
        try {
            List<Venta> ventas = ventaDAO.listarTodasVentas();
            if (ventas.isEmpty()) {
                mostrarAlerta("Sin ventas", "No hay ventas registradas para exportar.");
                return;
            }

            FileChooser chooser = new FileChooser();
            chooser.setTitle("Guardar Historial de Ventas");
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Archivo PDF", "*.pdf"));
            File destino = chooser.showSaveDialog(btnExportarHistorialPDF.getScene().getWindow());
            if (destino == null) return;

            PDFUtil.exportarHistorialVentas(destino, ventas);

            mostrarAlerta("Éxito", "Historial de ventas exportado exitosamente:\n" + destino.getAbsolutePath());
        } catch (Exception ex) {
            ex.printStackTrace();
            mostrarAlerta("Error", "No se pudo exportar el historial de ventas:\n" + ex.getMessage());
        }
    }

    private void limpiarVenta() {
        listaItems.clear();
        total = 0;
        lblTotal.setText("$ 0.00");
        lblClienteInfo.setText("");
        txtIdCliente.clear();
        txtCodigoProducto.clear();
        txtCantidad.clear();
        txtDescripcion.clear();
        lblStock.setText("0");
    }

    private boolean camposProductoValidos() {
        try {
            Integer.parseInt(txtCodigoProducto.getText().trim());
            Integer.parseInt(txtCantidad.getText().trim());
            return true;
        } catch (NumberFormatException ex) {
            return false;
        }
    }

    private void mostrarAlerta(String titulo, String mensaje) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(titulo);
        alert.setHeaderText(null);
        alert.setContentText(mensaje);
        alert.showAndWait();
    }
}
