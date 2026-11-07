package org.example;

import java.io.*;import java.math.BigDecimal;import java.time.*;import java.util.*;
import static org.example.CurriculumData.*;
import static org.example.CourseData.*;

/** Bounded, deterministic search; one course fills at most one requirement per degree. */
public final class AdvancedCoursePlanner {
 public record Requirement(String id,String degree,String name,String code,String status,Set<String> codes,List<String> preferred,List<Section> choices,String unavailable){}
 public record Alternative(List<Section> sections,List<String> missing,Map<String,Integer> coverage,int days,int gaps,int teacherPenalty,int timePenalty,List<Long> score,String signature) {public Alternative{sections=List.copyOf(sections);missing=List.copyOf(missing);coverage=Map.copyOf(coverage);score=List.copyOf(score);} @Override public String toString(){return sections.size()+" ders · "+days+" gün · "+gaps+" dk ara · "+missing.size()+" eksik gereksinim";}}
 public record Outcome(List<Alternative> results,boolean limited,int nodes,List<String> diagnostics){}
 private final CurriculumData plan;private final Preferences prefs;private final List<Requirement> requirements;private final List<Lesson> fixed;
 private final List<Alternative> results=new ArrayList<>();private final List<Section> selected=new ArrayList<>();private final Map<String,Section> assignment=new HashMap<>();private int nodes;private boolean limited;private long deadline;private int maxNodes;
 private AdvancedCoursePlanner(CurriculumData plan,CourseData data,LocalDate start,LocalDate end)throws IOException {
  CurriculumStorageService.validate(plan);if(plan.degrees().isEmpty()||plan.degrees().stream().anyMatch(d->!d.reviewed()))throw new IOException("Müfredat ve ders geçmişini kontrol edip onayla.");if(start==null||end==null||end.isBefore(start)||end.isAfter(start.plusYears(2)))throw new IOException("Geçerli dönem tarihleri gerekli (en fazla iki yıl).");
  this.plan=plan;prefs=plan.preferences();fixed=CourseCatalogService.active(data,start,end).stream().filter(c->c.sectionId().isBlank()).flatMap(c->c.lessons().stream()).toList();
  for(int i=0;i<fixed.size();i++)for(int j=i+1;j<fixed.size();j++)if(CourseCatalogService.overlaps(fixed.get(i),fixed.get(j)))throw new IOException("Elle girilen sabit derslerin saatleri birbiriyle çakışıyor.");
  List<Requirement> reqs=new ArrayList<>();for(var d:plan.degrees()){
   Set<String> completed=new HashSet<>();for(var s:d.curriculum().semesters())for(var r:s.rows()){var p=d.decision(s,r);if(p.status().equals("done"))completed.add(p.choice().equals("auto")?r.code():p.choice());}
   for(var s:d.curriculum().semesters())for(var row:s.rows()){
    var p=d.decision(s,row);if(!List.of("required","prefer").contains(p.status()))continue;
    Set<String> codes=new LinkedHashSet<>();for(var member:d.curriculum().members(row))if(!completed.contains(member.code())&&!p.excluded().contains(member.code())&&(p.choice().equals("auto")||p.choice().equals(member.code())))codes.add(member.code());
    expand(codes,d.id());reqs.add(requirement(d.id()+":"+key(s,row),d.id(),d.name(),row.code(),p.status(),codes,p.preferred(),data));
   }
  }
  for(var rule:prefs.rules().entrySet())if(List.of("required","prefer").contains(rule.getValue().priority())){
   Set<String> codes=new LinkedHashSet<>(List.of(rule.getKey()));plan.equivalences().forEach(e->{if(e.a().equals(rule.getKey())||e.b().equals(rule.getKey())){codes.add(e.a());codes.add(e.b());}});
   reqs.add(requirement("rule:"+rule.getKey(),"extra","Ders tercihi",rule.getKey(),rule.getValue().priority(),codes,List.of(),data));
  }
  if(reqs.isEmpty())throw new IOException("Planlanacak ders yok; ders durumlarını veya ders önceliklerini düzenle.");if(reqs.size()>200)throw new IOException("Tek aramada en fazla 200 gereksinim planlanabilir.");
  reqs.sort(Comparator.comparing((Requirement r)->!r.status().equals("required")).thenComparingInt(r->r.choices().size()));requirements=List.copyOf(reqs);
 }
 private void expand(Set<String> codes,String degree){for(var pair:plan.equivalences())if(degree.equals("A")&&codes.contains(pair.a()))codes.add(pair.b());else if(degree.equals("B")&&codes.contains(pair.b()))codes.add(pair.a());}
 private String identity(String code){for(var pair:plan.equivalences())if(pair.a().equals(code)||pair.b().equals(code))return "eq:"+pair.a()+":"+pair.b();return code;}
 private Requirement requirement(String id,String degree,String name,String code,String status,Set<String> codes,List<String> preferred,CourseData data){
  List<Section> offered=data.sections().stream().filter(s->codes.contains(CurriculumData.code(s.code()))).map(this::credited).toList();List<Section> choices=offered.stream().filter(s->rejection(s).isEmpty()).toList();String reason=offered.isEmpty()?"CSV kataloğunda şube yok":String.join("; ",offered.stream().map(this::rejection).filter(s->!s.isEmpty()).distinct().toList());
  return new Requirement(id,degree,name,code,status,Set.copyOf(codes),preferred,choices,reason);
 }
 private Section credited(Section s){var values=plan.degrees().stream().map(d->d.curriculum().catalog().get(CurriculumData.code(s.code()))).filter(Objects::nonNull).toList();Set<BigDecimal> cr=new HashSet<>(),et=new HashSet<>();values.forEach(r->{cr.add(r.credit().stripTrailingZeros());et.add(r.ects().stripTrailingZeros());});return new Section(s.id(),s.code(),s.fullCode(),s.name(),s.instructor(),s.credit()!=null?s.credit():cr.size()==1?cr.iterator().next():null,s.ects()!=null?s.ects():et.size()==1?et.iterator().next():null,s.lessons());}
 private List<Rule> rules(Section s){return prefs.rules().entrySet().stream().filter(e->identity(e.getKey()).equals(identity(CurriculumData.code(s.code())))).map(Map.Entry::getValue).toList();}
 private String rejection(Section s){
  if(s.lessons().isEmpty())return "Saat bilgisi yok";
  for(int i=0;i<s.lessons().size();i++)for(int j=i+1;j<s.lessons().size();j++)if(CourseCatalogService.overlaps(s.lessons().get(i),s.lessons().get(j)))return "Şubenin kendi saatleri çakışıyor";
  if(s.lessons().stream().anyMatch(a->fixed.stream().anyMatch(b->CourseCatalogService.overlaps(a,b))))return "Elle girilen sabit dersle çakışıyor";
  for(var r:rules(s)){if(r.priority().equals("exclude"))return "Ders bu dönem dışarıda";if(r.teacherMode().equals("only")&&!r.teachers().contains(s.instructor()))return "Kesin hoca koşulu karşılanmıyor";}
  for(var l:s.lessons()){
   if(prefs.days().getOrDefault(l.day(),"allow").equals("hard"))return "Kesin uygun olmayan gün";
   if(prefs.windowMode().equals("hard")&&(min(l.start())<min(prefs.earliest())||min(l.end())>min(prefs.latest())))return "Kesin saat aralığı dışında";
   for(var b:prefs.blocks())if(b.mode().equals("hard")&&CourseCatalogService.overlaps(l,new Lesson(b.day(),b.start(),b.end())))return "Sabit / uygun olmayan zaman aralığı";
  }
  if(prefs.maxCredit()!=null&&s.credit()==null||prefs.maxEcts()!=null&&s.ects()==null)return "Yük sınırı için kredi / AKTS bilinmiyor";return "";
 }
 private boolean within(List<Section> courses){if(prefs.maxCourses()>0&&courses.size()>prefs.maxCourses())return false;
  if(prefs.maxCredit()!=null&&courses.stream().map(Section::credit).reduce(BigDecimal.ZERO,BigDecimal::add).compareTo(prefs.maxCredit())>0)return false;
  if(prefs.maxEcts()!=null&&courses.stream().map(Section::ects).reduce(BigDecimal.ZERO,BigDecimal::add).compareTo(prefs.maxEcts())>0)return false;
  if(prefs.maxDailyMinutes()>0)for(int day=1;day<=7;day++){int d=day;int sum=courses.stream().flatMap(s->s.lessons().stream()).filter(l->l.day()==d).mapToInt(l->min(l.end())-min(l.start())).sum()+fixed.stream().filter(l->l.day()==d).mapToInt(l->min(l.end())-min(l.start())).sum();if(sum>prefs.maxDailyMinutes())return false;}return true;
 }
 public static Outcome solve(CurriculumData plan,CourseData data,LocalDate start,LocalDate end)throws IOException{return solve(plan,data,start,end,120000,5000);}
 static Outcome solve(CurriculumData plan,CourseData data,LocalDate start,LocalDate end,int maxNodes,int milliseconds)throws IOException {
  CourseStorageService.validate(data);var solver=new AdvancedCoursePlanner(plan,data,start,end);solver.maxNodes=maxNodes;solver.deadline=System.nanoTime()+milliseconds*1_000_000L;
  List<String> diagnostics=solver.requirements.stream().filter(r->r.choices().isEmpty()).map(r->r.name()+" / "+r.code()+": "+r.unavailable()).toList();
  if(solver.requirements.stream().anyMatch(r->r.status().equals("required")&&r.choices().isEmpty()))return new Outcome(List.of(),false,0,diagnostics);
  solver.visit(0);if(solver.results.isEmpty())diagnostics=new ArrayList<>(diagnostics){{add("Kesin dersler, saatler veya yük sınırları birlikte karşılanamıyor.");}};return new Outcome(List.copyOf(solver.results),solver.limited,solver.nodes,diagnostics);
 }
 private void visit(int depth)throws IOException {
  if(Thread.currentThread().isInterrupted())throw new IOException("Program araması iptal edildi.");if(nodes>=maxNodes||System.nanoTime()>=deadline){limited=true;return;}nodes++;
  if(depth==requirements.size()){record();return;}var req=requirements.get(depth);
  for(var section:req.choices()){
   String id=identity(CurriculumData.code(section.code()));var existing=selected.stream().filter(s->identity(CurriculumData.code(s.code())).equals(id)).findFirst().orElse(null);
   if(existing!=null&&!existing.id().equals(section.id()))continue;
   if(!req.degree().equals("extra")&&requirements.stream().anyMatch(r->!r.degree().equals("extra")&&Objects.equals(assignment.get(r.id()),section)&&(r.degree().equals(req.degree())||!id.startsWith("eq:"))))continue;
   if(existing==null){if(selected.stream().anyMatch(s->s.lessons().stream().anyMatch(a->section.lessons().stream().anyMatch(b->CourseCatalogService.overlaps(a,b)))))continue;selected.add(section);if(!within(selected)){selected.removeLast();continue;}}
   assignment.put(req.id(),section);visit(depth+1);assignment.remove(req.id());if(existing==null)selected.removeLast();if(limited)break;
  }
  if(!limited&&!req.status().equals("required"))visit(depth+1);
 }
 private void record(){
  List<Requirement> missing=requirements.stream().filter(r->!assignment.containsKey(r.id())).toList();int days=0,gaps=0,finish=0,time=0,teacher=0;long balance=0;
  List<Lesson> lessons=new ArrayList<>(fixed);selected.forEach(s->lessons.addAll(s.lessons()));
  for(int day=1;day<=7;day++){int d=day;var own=lessons.stream().filter(l->l.day()==d).sorted(Comparator.comparing(Lesson::start)).toList();if(!own.isEmpty())days++;int previous=0,sum=0;for(var l:own){int a=min(l.start()),b=min(l.end());if(previous>0)gaps+=Math.max(0,a-previous);previous=Math.max(previous,b);finish=Math.max(finish,b);sum+=b-a;}balance+=(long)sum*sum;}
  for(var section:selected){for(var rule:rules(section))if(!rule.teachers().isEmpty()){int rank=rule.teachers().indexOf(section.instructor());teacher+=rank<0?rule.teachers().size()+1:rank;}
   for(var l:section.lessons())for(int minute=min(l.start());minute<min(l.end());minute++){int m=minute;if(prefs.days().getOrDefault(l.day(),"allow").equals("soft")||prefs.windowMode().equals("soft")&&(m<min(prefs.earliest())||m>=min(prefs.latest()))||prefs.blocks().stream().anyMatch(b->b.mode().equals("soft")&&b.day()==l.day()&&m>=min(b.start())&&m<min(b.end())))time++;}}
  int elective=0;for(var r:requirements)if(assignment.containsKey(r.id())&&!r.preferred().isEmpty()){Set<String> allowed=new HashSet<>(r.preferred());expand(allowed,r.degree());if(!allowed.contains(CurriculumData.code(assignment.get(r.id()).code())))elective++;}
  List<Long> score=new ArrayList<>();if(List.of("A","B").contains(prefs.departmentPriority()))score.add(missing.stream().filter(r->r.degree().equals(prefs.departmentPriority())).count());
  if(prefs.departmentPriority().equals("course"))for(String c:prefs.rules().keySet())score.add(missing.stream().anyMatch(r->r.codes().contains(c))?1L:0L);
  score.add((long)missing.size());score.add((long)teacher);score.add((long)time);score.add((long)elective);
  switch(prefs.objective()){case "gaps"->score.addAll(List.of((long)gaps,(long)days,(long)finish));case "early"->score.addAll(List.of((long)finish,(long)days,(long)gaps));case "balanced"->score.addAll(List.of(balance,(long)gaps,(long)days));default->score.addAll(List.of((long)days,(long)gaps,(long)finish));}
  Map<String,Integer> coverage=new LinkedHashMap<>();plan.degrees().forEach(d->coverage.put(d.id(),(int)requirements.stream().filter(r->r.degree().equals(d.id())&&assignment.containsKey(r.id())).count()));
  String signature=String.join("|",selected.stream().map(Section::id).sorted().toList());Alternative next=new Alternative(selected,missing.stream().map(r->r.name()+" / "+r.code()+": "+(r.choices().isEmpty()?r.unavailable():"Diğer dersler / yük sınırlarıyla yerleşmedi")).toList(),coverage,days,gaps,teacher,time,score,signature);
  var old=results.stream().filter(r->r.signature().equals(signature)).findFirst().orElse(null);if(old!=null&&compare(old.score(),next.score())<=0)return;results.remove(old);results.add(next);results.sort((a,b)->compare(a.score(),b.score()));if(results.size()>3)results.removeLast();
 }
 private static int compare(List<Long> a,List<Long>b){for(int i=0;i<Math.min(a.size(),b.size());i++){int c=Long.compare(a.get(i),b.get(i));if(c!=0)return c;}return Integer.compare(a.size(),b.size());}
 static int min(String time){return LocalTime.parse(time).getHour()*60+LocalTime.parse(time).getMinute();}
 public static void validate(Preferences p)throws IOException {try{
  if(!List.of("days","gaps","balanced","early").contains(p.objective())||!List.of("balanced","A","B","course").contains(p.departmentPriority())||!List.of("hard","soft").contains(p.windowMode())||min(p.earliest())>=min(p.latest())||p.maxCourses()<0||p.maxCourses()>200||p.maxDailyMinutes()<0||p.maxDailyMinutes()>1440||p.maxCredit()!=null&&p.maxCredit().signum()<=0||p.maxEcts()!=null&&p.maxEcts().signum()<=0||p.blocks().size()>100||p.rules().size()>200)throw new IOException("Program tercihleri / yük sınırları geçersiz.");
  for(var e:p.days().entrySet())if(e.getKey()<1||e.getKey()>7||!List.of("allow","hard","soft").contains(e.getValue()))throw new IOException("Gün tercihi geçersiz.");
  for(var b:p.blocks())if(b.day()<1||b.day()>7||min(b.start())>=min(b.end())||!List.of("hard","soft").contains(b.mode()))throw new IOException("Sabit zaman aralığı geçersiz.");
  for(var e:p.rules().entrySet()){var r=e.getValue();if(!e.getKey().matches("[A-Z][A-Z0-9,.-]{0,79}")||!List.of("normal","required","prefer","exclude").contains(r.priority())||!List.of("only","prefer").contains(r.teacherMode())||r.teachers().size()>20||r.teachers().stream().anyMatch(t->t.isBlank()||t.length()>300)||r.teacherMode().equals("only")&&r.teachers().isEmpty())throw new IOException("Ders / hoca tercihi geçersiz.");}
 }catch(RuntimeException e){throw new IOException("Program tercihleri eksik veya geçersiz.",e);}}
 public static CourseData apply(CourseData current,CourseData snapshot,Alternative alternative,LocalDate start,LocalDate end)throws IOException {
  if(!current.equals(snapshot))throw new IOException("Ders kayıtları aramadan sonra değişti. Yeniden alternatif oluştur.");if(alternative.sections().isEmpty())throw new IOException("Boş program uygulanamaz.");
  Set<String> chosen=new HashSet<>();alternative.sections().forEach(s->chosen.add(s.id()));CourseData next=current.withCourses(current.courses().stream().map(c->!c.sectionId().isBlank()&&!chosen.contains(c.sectionId())&&!LocalDate.parse(c.endDate()).isBefore(start)&&!LocalDate.parse(c.startDate()).isAfter(end)?c.withPlanned(false):c).toList());
  for(var s:alternative.sections()){var actual=next.sections().stream().filter(x->x.id().equals(s.id())).findFirst().orElseThrow(()->new IOException("Şube katalogdan kaldırılmış."));next=CourseCatalogService.select(next,actual.id(),start,end);}
  CourseStorageService.validate(next);return next;
 }
}
