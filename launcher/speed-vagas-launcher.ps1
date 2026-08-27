$ErrorActionPreference="Stop"
$Root=Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
$Backend=Join-Path $Root "backend"
$Jar=Join-Path $Backend "target\speed-vagas.jar"
$Url="http://127.0.0.1:8080"
function Fail($m){Add-Type -AssemblyName PresentationFramework;[System.Windows.MessageBox]::Show($m,"SPEED VAGAS PRO")|Out-Null;exit 1}
try{$r=Invoke-WebRequest -UseBasicParsing "$Url/api/health" -TimeoutSec 1;if($r.StatusCode-eq 200){Start-Process $Url;exit}}catch{}
if(!(Test-Path $Jar)){if(!(Get-Command mvn -ErrorAction SilentlyContinue)){Fail "Primeira execução: instale Java 21 e Maven. Depois abra o launcher novamente."};Push-Location $Backend;try{& mvn -q -DskipTests package;if($LASTEXITCODE-ne 0){Fail "Falha ao compilar o SPEED VAGAS PRO."}}finally{Pop-Location}}
$j=Get-Command javaw -ErrorAction SilentlyContinue
if(!$j){Fail "Java 21 não encontrado."}
Start-Process $j.Source -ArgumentList "-jar","`"$Jar`"" -WorkingDirectory $Backend -WindowStyle Hidden
$ok=$false
for($i=0;$i-lt 40;$i++){Start-Sleep -Milliseconds 250;try{$r=Invoke-WebRequest -UseBasicParsing "$Url/api/health" -TimeoutSec 1;if($r.StatusCode-eq 200){$ok=$true;break}}catch{}}
if(!$ok){Fail "O SPEED VAGAS PRO não conseguiu iniciar."}
Start-Process $Url
