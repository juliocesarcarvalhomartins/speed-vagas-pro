package com.speedvagas;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Server-side Google Programmable Search proxy with persistent daily quota and cache. */
public final class GoogleSearchService {
    private GoogleSearchService() {}
    private static final HttpClient HTTP = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(8)).followRedirects(HttpClient.Redirect.NORMAL).build();
    private static final Map<String,CacheEntry> CACHE=new ConcurrentHashMap<>();
    private static final Duration CACHE_TTL=Duration.ofMinutes(30);
    private record CacheEntry(Map<String,Object> value,Instant at){}

    private static String cfg(String key,String def) {
        String p=System.getProperty(key); if(p!=null&&!p.isBlank())return p.trim();
        String e=System.getenv(key); return e==null||e.isBlank()?def:e.trim();
    }
    private static String cfg(String key){return cfg(key,"");}

    public static boolean configured(){return !cfg("SPEED_GOOGLE_SEARCH_KEY").isBlank()&&!cfg("SPEED_GOOGLE_SEARCH_CX").isBlank();}
    public static int dailyLimit(){try{return Math.max(1,Math.min(1000,Integer.parseInt(cfg("SPEED_GOOGLE_DAILY_LIMIT","100"))));}catch(Exception e){return 100;}}

    public static Map<String,Object> search(Map<String,Object> req)throws Exception{
        if(!configured())throw new IllegalStateException("Google Search não configurado no backend. Defina SPEED_GOOGLE_SEARCH_KEY e SPEED_GOOGLE_SEARCH_CX no ambiente.");
        String q=Json.str(req,"q","").trim();if(q.isBlank())throw new IllegalArgumentException("Consulta vazia.");
        int start=Math.max(1,Math.min(Json.integer(req,"start",1),91));int num=Math.max(1,Math.min(Json.integer(req,"num",10),10));
        String cacheKey=JobRules.norm(q)+"|"+start+"|"+num;
        CacheEntry ce=CACHE.get(cacheKey);
        if(ce!=null&&Duration.between(ce.at(),Instant.now()).compareTo(CACHE_TTL)<0){
            Map<String,Object> out=new LinkedHashMap<>(ce.value());out.put("cached",true);out.putAll(quotaStatus());return out;
        }
        reserveQuota();
        String url="https://www.googleapis.com/customsearch/v1?key="+enc(cfg("SPEED_GOOGLE_SEARCH_KEY"))+"&cx="+enc(cfg("SPEED_GOOGLE_SEARCH_CX"))+"&q="+enc(q)+"&num="+num+"&start="+start;
        HttpRequest httpReq=HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(12)).header("User-Agent","SPEED-VAGAS/6.4").GET().build();
        ExternalRateLimiter.acquire("google-search",Duration.ofMillis(350));
        HttpResponse<String> r=HTTP.send(httpReq,HttpResponse.BodyHandlers.ofString());
        if(r.statusCode()/100!=2){
            if(r.statusCode()==429)throw new IllegalStateException("GOOGLE_QUOTA: Google Search recusou por limite de cota.");
            throw new IllegalStateException("GOOGLE_HTTP_"+r.statusCode()+": Google Search respondeu HTTP "+r.statusCode()+".");
        }
        Map<String,Object> raw=Json.obj(r.body());List<Map<String,Object>> items=new ArrayList<>();Object list=raw.get("items");
        if(list instanceof List<?> l)for(Object o:l)if(o instanceof Map<?,?> m){Map<String,Object>x=new LinkedHashMap<>();x.put("title",safe(m.get("title")));x.put("link",safe(m.get("link")));x.put("snippet",safe(m.get("snippet")));x.put("displayLink",safe(m.get("displayLink")));items.add(x);}
        Map<String,Object> out=new LinkedHashMap<>();out.put("items",items);out.put("count",items.size());out.put("configured",true);out.put("cached",false);out.putAll(quotaStatus());
        CACHE.put(cacheKey,new CacheEntry(new LinkedHashMap<>(out),Instant.now()));return out;
    }

    private static synchronized void reserveQuota()throws Exception{
        String day=LocalDate.now().toString();int limit=dailyLimit();
        var rows=Database.query("select used_count from google_search_quota where quota_day=?",day);
        int used=rows.isEmpty()?0:((Number)rows.get(0).get("used_count")).intValue();
        if(used>=limit)throw new IllegalStateException("GOOGLE_QUOTA_EXHAUSTED: cota diária do Google Search esgotada ("+used+"/"+limit+").");
        if(rows.isEmpty())Database.insert("insert into google_search_quota(quota_day,used_count,updated_at) values(?,1,CURRENT_TIMESTAMP)",day);
        else Database.update("update google_search_quota set used_count=used_count+1,updated_at=CURRENT_TIMESTAMP where quota_day=?",day);
    }
    public static Map<String,Object> quotaStatus(){
        try{String day=LocalDate.now().toString();var rows=Database.query("select used_count from google_search_quota where quota_day=?",day);int used=rows.isEmpty()?0:((Number)rows.get(0).get("used_count")).intValue();int limit=dailyLimit();return Map.of("quotaDay",day,"quotaUsed",used,"quotaLimit",limit,"quotaRemaining",Math.max(0,limit-used));}
        catch(Exception e){return Map.of("quotaUsed",0,"quotaLimit",dailyLimit(),"quotaRemaining",dailyLimit());}
    }
    public static Map<String,Object> status(){Map<String,Object> out=new LinkedHashMap<>();out.put("configured",configured());out.put("credentialLocation","BACKEND_ENV");out.put("cacheMinutes",30);out.putAll(quotaStatus());return out;}
    private static String enc(String s){return URLEncoder.encode(s,StandardCharsets.UTF_8);}private static String safe(Object o){return o==null?"":String.valueOf(o);}
}
