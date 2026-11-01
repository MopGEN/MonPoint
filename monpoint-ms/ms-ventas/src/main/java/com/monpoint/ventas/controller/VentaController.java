package com.monpoint.ventas.controller;

import com.monpoint.ventas.dto.request.VentaRequest;
import com.monpoint.ventas.dto.response.VentaDetalleResponse;
import com.monpoint.ventas.dto.response.VentaResponse;
import com.monpoint.ventas.security.UsuarioActual;
import com.monpoint.ventas.service.VentaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/ventas")
@RequiredArgsConstructor
public class VentaController {
    private final VentaService ventas;

    @PostMapping
    public ResponseEntity<VentaResponse> registrar(@RequestHeader("X-User-Id") String usuarioId,
                                                  @RequestHeader("X-User-Role") String rol,
                                                  @Valid @RequestBody VentaRequest solicitud) {
        return ResponseEntity.accepted().body(ventas.registrar(solicitud, new UsuarioActual(usuarioId, rol)));
    }

    /**
     * Consulta el estado de una venta (actividad 5.2).
     *
     * <p>Es la única forma que tiene el cliente web de saber cómo terminó la saga: el registro responde 202
     * con la venta en PENDIENTE y la resolución llega después por mensajería, no por esa respuesta HTTP.
     *
     * <p><b>{@code no-store} no es opcional.</b> La caja va a consultar este recurso repetidamente mientras
     * la venta está PENDIENTE. Sin la cabecera, el navegador o cualquier intermediario puede servir la
     * primera respuesta —PENDIENTE— a las consultas siguientes, y el sondeo nunca vería el cambio a
     * CONFIRMADA: la venta se habría cobrado y la pantalla seguiría esperando. {@code no-store} es más
     * estricto que {@code no-cache} y es lo que corresponde: esta respuesta no debe quedar escrita en
     * ningún disco, ni siquiera para revalidarla.
     */
    @GetMapping("/{id}")
    public ResponseEntity<VentaDetalleResponse> consultar(@RequestHeader("X-User-Id") String usuarioId,
                                                          @RequestHeader("X-User-Role") String rol,
                                                          @PathVariable String id) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(ventas.consultar(id, new UsuarioActual(usuarioId, rol)));
    }
}
