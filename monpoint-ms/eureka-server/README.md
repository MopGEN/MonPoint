# eureka-server

Servidor de descubrimiento de MonPoint (H-001, actividad 1.2). Cada microservicio se registra aquí al
arrancar, y el API Gateway lo encuentra por su nombre (`lb://ms-auth`) en lugar de por una dirección fija.

Es un servidor **standalone**: un solo nodo, que no se registra en sí mismo ni replica con otros.

---

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

- Panel: http://localhost:8761. Es el único puerto que se publica además del Gateway, y solo sirve para inspección.
- El contenedor queda *healthy* cuando `/actuator/health` responde `UP`. Los demás servicios esperan a ese estado (`depends_on: condition: service_healthy`).

En local, fuera de Docker:

```bash
cd monpoint-ms/eureka-server && ./mvnw spring-boot:run
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

## Cómo se registra un servicio

Ya lo hace `ms-auth`. Un servicio nuevo necesita:

1. La dependencia `spring-cloud-starter-netflix-eureka-client` y `spring-boot-starter-actuator`.
2. Las propiedades de `ms-auth/src/main/resources/application.properties`, sección *Eureka Discovery Client*: `defaultZone` desde `EUREKA_URL`, `prefer-ip-address=true`, `instance-id` con la IP, latido de 10 s, expiración de 30 s y `healthcheck.enabled=true`.
3. En `docker-compose.yml`: `EUREKA_URL: http://eureka-server:8761/eureka/` y `depends_on` con `eureka-server` en `service_healthy`.

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

Por eso `ms-auth` y `eureka-server` llevan `stop_grace_period: 15s` en `infra/docker-compose.yml`.
**Todo servicio nuevo que se registre en Eureka debe llevarlo también.**

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
