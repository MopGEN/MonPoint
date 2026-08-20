package dao;

import entidades.Proveedor;
import java.util.List;

public interface ProveedorDAO {
    void guardar(Proveedor proveedor);
    void actualizar(Proveedor proveedor);
    void eliminar(Proveedor proveedor);
    Proveedor buscarPorId(int id);
    List<Proveedor> listarTodos();
}