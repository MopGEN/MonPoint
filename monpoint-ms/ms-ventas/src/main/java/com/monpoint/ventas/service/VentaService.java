package com.monpoint.ventas.service;

import com.mongodb.MongoException;
import com.monpoint.ventas.dto.request.VentaRequest;
import com.monpoint.ventas.dto.response.VentaResponse;
import com.monpoint.ventas.event.VentaEventPublisher;
import com.monpoint.ventas.exception.AccesoDenegadoException;
import com.monpoint.ventas.exception.NoAutenticadoException;
import com.monpoint.ventas.exception.VentaInvalidaException;
import com.monpoint.ventas.model.ClienteReplica;
import com.monpoint.ventas.model.LineaVenta;
import com.monpoint.ventas.model.Venta;
import com.monpoint.ventas.repository.ClienteReplicaRepository;
import com.monpoint.ventas.repository.ProductoReplicaRepository;
import com.monpoint.ventas.repository.VentaRepository;
import com.monpoint.ventas.security.UsuarioActual;
import jakarta.validation.Validator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@Service
public class VentaService {
    public static final String CLIENTE_NO_DISPONIBLE =
            "el cliente no está disponible; si acaba de darlo de alta, inténtelo de nuevo en unos segundos";
    private static final int MAX_INTENTOS = 10;
    private final ProductoReplicaRepository productos;
    private final ClienteReplicaRepository clientes;
    private final VentaRepository ventas;
    private final FolioService folios;
    private final VentaEventPublisher publicador;
    private final Validator validator;
    private final TransactionTemplate transaccion;

    public VentaService(ProductoReplicaRepository productos, ClienteReplicaRepository clientes,
                        VentaRepository ventas, FolioService folios, VentaEventPublisher publicador,
                        Validator validator, PlatformTransactionManager transactionManager) {
        this.productos = productos;
        this.clientes = clientes;
        this.ventas = ventas;
        this.folios = folios;
        this.publicador = publicador;
        this.validator = validator;
        this.transaccion = new TransactionTemplate(transactionManager);
        // Cada reintento debe abrir su propia transacción, nunca reutilizar una ya abortada.
        this.transaccion.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public VentaResponse registrar(VentaRequest solicitud, UsuarioActual usuario) {
        if (usuario == null || usuario.id() == null || usuario.id().isBlank()
                || usuario.rol() == null || usuario.rol().isBlank()) {
            throw new NoAutenticadoException();
        }
        if (!"ADMIN".equals(usuario.rol()) && !"VENDEDOR".equals(usuario.rol())) {
            throw new AccesoDenegadoException("Solo ADMIN o VENDEDOR pueden registrar ventas");
        }
        if (solicitud == null || !validator.validate(solicitud).isEmpty()) {
            throw new VentaInvalidaException("La venta requiere líneas con producto y cantidad positiva");
        }
        for (int intento = 1; ; intento++) {
            try {
                return Objects.requireNonNull(transaccion.execute(estado -> registrarEnTransaccion(solicitud)));
            } catch (RuntimeException ex) {
                if (intento >= MAX_INTENTOS || !esConflictoReintentable(ex)) throw ex;
                log.info("evento=REGISTRO_VENTA_REINTENTO intento={} motivo=conflicto_transitorio", intento);
                try {
                    Thread.sleep(ThreadLocalRandom.current().nextLong(10, 50) * intento);
                } catch (InterruptedException interrupcion) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Registro de venta interrumpido", interrupcion);
                }
            }
        }
    }

    private VentaResponse registrarEnTransaccion(VentaRequest solicitud) {
        var lineas = new ArrayList<LineaVenta>();
        var cantidades = new HashMap<String, Integer>();
        for (var entrada : solicitud.lineas()) {
            var producto = productos.findById(entrada.productoId())
                    .filter(p -> p.isActivo())
                    .orElseThrow(() -> new VentaInvalidaException("El producto no está disponible o está inactivo: "
                            + entrada.productoId()));
            // Inventario agrupa productos repetidos con cantidades Int32; evitar desbordar ese contrato.
            try {
                cantidades.merge(producto.getId(), entrada.cantidad(), Math::addExact);
            } catch (ArithmeticException ex) {
                throw new VentaInvalidaException("La cantidad acumulada del producto excede el máximo permitido");
            }
            lineas.add(new LineaVenta(producto.getId(), producto.getNombre(), producto.getPrecio(), entrada.cantidad()));
        }
        ClienteReplica cliente = null;
        if (solicitud.clienteId() != null) {
            cliente = clientes.findById(solicitud.clienteId())
                    .orElseThrow(() -> new VentaInvalidaException(CLIENTE_NO_DISPONIBLE));
        }
        Venta venta = Venta.pendiente(cliente == null ? null : cliente.getId(),
                cliente == null ? null : cliente.getNombre(), lineas, Instant.now());
        venta.asignarFolio(folios.siguiente());
        ventas.insert(venta);
        publicador.publicarVentaCreada(venta.getId(), venta.getFolio(), venta.getClienteId(), venta.getClienteNombre(),
                venta.getTotal(), venta.getFechaCreacion(), venta.getLineas().stream()
                        .map(l -> new VentaEventPublisher.LineaPublicada(l.productoId(), l.nombreProducto(),
                                l.cantidad(), l.precioUnitario())).toList());
        return VentaResponse.de(venta);
    }

    private static boolean esConflictoReintentable(Throwable fallo) {
        for (Throwable causa = fallo; causa != null; causa = causa.getCause()) {
            if (causa instanceof FolioService.CreacionConcurrenteDeContador) return true;
            if (causa instanceof MongoException mongo
                    && mongo.hasErrorLabel(MongoException.TRANSIENT_TRANSACTION_ERROR_LABEL)
                    && !mongo.hasErrorLabel(MongoException.UNKNOWN_TRANSACTION_COMMIT_RESULT_LABEL)) return true;
        }
        return false;
    }
}
