<?php
declare(strict_types=1);

header('Content-Type: application/json; charset=utf-8');
header('Cache-Control: no-store');

// ==========================================
// RESPUESTAS Y VALIDACIONES
// ==========================================

function respond(array $data, int $code = 200): void {
    http_response_code($code);
    echo json_encode(
        $data,
        JSON_UNESCAPED_UNICODE | JSON_INVALID_UTF8_SUBSTITUTE
    );
    exit;
}

function fail(string $message, int $code): void {
    respond(['ok' => false, 'error' => $message], $code);
}

function integer($value): int {
    if (
        filter_var($value, FILTER_VALIDATE_INT) === false ||
        (int)$value < 1
    ) {
        fail('Identificador inválido.', 422);
    }
    return (int)$value;
}

function readBody(): array {
    $raw = file_get_contents('php://input', false, null, 0, 16385);

    if ($raw === false || strlen($raw) > 16384) {
        fail('Solicitud demasiado grande.', 413);
    }

    $data = json_decode($raw, true);

    if (!is_array($data) || array_is_list($data)) {
        fail('El cuerpo debe ser un objeto JSON válido.', 400);
    }

    return $data;
}

function query(
    PDO $db,
    string $sql,
    array $args = []
): PDOStatement {
    $stmt = $db->prepare($sql);
    $stmt->execute($args);
    return $stmt;
}

// ==========================================
// INCUBACIÓN ACTIVA
// ==========================================

function currentCycle(PDO $db, int $id): ?array {
    $row = query(
        $db,
        "SELECT *
         FROM public.historial_incubaciones
         WHERE id_incubadora = ?
           AND estado = 'en_proceso'
         ORDER BY id_incubacion DESC
         LIMIT 1",
        [$id]
    )->fetch();

    return $row ?: null;
}

// ==========================================
// SENSOR ACTIVO
// ==========================================

function currentSensor(PDO $db, int $id): ?array {
    $row = query(
        $db,
        "SELECT *
         FROM public.sensores
         WHERE id_incubadora = ?
           AND activo IS TRUE
         ORDER BY id_sensor ASC
         LIMIT 1",
        [$id]
    )->fetch();

    return $row ?: null;
}

// ==========================================
// CONEXIÓN Y AUTENTICACIÓN
// ==========================================

try {
    $cfg = require __DIR__ . '/config.php';

    $key = $_SERVER['HTTP_X_API_KEY'] ?? '';

    if (
        empty($cfg['api_token']) ||
        !is_string($cfg['api_token']) ||
        !hash_equals($cfg['api_token'], $key)
    ) {
        fail('Clave de la API incorrecta.', 401);
    }

    $action = $_GET['accion'] ?? 'estado';

    $methods = [
        'estado' => 'GET',
        'historial' => 'GET',
        'guardar_configuracion' => 'POST',
        'registrar_lectura' => 'POST'
    ];

    if (!isset($methods[$action])) {
        fail('Acción desconocida.', 404);
    }

    if ($_SERVER['REQUEST_METHOD'] !== $methods[$action]) {
        header('Allow: ' . $methods[$action]);
        fail('Método no permitido.', 405);
    }

    // config.php entrega el arreglo 'database'; también se acepta DATABASE_URL.
    $pg = $cfg['database'] ?? null;
    if (!is_array($pg)) {
        $databaseUrl = parse_url($cfg['database_url'] ?? getenv('DATABASE_URL') ?: '');
        if ($databaseUrl === false || empty($databaseUrl['host']) || empty($databaseUrl['path']) ||
            empty($databaseUrl['user']) || !array_key_exists('pass', $databaseUrl)) {
            throw new RuntimeException('DATABASE_URL de PostgreSQL inválida.');
        }
        $pg = [
            'host' => $databaseUrl['host'],
            'port' => (int)($databaseUrl['port'] ?? 5432),
            'database' => rawurldecode(ltrim($databaseUrl['path'], '/')),
            'user' => rawurldecode($databaseUrl['user']),
            'password' => rawurldecode($databaseUrl['pass']),
        ];
    }

    $dsn = 'pgsql:host=' . $pg['host']
        . ';port=' . ($pg['port'] ?? 5432)
        . ';dbname=' . $pg['database']
        . ';sslmode=require';

    $db = new PDO(
        $dsn,
        $pg['user'],
        $pg['password'],
        [
            PDO::ATTR_ERRMODE => PDO::ERRMODE_EXCEPTION,
            PDO::ATTR_DEFAULT_FETCH_MODE => PDO::FETCH_ASSOC,
            PDO::ATTR_EMULATE_PREPARES => false
        ]
    );

    $db->exec("SET TIME ZONE 'UTC'");

    $data = $_SERVER['REQUEST_METHOD'] === 'POST'
        ? readBody()
        : [];

    $id = integer(
        $data['id_incubadora']
        ?? $_GET['id_incubadora']
        ?? 1
    );

    $incubator = query(
        $db,
        "SELECT *
         FROM public.incubadoras
         WHERE id_incubadora = ?
           AND activa IS TRUE",
        [$id]
    )->fetch();

    if (!$incubator) {
        fail('La incubadora no existe o está inactiva.', 404);
    }

    // ======================================
    // ACCIÓN: ESTADO
    // ======================================

    if ($action === 'estado') {
        $cycle = currentCycle($db, $id);
        $sensor = currentSensor($db, $id);

        $lastReading = query(
            $db,
            "SELECT
                l.id_lectura,
                l.id_sensor,
                l.id_incubacion,
                l.temperatura,
                l.humedad,
                l.fecha_hora
             FROM public.lecturas l
             INNER JOIN public.sensores s
                ON s.id_sensor = l.id_sensor
             WHERE s.id_incubadora = ?
             ORDER BY l.fecha_hora DESC, l.id_lectura DESC
             LIMIT 1",
            [$id]
        )->fetch();

        $alerts = query(
            $db,
            "SELECT a.id_alerta,a.tipo,a.mensaje,
                    CASE WHEN a.atendida IS TRUE THEN 'atendida' ELSE 'pendiente' END AS estado,
                    a.fecha_hora AS creada_en
             FROM public.alertas a
             INNER JOIN public.lecturas l ON l.id_lectura = a.id_lectura
             INNER JOIN public.sensores s ON s.id_sensor = l.id_sensor
             WHERE s.id_incubadora = ?
             ORDER BY a.fecha_hora DESC, a.id_alerta DESC
             LIMIT 20",
            [$id]
        )->fetchAll();

        $day = null;

        if ($cycle) {
            $tz = new DateTimeZone(
                $cfg['timezone'] ?? 'America/Guayaquil'
            );

            $start = new DateTimeImmutable(
                $cycle['fecha_inicio'],
                $tz
            );

            $today = new DateTimeImmutable('today', $tz);

            $day = (int)$start->diff($today)->format('%r%a') + 1;
        }

        $stage = null;
        $stages = query(
            $db,
            'SELECT id_parametro AS id_etapa,descripcion AS nombre,dia_inicio,dia_fin,
                    (temperatura_min + temperatura_max) / 2 AS temperatura_objetivo,
                    temperatura_min,temperatura_max,humedad_min AS humedad_minima,humedad_max AS humedad_maxima
             FROM public.parametros_por_dia ORDER BY dia_inicio'
        )->fetchAll();
        if ($day !== null) {
            foreach ($stages as $candidate) {
                if ($day >= (int)$candidate['dia_inicio'] && $day <= (int)$candidate['dia_fin']) {
                    $stage = ['dia'=>$day,'duracion_dias'=>21,'parametros'=>$candidate];
                    break;
                }
            }
        }
        $configuration = query(
            $db,
            'SELECT id_configuracion,id_incubadora,alertas_activas,notificacion_app,alerta_sonora,fecha_actualizacion
             FROM public.configuracion_alertas WHERE id_incubadora=?
             ORDER BY fecha_actualizacion DESC,id_configuracion DESC LIMIT 1',
            [$id]
        )->fetch() ?: null;

        respond([
            'ok' => true,
            'incubadora' => $incubator,
            'sensor' => $sensor,
            'ciclo' => $cycle,
            'dia_incubacion' => $day,
            'etapa' => $stage,
            'configuracion' => $configuration,
            'lectura' => $lastReading ?: null,
            'alertas' => $alerts,
            'etapas' => $stages,
            'timezone' => $cfg['timezone']
                ?? 'America/Guayaquil'
        ]);
    }

    // ======================================
    // ACCIÓN: HISTORIAL
    // ======================================

    if ($action === 'historial') {
        $period = $_GET['periodo'] ?? 'dia';

        if (!in_array(
            $period,
            ['dia', 'semana', 'mes'],
            true
        )) {
            fail('Periodo inválido.', 422);
        }

        $tz = new DateTimeZone(
            $cfg['timezone'] ?? 'America/Guayaquil'
        );

        $start = new DateTimeImmutable('today', $tz);

        if ($period === 'semana') {
            $start = $start->modify('-6 days');
        }

        if ($period === 'mes') {
            $start = $start->modify('-29 days');
        }

        $end = new DateTimeImmutable('tomorrow', $tz);

        $startUtc = $start
            ->setTimezone(new DateTimeZone('UTC'))
            ->format('Y-m-d H:i:sP');

        $endUtc = $end
            ->setTimezone(new DateTimeZone('UTC'))
            ->format('Y-m-d H:i:sP');

        $rows = query(
            $db,
            "SELECT
                l.temperatura,
                l.humedad,
                l.fecha_hora
             FROM public.lecturas l
             INNER JOIN public.sensores s
                ON s.id_sensor = l.id_sensor
             WHERE s.id_incubadora = ?
               AND l.fecha_hora >= CAST(? AS timestamptz)
               AND l.fecha_hora < CAST(? AS timestamptz)
             ORDER BY l.fecha_hora, l.id_lectura",
            [$id, $startUtc, $endUtc]
        );

        $buckets = [];
        $totalTemp = 0.0;
        $totalHum = 0.0;
        $count = 0;

        while ($row = $rows->fetch()) {
            $local = (new DateTimeImmutable(
                $row['fecha_hora']
            ))->setTimezone($tz);

            $label = $local->format(
                $period === 'dia' ? 'H:00' : 'd/m'
            );

            if (!isset($buckets[$label])) {
                $buckets[$label] = [
                    't' => 0.0,
                    'h' => 0.0,
                    'n' => 0
                ];
            }

            $t = (float)$row['temperatura'];
            $h = (float)$row['humedad'];

            $buckets[$label]['t'] += $t;
            $buckets[$label]['h'] += $h;
            $buckets[$label]['n']++;

            $totalTemp += $t;
            $totalHum += $h;
            $count++;
        }

        $points = [];

        foreach ($buckets as $label => $bucket) {
            $points[] = [
                'etiqueta' => $label,
                'temperatura' => round(
                    $bucket['t'] / $bucket['n'], 2
                ),
                'humedad' => round(
                    $bucket['h'] / $bucket['n'], 2
                )
            ];
        }

        respond([
            'ok' => true,
            'puntos' => $points,
            'cantidad_lecturas' => $count,
            'temperatura_promedio' => $count
                ? round($totalTemp / $count, 2)
                : null,
            'humedad_promedio' => $count
                ? round($totalHum / $count, 2)
                : null
        ]);
    }

    // ======================================
    // ACCIÓN: GUARDAR CONFIGURACIÓN
    // ======================================

    if ($action === 'guardar_configuracion') {
        $tz = new DateTimeZone($cfg['timezone'] ?? 'America/Guayaquil');
        $flags = ['alertas_activas', 'notificacion_app', 'alerta_sonora'];
        $values = [];
        foreach ($flags as $flag) {
            if (!array_key_exists($flag, $data) ||
                !in_array($data[$flag], [true, false, 0, 1], true)) {
                fail('Preferencia inválida: ' . $flag, 422);
            }
            $values[] = $data[$flag] === true || $data[$flag] === 1 ? 'true' : 'false';
        }

        $date = $data['fecha_inicio'] ?? null;
        if ($date !== null) {
            if (!is_string($date)) fail('Fecha de inicio inválida.', 422);
            $parsed = DateTimeImmutable::createFromFormat('!Y-m-d', $date, $tz);
            if (!$parsed || $parsed->format('Y-m-d') !== $date ||
                $parsed > new DateTimeImmutable('today', $tz)) {
                fail('Fecha de inicio inválida.', 422);
            }
        }

        $db->beginTransaction();
        query($db,
            'SELECT id_incubadora FROM public.incubadoras WHERE id_incubadora = ? FOR UPDATE',
            [$id]
        );
        $existing = query($db,
            'SELECT id_configuracion FROM public.configuracion_alertas
             WHERE id_incubadora = ?
             ORDER BY fecha_actualizacion DESC, id_configuracion DESC LIMIT 1',
            [$id]
        )->fetchColumn();

        if ($existing !== false) {
            query($db,
                'UPDATE public.configuracion_alertas
                 SET alertas_activas = CAST(? AS boolean),
                     notificacion_app = CAST(? AS boolean),
                     alerta_sonora = CAST(? AS boolean),
                     fecha_actualizacion = CURRENT_TIMESTAMP
                 WHERE id_configuracion = ?',
                [...$values, $existing]
            );
        } else {
            query($db,
                'INSERT INTO public.configuracion_alertas
                 (id_incubadora, alertas_activas, notificacion_app, alerta_sonora, fecha_actualizacion)
                 VALUES (?, CAST(? AS boolean), CAST(? AS boolean), CAST(? AS boolean), CURRENT_TIMESTAMP)',
                [$id, ...$values]
            );
        }

        if ($date !== null) {
            $cycle = currentCycle($db, $id);
            if (!$cycle) {
                $db->rollBack();
                fail('No hay una incubación activa para actualizar la fecha de inicio.', 409);
            }
            query($db,
                'UPDATE public.historial_incubaciones
                 SET fecha_inicio = ? WHERE id_incubacion = ?',
                [$date, $cycle['id_incubacion']]
            );
        }

        $db->commit();
        respond(['ok' => true, 'mensaje' => 'Configuración guardada en PostgreSQL.']);
    }

    // ======================================
    // ACCIÓN: REGISTRAR LECTURA DEL ESP32
    // ======================================

    if ($action === 'registrar_lectura') {
        foreach (['temperatura', 'humedad'] as $field) {
            if (
                !isset($data[$field]) ||
                !is_numeric($data[$field]) ||
                !is_finite((float)$data[$field])
            ) {
                fail('Lectura inválida: ' . $field, 422);
            }
        }

        $temperature = (float)$data['temperatura'];
        $humidity = (float)$data['humedad'];

        if (
            $temperature < -100 ||
            $temperature > 150 ||
            $humidity < 0 ||
            $humidity > 100
        ) {
            fail('Lectura fuera de límites físicos.', 422);
        }

        $sensorId = isset($data['id_sensor']) ? integer($data['id_sensor']) : null;
        if ($sensorId !== null) {
            $sensor = query(
                $db,
                'SELECT id_sensor FROM public.sensores
                 WHERE id_sensor = ? AND id_incubadora = ? AND activo IS TRUE',
                [$sensorId, $id]
            )->fetch();
        } else {
            $sensor = currentSensor($db, $id);
        }
        $cycle = currentCycle($db, $id);

        if (!$sensor) {
            fail(
                'No existe un sensor activo válido para esta incubadora.',
                404
            );
        }

        if (!$cycle) {
            fail(
                'No existe una incubación activa para registrar la lectura.',
                409
            );
        }

        $stmt = query(
            $db,
            "INSERT INTO public.lecturas (
                id_sensor,
                id_incubacion,
                temperatura,
                humedad,
                fecha_hora
             )
             VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP)
             RETURNING id_lectura, fecha_hora",
            [
                $sensor['id_sensor'],
                $cycle['id_incubacion'],
                $temperature,
                $humidity
            ]
        );

        $reading = $stmt->fetch();

        respond([
            'ok' => true,
            'mensaje' => 'Lectura registrada correctamente.',
            'id_lectura' => (int)$reading['id_lectura'],
            'id_incubadora' => $id,
            'id_sensor' => (int)$sensor['id_sensor'],
            'id_incubacion' => (int)$cycle['id_incubacion'],
            'temperatura' => $temperature,
            'humedad' => $humidity,
            'fecha_hora' => $reading['fecha_hora']
        ], 201);
    }

} catch (Throwable $e) {
    if (isset($db) && $db->inTransaction()) {
        $db->rollBack();
    }

    error_log('Incubacion API: ' . $e->getMessage());

    // Return a SQLSTATE to authenticated clients without exposing SQL text,
    // credentials, or table data. The full exception remains in Render logs.
    $sqlState = $e instanceof PDOException ? (string)$e->getCode() : 'INTERNAL';
    respond([
        'ok' => false,
        'error' => 'Error interno al procesar la solicitud en PostgreSQL.',
        'codigo_error' => $sqlState,
    ], 500);
}
