package dao;

import entidades.Cliente;
import java.util.List;

public interface ClienteDAO {
    void guardar(Cliente cliente);
    void actualizar(Cliente cliente);
    void eliminar(Cliente cliente);
    Cliente buscarPorId(int id);
    List<Cliente> listarTodos();
}
