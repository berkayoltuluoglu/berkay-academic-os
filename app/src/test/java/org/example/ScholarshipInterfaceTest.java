package org.example;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.io.TempDir;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Stage;
import java.nio.file.*;
import java.time.LocalDate;
import static org.example.FxTestSupport.fx;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIf("org.example.FxTestSupport#isGuiAvailable")
class ScholarshipInterfaceTest {
    @TempDir Path root;
    private ScholarshipModuleView view;
    private Stage stage;
    @Test void createsApplicationAndPersistsAReadyDocumentThroughTheActualForm() throws Exception {
        FxTestSupport.initialize(); Path file=root.resolve("applications.json");
        fx(()->{ view=new ScholarshipModuleView(file); stage=new Stage(); stage.setScene(new Scene(view.build(),1050,760)); stage.show(); return null; });
        try {
            waitIdle();
            fx(()->{ ((TextField)stage.getScene().lookup("#scholarship-name")).setText("Staj başvurusu");
                ((DatePicker)stage.getScene().lookup("#scholarship-deadline")).setValue(LocalDate.now().plusDays(5));
                ((TextField)stage.getScene().lookup("#scholarship-document-name")).setText("Özgeçmiş");
                ((Button)stage.getScene().lookup("#scholarship-document-add")).fire();
                ((CheckBox)stage.getScene().lookup(".check-box")).fire();
                ((Button)stage.getScene().lookup("#scholarship-save")).fire(); return null; });
            waitIdle(); var saved=new ScholarshipStorageService(file).load().applications().getFirst();
            assertEquals("Staj başvurusu",saved.name()); assertTrue(saved.documents().getFirst().ready());
            assertEquals(LocalDate.now().plusDays(5).toString(),saved.deadline());
            fx(()->{ ((TextField)stage.getScene().lookup("#scholarship-amount")).setText("Ücretli staj");
                ((Button)stage.getScene().lookup("#scholarship-save")).fire(); return null; });
            waitIdle(); var changed=new ScholarshipStorageService(file).load(); assertEquals(1,changed.applications().size()); assertEquals(saved.id(),changed.applications().getFirst().id());
        } finally { fx(()->{ view.dispose(); stage.close(); return null; }); }
    }
    private void waitIdle() throws Exception { long until=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(10);
        while (fx(()->stage.getScene().getRoot().isDisabled()) && System.nanoTime()<until) Thread.sleep(20);
        assertFalse(fx(()->stage.getScene().getRoot().isDisabled())); }
}
