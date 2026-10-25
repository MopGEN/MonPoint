# ms-ventas — réplica local (actividad 3.6)

Los consumidores de `ventas.productos` y `ventas.clientes` actualizan las
réplicas y registran `eventoId` en una misma transacción de MongoDB.

- Un identificador ya procesado no modifica la réplica.
- Un evento estrictamente anterior no modifica la réplica, pero se registra.
- Si cualquiera de las fechas es nula, o ambas son iguales, se aplica el evento.
- Los tres eventos de producto usan el mismo recorrido, incluida la baja con
  `activo=false`. Si `activo` está ausente o es nulo, el mensaje falla.
- Las excepciones salen del consumidor. La configuración de AMQP realiza hasta
  tres intentos en total y después envía el mensaje a la cola de fallidos.

Las réplicas no almacenan stock ni correo, teléfono o dirección del cliente.
ms-clientes todavía no publica bajas; los clientes eliminados pueden permanecer
en la réplica hasta que se acuerde ese contrato entre servicios.

## Pruebas

Desde este directorio, con Java 21 y Maven:

```powershell
mvn test
```

Las pruebas unitarias no necesitan Docker. Para incluir las pruebas de
transacción, iniciar MongoDB con el Compose de `../infra` y definir
`MONGO_VENTAS_TEST_URI` con la URI autenticada de una base de desarrollo
aprovisionada mediante los guiones de `infra/mongo-init`. Al conectar desde
Windows, la URI usa `localhost:27017`, `replicaSet=rs0`,
`authSource=ventas_db` y `directConnection=true`.

```powershell
# Definir MONGO_VENTAS_TEST_URI en el entorno sin versionar la credencial.
mvn test
```

Sin esa variable, `ReplicasTransaccionTest` se omite explícitamente. Con ella,
comprueba el rollback de creación y actualización de ambas réplicas ante un
conflicto real del índice único de eventos, la idempotencia, el almacenamiento
`Decimal128` y la ausencia de campos adicionales. Cada prueba elimina únicamente
los documentos de sus identificadores aleatorios; no borra colecciones.

La configuración compartida y el aviso sobre `MONGO_VENTAS_PASSWORD` están en
[`../infra/README.md`](../infra/README.md).
