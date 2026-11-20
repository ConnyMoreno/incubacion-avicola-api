<?php
declare(strict_types=1);

function requiredEnv(string $name): string {
    $value = getenv($name);
    if ($value === false || $value === '') {
        throw new RuntimeException('Falta configurar la variable de entorno: ' . $name);
    }
    return $value;
}

return [
    'database_url' => requiredEnv('DATABASE_URL'),
    'api_token' => requiredEnv('API_TOKEN'),
    'timezone' => getenv('APP_TIMEZONE') ?: 'America/Guayaquil',
];
