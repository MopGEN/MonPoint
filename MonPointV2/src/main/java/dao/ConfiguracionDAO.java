package dao;

import entidades.Configuracion;

public interface ConfiguracionDAO {
    void guardar(Configuracion configuracion);
    void actualizar(Configuracion configuracion);
    Configuracion obtener();
}
