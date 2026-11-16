# Ventas — dos endpoints

[Índice](README.md) · [Seguridad](seguridad.md) · [README del servicio](../../ms-ventas/README.md)

Prefijo `/ventas`. JWT en Gateway; VentaService exige identidad no vacía y
compara el rol recibido como cadena con `ADMIN` o `VENDEDOR`. Ambos roles pueden
registrar y consultar cualquier venta. No hay control de propiedad: el modelo
no guarda el usuario que la registró.

| Método y ruta | Entrada | Éxito | Errores específicos |
|---|---|---|---|
| `POST /ventas` | VentaRequest | 202 VentaResponse | 400 líneas/ids/réplicas; 403 rol interno no admitido |
| `GET /ventas/{id}` | Id de venta | 200 VentaDetalleResponse, Cache-Control: no-store | 400 formato de id; 404 inexistente |

Ambas pueden recibir 401 por falta/invalidación del JWT en Gateway. En el
servicio, identidad vacía también devuelve 401. El detalle de la comparación
por cadena frente a enums está en [seguridad](seguridad.md).

## Registrar: cuerpo y validaciones

```json
{
  "clienteId": null,
  "lineas": [
    {"productoId": "640000000000000000000010", "cantidad": 5}
  ]
}
```

- clienteId: opcional/null para venta sin cliente; si viene, 24 caracteres
  hexadecimales y presencia en la réplica local de clientes.
- lineas: lista obligatoria, no vacía; no admite elementos null.
- productoId de cada línea: obligatorio, no blanco, 24 caracteres hexadecimales;
  el producto debe existir y estar activo en la réplica local.
- cantidad: Integer obligatorio y positivo. La deserialización rechaza
  fracciones; la suma de cantidades de un producto repetido no puede desbordar Int32.

Campos ajenos como precio, total, estado o folio se ignoran en el request;
no son autoridad del cliente. Nombres y precios salen de las réplicas. Los
importes se calculan con BigDecimal y se guardan en Decimal128. Las líneas
congelan nombre y precio y la venta congela clienteNombre. Una actualización
posterior del catálogo no reescribe esa venta.

El servicio no consulta inventario/clientes por HTTP. Un producto o cliente
recién creado puede no haber llegado por RabbitMQ: que el id exista en el
servicio dueño no demuestra que su réplica esté lista. Para cliente no disponible
el detail es:

```text
el cliente no está disponible; si acaba de darlo de alta, inténtelo de nuevo en unos segundos
```

## 202 no es confirmación

Respuesta ilustrativa de aceptación:

```json
{"id":"640000000000000000000030","folio":42,"estado":"PENDIENTE"}
```

Se asigna folio e inserta la venta en transacción, y VentaCreada se publica
tras confirmar. Inventario reserva existencias y la respuesta de stock cierra
la saga como CONFIRMADA o CANCELADA. No hay una petición HTTP para confirmar
o cancelar manualmente la venta.

El POST **no es idempotente**. Ante timeout, desconexión, 5xx o respuesta
incompleta no se debe reenviar como si no se hubiera registrado: puede crear
otra venta. Conservar id/folio si se recibieron y consultar el estado por GET.
Los reintentos internos por conflicto de transacción no son una clave de
idempotencia HTTP para el cliente. Sin outbox una venta puede quedar PENDIENTE
por publicación perdida; consultar repetidamente no la resuelve.

## Consultar el detalle

```json
{
  "id": "640000000000000000000030",
  "folio": 42,
  "estado": "CONFIRMADA",
  "clienteId": null,
  "clienteNombre": null,
  "lineas": [
    {
      "productoId": "640000000000000000000010",
      "nombreProducto": "Producto de ejemplo",
      "precioUnitario": 41.15,
      "cantidad": 5,
      "subtotal": 205.75
    }
  ],
  "total": 205.75,
  "motivo": null,
  "productoIdRechazado": null,
  "fechaCreacion": "2026-11-16T18:00:00Z",
  "fechaResolucion": "2026-11-16T18:00:01Z"
}
```

El servidor deriva subtotal para la respuesta; no lo persiste como segunda
fuente de verdad. El cliente muestra subtotal/total recibidos. Estados:
PENDIENTE, CONFIRMADA y CANCELADA. fechaResolucion es null mientras está
pendiente; motivo contiene la frase de cancelación, no el código del evento.
productoIdRechazado puede ser null en ventas históricas. Los dos campos de
cliente son null si no se seleccionó uno. La respuesta usa no-store para que
el sondeo no reutilice una versión PENDIENTE guardada en caché.

No existe `GET /ventas`, paginación, filtro por vendedor/estado ni listado de
pendientes. El [historial PDF de reportes](reportes.md) solo lista confirmadas
en su proyección: no sustituye esas capacidades. Las pantallas de Historial y
Ventas en proceso del prototipo necesitarían ampliar el backend.

## Fuentes

- [VentaController](../../ms-ventas/src/main/java/com/monpoint/ventas/controller/VentaController.java) y [VentaService](../../ms-ventas/src/main/java/com/monpoint/ventas/service/VentaService.java).
- [VentaRequest](../../ms-ventas/src/main/java/com/monpoint/ventas/dto/request/VentaRequest.java), [acuse](../../ms-ventas/src/main/java/com/monpoint/ventas/dto/response/VentaResponse.java) y [detalle](../../ms-ventas/src/main/java/com/monpoint/ventas/dto/response/VentaDetalleResponse.java).
- [Configuración](../../ms-ventas/src/main/resources/application.properties) y [errores](../../ms-ventas/src/main/java/com/monpoint/ventas/controller/GlobalExceptionHandler.java).
- [VentaCreada](../eventos/venta-creada.md) y [cierre de saga](../eventos/venta-cierre.md).
