package utils;

import dao.ConfiguracionDAO;
import dao.ConfiguracionDAOImpl;
import entidades.Configuracion;

public class ConfiguracionUtil {

    private static final ConfiguracionDAO configuracionDAO = new ConfiguracionDAOImpl();
    private static Configuracion cache = null;

    public static Configuracion obtenerConfiguracion() {
        if (cache == null) {
            cache = configuracionDAO.obtener();
        }
        return cache;
    }

    public static void recargar() {
        cache = configuracionDAO.obtener();
    }
}
