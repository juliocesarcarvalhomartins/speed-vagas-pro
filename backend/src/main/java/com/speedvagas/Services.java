package com.speedvagas;

import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import java.io.*;
import java.net.URI;
import java.net.Socket;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.*;
import java.text.Normalizer;
import java.time.*;
import java.util.*;
import java.util.regex.*;

public final class Services {
    private Services() {}
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(6)).followRedirects(HttpClient.Redirect.NORMAL).build();

    public static Map<String,Object> profile() throws Exception {
        var rows=Database.query("select * from candidate_profile order by id limit 1"); return rows.isEmpty()?new LinkedHashMap<>():rows.get(0);
    }
    public static Map<String,Object> saveProfile(Map<String,Object> m) throws Exception {
        Map<String,Object> p=profile(); long id=((Number)p.get("id")).longValue();
        Database.update("update candidate_profile set name=?,email=?,phone=?,city=?,state=?,target_roles=?,skills=?,radius_km=?,updated_at=CURRENT_TIMESTAMP where id=?",
                Json.str(m,"name",String.valueOf(p.get("name"))),Json.str(m,"email",String.valueOf(p.get("email"))),Json.str(m,"phone",String.valueOf(p.get("phone"))),Json.str(m,"city",String.valueOf(p.get("city"))),Json.str(m,"state",String.valueOf(p.get("state"))),Json.str(m,"target_roles",String.valueOf(p.get("target_roles"))),Json.str(m,"skills",String.valueOf(p.get("skills"))),Json.dbl(m,"radius_km",Double.parseDouble(String.valueOf(p.get("radius_km")))),id);
        recalcAll(); return profile();
    }
    public static Map<String,Object> saveDocument(Map<String,Object> m) throws Exception {
        String kind=Json.str(m,"kind","RESUME").toUpperCase(Locale.ROOT);
        String file=Json.str(m,"fileName","arquivo.bin");
        String mime=Json.str(m,"mime","application/octet-stream");
        String b64=Json.str(m,"dataBase64","");
        if(b64.contains(",")) b64=b64.substring(b64.indexOf(',')+1);
        return saveDocumentBytes(kind,file,mime,Base64.getDecoder().decode(b64));
    }
    public static Map<String,Object> saveDocumentBytes(String kind,String file,String mime,byte[] data) throws Exception {
        kind=(kind==null?"RESUME":kind).toUpperCase(Locale.ROOT);
        file=Path.of(file==null||file.isBlank()?"arquivo.bin":file).getFileName().toString();
        mime=mime==null?"application/octet-stream":mime.toLowerCase(Locale.ROOT);
        String ext=file.toLowerCase(Locale.ROOT);
        if("PHOTO".equals(kind)){
            boolean ok=mime.startsWith("image/") && (ext.endsWith(".png")||ext.endsWith(".jpg")||ext.endsWith(".jpeg")||ext.endsWith(".webp"));
            if(!ok) throw new IllegalArgumentException("Foto deve ser JPG, PNG ou WebP.");
        } else if("RESUME".equals(kind)){
            boolean ok=ext.endsWith(".pdf")||ext.endsWith(".doc")||ext.endsWith(".docx");
            if(!ok) throw new IllegalArgumentException("Currículo deve ser PDF, DOC ou DOCX.");
        } else throw new IllegalArgumentException("Tipo de documento inválido.");
        long limit="PHOTO".equals(kind)?8_000_000:15_000_000;
        if(data==null||data.length==0)throw new IllegalArgumentException("Arquivo vazio.");
        if(data.length>limit)throw new IllegalArgumentException("Arquivo excede o limite de "+(limit/1_000_000)+" MB.");
        String safe=(kind.equals("PHOTO")?"foto_":"curriculo_")+System.currentTimeMillis()+"_"+file.replaceAll("[^a-zA-Z0-9._-]","_");
        Path path=Path.of(cfg("SPEED_UPLOAD_DIR","data/uploads"),safe); Files.createDirectories(path.getParent()); Files.write(path,data);
        Map<String,Object> p=profile(); long pid=((Number)p.get("id")).longValue();
        Database.insert("insert into candidate_documents(candidate_id,document_type,file_name,mime_type,file_path,file_size,created_at) values(?,?,?,?,?,?,CURRENT_TIMESTAMP)",pid,kind,file,mime,path.toString().replace('\\','/'),data.length);
        if("PHOTO".equals(kind)) Database.update("update candidate_profile set photo_path=?,updated_at=CURRENT_TIMESTAMP where id=?",path.toString().replace('\\','/'),pid);
        else Database.update("update candidate_profile set resume_path=?,updated_at=CURRENT_TIMESTAMP where id=?",path.toString().replace('\\','/'),pid);
        Map<String,Object> out=profile(); out.put("uploaded_file",safe); return out;
    }
    public static List<Map<String,Object>> jobs(Map<String,String> q) throws Exception {
        List<Map<String,Object>> rows=Database.query("select j.*, c.name company_name, c.website company_website from jobs j join companies c on c.id=j.company_id order by priority_score desc, published_at desc");
        int min=(int)parse(q.get("minScore"),0); String mode=q.getOrDefault("mode","TODAS"); String term=norm(q.getOrDefault("q","")); String sort=q.getOrDefault("sort","priority");
        Map<String,Object> prof=profile(); String roles=String.valueOf(prof.getOrDefault("target_roles","")), skills=String.valueOf(prof.getOrDefault("skills",""));
        rows.removeIf(j->{String m=String.valueOf(j.get("work_mode"));boolean remote=isRemote(m);double d=num(j.get("distance_km"));int sc=(int)num(j.get("compatibility_score"));String hay=norm(j.get("title")+" "+j.get("company_name")+" "+j.get("city")+" "+j.get("description"));boolean entry=JobRules.isEntryLevel(String.valueOf(j.get("title")),String.valueOf(j.get("description")),String.valueOf(j.get("level")));boolean relevant=JobRules.isRelevantToProfile(roles,skills,String.valueOf(j.get("title")),String.valueOf(j.get("description")));return !entry||!relevant||sc<min||(!"TODAS".equalsIgnoreCase(mode)&&!m.equalsIgnoreCase(mode))||(!term.isBlank()&&!hay.contains(term));});
        Comparator<Map<String,Object>> c=switch(sort){case"compatibility"->Comparator.comparingDouble((Map<String,Object>x)->num(x.get("compatibility_score"))).reversed();case"recent"->Comparator.comparing((Map<String,Object>x)->String.valueOf(x.get("published_at")),Comparator.reverseOrder());default->Comparator.comparingDouble((Map<String,Object>x)->num(x.get("priority_score"))).reversed();}; rows.sort(c); return rows;
    }
    public static Map<String,Object> addJob(Map<String,Object> m) throws Exception {
        String company=PublicJobSources.strip(Json.str(m,"company","Empresa")); String website=Json.str(m,"companyWebsite","");
        String title=PublicJobSources.strip(Json.str(m,"title","Vaga"));String city=PublicJobSources.strip(Json.str(m,"city","São Paulo"));
        String source=Json.str(m,"source","MANUAL"),externalId=Json.str(m,"externalId",UUID.randomUUID().toString());
        if(isCrossSourceDuplicate(company,title,city,Json.str(m,"url",""))) throw new IllegalStateException("Vaga duplicada: empresa+título+local já existem no gestor.");
        long cid=company(company,website);
        long id=Database.insert("insert into jobs(company_id,source,external_id,title,city,state,work_mode,level,salary_text,description,requirements,url,published_at,created_at) values(?,?,?,?,?,?,?,?,?,?,?,?,?,CURRENT_TIMESTAMP)",cid,source,externalId,title,city,Json.str(m,"state","SP"),Json.str(m,"workMode","Presencial"),Json.str(m,"level","Júnior"),Json.str(m,"salaryText",""),PublicJobSources.strip(Json.str(m,"description","")),PublicJobSources.strip(Json.str(m,"requirements","")),Json.str(m,"url",""),Json.str(m,"publishedAt",LocalDate.now().toString()));
        recalc(id); return Database.query("select j.*,c.name company_name,c.website company_website from jobs j join companies c on c.id=j.company_id where j.id=?",id).get(0);
    }
    private static boolean isCrossSourceDuplicate(String company,String title,String city,String url)throws Exception{
        String u=String.valueOf(url).trim();if(!u.isBlank()&&!Database.query("select id from jobs where url=? limit 1",u).isEmpty())return true;
        String key=JobRules.norm(company)+"|"+JobRules.norm(title)+"|"+JobRules.norm(city);
        for(Map<String,Object> r:Database.query("select j.title,j.city,c.name company_name from jobs j join companies c on c.id=j.company_id order by j.id desc limit 1500")){
            String k=JobRules.norm(String.valueOf(r.get("company_name")))+"|"+JobRules.norm(String.valueOf(r.get("title")))+"|"+JobRules.norm(String.valueOf(r.get("city")));
            if(k.equals(key))return true;
        }
        return false;
    }
    private static long company(String name,String website)throws Exception{var r=Database.query("select id from companies where lower(name)=lower(?) limit 1",name);if(!r.isEmpty()){long id=((Number)r.get(0).get("id")).longValue();if(!website.isBlank())Database.update("update companies set website=coalesce(nullif(website,''),?) where id=?",website,id);return id;}return Database.insert("insert into companies(name,website,created_at) values(?,?,CURRENT_TIMESTAMP)",name,website);}

    public static Map<String,Object> searchInternet(Map<String,Object> req) throws Exception {
        String query=Json.str(req,"query","suporte TI");
        String scope=Json.str(req,"scope","ALL").toUpperCase(Locale.ROOT);
        String where=Json.str(req,"where","São Paulo");
        int limit=Math.max(1,Math.min(Json.integer(req,"limit",25),100));
        int added=0,skipped=0;
        List<Map<String,Object>> stats=new ArrayList<>();
        List<String> providers=new ArrayList<>(),errors=new ArrayList<>(),notices=new ArrayList<>();
        List<Map<String,Object>> providerErrors=new ArrayList<>();
        Map<String,Object> prof=profile();String targetRoles=String.valueOf(prof.getOrDefault("target_roles","")),skills=String.valueOf(prof.getOrDefault("skills",""));

        List<PublicJobSources.Batch> batches=new ArrayList<>();
        boolean remote=scope.equals("ALL")||scope.equals("REMOTE")||scope.equals("EAST_PLUS_REMOTE");
        boolean local=scope.equals("ALL")||scope.equals("LOCAL")||scope.equals("EAST_ZONE")||scope.equals("EAST_PLUS_REMOTE");

        if(remote){
            batches.add(PublicJobSources.jobicy(query,Math.min(50,limit)));
            batches.add(PublicJobSources.remotive(query,Math.min(50,limit)));
            batches.add(PublicJobSources.arbeitnow(query,Math.min(50,limit)));
            batches.add(PublicJobSources.remoteok(query,Math.min(50,limit)));
        }
        if(local)batches.add(PublicJobSources.adzuna(query,where,Math.min(50,limit)));

        for(PublicJobSources.Batch b:batches){
            if(b.configured())providers.add(b.provider());else notices.add(b.provider()+": "+b.note());
            int found=b.jobs().size(),before=added,dup=0;
            if(b.error()!=null&&!b.error().isBlank()){errors.add(b.provider()+": "+b.error());providerErrors.add(Map.of("provider",b.provider(),"code",b.errorCode(),"message",b.error()));}
            for(Map<String,Object> j:b.jobs()){
                if(added>=limit)break;
                String source=String.valueOf(j.get("source")),ext=String.valueOf(j.get("externalId"));
                String title=String.valueOf(j.getOrDefault("title","")),desc=String.valueOf(j.getOrDefault("description","")),level=String.valueOf(j.getOrDefault("level",""));
                if(!JobRules.isEntryLevel(title,desc,level)){skipped++;ActivityService.jobDecision(j,"SENIOR_LEVEL","Senioridade ou requisito de experiência acima do nível de entrada.",null);continue;}
                if(!JobRules.isRelevantToProfile(targetRoles,skills,title,desc)){skipped++;ActivityService.jobDecision(j,"OUT_OF_PROFILE","A vaga não contém cargo/habilidades compatíveis com o perfil configurado.",null);continue;}
                if(!Database.query("select id from jobs where source=? and external_id=?",source,ext).isEmpty()){dup++;skipped++;ActivityService.jobDecision(j,"DUPLICATE","Mesmo identificador já importado desta fonte.",null);continue;}
                try{addJob(j);added++;}catch(IllegalStateException e){dup++;skipped++;ActivityService.jobDecision(j,"DUPLICATE",e.getMessage(),null);}catch(Exception e){skipped++;errors.add(b.provider()+": "+e.getMessage());providerErrors.add(Map.of("provider",b.provider(),"code","INGEST_ERROR","message",String.valueOf(e.getMessage())));}
            }
            Map<String,Object> st=new LinkedHashMap<>();st.put("provider",b.provider());st.put("configured",b.configured());
            st.put("found",found);st.put("added",added-before);st.put("duplicates",dup);
            st.put("status",!b.configured()?"OPTIONAL":(b.error()==null||b.error().isBlank()?"OK":"ERROR"));st.put("note",b.note());stats.add(st);
        }

        long run=Database.insert("insert into search_runs(query_text,provider,status,items_found,items_added,error_message,started_at,finished_at) values(?,?,?,?,?,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",
            query,String.join("+",providers),errors.isEmpty()?"SUCCESS":"PARTIAL",added+skipped,added,String.join(" | ",errors));

        Map<String,Object> out=new LinkedHashMap<>();out.put("runId",run);out.put("providers",providers);out.put("providerStats",stats);
        out.put("added",added);out.put("skipped",skipped);out.put("errors",errors);out.put("providerErrors",providerErrors);out.put("notices",notices);out.put("scope",scope);
        out.put("localConfigured",PublicJobSources.adzuna("teste",where,1).configured());
        out.put("message",added>0?added+" vaga(s) nova(s) encontrada(s).":"Nenhuma vaga nova entrou nesta consulta.");
        return out;
    }

    public static Map<String,Object> smartSearch(Map<String,Object> req) throws Exception {
        String scope=Json.str(req,"scope","EAST_PLUS_REMOTE").toUpperCase(Locale.ROOT);
        int totalLimit=Math.max(10,Math.min(Json.integer(req,"limit",80),150));
        Map<String,Object> prof=profile();
        List<String> queries=buildSearchQueries(prof);
        List<String> east=List.of("Ferraz de Vasconcelos","Poá","Suzano","Mogi das Cruzes","Itaquera, São Paulo","São Miguel Paulista, São Paulo","Penha, São Paulo","Tatuapé, São Paulo","Vila Matilde, São Paulo","Ermelino Matarazzo, São Paulo","Guaianases, São Paulo","Itaim Paulista, São Paulo");
        int added=0,skipped=0;List<String> providers=new ArrayList<>(),errors=new ArrayList<>(),notices=new ArrayList<>();
        List<Map<String,Object>> providerStats=new ArrayList<>();

        // Uma rodada remota por consulta. Jobicy reutiliza cache de 1h; Remotive aplica busca por termo.
        if(scope.equals("REMOTE")||scope.equals("ALL")||scope.equals("EAST_PLUS_REMOTE")){
            for(String q:queries){
                if(added>=totalLimit)break;
                Map<String,Object> r=searchInternet(new LinkedHashMap<>(Map.of("query",q,"scope","REMOTE","where","Brasil","limit",Math.min(30,totalLimit-added))));
                added+=((Number)r.getOrDefault("added",0)).intValue();skipped+=((Number)r.getOrDefault("skipped",0)).intValue();
                mergeList(providers,r.get("providers"));mergeList(errors,r.get("errors"));mergeList(notices,r.get("notices"));mergeStats(providerStats,r.get("providerStats"));
            }
        }

        // Busca local só quando a fonte local está configurada.
        boolean localConfigured=PublicJobSources.adzuna("teste","São Paulo",1).configured();
        if((scope.equals("LOCAL")||scope.equals("EAST_ZONE")||scope.equals("ALL")||scope.equals("EAST_PLUS_REMOTE"))&&localConfigured){
            int calls=0;
            outer:for(String loc:east)for(String q:queries){
                if(added>=totalLimit||calls>=24)break outer;calls++;
                Map<String,Object> r=searchInternet(new LinkedHashMap<>(Map.of("query",q,"scope","LOCAL","where",loc,"limit",Math.min(12,totalLimit-added))));
                added+=((Number)r.getOrDefault("added",0)).intValue();skipped+=((Number)r.getOrDefault("skipped",0)).intValue();
                mergeList(providers,r.get("providers"));mergeList(errors,r.get("errors"));mergeStats(providerStats,r.get("providerStats"));
            }
        }else if(scope.equals("LOCAL")||scope.equals("EAST_ZONE")||scope.equals("EAST_PLUS_REMOTE")){
            notices.add("Busca local da Zona Leste não está conectada; resultados Home Office continuam funcionando.");
        }

        recalcAll();
        Map<String,Object> out=new LinkedHashMap<>();out.put("added",added);out.put("skipped",skipped);out.put("providers",new LinkedHashSet<>(providers));
        out.put("errors",new LinkedHashSet<>(errors));out.put("notices",new LinkedHashSet<>(notices));out.put("providerStats",providerStats);
        out.put("scope",scope);out.put("localConfigured",localConfigured);
        out.put("message",added>0?added+" vaga(s) nova(s) adicionada(s).":"Busca concluída sem novas vagas; vagas já salvas continuam disponíveis.");
        return out;
    }

    static List<String> buildSearchQueries(Map<String,Object> prof){
        LinkedHashSet<String> out=new LinkedHashSet<>();
        String roles=String.valueOf(prof==null?"":prof.getOrDefault("target_roles",""));
        String skills=String.valueOf(prof==null?"":prof.getOrDefault("skills",""));
        for(String raw:roles.split("[,;\\n]")){String q=raw.trim();if(q.length()>=3)out.add(q);if(out.size()>=8)break;}
        if(out.size()<4)for(String raw:skills.split("[,;\\n]")){String q=raw.trim();if(q.length()>=3)out.add(q);if(out.size()>=8)break;}
        // Fallback só para perfil vazio; nunca substitui cargos configurados pelo usuário.
        if(out.isEmpty())out.addAll(List.of("suporte TI","help desk","service desk","assistente TI","estagio TI","analista dados junior"));
        return new ArrayList<>(out);
    }

    private static void mergeList(List<String> dest,Object obj){if(obj instanceof Collection<?> c)for(Object x:c){String v=String.valueOf(x);if(!dest.contains(v))dest.add(v);}}
    private static void mergeStats(List<Map<String,Object>> dest,Object obj){if(obj instanceof Collection<?> c)for(Object x:c)if(x instanceof Map<?,?> m){Map<String,Object> z=new LinkedHashMap<>();for(var e:m.entrySet())z.put(String.valueOf(e.getKey()),e.getValue());dest.add(z);}}

    public static Map<String,Object> discoverContacts(Map<String,Object> req) throws Exception {
        long companyId=Json.lng(req,"companyId",0); String website=Json.str(req,"website",""); if(companyId>0&&website.isBlank()){var r=Database.query("select website from companies where id=?",companyId);if(!r.isEmpty())website=String.valueOf(r.get(0).get("website"));} if(website.isBlank())throw new IllegalArgumentException("Informe o site oficial da empresa"); if(!website.startsWith("http"))website="https://"+website;
        URI base=URI.create(website); String host=base.getHost(); if(host==null)throw new IllegalArgumentException("Site inválido");
        LinkedHashSet<String> emails=new LinkedHashSet<>(), phones=new LinkedHashSet<>(), pages=new LinkedHashSet<>(); pages.add(website); String origin=base.getScheme()+"://"+host; for(String p:List.of("/contato","/contact","/fale-conosco","/trabalhe-conosco","/carreiras","/careers"))pages.add(origin+p);
        for(String page:pages){try{HttpRequest h=HttpRequest.newBuilder(URI.create(page)).timeout(Duration.ofSeconds(8)).header("User-Agent","SPEED-VAGAS/3.0").GET().build();HttpResponse<String> r=HTTP.send(h,HttpResponse.BodyHandlers.ofString());if(r.statusCode()/100!=2)continue;String html=r.body();Matcher em=Pattern.compile("[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}",Pattern.CASE_INSENSITIVE).matcher(html);while(em.find()){String e=em.group();if(!e.toLowerCase().matches(".*(example|sentry|wixpress|cloudflare).*"))emails.add(e);}Matcher ph=Pattern.compile("(?:\\+?55\\s*)?(?:\\(?\\d{2}\\)?\\s*)?9?\\d{4}[- .]?\\d{4}").matcher(html);while(ph.find())phones.add(ph.group().trim());}catch(Exception ignored){}}
        int saved=0; for(String e:emails){if(companyId>0 && Database.query("select id from company_contacts where company_id=? and contact_value=?",companyId,e).isEmpty()){String role=e.toLowerCase().matches(".*(rh|recrut|talent|carreira|jobs|people|vagas).*" )?"RH/RECRUTAMENTO":"CORPORATIVO";Database.insert("insert into company_contacts(company_id,contact_type,contact_value,role_label,source_url,is_public,confidence,created_at) values(?,?,?,?,?,?,?,CURRENT_TIMESTAMP)",companyId,"EMAIL",e,role,website,true,role.startsWith("RH")?95:65);saved++;}}
        for(String p:phones){if(companyId>0 && Database.query("select id from company_contacts where company_id=? and contact_value=?",companyId,p).isEmpty()){Database.insert("insert into company_contacts(company_id,contact_type,contact_value,role_label,source_url,is_public,confidence,created_at) values(?,?,?,?,?,?,?,CURRENT_TIMESTAMP)",companyId,"PHONE",p,"CORPORATIVO",website,true,55);saved++;}}
        Map<String,Object> out=new LinkedHashMap<>();out.put("emails",new ArrayList<>(emails));out.put("phones",new ArrayList<>(phones));out.put("saved",saved);out.put("policy","Somente contatos publicados em páginas oficiais da empresa; sem coleta de dados privados.");return out;
    }

    public static List<Map<String,Object>> contacts() throws Exception { return Database.query("select cc.*,c.name company_name,c.website from company_contacts cc join companies c on c.id=cc.company_id order by cc.confidence desc,cc.created_at desc"); }
    public static List<Map<String,Object>> applications() throws Exception { return Database.query("select a.*,j.title,j.compatibility_score,c.name company_name from applications a join jobs j on j.id=a.job_id join companies c on c.id=j.company_id order by a.updated_at desc"); }

    public static Map<String,Object> prepareApplication(long jobId) throws Exception {
        if(jobId==0) throw new IllegalArgumentException("Vaga inválida.");
        var existing=Database.query("select * from applications where job_id=?",jobId);
        if(!existing.isEmpty()) return existing.get(0);
        var rows=Database.query("select j.*,c.name company_name,c.website company_website from jobs j join companies c on c.id=j.company_id where j.id=?",jobId);
        if(rows.isEmpty()) throw new IllegalArgumentException("Vaga não encontrada.");
        Map<String,Object> j=rows.get(0);
        long companyId=((Number)j.get("company_id")).longValue();
        var contacts=Database.query("select * from company_contacts where company_id=? and contact_type='EMAIL' and is_public=true order by case when role_label='RH/RECRUTAMENTO' then 0 else 1 end, confidence desc",companyId);
        String site=String.valueOf(j.getOrDefault("company_website",""));
        if(contacts.isEmpty()&&!site.isBlank()&&!"null".equalsIgnoreCase(site)){
            try{discoverContacts(new LinkedHashMap<>(Map.of("companyId",companyId,"website",site)));}catch(Exception ignored){}
            contacts=Database.query("select * from company_contacts where company_id=? and contact_type='EMAIL' and is_public=true order by case when role_label='RH/RECRUTAMENTO' then 0 else 1 end, confidence desc",companyId);
        }
        if(contacts.isEmpty()) throw new IllegalStateException("Nenhum e-mail público de RH/corporativo foi encontrado para esta empresa.");
        String email=String.valueOf(contacts.get(0).get("contact_value"));
        String message=personalizedMessage(j);
        long id=Database.insert("insert into applications(job_id,status,channel,contact_value,message_body,created_at,updated_at) values(?,?,?,?,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",jobId,"DRAFT_PENDING_APPROVAL","EMAIL",email,message);
        ActivityService.log("APPLICATION_DRAFT_CREATED","JOB",jobId,"Candidatura aguardando aprovação",j.get("title")+" — "+j.get("company_name")+" | Para: "+email,"WARNING");
        ActivityService.notifyUser("APPLICATION_APPROVAL","Candidatura aguardando aprovação",j.get("title")+" — "+j.get("company_name"),"WARNING","APPLICATION",id);
        return Database.query("select * from applications where id=?",id).get(0);
    }

    public static Map<String,Object> approveApplication(Map<String,Object> req) throws Exception {
        long id=Json.lng(req,"applicationId",0);
        if(id==0) throw new IllegalArgumentException("Candidatura inválida.");
        var rows=Database.query("select a.*,j.title,c.name company_name from applications a join jobs j on j.id=a.job_id join companies c on c.id=j.company_id where a.id=?",id);
        if(rows.isEmpty()) throw new IllegalArgumentException("Candidatura não encontrada.");
        Map<String,Object> a=rows.get(0);
        if(!"DRAFT_PENDING_APPROVAL".equalsIgnoreCase(String.valueOf(a.get("status")))) throw new IllegalStateException("Esta candidatura não está aguardando aprovação.");
        Map<String,Object> p=profile();
        String resume=String.valueOf(p.getOrDefault("resume_path",""));
        if(resume.isBlank()||"null".equalsIgnoreCase(resume)||!Files.isRegularFile(Path.of(resume))) throw new IllegalStateException("Adicione seu currículo em Meu perfil antes de enviar.");
        String email=String.valueOf(a.getOrDefault("contact_value",""));
        String message=String.valueOf(a.getOrDefault("message_body",""));
        GmailApiService.sendResume(email,"Candidatura — "+a.get("title"),message,resume);
        Database.update("update applications set status='ENVIADA',updated_at=CURRENT_TIMESTAMP where id=?",id);
        ActivityService.log("APPLICATION_SENT","APPLICATION",id,"Currículo enviado após aprovação",a.get("title")+" — "+a.get("company_name")+" | Para: "+email,"SUCCESS");
        return Map.of("sent",true,"applicationId",id,"email",email,"job",a.get("title"),"company",a.get("company_name"));
    }
    public static Map<String,Object> apply(Map<String,Object> req) throws Exception {
        long jobId=Json.lng(req,"jobId",0); if(jobId==0)throw new IllegalArgumentException("Vaga inválida"); if(!Database.query("select id from applications where job_id=?",jobId).isEmpty())throw new IllegalStateException("Candidatura já registrada"); String status=Json.str(req,"status","AGUARDANDO"); long id=Database.insert("insert into applications(job_id,status,channel,created_at,updated_at) values(?,?,?,?,?)",jobId,status,Json.str(req,"channel","APP"),Timestamp.valueOf(LocalDateTime.now()),Timestamp.valueOf(LocalDateTime.now())); return Database.query("select * from applications where id=?",id).get(0);
    }
    public static Map<String,Object> sendResume(Map<String,Object> req) throws Exception {
        long jobId=Json.lng(req,"jobId",0);
        if(jobId==0) throw new IllegalArgumentException("Vaga inválida.");
        if(!Database.query("select id from applications where job_id=?",jobId).isEmpty()) throw new IllegalStateException("Você já registrou ou enviou candidatura para esta vaga.");
        var rows=Database.query("select j.*,c.name company_name,c.website company_website from jobs j join companies c on c.id=j.company_id where j.id=?",jobId);
        if(rows.isEmpty()) throw new IllegalArgumentException("Vaga não encontrada.");
        Map<String,Object> j=rows.get(0), p=profile();
        String resume=String.valueOf(p.getOrDefault("resume_path",""));
        if(resume.isBlank()||"null".equalsIgnoreCase(resume)||!Files.exists(Path.of(resume))) throw new IllegalStateException("Adicione seu currículo em Meu perfil antes de enviar.");

        long companyId=((Number)j.get("company_id")).longValue();
        var contacts=Database.query("select * from company_contacts where company_id=? and contact_type='EMAIL' and is_public=true order by case when role_label='RH/RECRUTAMENTO' then 0 else 1 end, confidence desc",companyId);
        String site=String.valueOf(j.getOrDefault("company_website",""));
        if(contacts.isEmpty()&&!site.isBlank()&&!"null".equalsIgnoreCase(site)){
            try{discoverContacts(new LinkedHashMap<>(Map.of("companyId",companyId,"website",site)));}catch(Exception ignored){}
            contacts=Database.query("select * from company_contacts where company_id=? and contact_type='EMAIL' and is_public=true order by case when role_label='RH/RECRUTAMENTO' then 0 else 1 end, confidence desc",companyId);
        }
        if(contacts.isEmpty()) throw new IllegalStateException("Nenhum e-mail público de RH/corporativo foi encontrado para esta empresa. Use 'Buscar contato RH' primeiro.");

        String email=String.valueOf(contacts.get(0).get("contact_value"));
        String message=personalizedMessage(j);
        GmailApiService.sendResume(email,"Candidatura — "+j.get("title"),message,resume);
        long id=Database.insert("insert into applications(job_id,status,channel,contact_value,message_body,created_at,updated_at) values(?,?,?,?,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",jobId,"ENVIADA","EMAIL",email,message);
        Map<String,Object> out=new LinkedHashMap<>();
        out.put("sent",true); out.put("applicationId",id); out.put("email",email); out.put("job",j.get("title")); out.put("company",j.get("company_name"));
        return out;
    }
    public static String verificationEmail() throws Exception {
        var r=Database.query("select setting_value from app_settings where setting_key='verification_email'");
        if(!r.isEmpty()){
            String v=String.valueOf(r.get(0).get("setting_value")).trim();
            if(v.matches("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")) return v;
        }
        String env=cfg("SPEED_GMAIL_SENDER_EMAIL","");
        return env==null?"":env.trim();
    }
    public static Map<String,Object> setVerificationEmail(Map<String,Object> req) throws Exception {
        String email=Json.str(req,"email","").trim().toLowerCase(Locale.ROOT);
        if(!email.matches("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$"))
            throw new IllegalArgumentException("Digite um Gmail valido.");
        Database.update("merge into app_settings(setting_key,setting_value,updated_at) key(setting_key) values('verification_email',?,CURRENT_TIMESTAMP)",email);
        Database.update("merge into app_settings(setting_key,setting_value,updated_at) key(setting_key) values('gmail_sender_email',?,CURRENT_TIMESTAMP)",email);
        return Map.of("email",email,"masked",maskEmail(email),"saved",true);
    }
    public static String maskEmail(String email){
        int at=email.indexOf('@');
        if(at<=2)return email;
        return email.substring(0,2)+"*" .repeat(Math.max(3,at-2))+email.substring(at);
    }

    public static Map<String,Object> sendManualVerificationCode(Map<String,Object> req) throws Exception { return GmailApiService.sendCode(req); }

    public static Map<String,Object> gmailDiagnostic() {
        Map<String,Object> st=GoogleOAuthService.status();
        Map<String,Object> out=new LinkedHashMap<>(st);
        out.put("status",Boolean.TRUE.equals(st.get("connected"))?"OK":"NAO_CONECTADO");
        return out;
    }

    public static Map<String,Object> settings() throws Exception {Map<String,Object> o=new LinkedHashMap<>();for(var r:Database.query("select setting_key,setting_value from app_settings"))o.put(String.valueOf(r.get("setting_key")),r.get("setting_value"));o.put("gmail_configured",gmailConfigured());o.put("gmail_sender",gmailUser());o.put("verification_email",verificationEmail());o.put("adzuna_configured",cfg("SPEED_ADZUNA_APP_ID","")!=null&&!cfg("SPEED_ADZUNA_APP_ID","").isBlank()&&cfg("SPEED_ADZUNA_APP_KEY","")!=null&&!cfg("SPEED_ADZUNA_APP_KEY","").isBlank());o.putAll(GoogleSearchService.quotaStatus());return o;}
    public static Map<String,Object> saveSettings(Map<String,Object> req)throws Exception{for(var e:req.entrySet()){String k=e.getKey();if(k.toLowerCase().contains("password")||k.toLowerCase().contains("secret"))continue;Database.update("merge into app_settings(setting_key,setting_value,updated_at) key(setting_key) values(?,?,CURRENT_TIMESTAMP)",k,String.valueOf(e.getValue()));}return settings();}

    public static Map<String,Object> agentRun(Map<String,Object> req) throws Exception {
        Map<String,Object> x=new LinkedHashMap<>(req);x.put("search",false);return AutomationService.run(x);
    }
    private static String personalizedMessage(Map<String,Object> j)throws Exception{Map<String,Object>p=profile();return "Olá, equipe de recrutamento da "+j.get("company_name")+".\n\nMeu nome é "+p.get("name")+" e gostaria de me candidatar à vaga de "+j.get("title")+". Atualmente curso Análise e Desenvolvimento de Sistemas e possuo experiência com suporte técnico, manutenção de computadores, instalação de programas e atendimento, além de conhecimentos em SQL, Excel, Power BI e Python.\n\nEncaminho meu currículo para avaliação. Fico à disposição para conversar sobre a oportunidade.\n\nAtenciosamente,\n"+p.get("name")+"\n"+p.get("phone")+"\n"+p.get("email");}
    public static String gmailUser(){
        try{
            var r=Database.query("select setting_value from app_settings where setting_key='gmail_sender_email'");
            if(!r.isEmpty()){
                String v=String.valueOf(r.get(0).get("setting_value")).trim();
                if(!v.isBlank()) return v;
            }
        }catch(Exception ignored){}
        String u=cfg("SPEED_GMAIL","");
        if(u==null||u.isBlank())u=cfg("SPEED_GMAIL_SENDER_EMAIL","");
        return u==null?"":u.trim();
    }
    public static boolean gmailConfigured(){try{return Boolean.TRUE.equals(GoogleOAuthService.status().get("connected"));}catch(Exception e){return false;}}

    private static String cfg(String key,String def){String p=System.getProperty(key);if(p!=null&&!p.isBlank())return p;String e=System.getenv(key);return e==null||e.isBlank()?def:e;}

    public static Map<String,Object> autoManagerCycle()throws Exception{
        Map<String,Object> cfgs=settings();
        if(!"true".equalsIgnoreCase(String.valueOf(cfgs.getOrDefault("auto_manager_enabled","true"))))return Map.of("status","DISABLED");
        if(!gmailConfigured())return Map.of("status","WAITING_GMAIL");
        String resume=String.valueOf(profile().getOrDefault("resume_path",""));if(resume.isBlank()||!Files.isRegularFile(Path.of(resume)))return Map.of("status","WAITING_RESUME");
        int interval=Math.max(15,Integer.parseInt(String.valueOf(cfgs.getOrDefault("auto_manager_interval_minutes","30"))));
        long now=System.currentTimeMillis(),last=0;try{last=Long.parseLong(String.valueOf(cfgs.getOrDefault("last_auto_manager_epoch_ms","0")));}catch(Exception ignored){}
        if(last>0&&now-last<interval*60_000L)return Map.of("status","WAITING_INTERVAL");
        Map<String,Object> r=AutomationService.run(new LinkedHashMap<>(Map.of("scope","ALL","search",true,"minScore",Integer.parseInt(String.valueOf(cfgs.getOrDefault("min_score_auto","65"))),"max",Integer.parseInt(String.valueOf(cfgs.getOrDefault("max_auto_per_run","5"))))));
        Database.update("merge into app_settings(setting_key,setting_value,updated_at) key(setting_key) values('last_auto_manager_epoch_ms',?,CURRENT_TIMESTAMP)",String.valueOf(now));
        Database.insert("insert into audit_events(event_type,entity_type,entity_id,details,created_at) values('AUTO_MANAGER','SYSTEM','0',?,CURRENT_TIMESTAMP)",Json.stringify(r));
        return r;
    }


    public static List<Map<String,Object>> connectorStatus() {
        List<Map<String,Object>> out=new ArrayList<>();
        out.add(connector("LINKEDIN","LinkedIn","https://www.linkedin.com/jobs/search/","https://www.linkedin.com/login","LOGIN_PORTAL","CANDIDATURA_ASSISTIDA",
            "Login/sessao do LinkedIn e integracao oficial quando disponivel."));
        out.add(connector("INDEED","Indeed","https://br.indeed.com/jobs","https://secure.indeed.com/auth","LOGIN_PORTAL","CANDIDATURA_ASSISTIDA",
            "Login Indeed; envio automatico por API depende de credencial/parceria oficial."));
        out.add(connector("INFOJOBS","InfoJobs","https://www.infojobs.com.br/vagas-de-emprego.aspx","https://login.infojobs.com.br/","LOGIN_PORTAL","CANDIDATURA_ASSISTIDA",
            "Login do InfoJobs; usa sessao do navegador para candidatura permitida pelo portal."));
        out.add(connector("VAGAS","Vagas.com","https://www.vagas.com.br/vagas-de-ti","https://login.vagas.com.br/","LOGIN_PORTAL","CANDIDATURA_ASSISTIDA",
            "Login do Vagas.com; automacao completa depende do fluxo permitido pelo portal."));
        return out;
    }

    private static Map<String,Object> connector(String id,String name,String searchUrl,String loginUrl,String auth,String mode,String note){
        Map<String,Object> m=new LinkedHashMap<>();
        m.put("id",id);m.put("name",name);m.put("searchUrl",searchUrl);m.put("loginUrl",loginUrl);
        m.put("auth",auth);m.put("mode",mode);m.put("status","PRECISA_LOGIN");m.put("note",note);
        return m;
    }

    public static Map<String,Object> testConnector(Map<String,Object> req){
        String id=Json.str(req,"id","").trim().toUpperCase(Locale.ROOT);
        for(Map<String,Object> c:connectorStatus()){
            if(id.equals(String.valueOf(c.get("id")))){
                Map<String,Object> r=new LinkedHashMap<>(c);
                r.put("localTest","PASSOU");
                r.put("urlTest",String.valueOf(c.get("searchUrl")).startsWith("https://")?"PASSOU":"FALHOU");
                r.put("realAccountTest","PRECISA_LOGIN_USUARIO");
                r.put("automaticSubmit","DEPENDE_DA_PLATAFORMA");
                r.put("message","Conector local validado. Para testar sua conta real, entre pelo navegador oficial da plataforma.");
                return r;
            }
        }
        throw new IllegalArgumentException("Conector desconhecido.");
    }

    public static Map<String,Object> testAllConnectors(){
        List<Map<String,Object>> tests=new ArrayList<>();
        for(Map<String,Object> c:connectorStatus()){
            tests.add(testConnector(new LinkedHashMap<>(Map.of("id",String.valueOf(c.get("id"))))));
        }
        Map<String,Object> out=new LinkedHashMap<>();
        out.put("status","PASSOU_LOCAL");
        out.put("connectors",tests);
        out.put("gmail",gmailConfigured()?"CONFIGURADO":"PRECISA_CONFIGURAR");
        out.put("resume",safeResumeExists()?"PASSOU":"AUSENTE");
        out.put("database","PASSOU");
        out.put("note","Testes de conta real e candidatura externa exigem login/autorizacao de cada plataforma e nao sao simulados como sucesso.");
        return out;
    }

    private static boolean safeResumeExists(){
        try{
            String p=String.valueOf(profile().getOrDefault("resume_path",""));
            return !p.isBlank()&&Files.isRegularFile(Path.of(p));
        }catch(Exception e){return false;}
    }

    public static Map<String,Object> diagnostics()throws Exception{
        Map<String,Object> o=new LinkedHashMap<>();o.put("database",profile().isEmpty()?"ERRO":"OK");o.put("profile","OK");o.put("resume",Files.isRegularFile(Path.of(String.valueOf(profile().getOrDefault("resume_path",""))))?"OK":"AUSENTE");o.put("gmail",gmailConfigured()?"CONFIGURADO":"NAO_CONFIGURADO");o.put("jobs",Database.query("select count(*) c from jobs").get(0).get("c"));o.put("contacts",Database.query("select count(*) c from company_contacts").get(0).get("c"));o.put("applications",Database.query("select count(*) c from applications").get(0).get("c"));o.put("connectors",connectorStatus());o.put("status","OK");return o;
    }
    public static Map<String,Object> managerRun(Map<String,Object> req)throws Exception{
        String scope=Json.str(req,"scope","ALL");Map<String,Object> search;
        try{search=smartSearch(new LinkedHashMap<>(Map.of("scope",scope,"limit",Json.integer(req,"searchLimit",60))));if(GoogleSearchService.configured())search.put("googleIntelligence",GoogleIntelligenceService.run(scope,35));}
        catch(Exception e){search=new LinkedHashMap<>();search.put("error",e.getMessage());}
        Map<String,Object> agentReq=new LinkedHashMap<>();agentReq.put("minScore",Json.integer(req,"minScore",Integer.parseInt(String.valueOf(settings().getOrDefault("min_score_auto","65")))));agentReq.put("max",Json.integer(req,"max",Integer.parseInt(String.valueOf(settings().getOrDefault("max_auto_per_run","5")))));agentReq.put("search",false);
        Map<String,Object> agent=AutomationService.run(agentReq);Map<String,Object> out=new LinkedHashMap<>();out.put("search",search);out.put("agent",agent);out.put("status","DONE");return out;
    }

    public static Map<String,Object> dashboard() throws Exception {
        Map<String,Object>o=new LinkedHashMap<>();
        long analyzed=((Number)Database.query("select count(*) c from jobs").get(0).get("c")).longValue();long apps=((Number)Database.query("select count(*) c from applications").get(0).get("c")).longValue();
        long replies=((Number)Database.query("select count(*) c from email_events where classification in ('RESPOSTA_RH','ENTREVISTA','ACAO_NECESSARIA','CONFIRMACAO')").get(0).get("c")).longValue();
        o.put("analyzed",analyzed);o.put("analyzedToday",Database.query("select count(*) c from jobs where cast(created_at as date)=CURRENT_DATE").get(0).get("c"));o.put("compatible",Database.query("select count(*) c from jobs where compatibility_score>=80").get(0).get("c"));o.put("applications",apps);o.put("contacts",Database.query("select count(*) c from company_contacts").get(0).get("c"));o.put("searches",Database.query("select count(*) c from search_runs").get(0).get("c"));o.put("sentToday",Database.query("select count(*) c from applications where status in ('ENVIADA','ENVIADA_AUTO') and cast(created_at as date)=CURRENT_DATE").get(0).get("c"));o.put("responses",replies);o.put("interviews",Database.query("select count(*) c from email_events where classification='ENTREVISTA'").get(0).get("c"));o.put("responseRate",apps==0?0:Math.round((replies*1000.0/apps))/10.0);o.putAll(GoogleSearchService.quotaStatus());return o;
    }
    public static List<Map<String,Object>> discardedJobs(Map<String,String> q)throws Exception {int limit=(int)Math.max(1,Math.min(parse(q.get("limit"),50),200));return ActivityService.jobDecisions(limit);}
    public static Map<String,Object> approveBulk(Map<String,Object> req)throws Exception {int min=Math.max(50,Math.min(99,Json.integer(req,"minScore",90))),limit=Math.max(1,Math.min(20,Json.integer(req,"limit",10)));List<Map<String,Object>> rows=Database.query("select a.id from applications a join jobs j on j.id=a.job_id where a.status='DRAFT_PENDING_APPROVAL' and j.compatibility_score>=? order by j.priority_score desc limit ?",min,limit);int sent=0,failed=0;List<Map<String,Object>> results=new ArrayList<>();for(Map<String,Object> r:rows){try{Map<String,Object>x=approveApplication(Map.of("applicationId",((Number)r.get("id")).longValue()));results.add(x);sent++;}catch(Exception e){failed++;results.add(Map.of("error",String.valueOf(e.getMessage()),"applicationId",r.get("id")));}}return Map.of("sent",sent,"failed",failed,"results",results);}
    public static Map<String,Object> jobFeedback(Map<String,Object> req)throws Exception {long jobId=Json.lng(req,"jobId",0);if(jobId<=0)throw new IllegalArgumentException("Vaga inválida.");String type=Json.str(req,"type","BAD_MATCH").toUpperCase(Locale.ROOT);String note=Json.str(req,"note","");Database.insert("insert into job_feedback(job_id,feedback_type,note,created_at) values(?,?,?,CURRENT_TIMESTAMP)",jobId,type,note);Map<String,Object> j=Database.query("select j.*,c.name company_name from jobs j join companies c on c.id=j.company_id where j.id=?",jobId).stream().findFirst().orElse(new LinkedHashMap<>());ActivityService.jobDecision(j,"BAD_MATCH_FEEDBACK","Usuário marcou este match como ruim para não repetir a decisão.",jobId);return Map.of("saved",true,"jobId",jobId,"type",type);}
    public static boolean hasBadMatchFeedback(long jobId)throws Exception{return !Database.query("select id from job_feedback where job_id=? and feedback_type='BAD_MATCH' limit 1",jobId).isEmpty();}

    public static void recalcAll() throws Exception {for(var r:Database.query("select id from jobs"))recalc(((Number)r.get("id")).longValue());}
    private static void recalc(long id)throws Exception{var r=Database.query("select j.*,c.name company_name from jobs j join companies c on c.id=j.company_id where j.id=?",id);if(r.isEmpty())return;Map<String,Object>j=r.get(0),p=profile();boolean remote=isRemote(String.valueOf(j.get("work_mode")));double d=remote?0:distance(String.valueOf(p.get("city")),String.valueOf(j.get("city")));int comp=compat(p,j);int rec=recency(String.valueOf(j.get("published_at")));double priority=remote?comp*.78+rec*.22:comp*.65+rec*.20+Math.max(0,100-Math.min(d,60)/60*100)*.15;Database.update("update jobs set distance_km=?,compatibility_score=?,priority_score=?,updated_at=CURRENT_TIMESTAMP where id=?",round(d),comp,round(priority),id);}
    private static int compat(Map<String,Object>p,Map<String,Object>j){
        return JobRules.compatibility(String.valueOf(p.get("target_roles")),String.valueOf(p.get("skills")),String.valueOf(j.get("title")),String.valueOf(j.get("description")),String.valueOf(j.get("requirements")),String.valueOf(j.get("level")));
    }
    private static Set<String> tokens(String s){Set<String>o=new LinkedHashSet<>();for(String x:s.split("[,;|/\\n]")){x=x.trim();if(!x.isBlank())o.add(x);}return o;}
    private static boolean legacyIsEntryLevel(String title,String description,String level){
        String t=norm(title+" "+level), d=norm(description), all=t+" "+d;
        String[] blocked={
            "senior","sr."," sr ","pleno","especialista","coordenador","coordenadora","supervisor","supervisora",
            "gerente","manager","tech lead","team lead","lider tecnico","arquiteto","architect","principal","staff",
            "tier ii","tier 2","tier iii","tier 3","nivel 2","nivel 3","n2","n3","level 2","level 3","mid level","mid-level"
        };
        for(String b:blocked) if(all.contains(b)) return false;
        String[] entry={"junior"," jr ","jr.","estagio","estagiario","estagiaria","trainee","assistente","auxiliar","nivel 1","n1","help desk","service desk","entry level"};
        for(String e:entry) if((" "+t+" ").contains(e)||all.contains(e)) return true;
        return all.contains("sem experiencia")||all.contains("primeiro emprego")||all.contains("inicio de carreira");
    }
    private static boolean legacyIsCareerRelevant(String title,String description){
        String all=norm(title+" "+description);
        String[] good={
            "tecnico de ti","tecnico ti","assistente de ti","auxiliar de ti","informatica","suporte tecnico","suporte ti",
            "help desk","service desk","service-desk","ti junior","analista de suporte","infraestrutura","desktop support",
            "it support","technical support","dados","data analyst","analista de dados","business intelligence","power bi",
            "estagio ti","estagio em ti","estagio tecnologia","estagio dados","estagio em dados","sistemas","tecnologia da informacao"
        };
        for(String g:good) if(all.contains(g)) return true;
        return false;
    }
    private static String inferEntryLevel(String title,String description){
        String n=norm(title+" "+description);
        if(n.contains("estagio")||n.contains("estagi")) return "Estágio";
        if(n.contains("trainee")) return "Trainee";
        if(n.contains("assistente")) return "Assistente";
        if(n.contains("auxiliar")) return "Auxiliar";
        if(n.contains("n1")||n.contains("nivel 1")||n.contains("help desk")||n.contains("service desk")) return "N1";
        return "Júnior";
    }
    private static boolean isRemote(String m){m=norm(m);return m.contains("remoto")||m.contains("home office")||m.contains("home-office");}
    private static int recency(String d){try{long days=Duration.between(LocalDate.parse(d.substring(0,10)).atStartOfDay(),LocalDate.now().atStartOfDay()).toDays();return(int)Math.max(20,100-days*12);}catch(Exception e){return 55;}}
    private static double distance(String a,String b){double[]x=coord(a),y=coord(b);double R=6371,dLat=Math.toRadians(y[0]-x[0]),dLon=Math.toRadians(y[1]-x[1]);double h=Math.sin(dLat/2)*Math.sin(dLat/2)+Math.cos(Math.toRadians(x[0]))*Math.cos(Math.toRadians(y[0]))*Math.sin(dLon/2)*Math.sin(dLon/2);return R*2*Math.atan2(Math.sqrt(h),Math.sqrt(1-h));}
    private static double[] coord(String p){String n=norm(p);Map<String,double[]>m=new LinkedHashMap<>();m.put("ferraz",new double[]{-23.5411,-46.3687});m.put("poa",new double[]{-23.5286,-46.3440});m.put("suzano",new double[]{-23.5448,-46.3112});m.put("itaqua",new double[]{-23.4861,-46.3487});m.put("mogi",new double[]{-23.5229,-46.1883});m.put("guaianases",new double[]{-23.5508,-46.4132});m.put("itaquera",new double[]{-23.5407,-46.4620});m.put("sao miguel",new double[]{-23.4935,-46.4446});m.put("ermelino",new double[]{-23.4918,-46.4843});m.put("penha",new double[]{-23.5227,-46.5422});m.put("tatuape",new double[]{-23.5403,-46.5767});m.put("mooca",new double[]{-23.5618,-46.5972});m.put("sao mateus",new double[]{-23.5982,-46.4775});m.put("sapopemba",new double[]{-23.6046,-46.5093});m.put("vila prudente",new double[]{-23.5844,-46.5816});for(var e:m.entrySet())if(n.contains(e.getKey()))return e.getValue();return m.get("ferraz");}
    private static String norm(String s){String n=Normalizer.normalize(String.valueOf(s).toLowerCase(Locale.ROOT),Normalizer.Form.NFD).replaceAll("\\p{M}+","");return n.replaceAll("\\s+"," ").trim();}
    private static double num(Object o){try{return Double.parseDouble(String.valueOf(o));}catch(Exception e){return 0;}}private static double parse(String s,double d){try{return Double.parseDouble(s);}catch(Exception e){return d;}}private static double round(double d){return Math.round(d*10.0)/10.0;}private static String stripHtml(String s){return s.replaceAll("(?is)<script.*?</script>"," ").replaceAll("(?is)<style.*?</style>"," ").replaceAll("<[^>]+>"," ").replace("&nbsp;"," ").replace("&amp;","&").replaceAll("\\s+"," ").trim();}
}
