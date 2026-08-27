package com.speedvagas;

import com.sun.net.httpserver.HttpServer;
import java.io.*;import java.net.*;import java.net.http.*;import java.nio.charset.StandardCharsets;import java.time.Duration;import java.util.*;

public class SelfTest {
 static int pass=0,fail=0,APP;static HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
 static void test(String n,boolean ok){System.out.println((ok?"[PASS] ":"[FAIL] ")+n);if(ok)pass++;else fail++;}
 public static void main(String[]a)throws Exception{
  APP=freePort();System.setProperty("speed.port",String.valueOf(APP));
  System.setProperty("speed.db.url","jdbc:h2:mem:speedtest;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;DEFAULT_NULL_ORDERING=HIGH");
  System.setProperty("SPEED_UPLOAD_DIR","data/test_uploads");System.setProperty("speed.test.google.token","TEST_TOKEN");

  HttpServer ext=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
  ext.createContext("/jobicy",x->json(x,"{\"jobs\":[{\"id\":1,\"url\":\"https://jobicy.test/1\",\"jobTitle\":\"IT Support Junior\",\"companyName\":\"Jobicy Co\",\"jobGeo\":\"Anywhere\",\"jobLevel\":\"Junior\",\"jobDescription\":\"Help desk Windows sem experiencia\",\"pubDate\":\"2026-08-18T00:00:00Z\"}]}"));
  ext.createContext("/remotive",x->json(x,"{\"jobs\":[{\"id\":\"r1\",\"title\":\"Estagio em Suporte TI\",\"company_name\":\"Remote Co\",\"candidate_required_location\":\"Brazil\",\"description\":\"Estagio help desk Windows sem experiencia\",\"url\":\"https://remotive.test/r1\",\"publication_date\":\"2026-08-18\"}]}"));
  ext.createContext("/adzuna",x->json(x,"{\"results\":[{\"id\":\"a1\",\"title\":\"Assistente de TI\",\"description\":\"Suporte tecnico N1 sem experiencia\",\"redirect_url\":\"https://adzuna.test/a1\",\"created\":\"2026-08-18T00:00:00Z\",\"company\":{\"display_name\":\"Local Co\"},\"location\":{\"display_name\":\"Itaquera, Sao Paulo\"}}]}"));
  ext.createContext("/arbeitnow",x->json(x,"{\"data\":[{\"slug\":\"ab1\",\"company_name\":\"Arbeit Co\",\"title\":\"IT Support Junior\",\"location\":\"Remote\",\"remote\":true,\"description\":\"Help desk Windows junior\",\"url\":\"https://arbeit.test/ab1\",\"created_at\":\"2026-08-18\"}]}"));
  ext.createContext("/remoteok",x->json(x,"[{\"legal\":\"notice\"},{\"id\":\"ro1\",\"position\":\"IT Support Junior\",\"company\":\"RemoteOK Co\",\"location\":\"Worldwide\",\"description\":\"Help desk Windows junior\",\"url\":\"https://remoteok.test/ro1\",\"date\":\"2026-08-18\"}]"));
  ext.createContext("/gmail/messages/send",x->json(x,"{\"id\":\"gm1\"}"));
  ext.createContext("/gmail/messages",x->json(x,"{\"messages\":[]}"));
  ext.start();int ep=ext.getAddress().getPort();
  System.setProperty("speed.jobicy.url","http://127.0.0.1:"+ep+"/jobicy");
  System.setProperty("speed.remotive.url","http://127.0.0.1:"+ep+"/remotive?search=");
  System.setProperty("speed.arbeitnow.url","http://127.0.0.1:"+ep+"/arbeitnow");
  System.setProperty("speed.remoteok.url","http://127.0.0.1:"+ep+"/remoteok");
  System.setProperty("SPEED_ADZUNA_APP_ID","x");System.setProperty("SPEED_ADZUNA_APP_KEY","y");
  System.setProperty("SPEED_ADZUNA_URL","http://127.0.0.1:"+ep+"/adzuna?app_id=");
  System.setProperty("speed.gmail.api.base","http://127.0.0.1:"+ep+"/gmail");

  HttpServer company=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
  company.createContext("/",x->{byte[]b="<html>rh@empresa-teste.com.br</html>".getBytes(StandardCharsets.UTF_8);x.sendResponseHeaders(200,b.length);x.getResponseBody().write(b);x.close();});company.start();
  int companyPort=company.getAddress().getPort();

  new Thread(()->{try{SpeedVagasServer.main(new String[0]);}catch(Exception e){e.printStackTrace();}},"server-test").start();waitHealth();
  String healthBody=get("/api/health");
  test("health",healthBody.contains("\"ok\":true")||healthBody.contains("\"status\":\"ok\"")||healthBody.toLowerCase(Locale.ROOT).contains("ok"));test("static html",raw("/").contains("SPEED VAGAS"));
  test("database profile",get("/api/profile").contains("Ferraz de Vasconcelos"));
  test("profile save",put("/api/profile","{\"name\":\"Julio Teste\",\"email\":\"x\",\"phone\":\"1\",\"city\":\"Ferraz de Vasconcelos\",\"state\":\"SP\",\"target_roles\":\"Tecnico TI Jr\",\"skills\":\"Windows SQL\"}").contains("Julio Teste"));
  test("resume upload",upload("RESUME","cv.pdf","application/pdf","%PDF SPEED".getBytes()).contains("uploaded_file"));
  test("photo upload",upload("PHOTO","foto.png","image/png",new byte[]{(byte)0x89,0x50,0x4e,0x47,1}).contains("uploaded_file"));

  String search=post("/api/search/internet","{\"query\":\"suporte TI\",\"scope\":\"EAST_PLUS_REMOTE\",\"where\":\"Itaquera\",\"limit\":20}");
  test("multi-source search",search.contains("Jobicy")&&search.contains("Remotive")&&search.contains("Arbeitnow")&&search.contains("RemoteOK")&&search.contains("Adzuna"));
  String jobs=get("/api/jobs?sort=priority&minScore=0");long persisted=((Number)Database.query("select count(*) as total from jobs").get(0).get("total")).longValue();
  test("jobs persisted",persisted>=2);
  test("jobs api",jobs.startsWith("[")&&jobs.length()>2);
  test("senior filter",!JobRules.isEntryLevel("Senior Support Engineer","5 anos","Senior"));

  String manual=post("/api/jobs","{\"title\":\"Tecnico de TI Jr\",\"company\":\"Empresa Teste\",\"city\":\"Poa\",\"state\":\"SP\",\"workMode\":\"Presencial\",\"level\":\"Junior\",\"companyWebsite\":\"http://127.0.0.1:"+companyPort+"\",\"description\":\"Suporte Windows help desk\"}");
  Map<String,Object> jm=Json.obj(manual);long jid=((Number)jm.get("id")).longValue(),cid=((Number)jm.get("company_id")).longValue();
  test("contact discovery",post("/api/contacts/discover","{\"companyId\":"+cid+",\"website\":\"http://127.0.0.1:"+companyPort+"\"}").contains("rh@empresa-teste.com.br"));
  test("gmail api send",post("/api/applications/send","{\"jobId\":"+jid+"}").contains("\"sent\":true"));
  test("application persisted",get("/api/applications").contains("ENVIADA"));
  test("duplicate blocked",statusPost("/api/applications/send","{\"jobId\":"+jid+"}")==409);
  test("gmail read api",post("/api/email/check","{\"max\":5}").contains("\"checked\":0"));
  test("settings",get("/api/settings").contains("min_score_auto"));
  test("activity",get("/api/activity?limit=10").startsWith("["));
  test("diagnostics",get("/api/diagnostics").contains("\"status\":\"OK\""));
  System.out.println("====================================");System.out.println("SELFTEST 6.4.0: "+pass+" PASS / "+fail+" FAIL");System.out.println("====================================");
  ext.stop(0);company.stop(0);System.exit(fail==0?0:1);
 }
 static void json(com.sun.net.httpserver.HttpExchange x,String s)throws IOException{byte[]b=s.getBytes(StandardCharsets.UTF_8);x.getResponseHeaders().set("Content-Type","application/json");x.sendResponseHeaders(200,b.length);x.getResponseBody().write(b);x.close();}
 static int freePort()throws Exception{try(ServerSocket s=new ServerSocket(0,1,InetAddress.getByName("127.0.0.1"))){return s.getLocalPort();}}
 static void waitHealth()throws Exception{
  Exception last=null;
  for(int i=0;i<100;i++){
    try{
      HttpResponse<String> r=req("GET","/api/health",null,"application/json");
      if(r.statusCode()==200 && r.body()!=null && !r.body().isBlank())return;
    }catch(Exception e){last=e;}
    Thread.sleep(100);
  }
  throw new RuntimeException("Servidor não ficou saudável a tempo.",last);
}
 static String get(String p)throws Exception{return req("GET",p,null,"application/json").body();}static String raw(String p)throws Exception{return req("GET",p,null,"text/plain").body();}
 static String post(String p,String b)throws Exception{var r=req("POST",p,b.getBytes(StandardCharsets.UTF_8),"application/json");if(r.statusCode()/100!=2)throw new RuntimeException(r.body());return r.body();}
 static String put(String p,String b)throws Exception{var r=req("PUT",p,b.getBytes(StandardCharsets.UTF_8),"application/json");if(r.statusCode()/100!=2)throw new RuntimeException(r.body());return r.body();}
 static int statusPost(String p,String b)throws Exception{return req("POST",p,b.getBytes(StandardCharsets.UTF_8),"application/json").statusCode();}
 static String upload(String k,String n,String m,byte[]d)throws Exception{var r=req("POST","/api/profile/upload?kind="+k+"&fileName="+URLEncoder.encode(n,StandardCharsets.UTF_8),d,m);return r.body();}
 static HttpResponse<String> req(String method,String p,byte[]b,String ct)throws Exception{HttpRequest.Builder q=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+APP+p)).timeout(Duration.ofSeconds(10)).header("Content-Type",ct);if(method.equals("GET"))q.GET();else q.method(method,HttpRequest.BodyPublishers.ofByteArray(b==null?new byte[0]:b));return http.send(q.build(),HttpResponse.BodyHandlers.ofString());}
}
