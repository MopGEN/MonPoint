package com.monpoint.reportes.service;

import com.monpoint.reportes.dto.response.DocumentoPdf;
import com.monpoint.reportes.exception.AccesoDenegadoException;
import com.monpoint.reportes.exception.RangoInvalidoException;
import com.monpoint.reportes.exception.VentaCanceladaException;
import com.monpoint.reportes.exception.VentaNoDisponibleException;
import com.monpoint.reportes.model.Configuracion;
import com.monpoint.reportes.model.ContraparteProyeccion;
import com.monpoint.reportes.model.EstadoVenta;
import com.monpoint.reportes.model.ProductoProyeccion;
import com.monpoint.reportes.model.TipoContraparte;
import com.monpoint.reportes.model.VentaProyeccion;
import com.monpoint.reportes.pdf.PdfGenerador;
import com.monpoint.reportes.repository.ConfiguracionRepository;
import com.monpoint.reportes.repository.ContraparteProyeccionRepository;
import com.monpoint.reportes.repository.ProductoProyeccionRepository;
import com.monpoint.reportes.repository.VentaProyeccionRepository;
import com.monpoint.reportes.security.UsuarioActual;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.text.Collator;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Function;

/**
 * Documentos PDF (H-051, H-052, H-053). Todo sale de las proyecciones locales y de la configuración:
 * aquí no hay ninguna llamada a otro servicio.
 * <p>
 * El constructor es explícito porque Lombok no copia {@code @Value} a los parámetros del constructor.
 */
@Slf4j
@Service
public class DocumentoService {

    /** Un año, contando el bisiesto. Evita armar en memoria un PDF de años de ventas por un rango absurdo. */
    static final long MAX_DIAS_HISTORIAL = 366;

    private final VentaProyeccionRepository ventas;
    private final ProductoProyeccionRepository productos;
    private final ContraparteProyeccionRepository contrapartes;
    private final ConfiguracionRepository configuracion;
    private final PdfGenerador pdf;
    private final Clock clock;
    private final ZoneId zona;

    public DocumentoService(VentaProyeccionRepository ventas, ProductoProyeccionRepository productos,
                            ContraparteProyeccionRepository contrapartes, ConfiguracionRepository configuracion,
                            PdfGenerador pdf, Clock clock, @Value("${monpoint.reportes.zona-horaria}") ZoneId zona) {
        this.ventas = ventas;
        this.productos = productos;
        this.contrapartes = contrapartes;
        this.configuracion = configuracion;
        this.pdf = pdf;
        this.clock = clock;
        this.zona = zona;
    }

    /**
     * Comprobante de una venta; lo pueden pedir ADMIN y VENDEDOR (el cajero se lo entrega al cliente).
     * <p>
     * Si la venta no está en la proyección puede ser porque sigue PENDIENTE, porque su evento aún no llega o
     * porque no existe: desde aquí no se distinguen, así que se responde 404 con un código propio para que el
     * cliente sepa que puede reintentar (RF-5.8).
     */
    public DocumentoPdf ticket(String ventaId) {
        VentaProyeccion venta = ventas.findById(ventaId).orElseThrow(() -> new VentaNoDisponibleException(
                "La venta todavía no está disponible para reportes. Si se acaba de registrar, "
                        + "espera unos segundos a que se confirme e inténtalo de nuevo."));
        if (venta.getEstado() == EstadoVenta.CANCELADA) {
            throw new VentaCanceladaException("La venta con folio " + venta.getFolio()
                    + " fue cancelada y no tiene comprobante");
        }
        byte[] contenido = pdf.ticket(venta, empresa(), Instant.now(clock));
        log.info("evento=PDF_GENERADO documento=TICKET ventaId={} folio={}", venta.getId(), venta.getFolio());
        return new DocumentoPdf("ticket-folio-" + venta.getFolio() + ".pdf", contenido);
    }

    /** Historial de ventas confirmadas; {@code desde} y {@code hasta} son días completos e inclusivos. */
    public DocumentoPdf historial(LocalDate desde, LocalDate hasta, UsuarioActual usuario) {
        exigirAdmin(usuario, "Solo un ADMIN puede exportar el historial de ventas");
        Objects.requireNonNull(desde, "La fecha 'desde' es obligatoria");
        Objects.requireNonNull(hasta, "La fecha 'hasta' es obligatoria");
        if (hasta.isBefore(desde)) {
            throw new RangoInvalidoException("La fecha 'hasta' no puede ser anterior a 'desde'");
        }
        if (ChronoUnit.DAYS.between(desde, hasta) + 1 > MAX_DIAS_HISTORIAL) {
            throw new RangoInvalidoException("El historial abarca como máximo " + MAX_DIAS_HISTORIAL
                    + " días; divide el periodo en partes");
        }

        // Mismo rango semiabierto que la agregación: [desde 00:00, hasta+1 00:00) en la zona del negocio.
        List<VentaProyeccion> confirmadas = ventas.confirmadasEntre(desde.atStartOfDay(zona).toInstant(),
                hasta.plusDays(1).atStartOfDay(zona).toInstant());
        BigDecimal total = confirmadas.stream().map(VentaProyeccion::getTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        byte[] contenido = pdf.historial(confirmadas, desde, hasta, total, empresa(), Instant.now(clock));
        log.info("evento=PDF_GENERADO documento=HISTORIAL desde={} hasta={} ventas={}", desde, hasta,
                confirmadas.size());
        return new DocumentoPdf("historial-ventas-" + desde + "-a-" + hasta + ".pdf", contenido);
    }

    /** Catálogo vigente, sin productos dados de baja, en orden alfabético. */
    public DocumentoPdf listadoProductos(UsuarioActual usuario) {
        exigirAdmin(usuario, "Solo un ADMIN puede exportar el listado de productos");
        List<ProductoProyeccion> activos = ordenadosPorNombre(productos.findByActivoTrue(),
                ProductoProyeccion::getNombre);
        Instant ahora = Instant.now(clock);
        byte[] contenido = pdf.listadoProductos(activos, empresa(), ahora);
        log.info("evento=PDF_GENERADO documento=LISTADO_PRODUCTOS registros={}", activos.size());
        return new DocumentoPdf("listado-productos-" + LocalDate.ofInstant(ahora, zona) + ".pdf", contenido);
    }

    /** Directorio de clientes en orden alfabético. */
    public DocumentoPdf listadoClientes(UsuarioActual usuario) {
        exigirAdmin(usuario, "Solo un ADMIN puede exportar el listado de clientes");
        List<ContraparteProyeccion> clientes = ordenadosPorNombre(contrapartes.findByTipo(TipoContraparte.CLIENTE),
                ContraparteProyeccion::getNombre);
        Instant ahora = Instant.now(clock);
        byte[] contenido = pdf.listadoClientes(clientes, empresa(), ahora);
        log.info("evento=PDF_GENERADO documento=LISTADO_CLIENTES registros={}", clientes.size());
        return new DocumentoPdf("listado-clientes-" + LocalDate.ofInstant(ahora, zona) + ".pdf", contenido);
    }

    /** Sin configuración el PDF se genera igual, con el aviso «Empresa sin configurar». */
    private Configuracion empresa() {
        return configuracion.findById(Configuracion.ID_UNICO).orElse(null);
    }

    private static void exigirAdmin(UsuarioActual usuario, String mensaje) {
        if (!usuario.esAdmin()) {
            throw new AccesoDenegadoException(mensaje);
        }
    }

    /**
     * Orden alfabético en español: Mongo compara bytes y pondría «Ávila» después de «Zapata».
     * El {@link Collator} se crea en cada llamada porque no es seguro compartirlo entre hilos sin sincronizar.
     */
    private static <T> List<T> ordenadosPorNombre(List<T> elementos, Function<T, String> nombre) {
        Collator collator = Collator.getInstance(Locale.of("es", "MX"));
        return elementos.stream().sorted(Comparator.comparing(nombre, collator)).toList();
    }
}
