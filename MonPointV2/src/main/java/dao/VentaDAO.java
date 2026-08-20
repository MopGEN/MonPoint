package dao;

import entidades.Venta;
import java.time.LocalDate;
import java.util.List;

public interface VentaDAO {
    void guardar(Venta venta);
    void actualizar(Venta venta);
    void eliminar(Venta venta);
    Venta buscarPorId(int id);
    List<Venta> listarTodos();
    List<Venta> listarTodasVentas(); 
    List<Venta> listarVentasOrdenadas(String campo, boolean asc);
    List<Venta> listarVentasPorRangoFecha(LocalDate inicio, LocalDate fin); // 🔥 nuevo método
}
