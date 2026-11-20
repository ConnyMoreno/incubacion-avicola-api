-- PROCESO Y DATOS DE INCUBACION AVICOLA
-- Base existente: bd_monitoreo_incubacion_avicola.
-- Incluye las 6 tablas existentes y 8 tablas de manejo, nacimiento y costos.
-- Fuente principal: texto proporcionado por el usuario el 27/10/2026.
-- El DOCX adjunto contiene otra version (45 pollitos); NO se mezclan sus cifras.
-- Este script NO elimina tablas, ciclos, lecturas ni gastos existentes.
-- CREATE TABLE IF NOT EXISTS conserva las tablas ya creadas.
-- Se actualizan los parametros de referencia y se agregan los catalogos faltantes.
-- Humedad activa: 80-85 % desde el dia 18, confirmada por el usuario.
-- Esta confirmacion prevalece sobre la tabla de humedad original del mensaje.
-- Los dias 22 y 23 son de observacion: no se inventa una temperatura no suministrada.

SET NAMES utf8mb4;
CREATE DATABASE IF NOT EXISTS bd_monitoreo_incubacion_avicola
  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE bd_monitoreo_incubacion_avicola;

CREATE TABLE IF NOT EXISTS incubadoras (
  id_incubadora INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  nombre VARCHAR(100) NOT NULL,
  codigo_dispositivo VARCHAR(100) NOT NULL,
  ubicacion VARCHAR(150) NULL,
  activa TINYINT(1) NOT NULL DEFAULT 1,
  creada_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uq_incubadora_dispositivo (codigo_dispositivo)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS etapas_incubacion (
  id_etapa INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  nombre VARCHAR(80) NOT NULL,
  dia_inicio TINYINT UNSIGNED NOT NULL,
  dia_fin TINYINT UNSIGNED NOT NULL,
  temperatura_objetivo DECIMAL(4,1) NOT NULL,
  humedad_minima DECIMAL(5,2) NOT NULL,
  humedad_maxima DECIMAL(5,2) NOT NULL,
  UNIQUE KEY uq_etapa_dias (dia_inicio, dia_fin)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS ciclos_incubacion (
  id_ciclo INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  id_incubadora INT UNSIGNED NOT NULL,
  nombre VARCHAR(100) NOT NULL,
  fecha_inicio DATE NOT NULL,
  duracion_dias TINYINT UNSIGNED NOT NULL DEFAULT 21,
  cantidad_huevos SMALLINT UNSIGNED NULL,
  estado ENUM('activo','finalizado','cancelado') NOT NULL DEFAULT 'activo',
  fecha_fin DATE NULL,
  observaciones TEXT NULL,
  creado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uq_ciclo_incubadora (id_ciclo, id_incubadora),
  KEY idx_ciclo_estado (id_incubadora, estado),
  CONSTRAINT fk_ciclo_incubadora FOREIGN KEY (id_incubadora)
    REFERENCES incubadoras (id_incubadora) ON DELETE RESTRICT
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS configuracion (
  id_incubadora INT UNSIGNED PRIMARY KEY,
  nombre_usuario VARCHAR(100) NOT NULL DEFAULT 'Nayeli',
  alertas_activas TINYINT(1) NOT NULL DEFAULT 1,
  alerta_temperatura TINYINT(1) NOT NULL DEFAULT 1,
  alerta_humedad TINYINT(1) NOT NULL DEFAULT 1,
  alerta_energia TINYINT(1) NOT NULL DEFAULT 1,
  alerta_conexion TINYINT(1) NOT NULL DEFAULT 1,
  notificacion_app TINYINT(1) NOT NULL DEFAULT 1,
  notificacion_telefono TINYINT(1) NOT NULL DEFAULT 1,
  alerta_sonora TINYINT(1) NOT NULL DEFAULT 0,
  tolerancia_temperatura DECIMAL(4,2) NOT NULL DEFAULT 0.50,
  tolerancia_humedad DECIMAL(5,2) NOT NULL DEFAULT 5.00,
  actualizada_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_config_incubadora FOREIGN KEY (id_incubadora)
    REFERENCES incubadoras (id_incubadora) ON DELETE RESTRICT
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS lecturas (
  id_lectura BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  id_incubadora INT UNSIGNED NOT NULL,
  id_ciclo INT UNSIGNED NULL,
  temperatura DECIMAL(5,2) NOT NULL COMMENT 'Grados Celsius',
  humedad DECIMAL(5,2) NOT NULL COMMENT 'Porcentaje de 0 a 100; validar en la API',
  energia_disponible TINYINT(1) NOT NULL DEFAULT 1,
  medida_en DATETIME NOT NULL COMMENT 'Fecha y hora de medición en UTC',
  recibida_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_lectura_incubadora_fecha (id_incubadora, medida_en, id_lectura),
  KEY idx_lectura_ciclo_fecha (id_ciclo, medida_en),
  KEY idx_lectura_ciclo_incubadora (id_ciclo, id_incubadora),
  CONSTRAINT fk_lectura_incubadora FOREIGN KEY (id_incubadora)
    REFERENCES incubadoras (id_incubadora) ON DELETE RESTRICT,
  CONSTRAINT fk_lectura_ciclo FOREIGN KEY (id_ciclo, id_incubadora)
    REFERENCES ciclos_incubacion (id_ciclo, id_incubadora) ON DELETE RESTRICT
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS alertas (
  id_alerta BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  id_incubadora INT UNSIGNED NOT NULL,
  id_ciclo INT UNSIGNED NULL,
  tipo ENUM('temperatura_baja','temperatura_alta','humedad_baja','humedad_alta','sin_energia','sin_conexion') NOT NULL,
  mensaje VARCHAR(255) NOT NULL,
  valor_detectado DECIMAL(7,2) NULL,
  estado ENUM('pendiente','leida','resuelta') NOT NULL DEFAULT 'pendiente',
  creada_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  resuelta_en DATETIME NULL COMMENT 'UTC',
  KEY idx_alerta_estado_fecha (id_incubadora, estado, creada_en),
  KEY idx_alerta_ciclo_incubadora (id_ciclo, id_incubadora),
  CONSTRAINT fk_alerta_incubadora FOREIGN KEY (id_incubadora)
    REFERENCES incubadoras (id_incubadora) ON DELETE RESTRICT,
  CONSTRAINT fk_alerta_ciclo FOREIGN KEY (id_ciclo, id_incubadora)
    REFERENCES ciclos_incubacion (id_ciclo, id_incubadora) ON DELETE RESTRICT
) ENGINE=InnoDB;


-- 7. Pisos del equipo. Capacidad de referencia del plan de dos pisos.
CREATE TABLE IF NOT EXISTS pisos_incubadora (
    id_piso INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    id_incubadora INT UNSIGNED NOT NULL,
    numero_piso TINYINT UNSIGNED NOT NULL,
    capacidad_huevos SMALLINT UNSIGNED NOT NULL,
    descripcion VARCHAR(200) NULL,
    UNIQUE KEY uq_piso_numero (id_incubadora, numero_piso),
    UNIQUE KEY uq_piso_equipo (id_piso, id_incubadora),
    CONSTRAINT fk_piso_incubadora FOREIGN KEY (id_incubadora)
        REFERENCES incubadoras(id_incubadora) ON DELETE RESTRICT,
    CONSTRAINT ck_piso_capacidad CHECK (numero_piso > 0 AND capacidad_huevos > 0)
) ENGINE=InnoDB;

-- 8. Carga real de huevos por piso. No se insertan cargas ficticias.
CREATE TABLE IF NOT EXISTS carga_pisos (
    id_ciclo INT UNSIGNED NOT NULL,
    id_piso INT UNSIGNED NOT NULL,
    id_incubadora INT UNSIGNED NOT NULL,
    huevos_colocados SMALLINT UNSIGNED NOT NULL,
    colocados_en DATETIME NOT NULL COMMENT 'Fecha y hora UTC',
    observaciones VARCHAR(255) NULL,
    PRIMARY KEY (id_ciclo, id_piso),
    KEY idx_carga_ciclo_equipo (id_ciclo, id_incubadora),
    KEY idx_carga_piso_equipo (id_piso, id_incubadora),
    CONSTRAINT fk_carga_ciclo FOREIGN KEY (id_ciclo, id_incubadora)
        REFERENCES ciclos_incubacion(id_ciclo, id_incubadora) ON DELETE RESTRICT,
    CONSTRAINT fk_carga_piso FOREIGN KEY (id_piso, id_incubadora)
        REFERENCES pisos_incubadora(id_piso, id_incubadora) ON DELETE RESTRICT
) ENGINE=InnoDB;

-- 9. Protocolo de referencia; no representa acciones ya realizadas.
CREATE TABLE IF NOT EXISTS protocolos_manejo (
    id_protocolo INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    codigo VARCHAR(60) NOT NULL UNIQUE,
    nombre VARCHAR(150) NOT NULL,
    fuente VARCHAR(255) NOT NULL,
    dias_programados TINYINT UNSIGNED NOT NULL DEFAULT 21,
    dia_maximo_observacion TINYINT UNSIGNED NOT NULL DEFAULT 23,
    dia_inicio_picaje TINYINT UNSIGNED NOT NULL DEFAULT 18,
    dia_inicio_eclosion TINYINT UNSIGNED NOT NULL DEFAULT 20,
    horas_eclosion_min SMALLINT UNSIGNED NOT NULL DEFAULT 48,
    horas_eclosion_max SMALLINT UNSIGNED NOT NULL DEFAULT 72,
    dia_apertura_ventila TINYINT UNSIGNED NOT NULL DEFAULT 18,
    apertura_ventila_inicial DECIMAL(5,2) NOT NULL DEFAULT 0,
    apertura_ventila_nacimiento DECIMAL(5,2) NOT NULL DEFAULT 75,
    horas_caja_con_luz SMALLINT UNSIGNED NOT NULL DEFAULT 24,
    horas_secado_min SMALLINT UNSIGNED NULL,
    horas_secado_max SMALLINT UNSIGNED NULL,
    humedad_nacimiento_texto_min DECIMAL(5,2) NOT NULL DEFAULT 80,
    humedad_nacimiento_texto_max DECIMAL(5,2) NOT NULL DEFAULT 85,
    criterio_humedad ENUM('tabla_por_dias','texto_desde_dia18') NOT NULL DEFAULT 'texto_desde_dia18',
    observaciones TEXT NULL,
    CONSTRAINT ck_protocolo_tiempos CHECK (
        dias_programados > 0 AND dia_maximo_observacion >= dias_programados
        AND horas_eclosion_min <= horas_eclosion_max
    ),
    CONSTRAINT ck_protocolo_ventila CHECK (
        apertura_ventila_inicial BETWEEN 0 AND 100
        AND apertura_ventila_nacimiento BETWEEN 0 AND 100
    )
) ENGINE=InnoDB;

-- 10. Actividades previstas en el protocolo, con dias inclusivos.
CREATE TABLE IF NOT EXISTS actividades_protocolo (
    id_actividad INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    id_protocolo INT UNSIGNED NOT NULL,
    codigo VARCHAR(60) NOT NULL,
    nombre VARCHAR(150) NOT NULL,
    momento ENUM('antes_inicio','dia_incubacion','tras_eclosion') NOT NULL,
    dia_inicio TINYINT UNSIGNED NULL,
    dia_fin TINYINT UNSIGNED NULL,
    duracion_min_minutos INT UNSIGNED NULL,
    duracion_max_minutos INT UNSIGNED NULL,
    instrucciones TEXT NOT NULL,
    UNIQUE KEY uq_actividad_codigo (id_protocolo, codigo),
    CONSTRAINT fk_actividad_protocolo FOREIGN KEY (id_protocolo)
        REFERENCES protocolos_manejo(id_protocolo) ON DELETE RESTRICT,
    CONSTRAINT ck_actividad_dias CHECK (
        (momento = 'dia_incubacion' AND dia_inicio IS NOT NULL AND dia_fin IS NOT NULL
         AND dia_inicio >= 1 AND dia_fin >= dia_inicio)
        OR (momento <> 'dia_incubacion' AND dia_inicio IS NULL AND dia_fin IS NULL)
    ),
    CONSTRAINT ck_actividad_duracion CHECK (
        duracion_min_minutos IS NULL OR duracion_max_minutos IS NULL
        OR duracion_max_minutos >= duracion_min_minutos
    )
) ENGINE=InnoDB;

-- 11. Registro de actividades realmente ejecutadas y ovoscopias.
CREATE TABLE IF NOT EXISTS seguimiento_actividades (
    id_seguimiento BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    id_ciclo INT UNSIGNED NOT NULL,
    id_actividad INT UNSIGNED NOT NULL,
    estado ENUM('pendiente','realizada','omitida') NOT NULL DEFAULT 'pendiente',
    iniciada_en DATETIME NULL COMMENT 'UTC',
    finalizada_en DATETIME NULL COMMENT 'UTC',
    responsable VARCHAR(100) NULL,
    huevos_revisados SMALLINT UNSIGNED NULL,
    huevos_fertiles SMALLINT UNSIGNED NULL,
    huevos_infertiles SMALLINT UNSIGNED NULL,
    rodillos_retirados TINYINT(1) NULL,
    apertura_ventila_porcentaje DECIMAL(5,2) NULL,
    observaciones TEXT NULL,
    KEY idx_seguimiento_ciclo (id_ciclo, id_actividad, iniciada_en),
    CONSTRAINT fk_seguimiento_ciclo FOREIGN KEY (id_ciclo)
        REFERENCES ciclos_incubacion(id_ciclo) ON DELETE RESTRICT,
    CONSTRAINT fk_seguimiento_actividad FOREIGN KEY (id_actividad)
        REFERENCES actividades_protocolo(id_actividad) ON DELETE RESTRICT,
    CONSTRAINT ck_seguimiento_horas CHECK (
        iniciada_en IS NULL OR finalizada_en IS NULL OR finalizada_en >= iniciada_en
    ),
    CONSTRAINT ck_seguimiento_huevos CHECK (
        huevos_revisados IS NULL OR huevos_fertiles IS NULL OR huevos_infertiles IS NULL
        OR huevos_fertiles + huevos_infertiles <= huevos_revisados
    ),
    CONSTRAINT ck_seguimiento_ventila CHECK (
        apertura_ventila_porcentaje IS NULL OR apertura_ventila_porcentaje BETWEEN 0 AND 100
    )
) ENGINE=InnoDB;

-- 12. Nacimientos reales, individuales o por grupos que compartan horarios.
CREATE TABLE IF NOT EXISTS eclosiones (
    id_eclosion BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    id_ciclo INT UNSIGNED NOT NULL,
    cantidad_pollitos SMALLINT UNSIGNED NOT NULL,
    inicio_picaje DATETIME NULL COMMENT 'UTC',
    nacimiento DATETIME NOT NULL COMMENT 'UTC',
    fin_secado DATETIME NULL COMMENT 'UTC',
    entrada_caja_luz DATETIME NULL COMMENT 'UTC',
    salida_caja_luz DATETIME NULL COMMENT 'UTC',
    traslado_galpon DATETIME NULL COMMENT 'UTC',
    observaciones TEXT NULL,
    KEY idx_eclosion_ciclo_fecha (id_ciclo, nacimiento),
    CONSTRAINT fk_eclosion_ciclo FOREIGN KEY (id_ciclo)
        REFERENCES ciclos_incubacion(id_ciclo) ON DELETE RESTRICT,
    CONSTRAINT ck_eclosion_cantidad CHECK (cantidad_pollitos > 0),
    CONSTRAINT ck_eclosion_picaje CHECK (inicio_picaje IS NULL OR nacimiento >= inicio_picaje),
    CONSTRAINT ck_eclosion_secado CHECK (fin_secado IS NULL OR fin_secado >= nacimiento),
    CONSTRAINT ck_eclosion_caja CHECK (
        salida_caja_luz IS NULL OR entrada_caja_luz IS NULL OR salida_caja_luz >= entrada_caja_luz
    )
) ENGINE=InnoDB;

-- 13. Presupuestos; las cantidades calculadas no son registros de compra real.
CREATE TABLE IF NOT EXISTS presupuestos_alimento (
    id_presupuesto INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    id_protocolo INT UNSIGNED NOT NULL,
    id_ciclo INT UNSIGNED NULL,
    codigo VARCHAR(80) NOT NULL UNIQUE,
    alimento VARCHAR(120) NOT NULL,
    cantidad_pollitos SMALLINT UNSIGNED NOT NULL,
    gramos_por_pollito_dia DECIMAL(8,2) NOT NULL,
    dias_alimentacion SMALLINT UNSIGNED NOT NULL,
    kg_compra_planificada DECIMAL(10,3) NOT NULL,
    precio_estimado_kg_usd DECIMAL(10,2) NOT NULL,
    kg_necesarios DECIMAL(12,3) GENERATED ALWAYS AS (
        cantidad_pollitos * gramos_por_pollito_dia * dias_alimentacion / 1000
    ) STORED,
    costo_estimado_usd DECIMAL(12,2) GENERATED ALWAYS AS (
        kg_compra_planificada * precio_estimado_kg_usd
    ) STORED,
    observaciones VARCHAR(255) NULL,
    CONSTRAINT fk_presupuesto_protocolo FOREIGN KEY (id_protocolo)
        REFERENCES protocolos_manejo(id_protocolo) ON DELETE RESTRICT,
    CONSTRAINT fk_presupuesto_ciclo FOREIGN KEY (id_ciclo)
        REFERENCES ciclos_incubacion(id_ciclo) ON DELETE RESTRICT,
    CONSTRAINT ck_presupuesto_valores CHECK (
        cantidad_pollitos > 0 AND gramos_por_pollito_dia > 0 AND dias_alimentacion > 0
        AND kg_compra_planificada >= 0 AND precio_estimado_kg_usd >= 0
    ),
    CONSTRAINT ck_presupuesto_cobertura CHECK (
        kg_compra_planificada * 1000 >= cantidad_pollitos * gramos_por_pollito_dia * dias_alimentacion
    )
) ENGINE=InnoDB;

-- 14. Gastos realmente efectuados. No se insertan gastos supuestos.
CREATE TABLE IF NOT EXISTS gastos (
    id_gasto BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    id_ciclo INT UNSIGNED NULL,
    categoria ENUM('alimento','energia','materiales','otros') NOT NULL,
    descripcion VARCHAR(200) NOT NULL,
    fecha DATE NOT NULL,
    cantidad DECIMAL(12,3) NOT NULL,
    unidad ENUM('kg','unidad','kwh','otro') NOT NULL,
    precio_unitario_usd DECIMAL(12,2) NOT NULL,
    total_usd DECIMAL(14,2) GENERATED ALWAYS AS (cantidad * precio_unitario_usd) STORED,
    comprobante VARCHAR(120) NULL,
    observaciones TEXT NULL,
    KEY idx_gasto_ciclo_fecha (id_ciclo, fecha),
    CONSTRAINT fk_gasto_ciclo FOREIGN KEY (id_ciclo)
        REFERENCES ciclos_incubacion(id_ciclo) ON DELETE RESTRICT,
    CONSTRAINT ck_gasto_valores CHECK (cantidad > 0 AND precio_unitario_usd >= 0)
) ENGINE=InnoDB;

START TRANSACTION;

-- Ajustar los intervalos anteriores sin eliminar identificadores ni mediciones.
-- Dia 18: comienza humedad 80-85 %; dia 19: temperatura todavia 37.6 C.
UPDATE etapas_incubacion
SET dia_fin = 17
WHERE dia_inicio = 13 AND dia_fin IN (18, 19);

UPDATE etapas_incubacion
SET dia_inicio = 18, dia_fin = 19
WHERE dia_inicio = 19 AND dia_fin IN (19, 21);

INSERT INTO etapas_incubacion
    (nombre, dia_inicio, dia_fin, temperatura_objetivo, humedad_minima, humedad_maxima)
VALUES
    ('Desarrollo inicial', 1, 6, 38.0, 60, 60),
    ('Desarrollo embrionario', 7, 12, 37.8, 55, 55),
    ('Desarrollo avanzado', 13, 17, 37.6, 60, 60),
    ('Preparacion para el nacimiento', 18, 19, 37.6, 80, 85),
    ('Eclosion', 20, 21, 37.2, 80, 85)
ON DUPLICATE KEY UPDATE
    nombre = VALUES(nombre),
    temperatura_objetivo = VALUES(temperatura_objetivo),
    humedad_minima = VALUES(humedad_minima),
    humedad_maxima = VALUES(humedad_maxima);

INSERT INTO incubadoras (nombre, codigo_dispositivo)
SELECT 'Incubadora principal', 'INC-001'
WHERE NOT EXISTS (SELECT 1 FROM incubadoras WHERE codigo_dispositivo = 'INC-001');

INSERT INTO configuracion (id_incubadora)
SELECT i.id_incubadora FROM incubadoras i
WHERE i.codigo_dispositivo = 'INC-001'
AND NOT EXISTS (SELECT 1 FROM configuracion c WHERE c.id_incubadora = i.id_incubadora);

-- Solo se agregan pisos ausentes; no se cambia la capacidad de pisos ya configurados.
INSERT INTO pisos_incubadora (id_incubadora, numero_piso, capacidad_huevos, descripcion)
SELECT i.id_incubadora, n.numero, 80, 'Capacidad de referencia del plan de 160, repartido en dos pisos'
FROM incubadoras i
CROSS JOIN (SELECT 1 AS numero UNION ALL SELECT 2 AS numero) n
WHERE i.codigo_dispositivo = 'INC-001'
AND NOT EXISTS (
    SELECT 1 FROM pisos_incubadora p
    WHERE p.id_incubadora = i.id_incubadora AND p.numero_piso = n.numero
);

INSERT INTO protocolos_manejo (
    codigo, nombre, fuente, dias_programados, dia_maximo_observacion,
    dia_inicio_picaje, dia_inicio_eclosion, horas_eclosion_min, horas_eclosion_max,
    dia_apertura_ventila, apertura_ventila_inicial, apertura_ventila_nacimiento,
    horas_caja_con_luz, horas_secado_min, horas_secado_max,
    humedad_nacimiento_texto_min, humedad_nacimiento_texto_max,
    criterio_humedad, observaciones
) VALUES (
    'PROCESO_AVICOLA_160', 'Proceso de incubacion y manejo de 160 pollitos',
    'Texto proporcionado en el chat el 27/10/2026', 21, 23, 18, 20, 48, 72,
    18, 0, 75, 24, NULL, NULL, 80, 85, 'texto_desde_dia18',
    'El usuario confirmo humedad 80-85 % desde el dia 18. Esta instruccion prevalece sobre la tabla original del mensaje, que indicaba 60 % el dia 18 y 65-85 % los dias 19-21. Secado: unas horas, sin duracion exacta. No se especifica temperatura para dias 22-23; no se crea una etapa termica inventada. La humedad de nacimiento confirmada es 80-85 % desde el dia 18. La capacidad 80 por piso es referencia del plan, no un conteo de huevos cargados.'
)
ON DUPLICATE KEY UPDATE
    nombre = VALUES(nombre), fuente = VALUES(fuente),
    dias_programados = VALUES(dias_programados), dia_maximo_observacion = VALUES(dia_maximo_observacion),
    dia_inicio_picaje = VALUES(dia_inicio_picaje), dia_inicio_eclosion = VALUES(dia_inicio_eclosion),
    horas_eclosion_min = VALUES(horas_eclosion_min), horas_eclosion_max = VALUES(horas_eclosion_max),
    dia_apertura_ventila = VALUES(dia_apertura_ventila),
    apertura_ventila_inicial = VALUES(apertura_ventila_inicial),
    apertura_ventila_nacimiento = VALUES(apertura_ventila_nacimiento),
    horas_caja_con_luz = VALUES(horas_caja_con_luz),
    humedad_nacimiento_texto_min = VALUES(humedad_nacimiento_texto_min),
    humedad_nacimiento_texto_max = VALUES(humedad_nacimiento_texto_max),
    criterio_humedad = VALUES(criterio_humedad), observaciones = VALUES(observaciones);

SET @id_protocolo = (
    SELECT id_protocolo FROM protocolos_manejo WHERE codigo = 'PROCESO_AVICOLA_160'
);

INSERT INTO actividades_protocolo
    (id_protocolo, codigo, nombre, momento, dia_inicio, dia_fin,
     duracion_min_minutos, duracion_max_minutos, instrucciones)
VALUES
    (@id_protocolo, 'ARMADO', 'Armar y conectar la incubadora', 'antes_inicio', NULL, NULL, NULL, NULL,
     'Armar y conectar el equipo siguiendo las instrucciones de su fabricante.'),
    (@id_protocolo, 'CARGA', 'Colocar los huevos', 'antes_inicio', NULL, NULL, NULL, NULL,
     'Colocar los huevos sobre los rodillos, distribuidos por piso segun la capacidad real del equipo.'),
    (@id_protocolo, 'VENTILA_INICIAL', 'Posicion inicial de la ventila', 'dia_incubacion', 1, 17, NULL, NULL,
     'Segun el texto del usuario, mantener la ventila cerrada al iniciar el proceso. Apertura de referencia: 0 %.'),
    (@id_protocolo, 'VENTILA_NACIMIENTO', 'Abrir la ventila a tres cuartos', 'dia_incubacion', 18, 23, NULL, NULL,
     'Desde el dia 18, abrir la ventila tres cuartos: 75 % de apertura, segun el texto proporcionado.'),
    (@id_protocolo, 'HUMEDAD_NACIMIENTO', 'Elevar humedad para nacimiento', 'dia_incubacion', 18, 23, NULL, NULL,
     'Mantener humedad de referencia 80-85 % desde el dia 18, segun la confirmacion del usuario.'),
    (@id_protocolo, 'OVOSCOPIA', 'Realizar ovoscopia', 'dia_incubacion', 18, 19, 5, 10,
     'Revisar todos los huevos en el ovoscopio del equipo y separar fertiles de infertiles. Duracion de referencia del procedimiento: 5-10 minutos, segun el texto.'),
    (@id_protocolo, 'RODILLOS', 'Retirar los rodillos', 'dia_incubacion', 18, 19, NULL, NULL,
     'Retirar los rodillos durante la preparacion para el nacimiento, para favorecer la absorcion de humedad, segun el texto proporcionado.'),
    (@id_protocolo, 'PICAJE', 'Observar el inicio del picaje', 'dia_incubacion', 18, 19, NULL, NULL,
     'El texto situa el inicio de rotura del cascaron en el dia 18.'),
    (@id_protocolo, 'ECLOSION', 'Observar la eclosion', 'dia_incubacion', 20, 23, 2880, 4320,
     'Nacimiento previsto desde el dia 20 al 21, con observacion hasta el dia 23. El texto indica 48-72 horas para la eclosion de un huevo.'),
    (@id_protocolo, 'SECADO', 'Mantener hasta que se sequen', 'tras_eclosion', NULL, NULL, NULL, NULL,
     'Mantener los pollitos unas horas en la incubadora hasta que se sequen. No se especifica una cantidad exacta de horas.'),
    (@id_protocolo, 'CAJA_LUZ', 'Pasar a caja con luz', 'tras_eclosion', NULL, NULL, 1440, 1440,
     'Despues del secado, mantenerlos en una caja improvisada con luz durante un dia: 24 horas.'),
    (@id_protocolo, 'GALPON', 'Trasladar al galpon adaptado', 'tras_eclosion', NULL, NULL, NULL, NULL,
     'Al finalizar la etapa en caja con luz, trasladar los pollitos al galpon adaptado.')
ON DUPLICATE KEY UPDATE
    nombre = VALUES(nombre), momento = VALUES(momento),
    dia_inicio = VALUES(dia_inicio), dia_fin = VALUES(dia_fin),
    duracion_min_minutos = VALUES(duracion_min_minutos),
    duracion_max_minutos = VALUES(duracion_max_minutos), instrucciones = VALUES(instrucciones);

INSERT INTO presupuestos_alimento (
    id_protocolo, id_ciclo, codigo, alimento, cantidad_pollitos,
    gramos_por_pollito_dia, dias_alimentacion,
    kg_compra_planificada, precio_estimado_kg_usd, observaciones
) VALUES (
    @id_protocolo, NULL, 'ALIMENTO_160_POLLITOS_3_DIAS', 'Balanceado pre-inicial',
    160, 10, 3, 5, 1.00,
    'Referencia del usuario: 80 por piso. 160 x 10 g x 3 dias = 4800 g = 4.8 kg; compra planificada 5 kg a aproximadamente USD 1/kg. Presupuesto, no gasto pagado.'
)
ON DUPLICATE KEY UPDATE
    alimento = VALUES(alimento), cantidad_pollitos = VALUES(cantidad_pollitos),
    gramos_por_pollito_dia = VALUES(gramos_por_pollito_dia),
    dias_alimentacion = VALUES(dias_alimentacion),
    kg_compra_planificada = VALUES(kg_compra_planificada),
    precio_estimado_kg_usd = VALUES(precio_estimado_kg_usd),
    observaciones = VALUES(observaciones);

COMMIT;

CREATE OR REPLACE SQL SECURITY INVOKER VIEW vw_presupuesto_alimento AS
SELECT b.id_presupuesto, b.codigo, b.id_ciclo, b.alimento,
       b.cantidad_pollitos, b.gramos_por_pollito_dia, b.dias_alimentacion,
       b.cantidad_pollitos * b.gramos_por_pollito_dia AS gramos_diarios_total,
       b.kg_necesarios, b.kg_compra_planificada,
       b.kg_compra_planificada - b.kg_necesarios AS kg_margen_compra,
       b.precio_estimado_kg_usd, b.costo_estimado_usd
FROM presupuestos_alimento b;

CREATE OR REPLACE SQL SECURITY INVOKER VIEW vw_resumen_ciclos AS
SELECT c.id_ciclo, i.nombre AS incubadora, c.nombre AS ciclo,
       c.fecha_inicio, c.duracion_dias, c.estado, c.cantidad_huevos,
       COALESCE(h.huevos_colocados, 0) AS huevos_cargados_por_pisos,
       COALESCE(e.pollitos_nacidos, 0) AS pollitos_nacidos,
       COALESCE(g.gasto_total_usd, 0) AS gasto_total_usd
FROM ciclos_incubacion c
JOIN incubadoras i ON i.id_incubadora = c.id_incubadora
LEFT JOIN (SELECT id_ciclo, SUM(huevos_colocados) AS huevos_colocados
           FROM carga_pisos GROUP BY id_ciclo) h ON h.id_ciclo = c.id_ciclo
LEFT JOIN (SELECT id_ciclo, SUM(cantidad_pollitos) AS pollitos_nacidos
           FROM eclosiones GROUP BY id_ciclo) e ON e.id_ciclo = c.id_ciclo
LEFT JOIN (SELECT id_ciclo, SUM(total_usd) AS gasto_total_usd
           FROM gastos GROUP BY id_ciclo) g ON g.id_ciclo = c.id_ciclo;

-- Comprobaciones de los datos de referencia.
SELECT nombre, dia_inicio, dia_fin, temperatura_objetivo, humedad_minima, humedad_maxima
FROM etapas_incubacion ORDER BY dia_inicio;

-- Resultado esperado del presupuesto: 1600 g/dia, 4.800 kg necesarios,
-- 5.000 kg de compra, 0.200 kg de margen y USD 5.00 presupuestados.
SELECT * FROM vw_presupuesto_alimento
WHERE codigo = 'ALIMENTO_160_POLLITOS_3_DIAS';
