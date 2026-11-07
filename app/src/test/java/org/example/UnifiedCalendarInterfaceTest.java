package org.example;

import javafx.scene.Scene;import javafx.scene.control.*;import javafx.stage.*;
import org.junit.jupiter.api.*;import org.junit.jupiter.api.condition.EnabledIf;import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;import java.time.*;import java.util.*;import java.util.concurrent.*;import javax.imageio.ImageIO;
import static org.example.FxTestSupport.fx;import static org.junit.jupiter.api.Assertions.*;

@EnabledIf("org.example.FxTestSupport#isGuiAvailable")
class UnifiedCalendarInterfaceTest {
    @TempDir Path root;
    @Test void dayTitlesIncludeLocalRecordsAndCalendarSelectionFiltersWithoutMakingOtherSourcesEditable()throws Exception {
        FxTestSupport.initialize();LocalDate date=LocalDate.now();YearMonth month=YearMonth.from(date);
        new TaskStorageService(root.resolve("data/tasks.json")).saveTasks(List.of(new TaskItem("task","Report deadline",date,"","Bekliyor")));
        var studies=new StudyStorageService(root.resolve("data/study/sessions.json"));studies.load();studies.save(List.of(new StudySession("study","Reading",date.toString(),"11:00","12:00","",false)));
        var google=List.of(CalendarEventItem.timed("main","Meeting",date.atTime(13,0),date.atTime(14,0),"","").withGoogleMetadata(date,"e1",true,false),
                CalendarEventItem.allDay("holiday","Holiday",date,"","").withGoogleMetadata(date,"e2",true,false).withSource("holidays","Holidays","#0b8043"));
        Set<String> selected=new HashSet<>(List.of("primary","holidays"));
        var service=new GoogleCalendarService(){
            @Override public boolean isConnected(){return true;}
            @Override public String getSourceHint(){return "Google Calendar: student@example.com · 2 takvim";}
            @Override public List<CalendarEventItem> loadEventsForMonth(YearMonth m){return google.stream().filter(e->selected.contains(e.getCalendarId())).toList();}
            @Override public List<Source> calendarSources(){return List.of(new Source("primary","Main","#4285f4",true,selected.contains("primary"),"owner"),new Source("holidays","Holidays","#0b8043",false,selected.contains("holidays"),"reader"));}
            @Override public void selectCalendars(Set<String> ids){selected.clear();selected.addAll(ids);}
        };
        CalendarModuleView view=new CalendarModuleView(service);Stage[] window=new Stage[1];
        fx(()->{UiTheme.loadFonts();var pane=view.build(root.resolve("data/tasks.json"));new UiTheme(root.resolve("theme.properties"),"light").apply(pane);var scene=new Scene(pane,1300,820);scene.getStylesheets().add(UiTheme.class.getResource("/ui/style.css").toExternalForm());window[0]=new Stage();window[0].setTitle("Academic OS Unified Calendar Verification");window[0].setScene(scene);window[0].show();return null;});
        try {
            waitFor(()->text(window[0]).contains("Bu ay 4 kayıt"));
            fx(()->{Button day=(Button)window[0].getScene().lookup("#calendar-day-"+date);assertTrue(day.getTooltip().getText().contains("Report deadline"));assertTrue(day.getTooltip().getText().contains("Holiday"));day.fire();ListView<CalendarEventItem> list=events(window[0]);assertEquals(4,list.getItems().size());list.getSelectionModel().select(list.getItems().stream().filter(e->e.getTitle().equals("Holiday")).findFirst().orElseThrow());assertTrue(((Button)window[0].getScene().lookup("#google-calendar-delete")).isDisabled());((Button)window[0].getScene().lookup("#google-calendar-sources")).fire();return null;});
            waitFor(()->showing("#calendar-sources-dialog")!=null);
            fx(()->{var dialog=showing("#calendar-sources-dialog");dialog.getScene().getRoot().lookupAll(".check-box").stream().filter(CheckBox.class::isInstance).map(CheckBox.class::cast).filter(c->c.getText().equals("Holidays")).findFirst().orElseThrow().setSelected(false);((Button)dialog.getScene().lookup("#calendar-sources-save")).fire();return null;});
            waitFor(()->text(window[0]).contains("Bu ay 3 kayıt"));assertFalse(selected.contains("holidays"));
            fx(()->{((CheckBox)window[0].getScene().lookup("#calendar-show-local")).fire();return null;});waitFor(()->text(window[0]).contains("Bu ay 1 kayıt"));assertFalse(new CalendarSourcesStore(root).showLocal());
            fx(()->{((CheckBox)window[0].getScene().lookup("#calendar-show-local")).fire();return null;});waitFor(()->text(window[0]).contains("Bu ay 3 kayıt"));
            if(System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("linux"))try{new ProcessBuilder("i3-msg","[title=\"^Academic OS Unified Calendar Verification$\"] floating enable, resize set 1400 px 900 px, move position center").redirectOutput(ProcessBuilder.Redirect.DISCARD).start().waitFor(3,TimeUnit.SECONDS);}catch(Exception ignored){}
            fx(()->{window[0].getScene().getRoot().applyCss();window[0].getScene().getRoot().layout();var image=window[0].getScene().snapshot(null);int width=(int)image.getWidth(),height=(int)image.getHeight();var output=new java.awt.image.BufferedImage(width,height,java.awt.image.BufferedImage.TYPE_INT_ARGB);for(int y=0;y<height;y++)for(int x=0;x<width;x++)output.setRGB(x,y,image.getPixelReader().getArgb(x,y));Path target=Path.of("build/reports/unified-calendar.png");Files.createDirectories(target.getParent());ImageIO.write(output,"png",target.toFile());return null;});
        }finally{fx(()->{view.dispose();window[0].close();return null;});}
    }
    @SuppressWarnings("unchecked") private static ListView<CalendarEventItem> events(Stage stage){return (ListView<CalendarEventItem>)stage.getScene().lookup("#google-calendar-events");}
    private static Window showing(String id){java.util.concurrent.Callable<Window> find=()->Window.getWindows().stream().filter(w->w.isShowing()&&w.getScene()!=null&&w.getScene().lookup(id)!=null).findFirst().orElse(null);try{return javafx.application.Platform.isFxApplicationThread()?find.call():fx(find);}catch(Exception e){throw new RuntimeException(e);}}
    private static String text(Stage stage){try{return fx(()->((Label)stage.getScene().lookup("#google-calendar-status")).getText());}catch(Exception e){throw new RuntimeException(e);}}
    private static void waitFor(java.util.function.BooleanSupplier condition)throws Exception{long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);while(!condition.getAsBoolean()&&System.nanoTime()<until)Thread.sleep(20);assertTrue(condition.getAsBoolean());}
}
