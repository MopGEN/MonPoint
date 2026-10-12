# eureka-server

Servidor de descubrimiento de MonPoint (H-001, actividad 1.2). Cada microservicio se registra aquí al
arrancar, y el API Gateway lo encuentra por su nombre (`lb://ms-auth`) en lugar de por una dirección fija.

Es un servidor **standalone**: un solo nodo, que no se registra en sí mismo ni replica con otros.

> **Si eres una IA generando un servicio de MonPoint:** no modifiques este servidor. Tu servicio solo necesita el
> cliente de Eureka con las propiedades de [esta sección](#qué-debe-hacer-tu-microservicio-para-registrarse), copiadas
> tal cual, y un `spring.application.name` **exactamente** igual al de la tabla de rutas del
> [Gateway](../api-gateway/README.md#rutas).

---

## Qué hace y cómo funciona

### Propósito
En Docker, cada contenedor tiene una IP que cambia al reiniciarse. Eureka es la **agenda** del sistema: cada servicio
anuncia "soy `MS-INVENTARIO` y estoy en tal IP y puerto", y el Gateway pregunta por **nombre**. Los servicios de
dominio no se buscan entre sí, porque no se llaman: se comunican por eventos.

### Patrón
- **Registro de servicios** (*service registry*) con **descubrimiento del lado del cliente**: el Gateway lee el registro y elige la instancia (`lb://`).
- **Latidos (*heartbeats*):** cada instancia confirma que sigue viva cada 10 s. Si deja de hacerlo, su registro expira y Eureka la quita.
- **Salud real:** con `eureka.client.healthcheck.enabled=true`, el estado `UP`/`DOWN` que ve Eureka es el de `/actuator/health` del servicio (por ejemplo, `DOWN` si su MongoDB cae).

### Ciclo de vida de una instancia

```
servicio arranca ──POST /eureka/apps/MS-X──► registro (DOWN ~10 s mientras termina de arrancar, luego UP)
      │
      ├── cada 10 s ──PUT (latido)──► renueva el registro
      │
      │                        Gateway ──cada 10 s──► lee el registro ──► enruta lb://ms-x a una instancia UP
      │
      ├── apagado ordenado (docker compose stop) ──DELETE──► baja inmediata
      └── caída (kill, sin latidos) ──► el registro expira a los ~60 s y el barrido (cada 5 s) lo quita
```

### Tecnologías y configuración clave

| Elemento | Valor | Por qué |
|---|---|---|
| Librería | `spring-cloud-starter-netflix-eureka-server` (Spring Cloud 2025.1.3, Netflix Eureka 2.0.x) | Servidor de registro |
| `@EnableEurekaServer` | En `EurekaServerApplication` | Activa el registro, la API `/eureka/**` y el panel |
| `register-with-eureka` / `fetch-registry` | `false` / `false` | Es el único nodo: no se registra en sí mismo ni copia registros |
| `eureka.instance.hostname` + `defaultZone` a sí mismo | `localhost` | Configuración standalone oficial; evita que intente replicar hacia "otro" nodo |
| `enable-self-preservation` | `false` | Con pocas instancias, la autopreservación congelaría el registro y nada expiraría |
| `eviction-interval-timer-in-ms` | `5000` | Barre las instancias vencidas cada 5 s (el valor por omisión es 60 s) |
| Actuator | Solo `health`, sin detalles | Healthcheck de Docker |

## Variables de entorno

| Variable | Por omisión | Para qué |
|---|---|---|
| `SERVER_PORT` | `8761` | Puerto del panel y de la API `/eureka/**` |
| `EUREKA_SELF_PRESERVATION` | `false` | Autopreservación. Va apagada para que las instancias caídas expiren; con pocas instancias, encendida congelaría el registro |
| `EUREKA_EVICTION_INTERVAL_MS` | `5000` | Cada cuánto se barren las instancias vencidas (el valor de Eureka es 60 s) |

Con la autopreservación apagada, el panel muestra el aviso *"THE SELF PRESERVATION MODE IS TURNED OFF"*. Es lo esperado.

## Cómo levantarlo

En Docker, que es lo normal:

```bash
cd monpoint-ms/infra && docker compose up -d --build eureka-server
```

- Panel: http://localhost:8761. Se publica **solo en tu máquina** (`127.0.0.1:8761`), para inspección.
- El contenedor queda *healthy* cuando `/actuator/health` responde `UP`. Los demás servicios esperan a ese estado (`depends_on: condition: service_healthy`).

En local, fuera de Docker:

```bash
cd monpoint-ms/eureka-server && ./mvnw spring-boot:run
```

> 🔒 **Por qué solo en `127.0.0.1`:** la API de Eureka **no tiene autenticación**. Si el puerto estuviera abierto a la
> red (por ejemplo, el WiFi de la escuela), alguien podría registrar una instancia falsa de `MS-AUTH` y el Gateway le
> mandaría los logins. Dentro de Docker, los servicios lo alcanzan por la red interna (`eureka-server:8761`).

## API REST para depurar

Útil para ver el estado exacto de un servicio sin abrir el panel. Respuestas en JSON con `-H 'Accept: application/json'`:

| Petición | Devuelve | Ojo |
|---|---|---|
| `GET /eureka/apps` | Todas las aplicaciones registradas | Pasa por una caché de **hasta 30 s** |
| `GET /eureka/apps/MS-AUTH` | Las instancias de una aplicación | Misma caché |
| `GET /eureka/apps/MS-AUTH/ms-auth:<ip>:8081` | Una instancia, con su `status` | **Sin caché**: es la más confiable al depurar |
| `GET /` | Panel web | Lee el registro directo, sin caché |

```bash
curl -s -H 'Accept: application/json' http://localhost:8761/eureka/apps/MS-AUTH
```

## Pruebas

```bash
cd monpoint-ms/eureka-server && ./mvnw test
```

Son 4 pruebas y no necesitan MongoDB ni Docker. Levantan el servidor real en un puerto aleatorio y lo consultan por HTTP:

| Prueba | Qué comprueba |
|---|---|
| `EurekaServerApplicationTests` | El contexto arranca **con el registro de Eureka activo**; falla si se quita `@EnableEurekaServer` |
| `actuatorHealth_RespondeUpSinDetalles` | `/actuator/health` ➔ 200 `UP`, sin detalles internos |
| `panelWeb_RespondeElDashboardDeEureka` | `/` ➔ 200 con el panel de Eureka |
| `instanciaRegistrada_ApareceEnElPanel_YDesapareceAlDarseDeBaja` | Registro, consulta y baja de una instancia por la API REST, y su reflejo en el panel |

## Qué debe hacer tu microservicio para registrarse

Ya lo hace `ms-auth`; cópialo. Checklist:

1. **Dependencias:** `spring-cloud-starter-netflix-eureka-client` y `spring-boot-starter-actuator`, con el BOM de Spring Cloud **2025.1.3**.
2. **Nombre:** `spring.application.name` exactamente `ms-inventario`, `ms-ventas`, `ms-clientes`, `ms-reportes` o `ms-notificaciones`. Es el nombre con el que el Gateway te busca (`lb://ms-…`); si no coincide, el Gateway responde 503.
3. **Propiedades:**
   ```properties
   eureka.client.service-url.defaultZone=${EUREKA_URL:http://localhost:8761/eureka/}
   eureka.instance.prefer-ip-address=true
   eureka.instance.instance-id=${spring.application.name}:${spring.cloud.client.ip-address}:${server.port}
   eureka.instance.lease-renewal-interval-in-seconds=10
   eureka.instance.lease-expiration-duration-in-seconds=30
   eureka.client.healthcheck.enabled=true
   eureka.client.instance-info-replication-interval-seconds=10
   management.endpoints.web.exposure.include=health
   management.endpoint.health.show-details=never
   ```

   | Propiedad | Por qué |
   |---|---|
   | `prefer-ip-address=true` | En Docker, los demás no resuelven el hostname de tu contenedor; se anuncia la IP |
   | `instance-id` con la IP | Dos instancias en el mismo puerto (una local y otra en Docker) no se pisan el registro |
   | Latido 10 s / expiración 30 s | Una instancia caída desaparece en ~60 s y no en minutos |
   | `healthcheck.enabled=true` | Si tu MongoDB cae, Eureka te marca `DOWN` y el Gateway deja de mandarte tráfico. Requiere Actuator |
   | `instance-info-replication-interval-seconds=10` | Al arrancar pasas de `DOWN` a `UP` en ~10 s (con el valor por omisión tardaba 30 s) |
4. **Compose:** `EUREKA_URL: http://eureka-server:8761/eureka/`, `depends_on: eureka-server: condition: service_healthy`, la red `monpoint-red` y **`stop_grace_period: 15s`** (ver abajo por qué).
5. **Pruebas:** apaga Eureka en ellas con `@SpringBootTest(properties = "eureka.client.enabled=false")`; si no, tardan y llenan el log de `Connection refused`.
6. **Comprueba:** levanta tu servicio y busca su nombre en http://localhost:8761. Primero aparece `DOWN` unos 10 s y luego `UP`; es normal.

Guía completa para construir un servicio: [`ms-auth/README.md`](../ms-auth/README.md#guía-para-tu-microservicio).

## Tiempos

| Situación | Qué pasa |
|---|---|
| El servicio arranca | Aparece de inmediato; `DOWN` unos 10 s mientras termina de arrancar y luego `UP` |
| Apagado ordenado (`docker compose stop`) | Se da de baja **al instante**, unos 3 s después de la orden (ver la nota de abajo) |
| Caída (`docker compose kill`) | Su registro expira solo a los **~60 s**: Eureka cuenta dos veces los 30 s de vigencia por un error conocido de la librería. Si se cae justo después de registrarse o de cambiar de estado, a los ~35 s |
| Clientes que leen `/eureka/apps` (el Gateway) | Ven los cambios con hasta 30 s de retraso por la caché de respuestas de Eureka |

### `stop_grace_period: 15s` en los servicios que se registran

Al apagarse, el cliente de Eureka 2.0.6 espera hasta **3 s** a su tarea de replicación antes de mandar la baja.
En el entorno verificado (Docker Desktop 29.8, Compose 5.5), Docker mataba el contenedor justo a los 3 s
(`SIGKILL`, exit 137). La baja no llegaba y la instancia quedaba `DOWN` en el panel hasta expirar unos 30 s después.

Por eso `ms-auth`, `api-gateway` y `eureka-server` llevan `stop_grace_period: 15s` en `infra/docker-compose.yml`.
**Todo servicio nuevo que se registre en Eureka debe llevarlo también.** Ya está en la plantilla del compose.

| `docker compose stop ms-auth` | Exit | Baja en Eureka |
|---|---|---|
| Sin `stop_grace_period` | 137 (`SIGKILL` a los 3 s) | No llega; expira a los ~31 s |
| Con `stop_grace_period: 15s` | 143 (cierre limpio en 3.4 s, sin `SIGKILL`) | `Cancelled instance MS-AUTH` al instante |

## Verificación (2026-10-10)

- `./mvnw test`: 4 pruebas, 0 fallos.
- Imagen: *healthy* en unos 6 s, proceso con uid 10001, `/actuator/health` ➔ `UP`, panel ➔ 200.
- `ms-auth` en Docker se registra como `MS-AUTH` en `UP` (`ms-auth:172.19.0.4:8081`, `healthCheckUrl` a su `/actuator/health`).
- `docker compose stop ms-auth` con `stop_grace_period: 15s`: cierre limpio en 3.4 s (exit 143, solo `SIGTERM`) y `Cancelled instance MS-AUTH` en Eureka al instante. El panel ya no la muestra y la consulta por id da 404.
- Antes del ajuste: `SIGKILL` a los 3 s y expiración a los 31 s.
- `kill` con ms-auth estable (enviando latidos): expiración a los 62 s. `kill` justo al pasar a `UP`: 35 s.
- 2026-10-11: el puerto 8761 se publica solo en `127.0.0.1`. Comprobado: cerrado desde la IP de red local y abierto en `localhost`.
