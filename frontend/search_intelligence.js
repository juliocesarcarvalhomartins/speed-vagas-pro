(function(root,factory){
  const api=factory();
  if(typeof module==='object'&&module.exports)module.exports=api;
  else root.SpeedSearch=api;
})(typeof self!=='undefined'?self:this,function(){
  const norm=s=>String(s||'').normalize('NFD').replace(/[\u0300-\u036f]/g,'').toLowerCase().replace(/\s+/g,' ').trim();

  function sourceFromUrl(url){
    const u=norm(url);
    if(u.includes('linkedin.com'))return 'LinkedIn';
    if(u.includes('indeed.com'))return 'Indeed';
    if(u.includes('infojobs.com.br'))return 'InfoJobs';
    if(u.includes('vagas.com.br'))return 'Vagas.com.br';
    if(u.includes('gupy.io'))return 'Gupy';
    return 'Google';
  }

  function isBlocked(text){
    const h=norm(text);
    const blocked=[
      'senior','senior ',' sr ',' sr.','pleno','n2','n3','tier ii','tier iii',
      'tier 2','tier 3','level 2','level 3','lead ','manager','gerente',
      'principal','staff engineer','especialista','coordenador','supervisor'
    ];
    return blocked.some(x=>h.includes(x));
  }

  function profileTerms(profile={}){
    const roles=String(profile.target_roles||'').split(/[,;\n]+/).map(x=>norm(x)).filter(x=>x.length>2);
    const skills=String(profile.skills||'').split(/[,;\n]+/).map(x=>norm(x)).filter(x=>x.length>2);
    return {roles,skills};
  }

  function isRelevant(text,profile={}){
    const h=norm(text);
    if(isBlocked(h))return false;
    const {roles,skills}=profileTerms(profile);
    if(roles.length||skills.length){
      for(const role of roles){
        if(h.includes(role))return true;
        const words=role.split(' ').filter(w=>w.length>2&&!['junior','estagio','assistente','auxiliar'].includes(w));
        if(words.length&&words.filter(w=>h.includes(w)).length>=Math.min(2,words.length))return true;
      }
      if(skills.some(k=>h.includes(k)))return true;
      return false;
    }
    const fallback=['junior','estagio','intern','trainee','assistente','auxiliar','n1','tier 1','level 1','help desk','service desk','it support','data analyst','sql'];
    return fallback.some(x=>h.includes(norm(x)));
  }

  function scoreResult(r,profile={}){
    const h=norm([r.title,r.snippet,r.link].join(' '));
    if(isBlocked(h))return 0;
    const {roles,skills}=profileTerms(profile);
    let s=35;
    if(/junior|estagio|intern|trainee|assistente|auxiliar|n1|tier 1|level 1/.test(h))s+=20;
    let roleHits=0;
    for(const role of roles){
      if(h.includes(role)){roleHits=Math.max(roleHits,3);continue;}
      const words=role.split(' ').filter(w=>w.length>2);
      roleHits=Math.max(roleHits,words.filter(w=>h.includes(w)).length);
    }
    s+=Math.min(30,roleHits*10);
    let skillHits=0;for(const k of skills)if(h.includes(k))skillHits++;
    s+=Math.min(20,skillHits*4);
    if(/remoto|remote|home office|sao paulo|ferraz|poa|suzano|mogi|itaquera|penha|tatuape|guaianases|sao miguel/.test(h))s+=8;
    return Math.max(0,Math.min(99,s));
  }

  function inferCompany(title,source){
    let t=String(title||'').replace(/\s+/g,' ').trim();
    const suffix=new RegExp('\\s*[-|–—]\\s*'+String(source||'').replace('.','\\.')+'\\s*$','i');
    t=t.replace(suffix,'');
    const parts=t.split(/\s+[|–—]\s+|\s+-\s+/).map(x=>x.trim()).filter(Boolean);
    if(parts.length>=2)return parts[parts.length-1].slice(0,120);
    return source||'Empresa';
  }

  function cleanTitle(title,source){
    let t=String(title||'').replace(/\s+/g,' ').trim();
    t=t.replace(new RegExp('\\s*[-|–—]\\s*'+String(source||'').replace('.','\\.')+'\\s*$','i'),'');
    const parts=t.split(/\s+[|–—]\s+|\s+-\s+/).map(x=>x.trim()).filter(Boolean);
    return (parts[0]||t||'Vaga').slice(0,180);
  }

  function dedupe(rows){
    const seen=new Set(),out=[];
    for(const r of rows||[]){
      const key=norm(r.link||r.url||r.title);
      if(!key||seen.has(key))continue;
      seen.add(key);out.push(r);
    }
    return out;
  }

  function buildQueries(profile={},scope='EAST_PLUS_REMOTE'){
    const roles=String(profile.target_roles||'').split(/[,;\n]+/).map(x=>x.trim()).filter(Boolean).slice(0,6);
    const skills=String(profile.skills||'').split(/[,;\n]+/).map(x=>x.trim()).filter(Boolean).slice(0,4);
    let base=[...new Set([...roles,...skills])];
    if(!base.length)base=['suporte TI','help desk','service desk','assistente de TI','estágio TI','analista de dados junior'];
    const terms=base.slice(0,8).map(x=>`"${x}"`).join(' OR ');
    const east='"Zona Leste" OR Itaquera OR "São Miguel Paulista" OR Penha OR Tatuapé OR Guaianases OR "Itaim Paulista" OR "Ermelino Matarazzo" OR "Ferraz de Vasconcelos" OR Poá OR Suzano OR "Mogi das Cruzes"';
    const remote='"home office" OR remoto OR remote';
    const geo=scope==='REMOTE'?remote:scope==='EAST_ZONE'?east:`(${east}) OR (${remote})`;
    return [
      {source:'LinkedIn',q:`site:linkedin.com/jobs ${terms} (${geo})`},
      {source:'Indeed',q:`site:indeed.com ${terms} (${geo})`},
      {source:'InfoJobs',q:`site:infojobs.com.br ${terms} (${geo})`},
      {source:'Vagas.com.br',q:`site:vagas.com.br ${terms} (${geo})`},
      {source:'Gupy',q:`site:gupy.io/jobs ${terms} (${geo})`}
    ];
  }


  return {norm,sourceFromUrl,isBlocked,isRelevant,scoreResult,inferCompany,cleanTitle,dedupe,buildQueries};
});
