<?php
declare(strict_types=1);
header('Content-Type: application/json; charset=utf-8');
header('Cache-Control: no-store');

function respond(array $data, int $code = 200): void {
    http_response_code($code);
    echo json_encode($data, JSON_UNESCAPED_UNICODE | JSON_INVALID_UTF8_SUBSTITUTE);
    exit;
}
function fail(string $message, int $code): void { respond(['ok'=>false,'error'=>$message], $code); }
function integer($v): int {
    if (filter_var($v, FILTER_VALIDATE_INT) === false || (int)$v < 1) fail('Identificador inválido.', 422);
    return (int)$v;
}
function readBody(): array {
    $raw=file_get_contents('php://input', false, null, 0, 16385);
    if ($raw === false || strlen($raw)>16384) fail('Solicitud demasiado grande.', 413);
    $data=json_decode($raw, true);
    if (!is_array($data)) fail('El cuerpo debe ser JSON válido.', 400);
    return $data;
}
function query(PDO $db, string $sql, array $args=[]): PDOStatement {
    $stmt=$db->prepare($sql); $stmt->execute($args); return $stmt;
}

// Neon already contains the project's PostgreSQL schema. Keep using those
// tables instead of creating a parallel MySQL-shaped schema.
function currentCycle(PDO $db, int $id) {
    return query($db,
        "SELECT id_incubacion AS id_ciclo,id_incubadora,'Ciclo de incubación' AS nombre,
                fecha_inicio,21 AS duracion_dias,
                CASE WHEN estado='en_proceso' THEN 'activo' ELSE estado END AS estado,
                fecha_fin,observaciones
         FROM historial_incubaciones
         WHERE id_incubadora=? AND estado='en_proceso'
         ORDER BY id_incubacion DESC LIMIT 1",[$id])->fetch() ?: null;
}
function currentSensor(PDO $db, int $id): ?array {
    return query($db,
        'SELECT id_sensor,id_incubadora,activo FROM sensores WHERE id_incubadora=? AND activo IS TRUE ORDER BY id_sensor LIMIT 1',
        [$id])->fetch() ?: null;
}
function stage(PDO $db, $cycle, string $tz) {
    if (!$cycle) return null;
    $start=new DateTimeImmutable($cycle['fecha_inicio'],new DateTimeZone($tz));
    $today=new DateTimeImmutable('today',new DateTimeZone($tz));
    $day=(int)$start->diff($today)->format('%r%a')+1;
    $row=query($db,
        'SELECT id_parametro AS id_etapa,descripcion AS nombre,dia_inicio,dia_fin,
                (temperatura_min+temperatura_max)/2 AS temperatura_objetivo,
                temperatura_min,temperatura_max,humedad_min AS humedad_minima,humedad_max AS humedad_maxima
         FROM parametros_por_dia WHERE ? BETWEEN dia_inicio AND dia_fin ORDER BY dia_inicio LIMIT 1',[$day])->fetch();
    return ['dia'=>$day,'duracion_dias'=>21,'parametros'=>$row ?: null];
}
function incubationStages(PDO $db): array {
    return query($db,
        'SELECT id_parametro AS id_etapa,descripcion AS nombre,dia_inicio,dia_fin,
                (temperatura_min+temperatura_max)/2 AS temperatura_objetivo,
                temperatura_min,temperatura_max,humedad_min AS humedad_minima,humedad_max AS humedad_maxima
         FROM parametros_por_dia ORDER BY dia_inicio')->fetchAll();
}
function configuration(PDO $db, int $id): ?array {
    $row=query($db,'SELECT id_configuracion,id_incubadora,alertas_activas,notificacion_app,alerta_sonora,fecha_actualizacion FROM configuracion_alertas WHERE id_incubadora=? ORDER BY id_configuracion DESC LIMIT 1',[$id])->fetch();
    if (!$row) return null;
    return array_merge([
        'nombre_usuario'=>'Nayeli','alertas_activas'=>true,'alerta_temperatura'=>true,
        'alerta_humedad'=>true,'alerta_energia'=>true,'alerta_conexion'=>true,
        'notificacion_app'=>true,'notificacion_telefono'=>true,'alerta_sonora'=>false,
        'tolerancia_temperatura'=>0.50,'tolerancia_humedad'=>5.00,
    ],$row);
}

try {
    $cfg=require __DIR__.'/config.php';
    $key=$_SERVER['HTTP_X_API_KEY'] ?? '';
    if (empty($cfg['api_token']) || !hash_equals($cfg['api_token'],$key)) fail('Clave de la API incorrecta.',401);
    $action=$_GET['accion'] ?? 'estado';
    $methods=['estado'=>'GET','historial'=>'GET','guardar_configuracion'=>'POST','registrar_lectura'=>'POST'];
    if (!isset($methods[$action])) fail('Acción desconocida.',404);
    if ($_SERVER['REQUEST_METHOD']!==$methods[$action]) { header('Allow: '.$methods[$action]);fail('Método no permitido.',405); }
    // La configuración principal ya entrega los datos separados. También
    // aceptamos DATABASE_URL para instalaciones que usen la cadena de Neon.
    $pg = $cfg['database'] ?? null;
    if (!is_array($pg)) {
        $databaseUrl = $cfg['database_url'] ?? getenv('DATABASE_URL') ?: '';
        $parts = parse_url($databaseUrl);
        if ($parts === false || empty($parts['host']) || empty($parts['path']) || empty($parts['user']) || !array_key_exists('pass', $parts)) {
            throw new RuntimeException('DATABASE_URL de PostgreSQL inválida.');
        }
        $pg = [
            'host' => $parts['host'],
            'port' => (int)($parts['port'] ?? 5432),
            'database' => rawurldecode(ltrim($parts['path'], '/')),
            'user' => rawurldecode($parts['user']),
            'password' => rawurldecode($parts['pass']),
        ];
    }
    $dsn = 'pgsql:host='.$pg['host'].';port='.$pg['port'].';dbname='.$pg['database'].';sslmode=require';
    $db=new PDO($dsn,$pg['user'],$pg['password'],[
        PDO::ATTR_ERRMODE=>PDO::ERRMODE_EXCEPTION,PDO::ATTR_DEFAULT_FETCH_MODE=>PDO::FETCH_ASSOC,PDO::ATTR_EMULATE_PREPARES=>false
    ]);
    $db->exec("SET TIME ZONE 'UTC'");
    $data=$_SERVER['REQUEST_METHOD']==='POST' ? readBody() : [];
    $id=integer($data['id_incubadora'] ?? $_GET['id_incubadora'] ?? 1);
    $incubator=query($db,'SELECT * FROM incubadoras WHERE id_incubadora=? AND activa IS TRUE',[$id])->fetch();
    if (!$incubator && !in_array($action,['estado','historial'],true)) fail('La incubadora no existe o está inactiva en Neon.',404);

    if ($action==='estado') {
        $cycle=currentCycle($db,$id);
        $sensor=currentSensor($db,$id);
        $latest=query($db,
            'SELECT l.id_lectura,h.id_incubadora,l.id_incubacion AS id_ciclo,l.temperatura,l.humedad,
                    TRUE AS energia_disponible,l.fecha_hora AS medida_en
             FROM lecturas l JOIN historial_incubaciones h ON h.id_incubacion=l.id_incubacion
             WHERE h.id_incubadora=? ORDER BY l.fecha_hora DESC,l.id_lectura DESC LIMIT 1',[$id])->fetch() ?: null;
        $alerts=query($db,
            "SELECT a.id_alerta,a.tipo,a.mensaje,
                    CASE WHEN a.atendida IS TRUE THEN 'atendida' ELSE 'pendiente' END AS estado,
                    a.fecha_hora AS creada_en
             FROM alertas a JOIN lecturas l ON l.id_lectura=a.id_lectura
             JOIN sensores s ON s.id_sensor=l.id_sensor
             WHERE s.id_incubadora=? ORDER BY a.fecha_hora DESC,a.id_alerta DESC LIMIT 20",[$id])->fetchAll();
        respond(['ok'=>true,'incubadora'=>$incubator,'sensor'=>$sensor,'ciclo'=>$cycle,
            'etapa'=>stage($db,$cycle,$cfg['timezone']),'configuracion'=>configuration($db,$id),
            'lectura'=>$latest,'alertas'=>$alerts,'etapas'=>incubationStages($db),'timezone'=>$cfg['timezone']]);
    }
    if ($action==='historial') {
        $period=$_GET['periodo'] ?? 'dia';
        if (!in_array($period,['dia','semana','mes'],true)) fail('Periodo inválido.',422);
        $tz=new DateTimeZone($cfg['timezone']);$start=new DateTimeImmutable('today',$tz);
        if ($period==='semana') $start=$start->modify('-6 days');
        if ($period==='mes') $start=$start->modify('-29 days');
        $end=(new DateTimeImmutable('tomorrow',$tz))->setTimezone(new DateTimeZone('UTC'));
        $startUtc=$start->setTimezone(new DateTimeZone('UTC'))->format('Y-m-d H:i:s');
        $rows=query($db,'SELECT l.temperatura,l.humedad,l.fecha_hora AS medida_en FROM lecturas l JOIN sensores s ON s.id_sensor=l.id_sensor WHERE s.id_incubadora=? AND l.fecha_hora>=? AND l.fecha_hora<? ORDER BY l.fecha_hora,l.id_lectura',[$id,$startUtc,$end->format('Y-m-d H:i:s')]);
        $buckets=[];$ts=0.0;$hs=0.0;$count=0;
        while ($row=$rows->fetch()) {
            $local=(new DateTimeImmutable($row['medida_en'],new DateTimeZone('UTC')))->setTimezone($tz);
            $label=$local->format($period==='dia'?'H:00':'d/m');
            if (!isset($buckets[$label])) $buckets[$label]=['t'=>0.0,'h'=>0.0,'n'=>0];
            $t=(float)$row['temperatura'];$h=(float)$row['humedad'];
            $buckets[$label]['t']+=$t;$buckets[$label]['h']+=$h;$buckets[$label]['n']++;
            $ts+=$t;$hs+=$h;$count++;
        }
        $points=[];foreach($buckets as $label=>$b) $points[]=['etiqueta'=>$label,'temperatura'=>round($b['t']/$b['n'],2),'humedad'=>round($b['h']/$b['n'],2)];
        respond(['ok'=>true,'puntos'=>$points,'cantidad_lecturas'=>$count,'temperatura_promedio'=>$count?round($ts/$count,2):null,'humedad_promedio'=>$count?round($hs/$count,2):null]);
    }
    if ($action==='guardar_configuracion') {
        $flags=['alertas_activas','alerta_temperatura','alerta_humedad','alerta_energia','alerta_conexion','notificacion_app','notificacion_telefono','alerta_sonora'];
        $values=[];foreach ($flags as $name) {
            if (!array_key_exists($name,$data) || !in_array($data[$name],[true,false,0,1],true)) fail('Preferencia inválida: '.$name,422);
            $values[$name]=(bool)$data[$name];
        }
        $name=trim((string)($data['nombre_usuario'] ?? 'Nayeli'));
        if ($name==='' || strlen($name)>100) fail('Nombre inválido.',422);
        $date=(string)($data['fecha_inicio'] ?? '');
        $parsed=DateTimeImmutable::createFromFormat('!Y-m-d',$date,new DateTimeZone($cfg['timezone']));
        if (!$parsed || $parsed->format('Y-m-d')!==$date || $parsed>new DateTimeImmutable('today',new DateTimeZone($cfg['timezone']))) fail('Fecha de inicio inválida.',422);
        $db->beginTransaction();
        query($db,'SELECT id_incubadora FROM incubadoras WHERE id_incubadora=? FOR UPDATE',[$id]);
        $existing=query($db,'SELECT id_configuracion FROM configuracion_alertas WHERE id_incubadora=? ORDER BY id_configuracion DESC LIMIT 1',[$id])->fetchColumn();
        if ($existing) {
            query($db,'UPDATE configuracion_alertas SET alertas_activas=?,notificacion_app=?,alerta_sonora=?,fecha_actualizacion=CURRENT_TIMESTAMP WHERE id_configuracion=?',[
                $values['alertas_activas'],$values['notificacion_app'],$values['alerta_sonora'],$existing]);
        } else {
            query($db,'INSERT INTO configuracion_alertas (id_incubadora,alertas_activas,notificacion_app,alerta_sonora) VALUES (?,?,?,?)',[
                $id,$values['alertas_activas'],$values['notificacion_app'],$values['alerta_sonora']]);
        }
        $cycle=currentCycle($db,$id);
        if ($cycle) query($db,'UPDATE historial_incubaciones SET fecha_inicio=? WHERE id_incubacion=?',[$date,$cycle['id_ciclo']]);
        else query($db,"INSERT INTO historial_incubaciones (id_incubadora,fecha_inicio,estado) VALUES (?,?,'en_proceso')",[$id,$date]);
        $db->commit();respond(['ok'=>true,'mensaje'=>'Configuración guardada en PostgreSQL.']);
    }
    if ($action==='registrar_lectura') {
        foreach (['temperatura','humedad'] as $field) if (!isset($data[$field]) || !is_numeric($data[$field]) || !is_finite((float)$data[$field])) fail('Lectura inválida.',422);
        $t=(float)$data['temperatura'];$h=(float)$data['humedad'];
        if ($t < -100 || $t>150 || $h<0 || $h>100) fail('Lectura fuera de límites físicos.',422);
        $cycle=currentCycle($db,$id);
        if (!$cycle) fail('Primero configura la fecha de inicio del proceso de incubación.',409);
        $sensor=currentSensor($db,$id);
        if (!$sensor) fail('La incubadora aún no tiene un sensor activo configurado.',409);
        $stmt=query($db,'INSERT INTO lecturas (id_sensor,id_incubacion,temperatura,humedad,fecha_hora) VALUES (?,?,?,?,CURRENT_TIMESTAMP) RETURNING id_lectura',[$sensor['id_sensor'],$cycle['id_ciclo'],$t,$h]);
        respond(['ok'=>true,'id_lectura'=>(int)$stmt->fetchColumn()],201);
    }
} catch (Throwable $e) {
    if (isset($db) && $db->inTransaction()) $db->rollBack();
    error_log('Incubacion API: '.$e->getMessage());
    fail('No se pudo consultar PostgreSQL. Revisa las variables de conexión de Neon, las tablas y los registros de Render.',500);
}
