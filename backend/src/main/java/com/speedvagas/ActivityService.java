package com.speedvagas;

import java.util.*;

public final class ActivityService {
    private ActivityService(){}

    public static void log(String type,String entityType,Object entityId,String title,String details,String status){
        try{Database.insert("insert into activity_events(event_type,entity_type,entity_id,title,details,status,created_at) values(?,?,?,?,?,?,CURRENT_TIMESTAMP)",type,entityType,entityId==null?null:String.valueOf(entityId),title,details,status);}catch(Exception e){System.err.println("[ACTIVITY] "+e.getMessage());}
    }

    public static void jobDecision(Map<String,Object> job,String reasonCode,String reasonMessage,Object jobId){
        try{
            String source=String.valueOf(job.getOrDefault("source","")),external=String.valueOf(job.getOrDefault("externalId",job.getOrDefault("external_id","")));
            String title=String.valueOf(job.getOrDefault("title","")),company=String.valueOf(job.getOrDefault("company",job.getOrDefault("company_name","")));
            Database.insert("insert into job_decisions(source,external_id,job_id,title,company_name,reason_code,reason_message,created_at) values(?,?,?,?,?,?,?,CURRENT_TIMESTAMP)",source,external,jobId,title,company,reasonCode,reasonMessage);
            log("JOB_DISCARDED","JOB",jobId,"Vaga descartada: "+reasonLabel(reasonCode),title+" — "+company+" | "+reasonMessage,"INFO");
        }catch(Exception e){System.err.println("[JOB_DECISION] "+e.getMessage());}
    }

    public static List<Map<String,Object>> jobDecisions(int limit)throws Exception{
        limit=Math.max(1,Math.min(limit,200));return Database.query("select * from job_decisions order by id desc limit ?",limit);
    }

    public static String reasonLabel(String code){return switch(String.valueOf(code)){
        case "SENIOR_LEVEL"->"nível acima do desejado";case "OUT_OF_PROFILE"->"fora do perfil";case "DUPLICATE"->"duplicada";case "NO_RH_CONTACT"->"sem contato de RH";case "BAD_MATCH_FEEDBACK"->"match marcado como ruim";default->String.valueOf(code).replace('_',' ').toLowerCase(Locale.ROOT);
    };}

    public static List<Map<String,Object>> recent(int limit)throws Exception{limit=Math.max(1,Math.min(limit,200));return Database.query("select * from activity_events order by id desc limit ?",limit);}
    public static List<Map<String,Object>> notifications()throws Exception{return Database.query("select * from notifications where resolved=false order by case severity when 'CRITICAL' then 0 when 'WARNING' then 1 else 2 end,id desc limit 100");}
    public static void notifyUser(String type,String title,String message,String severity,String refType,Object refId){try{Database.insert("insert into notifications(notification_type,title,message,severity,reference_type,reference_id,resolved,created_at) values(?,?,?,?,?,?,false,CURRENT_TIMESTAMP)",type,title,message,severity,refType,refId==null?null:String.valueOf(refId));}catch(Exception e){System.err.println("[NOTIFY] "+e.getMessage());}}
    public static Map<String,Object> resolveNotification(Map<String,Object> req)throws Exception{long id=Json.lng(req,"id",0);if(id<=0)throw new IllegalArgumentException("Notificação inválida.");Database.update("update notifications set resolved=true,resolved_at=CURRENT_TIMESTAMP where id=?",id);return Map.of("resolved",true,"id",id);}
}
