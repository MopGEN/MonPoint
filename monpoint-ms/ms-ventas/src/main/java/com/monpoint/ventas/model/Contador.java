package com.monpoint.ventas.model;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "contadores")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Contador {
    public static final String VENTA_FOLIO = "venta_folio";
    @Id private String id;
    private long valor;
}
