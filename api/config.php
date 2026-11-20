<?php
declare(strict_types=1);

function envValue(string $name): ?string {
    $value = getenv($name);
    return $value === false || trim($value) === '' ? null : trim($value);
}

function postgresSettings(): array {
    $url = envValue('DATABASE_URL');
    if ($url !== null && preg_match('/^postgres(?:ql)?:\/\//i', $url)) {
        $parts = parse_url($url);
        if ($parts === false || empty($parts['host']) || empty($parts['path']) || empty($parts['user']) || !array_key_exists('pass', $parts)) {
            throw new RuntimeException('DATABASE_URL de PostgreSQL inválida; revisa el formato de Neon.');
        }
        return [
            'host' => $parts['host'],
            'port' => (int)($parts['port'] ?? 5432),
            'database' => rawurldecode(ltrim($parts['path'], '/')),
            'user' => rawurldecode($parts['user']),
            'password' => rawurldecode($parts['pass']),
        ];
    }

    // También acepta las variables separadas que Neon muestra en su panel.
    $host = envValue('NEON_HOST') ?? $url;
    $database = envValue('NEON_DATABASE');
    $user = envValue('NEON_USER');
    $password = envValue('NEON_PASSWORD');
    $port = envValue('NEON_PORT') ?? '5432';
    if ($host === null || $database === null || $user === null || $password === null) {
        throw new RuntimeException('Configura DATABASE_URL completa de PostgreSQL o las variables NEON_HOST, NEON_DATABASE, NEON_USER, NEON_PASSWORD y NEON_PORT.');
    }
    if (!ctype_digit($port) || (int)$port < 1 || (int)$port > 65535) {
        throw new RuntimeException('NEON_PORT no es válido.');
    }
    return ['host' => $host, 'port' => (int)$port, 'database' => $database, 'user' => $user, 'password' => $password];
}

return [
    'database' => postgresSettings(),
    'api_token' => envValue('API_TOKEN') ?? throw new RuntimeException('Falta configurar la variable de entorno: API_TOKEN'),
    'timezone' => getenv('APP_TIMEZONE') ?: 'America/Guayaquil',
];
