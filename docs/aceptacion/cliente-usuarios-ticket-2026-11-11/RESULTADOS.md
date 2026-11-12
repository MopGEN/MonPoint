# Usuarios y ticket en Caja: revisión e integración real

11 de noviembre de 2026, Ciudad de México. Base `6a1b9d7`: Reportes ya está
fusionado en main por PR #27. Parche recibido `0005-cliente-usuarios.patch`,
commit original declarado `99fea4f6`. Se aplicó con git am --3way y se resolvió
el conflicto de router/index.js conservando Reportes y Usuarios. Sí coincidían
en ese archivo. Importación `6aa36c6`, con autor Alejandro Bermúdez
<os.bermudez@outlook.com> por petición expresa del usuario; se conservó la
atribución del mensaje recibido. El ticket y las correcciones son posteriores.

## Cambios revisados

Usuarios es ADMIN: listado de activos, alta, edición de datos/contraseña/rol y
desactivación. Ambos roles conservan Caja y pueden descargar ticket de una venta
confirmada. No se añade historial paginado ni backend nuevo.

Correcciones sobre el parche:

1. Reconocer la cuenta propia por correo dejaba de funcionar al modificarlo.
   AuthResponse no tiene id, pero **GET /auth/me sí lo devuelve**. Se consulta
   junto al listado y se compara por id estable. Se verificó también el correo
   cambiado por otro administrador con la sesión original abierta. Guardar el
   perfil propio sincroniza nombre/correo visibles sin simular un JWT nuevo.
2. La contraseña no se precarga y ahora también se vacía al cancelar o guardar.
   Null en password/rol conserva el valor remoto; se precisaron los comentarios
   que hablaban de omitir claves cuando realmente se serializan como null.
3. La desactivación no revoca JWT emitidos; cambiar rol tampoco modifica sus
   claims. La pantalla informa de ese límite y de la falta de reactivación.
   La explicación no promete cerrar sesiones ajenas ni presenta detalles de
   intervención en MongoDB como parte del flujo de la aplicación.
4. Una cuenta con nombre largo desbordaba la cabecera a 390 px. La primera
   integración real lo detectó; se permite envolver nombre/rol y se mantiene
   Salir visible. La segunda ejecución y las regresiones móviles pasan.
5. La prueba de borrado espera cierre y contenido final, no ausencia durante
   Cargando. El recorrido simulado prueba la guarda mediante historial con
   sesión conservada, sin recargar y perder el token. Su comentario distingue
   las dos formas de error simuladas del mapa campos probado por separado.

Se leyó el contrato completo de DTOs, servicio, controlador, seguridad, modelo,
validador de contraseñas y manejador de errores de ms-auth. El servidor valida
@Email, política y permisos; no se duplica esa política en JavaScript. Los 409
actuales de estas operaciones se traducen a correo por el contrato explícito
de GlobalExceptionHandler. No se usa la ausencia en un filtro de texto como
prueba de que falte una validación.

## Ticket

`TicketVenta` se renderiza para CONFIRMADA en el resultado y en seguimiento.
Usa gateway.descargar con Bearer, captura id/folio antes de esperar y guarda
`ticket-folio-N.pdf`. Comparte guardarDocumento con Reportes; no duplica el
manejo de enlaces y URLs de objeto. Se revocan tras diez segundos.

La guarda síncrona de useAccion impide dos descargas simultáneas por componente.
Desmontar aborta la petición y una respuesta tardía no guarda el documento.
El error 404 con código VENTA_NO_DISPONIBLE conserva el resultado confirmado y
permite reintento manual. No hay reintento automático ni se repite POST /ventas.
Pendientes/canceladas no ofrecen ticket. El 401 mantiene la expiración común.
El color del botón hereda el contexto de Caja, incluido su panel verde.

## Validación ejecutada

| Comprobación | Resultado |
|---|---|
| npm test | 95 pruebas: las 79 de main y 16 de Usuarios; sin fallos ni omitidas |
| npm run build | Correcta; sin nuevas dependencias ni cambios de lockfile |
| Usuarios con Gateway simulado | Alta, política/409, perfil propio tras cambiar correo, desactivación tras recarga y guarda de rol |
| Ticket con Gateway simulado | Seis casos: ADMIN y VENDEDOR con 404/reintento/doble clic; cancelada; seguimiento; desmontaje; 401 |
| Caja con API simulada | Sus siete escenarios previos correctos, incluidos cobro ambiguo y recuperación tras 401 |
| Reportes, Clientes, Productos y Notificaciones simulados | Cuatro regresiones correctas |
| Usuarios y tickets con servicios reales | PASS, sin intercepciones de red ni errores JavaScript |

Son siete guiones de navegador simulados, además de la integración real. Las
95 pruebas son unitarias; no se suman a ellas los escenarios de navegador para
inflar ese número. El mapa campos tiene prueba unitaria; el recorrido real de
Usuarios acredita específicamente la lista de política y el 409, no todos los
posibles rechazos Bean Validation. El PDF simulado solo prueba transporte.

## Ejecución real conservada

**19:13:46–19:13:55 del 11 de noviembre de Ciudad de México**, equivalente a
`2026-11-12T01:13:46–01:13:55Z`. Navegador automatizado Linux, Vite en 5173 y
Gateway en 8080, sin proxy de desarrollo. [resultado.json](resultado.json)
conserva respuestas y estados, sin contraseñas, hashes de contraseña ni tokens.

1. El ADMIN inicial crea por API un administrador de prueba. Este inicia sesión
   y opera Usuarios. Alta con contraseña corta: **400**, cuatro reglas visibles
   junto a password y sin aviso genérico duplicado. Correo repetido: **409**
   junto a correo. Corregirlo crea un VENDEDOR desde la pantalla con **201**.
2. PUT desde el formulario cambia VENDEDOR a ADMIN; nuevo login confirma ADMIN.
   Cambiar contraseña da **200**, login con anterior **401** y nueva **200**.
   Otro PUT lo devuelve a VENDEDOR. El JWT emitido antes del cambio todavía
   obtiene GET usuarios **200**, observación concreta de la ausencia de revocación.
3. El ADMIN inicial cambia por API el correo del administrador de prueba.
   Al volver a Usuarios se conserva su marca usted y el rol bloqueado gracias
   a /auth/me. El usuario modifica su propio nombre/correo desde el formulario;
   cabecera y fila quedan sincronizadas. La API rechaza desactivar su cuenta y
   cambiar su propio rol con **400**. No se alteró la cuenta ADMIN inicial.
4. VENDEDOR no ve Usuarios. Con su token real, GET listado, POST registro,
   PATCH desactivar a otro y PUT sobre otro usuario responden **403**, con sus
   cuerpos conservados. Es distinto de una petición sin credencial.
5. Desde Caja, VENDEDOR cobra una unidad a 41.15: **folio 59 CONFIRMADA**. Pide
   ticket con **200**, `ticket-folio-59.pdf`, **1859 bytes**. ADMIN inicia otra
   sesión y cobra otra unidad: **folio 60 CONFIRMADA**, ticket **200**,
   `ticket-folio-60.pdf`, **1859 bytes**. Cada cobro hizo un POST 202; descargar
   no añadió otro. pdftotext contrastó título, folio, producto y total $41.15.
   Ambos documentos originales se conservan con SHA-256 y capturas móviles.
6. En Usuarios, cancelar la desactivación conserva la cuenta en el listado.
   Confirmar devuelve **204**; la recarga ya no la contiene y la pantalla
   muestra el vacío del filtro. Su nuevo login responde **401**. Al terminar,
   se desactivan exclusivamente las dos cuentas creadas por el guion con el
   ADMIN inicial: **204/204**.

El producto y ambas ventas permanecen. La configuración de empresa no se
modificó: los tickets usan la existente en el entorno. No se realizó una
cancelación real en esta ejecución; ocultar su botón se verifica en el simulador.
Los dos tickets reales estuvieron disponibles al primer GET; el retraso 404 y
su reintento se verifican con respuesta controlada, no como un retraso observado
en estas ventas reales.

## Intento anterior y entorno

[Intento fallido](intentos/resultado.json): `19:11:35–19:11:43` de Ciudad de
México. Alcanzó el ticket real de VENDEDOR, folio **58**, y se detuvo al detectar
desbordamiento horizontal con el nombre largo. Se conservan captura, respuesta
y ese PDF. No se le atribuyen el ticket ADMIN ni la desactivación desde la
pantalla que no alcanzó. Su limpieza final sí desactivó sus dos cuentas con 204.

Los once contenedores existentes estaban detenidos al reanudar el entorno;
se arrancaron conservando sus volúmenes e imágenes. No se reconstruyó backend
ni se ejecutó una instalación limpia. [entorno.json](entorno.json) identifica
las imágenes de auth/reportes/gateway y el estado observado. El nginx existente
se arrancó con su imagen anterior; los recorridos usaron el nuevo cliente en
Vite o la compilación servida por preview, no ese nginx.

## Límites

- No es el recorrido manual único de 6.1 en Windows ni cierra controles o Asana.
  No se restauran las capturas antiguas ni se cambia aquel manifiesto.
- El empaquetado Windows/nginx sigue aplazado por indicación del usuario.
- La pantalla no implementa reactivación, revocación, historial paginado,
  búsqueda de tickets históricos por folio ni recuperación del estado tras F5.
  El ticket se ofrece para los resultados confirmados retenidos en memoria.
- La guarda de descarga es por componente; no garantiza idempotencia entre
  pestañas. Descargar de nuevo es otro GET y no modifica la venta.
- No se realizaron pruebas de carga ni una auditoría completa de accesibilidad.
  Se verificaron las etiquetas, estados y tamaños concretos de los recorridos.
- El backend todavía acepta JWT anteriores a cambios de rol o desactivación;
  se documenta y comunica ese comportamiento, no se declara corregido.

`fuentes.json` identifica los archivos revisados. `SHA256.json` cubre los bytes
de este expediente, excluido el propio manifiesto.
