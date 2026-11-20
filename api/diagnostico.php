<?php
declare(strict_types=1);

header('Content-Type: application/json; charset=utf-8');
header('Cache-Control: no-store');

function report(array $data, int $status = 200): void {
    http_response_code($status);
    echo json_encode($data, JSON_UNESCAPED_UNICODE | JSON_INVALID_UTF8_SUBSTITUTE);
    exit;
}

try {
    $cfg = require __DIR__ . '/config.php';
    $pg = $cfg['database'];
    $dsn = 'pgsql:host=' . $pg['host'] . ';port=' . $pg['port'] . ';dbname=' . $pg['database'] . ';sslmode=require';
    $db = new PDO($dsn, $pg['user'], $pg['password'], [
        PDO::ATTR_ERRMODE => PDO::ERRMODE_EXCEPTION,
        PDO::ATTR_DEFAULT_FETCH_MODE => PDO::FETCH_ASSOC,
        PDO::ATTR_EMULATE_PREPARES => false,
        PDO::ATTR_TIMEOUT => 10,
    ]);
    $schema = (string)$db->query('SELECT current_schema()')->fetchColumn();
    $tables = ['incubadoras', 'etapas_incubacion', 'ciclos_incubacion', 'configuracion', 'lecturas', 'alertas'];
    $marks = implode(',', array_fill(0, count($tables), '?'));
    $stmt = $db->prepare("SELECT table_name FROM information_schema.tables WHERE table_schema = ? AND table_name IN ($marks)");
    $stmt->execute(array_merge([$schema], $tables));
    $present = array_column($stmt->fetchAll(), 'table_name');
    $missing = array_values(array_diff($tables, $present));
    report([
        'ok' => $missing === [],
        'servicio' => 'incubacion-api',
        'motor' => 'PostgreSQL / Neon',
        'conexion' => 'establecida',
        'esquema' => $schema,
        'tablas_faltantes' => $missing,
    ], $missing === [] ? 200 : 503);
} catch (Throwable $e) {
    error_log('Incubacion API diagnóstico: ' . $e->getMessage());
    $message = $e instanceof RuntimeException ? $e->getMessage() : 'No se pudo conectar con Neon; revisa las credenciales, el acceso de red y los registros de Render.';
    report([
        'ok' => false,
        'servicio' => 'incubacion-api',
        'motor' => 'PostgreSQL / Neon',
        'conexion' => 'fallida',
        'error' => $message,
    ], 503);
}
