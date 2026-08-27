package com.speedvagas;

import java.text.Normalizer;
import java.util.*;

public final class JobRules {
    private JobRules(){}

    public static String norm(String v){
        if(v==null)return "";
        String s=Normalizer.normalize(v,Normalizer.Form.NFD).replaceAll("\\p{M}","");
        return s.toLowerCase(Locale.ROOT).replaceAll("\\s+"," ").trim();
    }

    public static boolean isEntryLevel(String title,String description,String level){
        String t=" "+norm(title+" "+level)+" ";
        String all=t+" "+norm(description);
        String[] blocked={
            "senior"," sr "," sr.","pleno","especialista","coordenador","coordenadora","supervisor","supervisora",
            "gerente","manager","tech lead","team lead","lider tecnico","arquiteto","architect","principal","staff",
            "tier ii","tier 2","tier iii","tier 3","nivel 2","nivel 3"," n2 "," n3 ","level 2","level 3",
            "mid level","mid-level"
        };
        for(String b:blocked)if(all.contains(b))return false;

        // Bloqueia 3+ anos apenas quando a quantidade aparece em contexto de requisito de experiência.
        // Ex.: "empresa há 5 anos" NÃO deve reprovar a vaga.
        java.util.regex.Pattern expYears=java.util.regex.Pattern.compile(
            "(?:experiencia|experience|vivencia|requisit[oa]|required|requirement|necessari[oa]|minim[oa]|minimum|at least|pelo menos)"+
            ".{0,45}\\b([3-9]|[1-9][0-9])\\s*(?:\\+\\s*)?(?:anos|years?)\\b"+
            "|\\b([3-9]|[1-9][0-9])\\s*(?:\\+\\s*)?(?:anos\\s+de\\s+experiencia|years?\\s+(?:of\\s+)?experience)\\b",
            java.util.regex.Pattern.CASE_INSENSITIVE);
        if(expYears.matcher(all).find())return false;

        String[] entry={"junior"," jr ","jr.","estagio","estagiario","estagiaria","trainee","assistente","auxiliar",
            "nivel 1"," n1 ","help desk","service desk","entry level","aprendiz","sem experiencia",
            "primeiro emprego","inicio de carreira"};
        for(String e:entry)if(all.contains(e))return true;

        // Muitos anúncios de entrada não informam "Júnior". Se não há senioridade avançada
        // nem exigência contextual de 3+ anos, deixa passar para o ranking/perfil decidir.
        return true;
    }

    public static boolean isCareerRelevant(String title,String description){
        String all=norm(title+" "+description);
        String[] good={
            "tecnico de ti","tecnico ti","assistente de ti","auxiliar de ti","informatica","suporte tecnico","suporte ti",
            "help desk","service desk","service-desk","ti junior","analista de suporte","infraestrutura","desktop support",
            "it support","technical support","dados","data analyst","analista de dados","business intelligence","power bi",
            "estagio ti","estagio em ti","estagio tecnologia","estagio dados","estagio em dados","sistemas",
            "tecnologia da informacao","ads","sql","excel"
        };
        for(String g:good)if(all.contains(g))return true;
        return false;
    }

    /** Relevância dinâmica: usa os cargos e habilidades que o próprio usuário configurou. */
    public static boolean isRelevantToProfile(String targetRoles,String skills,String title,String description){
        String t=norm(title), all=norm(title+" "+description);
        boolean hasProfile=false;
        for(String x:String.valueOf(targetRoles==null?"":targetRoles).split("[,;\\n]")){
            String role=norm(x); if(role.isBlank())continue; hasProfile=true;
            if(t.contains(role)||role.contains(t))return true;
            int meaningful=0,hits=0;
            for(String w:role.split(" ")){
                if(w.length()<=2||Set.of("junior","jr","estagio","assistente","auxiliar").contains(w))continue;
                meaningful++; if(all.contains(w))hits++;
            }
            if(meaningful==1&&hits==1)return true;
            if(meaningful>=2&&hits>=2)return true;
        }
        int skillHits=0;
        for(String x:String.valueOf(skills==null?"":skills).split("[,;\\n]")){
            String k=norm(x); if(k.length()<=2)continue; hasProfile=true;
            if(all.contains(k) && ++skillHits>=1)return true;
        }
        // Compatibilidade com perfis antigos ainda sem cargo/habilidade preenchidos.
        return !hasProfile && isCareerRelevant(title,description);
    }

    public static boolean looksLikeInterview(String subject,String body){
        String all=norm(subject+" "+body);
        String[] k={"entrevista","agendar","agendamento","reuniao","meet","teams","video chamada","conversa com rh",
            "bate-papo","processo seletivo","horario disponivel","availability","interview"};
        for(String x:k)if(all.contains(x))return true;
        return false;
    }

    public static String classifyEmail(String subject,String body){
        String all=norm(subject+" "+body);
        if(looksLikeInterview(subject,body))return "ENTREVISTA";
        if(all.contains("infelizmente")||all.contains("nao seguiremos")||all.contains("nao daremos continuidade")||
           all.contains("outro candidato")||all.contains("not moving forward"))return "REPROVACAO";
        if(all.contains("teste")||all.contains("assessment")||all.contains("desafio")||all.contains("case")||
           all.contains("documento")||all.contains("preencher"))return "ACAO_NECESSARIA";
        if(all.contains("recebemos sua candidatura")||all.contains("candidatura recebida")||
           all.contains("application received"))return "CONFIRMACAO";
        return "RESPOSTA_RH";
    }

    public static int compatibility(String targetRoles,String skills,String title,String description,String requirements,String level){
        String t=norm(title), d=norm(description), req=norm(requirements), text=norm(title+" "+description+" "+requirements+" "+level);
        int roleScore=0;
        for(String x:String.valueOf(targetRoles).split("[,;\n]")){
            String n=norm(x);if(n.isBlank())continue;
            if(t.contains(n)||n.contains(t)){roleScore=Math.max(roleScore,40);continue;}
            int titleHits=0,bodyHits=0;
            for(String w:n.split(" ")){
                if(w.length()<=2||Set.of("junior","jr","estagio","assistente","auxiliar","de","da","do").contains(w))continue;
                if(t.contains(w))titleHits++;if(d.contains(w)||req.contains(w))bodyHits++;
            }
            if(titleHits>=2)roleScore=Math.max(roleScore,34);else if(titleHits==1)roleScore=Math.max(roleScore,22);else if(bodyHits>=2)roleScore=Math.max(roleScore,15);
        }
        int skillHits=0,titleSkillHits=0,frequencyBonus=0;
        for(String x:String.valueOf(skills).split("[,;\n]")){
            String k=norm(x);if(k.length()<=2)continue;
            if(text.contains(k)){
                skillHits++;if(t.contains(k))titleSkillHits++;
                int pos=0,count=0;while((pos=text.indexOf(k,pos))>=0&&count<4){count++;pos+=Math.max(1,k.length());}
                if(count>=2)frequencyBonus+=2;
            }
        }
        int combinationBonus=skillHits>=4?12:skillHits>=3?9:skillHits>=2?6:0;
        int ptBrBonus=(text.contains("junior")||text.contains("estagio")||text.contains("assistente")||text.contains("auxiliar")||text.contains("n1"))?5:0;
        int score=18+roleScore+(isRelevantToProfile(targetRoles,skills,title,description)?10:0)+(isEntryLevel(title,description,level)?8:-35)+Math.min(18,skillHits*3)+Math.min(8,titleSkillHits*4)+Math.min(6,frequencyBonus)+combinationBonus+ptBrBonus;
        return Math.max(10,Math.min(99,score));
    }

    public static double priority(int compat,double distanceKm,boolean remote,int ageDays){
        double dist=remote?100:Math.max(0,100-Math.min(distanceKm,60)/60.0*100);
        double rec=Math.max(0,100-Math.min(ageDays,30)/30.0*100);
        return Math.round((compat*.65+dist*.20+rec*.15)*100.0)/100.0;
    }
}
