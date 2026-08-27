package com.speedvagas;

import java.util.*;

public final class Json {
    private Json() {}

    public static Object parse(String s) {
        return new Parser(s == null ? "" : s).parseValue();
    }
    @SuppressWarnings("unchecked")
    public static Map<String,Object> obj(String s) {
        Object v = parse(s); return v instanceof Map ? (Map<String,Object>)v : new LinkedHashMap<>();
    }
    public static String stringify(Object v) {
        if (v == null) return "null";
        if (v instanceof String) return quote((String)v);
        if (v instanceof Number || v instanceof Boolean) return v.toString();
        if (v instanceof Map<?,?> m) {
            StringJoiner j = new StringJoiner(",", "{", "}");
            for (var e : m.entrySet()) j.add(quote(String.valueOf(e.getKey())) + ":" + stringify(e.getValue()));
            return j.toString();
        }
        if (v instanceof Iterable<?> it) {
            StringJoiner j = new StringJoiner(",", "[", "]"); for (Object x: it) j.add(stringify(x)); return j.toString();
        }
        return quote(v.toString());
    }
    public static String quote(String s) {
        StringBuilder b = new StringBuilder("\"");
        for (char c : s.toCharArray()) switch(c) {
            case '"' -> b.append("\\\""); case '\\' -> b.append("\\\\"); case '\b' -> b.append("\\b");
            case '\f' -> b.append("\\f"); case '\n' -> b.append("\\n"); case '\r' -> b.append("\\r"); case '\t' -> b.append("\\t");
            default -> { if (c < 32) b.append(String.format("\\u%04x", (int)c)); else b.append(c); }
        }
        return b.append('"').toString();
    }
    public static String str(Map<String,Object> m, String k, String d) { Object v=m.get(k); return v==null?d:String.valueOf(v); }
    public static int integer(Map<String,Object> m,String k,int d){ try{return ((Number)m.get(k)).intValue();}catch(Exception e){try{return Integer.parseInt(str(m,k,""));}catch(Exception x){return d;}}}
    public static long lng(Map<String,Object> m,String k,long d){ try{return ((Number)m.get(k)).longValue();}catch(Exception e){try{return Long.parseLong(str(m,k,""));}catch(Exception x){return d;}}}
    public static double dbl(Map<String,Object> m,String k,double d){ try{return ((Number)m.get(k)).doubleValue();}catch(Exception e){try{return Double.parseDouble(str(m,k,""));}catch(Exception x){return d;}}}
    public static boolean bool(Map<String,Object>m,String k,boolean d){Object v=m.get(k); return v==null?d:Boolean.parseBoolean(String.valueOf(v));}

    private static final class Parser {
        private final String s; private int i;
        Parser(String s){this.s=s;}
        Object parseValue(){ ws(); if(i>=s.length())return null; char c=s.charAt(i); if(c=='{')return object(); if(c=='[')return array(); if(c=='"')return string(); if(c=='t'){i+=4;return true;} if(c=='f'){i+=5;return false;} if(c=='n'){i+=4;return null;} return number(); }
        Map<String,Object> object(){ Map<String,Object>m=new LinkedHashMap<>(); i++; ws(); if(peek('}')){i++;return m;} while(i<s.length()){ws();String k=string();ws();expect(':');Object v=parseValue();m.put(k,v);ws();if(peek('}')){i++;break;}expect(',');}return m;}
        List<Object> array(){List<Object>a=new ArrayList<>();i++;ws();if(peek(']')){i++;return a;}while(i<s.length()){a.add(parseValue());ws();if(peek(']')){i++;break;}expect(',');}return a;}
        String string(){expect('"');StringBuilder b=new StringBuilder();while(i<s.length()){char c=s.charAt(i++);if(c=='"')break;if(c=='\\'){if(i>=s.length())break;char e=s.charAt(i++);switch(e){case '"'->b.append('"');case '\\'->b.append('\\');case '/'->b.append('/');case 'b'->b.append('\b');case 'f'->b.append('\f');case 'n'->b.append('\n');case 'r'->b.append('\r');case 't'->b.append('\t');case 'u'->{String h=s.substring(i,Math.min(i+4,s.length()));i+=4;b.append((char)Integer.parseInt(h,16));}default->b.append(e);}}else b.append(c);}return b.toString();}
        Number number(){int st=i;while(i<s.length()&&"-+0123456789.eE".indexOf(s.charAt(i))>=0)i++;String n=s.substring(st,i);try{return n.contains(".")||n.contains("e")||n.contains("E")?Double.parseDouble(n):Long.parseLong(n);}catch(Exception e){return 0;}}
        void ws(){while(i<s.length()&&Character.isWhitespace(s.charAt(i)))i++;}
        boolean peek(char c){return i<s.length()&&s.charAt(i)==c;}
        void expect(char c){ws();if(i>=s.length()||s.charAt(i)!=c)throw new IllegalArgumentException("JSON inválido na posição "+i);i++;}
    }
}
