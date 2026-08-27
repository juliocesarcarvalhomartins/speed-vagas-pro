$ErrorActionPreference='Stop'
$ProgressPreference='SilentlyContinue'

function Stop-WithError([string]$title,[object]$detail=$null){
    Write-Host ''
    Write-Host '==========================================================' -ForegroundColor Red
    Write-Host ('[ERRO] '+$title) -ForegroundColor Red
    if($detail){ Write-Host ($detail | Out-String) -ForegroundColor Yellow }
    Write-Host '==========================================================' -ForegroundColor Red
    Write-Host ''
    Write-Host 'A janela vai permanecer aberta para você tirar um print.' -ForegroundColor White
    Read-Host 'Pressione ENTER para fechar'
    exit 1
}

function Download-File([string[]]$urls,[string]$dest){
    foreach($url in $urls){
        try{
            Remove-Item $dest -Force -ErrorAction SilentlyContinue
            Write-Host ('[INFO] Baixando: '+$url)
            if(Get-Command curl.exe -ErrorAction SilentlyContinue){
                & curl.exe -L --fail --retry 3 --connect-timeout 20 $url -o $dest
                if($LASTEXITCODE -eq 0 -and (Test-Path $dest) -and ((Get-Item $dest).Length -gt 100000)){ return }
            }
            Invoke-WebRequest -UseBasicParsing -Uri $url -OutFile $dest -TimeoutSec 240
            if((Test-Path $dest) -and ((Get-Item $dest).Length -gt 100000)){ return }
        }catch{
            Write-Host ('[AVISO] Falhou nesta fonte: '+$_.Exception.Message) -ForegroundColor Yellow
        }
    }
    throw 'Não foi possível baixar a dependência necessária.'
}

function Java-Works([string]$exe){
    if(-not (Test-Path $exe)){ return $false }
    try{
        $p=Start-Process -FilePath $exe -ArgumentList '-version' -Wait -PassThru -WindowStyle Hidden
        return ($p.ExitCode -eq 0)
    }catch{
        return $false
    }
}

try{
    $app=Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
    Set-Location $app

    $runtime=Join-Path $app 'runtime'
    $jdk=Join-Path $runtime 'jdk'
    $java=Join-Path $jdk 'bin\java.exe'
    $javac=Join-Path $jdk 'bin\javac.exe'
    $lib=Join-Path $app 'lib'
    $h2=Join-Path $lib 'h2.jar'
    $logs=Join-Path $app 'logs'
    $data=Join-Path $app 'data'

    New-Item -ItemType Directory -Force -Path $runtime,$lib,$logs,$data | Out-Null

    Clear-Host
    Write-Host '==========================================================' -ForegroundColor DarkMagenta
    Write-Host '              SPEED VAGAS PRO 6.1' -ForegroundColor Magenta
    Write-Host '==========================================================' -ForegroundColor DarkMagenta

    Write-Host '[1/6] Java 21...' -ForegroundColor Cyan

    # First reuse a working Java 21+ already installed.
    $systemJava=$null
    $javaCmd=Get-Command java.exe -ErrorAction SilentlyContinue
    if($javaCmd){
        try{
            $versionText=& $javaCmd.Source -version 2>&1 | Select-Object -First 1
            if($versionText -match '"([0-9]+)'){
                if([int]$Matches[1] -ge 21 -and (Java-Works $javaCmd.Source)){
                    $systemJava=$javaCmd.Source
                }
            }
        }catch{}
    }

    if($systemJava){
        $java=$systemJava
        Write-Host ('[INFO] Usando Java já instalado: '+$java)
    }
    elseif(-not (Java-Works $java)){
        Write-Host '[INFO] Preparando JDK 21 portátil pela primeira vez...'
        $zip=Join-Path $runtime 'jdk21.zip'
        $extract=Join-Path $runtime '_extract'

        Remove-Item $zip -Force -ErrorAction SilentlyContinue
        Remove-Item $extract -Recurse -Force -ErrorAction SilentlyContinue
        Remove-Item $jdk -Recurse -Force -ErrorAction SilentlyContinue

        # FULL JDK, not JRE. This is the package family that already ran on this PC.
        Download-File @(
          'https://api.adoptium.net/v3/binary/latest/21/ga/windows/x64/jdk/hotspot/normal/eclipse'
        ) $zip

        Write-Host '[INFO] Extraindo JDK 21...'
        Expand-Archive -LiteralPath $zip -DestinationPath $extract -Force

        $candidate=Get-ChildItem $extract -Directory -Recurse -ErrorAction Stop |
            Where-Object {
                (Test-Path (Join-Path $_.FullName 'bin\java.exe')) -and
                (Test-Path (Join-Path $_.FullName 'bin\javac.exe'))
            } | Select-Object -First 1

        if(-not $candidate){ throw 'O JDK foi baixado, mas java.exe/javac.exe não foram encontrados.' }

        Write-Host '[INFO] Instalando JDK local...'
        Move-Item -Path $candidate.FullName -Destination $jdk

        Remove-Item $extract -Recurse -Force -ErrorAction SilentlyContinue
        Remove-Item $zip -Force -ErrorAction SilentlyContinue

        $java=Join-Path $jdk 'bin\java.exe'
        $javac=Join-Path $jdk 'bin\javac.exe'
    }

    if(-not (Java-Works $java)){
        throw 'O Java 21 não conseguiu executar. Verifique se o Windows Defender/antivírus bloqueou app\runtime\jdk\bin\java.exe.'
    }

    Write-Host '[OK] Java 21 funcionando.' -ForegroundColor Green

    Write-Host '[2/6] Banco H2...' -ForegroundColor Cyan
    if(-not (Test-Path $h2) -or ((Get-Item $h2).Length -lt 100000)){
        Download-File @(
          'https://repo.maven.apache.org/maven2/com/h2database/h2/2.4.240/h2-2.4.240.jar',
          'https://repo1.maven.org/maven2/com/h2database/h2/2.4.240/h2-2.4.240.jar'
        ) $h2
    }
    if(-not (Test-Path $h2)){ throw 'Banco H2 não está disponível.' }
    Write-Host '[OK] Banco pronto.' -ForegroundColor Green

    # Relative CP = no problem with C:\Users\SPEED KING GG\...
    $cp='speed-vagas.jar;lib\h2.jar'

    Write-Host '[3/6] Verificação crítica...' -ForegroundColor Cyan
    $checkLog=Join-Path $logs 'startup-check.log'
    Remove-Item $checkLog -Force -ErrorAction SilentlyContinue

    Push-Location $app
    try{
        & $java --add-modules jdk.httpserver -cp $cp StartupCheck *> $checkLog
        $checkExit=$LASTEXITCODE
    }finally{
        Pop-Location
    }

    if($checkExit -ne 0){
        if(Test-Path $checkLog){ Get-Content $checkLog }
        throw 'Falha na verificação crítica. Veja app\logs\startup-check.log.'
    }
    Write-Host '[OK] Banco, arquivos e permissões locais aprovados.' -ForegroundColor Green

    Write-Host '[4/6] Teste de regras...' -ForegroundColor Cyan
    $logicLog=Join-Path $logs 'logic-test.log'
    Remove-Item $logicLog -Force -ErrorAction SilentlyContinue
    Push-Location $app
    try{
        & $java -cp $cp LogicSelfTest *> $logicLog
        $logicExit=$LASTEXITCODE
    }finally{
        Pop-Location
    }
    if($logicExit -ne 0){
        if(Test-Path $logicLog){ Get-Content $logicLog }
        throw 'Teste de regras falhou. Veja app\logs\logic-test.log.'
    }
    Write-Host '[OK] Regras aprovadas.' -ForegroundColor Green

    
    Write-Host '[INFO] Atualizando vagas externas...' -ForegroundColor Cyan
    try{
        & (Join-Path $app 'bin\refresh-jobs.ps1') -Quiet
        $live=Join-Path $app 'web\live_jobs.json'
        if(Test-Path $live){
            $liveData=Get-Content $live -Raw | ConvertFrom-Json
            Write-Host ('[OK] '+$liveData.count+' vaga(s) externa(s) disponíveis.') -ForegroundColor Green
        }
    }catch{
        Write-Host ('[AVISO] Busca externa não atualizou: '+$_.Exception.Message) -ForegroundColor Yellow
    }

Write-Host '[5/6] Porta...' -ForegroundColor Cyan
    $port=8080
    while($port -le 8090 -and (Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue)){
        $port++
    }
    if($port -gt 8090){ throw 'Nenhuma porta livre entre 8080 e 8090.' }
    $url="http://localhost:$port"
    Write-Host ('[OK] '+$url) -ForegroundColor Green

    Write-Host '[6/6] Iniciando servidor...' -ForegroundColor Cyan
    $serverOut=Join-Path $logs 'server.log'
    $serverErr=Join-Path $logs 'server-error.log'
    Remove-Item $serverOut,$serverErr -Force -ErrorAction SilentlyContinue

    $serverArgs = '-Dspeed.port={0} --add-modules jdk.httpserver -cp "{1}" SpeedVagasServer' -f $port,$cp

    $server=Start-Process -FilePath $java `
        -ArgumentList $serverArgs `
        -WorkingDirectory $app `
        -RedirectStandardOutput $serverOut `
        -RedirectStandardError $serverErr `
        -PassThru

    $healthy=$false
    for($i=0;$i -lt 100;$i++){
        Start-Sleep -Milliseconds 200
        if($server.HasExited){ break }
        try{
            $r=Invoke-WebRequest -UseBasicParsing -Uri ($url+'/api/health') -TimeoutSec 2
            if($r.StatusCode -eq 200){ $healthy=$true; break }
        }catch{}
    }

    if(-not $healthy){
        Write-Host '[ERRO] Servidor não respondeu ao health check.' -ForegroundColor Red
        if(Test-Path $serverOut){ Get-Content $serverOut -Tail 80 }
        if(Test-Path $serverErr){ Get-Content $serverErr -Tail 80 }
        try{Stop-Process -Id $server.Id -Force -ErrorAction SilentlyContinue}catch{}
        throw 'Falha na inicialização do servidor. Veja app\logs\server-error.log.'
    }

    Write-Host '[OK] Servidor online.' -ForegroundColor Green
    Write-Host ('[INFO] '+$url)

    $opened=$false
    try{ Start-Process $url; $opened=$true }catch{}

    if(-not $opened){
        foreach($browser in @(
          "$env:ProgramFiles\BraveSoftware\Brave-Browser\Application\brave.exe",
          "$env:LOCALAPPDATA\BraveSoftware\Brave-Browser\Application\brave.exe",
          "$env:ProgramFiles\Google\Chrome\Application\chrome.exe",
          "$env:LOCALAPPDATA\Google\Chrome\Application\chrome.exe"
        )){
            if(Test-Path $browser){
                try{
                    Start-Process -FilePath $browser -ArgumentList $url
                    $opened=$true
                    break
                }catch{}
            }
        }
    }

    Write-Host ''
    Write-Host '[OK] SPEED VAGAS ESTÁ FUNCIONANDO.' -ForegroundColor Green
    Write-Host ('[INFO] Endereço: '+$url) -ForegroundColor White
    Write-Host '[INFO] Não feche esta janela enquanto estiver usando o app.' -ForegroundColor Yellow

    if(-not $opened){
        Write-Host '[AVISO] Use ABRIR NO NAVEGADOR.cmd.' -ForegroundColor Yellow
    }

    Wait-Process -Id $server.Id
}
catch{
    Stop-WithError $_.Exception.Message $_
}
