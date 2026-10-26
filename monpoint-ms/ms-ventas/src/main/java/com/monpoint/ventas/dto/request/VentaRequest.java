package com.monpoint.ventas.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.List;

/** Solo identificadores y cantidades: los nombres e importes salen de las réplicas. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record VentaRequest(String clienteId, @NotEmpty List<@NotNull @Valid LineaRequest> lineas) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record LineaRequest(@NotBlank String productoId, @NotNull @Positive Integer cantidad) { }
}
