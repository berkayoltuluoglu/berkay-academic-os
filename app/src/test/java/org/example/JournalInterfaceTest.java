package org.example;

import javafx.event.ActionEvent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import static org.example.FxTestSupport.fx;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIf("org.example.FxTestSupport#isGuiAvailable")
class JournalInterfaceTest {
    @TempDir Path directory;
    private JournalModuleView view;
    private VBox root;
    private HBox shell;
    private Stage stage;
    @BeforeAll static void toolkit()throws Exception{FxTestSupport.initialize();fx(()->{UiTheme.loadFonts();return null;});}
    private void open(boolean diary)throws Exception{
        fx(()->{view=new JournalModuleView(directory,diary);root=view.build();
            shell=new HBox(new SidebarBuilder().build(new ConfigLoader().load(),m->{}),root);HBox.setHgrow(root,Priority.ALWAYS);
            new UiTheme(directory.resolve("theme.properties"),"light").apply(shell);
            stage=new Stage();Scene scene=new Scene(shell,1366,768);scene.getStylesheets().add(App.class.getResource("/ui/style.css").toExternalForm());stage.setScene(scene);stage.show();return null;});await();
    }
    private void await()throws Exception{
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(15);
        while(fx(()->view.isBusy())&&System.nanoTime()<deadline)Thread.sleep(20);
        assertFalse(fx(()->view.isBusy()));fx(()->{root.applyCss();root.layout();return null;});
    }
    @AfterEach void close()throws Exception{if(view!=null){await();fx(()->{var close=root.lookup("#journal-close-details");if(close instanceof Button b)b.fire();view.dispose();stage.close();return null;});}}
    private void enter(String id,String value)throws Exception{await();fx(()->{TextField field=(TextField)root.lookup("#"+id);assertNotNull(field,id);field.setText(value);field.fireEvent(new ActionEvent());return null;});await();}
    private void click(String id)throws Exception{await();fx(()->{Button button=(Button)root.lookup("#"+id);assertNotNull(button,id);button.fire();return null;});await();}
    private List<TaskItem> tasks(){return new TaskStorageService(directory.resolve("data/tasks.json")).loadTasks();}
    private void day(LocalDate value)throws Exception{await();fx(()->{((DatePicker)root.lookup("#journal-date")).setValue(value);return null;});await();}
    private void capture(String name)throws Exception{
        WritableImage image=fx(()->shell.snapshot(null,null));var pixels=image.getPixelReader();var png=new java.awt.image.BufferedImage((int)image.getWidth(),(int)image.getHeight(),java.awt.image.BufferedImage.TYPE_INT_ARGB);
        for(int y=0;y<png.getHeight();y++)for(int x=0;x<png.getWidth();x++)png.setRGB(x,y,pixels.getArgb(x,y));
        Path path=Path.of("build/reports/"+name+".png");Files.createDirectories(path.getParent());javax.imageio.ImageIO.write(png,"png",path.toFile());
    }
    @Test void quickTasksListsDetailsAndNarrowLayoutPersist()throws Exception{
        open(false);enter("journal-new-list","Araştırma");
        var list=JournalStore.load(directory).lists().stream().filter(l->l.name().equals("Araştırma")).findFirst().orElseThrow();
        fx(()->{((ListView<JournalStore.TaskList>)root.lookup("#journal-lists")).getSelectionModel().select(list);return null;});
        enter("journal-quick","Sinyaller çıkmış soru çöz");enter("journal-quick","Kontrol notlarını toparla");enter("journal-quick","Raporu gözden geçir");
        assertEquals(3,tasks().size());String id=tasks().getFirst().getId();assertEquals(list.id(),JournalStore.load(directory).placement(id).listId());
        fx(()->{((ListView<TaskItem>)root.lookup("#journal-open-tasks")).getSelectionModel().selectFirst();return null;});
        await();enter("journal-detail-title","Sinyaller çıkmış sorular");
        assertEquals("Sinyaller çıkmış sorular",tasks().stream().filter(t->t.getId().equals(id)).findFirst().orElseThrow().getTitle());
        capture("journal-tasks-wide");fx(()->{stage.setWidth(1024);return null;});
        long resizeUntil=System.nanoTime()+TimeUnit.SECONDS.toNanos(3);
        while(fx(()->shell.getWidth())>1030&&System.nanoTime()<resizeUntil)Thread.sleep(20);
        await();capture("journal-tasks-narrow");assertTrue(fx(()->shell.getWidth())<1030);
        fx(()->{CheckBox box=(CheckBox)root.lookup(".journal-task-row .check-box");assertNotNull(box);box.fire();return null;});await();
        assertEquals(1,tasks().stream().filter(t->t.getStatus().equals("Tamamlandı")).count());
        click("journal-undo");assertEquals(0,tasks().stream().filter(t->t.getStatus().equals("Tamamlandı")).count());
    }
    @Test void diaryKeepsInvalidTextAndSupportsEditDeleteUndoAndDailyTotals()throws Exception{
        open(true);day(LocalDate.now(ZoneId.of("Europe/Istanbul")).minusDays(1));
        enter("journal-input","25:10–11:15 Sinyaller çalıştım");
        assertEquals("25:10–11:15 Sinyaller çalıştım",fx(()->((TextField)root.lookup("#journal-input")).getText()));
        assertEquals(0,JournalStore.load(directory).activities().size());
        enter("journal-input","10:30–11:15 Sinyaller çıkmış soru çözdüm");enter("journal-input","30 dk kontrol çalıştım");
        assertEquals("1 sa 15 dk",fx(()->((Label)root.lookup("#journal-day-total")).getText()));
        capture("journal-diary-wide");String id=JournalStore.load(directory).activities().getFirst().id();
        click("journal-edit-entry-"+id);enter("journal-input","20 dk Sinyaller çalıştım");
        assertEquals("50 dk",fx(()->((Label)root.lookup("#journal-day-total")).getText()));
        click("journal-delete-entry-"+id);assertEquals(1,JournalStore.load(directory).activities().size());
        click("journal-undo");assertEquals(2,JournalStore.load(directory).activities().size());
        assertFalse(Files.exists(directory.resolve("data/study/sessions.json")));
    }
    @Test void taskToDiaryMergesFocusOnceWithoutCompletingTask()throws Exception{
        var task=new TaskItem("task","Sinyaller",null,"","Bekliyor");new PlannerService(directory).execute(new PlannerCommand.CreateTask(task));
        LocalDate yesterday=LocalDate.now(ZoneId.of("Europe/Istanbul")).minusDays(1);
        var completed=yesterday.atTime(11,15).atZone(ZoneId.of("Europe/Istanbul")).toInstant();
        new StudyTimeLogService(directory).credit(new StudyTimeLogService.Entry("focus","task","task",25,completed.toString()));
        open(false);click("journal-record-task");day(yesterday);enter("journal-input","25 dk Sinyaller çıkmış soru çözdüm");
        assertEquals("25 dk",fx(()->((Label)root.lookup("#journal-day-total")).getText()));
        assertEquals(1,JournalStore.load(directory).activities().size());assertEquals("focus",JournalStore.load(directory).activities().getFirst().focusId());
        assertEquals("Bekliyor",tasks().getFirst().getStatus());assertEquals(25,new StudyTimeLogService(directory).load().getFirst().minutes());
    }
}
