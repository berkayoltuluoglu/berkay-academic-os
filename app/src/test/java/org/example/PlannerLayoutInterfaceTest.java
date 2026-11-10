package org.example;

import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.Region;
import javafx.stage.Stage;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import static org.example.FxTestSupport.fx;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIf("org.example.FxTestSupport#isGuiAvailable")
class PlannerLayoutInterfaceTest {
    @TempDir Path profile;
    final LocalDate today=LocalDate.now(ZoneId.of("Europe/Istanbul"));
    App app;
    Stage stage;

    @BeforeEach void open() throws Exception {
        FxTestSupport.initialize();
        new TaskStorageService(profile.resolve("data/tasks.json")).saveTasks(List.of(
                new TaskItem("seven","Yedi gün önceki iş",today.minusDays(7),"","Bekliyor"),
                new TaskItem("eight","Sekiz gün önceki iş",today.minusDays(8),"","Bekliyor"),
                new TaskItem("ancient","Eski dönemden kalan iş",today.minusDays(500),"","Bekliyor"),
                nightTask()));
        var week=today.with(java.time.temporal.TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        var courses=new CourseStorageService(profile.resolve("data/courses/academic.json"));courses.load();
        courses.save(new CourseData(2,List.of(new CourseData.Course("lab","Elektronik","EL201","","D204",week.toString(),week.plusDays(6).toString(),
                List.of(new CourseData.Lesson(today.getDayOfWeek().getValue(),"09:00","10:30")))),
                List.of(new CourseData.Work("night-work","lab","B Elektronik laboratuvar sonuç raporu","Ödev",today.toString(),false,"23:59",90,0,"Yüksek"))));
        var classroom=new ClassroomData(1,"student@example.test",Instant.now().toString(),
                List.of(new ClassroomData.Course("physics","Fizik","","")),
                List.of(new ClassroomData.Work("night-classroom","physics","C Classroom deney raporu","",today.toString(),"23:59","NEW","")));
        AtomicFileWriter.write(profile.resolve("data/classroom/catalog.json"),PlanningFiles.JSON.writeValueAsBytes(classroom));
        var sessions=new StudyStorageService(profile.resolve("data/study/sessions.json"));sessions.load();
        sessions.save(List.of(new StudySession("report-block","Rapor çalışması",today.toString(),"13:00","14:30","lab",false)));
        fx(()->{app=new App(profile);stage=new Stage();app.start(stage);stage.setTitle("Academic OS Planner Verification");return null;});
        PlannerWorkflowInterfaceTest.floatWindow();
        waitFor(()->ready("#today-refresh"));
    }

    TaskItem nightTask(){var task=new TaskItem("night-task","A Fizik deney raporu",today,"","Bekliyor");task.setDueTime(LocalTime.of(23,59));return task;}

    @AfterEach void close() throws Exception {
        fx(()->{if(app!=null)app.stop();if(stage!=null)stage.close();return null;});
    }

    @Test void recentHistoryIsLimitedInBothViewsWithoutChangingStoredOrCliData() throws Exception {
        fx(()->{
            assertNotNull(find("#todo-task-seven"));assertNull(find("#todo-task-eight"));assertNull(find("#todo-task-ancient"));
            ((ToggleButton)find("#nav-agenda")).fire();return null;
        });
        waitFor(()->ready("#week-refresh"));
        fx(()->{
            assertNotNull(find("#pool-seven"));assertNull(find("#pool-eight"));assertNull(find("#pool-ancient"));
            ((Button)find("#week-next")).fire();return null;
        });
        waitFor(()->ready("#week-refresh"));
        fx(()->{
            assertNotNull(find("#pool-seven"),"Hafta gezinmesi bugün tabanlı sınırı değiştirmemeli");assertNull(find("#pool-eight"));
            return null;
        });
        assertEquals(4,new TaskStorageService(profile.resolve("data/tasks.json")).loadTasks().size());
        assertTrue(new PlannerService(profile).snapshot(today).pool().stream().anyMatch(item->item.id().equals("ancient")),"CLI havuzu eski kayıtları korumalı");
    }

    @Test void deadlinesStayAboveHoursAndDetailsCanReleaseTheFullWidth() throws Exception {
        fx(()->{((ToggleButton)find("#nav-agenda")).fire();return null;});
        waitFor(()->ready("#week-refresh"));
        fx(()->{
            resizeSurface(1920,1040);
            var canvas=(ScrollPane)find("#planner-canvas");
            var header=find("#planner-deadline-header");
            var deadline=(Button)find("#planner-deadline-night-task");
            assertNotNull(deadline);assertTrue(deadline.getAccessibleText().contains("23:59"));
            assertFalse(((ToggleButton)find("#planner-details-toggle")).isSelected());
            Bounds before=header.localToScene(header.getBoundsInLocal());
            assertTrue(deadline.localToScene(deadline.getBoundsInLocal()).getMaxY()<=canvas.localToScene(canvas.getBoundsInLocal()).getMinY()+1,
                    "23:59 teslimi saat ızgarasının altında olmamalı");
            var more=find("#planner-more-deadlines-"+today);
            assertTrue(more.localToScene(more.getBoundsInLocal()).getMaxY()<=canvas.localToScene(canvas.getBoundsInLocal()).getMinY()+1,
                    "Diğer teslimlere erişim düğmesi kesilmemeli");
            canvas.setVvalue(1);layout();
            assertEquals(before.getMinY(),header.localToScene(header.getBoundsInLocal()).getMinY(),1,"Saatleri kaydırırken teslim başlıkları sabit kalmalı");
            deadline.fire();layout();
            assertEquals("23:59",((ComboBox<?>)find("#pool-due-time")).getValue());
            double withDetails=canvas.getViewportBounds().getWidth();
            screenshot("planner-layout-details");
            ((ToggleButton)find("#planner-details-toggle")).fire();layout();
            assertTrue(canvas.getViewportBounds().getWidth()>withDetails+150,"Kapanan panelin alanı takvime katılmalı");
            canvas.setVvalue(0);layout();
            Node last=find("#planner-day-"+today.with(java.time.temporal.TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY)));
            Node viewport=canvas.lookup(".viewport");
            assertEquals(viewport.localToScene(viewport.getBoundsInLocal()).getMaxX(),last.localToScene(last.getBoundsInLocal()).getMaxX(),6,
                    "Tam ekranda haftanın sütunları kullanılabilir genişliği doldurmalı");
            screenshot("planner-layout-full");
            ((Button)find("#planner-more-deadlines-"+today)).fire();layout();
            assertNotNull(find("#planner-inspector").lookup("#planner-inspector-deadline-"+ClassroomImportService.workId("student@example.test","physics","night-classroom")));
            resizeSurface(1200,800);screenshot("planner-layout-compact");
            ((ToggleButton)find("#theme-toggle")).fire();layout();screenshot("planner-layout-dark");
            return null;
        });
        assertEquals("23:59",new CourseStorageService(profile.resolve("data/courses/academic.json")).load().work().getFirst().dueTime());
        assertEquals(LocalTime.of(23,59),new TaskStorageService(profile.resolve("data/tasks.json")).loadTasks().stream().filter(task->task.getId().equals("night-task")).findFirst().orElseThrow().getDueTime());
    }

    private Node find(String selector){return stage.getScene().getRoot().lookup(selector);}
    private boolean ready(String selector){try{return fx(()->find(selector)!=null&&!find(selector).isDisabled()&&find(".module-content").getOpacity()>.99);}catch(Exception e){throw new RuntimeException(e);}}
    private void layout(){stage.getScene().getRoot().applyCss();stage.getScene().getRoot().layout();stage.getScene().getRoot().applyCss();stage.getScene().getRoot().layout();}
    private void resizeSurface(int width,int height){stage.setWidth(width);stage.setHeight(height);((Region)stage.getScene().getRoot()).resize(width,height);layout();}
    private void screenshot(String name)throws Exception{
        layout();var image=stage.getScene().getRoot().snapshot(null,null);
        var output=new java.awt.image.BufferedImage((int)image.getWidth(),(int)image.getHeight(),java.awt.image.BufferedImage.TYPE_INT_ARGB);
        for(int y=0;y<output.getHeight();y++)for(int x=0;x<output.getWidth();x++)output.setRGB(x,y,image.getPixelReader().getArgb(x,y));
        Path path=Path.of("build/reports/"+name+".png");Files.createDirectories(path.getParent());javax.imageio.ImageIO.write(output,"png",path.toFile());
    }
    private static void waitFor(BooleanSupplier condition)throws Exception{long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(15);while(!condition.getAsBoolean()&&System.nanoTime()<until)Thread.sleep(40);assertTrue(condition.getAsBoolean());}
}
