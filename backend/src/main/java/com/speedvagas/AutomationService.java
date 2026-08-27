package com.speedvagas;

import java.util.*;

public final class AutomationService {
    private AutomationService(){}

    enum Decision { SKIP_DUPLICATE, SKIP_BAD_MATCH, AUTO_SEND, DRAFT, ACTION_REQUIRED, NO_CHANNEL }

    static Decision decideCandidate(boolean duplicate,boolean badMatch,boolean hasEmail,boolean hasPortal,int score,boolean autoSendEnabled,int autoMinScore,int autoRemaining){
        if(duplicate)return Decision.SKIP_DUPLICATE;
        if(badMatch)return Decision.SKIP_BAD_MATCH;
        if(hasEmail&&autoSendEnabled&&score>=autoMinScore&&autoRemaining>0)return Decision.AUTO_SEND;
        if(hasEmail)return Decision.DRAFT;
        if(hasPortal)return Decision.ACTION_REQUIRED;
        return Decision.NO_CHANNEL;
    }

    public static Map<String,Object> run(Map<String,Object> req)throws Exception{
        int min=Math.max(50,Math.min(95,Json.integer(req,"minScore",65)));
        int max=Math.max(1,Math.min(Json.integer(req,"max",10),30));
        boolean search=Json.bool(req,"search",true);String scope=Json.str(req,"scope","ALL");
        Map<String,Object> cfg=Services.settings();
        boolean autoSendEnabled="true".equalsIgnoreCase(String.valueOf(cfg.getOrDefault("auto_send_email","false")));
        Map<String,Object> profile=Services.profile();
        String resumePath=String.valueOf(profile.getOrDefault("resume_path",""));
        boolean autoSendReady=autoSendEnabled&&Services.gmailConfigured()&&!resumePath.isBlank()&&!"null".equalsIgnoreCase(resumePath);
        int autoMinScore=Math.max(80,Math.min(99,Integer.parseInt(String.valueOf(cfg.getOrDefault("auto_send_min_score","85")))));
        int autoDailyLimit=Math.max(1,Math.min(20,Integer.parseInt(String.valueOf(cfg.getOrDefault("auto_send_daily_limit","3")))));
        int autoSentToday=((Number)Database.query("select count(*) c from applications where status='ENVIADA_AUTO' and cast(created_at as date)=CURRENT_DATE").get(0).get("c")).intValue();
        int autoRemaining=Math.max(0,autoDailyLimit-autoSentToday);

        Map<String,Object> searchResult=new LinkedHashMap<>();
        if(search){
            ActivityService.log("SEARCH_STARTED","SYSTEM",null,"Busca automática iniciada","Escopo: "+scope,"RUNNING");
            try{
                searchResult=Services.smartSearch(new LinkedHashMap<>(Map.of("scope",scope,"limit",60)));
                if(GoogleSearchService.configured()){
                    try{searchResult.put("googleIntelligence",GoogleIntelligenceService.run(scope,35));}
                    catch(Exception ge){searchResult.put("googleError",ge.getMessage());ActivityService.log("GOOGLE_SEARCH_ERROR","SYSTEM",null,"Google Intelligence indisponível",ge.getMessage(),"WARNING");}
                }else searchResult.put("googleNotice","Google Intelligence não configurada; demais fontes continuam ativas.");
                ActivityService.log("SEARCH_FINISHED","SYSTEM",null,"Busca concluída","Novas: "+searchResult.getOrDefault("added",0)+" | Ignoradas: "+searchResult.getOrDefault("skipped",0),"OK");
            }catch(Exception e){searchResult.put("error",e.getMessage());ActivityService.log("SEARCH_ERROR","SYSTEM",null,"Falha em uma fonte de busca",e.getMessage(),"WARNING");}
        }

        var candidates=Database.query("select j.*,c.name company_name,c.website company_website from jobs j join companies c on c.id=j.company_id where j.compatibility_score>=? order by j.priority_score desc limit ?",min,Math.max(max*5,40));
        int sent=0,actionRequired=0,duplicate=0,skipped=0,errors=0,drafts=0;
        List<Map<String,Object>> results=new ArrayList<>();
        for(Map<String,Object> j:candidates){
            if(results.size()>=max)break;
            long jobId=((Number)j.get("id")).longValue();int score=((Number)j.getOrDefault("compatibility_score",0)).intValue();
            boolean dup=!Database.query("select id from applications where job_id=?",jobId).isEmpty();boolean bad=Services.hasBadMatchFeedback(jobId);
            String portal=String.valueOf(j.getOrDefault("url",""));boolean hasPortal=!portal.isBlank()&&!"null".equalsIgnoreCase(portal);
            var cs=Database.query("select contact_value from company_contacts where company_id=? and contact_type='EMAIL' and is_public=true order by case when role_label='RH/RECRUTAMENTO' then 0 else 1 end,confidence desc limit 1",j.get("company_id"));
            String email=cs.isEmpty()?"":String.valueOf(cs.get(0).get("contact_value"));
            if(email.isBlank()){
                String website=String.valueOf(j.getOrDefault("company_website",""));if(!website.isBlank()&&!"null".equalsIgnoreCase(website))try{Services.discoverContacts(new LinkedHashMap<>(Map.of("companyId",j.get("company_id"),"website",website)));}catch(Exception ignored){}
                cs=Database.query("select contact_value from company_contacts where company_id=? and contact_type='EMAIL' and is_public=true order by case when role_label='RH/RECRUTAMENTO' then 0 else 1 end,confidence desc limit 1",j.get("company_id"));email=cs.isEmpty()?"":String.valueOf(cs.get(0).get("contact_value"));
            }
            Decision decision=decideCandidate(dup,bad,!email.isBlank(),hasPortal,score,autoSendReady,autoMinScore,autoRemaining);
            if(decision==Decision.SKIP_DUPLICATE){duplicate++;continue;}if(decision==Decision.SKIP_BAD_MATCH){skipped++;continue;}
            Map<String,Object> row=new LinkedHashMap<>();row.put("jobId",jobId);row.put("job",j.get("title"));row.put("company",j.get("company_name"));row.put("score",score);
            try{
                switch(decision){
                    case AUTO_SEND -> {
                        Map<String,Object> draft=Services.prepareApplication(jobId);Map<String,Object> sentRow=Services.approveApplication(Map.of("applicationId",draft.get("id")));
                        Database.update("update applications set status='ENVIADA_AUTO',updated_at=CURRENT_TIMESTAMP where id=?",draft.get("id"));
                        sent++;autoRemaining--;row.put("status","ENVIADA_AUTO");row.put("applicationId",draft.get("id"));row.put("evidence",sentRow.get("email"));
                        ActivityService.notifyUser("AUTO_APPLICATION_SENT","Candidatura enviada automaticamente",j.get("title")+" — "+j.get("company_name")+" • score "+score,"INFO","APPLICATION",draft.get("id"));
                    }
                    case DRAFT -> {Map<String,Object> draft=Services.prepareApplication(jobId);drafts++;row.put("status","DRAFT_PENDING_APPROVAL");row.put("applicationId",draft.get("id"));row.put("evidence",draft.get("contact_value"));}
                    case ACTION_REQUIRED -> {long appId=Database.insert("insert into applications(job_id,status,channel,contact_value,message_body,created_at,updated_at) values(?,'ACAO_NECESSARIA','PORTAL',?,'Candidatura precisa ser concluída no portal.',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",jobId,portal);actionRequired++;row.put("status","ACAO_NECESSARIA");row.put("applicationId",appId);row.put("evidence",portal);ActivityService.notifyUser("APPLICATION_ACTION","Candidatura precisa de você",j.get("title")+" — "+j.get("company_name"),"WARNING","APPLICATION",appId);}
                    case NO_CHANNEL -> {errors++;row.put("status","SEM_CANAL");ActivityService.jobDecision(j,"NO_RH_CONTACT","Nenhum e-mail público de RH foi encontrado e a vaga não possui portal utilizável.",jobId);}
                    default -> {}
                }
            }catch(Exception e){errors++;row.put("status","ERRO");row.put("error",e.getMessage());ActivityService.log("AUTOMATION_JOB_ERROR","JOB",jobId,"Falha ao preparar candidatura",String.valueOf(e.getMessage()),"WARNING");}
            results.add(row);
        }
        Map<String,Object> out=new LinkedHashMap<>();out.put("search",searchResult);out.put("sent",sent);out.put("drafts",drafts);out.put("actionRequired",actionRequired);out.put("duplicates",duplicate);out.put("skipped",skipped);out.put("errors",errors);out.put("results",results);out.put("autoSendEnabled",autoSendEnabled);out.put("autoSendReady",autoSendReady);out.put("autoSendMinScore",autoMinScore);out.put("autoRemainingToday",autoRemaining);out.put("message","Automação concluída. Autoenvio só ocorre quando habilitado, acima do score mínimo alto e dentro do limite diário.");return out;
    }

    private static volatile long lastAutomation=0L,lastEmailCheck=0L;
    public static synchronized Map<String,Object> backgroundCycle()throws Exception{
        Map<String,Object> cfg=Services.settings();long now=System.currentTimeMillis();int autoMin=Math.max(60,Integer.parseInt(String.valueOf(cfg.getOrDefault("auto_manager_interval_minutes","60"))));int mailMin=Math.max(5,Integer.parseInt(String.valueOf(cfg.getOrDefault("email_monitor_interval_minutes","10"))));boolean auto="true".equalsIgnoreCase(String.valueOf(cfg.getOrDefault("auto_manager_enabled","true"))),mail="true".equalsIgnoreCase(String.valueOf(cfg.getOrDefault("email_monitor_enabled","true")));Map<String,Object> out=new LinkedHashMap<>();out.put("automation","WAITING");out.put("email","WAITING");
        if(auto&&(lastAutomation==0L||now-lastAutomation>=autoMin*60_000L)){int score=Integer.parseInt(String.valueOf(cfg.getOrDefault("min_score_auto","65"))),max=Integer.parseInt(String.valueOf(cfg.getOrDefault("max_auto_per_run","10")));try{out.put("automation",run(new LinkedHashMap<>(Map.of("scope","ALL","search",true,"minScore",score,"max",max))));}catch(Exception e){ActivityService.log("AUTO_ERROR","SYSTEM",null,"Erro na automação automática",e.getMessage(),"WARNING");out.put("automationError",e.getMessage());}lastAutomation=now;}
        if(mail&&Services.gmailConfigured()&&(lastEmailCheck==0L||now-lastEmailCheck>=mailMin*60_000L)){try{out.put("email",GmailApiService.checkInbox(new LinkedHashMap<>(Map.of("max",30))));}catch(Exception e){ActivityService.log("EMAIL_MONITOR_ERROR","SYSTEM",null,"Falha ao verificar respostas",e.getMessage(),"WARNING");out.put("emailError",e.getMessage());}lastEmailCheck=now;}return out;
    }
}
