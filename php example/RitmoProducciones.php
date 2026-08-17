<?php

namespace App;

use Illuminate\Database\Eloquent\Model;

class RitmoProducciones extends Model
{
    use Modelo;
    protected $table = 'ritmoproducciones';
    public function getEntidad()
    {
        return 'ritmoproducciones';
    }
    public $timestamps = false;

    public static function generatePanelSala()
    {
        $ritmo = \App\RitmoProducciones::where('__numero', $_GET['__article__id'])->where('__sala', 3)->first();
        $res   = [];
        $inner = [];

        if ($ritmo) {
            $inner['__articulo____descripcion'] = $ritmo->__producto;
            $inner['__articulo____erp'] = $ritmo->__numero;
            $inner['__min'] = $ritmo->__min;
            $inner['__max'] = $ritmo->__max;
            $inner['__maxweight'] = $ritmo->__maxweight;
            $inner['__pesonominal'] = $ritmo->__pesonominal;
        } else {
            $inner['__min'] = 0;
            $inner['__max'] = 0;
            $inner['__maxweight'] = 0;
            $inner['__pesonominal'] = 0;
        }
        $sala1_article_id = 0;
        if (isset($_GET['sala1_article_id'])) $sala1_article_id = $_GET['sala1_article_id'];
        $ritmo = \App\RitmoProducciones::where('__numero', $sala1_article_id)->where('__sala', 1)->first();
        if ($ritmo) {
            $inner['SEG__articulo____descripcion'] = $ritmo->__producto;
            $inner['SEG__articulo____erp'] = $ritmo->__numero;
            $inner['SEG__min'] = $ritmo->__min;
            $inner['SEG__max'] = $ritmo->__max;
            $inner['SEG__maxweight'] = $ritmo->__maxweight;
            $inner['SEG__pesonominal'] = $ritmo->__pesonominal;
        } else {
            $inner['SEG__min'] = 0;
            $inner['SEG__max'] = 0;
            $inner['SEG__maxweight'] = 0;
            $inner['SEG__pesonominal'] = 0;
        }

        $res[] = $inner;
        return $res;
    }

    public static function getProductSettings()
    {
        if (isset($_POST['id']) && isset($_POST['numero']) && isset($_POST['producto']) && isset($_POST['min']) && isset($_POST['max'])) {
            if ($_POST['id'] == 'new') {
                $product = new \App\RitmoProducciones();
            } else {
                $product = \App\RitmoProducciones::where('id', ($_POST['id']))->first();
            }
            $product->__numero      = $_POST['numero'] ? $_POST['numero'] : 0;
            $product->__producto    = $_POST['producto'] ?  $_POST['producto'] : '_';
            $product->__min         = $_POST['min'] ? $_POST['min'] : 0;
            $product->__max         = $_POST['max'] ? $_POST['max'] : 0;
            $product->__pesonominal = $_POST['pesonominal'] ? $_POST['pesonominal'] : 0;
            $product->__maxweight   = $_POST['peso'] ?  $_POST['peso'] : 0;
            $product->__sala        = $_POST['sala'] ?  $_POST['sala'] : 3;
            $product->__time        = date('Y-m-d H:i:s');
            if (strtolower(trim($_POST['checkbox'])) == 'true') {
                $product->__checked = 1;
            } else {
                $product->__checked = 0;
            }
            if (strtolower(trim($_POST['checked__frito'])) == 'true') {
                $product->__checked__frito = 1;
            } else {
                $product->__checked__frito = 0;
            }

            $product->save();
        }
        $products = \App\RitmoProducciones::where('id', '>', 0)->orderBy('__numero', 'DESC')->get();
        return $products;
    }




    // funcion nueva de valoracion de rendimiento, valoracion de tramos ventana movil de 5 minutos de duracion con muestra de 5 minutos

    public static function informeProducciones($materiales, $xLinea)
    {
        // Cargar TODAS las filas una sola vez (1 query)
        $RITMOS = \App\RitmoProducciones::all();

        // Indices en memoria para lookup O(1)
        $ritmosPorNumSala = [];
        $ritmosPorNum     = [];

        foreach ($RITMOS as $r) {
            $num  = (string) $r->__numero;
            $sala = (string) $r->__sala;

            // Indice combinado: clave "num|sala"
            $ritmosPorNumSala[$num . '|' . $sala] = $r;

            // Indice solo por numero: si hay varias filas con el mismo numero,
            // nos quedamos con la primera (replica el ->first() original)
            if (!isset($ritmosPorNum[$num])) {
                $ritmosPorNum[$num] = $r;
            }
        }


        /*
    CONFIGURACION INDUSTRIAL DEL ANALISIS

    Punto cada:
        150 segundos = 2,5 minutos

    Ventana de valoracion:
        300 segundos = 5 minutos

    Esto significa:
        - cada 2,5 minutos genero un punto
        - pero cada punto calcula el ritmo mirando los ultimos 5 minutos

    Formula:
        kg producidos en los ultimos 5 minutos * 12 = kg/hora

    Regla de parada:
        una parada sigue siendo parada si supera 5 minutos
    */
        $segundosPasoAnalisis      = 150;   // 2,5 minutos: frecuencia de puntos
        $segundosVentanaValoracion = 300;   // 5 minutos: ventana movil valorada
        $segundosParadaMinima      = 300;   // 5 minutos: regla para detectar parada
        $segundosParadaLarga       = 18000; // 5 horas
        $segundosBocadillo         = 1800;  // 30 minutos

        $multiplicadorRitmoHora = 3600 / $segundosVentanaValoracion; // 12

        $article_index = 0;
        $article_old = '';
        $articulos_array = [];
        $workData = [];
        $total_kilos = 0;
        $anteUltimaLineaProduccion = null;

        foreach ($materiales as $mat) {

            $current_art_num = $mat['ArticleNumber'];
            $current_article = $mat['ArticleName'];
            $BatchNumber = $mat['BatchNumber'];

            if ($article_old != $current_art_num . $current_article) {
                $article_index++;
            }

            $article_old = $current_art_num . $current_article;
            $leenda = $BatchNumber . '___' . $article_index . '_______' . $current_art_num . '_______' . $current_article;

            if (!in_array($leenda, $articulos_array)) {

                $frito_si_no = null;

                $claveSala = ((string) $current_art_num) . '|' . ((string) $xLinea);
                $product   = $ritmosPorNumSala[$claveSala] ?? null;

                if ($product) {
                    $frito_si_no = $product->__checked__frito;
                }

                $anteUltimaLineaProduccion = substr($mat['CreationDate'], 0, 19);

                $articulos_array[] = $leenda;

                $workData[$leenda] = [
                    'frito_si_no' => $frito_si_no,
                    'linea' => $xLinea,
                    'numero' => $current_art_num,
                    'nombre' => $current_article,
                    'title' => $leenda,

                    'total_kilos' => 0,
                    'ritmo_max_var' => 0,

                    'segundosHornadaLaboral' => 0,
                    'segundos_perdidos_5horas' => [],
                    'segPerdidos5horasTotal' => 0,
                    '_' => '',

                    'segundos_perdidos_5minutos' => [],
                    'segundos_pedidos_5minutos' => 0,
                    'total_horas_perdidos_5minutos' => 0,
                    'hh_ii_horas_perdidas_5minutos' => 0,
                    '__' => '',

                    'ritmo_medio_por_hora_inicio_fin' => 0,
                    '___' => '',

                    'fechaIni' => substr($mat['CreationDate'], 0, 19),
                    'fechaFin' => '',
                    '______' => '',

                    'difInicioFinSegundos' => 0,
                    'difInicioFinHoras' => 0,
                    'duracionProduccionInicioFin' => 0,

                    'duracionProduccionRealSegundos' => 0,
                    'duracionProduccionRealHoras' => 0,
                    'duracionProduccionRealLeenda' => 0,
                    'ritmoProduccionReal' => 0,

                    '____' => '',

                    'ajusteMin' => 0,
                    'ajusteMax' => 0,

                    'ritmo_de_horas_productivas' => 0,

                    'linesTodas' => [],
                    'bocadillos' => 0,

                    'tiempoYValor' => [],
                    'tiempoPerdidoJustificado' => 0
                ];

                $total_kilos = 0;

                $ritmoAjustes = $ritmosPorNum[(string) $current_art_num] ?? null;
                if ($ritmoAjustes) {
                    $workData[$leenda]['ajusteMin'] = $ritmoAjustes->__min;
                    $workData[$leenda]['ajusteMax'] = $ritmoAjustes->__max;
                }
            }

            $total_kilos += $mat['ActualNetWeightValue'];
            $workData[$leenda]['total_kilos'] = $total_kilos;

            /*
        Fecha fin de la produccion actual.
        */
            $workData[$leenda]['fechaFin'] = substr($mat['CreationDate'], 0, 19);

            $fechaIni = strtotime($workData[$leenda]['fechaIni']);
            $fechaFin = strtotime($workData[$leenda]['fechaFin']);

            /*
        Guardamos cada pesada con su tiempo.
        Esto sera la base para la ventana movil de 5 minutos.
        */
            $workData[$leenda]['tiempoYValor'][] = [
                'tiempo' => $workData[$leenda]['fechaFin'],
                'valor' => $mat['ActualNetWeightValue']
            ];

            $segundosAnteUltimaLinea = strtotime($anteUltimaLineaProduccion);

            /*
        Paradas mayores de 5 minutos y menores o iguales a 5 horas.

        IMPORTANTE:
        aqui se mantiene la regla de 5 minutos.
        No depende de que el punto de analisis sea cada 2,5 minutos.
        */
            if (
                $fechaFin - $segundosAnteUltimaLinea > $segundosParadaMinima &&
                $fechaFin - $segundosAnteUltimaLinea <= $segundosParadaLarga
            ) {
                $segundosPerdidos = $fechaFin - $segundosAnteUltimaLinea;

                $workData[$leenda]['segundos_perdidos_5minutos'][] = $segundosPerdidos;

                $totalSegundosPerdidos = 0;

                foreach ($workData[$leenda]['segundos_perdidos_5minutos'] as $tiempoPerdido) {
                    $totalSegundosPerdidos += $tiempoPerdido;
                }

                $workData[$leenda]['segundos_pedidos_5minutos'] = $totalSegundosPerdidos;
                $workData[$leenda]['total_horas_perdidos_5minutos'] = $totalSegundosPerdidos / 3600;
                $workData[$leenda]['hh_ii_horas_perdidas_5minutos'] = sprintf(
                    "%02d:%02d",
                    floor($totalSegundosPerdidos / 3600),
                    floor(($totalSegundosPerdidos % 3600) / 60)
                );
            }

            /*
        Paradas mayores de 5 horas.
        */
            if ($fechaFin - $segundosAnteUltimaLinea > $segundosParadaLarga) {

                $segundosMayor5Horas = $fechaFin - $segundosAnteUltimaLinea;

                $workData[$leenda]['segundos_perdidos_5horas'][] = $segundosMayor5Horas;

                $total5HorasPerdidos = 0;

                foreach ($workData[$leenda]['segundos_perdidos_5horas'] as $tPerdido) {
                    $total5HorasPerdidos += $tPerdido;
                }

                $workData[$leenda]['segPerdidos5horasTotal'] = $total5HorasPerdidos;
            }

            $diferencia_segundos = $fechaFin - $fechaIni;

            if ($diferencia_segundos != 0) {

                $workData[$leenda]['difInicioFinSegundos'] = $diferencia_segundos;

                $workData[$leenda]['difInicioFinHoras'] = (
                    $diferencia_segundos - $workData[$leenda]['segPerdidos5horasTotal']
                ) / 3600;

                /*
            Ritmo medio desde inicio hasta fin,
            quitando solo paradas mayores de 5 horas.
            */
                if ($workData[$leenda]['difInicioFinHoras'] != 0) {
                    $workData[$leenda]['ritmo_medio_por_hora_inicio_fin'] =
                        $workData[$leenda]['total_kilos'] / $workData[$leenda]['difInicioFinHoras'];
                } else {
                    $workData[$leenda]['ritmo_medio_por_hora_inicio_fin'] = 0;
                }

                $horas = floor($diferencia_segundos / 3600);
                $minutos = floor(($diferencia_segundos % 3600) / 60);

                $workData[$leenda]['duracionProduccionInicioFin'] =
                    str_pad($horas, 2, '0', STR_PAD_LEFT) . ':' .
                    str_pad($minutos, 2, '0', STR_PAD_LEFT);
            }

            /*
        Guardamos las horas de todas las lineas para detectar bocadillos.
        */
            $fechaItem = explode(' ', $workData[$leenda]['fechaFin']);
            $workData[$leenda]['linesTodas'][] = $fechaItem[1];

            $anteUltimaLineaProduccion = $workData[$leenda]['fechaFin'];
        }

        $out = [];

        foreach ($workData as $key => $obj) {

            /*
        Detectar bocadillos.
        */
            $array_tiempos = $workData[$key]['linesTodas'];

            for ($i = 0; $i < count($array_tiempos) - 1; $i++) {

                $first = $array_tiempos[$i];
                $second = $array_tiempos[$i + 1];

                if (
                    ($first <= '02:15:00' && $second > '02:15:00') ||
                    ($first <= '10:15:00' && $second > '10:15:00') ||
                    ($first <= '18:15:00' && $second > '18:15:00')
                ) {
                    $workData[$key]['bocadillos'] += 1;
                }
            }

            /*
        Tiempo perdido justificado:
        - bocadillos
        - paradas mayores de 5 horas

        Este valor se usa como bolsa de tiempo que puede no penalizarse
        cuando el ritmo sale bajo por una causa justificada.
        */
            $workData[$key]['tiempoPerdidoJustificado'] =
                ($workData[$key]['bocadillos'] * $segundosBocadillo) +
                $workData[$key]['segPerdidos5horasTotal'];

            /*
        Dur L:
        tiempo laboral total quitando paradas mayores de 5 horas.
        */
            $workData[$key]['segTiempoLaboral'] =
                $workData[$key]['difInicioFinSegundos'] -
                $workData[$key]['segPerdidos5horasTotal'];

            $workData[$key]['segTiempoLaboralHoras'] =
                $workData[$key]['segTiempoLaboral'] / 3600;

            $workData[$key]['segTiempoLaboralLeenda'] = sprintf(
                "%02d:%02d",
                floor($workData[$key]['segTiempoLaboral'] / 3600),
                floor(($workData[$key]['segTiempoLaboral'] % 3600) / 60)
            );

            /*
        Ritmo R:
        quitando:
        - paradas mayores de 5 minutos
        - paradas mayores de 5 horas
        - bocadillos
        */
            $workData[$key]['duracionProduccionRealSegundos'] =
                (
                    $workData[$key]['difInicioFinSegundos'] -
                    $workData[$key]['segundos_pedidos_5minutos'] -
                    $workData[$key]['segPerdidos5horasTotal']
                ) -
                ($workData[$key]['bocadillos'] * $segundosBocadillo);

            $workData[$key]['duracionProduccionRealHoras'] =
                $workData[$key]['duracionProduccionRealSegundos'] / 3600;

            $workData[$key]['duracionProduccionRealLeenda'] = sprintf(
                "%02d:%02d",
                floor($workData[$key]['duracionProduccionRealSegundos'] / 3600),
                floor(($workData[$key]['duracionProduccionRealSegundos'] % 3600) / 60)
            );

            if ($workData[$key]['duracionProduccionRealHoras'] != 0) {
                $workData[$key]['ritmo_de_horas_productivas'] =
                    $workData[$key]['total_kilos'] / $workData[$key]['duracionProduccionRealHoras'];
            } else {
                $workData[$key]['ritmo_de_horas_productivas'] = 0;
            }

            /*
        Ritmo C/B:
        quitando:
        - paradas mayores de 5 minutos
        - paradas mayores de 5 horas

        No quita bocadillo.
        */
            $workData[$key]['CBduracionProduccionRealSegundos'] =
                $workData[$key]['difInicioFinSegundos'] -
                $workData[$key]['segundos_pedidos_5minutos'] -
                $workData[$key]['segPerdidos5horasTotal'];

            $workData[$key]['CBduracionProduccionRealHoras'] =
                $workData[$key]['CBduracionProduccionRealSegundos'] / 3600;

            $workData[$key]['CBduracionProduccionRealLeenda'] = sprintf(
                "%02d:%02d",
                floor($workData[$key]['CBduracionProduccionRealSegundos'] / 3600),
                floor(($workData[$key]['CBduracionProduccionRealSegundos'] % 3600) / 60)
            );

            if ($workData[$key]['CBduracionProduccionRealHoras'] != 0) {
                $workData[$key]['CBritmo_de_horas_productivas'] =
                    $workData[$key]['total_kilos'] / $workData[$key]['CBduracionProduccionRealHoras'];
            } else {
                $workData[$key]['CBritmo_de_horas_productivas'] = 0;
            }

        /*
        * Si Ritmo R es negativo o cero,
        * usamos el valor de Ritmo C/B.
        */
        if ($workData[$key]['ritmo_de_horas_productivas'] <= 0) {
             $workData[$key]['ritmo_de_horas_productivas'] = $workData[$key]['CBritmo_de_horas_productivas'];
        }


            /*
        FRANJAS HORARIAS CON VENTANA MOVIL

        - punto cada 2,5 minutos
        - cada punto mira los ultimos 5 minutos
        - kg de los ultimos 5 minutos * 12
        - se añade un último punto exacto en fechaFin si queda tramo parcial
        */
            $fechaIniA = strtotime($workData[$key]['fechaIni']);
            $fechaFinB = strtotime($workData[$key]['fechaFin']);

            $franjaHoraria = [];

            /*
        Empezamos cuando ya existe una ventana completa de 5 minutos.
        Así evitamos valorar como bajo el arranque solo porque todavía
        no han pasado 5 minutos.
        */
            $primerPuntoValoracion = $fechaIniA + $segundosVentanaValoracion;

            $ritmo_max_var = 0;
            $ultimoTimestampGenerado = null;

            for (
                $currentTimestamp = $primerPuntoValoracion;
                $currentTimestamp <= $fechaFinB;
                $currentTimestamp += $segundosPasoAnalisis
            ) {
                $ventanaInicio = $currentTimestamp - $segundosVentanaValoracion;
                $kgVentana = 0;

                for ($q = 0; $q < count($workData[$key]['tiempoYValor']); $q++) {

                    $tiempoPesada = strtotime($workData[$key]['tiempoYValor'][$q]['tiempo']);

                    if ($tiempoPesada >= $ventanaInicio && $tiempoPesada <= $currentTimestamp) {
                        $kgVentana += $workData[$key]['tiempoYValor'][$q]['valor'];
                    }
                }

                /*
            Ritmo del punto:
            kg de los ultimos 5 minutos * 12 = kg/hora
            */
                $ritmoHora = $kgVentana * $multiplicadorRitmoHora;

                if ($ritmo_max_var < $ritmoHora) {
                    $ritmo_max_var = $ritmoHora;
                }

                $franjaHoraria[] = [
                    'tiempo' => date('Y-m-d H:i:s', $currentTimestamp),
                    'ventanaIni' => date('Y-m-d H:i:s', $ventanaInicio),
                    'ventanaFin' => date('Y-m-d H:i:s', $currentTimestamp),
                    'kgVentana' => $kgVentana,
                    'ritmo' => $ritmoHora,

                    /*
                Peso real de este punto para calcular % Bajo / Normal / Alto.
                Los puntos normales pesan 150 segundos.
                */
                    'segundosPeso' => $segundosPasoAnalisis,
                    'esUltimoTramo' => false
                ];

                $ultimoTimestampGenerado = $currentTimestamp;
            }

            /*
        Añadir último punto exacto en fechaFin si queda un trozo final sin valorar.

        Ejemplo:
            ultimo punto: 11:21:34
            fecha fin:    11:23:33

        En ese caso generamos un punto final:
            ventana: fechaFin - 5 minutos hasta fechaFin
            peso: segundos reales desde el último punto hasta fechaFin
        */
            if (
                $fechaFinB >= $primerPuntoValoracion &&
                $ultimoTimestampGenerado !== null &&
                $ultimoTimestampGenerado < $fechaFinB
            ) {
                $currentTimestamp = $fechaFinB;
                $ventanaInicio = $currentTimestamp - $segundosVentanaValoracion;
                $kgVentana = 0;

                for ($q = 0; $q < count($workData[$key]['tiempoYValor']); $q++) {

                    $tiempoPesada = strtotime($workData[$key]['tiempoYValor'][$q]['tiempo']);

                    if ($tiempoPesada >= $ventanaInicio && $tiempoPesada <= $currentTimestamp) {
                        $kgVentana += $workData[$key]['tiempoYValor'][$q]['valor'];
                    }
                }

                $ritmoHora = $kgVentana * $multiplicadorRitmoHora;

                if ($ritmo_max_var < $ritmoHora) {
                    $ritmo_max_var = $ritmoHora;
                }

                $segundosPesoUltimoTramo = $fechaFinB - $ultimoTimestampGenerado;

                if ($segundosPesoUltimoTramo > 0) {
                    $franjaHoraria[] = [
                        'tiempo' => date('Y-m-d H:i:s', $currentTimestamp),
                        'ventanaIni' => date('Y-m-d H:i:s', $ventanaInicio),
                        'ventanaFin' => date('Y-m-d H:i:s', $currentTimestamp),
                        'kgVentana' => $kgVentana,
                        'ritmo' => $ritmoHora,

                        /*
                    Este último punto no pesa 150 segundos.
                    Pesa solo el trozo real que faltaba hasta fechaFin.
                    */
                        'segundosPeso' => $segundosPesoUltimoTramo,
                        'esUltimoTramo' => true
                    ];
                }
            }

            $workData[$key]['franjaHoraria'] = $franjaHoraria;
            $workData[$key]['ritmo_max_var'] = $ritmo_max_var;

            /*
        Valoracion Bajo / Normal / Alto.

        Cada punto representa 2,5 minutos de analisis,
        pero el ritmo del punto se ha calculado sobre ventana movil de 5 minutos.

        Importante:
        Ahora NO sumamos siempre 150 segundos.
        Sumamos segundosPeso, porque el último tramo puede ser parcial.
        */
            $tramos = $workData[$key]['franjaHoraria'];
            $ajusteMin = $workData[$key]['ajusteMin'];
            $ajusteMax = $workData[$key]['ajusteMax'];

            $tiempoBajoSegundos = 0;
            $tiempoMedioSegundos = 0;
            $tiempoSuperiorSegundos = 0;

            /*
        No modifico el valor original de tiempoPerdidoJustificado.
        Uso una copia para consumir la bolsa de tiempo justificado.
        */
            $tiempoJustificadoRestante = $workData[$key]['tiempoPerdidoJustificado'];

            for ($i = 0; $i < count($tramos); $i++) {

                $ritmo = $tramos[$i]['ritmo'];
                $segundosPesoTramo = $tramos[$i]['segundosPeso'] ?? $segundosPasoAnalisis;

                if ($ritmo < $ajusteMin) {

                    /*
                Si hay tiempo justificado pendiente,
                este punto bajo no penaliza.
                */
                    if ($tiempoJustificadoRestante > 0) {

                        $tiempoJustificadoRestante -= $segundosPesoTramo;

                        if ($tiempoJustificadoRestante < 0) {
                            $tiempoJustificadoRestante = 0;
                        }

                        continue;
                    }

                    $tiempoBajoSegundos += $segundosPesoTramo;
                } elseif ($ritmo >= $ajusteMin && $ritmo <= $ajusteMax && $ritmo != 0) {

                    $tiempoMedioSegundos += $segundosPesoTramo;
                } else {

                    $tiempoSuperiorSegundos += $segundosPesoTramo;
                }
            }

            $tiempoTotalSegundos =
                $tiempoBajoSegundos +
                $tiempoMedioSegundos +
                $tiempoSuperiorSegundos;

            if ($tiempoTotalSegundos != 0) {

                $workData[$key]['porcentajeBajo'] =
                    ($tiempoBajoSegundos / $tiempoTotalSegundos) * 100;

                $workData[$key]['porcentajeMedio'] =
                    ($tiempoMedioSegundos / $tiempoTotalSegundos) * 100;

                $workData[$key]['porcentajeSuperior'] =
                    ($tiempoSuperiorSegundos / $tiempoTotalSegundos) * 100;
            } else {

                $workData[$key]['porcentajeBajo'] = 0;
                $workData[$key]['porcentajeMedio'] = 0;
                $workData[$key]['porcentajeSuperior'] = 0;
            }

            /*
        Datos de control para comprobar que el informe esta usando
        el nuevo criterio.
        */
            $workData[$key]['segundosPasoAnalisis'] = $segundosPasoAnalisis;
            $workData[$key]['minutosPasoAnalisis'] = $segundosPasoAnalisis / 60;

            $workData[$key]['segundosVentanaValoracion'] = $segundosVentanaValoracion;
            $workData[$key]['minutosVentanaValoracion'] = $segundosVentanaValoracion / 60;

            $workData[$key]['multiplicadorRitmoHora'] = $multiplicadorRitmoHora;

            $workData[$key]['tiempoBajoSegundos'] = $tiempoBajoSegundos;
            $workData[$key]['tiempoMedioSegundos'] = $tiempoMedioSegundos;
            $workData[$key]['tiempoSuperiorSegundos'] = $tiempoSuperiorSegundos;
            $workData[$key]['tiempoTotalValoradoSegundos'] = $tiempoTotalSegundos;

            $workData[$key]['tiempoBajoLeenda'] = sprintf(
                "%02d:%02d",
                floor($tiempoBajoSegundos / 3600),
                floor(($tiempoBajoSegundos % 3600) / 60)
            );

            $workData[$key]['tiempoMedioLeenda'] = sprintf(
                "%02d:%02d",
                floor($tiempoMedioSegundos / 3600),
                floor(($tiempoMedioSegundos % 3600) / 60)
            );

            $workData[$key]['tiempoSuperiorLeenda'] = sprintf(
                "%02d:%02d",
                floor($tiempoSuperiorSegundos / 3600),
                floor(($tiempoSuperiorSegundos % 3600) / 60)
            );

            $workData[$key]['tiempoJustificadoRestante'] = $tiempoJustificadoRestante;

            /*
        Se mantiene franjaHoraria para pintar en HTML/JS.
        */
            # $workData[$key]['franjaHoraria'] = null;

            $workData[$key]['tiempoYValor'] = null;
            $workData[$key]['linesTodas'] = null;

            $out[] = $workData[$key];
        }

        return $out;
    }
}
