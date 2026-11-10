package com.monpoint.notificaciones.model;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;
@Document("notificaciones")
public record Notificacion(@Id String id, String tipo,
        @Field(targetType = FieldType.OBJECT_ID) String productoId, String productoNombre,
        int existenciaResultante, String mensaje, boolean activa, boolean leida,
        Instant fechaCreacion, Instant fechaLectura) {}
