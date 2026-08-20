package controladores;

import config.SecurityConfig;
import dao.UsuarioDAO;
import dao.UsuarioDAOImpl;
import entidades.Usuario;
import enums.RolUsuario;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.*;
import utils.SesionUsuario;
import utils.ValidadorUtil;

public class UsuariosController {

    @FXML private TableView<Usuario> tablaUsuarios;
    @FXML private TableColumn<Usuario, Integer> colId;
    @FXML private TableColumn<Usuario, String> colNombre;
    @FXML private TableColumn<Usuario, String> colCorreo;
    @FXML private TableColumn<Usuario, String> colRol;

    @FXML private TextField txtNombre;
    @FXML private TextField txtCorreo;
    @FXML private PasswordField txtContrasena;
    @FXML private TextField txtRol;

    @FXML private Label lblErrorNombre;
    @FXML private Label lblErrorCorreo;
    @FXML private Label lblErrorContrasena;
    @FXML private Label lblErrorRol;

    private final UsuarioDAO usuarioDAO = new UsuarioDAOImpl();
    private final ObservableList<Usuario> listaUsuarios = FXCollections.observableArrayList();

    @FXML
    private void initialize() {
        if (!esAdmin()) {
            mostrarAccesoDenegado();
            return;
        }

        colId.setCellValueFactory(cell -> new javafx.beans.property.SimpleIntegerProperty(cell.getValue().getId()).asObject());
        colNombre.setCellValueFactory(cell -> new javafx.beans.property.SimpleStringProperty(cell.getValue().getNombre()));
        colCorreo.setCellValueFactory(cell -> new javafx.beans.property.SimpleStringProperty(cell.getValue().getCorreo()));
        colRol.setCellValueFactory(cell -> new javafx.beans.property.SimpleStringProperty(cell.getValue().getRol().name()));

        tablaUsuarios.setItems(listaUsuarios);
        cargarUsuarios();

        tablaUsuarios.setOnMouseClicked(event -> {
            Usuario usuario = tablaUsuarios.getSelectionModel().getSelectedItem();
            if (usuario != null) {
                mostrarDetallesUsuario(usuario);
            }
        });
    }

    private void mostrarDetallesUsuario(Usuario u) {
        txtNombre.setText(u.getNombre());
        txtCorreo.setText(u.getCorreo());
        txtContrasena.setText(""); // nunca mostrar la contraseña
        txtRol.setText(u.getRol().name());
    }

    private boolean esAdmin() {
        Usuario usuario = SesionUsuario.getUsuarioActual();
        return usuario != null && usuario.getRol() == RolUsuario.ADMIN;
    }

    private void mostrarAccesoDenegado() {
        Alert alerta = new Alert(Alert.AlertType.ERROR);
        alerta.setTitle("Acceso Denegado");
        alerta.setHeaderText(null);
        alerta.setContentText("No tienes permiso para acceder a este módulo.");
        alerta.showAndWait();

        try {
            Parent dashboard = FXMLLoader.load(getClass().getResource("/vista/dashboard.fxml"));
            tablaUsuarios.getScene().setRoot(dashboard);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void cargarUsuarios() {
        listaUsuarios.clear();
        listaUsuarios.addAll(usuarioDAO.listarTodos());
    }

    @FXML
    private void guardarUsuario() {
        limpiarEtiquetasError();
        if (!camposValidos()) return;

        Usuario usuario = new Usuario();
        usuario.setNombre(txtNombre.getText().trim());
        usuario.setCorreo(txtCorreo.getText().trim());

        String contrasena = txtContrasena.getText().trim();
        String hashed = SecurityConfig.hashPassword(contrasena);
        usuario.setContrasena(hashed);

        usuario.setRol(RolUsuario.valueOf(txtRol.getText().trim().toUpperCase()));

        usuarioDAO.guardar(usuario);
        cargarUsuarios();
        limpiarCampos();
    }

    @FXML
    private void actualizarUsuario() {
        limpiarEtiquetasError();

        Usuario usuario = tablaUsuarios.getSelectionModel().getSelectedItem();
        if (usuario != null && camposValidos()) {
            usuario.setNombre(txtNombre.getText().trim());
            usuario.setCorreo(txtCorreo.getText().trim());

            String nuevaContrasena = txtContrasena.getText().trim();
            if (!nuevaContrasena.isEmpty()) {
                // solo actualiza si es una nueva contraseña y aún no está en formato seguro
                if (!nuevaContrasena.startsWith("$2a$")) {
                    usuario.setContrasena(SecurityConfig.hashPassword(nuevaContrasena));
                }
            }

            usuario.setRol(RolUsuario.valueOf(txtRol.getText().trim().toUpperCase()));

            usuarioDAO.actualizar(usuario);
            cargarUsuarios();
            limpiarCampos();
        } else {
            mostrarAlerta("Selección o datos inválidos", "Selecciona un usuario y llena correctamente los campos.");
        }
    }

    @FXML
    private void eliminarUsuario() {
        Usuario usuario = tablaUsuarios.getSelectionModel().getSelectedItem();
        if (usuario != null) {
            usuarioDAO.eliminar(usuario);
            cargarUsuarios();
            limpiarCampos();
        } else {
            mostrarAlerta("Selección requerida", "Selecciona un usuario para eliminarlo.");
        }
    }

    @FXML
    private void limpiarCampos() {
        txtNombre.clear();
        txtCorreo.clear();
        txtContrasena.clear();
        txtRol.clear();
        limpiarEtiquetasError();
        tablaUsuarios.getSelectionModel().clearSelection();
    }

    private void limpiarEtiquetasError() {
        lblErrorNombre.setText("");
        lblErrorCorreo.setText("");
        lblErrorContrasena.setText("");
        lblErrorRol.setText("");
    }

    private boolean camposValidos() {
        boolean validos = true;

        if (txtNombre.getText().trim().isEmpty()) {
            lblErrorNombre.setText("Nombre requerido");
            validos = false;
        }

        String correo = txtCorreo.getText().trim();
        if (correo.isEmpty()) {
            lblErrorCorreo.setText("Correo requerido");
            validos = false;
        } else if (!ValidadorUtil.validarCorreo(correo)) {
            lblErrorCorreo.setText("Correo inválido");
            validos = false;
        }

        String contrasena = txtContrasena.getText().trim();
        if (contrasena.isEmpty()) {
            lblErrorContrasena.setText("Contraseña requerida");
            validos = false;
        } else if (!ValidadorUtil.validarContrasenaSegura(contrasena)) {
            lblErrorContrasena.setText("Debe tener 8 caracteres, mayúsculas, minúsculas, números y símbolos");
            validos = false;
        }

        String rol = txtRol.getText().trim();
        if (rol.isEmpty()) {
            lblErrorRol.setText("Rol requerido");
            validos = false;
        } else if (!ValidadorUtil.validarRol(rol)) {
            lblErrorRol.setText("Rol inválido. Usa ADMIN o VENDEDOR");
            validos = false;
        }

        return validos;
    }

    private void mostrarAlerta(String titulo, String mensaje) {
        Alert alerta = new Alert(Alert.AlertType.WARNING);
        alerta.setTitle(titulo);
        alerta.setHeaderText(null);
        alerta.setContentText(mensaje);
        alerta.showAndWait();
    }
}
