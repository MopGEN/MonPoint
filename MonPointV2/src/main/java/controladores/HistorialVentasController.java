package controladores;

import dao.VentaDAO;
import dao.VentaDAOImpl;
import entidades.Usuario;
import entidades.Venta;
import javafx.application.Platform;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import utils.Animaciones;
import utils.PDFUtil;
import utils.SesionUsuario;
import javafx.scene.layout.BorderPane;
import controladores.DashboardController;
import java.io.IOException;

import java.io.File;
import java.time.LocalDate;
import java.util.List;

public class HistorialVentasController {

     @FXML
     private TableView<Venta> tablaVentas;
     @FXML
     private TableColumn<Venta, Integer> colId;
     @FXML
     private TableColumn<Venta, String> colCliente;
     @FXML
     private TableColumn<Venta, String> colFecha;
     @FXML
     private TableColumn<Venta, Double> colTotal;
     @FXML
     private Label lblPagina;
     @FXML
     private Button btnAnterior;
     @FXML
     private Button btnSiguiente;
     @FXML
     private Button btnExportarPDF;
     @FXML
     private DatePicker dpInicio;
     @FXML
     private DatePicker dpFin;

     private final VentaDAO ventaDAO = new VentaDAOImpl();
     private final ObservableList<Venta> listaVentas = FXCollections.observableArrayList();

     private static final int ELEMENTOS_POR_PAGINA = 10;
     private int paginaActual = 0;
     private int totalPaginas = 0;
     private List<Venta> ventasFiltradas;

     @FXML
     private void initialize() {
          if (!accesoPermitido()) {
               redireccionarAlDashboard();
               return;
          }

          colId.setCellValueFactory(c -> new SimpleIntegerProperty(c.getValue().getId()).asObject());
          colCliente.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getCliente().getNombre()));
          colFecha.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getFecha().toString()));
          colTotal.setCellValueFactory(c -> new SimpleDoubleProperty(c.getValue().getTotal()).asObject());

          cargarVentas();
          Platform.runLater(() -> Animaciones.fadeIn(tablaVentas, 500));
     }

     private boolean accesoPermitido() {
          Usuario usuario = SesionUsuario.getUsuarioActual();
          return usuario != null;
     }

     private void redireccionarAlDashboard() {
          mostrarAlerta("Acceso Denegado", "No tienes permiso para acceder a esta sección.");

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

     private void cargarVentas() {
          listaVentas.clear();
          ventasFiltradas = ventaDAO.listarTodasVentas();
          listaVentas.addAll(ventasFiltradas);

          totalPaginas = (int) Math.ceil((double) listaVentas.size() / ELEMENTOS_POR_PAGINA);
          paginaActual = 0;
          actualizarVista();
     }

     private void actualizarVista() {
          int inicio = paginaActual * ELEMENTOS_POR_PAGINA;
          int fin = Math.min(inicio + ELEMENTOS_POR_PAGINA, listaVentas.size());

          tablaVentas.setItems(FXCollections.observableArrayList(listaVentas.subList(inicio, fin)));
          lblPagina.setText("Página " + (paginaActual + 1) + " de " + (totalPaginas == 0 ? 1 : totalPaginas));

          btnAnterior.setDisable(paginaActual == 0);
          btnSiguiente.setDisable(paginaActual >= totalPaginas - 1);
     }

     @FXML
     private void paginaAnterior() {
          if (paginaActual > 0) {
               paginaActual--;
               actualizarVista();
          }
     }

     @FXML
     private void paginaSiguiente() {
          if (paginaActual < totalPaginas - 1) {
               paginaActual++;
               actualizarVista();
          }
     }

     @FXML
     private void exportarHistorial() {
          try {
               if (ventasFiltradas == null || ventasFiltradas.isEmpty()) {
                    mostrarAlerta("Sin registros", "No hay ventas registradas para exportar.");
                    return;
               }

               FileChooser chooser = new FileChooser();
               chooser.setTitle("Guardar Historial de Ventas");
               chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Archivo PDF", "*.pdf"));
               File destino = chooser.showSaveDialog(tablaVentas.getScene().getWindow());
               if (destino == null) {
                    return;
               }

               PDFUtil.exportarHistorialVentas(destino, ventasFiltradas);

               mostrarAlerta("Éxito", "Historial exportado correctamente:\n" + destino.getAbsolutePath());
          } catch (Exception ex) {
               ex.printStackTrace();
               mostrarAlerta("Error", "No se pudo exportar el historial:\n" + ex.getMessage());
          }
     }

     @FXML
     private void filtrarPorFechas() {
          try {
               LocalDate inicio = dpInicio.getValue();
               LocalDate fin = dpFin.getValue();

               if (inicio == null || fin == null) {
                    mostrarAlerta("Campos requeridos", "Debes seleccionar ambas fechas para buscar.");
                    return;
               }
               if (inicio.isAfter(fin)) {
                    mostrarAlerta("Error de rango", "La fecha de inicio debe ser antes que la fecha final.");
                    return;
               }

               listaVentas.clear();
               ventasFiltradas = ventaDAO.listarVentasPorRangoFecha(inicio, fin);
               listaVentas.addAll(ventasFiltradas);

               totalPaginas = (int) Math.ceil((double) listaVentas.size() / ELEMENTOS_POR_PAGINA);
               paginaActual = 0;
               actualizarVista();
          } catch (Exception e) {
               e.printStackTrace();
               mostrarAlerta("Error", "Ocurrió un error al filtrar ventas.");
          }
     }

     private void mostrarAlerta(String titulo, String mensaje) {
          Alert alerta = new Alert(Alert.AlertType.INFORMATION);
          alerta.setTitle(titulo);
          alerta.setHeaderText(null);
          alerta.setContentText(mensaje);
          alerta.showAndWait();
     }

     @FXML
     private void volverAVentas() {
          try {
               FXMLLoader loader = new FXMLLoader(getClass().getResource("/vista/dashboard.fxml"));
               Parent root = loader.load();

               // ✅ obtener el controlador correcto
               DashboardController controlador = loader.getController();
               controlador.mostrarPestaniaVentas();

               // ✅ cambiar la escena
               Stage stage = SesionUsuario.getStagePrincipal();
               if (stage != null) {
                    Scene escena = new Scene(root);
                    stage.setScene(escena);
                    stage.setTitle("MonPoint - Panel Principal");
                    stage.show();
               }
          } catch (IOException e) {
               e.printStackTrace();
          }
     }


}
