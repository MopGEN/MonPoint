# Cómo se trabaja en este repositorio

Convenciones de rama, confirmación, revisión e incidencias para MonPoint MS.

Este archivo no reemplaza al Documento 01 (Plan de Trabajo). Aquel dice **qué**
se construye, quién responde y cuándo; este dice **cómo** se mueve el código.

---

## Las dos herramientas

| | Asana | GitHub |
|---|---|---|
| Qué contiene | Las 35 actividades del proyecto, agrupadas por sprint | Incidencias del día a día y solicitudes de integración |
| Granularidad | Lo que cierra en días | Lo que cierra en horas |
| Flujo y límite de trabajo | **Aquí** | — |
| Vínculo con el código | Ninguno, es deliberado | Automático |

El tablero con el flujo `LISTO → EN PROGRESO → REVISIÓN → HECHO` y el límite de
una actividad en progreso por persona **vive únicamente en Asana**. No se
replica aquí. Dos tableros que miden lo mismo divergen en cuanto uno se
actualiza y el otro no.

**No se duplica información.** Una actividad que está en Asana no se copia a
GitHub como incidencia. Una incidencia técnica que está en GitHub no se copia a
Asana.

---

## Ramas

```
<tipo>/<actividad>-<descripcion-corta>
```

`<actividad>` es el número del tablero de Asana. Así, cualquiera que vea una
rama sabe a qué actividad pertenece sin preguntar.

| Tipo | Cuándo |
|---|---|
| `feat` | Funcionalidad nueva |
| `fix` | Corrección de un defecto |
| `infra` | Docker, Compose, configuración, inicialización |
| `docs` | Documentación, README, catálogo de eventos |
| `chore` | Dependencias, limpieza, renombres |

Ejemplos reales de este proyecto:

```
infra/1.1-mongodb-replica-set
infra/1.3-gateway-enrutamiento
feat/2.2-ms-auth-login-jwt
feat/4.1-ms-ventas-registro-asincrono
fix/2.4-cors-preflight-401
docs/6.2-readme-ms-ventas
```

Una rama por actividad. Si una actividad es grande, varias confirmaciones en la
misma rama; no se abren ramas paralelas para la misma actividad.

`main` es la única rama permanente. No hay `develop` ni ramas de versión: con
tres personas y seis semanas, esa estructura añade ceremonia sin resolver nada.

---

## Confirmaciones

```
<actividad> <descripción en presente, sin punto final>
```

```
1.2 Configura el servidor Eureka con puerto externalizado
4.3 Reserva existencias de forma atómica dentro de una transacción
2.4 Exime las rutas de autenticación del filtro de token
```

El número al inicio hace que `git log --oneline` se lea como el avance real del
proyecto contra el tablero, sin herramienta adicional.

Para vincular una incidencia, se agrega una línea al final:

```
2.4 Ejecuta el filtro de CORS antes del de JWT

Closes #14
```

La palabra clave va **en inglés**. GitHub solo reconoce `close`, `closes`,
`closed`, `fix`, `fixes`, `fixed`, `resolve`, `resolves` y `resolved` para
cerrar una incidencia automáticamente. "Cierra #14" no cierra nada: queda como
texto. Si solo se quiere referenciar sin cerrar, se usa `Refs #14`.

---

## Solicitudes de integración

**Toda integración a `main` pasa por una solicitud, incluidas las propias.**
No para bloquear: para dejar registro. Es lo que liga cada cambio a su actividad
y a su incidencia, y es la única razón por la que este repositorio aporta algo
que Asana no.

El equipo acordó **revisión posterior a la fusión**:

1. Se abre la solicitud de integración con la plantilla.
2. Quien la abre **la fusiona de inmediato**, sin esperar aprobación.
3. El revisor asignado comenta sobre lo ya fusionado, dentro de 24 horas.
4. Lo que encuentre se corrige en una confirmación de seguimiento. **No se
   revierte** salvo que `main` esté roto.

### La regla que compensa no bloquear

> **Quien fusiona responde por que `main` siga levantando.**

Antes de fusionar, el sistema arranca con `docker compose up` y los servicios
que ya existían siguen registrándose en Eureka. No es una recomendación: sin
revisión previa, es lo único que impide que un cambio detenga a los otros dos.

Si `main` queda roto, corregirlo tiene prioridad sobre cualquier actividad en
curso, de quien sea.

### Quién revisa qué

Según el Documento 01, sección 8.1:

| Servicio | Owner | Revisor |
|---|---|---|
| ms-auth | Hugo | Victor |
| ms-inventario | Victor | Hugo |
| ms-ventas | Alejandro | Hugo |
| ms-clientes | Victor | Hugo |
| ms-reportes | Hugo | Victor |
| ms-notificaciones | Hugo | Alejandro |
| api-gateway e infraestructura | Equipo | — |
| cliente-web (empaquetado) | Alejandro | Hugo |

---

## Incidencias

Se abre una incidencia cuando aparece algo que **no estaba planeado y cierra en
horas**: un error encontrado mientras se programa, una deuda técnica que se
decide dejar para después, un comportamiento que no coincide con el criterio de
aceptación.

No se abre una incidencia para trabajo que ya está en Asana.

### Cuándo escalar en vez de abrir una incidencia

Si al resolver algo aparece que **falta una actividad completa**, eso no es una
incidencia: es un cambio de alcance. Se escala al Product Owner y se decide en
Asana. No se añade trabajo al proyecto por iniciativa individual.

Lo mismo aplica a la regla de arquitectura del README: si construyendo algo
parece necesario que un servicio llame a otro por HTTP, no se hace. Significa
que falta un evento o una réplica local, y esa es una decisión de arquitectura.

---

## Etiquetas

Dos dimensiones. Toda incidencia lleva una de cada una.

**Por tipo**

| Etiqueta | Uso |
|---|---|
| `error` | Algo no funciona como dice su criterio de aceptación |
| `deuda-tecnica` | Funciona, pero hay que volver |
| `bloqueo` | Detiene a alguien. Se escala también en el check-in del día |
| `pregunta` | Ambigüedad en un criterio. Se escala al Product Owner |

**Por componente**

`ms-auth` · `ms-inventario` · `ms-ventas` · `ms-clientes` · `ms-reportes` ·
`ms-notificaciones` · `api-gateway` · `eureka` · `infra` · `cliente-web` ·
`docs`

---

## Qué no va en este repositorio

- **Credenciales.** El `.env` está excluido en `.gitignore`; solo se versiona
  `.env.example`. Una clave confirmada por error permanece en el historial
  aunque se borre después.
- **`target/`, `node_modules/`, `dist/`.** Ya excluidos. MonPointV2 tenía
  `target/` versionado; es uno de los hallazgos del Documento 02.
- **Archivos con espacios en el nombre.** Rompen guiones y rutas en Docker.
- **Copias de documentos.** Un documento, un archivo, siempre el vigente. La
  versión va impresa en su portada; el historial lo guarda git.
