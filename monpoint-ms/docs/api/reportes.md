# Reportes — siete endpoints

[Índice](README.md) · [Seguridad y ticket](seguridad.md) · [README del servicio](../../ms-reportes/README.md)

Prefijo `/reportes`. Gateway exige JWT; el controlador recibe identidad y
rol como enum. Las consultas leen proyecciones locales de RabbitMQ, sin HTTP
a otros servicios. Un dato ausente o antiguo en reportes no prueba su ausencia
en el servicio de origen.

| Método y ruta | Entrada y acceso por Gateway | Éxito | Errores específicos |
|---|---|---|---|
| `GET /reportes/configuracion` | Ambos roles | 200 ConfiguracionResponse | 404 si aún no existe |
| `PUT /reportes/configuracion` | ADMIN; ConfiguracionRequest | 200, crea o actualiza el único documento | 400 campos; 403 |
| `GET /reportes/ventas/agregado` | ADMIN; periodo, desde, hasta obligatorios | 200 AgregadoVentasResponse | 400 parámetros/rango/tope de puntos; 403 |
| `GET /reportes/ventas/{id}/ticket` | Ambos; id de venta; sin regla de rol o propiedad en DocumentoService | 200 PDF | 404 VENTA_NO_DISPONIBLE; 409 CANCELADA |
| `GET /reportes/ventas/historial` | ADMIN; desde y hasta obligatorios | 200 PDF | 400 parámetros/rango; 403 |
| `GET /reportes/productos/listado` | ADMIN | 200 PDF | 403 |
| `GET /reportes/clientes/listado` | ADMIN | 200 PDF | 403 |

La guarda ADMIN de los otros PDF no existe en ticket. Se exige JWT en Gateway
y cabeceras en el controlador, pero DocumentoService.ticket no recibe la
identidad. No documentar propiedad de la venta ni una autorización interna que
no se ejecuta. Ver el análisis de [seguridad](seguridad.md).

## Configuración de empresa

```json
{
  "nombreEmpresa": "Auto Tachi Refacciones y Accesorios",
  "rfc": "XAXX010101000",
  "direccion": "Domicilio de ejemplo",
  "telefono": "3312345678"
}
```

| Campo | Validación en ConfiguracionRequest |
|---|---|
| nombreEmpresa | Obligatorio, no blanco, máximo 120 caracteres |
| rfc | Obligatorio, no blanco; patrón de 3–4 letras (incluye Ñ/ñ y &), 6 dígitos y 3 alfanuméricos; admite minúsculas y espacios al principio/final |
| direccion | Opcional, máximo 200 caracteres |
| telefono | Opcional, máximo 30 caracteres |

Patrón Java expresado como regex, sin escape de una cadena JSON:

```regex
\s*[A-Za-zÑñ&]{3,4}\d{6}[A-Za-z0-9]{3}\s*
```

Es validación sintáctica; no consulta al SAT ni comprueba una fecha real dentro
del RFC. Al guardar se recortan nombre/RFC, el RFC pasa a mayúsculas y los
opcionales blancos se guardan null. Omitir un opcional en PUT lo borra; no
conserva su valor anterior. La respuesta añade fechaActualizacion (Instant)
y no expone el id fijo interno `config`.

En instalación nueva GET devuelve 404 hasta el primer PUT. El PUT responde
200 tanto al crear como al modificar. Los PDF pueden generarse sin empresa
configurada, con el texto «Empresa sin configurar».

## Agregación

Ejemplo: `/reportes/ventas/agregado?periodo=DIA&desde=2026-11-01&hasta=2026-11-30`.
periodo admite exactamente `DIA`, `MES` o `ANIO`, no semana ni `AÑO`.
Desde y hasta tienen formato ISO `AAAA-MM-DD`; ambos son inclusivos en la zona
del negocio. El servicio convierte a `[desde 00:00, hasta+1 00:00)` en
`America/Mexico_City`, valor configurado en `monpoint.reportes.zona-horaria`.
Solo suma CONFIRMADA y utiliza la fecha de resolución proyectada, no la de
creación de la venta.

```json
{
  "periodo": "DIA",
  "desde": "2026-11-01",
  "hasta": "2026-11-02",
  "zonaHoraria": "America/Mexico_City",
  "total": 205.75,
  "puntos": [
    {"inicio": "2026-11-01", "total": 0},
    {"inicio": "2026-11-02", "total": 205.75}
  ]
}
```

Hay un punto por periodo, ordenado y con cero para periodos vacíos. Con MES y
ANIO, inicio es el primer día del periodo; el primero y último pueden cubrir
solo parte del periodo solicitado. Hasta anterior a desde produce 400. Más
de **1000 puntos**, contando ambos extremos de la serie, produce 400: es un
límite del backend en AgregacionVentasService, no solo de la gráfica del cliente.

## PDF y cabeceras

Todas las descargas exitosas devuelven bytes con:

```http
Content-Type: application/pdf
Content-Disposition: inline; filename="ticket-folio-42.pdf"
Cache-Control: no-store
```

Los errores siguen siendo ProblemDetail, no un PDF. El controlador no exige
Accept: application/pdf para resolver la ruta. Se recomienda ese Accept al
cliente y comprobar Content-Type antes de guardar. La cabecera de nombre no
está expuesta por CORS; el cliente web actual construye su nombre de descarga.
Un enlace ordinario no lleva el Bearer que el cliente guarda en memoria.

| Documento | Contenido y límites | Nombre de archivo del servidor |
|---|---|---|
| Ticket | Venta cerrada en la proyección, nombre/precio congelados, folio y total de la venta | `ticket-folio-{folio}.pdf` |
| Historial | Solo confirmadas por fecha de resolución; desde/hasta inclusivos; máximo **366 días** contando ambos; rango vacío genera PDF vacío explicativo | `historial-ventas-{desde}-a-{hasta}.pdf` |
| Productos | Catálogo activo proyectado, nombre/precio; **no existencias**, porque no llegan en ese contrato de producto | `listado-productos-{fecha}.pdf` |
| Clientes | Proyección de clientes en orden de nombre; una baja física no llega como evento y puede seguir apareciendo | `listado-clientes-{fecha}.pdf` |

Los listados PDF se ordenan alfabéticamente con Collator español. El historial
es una exportación PDF, no un endpoint JSON de ventas paginado.

Un ticket responde 404 con `codigo: "VENTA_NO_DISPONIBLE"` si no hay proyección.
Puede ser venta pendiente, evento no recibido o id inexistente: ese 404 no
distingue las causas. Si la proyección está CANCELADA responde 409. Un GET de
venta CONFIRMADA en ms-ventas no garantiza que su ticket ya haya llegado a reportes.

## Fuentes

- Controladores de [configuración](../../ms-reportes/src/main/java/com/monpoint/reportes/controller/ConfiguracionController.java), [agregación](../../ms-reportes/src/main/java/com/monpoint/reportes/controller/ReporteVentasController.java) y [PDF](../../ms-reportes/src/main/java/com/monpoint/reportes/controller/DocumentoController.java).
- [ConfiguracionRequest](../../ms-reportes/src/main/java/com/monpoint/reportes/dto/request/ConfiguracionRequest.java) y [respuestas](../../ms-reportes/src/main/java/com/monpoint/reportes/dto/response/).
- [ConfiguracionService](../../ms-reportes/src/main/java/com/monpoint/reportes/service/ConfiguracionService.java), [AgregacionVentasService](../../ms-reportes/src/main/java/com/monpoint/reportes/service/AgregacionVentasService.java) y [DocumentoService](../../ms-reportes/src/main/java/com/monpoint/reportes/service/DocumentoService.java).
- [Consultas de proyección](../../ms-reportes/src/main/java/com/monpoint/reportes/repository/VentaProyeccionRepository.java) y [errores](../../ms-reportes/src/main/java/com/monpoint/reportes/controller/GlobalExceptionHandler.java).
