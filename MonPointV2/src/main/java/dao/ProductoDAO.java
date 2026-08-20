package dao;

import entidades.Producto;
import java.util.List;

public interface ProductoDAO {
    void guardar(Producto producto);
    void actualizar(Producto producto);
    void eliminar(Producto producto);
    Producto buscarPorId(int id);
    List<Producto> listarTodos();
}
