package dao;

import entidades.DetalleVenta;
import java.util.List;

public interface DetalleVentaDAO {
    void guardar(DetalleVenta detalle);
    void actualizar(DetalleVenta detalle);
    void eliminar(DetalleVenta detalle);
    DetalleVenta buscarPorId(int id);
    List<DetalleVenta> listarTodos();
}
