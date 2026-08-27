package com.speedvagas;

import java.util.*;

/** Compatibilidade da camada antiga: leitura agora usa exclusivamente Gmail API/OAuth. */
public final class EmailAgent {
    private EmailAgent(){}
    public static Map<String,Object> checkInbox(Map<String,Object> req)throws Exception{return GmailApiService.checkInbox(req);}
    public static List<Map<String,Object>> events()throws Exception{return Database.query("select * from email_events order by id desc limit 100");}
    public static Map<String,Object> suggestedReply(Map<String,Object> req)throws Exception{
        long id=Json.lng(req,"emailEventId",0);var rows=Database.query("select * from email_events where id=?",id);if(rows.isEmpty())throw new IllegalArgumentException("E-mail não encontrado.");
        Map<String,Object> e=rows.get(0),p=Services.profile();String cls=String.valueOf(e.get("classification")),subject=String.valueOf(e.get("subject")),name=String.valueOf(p.getOrDefault("name",""));
        String body=switch(cls){
            case "ENTREVISTA"->"Olá! Agradeço o contato e o convite para a entrevista. Tenho interesse em continuar no processo seletivo. Poderia me informar as opções de data e horário e se a entrevista será online ou presencial? Obrigado!";
            case "ACAO_NECESSARIA"->"Olá! Obrigado pelo retorno e pela oportunidade. Recebi a solicitação e vou providenciar a etapa solicitada. Caso exista algum prazo ou orientação adicional, por favor me informe.";
            default->"Olá! Muito obrigado pelo retorno e pela oportunidade. Continuo interessado na vaga e fico à disposição para as próximas etapas do processo seletivo.";
        };
        if(!name.isBlank()&&!"null".equalsIgnoreCase(name))body+="\n\nAtenciosamente,\n"+name;
        return Map.of("emailEventId",id,"classification",cls,"subject","Re: "+subject,"draft",body,"autoSendRecommended",false);
    }
}
