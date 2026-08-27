package com.speedvagas;

import java.util.*;
public class ProviderParsingTest {
  static int p=0,f=0;static void t(String n,boolean x){System.out.println((x?"[PASS] ":"[FAIL] ")+n);if(x)p++;else f++;}
  public static void main(String[]args){
    String j="{\"jobs\":[{\"id\":11,\"url\":\"https://jobicy.test/11\",\"jobTitle\":\"IT Support Junior\",\"companyName\":\"Acme\",\"jobGeo\":\"Anywhere\",\"jobLevel\":\"Junior\",\"jobDescription\":\"Help desk Windows sem experiencia\",\"pubDate\":\"2026-08-18T10:00:00Z\"}]}";
    var jl=PublicJobSources.parseJobicy(j);t("Jobicy parse",jl.size()==1&&"JOBICY".equals(jl.get(0).get("source")));t("Jobicy title","IT Support Junior".equals(jl.get(0).get("title")));
    String r="{\"jobs\":[{\"id\":\"r1\",\"title\":\"Estagio em Suporte TI\",\"company_name\":\"Remote Co\",\"candidate_required_location\":\"Brazil\",\"description\":\"Suporte Windows help desk\",\"url\":\"https://remotive.test/r1\",\"publication_date\":\"2026-08-18\"}]}";
    var rl=PublicJobSources.parseRemotive(r);t("Remotive parse",rl.size()==1&&"REMOTIVE".equals(rl.get(0).get("source")));t("Remotive location","Brazil".equals(rl.get(0).get("city")));
    String z="{\"results\":[{\"id\":\"a1\",\"title\":\"Assistente de TI\",\"description\":\"Suporte tecnico N1\",\"redirect_url\":\"https://adzuna.test/a1\",\"created\":\"2026-08-18T00:00:00Z\",\"company\":{\"display_name\":\"Local Co\"},\"location\":{\"display_name\":\"Itaquera, Sao Paulo\"}}]}";
    var al=PublicJobSources.parseAdzuna(z);t("Adzuna parse",al.size()==1&&"Local Co".equals(al.get(0).get("company")));
    String a="{\"data\":[{\"slug\":\"j1\",\"company_name\":\"Tech Co\",\"title\":\"IT Support Junior\",\"location\":\"Remote\",\"remote\":true,\"description\":\"Windows help desk\",\"url\":\"https://arbeit.test/j1\",\"created_at\":\"2026-08-20\"}]}";
    var ar=PublicJobSources.parseArbeitnow(a);t("Arbeitnow parse",ar.size()==1&&"ARBEITNOW".equals(ar.get(0).get("source")));
    String o="[{\"legal\":\"notice\"},{\"id\":\"r2\",\"position\":\"Help Desk Junior\",\"company\":\"RemoteOK Co\",\"location\":\"Worldwide\",\"description\":\"Support Windows\",\"url\":\"https://remoteok.test/r2\",\"date\":\"2026-08-20\"}]";
    var ro=PublicJobSources.parseRemoteOk(o);t("RemoteOK parse",ro.size()==1&&"REMOTEOK".equals(ro.get(0).get("source")));
    if(f>0)System.exit(1);System.out.println("[RESULT] "+p+" testes de providers passaram.");
  }
}
