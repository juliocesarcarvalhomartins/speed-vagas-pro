package com.speedvagas;

import java.time.LocalDate;
import java.util.*;

/** Executa no backend a mesma inteligência que antes existia apenas no clique do frontend. */
public final class GoogleIntelligenceService {
    private GoogleIntelligenceService(){}

    public static Map<String,Object> run(String scope,int saveLimit)throws Exception{
        Map<String,Object> profile=Services.profile();
        List<Map<String,String>> queries=buildQueries(profile,scope);
        int raw=0,filtered=0,saved=0,duplicates=0,errors=0;
        List<Map<String,Object>> health=new ArrayList<>();
        Set<String> seen=new HashSet<>();
        for(Map<String,String> item:queries){
            if(saved>=saveLimit)break;
            Map<String,Object> h=new LinkedHashMap<>();h.put("source",item.get("source"));
            try{
                Map<String,Object> r=GoogleSearchService.search(new LinkedHashMap<>(Map.of("q",item.get("q"),"start",1,"num",10)));
                Object oi=r.get("items");int count=0;
                if(oi instanceof List<?> list)for(Object o:list)if(o instanceof Map<?,?> mm){
                    count++;raw++;
                    Map<String,Object> row=new LinkedHashMap<>();for(var e:mm.entrySet())row.put(String.valueOf(e.getKey()),e.getValue());
                    String link=String.valueOf(row.getOrDefault("link",""));if(link.isBlank()||!seen.add(JobRules.norm(link)))continue;
                    String title=PublicJobSources.strip(String.valueOf(row.getOrDefault("title","")));
                    String snippet=PublicJobSources.strip(String.valueOf(row.getOrDefault("snippet","")));
                    String company=inferCompany(title,item.get("source"));
                    String source="GOOGLE_"+item.get("source").toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]","_");
                    boolean remote=JobRules.norm(title+" "+snippet).matches(".*(remote|remoto|home office).*" );
                    Map<String,Object> j=new LinkedHashMap<>();j.put("source",source);j.put("externalId",link);j.put("company",company);j.put("companyWebsite","");j.put("title",cleanTitle(title,item.get("source")));j.put("city",remote?"Remoto":"São Paulo");j.put("state","SP");j.put("workMode",remote?"Remoto":"Presencial");j.put("level",inferLevel(title+" "+snippet));j.put("salaryText","");j.put("description",snippet);j.put("requirements",snippet);j.put("url",link);j.put("publishedAt",LocalDate.now().toString());
                    if(!JobRules.isEntryLevel(String.valueOf(j.get("title")),snippet,String.valueOf(j.get("level")))){ActivityService.jobDecision(j,"SENIOR_LEVEL","Google Intelligence: senioridade acima do perfil de entrada.",null);continue;}
                    if(!JobRules.isRelevantToProfile(String.valueOf(profile.getOrDefault("target_roles","")),String.valueOf(profile.getOrDefault("skills","")),String.valueOf(j.get("title")),snippet)){ActivityService.jobDecision(j,"OUT_OF_PROFILE","Google Intelligence: termos da vaga não combinam com cargos/habilidades configurados.",null);continue;}
                    filtered++;
                    try{Services.addJob(j);saved++;}catch(IllegalStateException dup){duplicates++;ActivityService.jobDecision(j,"DUPLICATE",dup.getMessage(),null);}catch(Exception e){errors++;}
                    if(saved>=saveLimit)break;
                }
                h.put("count",count);h.put("status",count>0?"OK":"ZERO");
            }catch(Exception e){errors++;h.put("count",0);h.put("status","ERROR");h.put("error",e.getMessage());if(String.valueOf(e.getMessage()).contains("GOOGLE_QUOTA_EXHAUSTED"))break;}
            health.add(h);
        }
        Map<String,Object> out=new LinkedHashMap<>();out.put("raw",raw);out.put("filtered",filtered);out.put("saved",saved);out.put("duplicates",duplicates);out.put("errors",errors);out.put("health",health);out.putAll(GoogleSearchService.quotaStatus());return out;
    }

    static List<Map<String,String>> buildQueries(Map<String,Object> profile,String scope){
        List<String> terms=Services.buildSearchQueries(profile);String joined=String.join(" OR ",terms.stream().limit(8).map(x->"\""+x.replace("\"","")+"\"").toList());
        String east="\"Zona Leste\" OR Itaquera OR \"São Miguel Paulista\" OR Penha OR Tatuapé OR Guaianases OR \"Itaim Paulista\" OR \"Ermelino Matarazzo\" OR \"Ferraz de Vasconcelos\" OR Poá OR Suzano OR \"Mogi das Cruzes\"";
        String remote="\"home office\" OR remoto OR remote";String geo="REMOTE".equalsIgnoreCase(scope)?remote:"EAST_ZONE".equalsIgnoreCase(scope)?east:"("+east+") OR ("+remote+")";
        List<Map<String,String>> out=new ArrayList<>();out.add(Map.of("source","LinkedIn","q","site:linkedin.com/jobs "+joined+" ("+geo+")"));out.add(Map.of("source","Indeed","q","site:indeed.com "+joined+" ("+geo+")"));out.add(Map.of("source","InfoJobs","q","site:infojobs.com.br "+joined+" ("+geo+")"));out.add(Map.of("source","Vagas.com.br","q","site:vagas.com.br "+joined+" ("+geo+")"));out.add(Map.of("source","Gupy","q","site:gupy.io/jobs "+joined+" ("+geo+")"));return out;
    }
    private static String inferLevel(String s){String n=JobRules.norm(s);if(n.contains("estagio")||n.contains("intern"))return "Estágio";if(n.contains("assistente")||n.contains("auxiliar"))return "Assistente";return "Júnior";}
    private static String inferCompany(String title,String source){String[] p=title.split("\\s+[|–—]\\s+|\\s+-\\s+");return p.length>=2?p[p.length-1].trim():source;}
    private static String cleanTitle(String title,String source){String[] p=title.split("\\s+[|–—]\\s+|\\s+-\\s+");return (p.length>0?p[0]:title).trim();}
}
