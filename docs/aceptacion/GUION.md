# Repetir la aceptación de caja

Usar un entorno de desarrollo aislado. El guion crea ventas y detiene temporalmente
inventario; conservar los folios y restaurar el servicio al terminar.

## Preparación en Windows

1. Desde `C:\Users\osber\MonPoint`, actualizar la revisión que se desea probar.
2. En `monpoint-ms\infra`, verificar `.env` y ejecutar `docker compose up -d --build`.
   Esperar a que los servicios estén saludables y registrados en Eureka.
3. Para la revisión original de 5.5, desde `monpoint-ms\cliente-web` ejecutar
   `npm.cmd ci` y `npm.cmd run dev`. Abrir `http://localhost:5173`.
   Cuando se integre 5.6, usar la dirección del cliente nginx indicada en su README.
4. Iniciar sesión con el usuario de prueba. Verificar que el navegador carga
   catálogo y clientes sin errores CORS. No guardar contraseñas ni tokens en la evidencia.
5. Preparar un producto exclusivo para esta prueba: precio 41.15, existencia 10,
   umbral 0. Esperar su réplica en ventas. Usar un nombre único por ejecución.

## Tres desenlaces

| Caso | Pasos | Resultado esperado |
| --- | --- | --- |
| Confirmada | Agregar tres unidades y cobrar; observar el estado intermedio y anotar el folio | En proceso con folio → confirmada, total del servidor $123.45, carrito vacío, existencia 7 |
| Cancelada | Nueva venta con 20 unidades del mismo producto | Cancelada, motivo de existencia insuficiente en español, carrito editable conservado, línea señalada mediante productoIdRechazado, existencia 7 |
| Sin respuesta del sondeo | Desde infra: `docker compose stop ms-inventario`; cambiar la cantidad a 2 y cobrar | POST 202 con folio; después de unos 10 segundos, folio en seguimiento y caja disponible; backend PENDIENTE |
| Recuperación | `docker compose start ms-inventario`; esperar recuperación y pulsar Consultar de nuevo | Mismo folio confirmado, total $82.30, existencia final 5 |

Durante el cobro comprobar que doble clic no produce un segundo POST y que
las consultas de estado no se solapan. Guardar captura de cada desenlace,
folio, total, existencia, revisión Git y fecha. No exportar un HAR sin quitar
credenciales y cabeceras de autorización.

Al terminar, comprobar con `docker compose ps` que inventario volvió a healthy.
No volver a cobrar una operación pendiente ni borrar volúmenes para limpiar la prueba.

## POST sin respuesta — pendiente del recorrido real

En una prueba controlada, retener la respuesta de POST /ventas durante más de
10 segundos después de que el servidor la reciba. Debe quedar un aviso sin folio,
la caja disponible y ninguna repetición automática. Verificar manualmente la
operación con los registros del entorno antes de intentar otro cobro. Este caso
requiere inyección de fallo de red: detener inventario no lo reproduce.
