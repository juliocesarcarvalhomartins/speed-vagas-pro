package com.speedvagas;

import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class PublicJobSources {
    private static final HttpClient HTTP=HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(8)).followRedirects(HttpClient.Redirect.NORMAL).build();
    private static final Map<String,CacheEntry> CACHE=new ConcurrentHashMap<>();
    private static final Duration DEFAULT_CACHE=Duration.ofMinutes(30);
    private PublicJobSources(){}

    public record Batch(String provider,List<Map<String,Object>> jobs,String errorCode,String error,boolean configured,String note){}
    private record CacheEntry(String body,Instant at){}

    public static Batch jobicy(String query,int limit){
        try{
            String body=cachedGet("jobicy",prop("speed.jobicy.url","https://jobicy.com/api/v2/remote-jobs?count=100"),Duration.ofHours(1));
            return success("Jobicy",filter(parseJobicy(body),query,limit),"API pública sem chave; cache de 1 hora.");
        }catch(Exception e){return failure("Jobicy",e,true,"Falha temporária da fonte remota.");}
    }

    public static Batch remotive(String query,int limit){
        try{
            String url=prop("speed.remotive.url","https://remotive.com/api/remote-jobs?search=")+enc(query);
            return success("Remotive",filter(parseRemotive(cachedGet("remotive:"+query,url,DEFAULT_CACHE)),query,limit),"API pública; cache de 30 minutos.");
        }catch(Exception e){return failure("Remotive",e,true,"Falha temporária da fonte remota.");}
    }

    public static Batch arbeitnow(String query,int limit){
        try{
            String url=prop("speed.arbeitnow.url","https://www.arbeitnow.com/api/job-board-api");
            return success("Arbeitnow",filter(parseArbeitnow(cachedGet("arbeitnow",url,DEFAULT_CACHE)),query,limit),"API JSON pública sem chave; cache de 30 minutos.");
        }catch(Exception e){return failure("Arbeitnow",e,true,"Falha temporária da fonte remota.");}
    }

    public static Batch remoteok(String query,int limit){
        try{
            String url=prop("speed.remoteok.url","https://remoteok.com/api");
            return success("RemoteOK",filter(parseRemoteOk(cachedGet("remoteok",url,DEFAULT_CACHE)),query,limit),"API JSON pública sem chave; cache de 30 minutos.");
        }catch(Exception e){return failure("RemoteOK",e,true,"Falha temporária da fonte remota.");}
    }

    public static Batch adzuna(String query,String where,int limit){
        String appId=prop("SPEED_ADZUNA_APP_ID","");
        String appKey=prop("SPEED_ADZUNA_APP_KEY","");
        if(appId.isBlank()||appKey.isBlank())
            return new Batch("Adzuna",List.of(),"NOT_CONFIGURED","",false,"Busca local opcional ainda não configurada.");
        try{
            String base=prop("SPEED_ADZUNA_URL","https://api.adzuna.com/v1/api/jobs/br/search/1?app_id=");
            String url=base+enc(appId)+"&app_key="+enc(appKey)+"&results_per_page="+Math.min(50,limit)+
                "&what="+enc(query)+"&where="+enc(where)+"&content-type=application/json&sort_by=date";
            String key="adzuna:"+JobRules.norm(query)+":"+JobRules.norm(where)+":"+limit;
            return success("Adzuna",limit(parseAdzuna(cachedGet(key,url,DEFAULT_CACHE)),limit),"Fonte local configurada; cache de 30 minutos.");
        }catch(Exception e){return failure("Adzuna",e,true,"Fonte local respondeu com erro.");}
    }

    private static Batch success(String p,List<Map<String,Object>> jobs,String note){return new Batch(p,jobs,"","",true,note);}
    private static Batch failure(String p,Exception e,boolean configured,String note){return new Batch(p,List.of(),errorCode(e),friendly(e),configured,note);}

    private static List<Map<String,Object>> filter(List<Map<String,Object>> all,String query,int limit){
        String nq=JobRules.norm(query);List<Map<String,Object>> out=new ArrayList<>();
        for(Map<String,Object> j:all){
            String hay=JobRules.norm(j.get("title")+" "+j.get("description")+" "+j.get("requirements"));
            if(!nq.isBlank()){
                int hit=0;for(String w:nq.split(" "))if(w.length()>2&&hay.contains(w))hit++;
                if(hit==0)continue;
            }
            out.add(j);if(out.size()>=limit)break;
        }
        return out;
    }
    private static List<Map<String,Object>> limit(List<Map<String,Object>> all,int limit){return all.size()<=limit?all:new ArrayList<>(all.subList(0,limit));}

    static List<Map<String,Object>> parseJobicy(String body){
        Map<String,Object> r=Json.obj(body);Object jv=r.get("jobs");List<Map<String,Object>> out=new ArrayList<>();
        if(jv instanceof List<?> list)for(Object o:list)if(o instanceof Map<?,?> mm){
            Map<String,Object> j=base("JOBICY",str(mm,"id",UUID.randomUUID().toString()),str(mm,"companyName","Empresa"),str(mm,"jobTitle","Vaga remota"));
            j.put("city",str(mm,"jobGeo","Remoto"));j.put("state","BR");j.put("workMode","Remoto");j.put("level",str(mm,"jobLevel",""));
            j.put("description",strip(str(mm,"jobDescription","")));j.put("requirements",String.valueOf(mm.containsKey("jobIndustry")?mm.get("jobIndustry"):""));j.put("url",str(mm,"url",""));
            j.put("publishedAt",date10(str(mm,"pubDate",LocalDate.now().toString())));out.add(j);
        }return out;
    }
    static List<Map<String,Object>> parseRemotive(String body){
        Map<String,Object> r=Json.obj(body);Object jv=r.get("jobs");List<Map<String,Object>> out=new ArrayList<>();
        if(jv instanceof List<?> list)for(Object o:list)if(o instanceof Map<?,?> mm){
            Map<String,Object> j=base("REMOTIVE",str(mm,"id",UUID.randomUUID().toString()),str(mm,"company_name","Empresa"),str(mm,"title","Vaga remota"));
            j.put("city",str(mm,"candidate_required_location","Brasil / Remoto"));j.put("state","BR");j.put("workMode","Remoto");j.put("level","");
            j.put("description",strip(str(mm,"description","")));j.put("requirements",String.valueOf(mm.containsKey("tags")?mm.get("tags"):""));j.put("url",str(mm,"url",""));
            j.put("publishedAt",date10(str(mm,"publication_date",LocalDate.now().toString())));out.add(j);
        }return out;
    }
    static List<Map<String,Object>> parseAdzuna(String body){
        Map<String,Object> r=Json.obj(body);Object rv=r.get("results");List<Map<String,Object>> out=new ArrayList<>();
        if(rv instanceof List<?> list)for(Object o:list)if(o instanceof Map<?,?> mm){
            String company="Empresa";Object co=mm.get("company");if(co instanceof Map<?,?> cm&&cm.get("display_name")!=null)company=String.valueOf(cm.get("display_name"));
            String loc="São Paulo";Object lo=mm.get("location");if(lo instanceof Map<?,?> lm&&lm.get("display_name")!=null)loc=String.valueOf(lm.get("display_name"));
            Map<String,Object> j=base("ADZUNA",str(mm,"id",UUID.randomUUID().toString()),company,str(mm,"title","Vaga"));
            j.put("city",loc);j.put("state","SP");String desc=strip(str(mm,"description",""));String hay=JobRules.norm(j.get("title")+" "+desc);
            j.put("workMode",(hay.contains("remoto")||hay.contains("home office")||hay.contains("home-office"))?"Remoto":"Presencial");j.put("level","");j.put("description",desc);j.put("requirements",desc);j.put("url",str(mm,"redirect_url",""));j.put("publishedAt",date10(str(mm,"created",LocalDate.now().toString())));out.add(j);
        }return out;
    }
    static List<Map<String,Object>> parseArbeitnow(String body){
        Map<String,Object> r=Json.obj(body);Object rv=r.get("data");List<Map<String,Object>> out=new ArrayList<>();
        if(rv instanceof List<?> list)for(Object o:list)if(o instanceof Map<?,?> mm){
            Map<String,Object> j=base("ARBEITNOW",str(mm,"slug",UUID.randomUUID().toString()),str(mm,"company_name","Empresa"),str(mm,"title","Vaga"));
            String loc=str(mm,"location","Remoto");j.put("city",loc);j.put("state","BR");boolean remote=Boolean.parseBoolean(String.valueOf(mm.containsKey("remote")?mm.get("remote"):false));
            j.put("workMode",remote?"Remoto":"Presencial");j.put("level","");j.put("description",strip(str(mm,"description","")));j.put("requirements",String.valueOf(mm.containsKey("tags")?mm.get("tags"):""));j.put("url",str(mm,"url",""));
            Object created=mm.get("created_at");String pub=created==null?LocalDate.now().toString():String.valueOf(created);j.put("publishedAt",date10(pub));out.add(j);
        }return out;
    }
    static List<Map<String,Object>> parseRemoteOk(String body){
        Object root=Json.parse(body);List<Map<String,Object>> out=new ArrayList<>();
        if(root instanceof List<?> list)for(Object o:list)if(o instanceof Map<?,?> mm&&mm.get("position")!=null){
            Map<String,Object> j=base("REMOTEOK",str(mm,"id",str(mm,"slug",UUID.randomUUID().toString())),str(mm,"company","Empresa"),str(mm,"position","Vaga remota"));
            String loc=str(mm,"location","Remoto");j.put("city",loc.isBlank()?"Remoto":loc);j.put("state","BR");j.put("workMode","Remoto");j.put("level","");
            j.put("description",strip(str(mm,"description","")));j.put("requirements",String.valueOf(mm.containsKey("tags")?mm.get("tags"):""));j.put("url",str(mm,"url",""));j.put("publishedAt",date10(str(mm,"date",LocalDate.now().toString())));out.add(j);
        }return out;
    }

    private static Map<String,Object> base(String source,String id,String company,String title){Map<String,Object> j=new LinkedHashMap<>();j.put("source",source);j.put("externalId",id);j.put("company",company);j.put("title",strip(title));return j;}
    private static synchronized String cachedGet(String key,String url,Duration ttl)throws Exception{
        CacheEntry c=CACHE.get(key);if(c!=null&&Duration.between(c.at(),Instant.now()).compareTo(ttl)<0)return c.body();
        String body=get(url);CACHE.put(key,new CacheEntry(body,Instant.now()));return body;
    }
    private static String get(String url)throws Exception{
        HttpRequest q=HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(12)).header("Accept","application/json").header("User-Agent","SPEED-VAGAS/6.4").GET().build();
        HttpResponse<String> r=HTTP.send(q,HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));if(r.statusCode()/100!=2)throw new IOException("HTTP "+r.statusCode());return r.body();
    }
    private static String prop(String k,String d){String p=System.getProperty(k);if(p!=null&&!p.isBlank())return p;String e=System.getenv(k);return e==null||e.isBlank()?d:e;}
    private static String enc(String s){return URLEncoder.encode(s,StandardCharsets.UTF_8);}
    private static String friendly(Exception e){String m=String.valueOf(e.getMessage());return m==null||m.isBlank()?e.getClass().getSimpleName():m;}
    private static String errorCode(Exception e){String m=friendly(e).toLowerCase(Locale.ROOT);if(m.contains("timeout"))return "TIMEOUT";if(m.contains("429"))return "RATE_LIMIT";if(m.contains("403"))return "FORBIDDEN";if(m.contains("401"))return "UNAUTHORIZED";if(m.contains("http"))return "HTTP_ERROR";return "NETWORK_ERROR";}
    static String strip(String s){return s==null?"":s.replaceAll("(?is)<script[^>]*>.*?</script>"," ").replaceAll("(?is)<style[^>]*>.*?</style>"," ").replaceAll("(?s)<[^>]+>"," ").replace("&nbsp;"," ").replace("&amp;","&").replace("&lt;","<").replace("&gt;",">").replaceAll("[\\p{Cntrl}&&[^\\r\\n\\t]]"," ").replaceAll("\\s+"," ").trim();}
    private static String str(Map<?,?> m,String k,String d){Object v=m.get(k);return v==null?d:String.valueOf(v);}
    private static String date10(String s){return s!=null&&s.length()>=10?s.substring(0,10):LocalDate.now().toString();}
}
