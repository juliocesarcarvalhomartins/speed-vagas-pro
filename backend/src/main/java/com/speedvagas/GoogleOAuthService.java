package com.speedvagas;

import com.sun.net.httpserver.HttpServer;
import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.SecureRandom;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;

public final class GoogleOAuthService {
    private static final Path GOOGLE_DIR=Path.of("data","google");
    private static final Path CREDENTIALS=GOOGLE_DIR.resolve("credentials.json");
    private static final Path TOKEN_FILE=GOOGLE_DIR.resolve("oauth_token.properties");
    private static final HttpClient HTTP=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(12)).build();
    private static final SecureRandom RNG=new SecureRandom();
    private static final String AUTH_URL="https://accounts.google.com/o/oauth2/v2/auth";
    private static final String TOKEN_URL="https://oauth2.googleapis.com/token";
    private static final String USERINFO="https://www.googleapis.com/oauth2/v3/userinfo";
    private static final String SEND_SCOPE="https://www.googleapis.com/auth/gmail.send";
    private static final String READ_SCOPE="https://www.googleapis.com/auth/gmail.readonly";
    private static final String GMAIL_SCOPE=SEND_SCOPE+" "+READ_SCOPE+" openid email";
    private static volatile PendingAuth pending;

    private GoogleOAuthService(){}

    public static Set<String> requiredScopes(){return Set.of(SEND_SCOPE,READ_SCOPE,"openid","email");}

    public static Map<String,Object> status() {
        Map<String,Object> out=new LinkedHashMap<>();
        out.put("credentialsPresent",ensureCredentialsAvailable());
        try{
            Token t=loadToken();
            if(t==null){out.put("connected",false);out.put("message","Google ainda não conectado.");return out;}
            String access=ensureAccessToken(t);
            Map<String,Object> me=getJson(USERINFO,access);
            out.put("connected",true);
            out.put("email",String.valueOf(me.getOrDefault("email","")));
            out.put("expiresAt",t.expiresAt.toString());
            Set<String> scopes=resolveScopes(access,t.scopes);
            boolean send=scopes.contains(SEND_SCOPE),read=scopes.contains(READ_SCOPE);
            out.put("grantedScopes",new ArrayList<>(scopes));out.put("sendGranted",send);out.put("readGranted",read);
            out.put("permissionsOk",send&&read);
            out.put("message",send&&read?"Google e Gmail autorizados.":"Google conectado, mas faltam permissões do Gmail. Reconecte a conta.");
        }catch(Exception e){
            out.put("connected",false);
            out.put("message",friendly(e));
        }
        return out;
    }

    public static Map<String,Object> start() throws Exception { return start(false); }
    public static Map<String,Object> start(boolean force) throws Exception {
        if(force){Files.deleteIfExists(TOKEN_FILE);pending=null;}
        Credentials c=loadCredentials();
        if(c==null)throw new IllegalStateException("Credencial Google não encontrada. Importe o credentials.json em Configurações ou coloque o arquivo na pasta do SPEED VAGAS/Downloads e tente novamente.");
        if(pending!=null && !pending.done.get()) return Map.of("started",true,"authorizationUrl",pending.url,"waiting",true);

        String state=randomUrlSafe(24);
        HttpServer cb=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        int port=cb.getAddress().getPort();
        String redirect="http://localhost:"+port+"/oauth2/callback";
        CompletableFuture<String> codeFuture=new CompletableFuture<>();

        cb.createContext("/oauth2/callback",x->{
            try{
                Map<String,String> q=parseQuery(x.getRequestURI().getRawQuery());
                String body;
                if(!state.equals(q.get("state"))){
                    body="<html><body><h2>Falha de segurança</h2><p>State inválido.</p></body></html>";
                    codeFuture.completeExceptionally(new SecurityException("OAuth state inválido."));
                }else if(q.containsKey("error")){
                    body="<html><body><h2>Autorização cancelada</h2><p>Você pode fechar esta janela.</p></body></html>";
                    codeFuture.completeExceptionally(new IllegalStateException("Google OAuth: "+q.get("error")));
                }else{
                    body="<html><body style='font-family:sans-serif;background:#101014;color:#fff;padding:40px'><h2>Google conectado ao SPEED VAGAS ✓</h2><p>Você pode fechar esta janela e voltar ao aplicativo.</p></body></html>";
                    codeFuture.complete(q.get("code"));
                }
                byte[] b=body.getBytes(StandardCharsets.UTF_8);
                x.getResponseHeaders().set("Content-Type","text/html; charset=utf-8");
                x.sendResponseHeaders(200,b.length);
                try(OutputStream os=x.getResponseBody()){os.write(b);}
            }catch(Exception e){codeFuture.completeExceptionally(e);}
        });
        cb.setExecutor(Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,"oauth-callback");t.setDaemon(true);return t;}));
        cb.start();

        String url=AUTH_URL+
            "?client_id="+enc(c.clientId)+
            "&redirect_uri="+enc(redirect)+
            "&response_type=code"+
            "&scope="+enc(GMAIL_SCOPE)+
            "&access_type=offline"+
            "&prompt=consent"+
            "&include_granted_scopes=true"+
            "&state="+enc(state);

        PendingAuth pa=new PendingAuth(url,redirect,cb,codeFuture);
        pending=pa;

        Thread worker=new Thread(()->{
            try{
                String code=codeFuture.get(5,TimeUnit.MINUTES);
                Token t=exchangeCode(c,code,redirect);
                saveToken(t);
                pa.done.set(true);
                ActivityService.log("GOOGLE_CONNECTED","GMAIL",null,"Google conectado","Gmail API autorizada com OAuth 2.0.","SUCCESS");
            }catch(Exception e){
                pa.error=friendly(e);pa.done.set(true);
                ActivityService.log("GOOGLE_OAUTH_ERROR","GMAIL",null,"Falha ao conectar Google",pa.error,"WARNING");
            }finally{
                try{cb.stop(0);}catch(Exception ignored){}
            }
        },"google-oauth-worker");
        worker.setDaemon(true);worker.start();
        return Map.of("started",true,"authorizationUrl",url,"waiting",true,"redirectUri",redirect);
    }

    public static Map<String,Object> poll() {
        PendingAuth p=pending;
        if(p==null)return status();
        if(!p.done.get())return Map.of("connected",false,"waiting",true,"authorizationUrl",p.url,"message","Aguardando autorização no Google.");
        if(p.error!=null)return Map.of("connected",false,"waiting",false,"message",p.error);
        return status();
    }

    public static Map<String,Object> disconnect() throws Exception {
        Token t=loadToken();
        if(t!=null && t.accessToken!=null&&!t.accessToken.isBlank()){
            try{
                HttpRequest req=HttpRequest.newBuilder(URI.create("https://oauth2.googleapis.com/revoke?token="+enc(t.accessToken)))
                    .header("Content-Type","application/x-www-form-urlencoded").POST(HttpRequest.BodyPublishers.noBody()).build();
                HTTP.send(req,HttpResponse.BodyHandlers.discarding());
            }catch(Exception ignored){}
        }
        Files.deleteIfExists(TOKEN_FILE);
        ActivityService.log("GOOGLE_DISCONNECTED","GMAIL",null,"Google desconectado","Token OAuth removido deste computador.","OK");
        return Map.of("connected",false);
    }

    public static Map<String,Object> saveCredentials(byte[] bytes) throws Exception {
        if(bytes==null||bytes.length<20||bytes.length>100_000)throw new IllegalArgumentException("Arquivo credentials.json inválido.");
        String txt=new String(bytes,StandardCharsets.UTF_8);
        Map<String,Object> j=Json.obj(txt);
        Object installed=j.get("installed");
        if(!(installed instanceof Map<?,?>))throw new IllegalArgumentException("Use um OAuth Client ID do tipo Desktop app.");
        Map<?,?> m=(Map<?,?>)installed;
        String id=String.valueOf(m.containsKey("client_id")?m.get("client_id"):"");
        String secret=String.valueOf(m.containsKey("client_secret")?m.get("client_secret"):"");
        if(id.isBlank()||secret.isBlank())throw new IllegalArgumentException("credentials.json não contém client_id/client_secret.");
        Files.createDirectories(GOOGLE_DIR);
        Files.write(CREDENTIALS,bytes,StandardOpenOption.CREATE,StandardOpenOption.TRUNCATE_EXISTING);
        return Map.of("saved",true,"clientId",maskClient(id));
    }

    public static String accessToken() throws Exception {
        String test=System.getProperty("speed.test.google.token");
        if(test!=null&&!test.isBlank())return test;
        Token t=loadToken();
        if(t==null)throw new IllegalStateException("Google não conectado. Clique em Conectar com Google.");
        return ensureAccessToken(t);
    }

    private static String ensureAccessToken(Token t)throws Exception{
        if(t.accessToken!=null&&!t.accessToken.isBlank()&&Instant.now().isBefore(t.expiresAt.minusSeconds(60)))return t.accessToken;
        if(t.refreshToken==null||t.refreshToken.isBlank())throw new IllegalStateException("Refresh token ausente. Reconecte o Google.");
        Credentials c=loadCredentials();
        if(c==null)throw new IllegalStateException("credentials.json ausente.");
        String form="client_id="+enc(c.clientId)+"&client_secret="+enc(c.clientSecret)+"&refresh_token="+enc(t.refreshToken)+"&grant_type=refresh_token";
        HttpRequest req=HttpRequest.newBuilder(URI.create(TOKEN_URL)).header("Content-Type","application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(form)).build();
        HttpResponse<String> resp=HTTP.send(req,HttpResponse.BodyHandlers.ofString());
        if(resp.statusCode()/100!=2)throw new IOException("Google token refresh falhou: HTTP "+resp.statusCode());
        Map<String,Object> j=Json.obj(resp.body());
        String access=String.valueOf(j.getOrDefault("access_token",""));
        int expires=((Number)j.getOrDefault("expires_in",3600)).intValue();
        Token n=new Token(access,t.refreshToken,Instant.now().plusSeconds(expires),t.scopes);
        saveToken(n);
        return access;
    }

    private static Token exchangeCode(Credentials c,String code,String redirect)throws Exception{
        String form="code="+enc(code)+"&client_id="+enc(c.clientId)+"&client_secret="+enc(c.clientSecret)+"&redirect_uri="+enc(redirect)+"&grant_type=authorization_code";
        HttpRequest req=HttpRequest.newBuilder(URI.create(TOKEN_URL)).header("Content-Type","application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(form)).build();
        HttpResponse<String> resp=HTTP.send(req,HttpResponse.BodyHandlers.ofString());
        if(resp.statusCode()/100!=2)throw new IOException("Troca do código OAuth falhou: HTTP "+resp.statusCode()+" "+resp.body());
        Map<String,Object> j=Json.obj(resp.body());
        String access=String.valueOf(j.getOrDefault("access_token",""));
        String refresh=String.valueOf(j.getOrDefault("refresh_token",""));
        int expires=((Number)j.getOrDefault("expires_in",3600)).intValue();
        if(access.isBlank())throw new IOException("Google não retornou access_token.");
        String scopes=String.valueOf(j.getOrDefault("scope",GMAIL_SCOPE));return new Token(access,refresh,Instant.now().plusSeconds(expires),scopes);
    }

    private static Map<String,Object> getJson(String url,String token)throws Exception{
        HttpRequest req=HttpRequest.newBuilder(URI.create(url)).header("Authorization","Bearer "+token).GET().build();
        HttpResponse<String> r=HTTP.send(req,HttpResponse.BodyHandlers.ofString());
        if(r.statusCode()/100!=2)throw new IOException("Google API HTTP "+r.statusCode());
        return Json.obj(r.body());
    }

    private static Set<String> resolveScopes(String access,String stored){
        LinkedHashSet<String> out=new LinkedHashSet<>();
        if(stored!=null)for(String x:stored.split("\\s+"))if(!x.isBlank())out.add(x);
        if(out.contains(SEND_SCOPE)&&out.contains(READ_SCOPE))return out;
        try{
            String base=System.getProperty("speed.google.tokeninfo.url","https://oauth2.googleapis.com/tokeninfo?access_token=");
            Map<String,Object> j=Json.obj(httpGet(base+enc(access)));
            String sc=String.valueOf(j.getOrDefault("scope",""));
            for(String x:sc.split("\\s+"))if(!x.isBlank())out.add(x);
        }catch(Exception ignored){}
        return out;
    }
    private static String httpGet(String url)throws Exception{
        HttpRequest q=HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(8)).GET().build();
        HttpResponse<String> r=HTTP.send(q,HttpResponse.BodyHandlers.ofString());
        if(r.statusCode()/100!=2)throw new IOException("HTTP "+r.statusCode());return r.body();
    }

    private static Credentials loadCredentials()throws Exception{
        ensureCredentialsAvailable();
        if(!Files.isRegularFile(CREDENTIALS))return null;
        Map<String,Object> j=Json.obj(Files.readString(CREDENTIALS));
        Object o=j.get("installed");
        if(!(o instanceof Map<?,?>))return null;
        Map<?,?> m=(Map<?,?>)o;
        String id=String.valueOf(m.get("client_id"));
        String secret=String.valueOf(m.get("client_secret"));
        if(id.isBlank()||secret.isBlank())return null;
        return new Credentials(id,secret);
    }

    private static boolean ensureCredentialsAvailable(){
        try{
            if(Files.isRegularFile(CREDENTIALS))return true;
            List<Path> candidates=new ArrayList<>();
            String explicit=System.getenv("SPEED_GOOGLE_CREDENTIALS");
            if(explicit!=null&&!explicit.isBlank())candidates.add(Path.of(explicit));
            candidates.add(Path.of("credentials.json"));
            candidates.add(Path.of("..","credentials.json"));
            Path home=Path.of(System.getProperty("user.home","."));
            Path downloads=home.resolve("Downloads");
            if(Files.isDirectory(downloads)){
                try(var stream=Files.list(downloads)){
                    stream.filter(Files::isRegularFile)
                        .filter(x->{String n=x.getFileName().toString().toLowerCase(Locale.ROOT);return n.equals("credentials.json")||n.startsWith("client_secret")&&n.endsWith(".json");})
                        .sorted((a,b)->{try{return Files.getLastModifiedTime(b).compareTo(Files.getLastModifiedTime(a));}catch(Exception e){return 0;}})
                        .limit(5).forEach(candidates::add);
                }
            }
            for(Path c:candidates){
                try{
                    if(!Files.isRegularFile(c))continue;
                    byte[] bytes=Files.readAllBytes(c);
                    if(bytes.length<20||bytes.length>100_000)continue;
                    Map<String,Object> j=Json.obj(new String(bytes,StandardCharsets.UTF_8));
                    Object o=j.get("installed");
                    if(!(o instanceof Map<?,?> m))continue;
                    String id=String.valueOf(m.get("client_id")),secret=String.valueOf(m.get("client_secret"));
                    if(id.isBlank()||secret.isBlank())continue;
                    Files.createDirectories(GOOGLE_DIR);
                    Files.write(CREDENTIALS,bytes,StandardOpenOption.CREATE,StandardOpenOption.TRUNCATE_EXISTING);
                    return true;
                }catch(Exception ignored){}
            }
        }catch(Exception ignored){}
        return false;
    }

    private static void saveToken(Token t)throws Exception{
        Files.createDirectories(GOOGLE_DIR);
        Properties p=new Properties();
        p.setProperty("access_token",Base64.getEncoder().encodeToString(t.accessToken.getBytes(StandardCharsets.UTF_8)));
        p.setProperty("refresh_token",Base64.getEncoder().encodeToString(String.valueOf(t.refreshToken).getBytes(StandardCharsets.UTF_8)));
        p.setProperty("expires_at",t.expiresAt.toString());p.setProperty("scopes",String.valueOf(t.scopes));
        try(OutputStream o=Files.newOutputStream(TOKEN_FILE,StandardOpenOption.CREATE,StandardOpenOption.TRUNCATE_EXISTING)){
            p.store(o,"SPEED VAGAS OAuth token - arquivo local do usuario");
        }
    }

    private static Token loadToken()throws Exception{
        if(!Files.isRegularFile(TOKEN_FILE))return null;
        Properties p=new Properties();try(InputStream in=Files.newInputStream(TOKEN_FILE)){p.load(in);}
        String a=decode(p.getProperty("access_token","")),r=decode(p.getProperty("refresh_token",""));
        Instant e=Instant.parse(p.getProperty("expires_at",Instant.EPOCH.toString()));
        return new Token(a,r,e,p.getProperty("scopes",""));
    }

    private static String decode(String v){
        if(v==null||v.isBlank())return "";
        return new String(Base64.getDecoder().decode(v),StandardCharsets.UTF_8);
    }
    private static String enc(String s){return URLEncoder.encode(s,StandardCharsets.UTF_8);}
    private static String randomUrlSafe(int n){byte[] b=new byte[n];RNG.nextBytes(b);return Base64.getUrlEncoder().withoutPadding().encodeToString(b);}
    private static String maskClient(String s){return s.length()<12?s:s.substring(0,8)+"..."+s.substring(s.length()-8);}
    private static Map<String,String> parseQuery(String q){
        Map<String,String> m=new LinkedHashMap<>();if(q==null)return m;
        for(String p:q.split("&")){String[] a=p.split("=",2);m.put(URLDecoder.decode(a[0],StandardCharsets.UTF_8),a.length>1?URLDecoder.decode(a[1],StandardCharsets.UTF_8):"");}
        return m;
    }
    private static String friendly(Exception e){
        Throwable x=e;while(x.getCause()!=null)x=x.getCause();
        String m=String.valueOf(x.getMessage());
        if(m.contains("access_denied"))return "Autorização do Google foi cancelada.";
        if(m.contains("invalid_client"))return "Client ID/secret inválido. Gere um OAuth Client ID do tipo Desktop app.";
        return m==null||m.isBlank()?x.getClass().getSimpleName():m;
    }

    record Credentials(String clientId,String clientSecret){}
    record Token(String accessToken,String refreshToken,Instant expiresAt,String scopes){}
    static final class PendingAuth{
        final String url,redirect;final HttpServer server;final CompletableFuture<String> code;final java.util.concurrent.atomic.AtomicBoolean done=new java.util.concurrent.atomic.AtomicBoolean(false);volatile String error;
        PendingAuth(String u,String r,HttpServer s,CompletableFuture<String> c){url=u;redirect=r;server=s;code=c;}
    }
}
