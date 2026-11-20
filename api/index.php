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
function currentCycle(PDO $db, int $id) {
    return query($db,"SELECT * FROM ciclos_incubacion WHERE id_incubadora=? AND estado='activo' ORDER BY id_ciclo DESC LIMIT 1",[$id])->fetch() ?: null;
}
function stage(PDO $db, $cycle, string $tz) {
    if (!$cycle) return null;
    $start=new DateTimeImmutable($cycle['fecha_inicio'],new DateTimeZone($tz));
    $today=new DateTimeImmutable('today',new DateTimeZone($tz));
    $day=(int)$start->diff($today)->format('%r%a')+1;
    $row=query($db,'SELECT * FROM etapas_incubacion WHERE ? BETWEEN dia_inicio AND dia_fin ORDER BY dia_inicio LIMIT 1',[$day])->fetch();
    return ['dia'=>$day,'duracion_dias'=>(int)$cycle['duracion_dias'],'parametros'=>$row ?: null];
}
try {
    $cfg=require __DIR__.'/config.php';
    $key=$_SERVER['HTTP_X_API_KEY'] ?? '';
    if (empty($cfg['api_token']) || !hash_equals($cfg['api_token'],$key)) fail('Clave de la API incorrecta.',401);
    $action=$_GET['accion'] ?? 'estado';
    $methods=['estado'=>'GET','historial'=>'GET','guardar_configuracion'=>'POST','registrar_lectura'=>'POST'];
    if (!isset($methods[$action])) fail('Acción desconocida.',404);
    if ($_SERVER['REQUEST_METHOD']!==$methods[$action]) { header('Allow: '.$methods[$action]);fail('Método no permitido.',405); }
    $pg = $cfg['database'];
    $dsn = 'pgsql:host='.$pg['host'].';port='.$pg['port'].';dbname='.$pg['database'].';sslmode=require';
    $db=new PDO($dsn,$pg['user'],$pg['password'],[
        PDO::ATTR_ERRMODE=>PDO::ERRMODE_EXCEPTION,PDO::ATTR_DEFAULT_FETCH_MODE=>PDO::FETCH_ASSOC,PDO::ATTR_EMULATE_PREPARES=>false
    ]);
    $db->exec("SET TIME ZONE 'UTC'");
    $data=$_SERVER['REQUEST_METHOD']==='POST' ? readBody() : [];
    $id=integer($data['id_incubadora'] ?? $_GET['id_incubadora'] ?? 1);
    $incubator=query($db,'SELECT * FROM incubadoras WHERE id_incubadora=? AND activa=1',[$id])->fetch();
    if (!$incubator) fail('La incubadora no existe o está inactiva.',404);
    if ($action==='estado') {
        $cycle=currentCycle($db,$id);
        respond(['ok'=>true,'incubadora'=>$incubator,'ciclo'=>$cycle,'etapa'=>stage($db,$cycle,$cfg['timezone']),
            'configuracion'=>query($db,'SELECT * FROM configuracion WHERE id_incubadora=?',[$id])->fetch() ?: null,
            'lectura'=>query($db,'SELECT * FROM lecturas WHERE id_incubadora=? ORDER BY medida_en DESC,id_lectura DESC LIMIT 1',[$id])->fetch() ?: null,
            'alertas'=>query($db,'SELECT id_alerta,tipo,mensaje,estado,creada_en FROM alertas WHERE id_incubadora=? ORDER BY creada_en DESC,id_alerta DESC LIMIT 20',[$id])->fetchAll(),
            'etapas'=>query($db,'SELECT * FROM etapas_incubacion ORDER BY dia_inicio')->fetchAll(),'timezone'=>$cfg['timezone']]);
    }
    if ($action==='historial') {
        $period=$_GET['periodo'] ?? 'dia';
        if (!in_array($period,['dia','semana','mes'],true)) fail('Periodo inválido.',422);
        $tz=new DateTimeZone($cfg['timezone']);$start=new DateTimeImmutable('today',$tz);
        if ($period==='semana') $start=$start->modify('-6 days');
        if ($period==='mes') $start=$start->modify('-29 days');
        $end=(new DateTimeImmutable('tomorrow',$tz))->setTimezone(new DateTimeZone('UTC'));
        $startUtc=$start->setTimezone(new DateTimeZone('UTC'))->format('Y-m-d H:i:s');
        $rows=query($db,'SELECT temperatura,humedad,medida_en FROM lecturas WHERE id_incubadora=? AND medida_en>=? AND medida_en<? ORDER BY medida_en,id_lectura',[$id,$startUtc,$end->format('Y-m-d H:i:s')]);
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
        $args=[$id];foreach ($flags as $name) {
            if (!array_key_exists($name,$data) || !in_array($data[$name],[true,false,0,1],true)) fail('Preferencia inválida: '.$name,422);
            $args[]=(int)$data[$name];
        }
        $name=trim((string)($data['nombre_usuario'] ?? 'Nayeli'));
        if ($name==='' || strlen($name)>100) fail('Nombre inválido.',422);
        $date=(string)($data['fecha_inicio'] ?? '');
        $parsed=DateTimeImmutable::createFromFormat('!Y-m-d',$date,new DateTimeZone($cfg['timezone']));
        if (!$parsed || $parsed->format('Y-m-d')!==$date || $parsed>new DateTimeImmutable('today',new DateTimeZone($cfg['timezone']))) fail('Fecha de inicio inválida.',422);
        $args[]=$name;$db->beginTransaction();
        query($db,'SELECT id_incubadora FROM incubadoras WHERE id_incubadora=? FOR UPDATE',[$id]);
        query($db,'INSERT INTO configuracion (id_incubadora,'.implode(',',$flags).',nombre_usuario) VALUES (?,?,?,?,?,?,?,?,?,?) ON CONFLICT (id_incubadora) DO UPDATE SET '.implode(',',array_map(function($x){return "$x=EXCLUDED.$x";},$flags)).',nombre_usuario=EXCLUDED.nombre_usuario',$args);
        $cycle=currentCycle($db,$id);
        if ($cycle) query($db,'UPDATE ciclos_incubacion SET fecha_inicio=? WHERE id_ciclo=?',[$date,$cycle['id_ciclo']]);
        else query($db,"INSERT INTO ciclos_incubacion (id_incubadora,nombre,fecha_inicio) VALUES (?,'Ciclo de incubación',?)",[$id,$date]);
        $db->commit();respond(['ok'=>true,'mensaje'=>'Configuración guardada en PostgreSQL.']);
    }
    if ($action==='registrar_lectura') {
        foreach (['temperatura','humedad'] as $field) if (!isset($data[$field]) || !is_numeric($data[$field]) || !is_finite((float)$data[$field])) fail('Lectura inválida.',422);
        $t=(float)$data['temperatura'];$h=(float)$data['humedad'];
        if ($t < -100 || $t>150 || $h<0 || $h>100) fail('Lectura fuera de límites físicos.',422);
        $power=$data['energia_disponible'] ?? true;
        if (!in_array($power,[true,false,0,1],true)) fail('Estado de energía inválido.',422);
        $cycle=currentCycle($db,$id);
        $stmt=query($db,"INSERT INTO lecturas (id_incubadora,id_ciclo,temperatura,humedad,energia_disponible,medida_en) VALUES (?,?,?,?,?,CURRENT_TIMESTAMP AT TIME ZONE 'UTC') RETURNING id_lectura",[$id,$cycle?$cycle['id_ciclo']:null,$t,$h,(int)$power]);
        respond(['ok'=>true,'id_lectura'=>(int)$stmt->fetchColumn()],201);
    }
} catch (Throwable $e) {
    if (isset($db) && $db->inTransaction()) $db->rollBack();
    error_log('Incubacion API: '.$e->getMessage());
    fail('No se pudo consultar PostgreSQL. Revisa las variables de conexión de Neon, las tablas y los registros de Render.',500);
}
