package ec.espoch.monitoreoambiental;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Cliente HTTP de la app. La conexión PostgreSQL permanece únicamente en el servidor. */
final class IncubacionApi implements AutoCloseable {
    interface Result { void done(JSONObject json, String error); }

    static final String DEFAULT_URL = "https://incubacion-avicola-api-4.onrender.com/index.php";
    final String endpoint;
    final String token;
    final int incubadora;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private volatile boolean closed;

    IncubacionApi(Context context) {
        String address = ApiConfig.API_URL;
        String apiToken = "";
        int id = ApiConfig.ID_INCUBADORA;
        try (InputStream in = context.getAssets().open("api-config.json")) {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            byte[] buffer = new byte[2048];
            int count;
            while ((count = in.read(buffer)) != -1) bytes.write(buffer, 0, count);
            JSONObject config = new JSONObject(bytes.toString("UTF-8"));
            address = config.optString("url", address).trim();
            apiToken = config.optString("api_token", config.optString("api_key", "")).trim();
            id = config.optInt("id_incubadora", ApiConfig.ID_INCUBADORA);
        } catch (Exception ignored) {
            // Sin el archivo privado, la app conserva el endpoint y reportará cómo configurarlo.
        }
        if (address.endsWith("/")) address = address.substring(0, address.length() - 1);
        endpoint = address.toLowerCase().endsWith(".php") ? address : address + "/index.php";
        token = apiToken;
        incubadora = Math.max(1, id);
    }

    void state(Result callback) { request(ApiConfig.ESTADO_URL, "", null, callback); }

    void history(String period, Result callback) {
        request(ApiConfig.HISTORIAL_URL, "&periodo=" + period, null, callback);
    }

    void save(JSONObject body, Result callback) { request(ApiConfig.GUARDAR_CONFIGURACION_URL, "", body, callback); }

    void recordReading(float temperature, float humidity, boolean power, Result callback) {
        try {
            JSONObject body = new JSONObject().put("id_incubadora", incubadora)
                    .put("temperatura", temperature).put("humedad", humidity)
                    .put("energia_disponible", power);
            request(ApiConfig.REGISTRAR_LECTURA_URL, "", body, callback);
        } catch (Exception e) {
            callback.done(null, "No se pudo preparar la lectura.");
        }
    }

    private void request(String route, String extra, JSONObject body, Result callback) {
        if (closed) return;
        executor.execute(() -> {
            JSONObject result = null;
            String error = null;
            HttpURLConnection connection = null;
            try {
                if (token.isEmpty()) throw new IOException("Falta el token privado de la API.");
                URL url = new URL(route + "&id_incubadora=" + incubadora + extra);
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod(body == null ? "GET" : "POST");
                connection.setConnectTimeout(ApiConfig.CONNECT_TIMEOUT_MS);
                connection.setReadTimeout(ApiConfig.READ_TIMEOUT_MS);
                connection.setInstanceFollowRedirects(true);
                connection.setRequestProperty("Accept", "application/json");
                connection.setRequestProperty("X-API-Key", token);
                if (body != null) {
                    connection.setDoOutput(true);
                    connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                    byte[] payload = body.toString().getBytes(StandardCharsets.UTF_8);
                    connection.setFixedLengthStreamingMode(payload.length);
                    try (OutputStream out = connection.getOutputStream()) { out.write(payload); }
                }
                int status = connection.getResponseCode();
                InputStream stream = status >= 400 ? connection.getErrorStream() : connection.getInputStream();
                if (stream == null) throw new IOException("El servidor no devolvió respuesta (HTTP " + status + ").");
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                try (InputStream in = stream) {
                    byte[] buffer = new byte[4096];
                    int n;
                    while ((n = in.read(buffer)) != -1) {
                        if (bytes.size() + n > 1048576) throw new IOException("La respuesta de la API es demasiado grande.");
                        bytes.write(buffer, 0, n);
                    }
                }
                String response = bytes.toString("UTF-8");
                if (status < 200 || status >= 300) throw new IOException("Error HTTP " + status + ".");
                result = new JSONObject(response);
                if (!result.optBoolean("ok")) {
                    error = result.optString("error", "La API rechazó la solicitud.");
                    result = null;
                }
            } catch (Exception e) {
                error = e.getMessage() == null ? "No se pudo conectar con la API." : e.getMessage();
            } finally {
                if (connection != null) connection.disconnect();
            }
            JSONObject value = result;
            String message = error;
            main.post(() -> { if (!closed) callback.done(value, message); });
        });
    }

    @Override public void close() {
        closed = true;
        executor.shutdownNow();
    }
}

