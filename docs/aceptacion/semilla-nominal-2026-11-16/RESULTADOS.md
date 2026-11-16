# Semilla nominal y catálogo — 16 de noviembre de 2026

Base revisada: `73c648c` (PR #30). Cambio autorizado por el PO: tres ADMIN
nominales y un VENDEDOR genérico con dominio `@autotachi.mx`, claves por entorno
y catálogo del prototipo, en un solo commit. No incluye nginx ni documentación 6.2.

## Resultado observado

| Comprobación | Resultado | Evidencia |
|---|---|---|
| Suite completa de ms-auth, con MongoDB real | 76 pruebas, 0 fallos/errores/omitidas; jar construido | `auth-maven.txt` |
| Guiones Node nuevos e históricos | 48 pruebas, 0 fallos/omitidas | `guiones-node.txt` |
| Datos fuente | Las 24 filas coinciden en todos sus campos con el HTML original | `origen-catalogo.json` |
| Acceso por Gateway | Los cuatro login y perfiles corresponden a los roles ADMIN/ADMIN/ADMIN/VENDEDOR | `primera.json`, `comprobacion-real.mjs` |
| Importación por API | 23 altas; nombre, descripción, precio, existencia y umbral contrastados contra la fuente; incluye existencia 0 | `primera.json`, `resultado-real.json` |
| Repetición después de ajustar Balatas a 11 | 23 reutilizados, mismos ids, ninguna alta; conserva stock 11 y un producto histórico añadido aparte | `segunda.json`, `resultado-real.json` |
| Autorización de inventario | POST de producto con token VENDEDOR devuelve 403 | `comprobacion-real.mjs` |
| Reinicio de auth tras editar una cuenta y desactivar otra | Documentos completos idénticos antes/después, incluidas claves cifradas, roles, nombres, bajas, ids y fechas; conserva genérico añadido aparte | `resultado-real.json`, `comprobacion-real.mjs` |
| Comprobación funcional de esas decisiones | Clave nueva funciona, anterior devuelve 401, cuenta desactivada devuelve 401; importador se interrumpe sin altas | `comprobacion-real.mjs` |

La prueba añadió deliberadamente un genérico y un producto histórico **en la
base aislada** para comprobar su conservación. No implica que se hayan importado
los datos históricos de Windows a este entorno. `ACC-024`, inactivo en el
prototipo, queda excluido; no se inventa un campo SKU en ProductoRequest.

Compose se validó con `config --quiet` y con un archivo privado de prueba:
las cuatro claves interpoladas llegaron iguales a `services.ms-auth.environment`
y `ADMIN_DEFAULT_PASSWORD` dejó de figurar allí. No se guardó la salida completa,
porque contiene los valores secretos. `.env.example` solo tiene marcadores
inválidos para las contraseñas nominales. No hay cambios en el cliente web.

## Método y entorno

Ejecución del 16 de noviembre de 2026, Linux, Docker 28.4.0, Node 24.19.0,
JDK 21 local y Maven Wrapper 3.9.16. Los primeros intentos de compilación
local detectaron un JRE sin compilador y un caché Maven no escribible para las
dependencias de inventario; se usaron el JDK y el repositorio Maven existentes
en `/workspace/.monpoint/`. Un primer intento de suite completa encontró que
ApplicationContextRunner heredaba la bandera y claves del proceso de integración:
las pruebas del inicializador ahora aíslan esa fuente de propiedades. La
suite final conservada es la repetición corregida.

1. MongoDB 7 en un contenedor nuevo `monpoint-nominal-mongo`, replica set `rs0`,
   publicado solo en loopback `37027`; base `auth_nominal_prueba` inicialmente vacía.
2. Claves aleatorias generadas fuera del repositorio, `SEMILLA_NOMINAL_HABILITADA=true`.
   `./mvnw -B test package` en ms-auth, con MONGO_URI apuntando a esa base.
3. RabbitMQ 3, auth, inventario y Gateway en red aislada `monpoint-nominal-prueba`.
   Auth e inventario usan jars compilados del árbol actual montados en imágenes
   de ejecución existentes. Inventario se compiló con `package -DskipTests`:
   su suite no se declara ejecutada aquí. La compilación de auth sí ejecutó la suya.
4. Gateway real en `127.0.0.1:18090` con **dos rutas estáticas** para auth e inventario;
   Eureka deshabilitado. El primer arranque del Gateway requirió declarar también
   id y predicado: la lista de rutas de mayor prioridad reemplaza la de properties.
   Los primeros montajes de jar exigieron copias legibles por el uid del contenedor.
   Estos fueron ajustes del banco de pruebas, no cambios al Compose del producto.
5. Se ejecutó `comprobacion-real.mjs`: copia exacta del guion utilizado, con rutas
   locales del banco de pruebas. Requiere ese entorno aislado y el archivo privado
   `/workspace/.monpoint/semilla-nominal/prueba.env`; no distribuirlo con claves.
   No es un guion para ejecutar contra la base de demostración.

El guion conserva únicamente los resúmenes públicos. Los snapshots completos de
MongoDB se compararon en memoria; no se guardaron hashes de contraseña ni tokens.
Se omitieron las contraseñas temporales que Spring Security imprime durante
las pruebas (`Using generated security password: [OMITIDA]`); el resto de la
salida Maven se conserva. Los archivos distribuidos se comprobaron también
contra las claves nominales aleatorias para excluirlas. `SHA256.json` registra los bytes versionados de este expediente,
excepto el propio manifiesto.

## Límites y continuidad

Esto prueba el contrato de auth e inventario a través del Gateway con MongoDB y
RabbitMQ reales. No prueba Eureka, el Dockerfile multietapa, Windows, nginx,
el cliente, ventas, tickets ni recepción de eventos por reportes/notificaciones.
No había esos consumidores en este banco; un alta de catálogo no acredita su
proyección. No se ejecutó `down -v` ni se tocaron volúmenes anteriores.

Los expedientes previos de 3.1, 6.1 y F12 se conservan íntegros como registros
de estados anteriores. La nueva semilla es aditiva: en el entorno del usuario
pueden permanecer cuentas y productos genéricos y folios anteriores. No hay
promesa de exactamente cuatro usuarios y 23 productos si la base tenía datos.
La repetición del catálogo requiere una sola instancia y nombres estables,
activos: no hay unicidad por nombre ni listado de productos inactivos.

Instrucciones operativas: [SEMILLA_AUTOTACHI.md](../../../monpoint-ms/scripts/SEMILLA_AUTOTACHI.md).

## Transporte del parche a Windows

Un ensayo con `core.autocrlf=true` reprodujo la conversión de los nueve archivos
nuevos del expediente a CRLF en disco cuando `.gitattributes` entra en el mismo
`git am`: el árbol del commit coincidía, pero la copia de trabajo quedaba
modificada. Aplicar con `-c core.autocrlf=false` sobre una copia ya extraída con
CRLF tampoco sirve: los archivos anteriores no coinciden con el índice bajo
esa configuración temporal.

Se mantiene `git am` con la configuración habitual. Inmediatamente después se
comprueba que el expediente solo difiere por CR al final de línea y se ejecuta
`git restore --worktree -- docs/aceptacion/semilla-nominal-2026-11-16`.
Ahora la nueva regla `-text` ya está disponible y Git escribe los bytes del
índice sin conversión. El paso no cambia el commit ni la configuración de Git.
La comprobación de entrega contrasta árbol, estado limpio y ocho hashes tanto
en el commit como en disco después de ese procedimiento.
