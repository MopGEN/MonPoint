# Reportes: revisión del parche e integración con servicios reales

11 de noviembre de 2026. Base `bb529e0` (Clientes fusionado, PR #26).
El archivo recibido `0004-cliente-reportes.patch` carece de cabecera From:
de autor. `git am` no pudo crear el commit por identidad vacía; se abortó y se
importó el diff como `9474cd1`, dejando constancia de su procedencia sin
inventar una autoría. Los cambios de revisión se conservan después de ese commit.

## Alcance y correcciones

La pantalla ADMIN incorpora agregación por día, mes y año, historial PDF,
listados PDF de productos/clientes y configuración de empresa. No incorpora
el ticket por venta ni un endpoint nuevo de listado en ms-ventas. Por tanto,
no se declara que toda la funcionalidad de ms-reportes esté en la interfaz.

1. **Primer registro de empresa.** GET configuracion devuelve 404 cuando no
   existe. El parche trataba ese caso como error y ocultaba el único botón
   capaz de abrir el formulario. Ahora permite registrar la primera empresa;
   los demás errores siguen su tratamiento normal.
2. **Fechas de los datos.** La descripción de la gráfica seguía el borrador de
   los controles aunque el usuario no hubiera consultado. Ahora usa desde/hasta
   de la respuesta y muestra ese rango también en texto visible. Editar el
   borrador no atribuye fechas nuevas a datos anteriores.
3. **Límites independientes.** La agregación admite 1.000 puntos en el servidor;
   el cliente elige 370 como límite visual. El historial tiene un máximo remoto
   diferente: 366 días inclusivos, incluso si la gráfica se agrupa por año.
   El historial explica y bloquea fechas inválidas o demasiado largas antes de
   descargar. También se rechaza una granularidad desconocida y se corrige el
   tratamiento JavaScript de los años menores que 100.
4. **Contrato de configuración.** El DTO sí tiene @Pattern para RFC y máximos
   de 120/200/30 caracteres para nombre/dirección/teléfono. Se corrigió el
   comentario que negaba esas validaciones y se añadieron los máximos a los
   controles correspondientes. El RFC se valida en el servidor; la pantalla
   presenta su error por campo sin duplicarlo como aviso general.
5. **Descarga interrumpida.** Además del fallo inicial de fetch, se traduce el
   fallo al leer el cuerpo en un error de red legible. Un Blob incompleto no
   llega a crear una descarga exitosa. AbortError conserva su identidad.

No cambian backend, dependencias ni package-lock.json. La descarga sigue usando
Bearer en memoria, URL de objeto y revocación después de diez segundos. No se
añade almacenamiento del token ni se modifica CORS.

## Comprobaciones

| Comprobación | Resultado |
|---|---|
| npm test | 79 pruebas: 56 previas, 17 de consulta y 6 de descarga; sin fallos ni omitidas |
| Consulta con TZ=UTC y TZ=America/Mexico_City | 17 pruebas correctas en cada zona |
| npm run build | Correcta, Vite 6.4.5 |
| Reportes con Gateway simulado | Rangos, gráfica/tabla, configuración 404, errores por campo, descarga y guarda por historial |
| Clientes, Productos y Notificaciones simulados | Tres regresiones correctas |
| Gateway y ms-reportes reales | Configuración, proyecciones, gráfica, tres PDF y permisos: PASS |

Los seis casos de descarga comprueban Bearer/bytes/señal, ProblemDetail 403,
expiración 401, fallo inicial de conexión, cancelación y corte al leer el cuerpo.
Son pruebas aisladas del cliente HTTP; no se presentan como cortes físicos de
la red real. La prueba simulada usa un cuerpo que empieza por %PDF, sin afirmar
que constituya un documento válido. Los PDF reales se verificaron por separado.

El recorrido simulado conserva el token al volver por historial. Comprueba que
la entrada anterior apunta a /reportes y observa ese destino en el estado del
evento popstate, con redirección a Caja, sesión todavía abierta y sin nuevas consultas
de agregado. No utiliza una recarga que borraría el token y probaría otro caso.

## Recorrido real conservado

Navegador automatizado Linux, Vite en localhost:5173 y Gateway en localhost:8080,
sin intercepciones ni proxy de desarrollo. Node 24.19.0 y Chromium/Playwright.
La ejecución completa fue de **09:41:56 a 09:42:00 de Ciudad de México**
(`15:41:56–15:42:00Z`). [resultado.json](resultado.json) conserva estados,
respuestas, identificadores y hashes de los PDF, sin tokens ni contraseñas.

1. GET configuración **200**; abrir edición desde la pantalla. RFC SIN-FORMATO
   produce **400** real con campos.rfc, aria-invalid y ningún aviso general
   duplicado. Corregir a xaxx010101000 permite PUT **200** y se devuelve
   XAXX010101000. Los PDF incluyen la empresa guardada.
2. Crear producto de prueba a 41.15, existencia 10 y umbral 5, más cliente.
   Registrar dos unidades: POST **202**, folio **56 CONFIRMADA**, total **82.30**.
   El agregado del 11 de noviembre aumenta de **82.30 a 164.60**.
3. Registrar 99 unidades del mismo producto: folio **57 CANCELADA**, importe
   **4073.85**. El ticket por API responde **409**, acreditando que el cierre
   cancelado llegó a reportes; el agregado posterior conserva **164.60**.
4. Consultar desde la pantalla el día 11 y contrastar la respuesta con la API.
   Cambiar Desde sin consultar conserva la descripción del resultado. Consultar
   del 1 al 3 de enero de 2000 devuelve tres puntos en cero, presentes tanto
   en el SVG como en su tabla. No se infiere que cualquier rango esté vacío.
5. Descargar desde los tres botones: historial **1894 bytes**, productos
   **1692 bytes** y clientes **1858 bytes**. Se guardan los archivos originales;
   pdftotext pudo leer empresa y registros. El historial conserva las dos
   confirmadas de la revisión, total 164.60, sin el importe cancelado 4073.85.
6. Content-Disposition existe al leer la respuesta fuera del navegador, pero
   JavaScript recibe null por CORS. Los nombres descargados son los construidos
   por el cliente. No se afirma que se haya leído esa cabecera en la pantalla.
7. Un rango 2020–2026 consultado por año es válido para la gráfica y deja el
   historial bloqueado. Pedir ese historial directamente devuelve **400** por
   366 días. La agregación diaria directa devuelve **400** por más de 1.000 puntos.
8. A 390x844, documento sin desbordamiento horizontal. Con un VENDEDOR nuevo no
   se ofrece Reportes. Usando su token real, agregado y los tres PDF responden
   **403** con ProblemDetail; configuración permite GET **200** y rechaza PUT
   **403**. Se conservan los cuerpos, no solamente los códigos.

No hubo errores JavaScript. Al terminar se restauraron por PUT los campos de
configuración anteriores, con **200**; su fecha de actualización cambia. Las
ventas, clientes, productos y vendedor de prueba se conservan. Repetir el guion
crea otro juego de datos y altera los totales.

## Intentos anteriores y entorno

- [Primer intento](intentos/01-sin-vite.json): falló por conexión rechazada al
  puerto 5173, antes del login. Se arrancó Vite; no es un hallazgo del sistema.
- [Segundo intento](intentos/02-comparacion-texto-pdf.json): observó GET
  configuración **404**, registro inicial **200**, folios **54/55** confirmada/
  cancelada y el primer incremento de **0 a 82.30**. Se detuvo al comparar el
  nombre del cliente contra texto PDF que lo partía en dos líneas. Se corrigió
  solo la comparación, normalizando espacios. Este intento no acredita los
  pasos posteriores que no ejecutó. Se conserva su captura del RFC inválido.
- La ejecución completa posterior usa folios **56/57** y parte de los 82.30
  dejados por ese intento. No es una ejecución sobre volúmenes nuevos ni se
  confunde con los totales del expediente manual de Windows.
- Se añadió ms-reportes al conjunto de nube ya existente usando una imagen
  local preexistente. No se reconstruyó Java en esta revisión. El código fuente
  de ms-reportes no difiere entre 884aa00 y bb529e0; [entorno.json](entorno.json)
  identifica imagen, JAR y estado observado. No se reiniciaron los otros diez
  contenedores ni se borraron volúmenes.
- Las proyecciones verificadas proceden de eventos nuevos emitidos después del
  arranque de reportes. No se presupone recuperación de eventos anteriores a
  la declaración de sus colas. La empresa registrada en el segundo intento
  permanece porque antes no existía y la API no dispone de DELETE.

## Límites

- Esta revisión cubre agregación, tres exportaciones y configuración. El ticket
  confirmado no tiene un botón nuevo en Caja ni se ha probado aquí su descarga.
  El ticket cancelado se usó únicamente como observación de su proyección.
- No se incorpora historial de ventas paginado en ms-ventas. El PDF existente
  es una exportación de confirmadas, no esa pantalla nueva.
- No es una auditoría completa de todos los validadores, de accesibilidad ni
  de carga. La tabla accesible y la ausencia de desbordamiento son las
  comprobaciones concretas descritas; no una certificación general.
- No se recompiló el cliente nginx ni se verificó el paquete Windows. Tampoco
  se modifica Asana, se declaran controles cerrados o se toca el expediente 6.1.
- La configuración compartida y las proyecciones no tienen actualización en
  tiempo real ni control optimista nuevo de concurrencia. La guarda de escritura
  evita duplicados dentro de esta instancia; no garantiza idempotencia HTTP.

`fuentes.json` identifica los archivos de esta revisión. `SHA256.json` conserva
los bytes de los archivos de este expediente, excluido el propio manifiesto.
