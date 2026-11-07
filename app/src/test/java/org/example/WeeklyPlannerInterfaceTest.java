package org.example;

import javafx.scene.*;import javafx.scene.control.*;import javafx.scene.input.*;import javafx.stage.*;
import org.junit.jupiter.api.*;import org.junit.jupiter.api.condition.EnabledIf;import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;import java.time.*;import java.util.*;import java.util.concurrent.*;import java.util.function.BooleanSupplier;
import static org.example.FxTestSupport.fx;import static org.junit.jupiter.api.Assertions.*;

@EnabledIf("org.example.FxTestSupport#isGuiAvailable")
class WeeklyPlannerInterfaceTest {
    @TempDir Path profile;
    @Test void gridCreationCrossDayMoveResizeInspectorAndUndoUseRealProfileAndControls() throws Exception {
        FxTestSupport.initialize();LocalDate week=LocalDate.now().with(java.time.temporal.TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        var courseStore=new CourseStorageService(profile.resolve("data/courses/academic.json"));courseStore.load();courseStore.save(new CourseData(2,List.of(new CourseData.Course("fixed","Sabit ders","","","",week.toString(),week.plusDays(6).toString(),List.of(new CourseData.Lesson(1,"09:00","10:00")))),List.of()));
        WeeklyAgendaView view=new WeeklyAgendaView(profile,new GoogleCalendarService(),date->{},id->{});Stage[] stage=new Stage[1];
        fx(()->{UiTheme.loadFonts();var root=view.build();new UiTheme(profile.resolve("theme.properties"),"light").apply(root);stage[0]=new Stage();stage[0].setTitle("Academic OS Planner Verification");stage[0].setScene(new Scene(root,1450,900));stage[0].getScene().getStylesheets().add(UiTheme.class.getResource("/ui/style.css").toExternalForm());stage[0].show();return null;});
        try {
            waitFor(()->status(stage[0]).contains("birlikte gösteriliyor"));PlannerWorkflowInterfaceTest.floatWindow();
            fx(()->{assertNull(stage[0].getScene().lookup("#fixed-fixed").getOnDragDetected());Node day=stage[0].getScene().lookup("#planner-day-"+week);
                day.fireEvent(mouse(day,javafx.scene.input.MouseEvent.MOUSE_PRESSED,20,102,true));day.fireEvent(mouse(day,javafx.scene.input.MouseEvent.MOUSE_DRAGGED,20,153,true));day.fireEvent(mouse(day,javafx.scene.input.MouseEvent.MOUSE_RELEASED,20,153,false));
                TextField inline=(TextField)stage[0].getScene().lookup("#planner-inline-title");assertNotNull(inline);inline.setText("Okuma");inline.fireEvent(new javafx.event.ActionEvent());return null;});
            waitFor(()->blocks().size()==1&&ready(stage[0]));String id=blocks().getFirst().id();
            fx(()->{view.move(id,week.plusDays(1).toString(),"11:00",false);return null;});waitFor(()->blocks().getFirst().date().equals(week.plusDays(1).toString())&&ready(stage[0]));
            fx(()->{view.resize(id,90,false);return null;});waitFor(()->blocks().getFirst().end().equals("12:30")&&ready(stage[0]));
            fx(()->{((TextField)stage[0].getScene().lookup("#planner-title")).setText("Düzenlenmiş başlık");return null;});waitFor(()->blocks().getFirst().title().equals("Düzenlenmiş başlık")&&ready(stage[0]));
            fx(()->{((Button)stage[0].getScene().lookup("#planner-undo")).fire();return null;});waitFor(()->blocks().getFirst().title().equals("Okuma")&&ready(stage[0]));
            fx(()->{Node block=stage[0].getScene().lookup("#planner-block-"+id);block.fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED,"","",KeyCode.DOWN,false,false,true,false));return null;});waitFor(()->blocks().getFirst().start().equals("11:15")&&ready(stage[0]));
            fx(()->{snapshot(stage[0],"planner-stage-2");return null;});
        }finally{fx(()->{view.dispose();stage[0].close();return null;});}
    }
    private static javafx.scene.input.MouseEvent mouse(Node node,javafx.event.EventType<javafx.scene.input.MouseEvent> type,double x,double y,boolean down){return new javafx.scene.input.MouseEvent(node,node,type,x,y,x,y,MouseButton.PRIMARY,1,false,false,false,false,down,false,false,false,false,false,new javafx.scene.input.PickResult(node,new javafx.geometry.Point3D(x,y,0),0));}
    private List<StudySession> blocks(){try{return new StudyStorageService(profile.resolve("data/study/sessions.json")).load();}catch(Exception e){throw new RuntimeException(e);}}
    private static boolean ready(Stage stage){return !status(stage).contains("…");}
    private static String status(Stage stage){try{return fx(()->((Label)stage.getScene().lookup("#weekly-agenda-status")).getText());}catch(Exception e){throw new RuntimeException(e);}}
    static void snapshot(Stage stage,String name)throws Exception{stage.getScene().getRoot().applyCss();stage.getScene().getRoot().layout();var image=stage.getScene().snapshot(null);var out=new java.awt.image.BufferedImage((int)image.getWidth(),(int)image.getHeight(),java.awt.image.BufferedImage.TYPE_INT_ARGB);for(int y=0;y<out.getHeight();y++)for(int x=0;x<out.getWidth();x++)out.setRGB(x,y,image.getPixelReader().getArgb(x,y));Path file=Path.of("build/reports/"+name+".png");Files.createDirectories(file.getParent());javax.imageio.ImageIO.write(out,"png",file.toFile());}
    private static void waitFor(BooleanSupplier condition)throws Exception{long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(12);while(!condition.getAsBoolean()&&System.nanoTime()<until)Thread.sleep(30);assertTrue(condition.getAsBoolean());}
}
