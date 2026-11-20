package ec.espoch.monitoreoambiental;

/** Configuración centralizada de la API de incubación. */
public final class ApiConfig {
    private ApiConfig() { }

    public static final String BASE_URL = "https://incubacion-avicola-api-2.onrender.com/";
    public static final int CONNECT_TIMEOUT_MS = 15_000;
    public static final int READ_TIMEOUT_MS = 90_000;
    public static final int ID_INCUBADORA = 1;

    public static final String API_URL = BASE_URL + "index.php";
    public static final String HEALTH_URL = BASE_URL + "health.php";
    public static final String ESTADO_URL = API_URL + "?accion=estado";
    public static final String HISTORIAL_URL = API_URL + "?accion=historial";
    public static final String GUARDAR_CONFIGURACION_URL = API_URL + "?accion=guardar_configuracion";
    public static final String REGISTRAR_LECTURA_URL = API_URL + "?accion=registrar_lectura";
}
