const $=s=>document.querySelector(s), $$=s=>[...document.querySelectorAll(s)];
const api=async(url,opt={})=>{const h={...(opt.headers||{})};if(opt.body&&!h['Content-Type']&&!(opt.body instanceof Blob))h['Content-Type']='application/json';const r=await fetch(url,{...opt,headers:h});const j=await r.json().catch(()=>({}));if(!r.ok)throw new Error(j.error||`HTTP ${r.status}`);return j};
const esc=s=>String(s??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
const toast=m=>{const t=$('#toast');t.textContent=m;t.classList.add('show');setTimeout(()=>t.classList.remove('show'),3000)};
let googleSearchConfigured=false;
async function refreshGoogleSearchUi(){
 try{
  const c=await api('/api/search/google/status');googleSearchConfigured=!!c.configured;
  const st=$('#googleSearchState'),cfg=$('#googleSearchConfigStatus'),quota=$('#googleQuotaStatus');
  if(st){st.textContent=googleSearchConfigured?'Google Search: configurado no backend':'Google Search: falta configurar no backend';st.className='statusPill '+(googleSearchConfigured?'good':'warn')}
  if(cfg)cfg.textContent=googleSearchConfigured?'✓ Credenciais protegidas no backend.':'Defina SPEED_GOOGLE_SEARCH_KEY e SPEED_GOOGLE_SEARCH_CX no ambiente do backend.';
  if(quota)quota.textContent=`Cota diária Google: ${c.quotaUsed||0}/${c.quotaLimit||100} usadas • ${c.quotaRemaining??0} restantes`;
 }catch(e){googleSearchConfigured=false;const cfg=$('#googleSearchConfigStatus');if(cfg)cfg.textContent='Erro ao verificar Google Search: '+e.message}
}
async function googleCse(q,start=1){
 const r=await api('/api/search/google',{method:'POST',body:JSON.stringify({q,start,num:10})});
 return r.items||[];
}
function managerPayload(r,profile){
 const S=window.SpeedSearch,source=S.sourceFromUrl(r.link),score=S.scoreResult(r,profile);
 const text=(r.title+' '+r.snippet).toLowerCase(),remote=/remote|remoto|home office/.test(text);
 return {
  company:S.inferCompany(r.title,source),companyWebsite:'',source:'GOOGLE_'+source.toUpperCase().replace(/[^A-Z0-9]/g,'_'),
  externalId:r.link,title:S.cleanTitle(r.title,source),city:remote?'Remoto':'São Paulo',state:'SP',workMode:remote?'Remoto':'Presencial',
  level:/est[aá]gio|intern/.test(text)?'Estágio':/assistente|auxiliar/.test(text)?'Assistente':'Júnior',
  salaryText:'',description:r.snippet||'',requirements:r.snippet||'',url:r.link,publishedAt:new Date().toISOString().slice(0,10),
  _score:score,_source:source
 };
}
async function saveGoogleResults(rows,profile){
 const existing=await api('/api/jobs?sort=priority&radius=999&minScore=0').catch(()=>[]);
 const urls=new Set(existing.map(x=>String(x.url||'').trim()).filter(Boolean));
 let saved=0,failed=0;
 for(const r of rows){
   const p=managerPayload(r,profile);
   if(p._score<60||urls.has(p.url))continue;
   const body={...p};delete body._score;delete body._source;
   try{await api('/api/jobs',{method:'POST',body:JSON.stringify(body)});urls.add(p.url);saved++}catch(_e){failed++}
   if(saved>=35)break;
 }
 return {saved,failed};
}
async function runGoogleIntelligence(scope='EAST_PLUS_REMOTE'){
 const S=window.SpeedSearch,profile=await api('/api/profile').catch(()=>({}));
 const queries=S.buildQueries(profile,scope),all=[],health=[];
 let raw=0;
 for(const item of queries){
   try{
     const rows=await googleCse(item.q,1);raw+=rows.length;all.push(...rows);
     health.push({source:item.source,count:rows.length,status:rows.length?'ok':'zero'});
   }catch(e){health.push({source:item.source,count:0,status:'error',error:e.message})}
 }
 const unique=S.dedupe(all);
 const filtered=unique.filter(r=>S.isRelevant(r.title+' '+r.snippet,profile)).map(r=>({...r,_score:S.scoreResult(r,profile)})).filter(r=>r._score>=60).sort((a,b)=>b._score-a._score);
 const save=await saveGoogleResults(filtered,profile);
 return {raw,unique:unique.length,filtered:filtered.length,saved:save.saved,health,rows:filtered};
}
function renderIntelligence(r){
 if($('#igFound'))$('#igFound').textContent=r.raw||0;
 if($('#igFiltered'))$('#igFiltered').textContent=r.filtered||0;
 if($('#igSaved'))$('#igSaved').textContent=r.saved||0;
 if($('#igSources'))$('#igSources').textContent=(r.health||[]).filter(x=>x.count>0).length;
 if($('#igHuman'))$('#igHuman').textContent=Math.max(0,(r.filtered||0)-(r.saved||0));
 if($('#sourceHealth'))$('#sourceHealth').innerHTML=(r.health||[]).map(x=>`<span class="src ${x.status}" title="${esc(x.error||'')}">${esc(x.source)}: ${x.count}</span>`).join('');
}

const pages={dashboard:['AUTOMAÇÃO','SPEED VAGAS','Busca, candidatura, respostas e alertas em um só lugar.'],buscar:['VAGAS','Vagas para você','Somente oportunidades de entrada compatíveis.'],atividade:['AUDITORIA','Atividade do SPEED','Veja a prova de cada ação realizada.'],candidaturas:['ACOMPANHAR','Minhas candidaturas','Enviadas, pendentes e próximas etapas.'],perfil:['PERFIL','Meu perfil','Currículo e dados usados na busca.'],config:['AJUSTES','Configurações','Automação, Gmail e segurança.']};
function go(id){$$('.page').forEach(x=>x.classList.toggle('active',x.id===id));$$('#nav button').forEach(x=>x.classList.toggle('active',x.dataset.page===id));const p=pages[id]||pages.dashboard;$('#eyebrow').textContent=p[0];$('#title').textContent=p[1];$('#subtitle').textContent=p[2];if(id==='buscar'){loadJobs();loadDiscardedJobs();}if(id==='atividade'){loadActivity();loadEmailEvents()}if(id==='candidaturas')loadApps();if(id==='perfil')loadProfile();if(id==='config')loadSettings()}
$$('[data-page]').forEach(b=>b.onclick=()=>go(b.dataset.page));$$('[data-go]').forEach(b=>b.onclick=()=>go(b.dataset.go));

function jobHtml(j){
 const remote=String(j.work_mode||'').toLowerCase().includes('remot'),score=Math.round(j.compatibility_score||0),url=j.url||'';
 return `<article class="job simpleJob" data-job="${j.id}">
 <div class="jobMain"><div class="jobTitleLine"><h3>${esc(j.title)}</h3><span class="match ${score>=80?'excellent':score>=65?'good':'maybe'}">${score}% combina</span></div>
 <p class="companyLine">${esc(j.company_name)} • ${esc(j.city||'Local não informado')}</p>
 <div class="meta"><span class="chip">${remote?'🏠 Home Office':`📍 ${esc(j.city||'Presencial')}`}</span><span class="chip">${esc(j.work_mode||'')}</span><span class="chip">🕒 ${esc(String(j.published_at||'').slice(0,10)||'data não informada')}</span><span class="chip">${esc(j.source||'Internet')}</span></div>
 <p class="whyMatch">${score>=80?'✅ Excelente para seu perfil':score>=65?'👍 Boa opção para se candidatar':'ℹ Vale analisar'}</p>
 <div class="jobActions">${url?`<button class="soft" data-open="${esc(url)}">Ver vaga</button>`:''}<button class="primary" data-send="${j.id}">Enviar currículo</button></div></div></article>`;
}

function externalJobHtml(j){
 const score=Math.round(j.compatibility_score||0),url=j.url||'';
 return `<article class="job simpleJob externalJob">
 <div class="jobMain"><div class="jobTitleLine"><h3>${esc(j.title)}</h3><span class="match ${score>=80?'excellent':score>=65?'good':'maybe'}">${score}% combina</span></div>
 <p class="companyLine">${esc(j.company_name)} • ${esc(j.city||'Remoto')}</p>
 <div class="meta"><span class="chip">🏠 Home Office</span><span class="chip">🕒 ${esc(String(j.published_at||'').slice(0,10)||'data não informada')}</span><span class="chip">${esc(j.source||'Internet')}</span><span class="chip">Fonte externa</span></div>
 <p class="whyMatch">${score>=80?'✅ Boa compatibilidade com seu perfil':'👍 Vale analisar'}</p>
 <div class="jobActions">${url?`<button class="primary" data-open="${esc(url)}">Abrir vaga oficial</button>`:''}</div>
 </div></article>`;
}
async function loadExternalJobs(){
 try{
   const r=await fetch(`/live_jobs.json?v=${Date.now()}`,{cache:'no-store'});
   if(!r.ok)return [];
   const j=await r.json();
   return Array.isArray(j.jobs)?j.jobs:[];
 }catch(_e){return []}
}

function bindJobActions(root=document){root.querySelectorAll('[data-open]').forEach(b=>b.onclick=()=>window.open(b.dataset.open,'_blank','noopener'));root.querySelectorAll('[data-send]').forEach(b=>b.onclick=()=>sendResume(Number(b.dataset.send)))}

async function dashboard(){
 try{
  if($('#recommended'))$('#recommended').innerHTML='<div class="loadingState">Carregando recomendações...</div>';
  const [d,jobs,notes,act,apps]=await Promise.all([api('/api/dashboard'),api('/api/jobs?sort=priority&radius=999&minScore=55'),api('/api/notifications'),api('/api/activity?limit=8'),api('/api/applications')]);
  $('#stJobs').textContent=d.analyzedToday||0;$('#stCompat').textContent=d.compatible||0;$('#stSent').textContent=d.sentToday||0;
  if($('#stResponse'))$('#stResponse').textContent=`${Number(d.responseRate||0).toFixed(1)}%`;if($('#stInterviews'))$('#stInterviews').textContent=d.interviews||0;
  $('#stAction').textContent=apps.filter(a=>String(a.status).includes('ACAO')||String(a.status).includes('DRAFT')).length+notes.length;
  if(jobs.length){$('#recommended').innerHTML=jobs.slice(0,5).map(jobHtml).join('')}
  else{const ext=await loadExternalJobs();$('#recommended').innerHTML=ext.slice(0,5).map(externalJobHtml).join('')||'<div class="empty">Nenhuma vaga disponível agora.</div>'}
  bindJobActions($('#recommended'));
  renderAlerts(notes);renderActivity(act,$('#recentActivity'));
 }catch(e){toast('Dashboard: '+e.message)}
}

async function loadJobs(){
 try{
  if($('#jobs'))$('#jobs').innerHTML='<div class="loadingState">Carregando vagas...</div>';
  const sort=$('#sort')?.value||'priority',scope=$('#simpleScope')?.value||'EAST_PLUS_REMOTE';
  const mode=scope==='REMOTE'?'Remoto':'TODAS';
  const rows=await api(`/api/jobs?mode=${encodeURIComponent(mode)}&sort=${sort}&minScore=55`);
  if(rows.length){
    $('#jobCount').textContent=`${rows.length} vaga${rows.length===1?'':'s'} no gestor`;
    $('#jobs').innerHTML=rows.map(jobHtml).join('');
    bindJobActions($('#jobs'));
    return;
  }
  const ext=await loadExternalJobs();
  $('#jobCount').textContent=`${ext.length} vaga${ext.length===1?'':'s'} externa${ext.length===1?'':'s'}`;
  $('#jobs').innerHTML=ext.map(externalJobHtml).join('')||'<div class="empty">Nenhuma vaga disponível nas fontes agora.</div>';
  bindJobActions($('#jobs'));
 }catch(e){toast(e.message)}
}

async function findJobs(){
 const btn=$('#findForMe'),status=$('#simpleSearchStatus'),scope=$('#simpleScope')?.value||'EAST_PLUS_REMOTE';
 btn.disabled=true;btn.textContent='Gestor pesquisando...';status.textContent='Buscando vagas reais pelas fontes públicas e pelo Google opcional...';
 try{
   let gr=null;
   await refreshGoogleSearchUi();
   if(googleSearchConfigured){
     try{gr=await runGoogleIntelligence(scope);renderIntelligence(gr)}
     catch(e){console.warn('Google Search opcional indisponível:',e.message)}
   }
   const r=await api('/api/search/smart',{method:'POST',body:JSON.stringify({scope,limit:100})}).catch(e=>({added:0,providerStats:[],notices:[e.message],errors:[]}));
   const ext=await loadExternalJobs();
   const stats=r.providerStats||[],latest={};for(const x of stats)latest[x.provider]=x;
   const pills=Object.values(latest).map(x=>`<span class="sourcePill ${String(x.status).toLowerCase()}">${esc(x.provider)}: ${x.status==='OK'?`${x.added||0} no banco`:x.status==='OPTIONAL'?'opcional':'indisponível'}</span>`);
   if(gr)pills.unshift(`<span class="sourcePill ok portalTag">Google/5 portais: ${gr.filtered} compatível(is)</span>`);
   if(ext.length)pills.push(`<span class="sourcePill ok">Remotas fallback: ${ext.length}</span>`);
   $('#providerStatus').innerHTML=pills.join('');
   if(gr&&gr.filtered){
     status.innerHTML=`✅ Google encontrou <b>${gr.raw}</b> resultado(s), filtrou <b>${gr.filtered}</b> compatível(is) e salvou <b>${gr.saved}</b> no gestor.`;
   }else if(!googleSearchConfigured){
     const added=Number(r.added||0);
     if(added>0) status.innerHTML=`✅ Busca pública encontrou e salvou <b>${added}</b> nova(s) vaga(s). Google Search é opcional.`;
     else status.innerHTML='ℹ️ Busca concluída pelas fontes públicas. Google Search é opcional e não está configurado; nenhuma vaga nova compatível entrou nesta rodada.';
   }else if(ext.length){
     status.innerHTML=`⚠️ Google não retornou compatíveis nesta rodada; fallback remoto trouxe ${ext.length} vaga(s).`;
   }else{
     status.innerHTML='⚠️ Nenhuma fonte retornou vaga compatível nesta rodada. Veja o diagnóstico por portal acima.';
   }
   await loadJobs();await dashboard();
 }catch(e){status.textContent='❌ '+e.message}
 finally{btn.disabled=false;btn.textContent='⌕ Procurar vagas'}
}

async function runAutomation(){
 const buttons=[$('#runAutomation'),$('#runAutomationTop')].filter(Boolean);buttons.forEach(b=>{b.disabled=true;b.dataset.old=b.textContent;b.textContent='Agente trabalhando...'});
 try{
  const scope=$('#simpleScope')?.value||'EAST_PLUS_REMOTE';
  const r=await api('/api/automation/run',{method:'POST',body:JSON.stringify({scope,search:true,minScore:65,max:10})});
  const auto=Number(r.sent||0),drafts=Number(r.drafts||0),actions=Number(r.actionRequired||0);
  toast(`Gestor: ${auto} autoenvio(s), ${drafts} rascunho(s), ${actions} ação(ões) necessária(s).`);
  if(r.search?.googleIntelligence)renderIntelligence(r.search.googleIntelligence);
  await Promise.all([dashboard(),loadApps(),loadActivity(),loadJobs(),loadDiscardedJobs(),refreshGoogleSearchUi()]);
 }catch(e){toast(e.message)}finally{buttons.forEach(b=>{b.disabled=false;b.textContent=b.dataset.old})}
}

async function sendResume(id){
 try{const r=await api('/api/applications/send',{method:'POST',body:JSON.stringify({jobId:id})});toast(`✅ Currículo enviado para ${r.email}.`);await Promise.all([dashboard(),loadApps(),loadActivity()])}
 catch(e){toast(e.message)}
}

async function approveApplication(id){
 try{const r=await api('/api/applications/approve',{method:'POST',body:JSON.stringify({applicationId:id})});toast(`✅ Aprovado e enviado para ${r.email}.`);await Promise.all([dashboard(),loadApps(),loadActivity()])}
 catch(e){toast(e.message)}
}
async function loadApps(){
 try{
  if($('#apps'))$('#apps').innerHTML='<div class="loadingState">Carregando candidaturas...</div>';
  const rows=await api('/api/applications');$('#appSummary').textContent=`${rows.length} registro(s)`;
  $('#apps').innerHTML=rows.map(a=>{const st=String(a.status||'').toUpperCase(),sent=st==='ENVIADA'||st==='ENVIADA_AUTO',auto=st==='ENVIADA_AUTO',pending=st==='DRAFT_PENDING_APPROVAL',action=st.includes('ACAO');
   return `<article class="job application ${sent?'sentJob':(action||pending)?'actionJob':''}"><div><h3>${sent?'✅ ':(action||pending)?'⚠ ':''}${esc(a.title)}</h3><p>${esc(a.company_name)} • ${esc(a.channel||'')}</p><div class="meta"><span class="chip">${auto?'AUTOENVIADA':pending?'AGUARDANDO APROVAÇÃO':esc(st)}</span><span class="chip">${Math.round(a.compatibility_score||0)}% match</span>${a.contact_value?`<span class="chip">${action?'Abrir:':'Para:'} ${esc(a.contact_value)}</span>`:''}<span class="chip">${esc(a.created_at||'')}</span></div>${pending?`<p class="whyMatch">Revise o destinatário e confirme antes do envio.</p><button class="primary smallBtn" data-approve="${a.id}">Aprovar e enviar</button>`:''}${action&&a.contact_value?`<button class="primary smallBtn" data-open="${esc(a.contact_value)}">Abrir e concluir</button>`:''}${auto?`<button class="soft smallBtn" data-badmatch="${a.job_id}">Marcar este match como ruim</button>`:''}</div></article>`
  }).join('')||'<div class="empty">Nenhuma candidatura registrada.</div>';bindJobActions($('#apps'));$$('[data-approve]').forEach(b=>b.onclick=()=>approveApplication(Number(b.dataset.approve)));$$('[data-badmatch]').forEach(b=>b.onclick=()=>markBadMatch(Number(b.dataset.badmatch)));
 }catch(e){toast(e.message)}
}
async function approveBulk90(){const b=$('#approveBulk90');if(!b)return;b.disabled=true;const old=b.textContent;b.textContent='Aprovando...';try{const r=await api('/api/applications/approve-bulk',{method:'POST',body:JSON.stringify({minScore:90,limit:10})});toast(`${r.sent} candidatura(s) 90%+ aprovada(s) e enviada(s).`);await Promise.all([loadApps(),dashboard(),loadActivity()])}catch(e){toast(e.message)}finally{b.disabled=false;b.textContent=old}}
async function markBadMatch(jobId){try{await api('/api/jobs/feedback',{method:'POST',body:JSON.stringify({jobId,type:'BAD_MATCH'})});toast('Match marcado como ruim. O SPEED não repetirá essa decisão para a vaga.');await Promise.all([loadActivity(),loadDiscardedJobs()])}catch(e){toast(e.message)}}
async function loadDiscardedJobs(){const el=$('#discardedJobs');if(!el)return;el.innerHTML='<div class="loadingState">Carregando descartes...</div>';try{const rows=await api('/api/jobs/discarded?limit=50');el.innerHTML=rows.map(x=>`<article class="discardItem"><div><b>${esc(x.title||'Vaga')}</b><p>${esc(x.company_name||'')} • ${esc(x.source||'')}</p></div><span class="discardBadge">${esc(String(x.reason_code||'').replaceAll('_',' '))}</span><small>${esc(x.reason_message||'')}</small></article>`).join('')||'<div class="empty">Nenhum descarte registrado.</div>'}catch(e){el.innerHTML=`<div class="empty">Erro: ${esc(e.message)}</div>`}}

function renderActivity(rows,target){
 target.innerHTML=rows.map(x=>`<div class="timelineItem ${String(x.status||'').toLowerCase()}"><span class="timelineIcon">${x.status==='SUCCESS'||x.status==='OK'?'✓':x.status==='WARNING'?'!':'•'}</span><div><b>${esc(x.title)}</b><p>${esc(x.details||'')}</p><small>${esc(x.created_at||'')} • ${esc(x.event_type||'')}</small></div></div>`).join('')||'<div class="empty">Nenhuma atividade ainda.</div>';
}
async function loadActivity(){try{if($('#activity'))$('#activity').innerHTML='<div class="loadingState">Carregando atividade...</div>';renderActivity(await api('/api/activity?limit=100'),$('#activity'))}catch(e){toast(e.message)}}

function renderAlerts(notes){
 const b=$('#alertsBox');if(!notes.length){b.innerHTML='';return}
 b.innerHTML=notes.slice(0,5).map(n=>`<div class="alert ${String(n.severity).toLowerCase()}"><div><b>${esc(n.title)}</b><p>${esc(n.message)}</p></div><button class="soft smallBtn" data-resolve="${n.id}">Marcar como visto</button></div>`).join('');
 b.querySelectorAll('[data-resolve]').forEach(x=>x.onclick=async()=>{await api('/api/notifications/resolve',{method:'POST',body:JSON.stringify({id:Number(x.dataset.resolve)})});dashboard()});
}

async function checkEmail(){
 const buttons=[$('#checkEmail'),$('#checkEmailTop')].filter(Boolean);buttons.forEach(b=>{b.disabled=true;b.dataset.old=b.textContent;b.textContent='Verificando...'});
 try{const r=await api('/api/email/check',{method:'POST',body:JSON.stringify({max:30})});toast(`Gmail: ${r.new} nova(s), ${r.interviews} entrevista(s), ${r.actions} ação(ões).`);await Promise.all([loadEmailEvents(),dashboard(),loadActivity()])}
 catch(e){toast(e.message)}finally{buttons.forEach(b=>{b.disabled=false;b.textContent=b.dataset.old})}
}
async function loadEmailEvents(){
 try{const rows=await api('/api/email/events');$('#emailEvents').innerHTML=rows.map(e=>`<article class="emailItem ${String(e.classification).toLowerCase()}"><div><span class="emailClass">${esc(e.classification)}</span><b>${esc(e.subject)}</b><p>${esc(e.sender)}</p><small>${esc(e.message_date||e.created_at||'')}</small></div><button class="soft smallBtn" data-draft="${e.id}">Sugerir resposta</button></article>`).join('')||'<div class="empty">Nenhuma resposta de empresa identificada.</div>';
  $$('[data-draft]').forEach(b=>b.onclick=()=>showDraft(Number(b.dataset.draft)));
 }catch(e){toast(e.message)}
}
async function showDraft(id){
 try{const r=await api('/api/email/reply-draft',{method:'POST',body:JSON.stringify({emailEventId:id})});window.prompt(`Resposta sugerida (${r.classification}) — revise antes de enviar:`,r.draft)}
 catch(e){toast(e.message)}
}

async function loadProfile(){try{const p=await api('/api/profile'),f=$('#profileForm');['name','email','phone','city','state','target_roles','skills'].forEach(k=>{if(f.elements[k])f.elements[k].value=p[k]??''});$('#profileName').textContent=p.name||'Meu perfil';$('#profileLocation').textContent=`${p.city||''} • ${p.state||''}`;if(p.photo_path){const n=p.photo_path.split('/').pop();$('#photoPreview').innerHTML=`<img src="/files/${encodeURIComponent(n)}?v=${Date.now()}">`}else $('#photoPreview').textContent='JC'}catch(e){toast(e.message)}}
$('#profileForm').onsubmit=async e=>{e.preventDefault();const o=Object.fromEntries(new FormData(e.target));try{await api('/api/profile',{method:'PUT',body:JSON.stringify(o)});toast('Perfil salvo.');loadProfile();dashboard()}catch(x){toast(x.message)}};
async function upload(file,kind){if(!file)return;const st=$('#docStatus'),max=kind==='PHOTO'?8e6:15e6;if(file.size>max){st.textContent='Arquivo muito grande.';return}st.textContent='Enviando...';try{const r=await fetch(`/api/profile/upload?kind=${kind}&fileName=${encodeURIComponent(file.name)}`,{method:'POST',headers:{'Content-Type':file.type||'application/octet-stream'},body:file});const j=await r.json().catch(()=>({}));if(!r.ok)throw new Error(j.error||`HTTP ${r.status}`);st.textContent='Arquivo atualizado com sucesso.';loadProfile()}catch(e){st.textContent='Erro: '+e.message}}
$('#photoFile').onchange=e=>upload(e.target.files[0],'PHOTO');$('#resumeFile').onchange=e=>upload(e.target.files[0],'RESUME');

async function loadSettings(){try{const s=await api('/api/settings'),f=$('#settingsForm');['min_score_auto','max_auto_per_run','max_auto_per_day','auto_send_min_score','auto_send_daily_limit','auto_manager_interval_minutes','email_monitor_interval_minutes'].forEach(k=>{if(f.elements[k])f.elements[k].value=s[k]??''});['auto_manager_enabled','email_monitor_enabled','auto_send_email'].forEach(k=>{if(f.elements[k])f.elements[k].checked=String(s[k]??(k==='auto_send_email'?'false':'true'))==='true'});if($('#googleQuotaStatus'))$('#googleQuotaStatus').textContent=`Cota diária Google: ${s.quotaUsed||0}/${s.quotaLimit||100} usadas • ${s.quotaRemaining??0} restantes`;await loadGoogleStatus();if($('#settingsGmail'))$('#settingsGmail').value=s.verification_email||s.gmail_sender||''}catch(e){toast(e.message)}}
$('#settingsForm').onsubmit=async e=>{e.preventDefault();const o=Object.fromEntries(new FormData(e.target));['auto_manager_enabled','email_monitor_enabled','auto_send_email'].forEach(k=>o[k]=e.target.elements[k]?.checked?'true':'false');try{await api('/api/settings',{method:'PUT',body:JSON.stringify(o)});toast('Configurações salvas.')}catch(x){toast(x.message)}};
$('#sendVerificationCodeBtn').onclick=async()=>{const st=$('#verificationCodeStatus');st.textContent='Enviando pela Gmail API...';try{const gs=await api('/api/google/status');const email=gs.email||'';const r=await api('/api/gmail/send-code',{method:'POST',body:JSON.stringify({email})});st.textContent=`✅ E-mail de teste enviado para ${r.email}.`}catch(e){st.textContent='❌ '+e.message}};
$('#runDiagnostics').onclick=async()=>{const el=$('#diagnosticState');el.textContent='Verificando...';try{const d=await api('/api/diagnostics');el.textContent=`Banco: ${d.database} • Currículo: ${d.resume} • Gmail: ${d.gmail} • Vagas: ${d.jobs} • Candidaturas: ${d.applications}`}catch(e){el.textContent='Erro: '+e.message}};

$('#findForMe').onclick=findJobs;$('#runAutomation').onclick=runAutomation;$('#runAutomationTop').onclick=runAutomation;$('#checkEmail').onclick=checkEmail;$('#checkEmailTop').onclick=checkEmail;$('#refreshActivity').onclick=loadActivity;if($('#refreshDiscarded'))$('#refreshDiscarded').onclick=loadDiscardedJobs;if($('#approveBulk90'))$('#approveBulk90').onclick=approveBulk90;
$('#simpleScope').onchange=loadJobs;
const savedTheme=localStorage.getItem('speed-theme')||'dark';document.body.dataset.theme=savedTheme;if($('#themeToggle'))$('#themeToggle').onclick=()=>{const next=document.body.dataset.theme==='light'?'dark':'light';document.body.dataset.theme=next;localStorage.setItem('speed-theme',next)};
(async()=>{await Promise.all([dashboard(),loadProfile(),refreshGoogleSearchUi()]);setInterval(()=>dashboard(),30000)})();

$('#gmailDiagnosticBtn').onclick=async()=>{
  const el=$('#gmailDiagnosticState'),btn=$('#gmailDiagnosticBtn');btn.disabled=true;btn.textContent='Testando...';el.textContent='Conectando ao Google...';
  try{
    const r=await api('/api/gmail/diagnostic');
    el.textContent=(r.status==='OK'?'✅ ':'⚠ ')+r.message;
    $('#gmailState').textContent=r.status==='OK'?`✓ Gmail autenticado: ${r.email}`:`⚠ Gmail: ${r.status}`;
  }catch(e){el.textContent='❌ '+e.message}
  finally{btn.disabled=false;btn.textContent='Diagnosticar Gmail'}
};


async function loadGoogleStatus(){
  try{
    const s=await api('/api/google/status');
    const ok=!!s.connected&&s.permissionsOk!==false;
    const step1=$('#step1State'),step2=$('#step2State'),step3=$('#step3State');
    if(step1){step1.textContent=s.credentialsPresent?'Pronto':'Pendente';step1.className='stepState '+(s.credentialsPresent?'done':'pending')}
    if(step2){step2.textContent=s.credentialsPresent?'Importado':'Pendente';step2.className='stepState '+(s.credentialsPresent?'done':'pending')}
    if(step3){step3.textContent=ok?'Conectado':(s.connected?'Permissões incompletas':'Pendente');step3.className='stepState '+(ok?'done':'pending')}
    $('#gmailState').textContent=ok?`✓ Google + Gmail prontos: ${s.email}`:(s.connected?'⚠ Google conectado, mas faltam permissões do Gmail. Clique em Reconectar permissões Google.':(s.credentialsPresent?'Credencial pronta. Clique em Continuar com Google.':'Importe o credentials.json.'));
    $('#googleOAuthStatus').textContent=s.message||'';
    $('#connectGoogleBtn').setAttribute('aria-disabled',(ok||!s.credentialsPresent)?'true':'false');
    if($('#repairGoogleBtn'))$('#repairGoogleBtn').style.display=s.connected&&!ok?'inline-block':'none';
    $('#disconnectGoogleBtn').disabled=!s.connected;
    $('#sendVerificationCodeBtn').disabled=!ok;
    if($('#checkEmailConnectionBtn'))$('#checkEmailConnectionBtn').disabled=!ok;
  }catch(e){$('#gmailState').textContent='Erro ao verificar Google';$('#googleOAuthStatus').textContent=e.message}
}
$('#saveGoogleCredentials').onclick=async()=>{
  const f=$('#googleCredentialsFile').files[0];
  if(!f){toast('Escolha o arquivo credentials.json.');return}
  const b=await f.arrayBuffer();
  try{
    const r=await fetch('/api/google/credentials',{method:'POST',headers:{'Content-Type':'application/json'},body:b});
    const j=await r.json().catch(()=>({}));if(!r.ok)throw new Error(j.error||`HTTP ${r.status}`);
    toast('credentials.json importado. Agora conecte sua conta Google.');await loadGoogleStatus();
  }catch(e){toast(e.message)}
};
async function startGoogleLogin(){
  const st=$('#googleOAuthStatus'),btn=$('#connectGoogleBtn');
  try{
    btn.disabled=true;
    st.textContent='Verificando credencial Google...';
    const s=await api('/api/google/status');
    if(!s.credentialsPresent){
      go('config');
      st.textContent='Importe o credentials.json no passo 2 antes de conectar sua conta.';
      toast('Falta importar o credentials.json. Use o passo 2 em Configurações.');
      return;
    }
    if(s.connected&&s.permissionsOk!==false){
      st.textContent='Google já está conectado.';
      toast('Google já está conectado.');
      return;
    }
    st.textContent='Abrindo login oficial do Google...';
    window.location.assign('/google/connect');
  }catch(e){
    go('config');
    st.textContent='Erro ao preparar login Google: '+e.message;
    toast(e.message);
  }finally{btn.disabled=false}
}
$('#connectGoogleBtn').onclick=startGoogleLogin;
if($('#repairGoogleBtn'))$('#repairGoogleBtn').onclick=startGoogleLogin;
$('#disconnectGoogleBtn').onclick=async()=>{try{await api('/api/google/disconnect',{method:'POST'});toast('Google desconectado.');loadGoogleStatus()}catch(e){toast(e.message)}};


if($('#openGoogleCloudBtn'))$('#openGoogleCloudBtn').onclick=()=>{
  window.open('https://console.cloud.google.com/auth/clients','_blank','noopener');
  $('#googleOAuthStatus').textContent='No Google Cloud: crie um OAuth Client do tipo Desktop app e baixe o credentials.json.';
};

if($('#checkEmailConnectionBtn'))$('#checkEmailConnectionBtn').onclick=async()=>{
  const btn=$('#checkEmailConnectionBtn'),st=$('#verificationCodeStatus');
  btn.disabled=true;btn.textContent='Testando leitura...';st.textContent='Verificando Gmail pela API...';
  try{
    const r=await api('/api/email/check',{method:'POST',body:JSON.stringify({max:5})});
    st.textContent=`✅ Gmail API funcionando. ${r.checked||0} mensagem(ns) verificada(s).`;
  }catch(e){
    st.textContent='❌ '+e.message;
  }finally{
    btn.textContent='Testar leitura do Gmail';
    await loadGoogleStatus();
  }
};

async function resumeGoogleOAuthPoll(){
  try{
    const p=await api('/api/google/poll');
    if(p.connected){
      toast('Google conectado com sucesso.');
      await loadGoogleStatus();
      return;
    }
    if(p.waiting){
      $('#googleOAuthStatus').textContent='Aguardando conclusão do login Google...';
    }
  }catch(_e){}
}
setTimeout(resumeGoogleOAuthPoll,700);

// Se o backend redirecionar por falta de credencial, volta direto para Configurações.
(()=>{
  const q=new URLSearchParams(window.location.search);
  if(q.get('google')==='credentials-missing'){
    go('config');
    setTimeout(()=>{
      const st=$('#googleOAuthStatus');
      if(st)st.textContent='Credencial Google não encontrada. Importe o credentials.json no passo 2.';
      toast('Importe o credentials.json para continuar com o Google.');
    },150);
    history.replaceState({},'',window.location.pathname);
  }
})();


refreshGoogleSearchUi();
