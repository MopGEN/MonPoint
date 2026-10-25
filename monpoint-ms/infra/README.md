# Infraestructura de MonPoint MS

## Aviso para Hugo y Victor: clave de ventas

Antes de recrear MongoDB, añadir a su `infra/.env`:

```ini
MONGO_VENTAS_PASSWORD=<clave-local>
```

La variable está documentada en `.env.example`. El `.env` real no se versiona.
Usar una clave apta para una URI (por ejemplo, una cadena hexadecimal aleatoria).
Compose pasa la misma clave al guion de inicialización y a ms-ventas.

Si falta, el guion 01 falla **antes de crear cualquiera de los seis usuarios de
servicio**. Así evita dejar la inicialización a medias. No cambiar la variable
contra una base ya creada esperando que se actualice la contraseña de MongoDB:
los guiones de `mongo-init/` solo se ejecutan con un volumen vacío.

## Nombre del proyecto y arranque

El archivo Compose fija `name: monpoint-ms`. Desde esta carpeta:

```powershell
docker compose up -d --build
docker compose ps
docker compose logs mongodb | Select-String "Indices de ventas_db" -Context 0,9
```

Se crean cinco índices adicionales para ventas, dos únicos, además de los
índices automáticos `_id_`. Ventas comprueba los índices al arrancar.

Si antes se usaba el nombre implícito `infra`, sus volúmenes pertenecen a otro
proyecto y no se reutilizan automáticamente como `monpoint-ms`. `-p` y
`COMPOSE_PROJECT_NAME`, si se especifican, tienen prioridad sobre `name`.

Para recrear los datos de desarrollo del proyecto actual, después de configurar
el `.env`, se puede ejecutar `docker compose down -v` y luego `docker compose up
-d`. **`down -v` borra los datos locales de MongoDB y RabbitMQ de ese proyecto.**
