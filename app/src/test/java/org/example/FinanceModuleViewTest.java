package org.example;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import javafx.scene.Scene;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.concurrent.TimeUnit;
import static org.example.FxTestSupport.fx;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIf("org.example.FxTestSupport#isGuiAvailable")
class FinanceModuleViewTest {
    @TempDir Path directory;
    private final ObjectMapper mapper = new ObjectMapper();
    private FinanceModuleView view;
    private Stage stage;
    private VBox root;
    private Path file;

    @BeforeAll static void toolkit() throws Exception { FxTestSupport.initialize(); }

    private void open(boolean populated) throws Exception {
        file = directory.resolve("ledger.json");
        if (populated && !Files.exists(file)) {
            FinanceStorageService storage = new FinanceStorageService(file);
            storage.load(); storage.save(FinanceTestData.populated().toString());
        }
        fx(() -> {
            view = new FinanceModuleView();
            root = view.build(file);
            stage = new Stage();
            stage.setScene(new Scene(root, 1040, 760));
            stage.show();
            return null;
        });
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (!fx(view::isReady) && System.nanoTime() < deadline) Thread.sleep(30);
        assertTrue(fx(view::isReady), () -> "Finance page did not initialize");
        fx(() -> { view.webView().getEngine().setConfirmHandler(message -> true); return null; });
    }

    @AfterEach void close() throws Exception {
        fx(() -> {
            if (view != null) view.dispose();
            if (stage != null) stage.close();
            return null;
        });
    }

    private Object js(String source) throws Exception {
        return fx(() -> view.webView().getEngine().executeScript(source));
    }

    private void click(String selector) throws Exception {
        js("document.querySelector(" + mapper.writeValueAsString(selector) + ").click()");
    }

    private void value(String id, String value) throws Exception {
        js("document.getElementById(" + mapper.writeValueAsString(id) + ").value=" + mapper.writeValueAsString(value));
    }

    private JsonNode state() throws Exception { return mapper.readTree((String)js("window.financeApp.exportState()")); }
    private JsonNode summary() throws Exception { return mapper.readTree((String)js("window.financeApp.summary()")); }
    private JsonNode debt(String id) throws Exception {
        for (JsonNode debt : state().path("debts")) if (id.equals(debt.path("id").asText())) return debt;
        throw new AssertionError("Debt not found: " + id);
    }

    private void pay(String id, String amount, String account) throws Exception {
        click("[data-tab=borclar]");
        click("[data-pay=" + id + "]");
        value("od-tutar", amount);
        value("od-hesap", account);
        click("#bt-odeme-kaydet");
    }

    @Test void allEightScreensRenderOfflineAndPreviewIsCaptured() throws Exception {
        open(true);
        for (String tab : new String[]{"panel", "borclar", "abonelik", "kasa", "plan", "hedef", "akis", "ayarlar"}) {
            click("[data-tab=" + tab + "]");
            assertTrue(((Number) js("document.getElementById('bt-" + tab + "').textContent.length")).intValue() > 30, tab);
            assertEquals(false, js("document.getElementById('bt-" + tab + "').classList.contains('bt-hidden')"));
        }
        assertEquals(LocalDate.now().toString(), summary().path("today").asText());
        assertEquals(4000, summary().path("debt").asInt());
        assertEquals(600, summary().path("receivable").asInt());
        assertEquals(1200, summary().path("period").asInt());
        click("[data-tab=panel]");
        assertEquals("rgb(52, 62, 80)", js("getComputedStyle(document.querySelector('[data-tab=panel]')).color"));
        var snapshot = new java.util.concurrent.CompletableFuture<WritableImage>();
        fx(() -> {
            view.webView().snapshot(result -> { snapshot.complete(result.getImage()); return null; }, null, null);
            return null;
        });
        WritableImage image = snapshot.get(5, TimeUnit.SECONDS);
        var pixels = image.getPixelReader();
        var png = new java.awt.image.BufferedImage((int) image.getWidth(), (int) image.getHeight(), java.awt.image.BufferedImage.TYPE_INT_ARGB);
        for (int y=0; y<png.getHeight(); y++) for (int x=0; x<png.getWidth(); x++) png.setRGB(x,y,pixels.getArgb(x,y));
        Path screenshot = Path.of("build/reports/finance-preview.png");
        Files.createDirectories(screenshot.getParent());
        javax.imageio.ImageIO.write(png, "png", screenshot.toFile());
    }

    @Test void createsDebtWithTurkishAmountAndPersistsAcrossReopening() throws Exception {
        open(false);
        assertFalse(Files.exists(file));
        click("[data-tab=borclar]"); click("#bt-new");
        value("f-type", "elden");
        js("document.getElementById('f-type').dispatchEvent(new Event('input',{bubbles:true}))");
        value("f-name", "Test borcu"); value("f-balance", "1.250,50"); value("f-due", "2026-12-31");
        click("#bt-save");
        assertEquals(1250.50, state().path("debts").get(0).path("balance").asDouble(), .001);
        assertEquals(1, mapper.readTree(Files.readString(file)).path("debts").size());
        close(); open(false);
        assertEquals("Test borcu", state().path("debts").get(0).path("name").asText());
    }

    @Test void createsAndEditsSubscriptionsAccountsIncomesGoalsAndCashMovements() throws Exception {
        open(false);
        click("[data-tab=abonelik]");
        value("sb-name", "Telefon"); value("sb-amount", "150,75"); value("sb-day", "20");
        click("#bt-sub-save");
        assertEquals(150.75, state().path("subs").get(0).path("amount").asDouble(), .001);
        String subId = state().path("subs").get(0).path("id").asText();
        click("[data-subedit=\"" + subId + "\"]");
        value("sb-amount", "175"); click("#bt-sub-save");
        assertEquals(1, state().path("subs").size());
        assertEquals(175, state().path("subs").get(0).path("amount").asInt());
        click("[data-tab=kasa]");
        value("ac-name", "Cüzdan"); value("ac-balance", "2.000,50"); click("#bt-acc-save");
        assertEquals(2000.50, summary().path("cash").asDouble(), .001);
        click("[data-tab=plan]");
        value("in-name", "Ek gelir"); value("in-amount", "500"); value("in-date", "2026-12-01");
        click("#bt-inc-save");
        assertEquals(1, state().path("incomes").size());
        click("[data-tab=hedef]");
        value("hd-name", "Kitap"); value("hd-amount", "750"); click("#bt-goal-save");
        assertEquals(1, state().path("goals").size());
        click("[data-tab=akis]");
        value("fl-amount", "25,50"); value("fl-date", "2026-09-30"); click("#bt-flow-add");
        assertEquals(25.50, state().path("flows").get(0).path("amount").asDouble(), .001);
        String flowId = state().path("flows").get(0).path("id").asText();
        click("[data-delflow=\"" + flowId + "\"]");
        assertEquals(0, state().path("flows").size());
        assertEquals(1, mapper.readTree(Files.readString(file)).path("goals").size());
    }

    @Test void partialCardPaymentFreezesTargetAndUpdatesChosenAccount() throws Exception {
        open(true);
        pay("card", "50", "bank");
        assertEquals(950, debt("card").path("balance").asInt());
        assertEquals("2026-10-31", debt("card").path("due").asText());
        assertEquals(200, debt("card").path("donemHedef").asInt());
        assertEquals(4950, state().path("accounts").get(0).path("balance").asInt());
        pay("card", "150", "bank");
        assertEquals(800, debt("card").path("balance").asInt());
        assertEquals("2026-11-30", debt("card").path("due").asText());
        assertEquals(4800, state().path("accounts").get(0).path("balance").asInt());
        assertEquals(2, state().path("flows").size());
    }

    @Test void receivableCollectionIsIncomeAndCannotExceedRemainingAmount() throws Exception {
        open(true);
        pay("receivable", "100,25", "bank");
        assertEquals(499.75, debt("receivable").path("balance").asDouble(), .001);
        assertEquals("in", state().path("flows").get(0).path("type").asText());
        assertEquals(5100.25, state().path("accounts").get(0).path("balance").asDouble(), .001);
        assertEquals(4000, summary().path("debt").asInt());
        pay("receivable", "900", "bank");
        assertEquals(1, state().path("flows").size());
        assertEquals(499.75, debt("receivable").path("balance").asDouble(), .001);
    }

    @Test void partialLoanPaymentReducesOutstandingAndFinalPaymentClosesLoan() throws Exception {
        open(true);
        pay("loan", "250", "bank");
        assertEquals("3", debt("loan").path("kalanTaksit").asText());
        assertEquals(3750, summary().path("debt").asInt());
        pay("loan", "2750", "");
        assertEquals("0", debt("loan").path("kalanTaksit").asText());
        assertEquals(1000, summary().path("debt").asInt());
        assertEquals(200, summary().path("period").asInt());
    }

    @Test void jsonAndCsvImportsRoundTripAndRejectMalformedInput() throws Exception {
        open(true);
        pay("card", "50", "");
        String csv = (String)js("window.financeApp.exportCsv()");
        assertEquals(true, js("window.financeApp.importText(" + mapper.writeValueAsString(csv) + ",'replace')"));
        assertEquals(950, debt("card").path("balance").asInt());
        assertEquals("card", state().path("flows").get(0).path("debtId").asText());
        String saved = state().toString();
        assertEquals(false, js("window.financeApp.importText('{broken','replace')"));
        assertEquals(saved, state().toString());
        assertEquals(true, js("window.financeApp.importText(" + mapper.writeValueAsString(saved) + ",'merge')"));
        assertEquals(3, state().path("debts").size());
    }

    @Test void failedSaveRollsBackDisplayedBalancesAndPreservesDisk() throws Exception {
        open(true);
        String original = Files.readString(file);
        Path backup = Files.createDirectory(file.resolveSibling("ledger.json.bak"));
        Files.writeString(backup.resolve("keep"), "keep");
        pay("card", "50", "bank");
        assertEquals(1000, debt("card").path("balance").asInt());
        assertEquals(5000, state().path("accounts").get(0).path("balance").asInt());
        assertEquals(0, state().path("flows").size());
        assertTrue(((String)js("document.getElementById('bt-sync').textContent")).contains("kaydedilemedi"));
        assertEquals(original, Files.readString(file));
    }
}
