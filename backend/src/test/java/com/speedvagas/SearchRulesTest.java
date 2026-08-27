package com.speedvagas;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class SearchRulesTest {
    @Test void companyAgeDoesNotLookLikeRequiredExperience(){
        assertTrue(JobRules.isEntryLevel("Assistente Administrativo", "Empresa no mercado há 5 anos. Sem requisito de experiência.", ""));
    }

    @Test void explicitThreeYearsRequirementIsBlocked(){
        assertFalse(JobRules.isEntryLevel("Analista de Suporte", "Requisito: experiência mínima de 3 anos com suporte.", ""));
        assertFalse(JobRules.isEntryLevel("Support Analyst", "At least 4 years of experience required.", ""));
    }

    @Test void relevanceUsesConfiguredProfileInsteadOfFixedTiList(){
        assertTrue(JobRules.isRelevantToProfile("Assistente Administrativo", "Excel", "Assistente Administrativo Júnior", "Rotinas administrativas"));
        assertFalse(JobRules.isRelevantToProfile("Assistente Administrativo", "Excel", "Engenheiro Civil", "Obras e projetos"));
    }

    @Test void smartQueriesComeFromProfile(){
        Map<String,Object> p=new LinkedHashMap<>();
        p.put("target_roles", "Assistente Administrativo;Auxiliar Financeiro");
        p.put("skills", "Excel;SAP");
        List<String> q=Services.buildSearchQueries(p);
        assertEquals("Assistente Administrativo",q.get(0));
        assertTrue(q.contains("Auxiliar Financeiro"));
        assertFalse(q.contains("suporte TI"));
    }

    @Test void skillCombinationAndTitleWeightImproveScore(){
        int strong=JobRules.compatibility("Analista de Dados Jr","SQL;Power BI;Excel","Analista de Dados Jr SQL","Power BI Excel SQL","SQL Power BI","Júnior");
        int weak=JobRules.compatibility("Analista de Dados Jr","SQL;Power BI;Excel","Assistente","Conhecimento básico","","Júnior");
        assertTrue(strong>weak);
        assertTrue(strong>=80);
    }

    @Test void emptyProfileStillHasSafeFallback(){
        assertFalse(Services.buildSearchQueries(Map.of()).isEmpty());
    }
}
