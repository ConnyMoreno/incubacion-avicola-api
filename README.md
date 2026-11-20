# Incubación Avícola

Aplicación Android y API PHP para Render conectada a PostgreSQL en Neon.

## Despliegue de la API

Render debe usar el runtime **Docker**, con `Dockerfile` en la raíz del repositorio. El servicio incluye el endpoint de salud `/health.php`.
Para revisar la conexión con PostgreSQL y confirmar que existen las tablas que usa la API, abre `/diagnostico.php` en el dominio de Render.

La API usa PDO con el controlador `pdo_pgsql`; no se conecta a MySQL. Configura en Render una de estas opciones:

- `DATABASE_URL`: cadena de conexión completa de Neon (`postgresql://usuario:contraseña@host/base?sslmode=require`).
- O las variables separadas de Neon: `NEON_HOST`, `NEON_DATABASE`, `NEON_USER`, `NEON_PASSWORD` y `NEON_PORT` (normalmente `5432`). Si defines ambas opciones, se usa `DATABASE_URL` solo cuando comienza con `postgres://` o `postgresql://`.
- `API_TOKEN`: token privado generado en Render.
- `APP_TIMEZONE`: `America/Guayaquil`.

No guardes credenciales en el código ni en GitHub. `database.sql` es un script para MySQL/phpMyAdmin; no lo ejecutes en Neon. La API necesita que la base PostgreSQL ya tenga las tablas `incubadoras`, `ciclos_incubacion`, `etapas_incubacion`, `configuracion`, `lecturas` y `alertas`, con las columnas consultadas por `api/index.php`.
## Aplicación Android

El módulo Android está en `app/`. Abre el repositorio desde Android Studio con JDK 17.

Para conectar la app, copia `app/src/main/assets/api-config.json.example` como `app/src/main/assets/api-config.json` y coloca en `api_token` el mismo valor configurado en Render como `API_TOKEN`. El archivo real está excluido por `.gitignore`; no lo subas a GitHub.
