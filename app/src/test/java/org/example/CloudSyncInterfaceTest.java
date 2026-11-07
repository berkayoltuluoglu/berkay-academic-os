package org.example;
import javafx.scene.Scene;import javafx.scene.control.*;import javafx.stage.Stage;import org.junit.jupiter.api.*;import org.junit.jupiter.api.condition.EnabledIf;import org.junit.jupiter.api.io.TempDir;import java.nio.file.*;import java.time.*;import java.util.*;import static org.example.FxTestSupport.fx;import static org.junit.jupiter.api.Assertions.*;
@EnabledIf("org.example.FxTestSupport#isGuiAvailable")
class CloudSyncInterfaceTest {
 @TempDir Path root;
 @Test void showsThePendingOfflineChangeAndPersistsPollingPreferences()throws Exception{
  FxTestSupport.initialize();new CalendarCacheStore(root).setAccount("student@example.com");var calendar=new CalendarSyncService(root);calendar.createEvent("abcdef123456",new CalendarEventDraft("Offline plan","","",LocalDate.now(),LocalDate.now(),null,null,true));var drive=new DriveSyncService(root);var cloud=new CloudSyncService(root,calendar,drive);var view=new CloudSyncModuleView(cloud);Stage[] window=new Stage[1];fx(()->{window[0]=new Stage();window[0].setScene(new Scene(view.build(),1100,750));window[0].show();return null;});
  try{fx(()->{((TabPane)window[0].getScene().lookup(".tab-pane")).getSelectionModel().select(1);window[0].getScene().getRoot().applyCss();window[0].getScene().getRoot().layout();ListView<?> queue=(ListView<?>)window[0].getScene().lookup("#sync-calendar-queue");assertEquals(1,queue.getItems().size());((CheckBox)window[0].getScene().lookup("#sync-calendar-automatic")).fire();return null;});assertFalse(cloud.calendarAutomatic());try(var restarted=new CloudSyncService(root,calendar,drive)){assertFalse(restarted.calendarAutomatic());}assertEquals(1,calendar.pendingCount());}
  finally{fx(()->{view.dispose();window[0].close();return null;});cloud.close();}
 }
}
