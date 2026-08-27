CREATE TABLE IF NOT EXISTS candidate_profile (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 name VARCHAR(180) NOT NULL, email VARCHAR(180), phone VARCHAR(60), city VARCHAR(120), state VARCHAR(20),
 target_roles CLOB, skills CLOB, radius_km DECIMAL(8,2) DEFAULT 30, photo_path VARCHAR(500), resume_path VARCHAR(500), created_at TIMESTAMP, updated_at TIMESTAMP
);
CREATE TABLE IF NOT EXISTS candidate_documents (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, candidate_id BIGINT NOT NULL, document_type VARCHAR(30) NOT NULL,
 file_name VARCHAR(255), mime_type VARCHAR(100), file_path VARCHAR(500), file_size BIGINT, created_at TIMESTAMP,
 CONSTRAINT fk_doc_candidate FOREIGN KEY(candidate_id) REFERENCES candidate_profile(id)
);
CREATE TABLE IF NOT EXISTS companies (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, name VARCHAR(220) NOT NULL, website VARCHAR(500), domain VARCHAR(255), city VARCHAR(120), state VARCHAR(30), created_at TIMESTAMP
);
CREATE TABLE IF NOT EXISTS jobs (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, company_id BIGINT NOT NULL, source VARCHAR(60) NOT NULL, external_id VARCHAR(255), title VARCHAR(255) NOT NULL,
 city VARCHAR(160), state VARCHAR(40), work_mode VARCHAR(40), level VARCHAR(60), salary_text VARCHAR(120), description CLOB, requirements CLOB, url VARCHAR(1000),
 published_at VARCHAR(40), distance_km DECIMAL(8,2) DEFAULT 0, compatibility_score INT DEFAULT 0, priority_score DECIMAL(8,2) DEFAULT 0,
 created_at TIMESTAMP, updated_at TIMESTAMP, CONSTRAINT fk_job_company FOREIGN KEY(company_id) REFERENCES companies(id)
);
CREATE UNIQUE INDEX IF NOT EXISTS ux_jobs_source_external ON jobs(source,external_id);
CREATE INDEX IF NOT EXISTS ix_jobs_score ON jobs(compatibility_score,priority_score);
CREATE TABLE IF NOT EXISTS company_contacts (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, company_id BIGINT NOT NULL, contact_type VARCHAR(30), contact_value VARCHAR(255), role_label VARCHAR(80),
 source_url VARCHAR(1000), is_public BOOLEAN DEFAULT TRUE, confidence INT DEFAULT 50, created_at TIMESTAMP,
 CONSTRAINT fk_contact_company FOREIGN KEY(company_id) REFERENCES companies(id)
);
CREATE UNIQUE INDEX IF NOT EXISTS ux_contact_value ON company_contacts(company_id,contact_value);
CREATE TABLE IF NOT EXISTS search_runs (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, query_text VARCHAR(255), provider VARCHAR(80), status VARCHAR(30), items_found INT DEFAULT 0, items_added INT DEFAULT 0,
 error_message CLOB, started_at TIMESTAMP, finished_at TIMESTAMP
);
CREATE TABLE IF NOT EXISTS applications (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, job_id BIGINT NOT NULL, status VARCHAR(40), channel VARCHAR(40), contact_value VARCHAR(255), message_body CLOB,
 created_at TIMESTAMP, updated_at TIMESTAMP, CONSTRAINT fk_app_job FOREIGN KEY(job_id) REFERENCES jobs(id)
);
CREATE UNIQUE INDEX IF NOT EXISTS ux_application_job ON applications(job_id);
CREATE TABLE IF NOT EXISTS ai_runs (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, agent_name VARCHAR(80), mode VARCHAR(50), input_summary CLOB, output_summary CLOB, status VARCHAR(30), created_at TIMESTAMP
);
CREATE TABLE IF NOT EXISTS app_settings (
 setting_key VARCHAR(120) PRIMARY KEY, setting_value CLOB, updated_at TIMESTAMP
);
CREATE TABLE IF NOT EXISTS audit_events (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, event_type VARCHAR(80), entity_type VARCHAR(80), entity_id VARCHAR(80), details CLOB, created_at TIMESTAMP
);


CREATE TABLE IF NOT EXISTS activity_events (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 event_type VARCHAR(80) NOT NULL, entity_type VARCHAR(50), entity_id VARCHAR(100),
 title VARCHAR(255), details CLOB, status VARCHAR(30), created_at TIMESTAMP
);
CREATE INDEX IF NOT EXISTS ix_activity_created ON activity_events(created_at);
CREATE TABLE IF NOT EXISTS notifications (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 notification_type VARCHAR(80), title VARCHAR(255), message CLOB, severity VARCHAR(30),
 reference_type VARCHAR(50), reference_id VARCHAR(100), resolved BOOLEAN DEFAULT FALSE,
 created_at TIMESTAMP, resolved_at TIMESTAMP
);
CREATE INDEX IF NOT EXISTS ix_notifications_open ON notifications(resolved,severity,created_at);
CREATE TABLE IF NOT EXISTS email_events (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, external_id VARCHAR(500), sender VARCHAR(500), subject VARCHAR(1000),
 message_date VARCHAR(200), classification VARCHAR(50), status VARCHAR(30), created_at TIMESTAMP
);
CREATE UNIQUE INDEX IF NOT EXISTS ux_email_external ON email_events(external_id);
CREATE TABLE IF NOT EXISTS google_search_quota (
 quota_day VARCHAR(20) PRIMARY KEY, used_count INT DEFAULT 0, updated_at TIMESTAMP
);
CREATE TABLE IF NOT EXISTS job_decisions (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, source VARCHAR(80), external_id VARCHAR(500), job_id BIGINT,
 title VARCHAR(255), company_name VARCHAR(255), reason_code VARCHAR(80), reason_message CLOB, created_at TIMESTAMP
);
CREATE INDEX IF NOT EXISTS ix_job_decisions_created ON job_decisions(created_at);
CREATE TABLE IF NOT EXISTS job_feedback (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, job_id BIGINT NOT NULL, feedback_type VARCHAR(80), note CLOB, created_at TIMESTAMP,
 CONSTRAINT fk_feedback_job FOREIGN KEY(job_id) REFERENCES jobs(id)
);
CREATE INDEX IF NOT EXISTS ix_job_feedback_job ON job_feedback(job_id,feedback_type);
