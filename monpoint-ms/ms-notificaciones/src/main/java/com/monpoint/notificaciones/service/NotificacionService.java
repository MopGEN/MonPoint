package com.monpoint.notificaciones.service;

import com.monpoint.notificaciones.event.StockBajo;
import com.monpoint.notificaciones.model.EventoProcesado;
import com.monpoint.notificaciones.model.Notificacion;
import com.monpoint.notificaciones.security.UsuarioActual;
import com.monpoint.notificaciones.exception.AccesoDenegadoException;
import com.monpoint.notificaciones.exception.RecursoNoEncontradoException;
import com.mongodb.MongoException;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Service
@Slf4j
public class NotificacionService {
    private static final int MAX_INTENTOS_TRANSACCION = 20;
    private final MongoTemplate mongo;
    private final TransactionTemplate transaccion;

    public NotificacionService(MongoTemplate mongo, PlatformTransactionManager manager) {
        this.mongo = mongo;
        this.transaccion = new TransactionTemplate(manager);
        this.transaccion.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public void recibir(StockBajo evento) {
        validar(evento);
        // Un conflicto revierte TODO y se reintenta con otra transacción/snapshot.
        // El índice de eventoId garantiza idempotencia; el parcial garantiza una sola activa.
        for (int intento = 1; ; intento++) {
            try {
                Boolean aplicado = transaccion.execute(status -> {
                    Query identidad = Query.query(Criteria.where("eventoId").is(evento.eventoId()));
                    if (mongo.exists(identidad, EventoProcesado.class)) return false;
                    mongo.insert(EventoProcesado.de(evento.eventoId(), evento.tipo(), Instant.now()));
                    var d = evento.datos();
                    Query activa = Query.query(Criteria.where("productoId").is(d.productoId()).and("activa").is(true));
                    Update aviso = new Update().set("productoNombre", d.nombre())
                            .set("existenciaResultante", d.existenciaResultante())
                            .set("mensaje", "Existencia baja de " + d.nombre() + ": " + d.existenciaResultante())
                            .set("leida", false).setOnInsert("tipo", "STOCK_BAJO")
                            .setOnInsert("fechaCreacion", Instant.now()).setOnInsert("fechaLectura", null);
                    // La igualdad del filtro aporta productoId y activa al insertar.
                    mongo.findAndModify(activa, aviso, FindAndModifyOptions.options().upsert(true).returnNew(true), Notificacion.class);
                    return true;
                });
                log.info("evento=STOCK_BAJO_{} eventoId={}", Boolean.TRUE.equals(aplicado) ? "APLICADO" : "DESCARTADO", evento.eventoId());
                return;
            } catch (RuntimeException ex) {
                if (intento >= MAX_INTENTOS_TRANSACCION || !reintentable(ex)) throw ex;
                try {
                    Thread.sleep(ThreadLocalRandom.current().nextLong(5, 26));
                } catch (InterruptedException interrupcion) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Procesamiento interrumpido", interrupcion);
                }
            }
        }
    }

    private static boolean reintentable(Throwable ex) {
        for (Throwable causa = ex; causa != null; causa = causa.getCause()) {
            if (causa instanceof DuplicateKeyException) return true;
            if (causa instanceof MongoException mongo && (mongo.hasErrorLabel("TransientTransactionError")
                    || mongo.hasErrorLabel("UnknownTransactionCommitResult"))) return true;
        }
        return false;
    }

    public List<Notificacion> listar(Boolean leida, UsuarioActual usuario) {
        exigirAdmin(usuario);
        return mongo.find(leida == null ? new Query() : Query.query(Criteria.where("leida").is(leida)), Notificacion.class);
    }

    public void leer(String id, UsuarioActual usuario) {
        exigirAdmin(usuario);
        if (!ObjectId.isValid(id)) throw inexistente();
        Query pendiente = Query.query(Criteria.where("_id").is(id).and("leida").is(false));
        Notificacion leida = mongo.findAndModify(pendiente, new Update().set("leida", true).set("activa", false)
                .set("fechaLectura", Instant.now()), Notificacion.class);
        // Repetir el PUT conserva fechaLectura y devuelve 204, incluso si hay ya otro aviso activo.
        if (leida == null && !mongo.exists(Query.query(Criteria.where("_id").is(id)), Notificacion.class)) throw inexistente();
    }

    private static RecursoNoEncontradoException inexistente() {
        return new RecursoNoEncontradoException("La notificación no existe");
    }

    private static void exigirAdmin(UsuarioActual usuario) {
        if (usuario == null || usuario.id() == null || usuario.id().isBlank()) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Se requiere identidad válida");
        if (!usuario.esAdmin()) throw new AccesoDenegadoException("Solo un ADMIN puede consultar o leer notificaciones");
    }

    private static void validar(StockBajo e) {
        if (e == null || e.eventoId() == null || e.eventoId().isBlank() || !"StockBajo".equals(e.tipo())
                || !"1".equals(e.version()) || !"ms-inventario".equals(e.origen()) || e.fecha() == null
                || e.datos() == null || !ObjectId.isValid(e.datos().productoId()) || e.datos().nombre() == null
                || e.datos().nombre().isBlank() || e.datos().existenciaResultante() == null || e.datos().existenciaResultante() < 0)
            throw new IllegalArgumentException("Sobre StockBajo inválido");
        Instant.parse(e.fecha()); // Validación de contrato; NO se trata como versión u orden causal.
    }
}
