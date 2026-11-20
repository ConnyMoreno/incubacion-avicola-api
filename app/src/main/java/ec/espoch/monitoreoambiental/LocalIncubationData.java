package ec.espoch.monitoreoambiental;

import org.json.JSONArray;
import org.json.JSONObject;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/** Lee y guarda el estado de incubación mediante la API alojada; no se conecta directamente a PostgreSQL. */
final class LocalIncubationData implements AutoCloseable {
    final MainActivity app;
    final IncubacionApi api;
    JSONArray stages;
    boolean hasCycle = false, hasReading = false, connected = false, demo = false;
    float tempTolerance = .5f, humTolerance = 5f;
    private float[] temperatures = new float[0], humidities = new float[0];
    private String[] labels = new String[0];
    private final List<String> remoteEvents = new ArrayList<>();
    private Double averageTemperature, averageHumidity;
    private boolean errorShown;

    LocalIncubationData(MainActivity app) {
        this.app = app;
        this.api = new IncubacionApi(app);
        this.stages = app.fallbackStages();
    }

    void start() { refresh(); }
    void stop() { }

    void refresh() {
        api.state((json, error) -> {
            if (error != null) { onError(error); return; }
            connected = true;
            errorShown = false;
            applyState(json);
            fetchHistory();
            app.applyStage();
            app.render();
        });
    }

    private void applyState(JSONObject json) {
        JSONObject cycle = json.optJSONObject("ciclo");
        hasCycle = cycle != null;
        if (cycle != null) {
            long date = parseDate(cycle.optString("fecha_inicio", ""));
            if (date > 0) app.startDate = date;
        }

        JSONArray serverStages = json.optJSONArray("etapas");
        if (serverStages != null && serverStages.length() > 0) stages = serverStages;

        JSONObject config = json.optJSONObject("configuracion");
        if (config != null) {
            app.all = flag(config, "alertas_activas", app.all);
            app.aTemp = flag(config, "alerta_temperatura", app.aTemp);
            app.aHum = flag(config, "alerta_humedad", app.aHum);
            app.aPower = flag(config, "alerta_energia", app.aPower);
            app.aInternet = flag(config, "alerta_conexion", app.aInternet);
            app.appNote = flag(config, "notificacion_app", app.appNote);
            app.phoneNote = flag(config, "notificacion_telefono", app.phoneNote);
            app.sound = flag(config, "alerta_sonora", app.sound);
            tempTolerance = (float) config.optDouble("tolerancia_temperatura", tempTolerance);
            humTolerance = (float) config.optDouble("tolerancia_humedad", humTolerance);
            app.save();
        }

        JSONObject reading = json.optJSONObject("lectura");
        hasReading = reading != null;
        if (reading != null) {
            app.temp = (float) reading.optDouble("temperatura", app.temp);
            app.hum = (float) reading.optDouble("humedad", app.hum);
            app.power = flag(reading, "energia_disponible", true);
        }

        remoteEvents.clear();
        JSONArray alerts = json.optJSONArray("alertas");
        if (alerts != null) {
            for (int i = 0; i < alerts.length(); i++) {
                JSONObject alert = alerts.optJSONObject(i);
                if (alert != null) {
                    String message = alert.optString("mensaje", "Alerta de incubación");
                    String date = alert.optString("creada_en", "");
                    remoteEvents.add(date.isEmpty() ? message : date + "  " + message);
                }
            }
        }
    }

    private boolean flag(JSONObject object, String key, boolean fallback) {
        Object value = object.opt(key);
        if (value instanceof Boolean) return (Boolean) value;
        if (value instanceof Number) return ((Number) value).intValue() != 0;
        if (value instanceof String) {
            String text = ((String) value).trim();
            if (text.equals("1") || text.equalsIgnoreCase("true")) return true;
            if (text.equals("0") || text.equalsIgnoreCase("false")) return false;
        }
        return fallback;
    }

    private long parseDate(String value) {
        if (value == null || value.isEmpty()) return -1;
        String[] patterns = {"yyyy-MM-dd", "yyyy-MM-dd HH:mm:ss"};
        for (String pattern : patterns) {
            try {
                SimpleDateFormat format = new SimpleDateFormat(pattern, Locale.US);
                format.setTimeZone(TimeZone.getTimeZone("America/Guayaquil"));
                Date date = format.parse(value);
                if (date != null) return date.getTime();
            } catch (ParseException ignored) { }
        }
        return -1;
    }

    void changePeriod() { fetchHistory(); }

    private void fetchHistory() {
        String selected = app.period.equals("Día") ? "dia" : app.period.equals("Mes") ? "mes" : "semana";
        api.history(selected, (json, error) -> {
            if (error != null) { onError(error); return; }
            JSONArray points = json.optJSONArray("puntos");
            int count = points == null ? 0 : points.length();
            temperatures = new float[count];
            humidities = new float[count];
            labels = new String[count];
            for (int i = 0; i < count; i++) {
                JSONObject point = points.optJSONObject(i);
                if (point == null) continue;
                labels[i] = point.optString("etiqueta", "");
                temperatures[i] = (float) point.optDouble("temperatura", 0);
                humidities[i] = (float) point.optDouble("humedad", 0);
            }
            averageTemperature = json.isNull("temperatura_promedio") ? null : json.optDouble("temperatura_promedio");
            averageHumidity = json.isNull("humedad_promedio") ? null : json.optDouble("humedad_promedio");
            app.render();
        });
    }

    float[] values(boolean temperature) { return temperature ? temperatures : humidities; }

    String average(boolean temperature) {
        Double value = temperature ? averageTemperature : averageHumidity;
        if (value == null) return "--";
        return String.format(Locale.US, temperature ? "%.1f °C" : "%.0f %%", value);
    }

    String label(int position) {
        return position >= 0 && position < labels.length && labels[position] != null ? labels[position] : "";
    }

    boolean displayReading() { return hasReading; }

    List<String> events(List<String> local) {
        List<String> allEvents = new ArrayList<>(remoteEvents);
        allEvents.addAll(local);
        return allEvents;
    }

    void saveConfiguration() {
        app.applyStage();
        app.save();
        JSONObject body = new JSONObject();
        try {
            body.put("id_incubadora", api.incubadora)
                    .put("nombre_usuario", "Nayeli")
                    .put("fecha_inicio", new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date(app.startDate)))
                    .put("alertas_activas", app.all)
                    .put("alerta_temperatura", app.aTemp)
                    .put("alerta_humedad", app.aHum)
                    .put("alerta_energia", app.aPower)
                    .put("alerta_conexion", app.aInternet)
                    .put("notificacion_app", app.appNote)
                    .put("notificacion_telefono", app.phoneNote)
                    .put("alerta_sonora", app.sound);
        } catch (Exception e) {
            app.toast("No se pudo preparar la configuración.");
            return;
        }
        api.save(body, (json, error) -> {
            if (error != null) { onError(error); return; }
            app.dateDirty = false;
            app.toast("Configuración guardada en la base de datos.");
            refresh();
        });
    }

    void recordReading(float temperature, float humidity, boolean power) {
        api.recordReading(temperature, humidity, power, (json, error) -> {
            if (error != null) { onError(error); return; }
            refresh();
        });
    }

    private void onError(String message) {
        connected = false;
        if (!errorShown) {
            errorShown = true;
            String display = message.contains("token privado")
                    ? "Falta configurar app/src/main/assets/api-config.json con el token de Render."
                    : "No se pudo conectar con la API: " + message;
            app.toast(display);
        }
    }

    @Override public void close() { api.close(); }
}
