package com.speedvagas;

import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.util.*;

public final class GmailApiService {
    private static final HttpClient HTTP=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(12)).build();
    private static String api(){String p=System.getProperty("speed.gmail.api.base");return p==null||p.isBlank()?"https://gmail.googleapis.com/gmail/v1/users/me":p;}
    private GmailApiService(){}

    public static Map<String,Object> sendCode(Map<String,Object> req)throws Exception{
        String email=Json.str(req,"email","").trim();
        if(email.isBlank())email=String.valueOf(GoogleOAuthService.status().getOrDefault("email",""));
        if(email.isBlank())throw new IllegalArgumentException("E-mail de destino ausente.");
        String code=String.format(Locale.ROOT,"%06d",new java.security.SecureRandom().nextInt(1_000_000));
        String body="Seu código de teste do SPEED VAGAS é: "+code+"\n\nSe você não solicitou, ignore esta mensagem.";
        sendMessage(email,"SPEED VAGAS — Código de teste",body,null,null);
        return Map.of("sent",true,"email",email,"expiresMinutes",10);
    }

    public static Map<String,Object> sendResume(String to,String subject,String body,String resumePath)throws Exception{
        if(to==null||to.isBlank())throw new IllegalArgumentException("E-mail de destino ausente.");
        Path attachment=(resumePath==null||resumePath.isBlank())?null:Path.of(resumePath);
        if(attachment!=null&&!Files.isRegularFile(attachment))throw new IllegalStateException("Currículo não encontrado.");
        String id=sendMessage(to,subject,body,attachment,"application/pdf");
        return Map.of("sent",true,"email",to,"messageId",id);
    }

    public static Map<String,Object> checkInbox(Map<String,Object> req)throws Exception{
        int max=Math.max(5,Math.min(Json.integer(req,"max",30),50));
        ExternalRateLimiter.acquire("gmail-read", Duration.ofMillis(300));
        String token=GoogleOAuthService.accessToken();
        String q=URLEncoder.encode("newer_than:30d",StandardCharsets.UTF_8);
        Map<String,Object> list=get(api()+"/messages?maxResults="+max+"&q="+q,token);
        Object msgs=list.get("messages");
        int checked=0,newItems=0,interviews=0,actions=0,rejected=0;
        if(msgs instanceof List<?> l){
            for(Object o:l){
                if(!(o instanceof Map<?,?> mm))continue;
                String id=String.valueOf(mm.get("id"));checked++;
                if(!Database.query("select id from email_events where external_id=?",id).isEmpty())continue;
                Map<String,Object> full=get(api()+"/messages/"+id+"?format=metadata&metadataHeaders=From&metadataHeaders=Subject&metadataHeaders=Date",token);
                Map<String,String> h=headers(full);
                String sub=h.getOrDefault("Subject",""),from=h.getOrDefault("From",""),date=h.getOrDefault("Date","");
                String cls=JobRules.classifyEmail(sub,"");
                long eid=Database.insert("insert into email_events(external_id,sender,subject,message_date,classification,status,created_at) values(?,?,?,?,?,'NEW',CURRENT_TIMESTAMP)",
                    id,from,sub,date,cls);
                newItems++;
                ActivityService.log("EMAIL_RECEBIDO","EMAIL",eid,"E-mail recebido: "+sub,"De: "+from+" | Classificação: "+cls,"OK");
                if("ENTREVISTA".equals(cls)){interviews++;ActivityService.notifyUser("INTERVIEW","Entrevista / contato do RH",sub+" — "+from,"CRITICAL","EMAIL",eid);}
                else if("ACAO_NECESSARIA".equals(cls)){actions++;ActivityService.notifyUser("ACTION_REQUIRED","Empresa precisa de uma ação sua",sub+" — "+from,"WARNING","EMAIL",eid);}
                else if("REPROVACAO".equals(cls))rejected++;
            }
        }
        return Map.of("checked",checked,"new",newItems,"interviews",interviews,"actions",actions,"rejected",rejected,"message","Gmail verificado pela API.");
    }

    private static String sendMessage(String to,String subject,String body,Path attachment,String mime)throws Exception{
        String raw=buildMime(to,subject,body,attachment,mime);
        String b64=Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
        String json=Json.stringify(Map.of("raw",b64));
        String token=GoogleOAuthService.accessToken();
        HttpRequest req=HttpRequest.newBuilder(URI.create(api()+"/messages/send"))
            .header("Authorization","Bearer "+token).header("Content-Type","application/json")
            .POST(HttpRequest.BodyPublishers.ofString(json)).build();
        ExternalRateLimiter.acquire("gmail-send", Duration.ofMillis(500));
        HttpResponse<String> r=HTTP.send(req,HttpResponse.BodyHandlers.ofString());
        if(r.statusCode()/100!=2){
            String bodyErr=r.body();
            if(r.statusCode()==403&&(bodyErr.contains("ACCESS_TOKEN_SCOPE_INSUFFICIENT")||bodyErr.contains("insufficientPermissions")))
                throw new IllegalStateException("O Google conectou, mas não liberou permissão para enviar e-mail. Vá em Configurações e clique em Reconectar permissões Google.");
            throw new IOException("Gmail API envio falhou: HTTP "+r.statusCode()+" "+bodyErr);
        }
        Map<String,Object> j=Json.obj(r.body());
        return String.valueOf(j.getOrDefault("id",""));
    }

    private static String buildMime(String to,String subject,String body,Path attachment,String mime)throws Exception{
        String CRLF="\r\n";
        if(attachment==null){
            return "To: "+to+CRLF+"Subject: "+subject+CRLF+"MIME-Version: 1.0"+CRLF+
                "Content-Type: text/plain; charset=UTF-8"+CRLF+"Content-Transfer-Encoding: 8bit"+CRLF+CRLF+body;
        }
        String boundary="speed_"+UUID.randomUUID().toString().replace("-","");
        String fileName=attachment.getFileName().toString();
        String data=Base64.getMimeEncoder(76,CRLF.getBytes(StandardCharsets.US_ASCII)).encodeToString(Files.readAllBytes(attachment));
        return "To: "+to+CRLF+"Subject: "+subject+CRLF+"MIME-Version: 1.0"+CRLF+
            "Content-Type: multipart/mixed; boundary=\""+boundary+"\""+CRLF+CRLF+
            "--"+boundary+CRLF+"Content-Type: text/plain; charset=UTF-8"+CRLF+"Content-Transfer-Encoding: 8bit"+CRLF+CRLF+body+CRLF+
            "--"+boundary+CRLF+"Content-Type: "+(mime==null?"application/octet-stream":mime)+"; name=\""+fileName+"\""+CRLF+
            "Content-Disposition: attachment; filename=\""+fileName+"\""+CRLF+"Content-Transfer-Encoding: base64"+CRLF+CRLF+data+CRLF+
            "--"+boundary+"--"+CRLF;
    }

    private static Map<String,Object> get(String url,String token)throws Exception{
        HttpRequest req=HttpRequest.newBuilder(URI.create(url)).header("Authorization","Bearer "+token).GET().build();
        ExternalRateLimiter.acquire("gmail-read", Duration.ofMillis(300));
        HttpResponse<String> r=HTTP.send(req,HttpResponse.BodyHandlers.ofString());
        if(r.statusCode()/100!=2){
            if(r.statusCode()==403&&(r.body().contains("ACCESS_TOKEN_SCOPE_INSUFFICIENT")||r.body().contains("insufficientPermissions")))
                throw new IllegalStateException("Falta permissão para ler o Gmail. Reconecte o Google em Configurações.");
            throw new IOException("Gmail API HTTP "+r.statusCode()+" "+r.body());
        }
        return Json.obj(r.body());
    }

    private static Map<String,String> headers(Map<String,Object> full){
        Map<String,String> out=new LinkedHashMap<>();
        Object payload=full.get("payload");
        if(payload instanceof Map<?,?> pm){
            Object hs=pm.get("headers");
            if(hs instanceof List<?> l)for(Object o:l)if(o instanceof Map<?,?> hm){
                out.put(String.valueOf(hm.get("name")),String.valueOf(hm.get("value")));
            }
        }
        return out;
    }
}
