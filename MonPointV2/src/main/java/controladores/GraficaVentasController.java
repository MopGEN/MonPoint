package controladores;

import dao.VentaDAO;
import dao.VentaDAOImpl;
import entidades.Usuario;
import entidades.Venta;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.chart.*;
import javafx.scene.control.ComboBox;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ToggleGroup;
import javafx.stage.Stage;
import java.io.IOException;
import controladores.DashboardController;
import java.io.IOException;

import utils.Animaciones;
import utils.SesionUsuario;
import javafx.scene.layout.BorderPane;

import java.time.format.DateTimeFormatter;
import java.time.temporal.WeekFields;
import java.util.*;
import java.util.stream.Collectors;

public class GraficaVentasController {

     @FXML
     private ComboBox<String> cmbRango;
     @FXML
     private ToggleGroup chartTypeGroup;
     @FXML
     private RadioButton rbBar;
     @FXML
     private RadioButton rbPie;
     @FXML
     private BarChart<String, Number> chartVentas;
     @FXML
     private CategoryAxis xAxis;
     @FXML
     private NumberAxis yAxis;
     @FXML
     private PieChart pieChart;

     private final VentaDAO ventaDAO = new VentaDAOImpl();

     @FXML
     private void initialize() {
          if (!accesoPermitido()) {
               redireccionarAlDashboard();
               return;
          }

          configurarControles();
          Platform.runLater(() -> {
               Animaciones.fadeIn(chartVentas, 500);
               actualizarGrafico();
          });
     }

     private boolean accesoPermitido() {
          return SesionUsuario.getUsuarioActual() != null;
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

     private void configurarControles() {
          cmbRango.getItems().setAll("Día", "Semana", "Mes", "Año");
          cmbRango.setValue("Mes");
          cmbRango.setOnAction(e -> actualizarGrafico());
          chartTypeGroup.selectedToggleProperty().addListener((obs, o, n) -> actualizarGrafico());
     }

     private void actualizarGrafico() {
          List<Venta> ventas = ventaDAO.listarTodasVentas();
          String rango = cmbRango.getValue();

          Map<String, Double> agrupados = ventas.stream()
                  .collect(Collectors.groupingBy(
                          v -> formatearPeriodo(v, rango),
                          Collectors.summingDouble(Venta::getTotal)
                  ));

          List<String> etiquetas = new ArrayList<>(agrupados.keySet());
          etiquetas.sort(Comparator.naturalOrder());

          if (rbBar.isSelected()) {
               mostrarGraficoBarra(etiquetas, agrupados);
          } else {
               mostrarGraficoPastel(etiquetas, agrupados);
          }
     }

     private void mostrarGraficoBarra(List<String> etiquetas, Map<String, Double> agrupados) {
          pieChart.setVisible(false);
          chartVentas.setVisible(true);
          chartVentas.getData().clear();

          XYChart.Series<String, Number> serie = new XYChart.Series<>();
          serie.setName("Ingresos");

          for (String etiqueta : etiquetas) {
               serie.getData().add(new XYChart.Data<>(etiqueta, agrupados.get(etiqueta)));
          }
          chartVentas.setData(FXCollections.observableArrayList(serie));
     }

     private void mostrarGraficoPastel(List<String> etiquetas, Map<String, Double> agrupados) {
          chartVentas.setVisible(false);
          pieChart.setVisible(true);
          pieChart.getData().clear();

          List<PieChart.Data> datosPie = etiquetas.stream()
                  .map(et -> new PieChart.Data(et, agrupados.get(et)))
                  .collect(Collectors.toList());

          pieChart.setData(FXCollections.observableArrayList(datosPie));
     }

     private String formatearPeriodo(Venta v, String rango) {
          switch (rango) {
               case "Día":
                    return v.getFecha().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
               case "Semana":
                    WeekFields wf = WeekFields.ISO;
                    int semana = v.getFecha().get(wf.weekOfWeekBasedYear());
                    int anio = v.getFecha().getYear();
                    return "W" + semana + "-" + anio;
               case "Año":
                    return String.valueOf(v.getFecha().getYear());
               case "Mes":
               default:
                    return v.getFecha().format(DateTimeFormatter.ofPattern("MMM yyyy"));
          }
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
