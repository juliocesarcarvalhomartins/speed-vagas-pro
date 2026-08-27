param([switch]$Quiet)
$ErrorActionPreference='Stop'
$ProgressPreference='SilentlyContinue'
$app=Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
$out=Join-Path $app 'web\live_jobs.json'

function Clean([string]$s){
    if($null -eq $s){return ''}
    $t=[regex]::Replace($s,'<script.*?</script>',' ','Singleline,IgnoreCase')
    $t=[regex]::Replace($t,'<[^>]+>',' ')
    $t=[System.Net.WebUtility]::HtmlDecode($t)
    return ([regex]::Replace($t,'\s+',' ')).Trim()
}
function Relevant([string]$title,[string]$desc){
    $h=(($title+' '+$desc).ToLowerInvariant())
    $positive=@(
      'it support','technical support','help desk','helpdesk','service desk',
      'desktop support','support analyst','support technician',
      'data analyst','data assistant','information technology',
      'suporte ti','assistente ti','estagio ti','estágio ti',
      'analista de dados','sql','windows','infraestrutura','tier 1','level 1','n1'
    )
    $senior=@('senior',' sr ',' sr.','lead ','manager','director','principal','staff engineer','tier 3','tier iii','n3','n2','level 2','level 3')
    $ok=$false
    foreach($k in $positive){if($h.Contains($k)){$ok=$true;break}}
    if(-not $ok){return $false}
    foreach($k in $senior){if($h.Contains($k)){return $false}}
    return $true
}
function AddJob($list,$source,$id,$title,$company,$location,$url,$date,$desc){
    if([string]::IsNullOrWhiteSpace($title) -or [string]::IsNullOrWhiteSpace($url)){return}
    $clean=Clean $desc
    if(-not (Relevant $title $clean)){return}
    $key=($title+'|'+$company).ToLowerInvariant()
    if($script:seen.ContainsKey($key)){return}
    $script:seen[$key]=$true
    $score=70
    $h=(($title+' '+$clean).ToLowerInvariant())
    if($h -match 'junior|júnior|estagio|estágio|intern|trainee|entry|level 1|tier 1|n1'){$score=88}
    elseif($h -match 'support|suporte|help desk|service desk'){$score=80}
    $list.Add([ordered]@{
        external=$true
        id=('ext-'+$source+'-'+$id)
        title=$title
        company_name=$company
        city=($(if([string]::IsNullOrWhiteSpace($location)){'Remoto'}else{$location}))
        work_mode='Remoto'
        published_at=$date
        source=$source
        compatibility_score=$score
        url=$url
        description=$clean
    }) | Out-Null
}

$jobs=New-Object System.Collections.Generic.List[object]
$script:seen=@{}
$errors=New-Object System.Collections.Generic.List[string]

try{
    $j=Invoke-RestMethod -Uri 'https://jobicy.com/api/v2/remote-jobs?count=100' -TimeoutSec 30 -Headers @{'User-Agent'='SPEED-VAGAS/6.2'}
    foreach($x in @($j.jobs)){
        AddJob $jobs 'Jobicy' $x.id $x.jobTitle $x.companyName $x.jobGeo $x.url $x.pubDate $x.jobDescription
    }
}catch{$errors.Add('Jobicy: '+$_.Exception.Message)}

foreach($term in @('technical support','IT support','help desk','service desk','data analyst','junior')){
    try{
        $u='https://remotive.com/api/remote-jobs?search='+[uri]::EscapeDataString($term)
        $r=Invoke-RestMethod -Uri $u -TimeoutSec 30 -Headers @{'User-Agent'='SPEED-VAGAS/6.2'}
        foreach($x in @($r.jobs)){
            AddJob $jobs 'Remotive' $x.id $x.title $x.company_name $x.candidate_required_location $x.url $x.publication_date $x.description
        }
    }catch{$errors.Add('Remotive '+$term+': '+$_.Exception.Message)}
}

$jobsSorted=@($jobs | Sort-Object -Property @{Expression='compatibility_score';Descending=$true}, @{Expression='published_at';Descending=$true} | Select-Object -First 120)
$payload=[ordered]@{
    updated_at=(Get-Date).ToString('s')
    count=$jobsSorted.Count
    jobs=$jobsSorted
    errors=@($errors)
}
$payload | ConvertTo-Json -Depth 8 | Set-Content -Path $out -Encoding UTF8
if(-not $Quiet){Write-Host ("[OK] "+$jobsSorted.Count+" vaga(s) externa(s) preparadas.")}
