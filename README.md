# Incubación Avícola

Aplicación Android y API PHP para Render conectada a PostgreSQL en Neon.

## Despliegue de la API

Render debe usar el runtime **Docker**, con `Dockerfile` en la raíz del repositorio. El servicio incluye el endpoint de salud `/health.php`.

Configura estas variables en Render:

- `DATABASE_URL`: cadena de conexión PostgreSQL de Neon, con SSL requerido.
- `API_TOKEN`: token privado generado en Render.
- `APP_TIMEZONE`: `America/Guayaquil`.

No guardes credenciales en el código ni en GitHub. El archivo `database.sql` de MySQL no se incluye porque esta instalación usa Neon PostgreSQL.
