package org.example;

import java.math.BigDecimal;
import java.util.*;

public record CurriculumData(int schemaVersion,List<Degree> degrees,List<Equivalence> equivalences,Preferences preferences) {
 public CurriculumData {degrees=List.copyOf(degrees);equivalences=List.copyOf(equivalences);}
 public static CurriculumData empty(){return new CurriculumData(1,List.of(),List.of(),Preferences.defaults());}
 public record Row(int order,String code,String name,BigDecimal credit,BigDecimal ects,boolean group) { @Override public String toString(){return code+" · "+name;} }
 public record Semester(int number,List<Row> rows,BigDecimal credit,BigDecimal ects) {public Semester{rows=List.copyOf(rows);}}
 public record Model(String title,List<Semester> semesters,Map<String,List<Row>> groups) {
  public Model{semesters=List.copyOf(semesters);var copy=new LinkedHashMap<String,List<Row>>();groups.forEach((k,v)->copy.put(k,List.copyOf(v)));groups=Collections.unmodifiableMap(copy);}
  public List<Row> members(Row row){return groups.getOrDefault(row.code(),List.of(row));}
  public Map<String,Row> catalog(){Map<String,Row> result=new LinkedHashMap<>();semesters.forEach(s->s.rows().stream().filter(r->!groups.containsKey(r.code())).forEach(r->result.put(r.code(),r)));groups.values().forEach(list->list.forEach(r->result.put(r.code(),r)));return result;}
 }
 public record Decision(String status,String choice,String grade,String note,List<String> preferred,List<String> excluded) {
  public Decision{preferred=List.copyOf(preferred);excluded=List.copyOf(excluded);}
  public static Decision initial(boolean eligible){return new Decision(eligible?"prefer":"skip","auto","","",List.of(),List.of());}
 }
 public record Degree(String id,String name,int semester,boolean reviewed,String source,Model curriculum,Map<String,Decision> decisions) {
  public Degree{decisions=Collections.unmodifiableMap(new LinkedHashMap<>(decisions));}
  public Decision decision(Semester s,Row r){return decisions.getOrDefault(key(s,r),Decision.initial(s.number()<=semester));}
  @Override public String toString(){return name;}
 }
 public record Equivalence(String a,String b) { @Override public String toString(){return a+" ↔ "+b+" · Onaylandı";} }
 public record Block(int day,String start,String end,String mode){}
 public record Rule(String priority,String teacherMode,List<String> teachers){public Rule{teachers=List.copyOf(teachers);}}
 public record Preferences(String objective,String departmentPriority,String earliest,String latest,String windowMode,
                           Map<Integer,String> days,List<Block> blocks,int maxCourses,BigDecimal maxCredit,BigDecimal maxEcts,int maxDailyMinutes,Map<String,Rule> rules){
  public Preferences{days=Map.copyOf(days);blocks=List.copyOf(blocks);rules=Collections.unmodifiableMap(new LinkedHashMap<>(rules));}
  public static Preferences defaults(){return new Preferences("days","balanced","08:00","22:00","soft",Map.of(),List.of(),0,null,null,0,Map.of());}
 }
 public static String key(Semester s,Row r){return s.number()+":"+r.order();}
 public static String code(String code){return code.trim().toUpperCase(Locale.ROOT);}
}
