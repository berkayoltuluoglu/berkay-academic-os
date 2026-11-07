package org.example;

import org.junit.jupiter.api.*;import org.junit.jupiter.api.io.TempDir;import java.nio.file.*;import java.time.*;import java.util.*;import com.fasterxml.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;
class CalendarMonthServiceTest {
    @TempDir Path root;
    @Test void sharedMonthContainsLessonsTasksWorkStudyClassroomAndScholarshipsOnTheirOwnDates()throws Exception {
        var month=YearMonth.of(2026,10);var date=month.atDay(13);var courses=new CourseStorageService(root.resolve("data/courses/academic.json"));courses.load();
        var course=new CourseData.Course("course","Physics","PHY","Teacher","Room",month.atDay(1).toString(),month.atEndOfMonth().toString(),List.of(new CourseData.Lesson(2,"09:00","10:00")));
        courses.save(new CourseData(CourseData.VERSION,List.of(course),List.of(new CourseData.Work("work","course","Report","Ödev",date.toString(),false,"17:00",30,0,"Yüksek"))));
        new TaskStorageService(root.resolve("data/tasks.json")).saveTasks(List.of(new TaskItem("task","Task",date,"","Bekliyor")));
        var study=new StudyStorageService(root.resolve("data/study/sessions.json"));study.load();study.save(List.of(new StudySession("study","Revision",date.toString(),"11:00","12:00","",false)));
        var classroom=new ClassroomData(1,"student@example.com","",List.of(),List.of(new ClassroomData.Work("assignment","class","Classroom homework","",month.atDay(1).toString(),"23:59","NEW","")));
        AtomicFileWriter.write(root.resolve("data/classroom/catalog.json"),new ObjectMapper().writeValueAsBytes(classroom));
        var scholarships=new ScholarshipStorageService(root.resolve("data/finance/applications.json"));scholarships.load();scholarships.save(new ScholarshipData(1,List.of(new ScholarshipData.Application("application","Grant","Burs","",date.toString(),"","Planlanıyor","Beklemede","",List.of(),"",""))));
        var holiday=CalendarEventItem.allDay("holiday","Holiday",month.atDay(29),"","").withSource("holidays","Holidays","#0b8043");var data=new CalendarMonthService().load(root,month,List.of(holiday),true);
        assertTrue(data.warnings().isEmpty());assertEquals(1,data.googleCount());assertTrue(data.items().stream().map(CalendarEventItem::getTitle).toList().containsAll(List.of("Physics","Report","Task","Revision","Classroom homework","Grant","Holiday")));
        assertEquals(1,data.items().stream().filter(e->e.getTitle().equals("Classroom homework")).count());assertEquals(month.atDay(1),data.items().stream().filter(e->e.getTitle().equals("Classroom homework")).findFirst().orElseThrow().getDate());
        assertTrue(data.items().stream().filter(e->e.getCalendarId().startsWith("local:")).allMatch(e->!e.isEditable()));assertEquals(1,new CalendarMonthService().load(root,month,List.of(holiday),false).items().size());
    }
}
