package dao;

import entidades.Usuario;
import java.util.List;

public interface UsuarioDAO {
    void guardar(Usuario usuario);
    void actualizar(Usuario usuario);
    void eliminar(Usuario usuario);
    Usuario buscarPorId(int id);
    Usuario buscarPorCorreo(String correo);
    List<Usuario> listarTodos();
}
