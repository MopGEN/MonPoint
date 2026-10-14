# Despliegue de `ms-auth` (Fase 6)

Cómo se ejecuta `ms-auth`, cómo se registra en Eureka y cómo debe rutearlo el API Gateway.
El contrato del token está en [`CONTRATO_TOKEN.md`](CONTRATO_TOKEN.md).

---

## 1. Variables de entorno

| Variable | Valor por omisión | Para qué |
|---|---|---|
| `SERVER_PORT` | `8081` | Puerto HTTP |
| `MONGO_URI` | `mongodb://auth_user:auth_pass@localhost:27017/auth_db?replicaSet=rs0&authSource=auth_db&directConnection=true` | Conexión a `auth_db`. Dentro de Docker va **sin** `directConnection` |
| `EUREKA_URL` | `http://localhost:8761/eureka/` | Servidor Eureka |
| `JWT_SECRET` | Marcador de `infra/.env.example` | Clave HS256 compartida con el Gateway |
| `JWT_EXPIRATION_MS` | `86400000` (24 h) | Vigencia del token |
| `ADMIN_DEFAULT_PASSWORD` | `Admin123!` | Contraseña del admin inicial; solo se usa si la colección `usuarios` está vacía |

## 2. En local (fuera de Docker)

```bash
cd monpoint-ms/infra && docker compose up -d mongodb
cd ../ms-auth && ./mvnw spring-boot:run
```

`directConnection=true` es necesario aquí: el replica set anuncia su miembro como `mongodb:27017`,
un nombre que solo existe dentro de la red Docker.

Si no hay un Eureka corriendo, `ms-auth` funciona igual, pero reintenta registrarse y deja errores de
conexión en el log. Para trabajar sin Eureka:

```bash
./mvnw spring-boot:run -Dspring-boot.run.arguments=--eureka.client.enabled=false
```

## 3. En Docker

El servicio `ms-auth` de `infra/docker-compose.yml` se construye con el [`Dockerfile`](../Dockerfile):

- **Dos etapas:** Maven compila y la imagen final solo lleva el JRE 21 y el jar.
- **Usuario sin privilegios** (uid 10001), no root.
- **`HEALTHCHECK`** con `curl` a `/actuator/health`: el contenedor queda *healthy* cuando la app arrancó y MongoDB contesta.
- **Las pruebas no corren al construir la imagen** porque necesitan MongoDB. Hay que correr `./mvnw test` antes.
- **No publica puertos:** como todo servicio de dominio, se llega a él a través del Gateway. Para depurarlo directamente, agregar temporalmente `ports: ["8081:8081"]`.

Levantar el sistema completo:

```bash
cd monpoint-ms/infra && docker compose up -d --build
```

> Requiere que `eureka-server/` y `api-gateway/` ya tengan su proyecto y su `Dockerfile` (hoy están vacíos).
> Mientras tanto, `docker compose up -d --build --no-deps ms-auth` levanta solo `ms-auth`; necesita que
> `mongodb` esté arriba y que algún Eureka responda como `eureka-server` dentro de la red `monpoint-red`.

## 4. Registro en Eureka

| Aspecto | Valor |
|---|---|
| Nombre del servicio | `MS-AUTH` (de `spring.application.name=ms-auth`) |
| Id de instancia | `ms-auth:<ip>:8081`. La IP evita que dos instancias en el mismo puerto se pisen el registro |
| Dirección anunciada | La IP (`prefer-ip-address=true`): el hostname de un contenedor no lo resuelven los demás |
| Latido / expiración | Cada 10 s / 30 s sin latidos |
| Salud | `eureka.client.healthcheck.enabled=true`: Eureka recibe el estado real de `/actuator/health` |

Comportamiento medido:

- **Al arrancar**, la instancia aparece `DOWN` durante unos **10 s** (la app aún no está lista para recibir tráfico) y luego pasa a `UP`. El estado se reenvía cada 10 s; con los 30 s por omisión la espera era de medio minuto.
- **Si MongoDB cae**, la instancia pasa a `DOWN` (se midieron 76 s, que incluyen los 30 s de espera del driver) y el Gateway deja de enviarle tráfico. Al volver MongoDB, regresa a `UP`.

## 5. Ruteo desde el API Gateway

En las dos opciones **no se usa `StripPrefix`**: `ms-auth` espera la ruta completa (`/auth/login`, no `/login`).

### Opción A, con Eureka (recomendada)

El Gateway pregunta a Eureka dónde está `MS-AUTH` y reparte entre las instancias `UP`.
Con Spring Cloud **2025.1.x** (Gateway 5.0), las rutas van bajo `spring.cloud.gateway.server.webflux.routes`.
La variante MVC usa `...server.webmvc.routes`.

```yaml
spring:
  cloud:
    gateway:
      server:
        webflux:
          routes:
            - id: ms-auth
              uri: lb://ms-auth
              predicates:
                - Path=/auth/**
```

> El prefijo antiguo `spring.cloud.gateway.routes` ya no existe en Gateway 5.0. Un Gateway configurado
> así arranca **sin rutas y sin dar error**.

### Opción B, ruteo estático por la red de Docker

Si el Gateway corre en la misma red de Docker Compose, puede llamar a `ms-auth` por su nombre de
servicio, sin Eureka:

```yaml
              uri: http://ms-auth:8081
```

Es más simple, pero pierde lo que aporta Eureka: no reparte carga entre varias instancias y sigue
enviando tráfico aunque la instancia esté `DOWN`. Sirve como plan B mientras `eureka-server` no exista.

## 6. Verificación realizada (2026-10-02)

Con un Eureka desechable de Spring Cloud 2025.1.3 y la imagen construida por `docker compose`:

- `ms-auth` local y en contenedor se registran como `MS-AUTH` en `UP`, con `healthCheckUrl` a `/actuator/health`.
- Desde otro contenedor de la red, `http://ms-auth:8081` (opción B) y `http://<ip-registrada>:8081` (lo que resuelve `lb://`, opción A) responden. Login y `/auth/me` funcionan contra MongoDB.
- El contenedor corre como uid 10001, no publica puertos y queda *healthy* en unos 9 s.
- `/actuator/health` es público y solo responde `UP`/`DOWN`; el resto de Actuator no está expuesto.
