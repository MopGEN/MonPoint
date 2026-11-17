# Residuos previos al borrador 6.4 — 17 de noviembre de 2026

Base `4568a17`. Ejecución Linux de la nube, Node 24.19.0, Docker 28.4.0,
nginx **1.28.3**. No se operó la instalación Windows del PO. Fuentes de código
del banco: [hashes de blobs Git](fuentes-probadas.json).

## Resultados ejecutados

| Comprobación | Resultado | Archivo |
|---|---|---|
| Guiones de semilla históricos y Auto Tachi | 54 pruebas, 54 correctas, sin omitidas | [Salida Node](guiones-node.txt) |
| Licencias servidas por nginx | Dos GET 200 text/plain, bytes idénticos a los OFL públicos | [HTTP corregido](nginx-http-corregido.json) |
| Archivos inexistentes | GET y HEAD 404, nosniff; sin entregar SPA | Mismo archivo; dos rutas bajo /fuentes y un asset .woff2 |
| Enlaces profundos | Siete GET 200 text/html, no-cache y nosniff | Mismo archivo; login y seis rutas privadas |
| Misma prueba con nginx.conf de 4568a17 | Falla como se esperaba: /fuentes/no-existe.txt da 200 text/html | [HTTP base](nginx-http-base.json), [aserción](nginx-base-fallo-esperado.txt) |
| Sintaxis/version nginx | nginx -t correcto; 1.28.3 | [Validación](nginx-validacion.txt) |
| Plantilla Postman viva | adminPassword y vendedorPassword vacíos; ambos tokens también vacíos | [Comprobación](postman-verificacion.json) |

Son quince consultas HTTP en la variante corregida. El fallo de la base es
deliberado y demuestra el defecto; no se suma como una prueba aprobada.
[Resumen por variante](nginx-resultado.json) y [salida aprobada](nginx-prueba.txt).

## Qué se hizo y qué no se puede concluir

Se compiló el cliente con npm run build y se usó una imagen runtime de prueba,
derivada de `monpoint-ms-cliente-web:latest`, copiando el dist nuevo y nginx.conf.
Los contenedores, publicados en puertos loopback efímeros, se eliminaron después.
En la variante base solo se sustituyó nginx.conf por su versión de 4568a17.
El test reproducible es
[test/nginx-estaticos.mjs](../../../monpoint-ms/cliente-web/test/nginx-estaticos.mjs).

El primer intento runtime encontró 404 incluso para Inter-OFL.txt: el build del
host tenía directorios 0700 y archivos 0600 por el umask del entorno, ilegibles
para el worker nginx. Se corrigieron los permisos **de la copia temporal pública**
a directorios 0755/archivos 0644 y se repitió la prueba; no se cambió el producto
para compensar ese montaje. Los resultados publicados arriba son los finales.

Los intentos de construir el Dockerfile multietapa completo no acreditan éxito:
primero falló la escritura de estado buildx en un directorio de solo lectura;
al usar un directorio escribible se llegó a npm ci. Un intento devolvió error y
otros se interrumpieron al permanecer allí sin completar. Se ensayaron el
certificado de CA y los argumentos de proxy previstos por el Dockerfile, sin
desactivar TLS, pero no se estableció una causa de red concreta. El banco
runtime **no sustituye** la construcción multietapa actual ni la prueba por
Compose de Windows. El [seguimiento anterior](../5.6-revision-2026-11-04/README.md)
se mantiene con su fecha y alcance propios.

Las pruebas nuevas de empresa usan un servidor HTTP simulado: creación ante
404, repetición sin PUT, conflicto sin sobrescritura, lectura fallida, 200 null
que no se trata como ausencia, rechazo de validación y PUT guardado sin
respuesta. No acreditan una empresa nueva en el Gateway/Mongo reales ni que
Víctor/Hugo hayan ejecutado la semilla. No se implementó un RFC de respaldo:
el DTO remoto decide su validación.

No se volvieron a ejecutar Java, las 95 unitarias Vue ni los doce recorridos de
Caja por cambios de documentación/nginx/guiones. Su ejecución sobre el árbol
integrado de 4568a17 es un antecedente separado, no parte de este conteo.

## Operaciones pendientes en el anfitrión de entrega

No se rotó JWT_SECRET de Windows, no se cambió su documento de empresa ni se
inspeccionó `docs/aceptacion/windows-20261104-131653/`, ausente de este clon.
El borrador de cierre detallará sus pasos y la evidencia que falta; este
expediente no certifica su realización ni marca actividades en Asana.

Las claves históricas de fixtures y actas no se barrieron: se preserva la
evidencia. Se retiraron los valores de contraseña de la plantilla de Postman que
se importa para ejecutar pruebas actuales. Ni ese cambio ni la desactivación
de una cuenta revocan los JWT anteriores: la rotación coordinada sigue siendo
una operación distinta y pendiente de verificación en cada instalación.
