package com.speedvagas;

import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class SchemaCompatibilityTest {
    @Test void h2AndMysqlContainSameCoreTables() throws Exception {
        String h2=Files.readString(Path.of("sql/schema_h2.sql")).toLowerCase(Locale.ROOT);
        String my=Files.readString(Path.of("sql/schema_mysql.sql")).toLowerCase(Locale.ROOT);
        for(String table:List.of("candidate_profile","candidate_documents","companies","jobs","company_contacts","search_runs","applications","ai_runs","app_settings","audit_events","activity_events","notifications","email_events","google_search_quota","job_decisions","job_feedback")){
            assertTrue(h2.contains("table if not exists "+table)||h2.contains("table "+table),"H2 sem "+table);
            assertTrue(my.contains("table if not exists "+table)||my.contains("table "+table),"MySQL sem "+table);
        }
    }
    @Test void criticalColumnsExistInBothSchemas() throws Exception {
        String h2=Files.readString(Path.of("sql/schema_h2.sql")).toLowerCase(Locale.ROOT);
        String my=Files.readString(Path.of("sql/schema_mysql.sql")).toLowerCase(Locale.ROOT);
        for(String col:List.of("compatibility_score","priority_score","contact_value","message_body","reason_code","feedback_type","used_count")){
            assertTrue(h2.contains(col),"H2 sem coluna "+col);assertTrue(my.contains(col),"MySQL sem coluna "+col);
        }
    }
}
