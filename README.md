# Incubación Avícola

Aplicación Android y API PHP para Render conectada a PostgreSQL en Neon.

## Despliegue de la API

Render debe usar el runtime **Docker**, con `Dockerfile` en la raíz del repositorio. El servicio incluye el endpoint de salud `/health.php`.

Configura estas variables en Render:

- `DATABASE_URL`: cadena de conexión PostgreSQL de Neon, con SSL requerido.
- `API_TOKEN`: token privado generado en Render.
- `APP_TIMEZONE`: `America/Guayaquil`.

No guardes credenciales en el código ni en GitHub. El archivo `database.sql` de MySQL no se incluye porque esta instalación usa Neon PostgreSQL.
## Aplicación Android

El módulo Android está en `app/`. Abre el repositorio desde Android Studio con JDK 17.

Para conectar la app, copia `app/src/main/assets/api-config.json.example` como `app/src/main/assets/api-config.json` y coloca en `api_token` el mismo valor configurado en Render como `API_TOKEN`. El archivo real está excluido por `.gitignore`; no lo subas a GitHub.
