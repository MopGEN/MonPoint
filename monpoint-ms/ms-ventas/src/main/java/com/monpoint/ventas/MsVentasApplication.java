package com.monpoint.ventas;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * ms-ventas — registro de ventas y saga de confirmación.
 * <p>
 * Es el único servicio que publica y consume a la vez: publica VentaCreada para arrancar la saga y consume la
 * respuesta de ms-inventario para cerrarla. También mantiene réplicas locales del catálogo y de los clientes,
 * que son lo que le permite validar una venta sin llamar a ningún otro servicio por HTTP.
 */
@SpringBootApplication
public class MsVentasApplication {

    public static void main(String[] args) {
        SpringApplication.run(MsVentasApplication.class, args);
    }
}
