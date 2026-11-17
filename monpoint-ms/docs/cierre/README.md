# Documento de cierre — 6.4 / H-093

**Estado: borrador para revisión, 17 de noviembre de 2026.** Base de código:
`4568a17` (árbol `ced8c116bc0d7e16f7dfc9c16f4bc103d52abb2c`), más los cambios
de residuos y semilla de empresa de `8500fb29ba75bb39b810cf284fc69177b9f27eb8`.
Anfitrión previsto: Windows del PO; entrega acordada: **19 de noviembre**.
Los PDF de planeación mantienen su fecha original del 6 de noviembre. Este
documento registra la situación posterior, no modifica aquellos PDF ni Asana.

No es un acta de aprobación del proyecto completo. Distingue implementación,
ejecuciones conservadas y operaciones pendientes en el anfitrión de entrega.

| Criterio literal de H-093, Documento 03 p. 18 | Dónde se desarrolla |
|---|---|
| Describe la arquitectura final, sus decisiones y limitaciones conocidas | Secciones 1–3 |
| Registra lo que quedó fuera del alcance y por qué, con evidencia de ejecución | Secciones 4–6, inventario enlazado y pendientes de la sección 7 |

## 1. Resultado construido

Se migró el flujo de venta del monolito JavaFX de referencia a seis servicios
Java/Spring Boot con bases documentales propias. Se conserva MonPointV2 sin
reescribirlo. El cliente Vue/Pinia consume exclusivamente el Gateway; nginx
sirve el build. Eureka registra destinos HTTP y RabbitMQ transporta los eventos
entre dominios. El Compose reúne once servicios: seis de dominio, Gateway,
Eureka, MongoDB, RabbitMQ y cliente.

| Componente | Responsabilidad y persistencia |
|---|---|
| ms-auth | Usuarios activos, roles, política de contraseña y emisión de JWT; auth_db |
| ms-inventario | Catálogo, existencias, reserva atómica y aviso de umbral; inventario_db |
| ms-ventas | Folio, venta PENDIENTE y resolución por respuesta de stock; réplicas de catálogo; ventas_db |
| ms-clientes | Directorio y eventos de alta/cambio de clientes; clientes_db |
| ms-reportes | Proyecciones, agregación de confirmadas, cuatro clases de PDF y configuración de empresa; reportes_db |
| ms-notificaciones | Consumo de StockBajo y notificación activa/leída; notificaciones_db |

Las bases y usuarios son propios de cada servicio, pero comparten **un servidor
MongoDB**, configurado como replica set de un miembro. No se entrega un clúster
con tolerancia a fallo de nodos. Las transacciones son locales a un servicio;
no hay transacción distribuida. Los importes se calculan con BigDecimal y se
persisten como Decimal128; el cliente calcula solo un estimado.

Entre dominios no hay llamadas HTTP: ventas valida con sus réplicas y reportes
responde desde las suyas. El Gateway sí enruta HTTP y usa Eureka; ese tráfico
de borde no es una llamada de negocio entre dominios. Auth está fuera del bus.
Notificaciones consume **solo StockBajo**, no cierres de ventas.

La interfaz tiene login y seis rutas privadas: Caja, Productos, Clientes,
Reportes, Notificaciones y Usuarios. Incluye permisos visibles, concha clara y
oscura, fuentes locales, catálogo en tarjetas, atajos de Caja y ticket tras
confirmación. El token vive en memoria: recargar el documento pierde la sesión.
El historial PDF y los datos de empresa están dentro de Reportes.

Fuentes de referencia: [API: 27 operaciones](../api/README.md),
[componentes](../arquitectura/renders/01-componentes-actual.svg),
[secuencia con rechazo](../arquitectura/renders/02-saga-venta.svg),
[16 colecciones y tipos embebidos](../arquitectura/03-modelo-documental.md),
[operación y puertos](../api/operacion.md).

## 2. Decisiones y sus costes

### Coreografía, réplica local y consistencia eventual

POST /ventas valida las réplicas, congela nombres/precios y confirma una
transacción con folio y estado PENDIENTE. El 202 acredita aceptación, no venta
confirmada. VentaCreada llega a inventario; StockReservado o StockRechazado
resuelve la venta; el cierre actualiza reportes. StockBajo, cuando corresponde,
alimenta notificaciones de forma independiente.

La elección elimina un orquestador central y consultas HTTP sincrónicas entre
dominios, a cambio de réplicas retrasadas y estados intermedios. Un producto
recién creado puede no estar disponible todavía en ventas. Una confirmada puede
dar 404 al pedir ticket si reportes aún no recibió su proyección. La interfaz
preserva la confirmación y permite reintentar la descarga, sin repetir el POST.

### Sin outbox: el commit y la publicación no son atómicos

El recorte del Documento 01 §7.4 excluyó el outbox por coste y alcance de la
demostración. Ventas publica mediante afterCommit; inventario publica tras
retornar TransactionTemplate.execute, con la transacción ya terminada. En ambos
casos persiste una ventana entre confirmar la base y publicar en RabbitMQ.

Una caída en esa ventana puede dejar PENDIENTE una venta o atrasar una réplica.
Registrar eventoId y enviar excepciones a una cola de fallidos **no recupera una
publicación que nunca ocurrió**. Tampoco una reentrega cuyo marcador ya existe
vuelve a emitir la respuesta perdida. No se afirma entrega exactamente una vez
ni recuperación automática de todo fallo. Se priorizó el flujo normal y la
demostración de reentrega; esta implementación no ofrece durabilidad de
publicación equivalente a un outbox.

### Reserva: por qué el rechazo usa otra transacción

Dentro de una transacción se inserta el marcador de evento y se descuentan las
líneas con actualización condicional. Si alguna línea no admite la reserva, se
revierte **todo**, incluido aquel marcador: no puede quedar un descuento parcial.
Después se registra eventoId en otra transacción y se publica StockRechazado.
Así la reentrega del rechazo ya procesado se descarta sin convertirlo en un
nuevo intento de reserva que pudiera aprobarse con otro stock. Mantener el
marcador dentro de la transacción revertida no lograría esa propiedad.

La segunda transacción tampoco es un outbox: una caída después de su commit y
antes de StockRechazado conserva el mismo límite de publicación. Fuente:
[ReservaStockService](../../ms-inventario/src/main/java/com/monpoint/inventario/service/ReservaStockService.java).

### Idempotencia y fallidos

Los consumidores mantienen sus marcadores por eventoId y sus índices únicos.
La comprobación previa ahorra trabajo; el índice y la transacción protegen la
escritura ante concurrencia. Los reintentos AMQP configurados admiten hasta tres
entregas en total y después derivan a la cola correspondiente de fallidos; para
VentaCreada es `inventario.venta-creada.fallidos`. Un fallo técnico agotado no
equivale a CANCELADA: cancelar requiere el rechazo de negocio.

La idempotencia de eventos no es idempotencia HTTP. POST /ventas no tiene una
clave de solicitud: reenviar un cobro incierto puede crear otra venta. La Caja
bloquea envíos simultáneos y no reintenta automáticamente el POST. El sondeo
tiene un plazo del cliente; agotarlo no cancela la venta en el servidor.

### Autorización y frontera de confianza

El Gateway valida firma/claims/tiempo, elimina las cabeceras X-User-Id y
X-User-Role aportadas por el cliente y las repone desde el JWT. ms-auth vuelve a
validar Bearer y usa SecurityContext/requestMatchers. Los otros cinco servicios
no validan el token: reciben identidad por cabeceras y aplican sus reglas de
servicio, con UsuarioActual donde se utiliza.

No se puede suplantar el rol enviando esas cabeceras **a través del Gateway**.
Sí existe confianza en la red interna: un proceso que alcance directamente un
servicio puede construirlas. La ausencia de puertos publicados de dominio y el
aislamiento son condiciones del diseño, no autenticación criptográfica entre
servicios. No se implementaron mTLS ni una autorización uniforme para toda ruta.
La matriz y las excepciones están en [seguridad](../api/seguridad.md).

### Sesión sin revocación individual

La vigencia por omisión es 86400000 ms, **24 horas**, no una medición de todos
los tokens del anfitrión. Desactivar una cuenta, cambiar rol/contraseña o salir
del cliente no invalida los JWT ya emitidos. No hay lista de denegación,
introspección ni refresh token. Reducir la vigencia solo afecta nuevas emisiones.

La revocación inmediata disponible consiste en rotar JWT_SECRET en auth y
Gateway, retirando todas las instancias que acepten la clave anterior. Es global
y obliga a nuevos login. La [operación en Windows](VERIFICACION-WINDOWS.md)
permanece pendiente hasta conservar su comprobación; este documento no la da
por ejecutada. Aunque se realice, no añade revocación selectiva al producto.

## 3. Defectos y limitaciones que permanecen

| Hecho comprobado | Efecto y decisión de cierre |
|---|---|
| El ticket recibe cabeceras de identidad, pero no pasa UsuarioActual al servicio | Por Gateway exige JWT válido, no es público; internamente no comprueba rol ni pertenencia. Un ADMIN/VENDEDOR con el id puede obtener el ticket. Se declara la excepción, no se inventa una guarda. |
| Venta no persiste autor | Las cuentas nominales identifican accesos; no permiten atribuir ventas ni filtrar por vendedor. Agregarlo exigiría cambiar modelo/contrato. |
| Ventas compara el rol como String | Usa ADMIN/VENDEDOR literales; inventario, clientes, reportes y notificaciones usan enum Rol. Es una divergencia interna; el Gateway rechaza roles desconocidos antes. |
| productoId embebido usa tipos BSON distintos | OBJECT_ID en ms-ventas.LineaVenta y String en ms-reportes.LineaVenta. Se documenta tal cual; no se hizo migración de datos para uniformarlo. |
| Publicación sin outbox | Puede perderse después del commit; no se promete reconciliación automática. |
| Sin GET /ventas ni idempotencia de solicitudes | No hay listado de pendientes para diagnosticar todas las sagas ni recuperación por clave de un POST sin respuesta. |
| Réplicas por eventos | Un consumidor no levantado o una publicación sin cola enlazada puede perder datos; repetir la semilla no reemite altas reutilizadas. |
| Seguridad de despliegue local | HTTP y clave HS256 compartida, sin mTLS ni gestor de secretos. Algunas claves de desarrollo Mongo siguen versionadas; no se certifica cumplimiento íntegro de RNF-06 ni preparación para producción. |
| Marca pública estática del login | Coincide con la configuración de entrega; no sigue cambios de la base. La configuración de empresa se mantiene autenticada. |

Fuentes: [DocumentoController](../../ms-reportes/src/main/java/com/monpoint/reportes/controller/DocumentoController.java),
[Venta](../../ms-ventas/src/main/java/com/monpoint/ventas/model/Venta.java),
[VentaService](../../ms-ventas/src/main/java/com/monpoint/ventas/service/VentaService.java),
[línea de ventas](../../ms-ventas/src/main/java/com/monpoint/ventas/model/LineaVenta.java),
[línea de reportes](../../ms-reportes/src/main/java/com/monpoint/reportes/model/LineaVenta.java).

Las credenciales de fixtures y expedientes anteriores no se barrieron ni se
reescribió el historial público: hacerlo alteraría evidencia y no retiraría las
copias existentes. Se vacían las contraseñas del **entorno Postman vivo**. La
acción que corta JWT anteriores es la rotación en cada instalación, no borrar
un literal de un README. No se infiere que una cuenta histórica siga activa o
que ya esté desactivada en todos los equipos.

Las guías operativas de 6.2, de las semillas y de aceptación publicaron rutas de
usuario del anfitrión. Los archivos vigentes usan rutas relativas al clon.
Las notas de contexto de ventas describen la separación de instalaciones y el
traslado del almacenamiento de Docker sin necesitar rutas personales del equipo.

El criterio depende de la función del archivo, no de su carpeta: una constancia
de lo ocurrido se conserva; un procedimiento reutilizable se generaliza.
Por eso la [guía Windows](../../../docs/aceptacion/GUIA-WINDOWS.md) y el
[guion de Caja](../../../docs/aceptacion/GUION.md) usan también la raíz del clon,
aunque estén bajo docs/aceptacion/.

En el árbol vigente quedan cinco constancias con esas rutas: cuatro JSON de
contenedores y montajes de las dos recolecciones del
[11 de noviembre](../../../docs/aceptacion/6.1-windows-2026-11-11/RESULTADOS.md),
incluidas etiquetas de Compose, y el
[acta del 4 de noviembre](../../../docs/aceptacion/windows-2026-11-04/RESULTADOS.md).
Se mantienen los bytes de esas cinco constancias como referencias históricas.
No se reescribe el historial de main
ni se afirma haber retirado las cadenas de versiones o copias anteriores.

## 4. Alcance recortado, ampliado y no construido

El Documento 01 §7.4 registra el recorte de 234 a 144 puntos y 35 actividades.
Este cierre no modifica aquella suma ni afirma que las ampliaciones posteriores
estuvieran en ella desde el principio.

| Decisión del recorte | Resultado actual y motivo |
|---|---|
| Sin outbox | Se mantiene: coste de implementación y estabilización, con el riesgo declarado arriba. |
| Sin paginación/filtros/orden arbitrario de listados | Se mantiene. El volumen de demostración permite filtros locales; no se presentan como paginación de servidor. |
| Sin proveedores | Se mantiene. No participa en la saga y no se construyó un microservicio ni su gestión en clientes. |
| PDF retirado inicialmente | Reincorporado: ticket, historial de confirmadas y listados de productos/clientes. Lo acreditan los expedientes de Reportes y Usuarios/ticket. |
| Administración web retirada inicialmente | Reincorporadas cinco pantallas además de Caja: Productos, Clientes, Reportes, Notificaciones y Usuarios. Sus expedientes distinguen simulación e integración real. |

También se añadieron concha y tokens del prototipo, temas/fuentes locales,
logotipo e iconos, tarjetas/pasos/atajos de Caja y semilla nominal/catálogo. La
configuración opcional de empresa amplía el importador existente para un volumen
nuevo; no es un endpoint nuevo. Estas ampliaciones quedan registradas aquí,
sin una modificación retroactiva de los PDF ni del tablero.

Cinco rutas del prototipo no se entregan como pantallas propias:

| Pantalla | Por qué queda fuera y qué existe en su lugar |
|---|---|
| Proveedores | Fuera del recorte; no hay backend de proveedores. |
| Historial de ventas | No existe GET /ventas paginado/filtrado. Existe un historial PDF de confirmadas en Reportes, que no sustituye una tabla navegable de todas las ventas. |
| Ventas en proceso | Exigiría listado por estado y decidir si es diagnóstico o actividad normal. La bandeja de Caja conserva solo folios conocidos en esa sesión; no es una consulta global de PENDIENTES. |
| Configuración | Los datos de empresa sí se editan en Reportes; no hay una pantalla separada de configuración general. |
| Guía de estilo | Referencia de diseño/desarrollo, no una operación del negocio que deba añadirse al cliente distribuido. |

Fuentes de la ausencia y la referencia: [rutas del cliente](../../cliente-web/src/router/index.js),
[API real de ventas](../api/ventas.md),
[referencia del prototipo](../../../docs/aceptacion/cliente-concha-visual-2026-11-12/referencia-visual-prototipo.md).
No se usa una prueba de una ruta inexistente como evidencia de que se implementó.

### Despliegue gestionado/Atlas

Se conserva el Compose local de un anfitrión como despliegue de entrega. El
replica set anuncia `mongodb:27017`; las URIs, alias y Eureka están configurados
para la red interna. Un cliente externo de replica set requiere resolver sus
miembros anunciados o una conexión directa apropiada para depuración. El
keyFile y `/docker-entrypoint-initdb.d` son mecanismos de ese contenedor, no
pasos que una URI de Atlas ejecute automáticamente.

Migrar a Atlas exigiría aprovisionar usuarios/índices por otra vía y ajustar
URIs/red. **Solo ventas** desactiva auto-index-creation y verifica los índices al
arrancar; los otros cinco dominios lo activan. El modelo documental cerrado del
Documento 04 obliga a respetar su contrato, no hace técnicamente imposible
cambiar de proveedor. Habría que verificar compatibilidad y permisos.

No se realizó una prueba de Atlas. Se descartó introducir esa migración a dos
días de la entrega: usuarios en redes cambiantes requerirían administrar acceso
sin recurrir a abrir 0.0.0.0/0; la latencia de transacciones sobre red sería un
riesgo por medir, no un rendimiento que hayamos demostrado malo. Atlas no exige
abrir todas las IP. Una VM ejecutando el Compose sería la alternativa de menor
cambio, pero añadiría un anfitrión sin evidencia propia y no solucionaría la
distribución privada de credenciales. No se afirma que todo despliegue cloud sea
imposible, sino que no se migró ni verificó para esta entrega.

## 5. Qué ejecución sostiene cada conclusión

Las ejecuciones se conservan por fecha, revisión y alcance. No se suman casos de
navegador a pruebas unitarias ni se unen recorridos distintos para presentarlos
como una sola ejecución desde cero.

| Expediente | Qué acredita | Límite que se conserva |
|---|---|---|
| [6.1 Windows, 11 nov](../../../docs/aceptacion/6.1-windows-2026-11-11/RESULTADOS.md) | Siete escenarios; stock final 5/7/8, cinco confirmadas por 672.25 y una cancelada; reentrega manual recibida y descartada; 11 servicios sanos | Arranque limpio declarado sin salida de destrucción/build; no capturó PENDIENTE en pantalla, ticket ni todos los permisos. No basta para cerrar íntegro H-094/CONTROL 3. |
| [Reportes, 11 nov](../../../docs/aceptacion/cliente-reportes-2026-11-11/RESULTADOS.md) | Gráfica, configuración inicial/edición, tres PDF y permisos por Gateway real | Ejecución Linux separada; no era la prueba manual única de Windows. |
| [Usuarios y ticket, 11 nov](../../../docs/aceptacion/cliente-usuarios-ticket-2026-11-11/RESULTADOS.md) | Administración de cuentas, rechazos y PDF reales; ventas de ADMIN y VENDEDOR | Integración automatizada Linux, no Windows/nginx. También observó que un token conserva el rol anterior. |
| [F12 físico, 12 nov](../../../docs/aceptacion/cliente-caja-visual-2026-11-12/windows-f12/RESULTADOS.md) | En esa sesión F12 cobró Sello × 1 por 120.00, folio 7, En proceso → Confirmada; usuario declaró DevTools cerrado | Vite en 5173; no acredita número/cuerpo de POST, stock o PDF de ese folio; no generaliza el acelerador a otros navegadores. |
| [Semilla nominal, 16 nov](../../../docs/aceptacion/semilla-nominal-2026-11-16/RESULTADOS.md) | 76 pruebas auth, 48 Node; cuatro accesos y 23 productos por API; repetición conserva stock/cuentas | Banco aislado con rutas estáticas y sin Eureka; no contiene la nueva semilla de empresa ni acredita la instalación Windows. |
| [nginx, 4 nov](../../../docs/aceptacion/5.6-revision-2026-11-04/README.md) | Imagen/encabezados, 404 de asset, subrutas y disponibilidad estática con Gateway detenido | No es una nueva ejecución de todo el cliente vigente. El recorrido Windows del 4 nov tiene [acta propia](../../../docs/aceptacion/windows-2026-11-04/RESULTADOS.md). |
| [Limpieza previa a 6.4](../../../docs/aceptacion/residuos-entrega-2026-11-17/RESULTADOS.md) | Alcance y resultados de la corrección de licencias, Postman y pruebas de la semilla de empresa | Distingue las operaciones del repositorio de las pendientes en Windows; no convierte los dobles HTTP en un despliegue real de empresa. |

Para Productos, Clientes y Notificaciones, los expedientes adicionales están en
el [índice de aceptación](../../../docs/aceptacion/README.md). Sus pruebas de CRUD,
errores y roles complementan la verificación, sin cambiar los límites del acta
principal. Los hashes de cada expediente protegen sus propios archivos; no
acreditan que un volumen actual conserve aquellos datos.

La semilla nominal es aditiva y no reinicia folios. Los folios y existencias de
noviembre 4, 11 y 12 siguen siendo evidencia de aquellos estados; no se promete
que reaparezcan tras importar el catálogo Auto Tachi o en un volumen nuevo.

### Cierre de Caja y alcance del arreglo del título

`4568a17` integra las ramas aprobadas de iconos y título/recarga/clientes. Su
árbol se contrastó con la combinación probada: 95 unitarias, build, doce
recorridos de navegador simulados y diez casos de recarga. La revisión del PO
confirmó la igualdad de árboles. Son pruebas del cliente, no otra ejecución
integral del backend.

El caso de navegación retenida y cancelada reproducía «Reportes» en la pestaña
con Caja como ruta actual; el arreglo impide escribir títulos en afterEach
fallidos. La secuencia de Atrás con cambio de rol, por sí sola, también pasaba
en la base. Se corrigió ese mecanismo, que explica el síntoma observado el
lunes; **no se reprodujo de forma determinista su disparador concreto**. No se
presenta como identificación completa de aquella causa raíz.

## 6. H-095 y aceptación que no se debe dar por supuesta

El Documento 03 mantiene H-095: un escenario por módulo del cliente, rechazos
de credenciales/existencia/rol, precondición/pasos/resultado y ejecución
registrada. El Documento 01 enumera S6 por 16 puntos sin actividad independiente
para esa historia. Su ausencia en la suma no acredita cancelación. Se registra
la brecha; no se añaden tres puntos al plan ni se marca como cerrada.

Existe [GUION.md](../../../docs/aceptacion/GUION.md), centrado en Caja, y existen
guiones automatizados/actas por otros módulos. Eso es material reutilizable,
pero no se ha acreditado aquí una ejecución manual consolidada de todos los
criterios de H-095. Tampoco se declara completado H-094 reuniendo capturas de
fechas distintas: el ticket, la gráfica y En proceso tienen evidencias
complementarias, con sus entornos y límites expuestos en la sección anterior.

## 7. Pendientes concretos para el anfitrión de entrega

| Acción | Estado de este borrador | Evidencia necesaria para resolverla |
|---|---|---|
| Rotar JWT_SECRET en Windows y comprobar genérico desactivado | Procedimiento preparado, ejecución no observada aquí | Token anterior no vencido rechazado, nuevo nominal aceptado, login genérico rechazado; sin publicar tokens |
| Alinear el nombre del volumen actual | Pendiente de PUT desde Reportes en Windows | Configuración leída y cabecera/PDF nuevos con el nombre acordado |
| Reproducción de empresa en volumen nuevo | Código y pruebas del importador; falta despliegue del compañero | Ejecución habilitada que crea y después reutiliza el documento por Gateway real |
| Carpeta local windows-20261104-131653 | Ausente de main/nube; sin inspección | Inventario privado y decisión de archivado fuera del clon o incorporación revisada |
| Segundo anfitrión de Víctor o Hugo | Recomendado; no ejecutado ni confirmado | Clon/volúmenes nuevos identificados, build, semilla, salud y recorrido de quien lo levantó |
| Cifrado/distribución del .env | Decisión pendiente; no implementado | Acordar custodia, distribución y recuperación antes de programarlo |
| Revisión y aprobación de H-093 | Este borrador | Revisión del PO, ajuste de pendientes y aceptación explícita |

El [procedimiento Windows](VERIFICACION-WINDOWS.md) concreta esas operaciones.
Se conserva el anfitrión ya probado; frente al antecedente de disco lleno/WSL,
comprobar espacio y Docker Desktop y preparar otro equipo es una mitigación,
**no un plan B ya demostrado**. Este cierre no oculta esos pendientes ni los
convierte en nuevas funciones prometidas para el jueves.

## 8. Referencias de control

- [Documento 01 v3.4](../planeacion/MonPoint_MS_01_Plan_de_Trabajo_del_Equipo.pdf): §7.2–7.4, calendario/recorte original.
- [Documento 02](../planeacion/MonPoint_MS_02_Especificacion_de_Requisitos_y_Alcance.pdf): requisitos y límites de alcance.
- [Documento 03 v3.2](../planeacion/MonPoint_MS_03_Product_Backlog_y_Convenciones_de_Trabajo.pdf): H-090 a H-095, pp. 17–18.
- [Documento 04](../planeacion/MonPoint_MS_04_Modelo_de_Datos.pdf): esquema documental cerrado; las divergencias observadas se declaran, no se corrigen en ese PDF.
- [Diagramas vigentes y nota sobre el JPG histórico](../arquitectura/README.md).
- [Contrato y límites de seguridad](../api/seguridad.md), [operación](../api/operacion.md) y [semilla reproducible](../../scripts/SEMILLA_AUTOTACHI.md).
