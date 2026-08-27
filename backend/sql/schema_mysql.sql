CREATE DATABASE IF NOT EXISTS speed_vagas CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE speed_vagas;
CREATE TABLE candidate_profile (id BIGINT AUTO_INCREMENT PRIMARY KEY,name VARCHAR(180) NOT NULL,email VARCHAR(180),phone VARCHAR(60),city VARCHAR(120),state CHAR(2),target_roles TEXT,skills TEXT,radius_km DECIMAL(8,2) DEFAULT 30,photo_path VARCHAR(500),resume_path VARCHAR(500),created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP) ENGINE=InnoDB;
CREATE TABLE candidate_documents (id BIGINT AUTO_INCREMENT PRIMARY KEY,candidate_id BIGINT NOT NULL,document_type ENUM('RESUME','PHOTO','OTHER') NOT NULL,file_name VARCHAR(255),mime_type VARCHAR(100),file_path VARCHAR(500),file_size BIGINT,created_at DATETIME DEFAULT CURRENT_TIMESTAMP,CONSTRAINT fk_doc_candidate FOREIGN KEY(candidate_id) REFERENCES candidate_profile(id) ON DELETE CASCADE) ENGINE=InnoDB;
CREATE TABLE companies (id BIGINT AUTO_INCREMENT PRIMARY KEY,name VARCHAR(220) NOT NULL,website VARCHAR(500),domain VARCHAR(255),city VARCHAR(120),state CHAR(2),created_at DATETIME DEFAULT CURRENT_TIMESTAMP,INDEX ix_company_name(name)) ENGINE=InnoDB;
CREATE TABLE jobs (id BIGINT AUTO_INCREMENT PRIMARY KEY,company_id BIGINT NOT NULL,source VARCHAR(60) NOT NULL,external_id VARCHAR(255),title VARCHAR(255) NOT NULL,city VARCHAR(160),state VARCHAR(40),work_mode VARCHAR(40),level VARCHAR(60),salary_text VARCHAR(120),description MEDIUMTEXT,requirements MEDIUMTEXT,url VARCHAR(1000),published_at VARCHAR(40),distance_km DECIMAL(8,2) DEFAULT 0,compatibility_score TINYINT UNSIGNED DEFAULT 0,priority_score DECIMAL(8,2) DEFAULT 0,created_at DATETIME DEFAULT CURRENT_TIMESTAMP,updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,CONSTRAINT fk_job_company FOREIGN KEY(company_id) REFERENCES companies(id),UNIQUE KEY ux_jobs_source_external(source,external_id),INDEX ix_jobs_ranking(priority_score DESC,compatibility_score DESC),INDEX ix_jobs_location(state,city,work_mode)) ENGINE=InnoDB;
CREATE TABLE company_contacts (id BIGINT AUTO_INCREMENT PRIMARY KEY,company_id BIGINT NOT NULL,contact_type ENUM('EMAIL','PHONE','WHATSAPP','FORM') NOT NULL,contact_value VARCHAR(255) NOT NULL,role_label VARCHAR(80),source_url VARCHAR(1000),is_public BOOLEAN DEFAULT TRUE,confidence TINYINT UNSIGNED DEFAULT 50,created_at DATETIME DEFAULT CURRENT_TIMESTAMP,CONSTRAINT fk_contact_company FOREIGN KEY(company_id) REFERENCES companies(id) ON DELETE CASCADE,UNIQUE KEY ux_contact_value(company_id,contact_value),INDEX ix_contact_role(role_label,confidence)) ENGINE=InnoDB;
CREATE TABLE search_runs (id BIGINT AUTO_INCREMENT PRIMARY KEY,query_text VARCHAR(255),provider VARCHAR(80),status VARCHAR(30),items_found INT DEFAULT 0,items_added INT DEFAULT 0,error_message TEXT,started_at DATETIME,finished_at DATETIME,INDEX ix_search_runs(started_at,status)) ENGINE=InnoDB;
CREATE TABLE applications (id BIGINT AUTO_INCREMENT PRIMARY KEY,job_id BIGINT NOT NULL,status VARCHAR(40),channel VARCHAR(40),contact_value VARCHAR(255),message_body MEDIUMTEXT,created_at DATETIME DEFAULT CURRENT_TIMESTAMP,updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,CONSTRAINT fk_app_job FOREIGN KEY(job_id) REFERENCES jobs(id),UNIQUE KEY ux_application_job(job_id),INDEX ix_app_status(status,updated_at)) ENGINE=InnoDB;
CREATE TABLE ai_runs (id BIGINT AUTO_INCREMENT PRIMARY KEY,agent_name VARCHAR(80),mode VARCHAR(50),input_summary TEXT,output_summary TEXT,status VARCHAR(30),created_at DATETIME DEFAULT CURRENT_TIMESTAMP,INDEX ix_ai_runs(created_at,status)) ENGINE=InnoDB;
CREATE TABLE app_settings (setting_key VARCHAR(120) PRIMARY KEY,setting_value TEXT,updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP) ENGINE=InnoDB;
CREATE TABLE audit_events (id BIGINT AUTO_INCREMENT PRIMARY KEY,event_type VARCHAR(80),entity_type VARCHAR(80),entity_id VARCHAR(80),details JSON,created_at DATETIME DEFAULT CURRENT_TIMESTAMP,INDEX ix_audit(created_at,event_type)) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS activity_events (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,event_type VARCHAR(80) NOT NULL,entity_type VARCHAR(50),entity_id VARCHAR(100),
 title VARCHAR(255),details TEXT,status VARCHAR(30),created_at TIMESTAMP,
 INDEX ix_activity_created(created_at)
);
CREATE TABLE IF NOT EXISTS notifications (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,notification_type VARCHAR(80),title VARCHAR(255),message TEXT,severity VARCHAR(30),
 reference_type VARCHAR(50),reference_id VARCHAR(100),resolved BOOLEAN DEFAULT FALSE,created_at TIMESTAMP,resolved_at TIMESTAMP,
 INDEX ix_notifications_open(resolved,severity,created_at)
);
CREATE TABLE IF NOT EXISTS email_events (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,external_id VARCHAR(500) UNIQUE,sender VARCHAR(500),subject VARCHAR(1000),
 message_date VARCHAR(200),classification VARCHAR(50),status VARCHAR(30),created_at TIMESTAMP
);
CREATE TABLE IF NOT EXISTS google_search_quota (quota_day VARCHAR(20) PRIMARY KEY,used_count INT DEFAULT 0,updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS job_decisions (id BIGINT AUTO_INCREMENT PRIMARY KEY,source VARCHAR(80),external_id VARCHAR(500),job_id BIGINT,title VARCHAR(255),company_name VARCHAR(255),reason_code VARCHAR(80),reason_message TEXT,created_at DATETIME DEFAULT CURRENT_TIMESTAMP,INDEX ix_job_decisions_created(created_at)) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS job_feedback (id BIGINT AUTO_INCREMENT PRIMARY KEY,job_id BIGINT NOT NULL,feedback_type VARCHAR(80),note TEXT,created_at DATETIME DEFAULT CURRENT_TIMESTAMP,CONSTRAINT fk_feedback_job FOREIGN KEY(job_id) REFERENCES jobs(id),INDEX ix_job_feedback_job(job_id,feedback_type)) ENGINE=InnoDB;
