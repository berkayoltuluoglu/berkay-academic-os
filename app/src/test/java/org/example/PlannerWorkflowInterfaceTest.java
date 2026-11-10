package org.example;

import javafx.scene.*;import javafx.scene.control.*;import javafx.stage.*;
import org.junit.jupiter.api.Test;import org.junit.jupiter.api.condition.EnabledIf;import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;import java.time.*;import java.util.*;import java.util.concurrent.*;import java.util.concurrent.atomic.AtomicLong;import java.util.function.BooleanSupplier;
import static org.example.FxTestSupport.fx;import static org.junit.jupiter.api.Assertions.*;import static org.example.PlannerCommand.*;

@EnabledIf("org.example.FxTestSupport#isGuiAvailable")
class PlannerWorkflowInterfaceTest {
    @TempDir Path root;
    @Test void classroomPoolFocusReviewAndCliProposalPartialAcceptanceAreVisibleAndDurable()throws Exception{
        FxTestSupport.initialize();LocalDate week=LocalDate.now().with(java.time.temporal.TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        var task=new TaskItem("task","Fizik raporu",week.plusDays(6),"Kaynaklar","Bekliyor");task.setEstimatedMinutes(180);task.setSubtasks(List.of(new TaskItem.Subtask("read","Kaynakları oku",false)));
        var planner=new PlannerService(root);planner.execute(new CreateTask(task));planner.execute(new SaveBlock(new StudySession("block","Fizik raporu",week.toString(),"10:00","11:00","",false,"task","",0),true,ConflictPolicy.REJECT));
        var data=new ClassroomData(1,"student@example.test",Instant.now().toString(),List.of(new ClassroomData.Course("physics","Fizik","","")),List.of(new ClassroomData.Work("work","physics","Classroom deney raporu","",week.plusDays(13).toString(),"17:00","NEW","")));AtomicFileWriter.write(root.resolve("data/classroom/catalog.json"),PlanningFiles.JSON.writeValueAsBytes(data));
        AtomicLong millis=new AtomicLong(week.atTime(10,0).atZone(ZoneId.of("Europe/Istanbul")).toInstant().toEpochMilli());Clock clock=new Clock(){public ZoneId getZone(){return ZoneId.of("Europe/Istanbul");}public Clock withZone(ZoneId zone){return this;}public Instant instant(){return Instant.ofEpochMilli(millis.get());}};
        var timer=new FocusTimerService(root.resolve("data/focus.properties"),clock);var view=new WeeklyAgendaView(root,new GoogleCalendarService(),date->{},id->{},timer);Stage[] stage=new Stage[1];
        fx(()->{UiTheme.loadFonts();var pane=view.build();new UiTheme(root.resolve("theme.properties"),"light").apply(pane);stage[0]=new Stage();stage[0].setTitle("Academic OS Planner Verification");stage[0].setScene(new Scene(pane,1550,950));stage[0].getScene().getStylesheets().add(UiTheme.class.getResource("/ui/style.css").toExternalForm());stage[0].show();return null;});
        try{
            waitFor(()->status(stage[0]).contains("birlikte gösteriliyor"));floatWindow();
            fx(()->{WeeklyPlannerInterfaceTest.snapshot(stage[0],"planner-stage-3");Node block=stage[0].getScene().lookup("#planner-block-block");block.getOnMouseClicked().handle(new javafx.scene.input.MouseEvent(javafx.scene.input.MouseEvent.MOUSE_CLICKED,0,0,0,0,javafx.scene.input.MouseButton.PRIMARY,1,false,false,false,false,false,false,false,false,false,false,null));((Button)stage[0].getScene().lookup("#planner-focus")).fire();return null;});
            waitFor(()->window("#focus-mode")!=null);Stage focus=fx(()->(Stage)window("#focus-mode"));
            millis.addAndGet(61*60000L);waitFor(()->window("#focus-review-continue")!=null);
            fx(()->{WeeklyPlannerInterfaceTest.snapshot(focus,"planner-stage-4");((Button)focus.getScene().lookup("#focus-review-continue")).fire();return null;});
            waitFor(()->{try{return new StudyStorageService(root.resolve("data/study/sessions.json")).load().getFirst().actualMinutes()==60;}catch(Exception e){return false;}});
            waitFor(()->{try{return fx(()->((Label)focus.getScene().lookup("#focus-status")).getText().equals("Kaydedildi"));}catch(Exception e){return false;}});
            fx(()->{focus.close();return null;});
            var snap=planner.snapshot(week);List<PlanProposal.Action> actions=List.of(new PlanProposal.Action("schedule",null,"","","Sınav tekrarı",LocalDate.now(ZoneId.of("Europe/Istanbul")).toString(),"13:00",60,"Sınavdan önce tekrar",null),new PlanProposal.Action("schedule",null,"","","Okuma",week.plusDays(6).toString(),"15:00",60,"Haftaya yayılan çalışma",null));
            var p=new PlanProposal(1,"ui-proposal","CLI test ajanı","İki çalışma önerisi",null,week.toString(),snap.fingerprint(),actions,"pending","",List.of(),null);Path file=root.resolve("proposal.json");Files.write(file,PlanningFiles.JSON.writeValueAsBytes(p));
            var output=new java.io.ByteArrayOutputStream();assertEquals(0,AgentCli.run(new String[]{"--profile",root.toString(),"propose",file.toString()},new java.io.PrintStream(output)),output.toString());
            waitFor(()->exists(stage[0],"#proposal-ghost-ui-proposal-0"));
            fx(()->{((Button)stage[0].getScene().lookup("#planner-proposals")).fire();((Button)stage[0].getScene().lookup("#proposal-open-ui-proposal")).fire();return null;});
            waitFor(()->enabled(stage[0],"#proposal-accept"));
            fx(()->{WeeklyPlannerInterfaceTest.snapshot(stage[0],"planner-stage-5");((CheckBox)stage[0].getScene().lookup("#proposal-choice-1")).setSelected(false);((TextArea)stage[0].getScene().lookup("#proposal-feedback")).setText("İkinci çalışma cumartesi olsun");((Button)stage[0].getScene().lookup("#proposal-accept")).fire();return null;});
            waitFor(()->{try{return new ProposalService(root).status("ui-proposal").status().equals("partially_accepted");}catch(Exception e){return false;}});
            output.reset();assertEquals(0,AgentCli.run(new String[]{"--profile",root.toString(),"proposal-status","ui-proposal"},new java.io.PrintStream(output)));assertTrue(output.toString().contains("İkinci çalışma cumartesi olsun"));
            waitFor(()->!status(stage[0]).contains("…"));
        }finally{fx(()->{for(Window window:List.copyOf(Window.getWindows()))if(window!=stage[0]&&window.getScene()!=null&&window.getScene().lookup("#focus-mode")!=null)((Stage)window).close();view.dispose();stage[0].close();return null;});}
    }
    static void floatWindow(){if(System.getProperty("os.name").contains("Linux"))try{new ProcessBuilder("i3-msg","[title=\"^Academic OS Planner Verification$\"] floating enable, resize set 1550 px 950 px, move position center").redirectOutput(ProcessBuilder.Redirect.DISCARD).start().waitFor(3,TimeUnit.SECONDS);}catch(Exception ignored){}}
    private static boolean exists(Stage stage,String selector){try{return fx(()->stage.getScene().lookup(selector)!=null);}catch(Exception e){throw new RuntimeException(e);}}
    private static boolean enabled(Stage stage,String selector){try{return fx(()->stage.getScene().lookup(selector)!=null&&!stage.getScene().lookup(selector).isDisabled());}catch(Exception e){throw new RuntimeException(e);}}
    private static Window window(String selector){try{return javafx.application.Platform.isFxApplicationThread()?Window.getWindows().stream().filter(w->w.isShowing()&&w.getScene()!=null&&w.getScene().lookup(selector)!=null).findFirst().orElse(null):fx(()->window(selector));}catch(Exception e){throw new RuntimeException(e);}}
    private static String status(Stage stage){try{return fx(()->((Label)stage.getScene().lookup("#weekly-agenda-status")).getText());}catch(Exception e){throw new RuntimeException(e);}}
    private static void waitFor(BooleanSupplier condition)throws Exception{long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(15);while(!condition.getAsBoolean()&&System.nanoTime()<until)Thread.sleep(40);assertTrue(condition.getAsBoolean());}
}
