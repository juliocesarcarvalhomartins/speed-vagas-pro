package com.speedvagas;

import com.sun.net.httpserver.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.security.*;
import java.time.*;

public class SpeedVagasServer {
    private static final int PORT=Integer.parseInt(firstNonBlank(System.getProperty("speed.port"),System.getenv("PORT"),"8080"));
    private static final Path WEB=Path.of("web").toAbsolutePath().normalize();
    public static void main(String[] args)throws Exception{
        Database.init();
        HttpServer s=HttpServer.create(new InetSocketAddress("127.0.0.1",PORT),0);
        s.createContext("/api/health",x->ok(x,Map.of("status","ok","version","6.4.0","database","H2 embedded / MySQL-ready")));











        s.createContext("/api/google/status",x->route(x,"GET",GoogleOAuthService::status));
        s.createContext("/api/google/connect",x->route(x,"POST",GoogleOAuthService::start));
        // Fluxo direto pelo navegador: não depende de JavaScript nem de popup.

        s.createContext("/google/connect",x->{
            try{
                if(!"GET".equals(x.getRequestMethod())){method(x);return;}
                Map<String,Object> r=GoogleOAuthService.start(true);
                String url=String.valueOf(r.getOrDefault("authorizationUrl",""));
                if(url.isBlank())throw new IllegalStateException("Google não retornou URL de autorização.");
                x.getResponseHeaders().set("Location",url);
                x.getResponseHeaders().set("Cache-Control","no-store, no-cache, must-revalidate");
                x.sendResponseHeaders(302,-1);
                x.close();
            }catch(Exception e){
                String raw=String.valueOf(e.getMessage());
                if(raw.contains("Credencial Google não encontrada")){
                    x.getResponseHeaders().set("Location","/?google=credentials-missing");
                    x.getResponseHeaders().set("Cache-Control","no-store, no-cache, must-revalidate");
                    x.sendResponseHeaders(302,-1);
                    x.close();
                    return;
                }
                String msg=raw.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;");
                String home="http://127.0.0.1:"+PORT+"/";
                String html="<html><body style='font-family:Segoe UI;background:#0b0c10;color:white;padding:40px'>"
                    +"<h2>Falha ao iniciar login Google</h2><p>"+msg+"</p>"
                    +"<p><a style='color:#c084fc' href='"+home+"'>Voltar ao SPEED VAGAS</a></p></body></html>";
                byte[] b=html.getBytes(StandardCharsets.UTF_8);
                securityHeaders(x);
                x.getResponseHeaders().set("Content-Type","text/html; charset=utf-8");
                x.sendResponseHeaders(500,b.length);
                try(OutputStream os=x.getResponseBody()){os.write(b);}
            }
        });
        s.createContext("/api/google/start-diagnostic",x->route(x,"GET",()->{
            Map<String,Object> st=GoogleOAuthService.status();
            Map<String,Object> out=new LinkedHashMap<>(st);
            out.put("credentialsFile",Files.isRegularFile(Path.of("data","google","credentials.json")));
            out.put("server","OK");
            return out;
        }));
        s.createContext("/api/google/poll",x->route(x,"GET",GoogleOAuthService::poll));
        s.createContext("/api/google/disconnect",x->route(x,"POST",GoogleOAuthService::disconnect));
        s.createContext("/api/google/credentials",x->{try{if(!"POST".equals(x.getRequestMethod())){method(x);return;}byte[] b=x.getRequestBody().readNBytes(100001);if(b.length>100000)throw new IllegalArgumentException("credentials.json muito grande.");ok(x,GoogleOAuthService.saveCredentials(b));}catch(Exception e){error(x,e);}});
        s.createContext("/api/gmail/diagnostic",x->route(x,"GET",Services::gmailDiagnostic));
        s.createContext("/api/gmail/send-code",x->routeBody(x,"POST",Services::sendManualVerificationCode));
        auth(s.createContext("/api/dashboard",x->route(x,"GET",()->Services.dashboard())));
        auth(s.createContext("/api/profile",x->{try{if("GET".equals(x.getRequestMethod()))ok(x,Services.profile());else if("PUT".equals(x.getRequestMethod()))ok(x,Services.saveProfile(body(x)));else method(x);}catch(Exception e){error(x,e);}}));
        auth(s.createContext("/api/profile/document",x->routeBody(x,"POST",Services::saveDocument)));
        auth(s.createContext("/api/profile/upload",SpeedVagasServer::uploadRoute));
        auth(s.createContext("/api/jobs",x->{try{if("GET".equals(x.getRequestMethod()))ok(x,Services.jobs(query(x.getRequestURI().getRawQuery())));else if("POST".equals(x.getRequestMethod())){send(x,201,Services.addJob(body(x)));}else method(x);}catch(Exception e){error(x,e);}}));
        auth(s.createContext("/api/jobs/discarded",x->route(x,"GET",()->Services.discardedJobs(query(x.getRequestURI().getRawQuery())))));
        auth(s.createContext("/api/jobs/feedback",x->routeBody(x,"POST",Services::jobFeedback)));
        s.createContext("/api/search/providers",x->route(x,"GET",()->Services.searchInternet(new LinkedHashMap<>(Map.of("query","suporte TI","scope","REMOTE","limit",1)))));
        auth(s.createContext("/api/search/smart",x->routeBody(x,"POST",Services::smartSearch)));
        auth(s.createContext("/api/search/internet",x->routeBody(x,"POST",Services::searchInternet)));
        auth(s.createContext("/api/search/google",x->routeBody(x,"POST",GoogleSearchService::search)));
        auth(s.createContext("/api/search/google/status",x->route(x,"GET",GoogleSearchService::status)));
        auth(s.createContext("/api/contacts",x->route(x,"GET",()->Services.contacts())));
        auth(s.createContext("/api/contacts/discover",x->routeBody(x,"POST",Services::discoverContacts)));
        auth(s.createContext("/api/applications",x->{try{if("GET".equals(x.getRequestMethod()))ok(x,Services.applications());else if("POST".equals(x.getRequestMethod())){try{send(x,201,Services.apply(body(x)));}catch(IllegalStateException e){send(x,409,Map.of("error",e.getMessage()));}}else method(x);}catch(Exception e){error(x,e);}}));
        auth(s.createContext("/api/applications/send",x->{try{if(!"POST".equals(x.getRequestMethod())){method(x);return;}try{ok(x,Services.sendResume(body(x)));}catch(IllegalStateException e){send(x,409,Map.of("error",e.getMessage()));}}catch(Exception e){error(x,e);}}));
        auth(s.createContext("/api/applications/approve",x->routeBody(x,"POST",Services::approveApplication)));
        auth(s.createContext("/api/applications/approve-bulk",x->routeBody(x,"POST",Services::approveBulk)));
        auth(s.createContext("/api/settings",x->{try{if("GET".equals(x.getRequestMethod()))ok(x,Services.settings());else if("PUT".equals(x.getRequestMethod()))ok(x,Services.saveSettings(body(x)));else method(x);}catch(Exception e){error(x,e);}}));
        auth(s.createContext("/api/agent/run",x->routeBody(x,"POST",Services::agentRun)));
        auth(s.createContext("/api/recalculate",x->{try{if(!"POST".equals(x.getRequestMethod())){method(x);return;}Services.recalcAll();ok(x,Map.of("status","ok"));}catch(Exception e){error(x,e);}}));
        s.createContext("/api/connectors",x->route(x,"GET",()->Services.connectorStatus()));
        s.createContext("/api/connectors/test",x->routeBody(x,"POST",Services::testConnector));
        s.createContext("/api/connectors/test-all",x->route(x,"GET",()->Services.testAllConnectors()));
        s.createContext("/api/activity",x->route(x,"GET",()->ActivityService.recent(Integer.parseInt(query(x.getRequestURI().getRawQuery()).getOrDefault("limit","100")))));
        s.createContext("/api/notifications",x->route(x,"GET",ActivityService::notifications));
        s.createContext("/api/notifications/resolve",x->routeBody(x,"POST",ActivityService::resolveNotification));
        s.createContext("/api/automation/run",x->routeBody(x,"POST",AutomationService::run));
        s.createContext("/api/email/check",x->routeBody(x,"POST",GmailApiService::checkInbox));
        s.createContext("/api/email/events",x->route(x,"GET",EmailAgent::events));
        s.createContext("/api/email/reply-draft",x->routeBody(x,"POST",EmailAgent::suggestedReply));
        s.createContext("/api/diagnostics",x->route(x,"GET",()->Services.diagnostics()));
        s.createContext("/api/manager/run",x->routeBody(x,"POST",Services::managerRun));
        s.createContext("/files/",SpeedVagasServer::fileRoute);
        s.createContext("/",SpeedVagasServer::staticRoute);
        s.setExecutor(Executors.newFixedThreadPool(12));s.start();
        ScheduledExecutorService auto=Executors.newSingleThreadScheduledExecutor(r->{Thread t=new Thread(r,"speed-auto-manager");t.setDaemon(true);return t;});
        auto.scheduleWithFixedDelay(()->{try{Map<String,Object> r=AutomationService.backgroundCycle();System.out.println("[AUTO] ciclo concluido: "+Json.stringify(r));}catch(Exception e){System.err.println("[AUTO] "+e.getMessage());}},1,5,java.util.concurrent.TimeUnit.MINUTES);
        System.out.println("[OK] SPEED VAGAS PRO 6.4.0 iniciado em http://localhost:"+PORT);
        System.out.println("[OK] Banco: H2 persistente em ./data | Schema MySQL: ./sql/schema_mysql.sql");
        System.out.println("[MODO] Uso pessoal: sem tela de login.");
    }
    static HttpContext auth(HttpContext c){ return c; }
    static String firstNonBlank(String...v){for(String x:v)if(x!=null&&!x.isBlank())return x;return "";}
    interface Task{Object run()throws Exception;} interface BodyTask{Object run(Map<String,Object>m)throws Exception;}
    static void route(HttpExchange x,String m,Task t)throws IOException{if(!m.equals(x.getRequestMethod())){method(x);return;}try{ok(x,t.run());}catch(Exception e){error(x,e);}}
    static void routeBody(HttpExchange x,String m,BodyTask t)throws IOException{if(!m.equals(x.getRequestMethod())){method(x);return;}try{ok(x,t.run(body(x)));}catch(Exception e){error(x,e);}}
    static Map<String,Object> body(HttpExchange x)throws IOException{
        int max=1_000_000;ByteArrayOutputStream b=new ByteArrayOutputStream();byte[] buf=new byte[8192];int n,total=0;
        try(InputStream in=x.getRequestBody()){while((n=in.read(buf))!=-1){total+=n;if(total>max)throw new IllegalArgumentException("Requisição muito grande.");b.write(buf,0,n);}}
        return Json.obj(b.toString(StandardCharsets.UTF_8));
    }
    static Map<String,String> query(String raw){Map<String,String>m=new LinkedHashMap<>();if(raw==null)return m;for(String p:raw.split("&")){String[]kv=p.split("=",2);m.put(dec(kv[0]),kv.length>1?dec(kv[1]):"");}return m;}static String dec(String s){return URLDecoder.decode(s,StandardCharsets.UTF_8);}
    static void ok(HttpExchange x,Object o)throws IOException{send(x,200,o);}
    static void securityHeaders(HttpExchange x){
        var h=x.getResponseHeaders();
        h.set("X-Content-Type-Options","nosniff");h.set("X-Frame-Options","DENY");h.set("Referrer-Policy","no-referrer");
        h.set("Permissions-Policy","camera=(), microphone=(), geolocation=()");
        h.set("Content-Security-Policy","default-src 'self'; img-src 'self' data: blob:; style-src 'self' 'unsafe-inline'; script-src 'self'; connect-src 'self'; frame-ancestors 'none'; base-uri 'self'; form-action 'self'");
    }
    static void send(HttpExchange x,int status,Object o)throws IOException{
        securityHeaders(x);byte[]b=Json.stringify(o).getBytes(StandardCharsets.UTF_8);
        x.getResponseHeaders().set("Content-Type","application/json; charset=utf-8");x.getResponseHeaders().set("Cache-Control","no-store");
        x.sendResponseHeaders(status,b.length);try(OutputStream out=x.getResponseBody()){out.write(b);}
    }
    static void error(HttpExchange x,Exception e)throws IOException{e.printStackTrace();int status=e instanceof IllegalArgumentException?400:500;send(x,status,Map.of("error",e.getMessage()==null?e.getClass().getSimpleName():e.getMessage()));}static void method(HttpExchange x)throws IOException{send(x,405,Map.of("error","Método não permitido"));}
    static void staticRoute(HttpExchange x)throws IOException{String p=x.getRequestURI().getPath();if(p.equals("/"))p="/index.html";Path f=WEB.resolve(p.substring(1)).normalize();if(!f.startsWith(WEB)||!Files.exists(f)||Files.isDirectory(f)){x.sendResponseHeaders(404,-1);return;}String type=p.endsWith(".css")?"text/css":p.endsWith(".js")?"application/javascript":p.endsWith(".svg")?"image/svg+xml":"text/html";byte[]b=Files.readAllBytes(f);securityHeaders(x);x.getResponseHeaders().set("Content-Type",type+"; charset=utf-8");x.getResponseHeaders().set("Cache-Control","no-store, no-cache, must-revalidate");x.sendResponseHeaders(200,b.length);try(OutputStream o=x.getResponseBody()){o.write(b);}}
    static void uploadRoute(HttpExchange x)throws IOException {
        if(!"POST".equals(x.getRequestMethod())){method(x);return;}
        try{
            Map<String,String> q=query(x.getRequestURI().getRawQuery());
            String kind=q.getOrDefault("kind","RESUME");
            String file=Path.of(q.getOrDefault("fileName","arquivo.bin")).getFileName().toString().replaceAll("[^A-Za-z0-9._() -]","_");
            String mime=x.getRequestHeaders().getFirst("Content-Type");
            if(mime==null||mime.isBlank()) mime=q.getOrDefault("mime","application/octet-stream");
            int max="PHOTO".equalsIgnoreCase(kind)?8_000_000:15_000_000;
            ByteArrayOutputStream buf=new ByteArrayOutputStream();
            try(InputStream in=x.getRequestBody()){byte[] b=new byte[16384];int n,total=0;while((n=in.read(b))!=-1){total+=n;if(total>max)throw new IllegalArgumentException("Arquivo excede o limite permitido.");buf.write(b,0,n);}}
            ok(x,Services.saveDocumentBytes(kind,file,mime,buf.toByteArray()));
        }catch(Exception e){error(x,e);}
    }
    static void fileRoute(HttpExchange x)throws IOException{String p=x.getRequestURI().getPath().substring("/files/".length());Path base=Path.of("data/uploads").toAbsolutePath().normalize(),f=base.resolve(p).normalize();if(!f.startsWith(base)||!Files.exists(f)){x.sendResponseHeaders(404,-1);return;}securityHeaders(x);String n=f.getFileName().toString().toLowerCase();String type=n.endsWith(".pdf")?"application/pdf":n.endsWith(".png")?"image/png":n.endsWith(".jpg")||n.endsWith(".jpeg")?"image/jpeg":n.endsWith(".webp")?"image/webp":"application/octet-stream";byte[]b=Files.readAllBytes(f);x.getResponseHeaders().set("Content-Type",type);x.sendResponseHeaders(200,b.length);try(OutputStream o=x.getResponseBody()){o.write(b);}}
}
