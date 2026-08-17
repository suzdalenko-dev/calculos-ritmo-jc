<?php


# http://192.168.1.98/backendproduccion/public/index.php/api/informe_jc
Route::get('informe_jc', function(){
    header('Access-Control-Allow-Origin: *');
    
    $tresDiasAntes = date('Y-m-d', strtotime('-7 days'));           
    $conn = new PDO('sqlsrv:Server=192.168.1.xxx;Database=xxx', 'xxx', 'xxx.');
    $conn->setAttribute(PDO::ATTR_ERRMODE, PDO::ERRMODE_EXCEPTION);  
    $request =  "SELECT ActualNetWeightValue, CreationDate, ArticleName, ArticleNumber, BatchNumber, DeviceName
                 FROM PackageRecord 
                 WHERE CreationDate >= '".$tresDiasAntes." 00:00:00' AND DeviceName = 'CWE' AND ErrorFlag = 0
                 ORDER BY CreationDate ASC";
    $sql = $conn->query($request);
    $materiales = $sql->fetchAll(PDO::FETCH_ASSOC);


    # Pesadas individuales adifionales de la linea 3
    $year    = date('Y'); 
    $month   = date('m');
    $pesadas = file_get_contents('http://192.168.14.1/api/get-pesadas-individuales?year='.$year.'&month='.$month);
    $pesadas = json_decode($pesadas, true);

    if(date('d') <= 7){
        $year_anter = $year;
        $month_anterior = $month - 1;
        if($month_anterior == 0){ 
            $month_anterior = 12;
            $year_anter = $year - 1;
        }
        $pesadas_mes_anterior = file_get_contents('http://192.168.14.1/api/get-pesadas-individuales?year='.$year_anter.'&month='.$month_anterior);
        $pesadas = array_merge($pesadas, json_decode($pesadas_mes_anterior, true));
    }
    /*
    * La API devuelve meses completos.
    * Conservamos solamente los últimos 7 días.
    */
    $fechaLimite = $tresDiasAntes.' 00:00:00';
    $pesadas = array_filter($pesadas, function ($pesada) use ($fechaLimite) { return isset($pesada['CreationDate']) && substr($pesada['CreationDate'], 0, 19) >= $fechaLimite;});
    $pesadas = array_values($pesadas);
    /*
    * Unimos las dos fuentes de línea 3.
    */
    $materiales = array_merge($materiales, $pesadas);
    /*
    * Ordenamos cronológicamente todas las pesadas.
    */
    usort($materiales, function ($a, $b) {
        return strcmp(substr($a['CreationDate'], 0, 19), substr($b['CreationDate'], 0, 19));
    });
    $out = \App\RitmoProducciones::informeProducciones($materiales, 3);
    foreach($out as &$o){
        $o['nombre'] = 'L3 '. $o['nombre'];
    }
    unset($o);


     /* traer datos de la linea 1  - y solo coger los ultimos 7 dias */
    $request =  "SELECT ActualNetWeightValue, CreationDate, ArticleName, ArticleNumber, BatchNumber, DeviceName
                 FROM PackageRecord 
                 WHERE CreationDate >= '".$tresDiasAntes." 00:00:00' AND DeviceName = 'CWE 01' AND ErrorFlag = 0
                 ORDER BY CreationDate ASC";
    $sql = $conn->query($request);
    $linea1 = $sql->fetchAll(PDO::FETCH_ASSOC);    
    $out_linea1 = \App\RitmoProducciones::informeProducciones($linea1, 1);
    foreach($out_linea1 as &$o2){
        $o2['nombre'] = 'L1 '. $o2['nombre'];
    }
    unset($o2);

    if(count($out_linea1) > 0){
        $mergedArray = array_merge($out, $out_linea1);
        usort($mergedArray, function ($a, $b) {
            return strtotime($a['fechaIni']) - strtotime($b['fechaIni']);
        });
    } else {
        $mergedArray = $out;
    }
    $conn = null;
    return json_encode(['res'=>$mergedArray]);
});
