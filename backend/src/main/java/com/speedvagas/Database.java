package com.speedvagas;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.*;
import java.util.*;

public final class Database {
    private static final String DEFAULT_ROLES = "Assistente de TI,Técnico de TI Jr,Suporte N1,Help Desk,Service Desk,Analista de Suporte Jr,Assistente de Dados,Analista de Dados Jr,Estágio em TI,Estágio em Dados";
    private static final String DEFAULT_SKILLS = "Suporte técnico,Montagem e manutenção,Windows,Hardware,Formatação,Instalação de programas,Atendimento ao cliente,Python,Pandas,SQL,Power BI,Excel,Java,Lógica de programação";
    private static String url(){String p=System.getProperty("speed.db.url");if(p!=null&&!p.isBlank())return p;return "jdbc:h2:./data/speedvagas;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH";}
    private Database() {}
    public static Connection open() throws SQLException { return DriverManager.getConnection(url(), "sa", ""); }
    public static void init() throws Exception {
        Class.forName("org.h2.Driver");
        String sql = Files.readString(Path.of("sql/schema_h2.sql"), StandardCharsets.UTF_8);
        try(Connection c=open(); Statement st=c.createStatement()) {
            for(String part: sql.split(";\\s*(?:\\r?\\n|$)")) if(!part.isBlank()) st.execute(part);
        }
        seed();
        repairLegacyProfileText();
    }
    private static void seed() throws SQLException {
        try(Connection c=open()) {
            try(PreparedStatement ps=c.prepareStatement("select count(*) from candidate_profile"); ResultSet r=ps.executeQuery()) {
                r.next(); if(r.getInt(1)==0) {
                    try(PreparedStatement x=c.prepareStatement("insert into candidate_profile(name,email,phone,city,state,target_roles,skills,radius_km,resume_path,created_at,updated_at) values(?,?,?,?,?,?,?,?,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)")){
                        x.setString(1,env("SPEED_PROFILE_NAME",""));
                        x.setString(2,env("SPEED_PROFILE_EMAIL",""));
                        x.setString(3,env("SPEED_PROFILE_PHONE",""));
                        x.setString(4,env("SPEED_PROFILE_CITY",""));
                        x.setString(5,env("SPEED_PROFILE_STATE",""));
                        x.setString(6,env("SPEED_PROFILE_ROLES",DEFAULT_ROLES));
                        x.setString(7,env("SPEED_PROFILE_SKILLS",DEFAULT_SKILLS));
                        x.setDouble(8,envDouble("SPEED_PROFILE_RADIUS_KM",30));
                        x.setString(9,env("SPEED_PROFILE_RESUME_PATH",""));
                        x.executeUpdate();
                    }
                }
            }
            try(PreparedStatement ps=c.prepareStatement("select count(*) from app_settings"); ResultSet r=ps.executeQuery()) { r.next(); if(r.getInt(1)==0){
                Map<String,String> settings=new LinkedHashMap<>();
                settings.put("min_score_auto",env("SPEED_MIN_SCORE_AUTO","65"));
                settings.put("max_auto_per_run",env("SPEED_MAX_AUTO_PER_RUN","5"));
                settings.put("max_auto_per_day",env("SPEED_MAX_AUTO_PER_DAY","10"));
                settings.put("auto_send_email",env("SPEED_AUTO_SEND_EMAIL","false"));
                settings.put("auto_send_min_score",env("SPEED_AUTO_SEND_MIN_SCORE","85"));
                settings.put("auto_send_daily_limit",env("SPEED_AUTO_SEND_DAILY_LIMIT","3"));
                settings.put("auto_manager_enabled",env("SPEED_AUTO_MANAGER_ENABLED","true"));
                settings.put("auto_manager_interval_minutes",env("SPEED_AUTO_MANAGER_INTERVAL_MINUTES","60"));
                settings.put("ai_mode",env("SPEED_AI_MODE","LOCAL"));
                settings.put("remote_brazil","true");
                settings.put("search_local","true");
                settings.put("auto_reply_simple","false");
                settings.put("email_monitor_interval_minutes",env("SPEED_EMAIL_MONITOR_INTERVAL_MINUTES","10"));
                settings.put("email_monitor_enabled",env("SPEED_EMAIL_MONITOR_ENABLED","true"));
                String sender=env("SPEED_GMAIL_SENDER_EMAIL","");
                if(!sender.isBlank()) settings.put("gmail_sender_email",sender);
                for(var e:settings.entrySet()) try(PreparedStatement x=c.prepareStatement("insert into app_settings(setting_key,setting_value,updated_at) values(?,?,CURRENT_TIMESTAMP)")){x.setString(1,e.getKey());x.setString(2,e.getValue());x.executeUpdate();}
            }}
        }
    }

    private static String env(String key,String def){
        String p=System.getProperty(key); if(p!=null&&!p.isBlank()) return p;
        String e=System.getenv(key); return e==null||e.isBlank()?def:e;
    }
    private static double envDouble(String key,double def){try{return Double.parseDouble(env(key,String.valueOf(def)));}catch(Exception e){return def;}}

    private static void repairLegacyProfileText() throws SQLException {
        try(Connection c=open(); PreparedStatement ps=c.prepareStatement("select id,target_roles,skills from candidate_profile order by id limit 1"); ResultSet r=ps.executeQuery()) {
            if(!r.next()) return;
            long id=r.getLong("id");
            String roles=r.getString("target_roles");
            String skills=r.getString("skills");
            boolean badRoles=roles!=null && roles.stripLeading().toLowerCase(Locale.ROOT).startsWith("clob:");
            boolean badSkills=skills!=null && skills.stripLeading().toLowerCase(Locale.ROOT).startsWith("clob:");
            if(badRoles||badSkills) try(PreparedStatement up=c.prepareStatement("update candidate_profile set target_roles=?,skills=?,updated_at=CURRENT_TIMESTAMP where id=?")){
                up.setString(1,badRoles?DEFAULT_ROLES:roles);
                up.setString(2,badSkills?DEFAULT_SKILLS:skills);
                up.setLong(3,id); up.executeUpdate();
            }
        }
    }
    private static Object value(ResultSet rs,int i) throws SQLException {
        Object v=rs.getObject(i);
        if(v instanceof Clob c){ return c.getSubString(1,(int)c.length()); }
        if(v instanceof Blob b){ return b.getBytes(1,(int)b.length()); }
        return v;
    }
    public static List<Map<String,Object>> query(String sql,Object...args) throws SQLException {
        try(Connection c=open(); PreparedStatement ps=c.prepareStatement(sql)){bind(ps,args);try(ResultSet rs=ps.executeQuery()){List<Map<String,Object>>out=new ArrayList<>();ResultSetMetaData md=rs.getMetaData();while(rs.next()){Map<String,Object>m=new LinkedHashMap<>();for(int i=1;i<=md.getColumnCount();i++)m.put(md.getColumnLabel(i),value(rs,i));out.add(m);}return out;}}
    }
    public static int update(String sql,Object...args) throws SQLException { try(Connection c=open(); PreparedStatement ps=c.prepareStatement(sql)){bind(ps,args);return ps.executeUpdate();} }
    public static long insert(String sql,Object...args) throws SQLException { try(Connection c=open();PreparedStatement ps=c.prepareStatement(sql,Statement.RETURN_GENERATED_KEYS)){bind(ps,args);ps.executeUpdate();try(ResultSet r=ps.getGeneratedKeys()){return r.next()?r.getLong(1):-1;}} }
    private static void bind(PreparedStatement ps,Object...args)throws SQLException{for(int i=0;i<args.length;i++)ps.setObject(i+1,args[i]);}
}
