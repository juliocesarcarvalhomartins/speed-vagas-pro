$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
$Root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
$Backend = Join-Path $Root 'backend'
$Launcher = Join-Path $Root 'launcher'
$Tools = Join-Path $Launcher 'tools'
$Jar = Join-Path $Backend 'target\speed-vagas.jar'
$Url = 'http://127.0.0.1:8080'
$Ready = Join-Path $Launcher '.ready'
$ErrorFile = Join-Path $Launcher '.error'
$Status = Join-Path $Launcher '.status'
$Log = Join-Path $Launcher 'launcher.log'
Remove-Item $Ready,$ErrorFile,$Status -Force -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force -Path $Tools | Out-Null
function Set-Status([string]$Text){Set-Content -Path $Status -Value $Text -Encoding UTF8;Add-Content -Path $Log -Value ("[{0}] {1}" -f (Get-Date -Format 'yyyy-MM-dd HH:mm:ss'),$Text) -Encoding UTF8}
function Fail([string]$Message){Set-Status 'Falha ao iniciar.';Set-Content -Path $ErrorFile -Value $Message -Encoding UTF8;Add-Content -Path $Log -Value $Message -Encoding UTF8;exit 1}
function Test-Server{try{$r=Invoke-WebRequest -UseBasicParsing -Uri "$Url/api/health" -TimeoutSec 2;return ($r.StatusCode -eq 200)}catch{return $false}}
function Download-File([string[]]$Urls,[string]$Dest,[string]$Label){Set-Status $Label;foreach($u in $Urls){try{Remove-Item $Dest -Force -ErrorAction SilentlyContinue;if(Get-Command curl.exe -ErrorAction SilentlyContinue){& curl.exe -L --fail --retry 3 --connect-timeout 20 --max-time 420 "$u" -o "$Dest";if($LASTEXITCODE -eq 0 -and (Test-Path $Dest) -and (Get-Item $Dest).Length -gt 100000){return}};Invoke-WebRequest -UseBasicParsing -Uri $u -OutFile $Dest -TimeoutSec 420;if((Test-Path $Dest) -and (Get-Item $Dest).Length -gt 100000){return}}catch{Add-Content -Path $Log -Value ("Download falhou: "+$_.Exception.Message) -Encoding UTF8}};throw "Não foi possível baixar $Label. Verifique internet, firewall ou antivírus."}
try{
Set-Status 'Verificando o SPEED VAGAS PRO...'
if(Test-Server){Set-Status 'Aplicativo já está em execução.';Set-Content $Ready 'ok' -Encoding ASCII;Start-Process $Url;exit 0}
Set-Status 'Verificando Java 21...'
$javaw=$null;$systemJava=Get-Command javaw.exe -ErrorAction SilentlyContinue
if($systemJava){try{$javaExe=Join-Path (Split-Path $systemJava.Source -Parent) 'java.exe';$version=& $javaExe -version 2>&1 | Select-Object -First 1;if($version -match '"([0-9]+)' -and [int]$Matches[1] -ge 21){$javaw=$systemJava.Source;Set-Status 'Java 21 encontrado.'}}catch{}}
if(-not $javaw){$jdk=Get-ChildItem $Tools -Directory -ErrorAction SilentlyContinue|Where-Object{Test-Path (Join-Path $_.FullName 'bin\javaw.exe')}|Select-Object -First 1;if(-not $jdk){$zip=Join-Path $Tools 'java21.zip';Download-File @('https://api.adoptium.net/v3/binary/latest/21/ga/windows/x64/jdk/hotspot/normal/eclipse','https://api.adoptium.net/v3/binary/latest/21/ga/windows/x64/jdk/hotspot/normal/eclipse?project=jdk') $zip 'Preparando Java portátil (primeira execução)...';Set-Status 'Extraindo Java portátil...';Expand-Archive -LiteralPath $zip -DestinationPath $Tools -Force;Remove-Item $zip -Force -ErrorAction SilentlyContinue;$jdk=Get-ChildItem $Tools -Directory|Where-Object{Test-Path (Join-Path $_.FullName 'bin\javaw.exe')}|Select-Object -First 1};if(-not $jdk){throw 'Java portátil não foi encontrado após a preparação.'};$javaw=Join-Path $jdk.FullName 'bin\javaw.exe';$env:JAVA_HOME=$jdk.FullName;$env:Path=(Join-Path $jdk.FullName 'bin')+';'+$env:Path}else{$env:JAVA_HOME=Split-Path (Split-Path $javaw -Parent) -Parent;$env:Path=(Join-Path $env:JAVA_HOME 'bin')+';'+$env:Path}
if(!(Test-Path $Jar)){Set-Status 'Preparando o aplicativo pela primeira vez...';$mvn=Get-Command mvn.cmd -ErrorAction SilentlyContinue;$mvnCmd=if($mvn){$mvn.Source}else{$null};if(-not $mvnCmd){$mvnDir=Get-ChildItem $Tools -Directory -Filter 'apache-maven-*' -ErrorAction SilentlyContinue|Select-Object -First 1;if(-not $mvnDir){$mzip=Join-Path $Tools 'maven.zip';Download-File @('https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.9.11/apache-maven-3.9.11-bin.zip','https://repo1.maven.org/maven2/org/apache/maven/apache-maven/3.9.11/apache-maven-3.9.11-bin.zip') $mzip 'Preparando componentes do aplicativo...';Set-Status 'Extraindo componentes...';Expand-Archive -LiteralPath $mzip -DestinationPath $Tools -Force;Remove-Item $mzip -Force -ErrorAction SilentlyContinue;$mvnDir=Get-ChildItem $Tools -Directory -Filter 'apache-maven-*'|Select-Object -First 1};if(-not $mvnDir){throw 'Maven portátil não foi encontrado.'};$mvnCmd=Join-Path $mvnDir.FullName 'bin\mvn.cmd'};Set-Status 'Montando o SPEED VAGAS PRO...';Push-Location $Backend;try{$buildLog=Join-Path $Launcher 'build.log';& $mvnCmd '-DskipTests' 'package' *> $buildLog;if($LASTEXITCODE -ne 0){throw 'A compilação falhou. Veja launcher\build.log.'}}finally{Pop-Location};if(!(Test-Path $Jar)){throw 'A compilação terminou, mas o arquivo do aplicativo não foi gerado.'}}
Set-Status 'Iniciando servidor local...'
Start-Process -FilePath $javaw -ArgumentList @('-Dfile.encoding=UTF-8','-jar',"`"$Jar`"") -WorkingDirectory $Backend -WindowStyle Hidden
$started=$false;for($i=0;$i -lt 120;$i++){Start-Sleep -Milliseconds 250;if(Test-Server){$started=$true;break}};if(-not $started){throw 'O servidor não respondeu em http://127.0.0.1:8080. Veja launcher\launcher.log.'}
Set-Status 'Abrindo dashboard...';Start-Process $Url;Set-Content $Ready 'ok' -Encoding ASCII;Set-Status 'Pronto.'
}catch{Fail ("Não foi possível iniciar o SPEED VAGAS PRO.`r`n`r`n"+$_.Exception.Message)}
