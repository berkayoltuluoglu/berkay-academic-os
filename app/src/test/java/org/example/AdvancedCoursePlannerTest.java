package org.example;

import org.junit.jupiter.api.*;import org.junit.jupiter.api.io.TempDir;import java.io.*;import java.nio.file.*;import java.math.BigDecimal;import java.time.*;import java.util.*;
import org.apache.pdfbox.pdmodel.*;import org.apache.pdfbox.pdmodel.font.*;
import static org.example.CurriculumData.*;import static org.example.CourseData.*;import static org.junit.jupiter.api.Assertions.*;

class AdvancedCoursePlannerTest {
 @TempDir Path root;static final LocalDate START=LocalDate.of(2026,10,1),END=LocalDate.of(2027,1,31);
 static String text(){return "Engineering\n1. YARIYIL\nNo Ders Kodu Ders Adi T U Kredi ECTS\n1 CS101 Algorithms 3 0 3 5\n2 ELEC Elective (Bu ders ders grubundan alinacaktir) 3 0 3 5\nToplam 6 0 6 10\nGenel Toplam Ders Adedi: 2\nELEC\n1 CS201 Networks 3 0 3 5\n2 CS202 Systems 3 0 3 5\n";}
 static Model model()throws IOException{return new CurriculumParser().parse(text());}
 static Degree degree(String id,Model model,Map<String,Decision> decisions){return new Degree(id,id.equals("A")?"Anadal":"ÇAP",1,true,"fixture.txt",model,decisions);}
 static CurriculumData plan(Degree...degrees){return new CurriculumData(1,List.of(degrees),List.of(),Preferences.defaults());}
 static Section section(String id,String code,int day,String a,String b,String teacher){return new Section(id,code,code+"."+id,code,teacher,new BigDecimal("3"),new BigDecimal("5"),List.of(new Lesson(day,a,b)));}
 static CourseData catalog(Section...sections){return new CourseData(VERSION,List.of(),List.of(),List.of(sections));}
 @Test void strictTablesRejectMissingRowsTotalsAndUnresolvedElectivePools()throws Exception{
  var parsed=model();assertEquals(2,parsed.semesters().getFirst().rows().size());assertEquals(2,parsed.groups().get("ELEC").size());assertEquals(Set.of("CS101","CS201","CS202"),parsed.catalog().keySet());
  assertThrows(IOException.class,()->new CurriculumParser().parse(text().replace("Toplam 6 0 6 10","Toplam 6 0 7 10")));
  assertThrows(IOException.class,()->new CurriculumParser().parse(text().replace("Genel Toplam Ders Adedi: 2","Genel Toplam Ders Adedi: 3")));
  assertThrows(IOException.class,()->new CurriculumParser().parse(text().substring(0,text().indexOf("\nELEC\n"))));
  assertThrows(IOException.class,()->new CurriculumParser().parse(text().replace("2 ELEC","3 ELEC")));
 }
 @Test void actualPdfTextExtractionRunsWithThePackagedParser()throws Exception{
  Path file=root.resolve("curriculum.pdf");try(var pdf=new PDDocument()){var page=new PDPage();pdf.addPage(page);try(var content=new PDPageContentStream(pdf,page)){content.beginText();content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA),10);content.setLeading(16);content.newLineAtOffset(40,750);for(var line:text().split("\n")){content.showText(line);content.newLine();}content.endText();}pdf.save(file.toFile());}
  assertEquals(model(),new CurriculumParser().read(file));
 }
 @Test void historyIsIndependentAndEquivalencesMustBeExplicitAndUnique()throws Exception{
  var m=model();var done=new Decision("done","auto","AA","2025 güz",List.of(),List.of());var a=degree("A",m,Map.of("1:1",done));var b=degree("B",m,Map.of());var data=plan(a,b);var storage=new CurriculumStorageService(root.resolve("curricula.json"));storage.load();storage.save(data);var saved=storage.load();assertEquals("AA",saved.degrees().getFirst().decisions().get("1:1").grade());assertEquals("prefer",saved.degrees().get(1).decision(m.semesters().getFirst(),m.semesters().getFirst().rows().getFirst()).status());
  assertThrows(IOException.class,()->CurriculumStorageService.validate(new CurriculumData(1,List.of(a,b),List.of(new Equivalence("CS101","CS101"),new Equivalence("CS101","CS201")),Preferences.defaults())));
  assertThrows(IOException.class,()->CurriculumStorageService.validate(plan(degree("A",m,Map.of("1:2",new Decision("done","auto","","",List.of(),List.of()))))));
  var stale=new CurriculumStorageService(root.resolve("curricula.json"));stale.load();storage.save(new CurriculumData(1,List.of(a,b),List.of(new Equivalence("CS101","CS101")),Preferences.defaults()));assertThrows(IOException.class,()->stale.save(data));
 }
 @Test void sameCodeDoesNotCoverTwoDegreesUntilItsEquivalenceIsApproved()throws Exception{
  var m=new CurriculumParser().parse("1. YARIYIL\n1 CS101 Algorithms 3 0 3 5\nToplam 3 0 3 5");var data=plan(degree("A",m,Map.of()),degree("B",m,Map.of()));var offered=catalog(section("one","CS101",1,"09:00","10:00","A"));
  var without=AdvancedCoursePlanner.solve(data,offered,START,END).results().getFirst();assertEquals(1,without.missing().size());
  var approved=new CurriculumData(1,data.degrees(),List.of(new Equivalence("CS101","CS101")),data.preferences());var with=AdvancedCoursePlanner.solve(approved,offered,START,END).results().getFirst();assertTrue(with.missing().isEmpty());assertEquals(1,with.sections().size());assertEquals(Map.of("A",1,"B",1),with.coverage());
 }
 @Test void repeatedElectiveSlotsNeedDistinctCoursesAndCompletedElectivesCannotBeReused()throws Exception{
  var m=new CurriculumParser().parse(text().replace("1 CS101 Algorithms 3 0 3 5","1 ELEC Elective (Bu ders ders grubundan alinacaktir) 3 0 3 5"));var courses=catalog(section("a","CS201",1,"09:00","10:00","A"),section("b","CS202",1,"10:00","11:00","A"));var outcome=AdvancedCoursePlanner.solve(plan(degree("A",m,Map.of())),courses,START,END);assertEquals(2,outcome.results().getFirst().sections().size());assertTrue(outcome.results().getFirst().missing().isEmpty());
  var done=new Decision("done","CS201","AA","",List.of(),List.of());var remaining=AdvancedCoursePlanner.solve(plan(degree("A",m,Map.of("1:1",done))),courses,START,END).results().getFirst();assertEquals("CS202",remaining.sections().getFirst().code());assertEquals(1,remaining.sections().size());
 }
 @Test void hardTimeAndTeacherRulesAreEnforcedAndSoftPreferencesRankAlternatives()throws Exception{
  var base=plan(degree("A",model(),Map.of("1:2",new Decision("skip","auto","","",List.of(),List.of()))));var p=new Preferences("days","balanced","08:00","18:00","hard",Map.of(2,"hard"),List.of(new Block(1,"08:00","09:00","hard")),1,new BigDecimal("3"),new BigDecimal("5"),60,Map.of("CS101",new Rule("required","only",List.of("Preferred","Second"))));
  var offered=catalog(section("a","CS101",1,"09:00","10:00","Second"),section("b","CS101",1,"10:00","11:00","Preferred"),section("c","CS101",2,"10:00","11:00","Preferred"),section("d","CS101",1,"11:00","12:00","Other"));var result=AdvancedCoursePlanner.solve(new CurriculumData(1,base.degrees(),List.of(),p),offered,START,END);assertEquals("b",result.results().getFirst().sections().getFirst().id());assertTrue(result.results().stream().flatMap(r->r.sections().stream()).noneMatch(s->List.of("c","d").contains(s.id())));
 }
 @Test void mandatoryCoursesAndUnknownLoadAreExplainedInsteadOfSilentlyDropped()throws Exception{
  var d=degree("A",model(),Map.of("1:1",new Decision("required","auto","","",List.of(),List.of())));var p=Preferences.defaults();var limit=new Preferences(p.objective(),p.departmentPriority(),p.earliest(),p.latest(),p.windowMode(),p.days(),p.blocks(),0,new BigDecimal("6"),null,0,p.rules());var unknown=new Section("x","CS101","CS101.x","Algorithms","A",null,null,List.of(new Lesson(1,"09:00","10:00")));var result=AdvancedCoursePlanner.solve(new CurriculumData(1,List.of(d),List.of(),limit),catalog(unknown),START,END);assertFalse(result.results().isEmpty(),"A single consistent curriculum supplies missing credits");
  var unavailable=AdvancedCoursePlanner.solve(plan(d),catalog(),START,END);assertTrue(unavailable.results().isEmpty());assertTrue(unavailable.diagnostics().stream().anyMatch(s->s.contains("CS101")));
 }
 @Test void applyingAProgramKeepsAssignmentIdsAndManualCoursesAndRejectsStaleSnapshots()throws Exception{
  Section a=section("a","CS101",1,"09:00","10:00","A"),b=section("b","CS101",1,"10:00","11:00","B"),c=section("c","CS201",2,"09:00","10:00","A");CourseData data=catalog(a,b,c);data=CourseCatalogService.select(data,"a",START,END);data=CourseCatalogService.select(data,"c",START,END);String id=data.courses().getFirst().id();var work=new Work("work",id,"Homework","Ödev","2026-11-01",false);var manual=new Course("manual","Meeting","","","",START.toString(),END.toString(),List.of(new Lesson(3,"12:00","13:00")));var list=new ArrayList<>(data.courses());list.add(manual);data=new CourseData(VERSION,list,List.of(work),data.sections());var alternative=new AdvancedCoursePlanner.Alternative(List.of(b),List.of(),Map.of("A",1),1,0,0,0,List.of(0L),"b");var next=AdvancedCoursePlanner.apply(data,data,alternative,START,END);
  assertEquals(work,next.work().getFirst());assertEquals(id,next.courses().stream().filter(x->x.code().equals("CS101")).findFirst().orElseThrow().id());assertTrue(next.courses().contains(manual));assertFalse(next.courses().stream().filter(x->x.code().equals("CS201")).findFirst().orElseThrow().planned());CourseData changed=data.withWork(List.of(work.withDone(true)));CourseData snapshot=data;assertThrows(IOException.class,()->AdvancedCoursePlanner.apply(changed,snapshot,alternative,START,END));
 }
 @Test void searchLimitsAreVisibleAndPlanningDoesNotMutateTheSource()throws Exception{
  var data=catalog(section("a","CS101",1,"09:00","10:00","A"));var outcome=AdvancedCoursePlanner.solve(plan(degree("A",model(),Map.of())),data,START,END,1,1000);assertTrue(outcome.limited());assertTrue(data.courses().isEmpty());
 }
 @Test void backupsValidateAndRestoreCurriculumHistory()throws Exception{
  Path file=root.resolve("profile/data/courses/curricula.json");var storage=new CurriculumStorageService(file);storage.load();storage.save(plan(degree("A",model(),Map.of())));Path zip=root.resolve("backup.zip");new BackupService(root.resolve("profile")).exportTo(zip);Path target=root.resolve("restored");new BackupService(target).restoreFrom(zip);assertEquals(storage.load(),new CurriculumStorageService(target.resolve("data/courses/curricula.json")).load());
 }
}
