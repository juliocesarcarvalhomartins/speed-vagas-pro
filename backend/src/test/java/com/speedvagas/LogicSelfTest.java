package com.speedvagas;

public class LogicSelfTest {
    static int pass=0,fail=0;
    static void test(String name,boolean ok){
        System.out.println((ok?"[PASS] ":"[FAIL] ")+name);
        if(ok)pass++;else fail++;
    }
    public static void main(String[]args){
        test("Junior aceito",JobRules.isEntryLevel("Técnico de TI Jr","",""));
        test("Estagio aceito",JobRules.isEntryLevel("Estágio em TI","",""));
        test("Tier III bloqueado",!JobRules.isEntryLevel("Tier III Service Desk Engineer","",""));
        test("Senior bloqueado",!JobRules.isEntryLevel("Senior Data Analyst","",""));
        test("N2 bloqueado",!JobRules.isEntryLevel("Suporte N2","",""));
        test("TI relevante",JobRules.isCareerRelevant("Assistente de TI","suporte a usuarios"));
        test("Fora da area bloqueado",!JobRules.isCareerRelevant("Assistente Administrativo","financeiro"));
        test("Entrevista detectada",JobRules.looksLikeInterview("Convite para entrevista","Gostaríamos de agendar"));
        test("Reprovacao detectada","REPROVACAO".equals(JobRules.classifyEmail("Retorno do processo","Infelizmente não seguiremos")));
        int score=JobRules.compatibility("Técnico de TI Jr,Suporte N1","Windows,Hardware,SQL","Técnico de TI Jr","Suporte Windows e hardware","Windows","Júnior");
        test("Compatibilidade coerente",score>=70);
        double near=JobRules.priority(80,5,false,1),far=JobRules.priority(80,50,false,1);
        test("Distancia prioriza vaga proxima",near>far);
        if(fail>0){System.err.println("[RESULT] "+fail+" falha(s)");System.exit(1);}
        System.out.println("[RESULT] "+pass+" testes passaram.");
    }
}
