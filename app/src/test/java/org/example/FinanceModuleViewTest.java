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
        click("[data-pay=\"" + id + "\"]");
        value("od-tutar", amount);
        value("od-hesap", account);
        click("#bt-odeme-kaydet");
    }

    @Test void allEightScreensRenderOfflineAndPreviewIsCaptured() throws Exception {
        open(true);
        for (String tab : new String[]{"panel", "borclar", "abonelik", "kasa", "taksit", "ekstre", "plan", "hedef", "akis", "ayarlar"}) {
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
        capture("finance-preview");
    }

    private void capture(String name) throws Exception {
        var snapshot = new java.util.concurrent.CompletableFuture<WritableImage>();
        fx(() -> {
            view.webView().snapshot(result -> { snapshot.complete(result.getImage()); return null; }, null, null);
            return null;
        });
        WritableImage image = snapshot.get(5, TimeUnit.SECONDS);
        var pixels = image.getPixelReader();
        var png = new java.awt.image.BufferedImage((int) image.getWidth(), (int) image.getHeight(), java.awt.image.BufferedImage.TYPE_INT_ARGB);
        for (int y=0; y<png.getHeight(); y++) for (int x=0; x<png.getWidth(); x++) png.setRGB(x,y,pixels.getArgb(x,y));
        Path screenshot = Path.of("build/reports/" + name + ".png");
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

    private void newCard(String period, String total) throws Exception {
        click("[data-tab=borclar]"); click("#bt-new");
        value("f-name", "WORLD"); value("f-limit", "15000");
        value("f-balance", period); value("f-total-balance", total);
        value("f-due", "2026-01-01");
        click("#bt-save");
    }

    @Test void futureCardDebtDoesNotBecomeCurrentMinimumOrOverdueDebt() throws Exception {
        open(false);
        newCard("0", "12.440,52");
        assertEquals(1, state().path("debts").size());
        assertEquals(12440.52, summary().path("debt").asDouble(), .001);
        assertEquals(0, summary().path("period").asDouble(), .001);
        assertEquals(0, ((Number)js("document.querySelectorAll('#bt-borclar .bt-chip.red').length")).intValue());
        capture("finance-card-balances");
        click("[data-tab=panel]");
        assertFalse(((String)js("document.getElementById('bt-panel').textContent")).contains("gün önce geçti"));
        close(); open(false);
        assertEquals(0, summary().path("period").asDouble(), .001);
        assertEquals(12440.52, summary().path("debt").asDouble(), .001);
    }

    @Test void cardFormDistinguishesEmptyZeroAndOneCentAndKeepsInvalidInput() throws Exception {
        open(false);
        js("window.testErrors=[];window.addEventListener('error',e=>window.testErrors.push(e.message))");
        click("[data-tab=borclar]"); click("#bt-new");
        value("f-name", "Küçük tutar"); value("f-balance", "");
        click("#bt-save");
        assertEquals(0, state().path("debts").size());
        assertEquals("Küçük tutar", js("document.getElementById('f-name').value"));
        value("f-balance", "0,01"); click("#bt-save");
        assertEquals(.01, state().path("debts").get(0).path("balance").asDouble(), .00001);
        assertEquals(.01, summary().path("debt").asDouble(), .00001);
        String id = state().path("debts").get(0).path("id").asText();
        click("[data-toggle=\"" + id + "\"]");
        assertEquals(0, ((Number)js("window.testErrors.length")).intValue());
        close(); open(false);
        assertEquals(.01, state().path("debts").get(0).path("balance").asDouble(), .00001);
    }

    @Test void explicitCardMinimumPaymentDoesNotInventANewStatement() throws Exception {
        open(false);
        newCard("100", "1000");
        String id = state().path("debts").get(0).path("id").asText();
        pay(id, "5", "");
        assertEquals(15, summary().path("period").asInt());
        pay(id, "15", "");
        assertEquals(80, debt(id).path("balance").asInt());
        assertEquals(980, debt(id).path("totalBalance").asInt());
        assertEquals("2026-01-01", debt(id).path("due").asText());
        assertEquals(0, summary().path("period").asInt());
        pay(id, "100", "");
        assertEquals(80, debt(id).path("balance").asInt());
        pay(id, "80", "");
        assertEquals(0, debt(id).path("balance").asInt());
        assertEquals(900, summary().path("debt").asInt());
        assertEquals(0, summary().path("period").asInt());
    }

    @Test void cashFlowEditAccountChangeDeleteAndReloadApplyExactlyOnce() throws Exception {
        open(true);
        click("[data-tab=akis]");
        value("fl-account", "bank"); value("fl-amount", "-25,50");
        click("#bt-flow-add");
        assertEquals(4974.50, summary().path("cash").asDouble(), .001);
        String id = state().path("flows").get(0).path("id").asText();
        click("[data-editflow=\"" + id + "\"]");
        value("fl-amount", "40"); click("#bt-flow-add");
        assertEquals(4960, summary().path("cash").asInt());
        click("[data-editflow=\"" + id + "\"]");
        value("fl-type", "in"); value("fl-amount", "10"); click("#bt-flow-add");
        assertEquals(5010, summary().path("cash").asInt());
        close(); open(false);
        assertEquals(5010, summary().path("cash").asInt());
        click("[data-tab=akis]"); click("[data-editflow=\"" + id + "\"]");
        value("fl-account", ""); click("#bt-flow-add");
        assertEquals(5000, summary().path("cash").asInt());
        click("[data-delflow=\"" + id + "\"]");
        assertEquals(0, state().path("flows").size());
        assertEquals(5000, summary().path("cash").asInt());
    }

    @Test void flowReversalRestoresRestrictedCashAndInvalidExpenseDoesNotMutateState() throws Exception {
        open(true);
        click("[data-tab=akis]");
        value("fl-account", "bank"); value("fl-amount", "4980"); click("#bt-flow-add");
        assertEquals(20, summary().path("cash").asInt());
        assertEquals(20, state().path("accounts").get(0).path("kisitli").asInt());
        String id = state().path("flows").get(0).path("id").asText();
        click("[data-delflow=\"" + id + "\"]");
        assertEquals(5000, summary().path("cash").asInt());
        assertEquals(500, state().path("accounts").get(0).path("kisitli").asInt());
        value("fl-amount", "5001"); value("fl-account", "bank"); click("#bt-flow-add");
        assertEquals(0, state().path("flows").size());
        assertEquals(5000, summary().path("cash").asInt());
    }

    @Test void failedCashFlowSavePreservesBalancesAndShowsErrorWithoutUnhandledScriptError() throws Exception {
        open(true);
        String original = Files.readString(file);
        Path backup = Files.createDirectory(file.resolveSibling("ledger.json.bak"));
        Files.writeString(backup.resolve("keep"), "keep");
        js("window.testErrors=[];window.addEventListener('error',e=>window.testErrors.push(e.message))");
        click("[data-tab=akis]");
        value("fl-account", "bank"); value("fl-amount", "0,01"); click("#bt-flow-add");
        assertEquals(5000, summary().path("cash").asInt());
        assertEquals(0, state().path("flows").size());
        assertEquals(original, Files.readString(file));
        assertEquals(0, ((Number)js("window.testErrors.length")).intValue());
        assertTrue(((String)js("document.getElementById('bt-error').textContent")).contains("Kaydedilemedi"));
        assertEquals("0,01", js("document.getElementById('fl-amount').value"));
    }

    @Test void movingFlowToAnotherAccountAndDeletingItPreservesBothBalances() throws Exception {
        open(true);
        click("[data-tab=kasa]"); value("ac-name", "Cüzdan"); value("ac-balance", "100"); click("#bt-acc-save");
        String wallet = state().path("accounts").get(1).path("id").asText();
        click("[data-tab=akis]"); value("fl-account", "bank"); value("fl-amount", "30"); click("#bt-flow-add");
        String id = state().path("flows").get(0).path("id").asText();
        click("[data-editflow=\"" + id + "\"]"); value("fl-account", wallet); click("#bt-flow-add");
        assertEquals(5000, state().path("accounts").get(0).path("balance").asInt());
        assertEquals(70, state().path("accounts").get(1).path("balance").asInt());
        assertEquals(1, state().path("flows").size());
        click("[data-tab=kasa]"); click("[data-accdel=\"" + wallet + "\"]");
        assertEquals(2, state().path("accounts").size());
        click("[data-tab=akis]"); click("[data-delflow=\"" + id + "\"]");
        assertEquals(100, state().path("accounts").get(1).path("balance").asInt());
        assertEquals(5100, summary().path("cash").asInt());
    }

    @Test void csvAndJsonRoundTripsKeepCardTotalsAndDoNotReplayAccountEffects() throws Exception {
        open(true);
        newCard("0", "12440,52");
        click("[data-tab=akis]"); value("fl-account", "bank"); value("fl-amount", "4980"); click("#bt-flow-add");
        String id = state().path("flows").get(0).path("id").asText();
        String csv = (String)js("window.financeApp.exportCsv()");
        assertEquals(true, js("window.financeApp.importText(" + mapper.writeValueAsString(csv) + ",'replace')"));
        assertEquals(20, summary().path("cash").asInt());
        assertEquals(16440.52, summary().path("debt").asDouble(), .001);
        String json = state().toString();
        assertEquals(true, js("window.financeApp.importText(" + mapper.writeValueAsString(json) + ",'merge')"));
        assertEquals(20, summary().path("cash").asInt());
        click("[data-delflow=\"" + id + "\"]");
        assertEquals(5000, summary().path("cash").asInt());
        assertEquals(500, state().path("accounts").get(0).path("kisitli").asInt());
    }

    @Test void editingPaidCardMetadataPreservesMinimumAndNewStatementResetsIt() throws Exception {
        open(false);
        newCard("100", "1000");
        String id = state().path("debts").get(0).path("id").asText();
        pay(id, "5", "");
        click("[data-edit=\"" + id + "\"]"); value("f-note", "Ekstre notu"); click("#bt-save");
        assertEquals(15, summary().path("period").asInt());
        click("[data-edit=\"" + id + "\"]"); value("f-balance", "200"); value("f-due", "2026-12-31"); click("#bt-save");
        assertEquals(40, summary().path("period").asInt());
        assertEquals(995, summary().path("debt").asInt());
        assertEquals(1, state().path("debts").size());
    }

    @Test void invalidCardAmountsAndFailedEditKeepInputForRetry() throws Exception {
        open(true);
        click("[data-tab=borclar]"); click("[data-edit=card]");
        value("f-balance", "0,000001"); click("#bt-save");
        assertEquals(1000, debt("card").path("balance").asInt());
        assertEquals("0,000001", js("document.getElementById('f-balance').value"));
        value("f-balance", "1000");
        value("f-total-balance", "1"); click("#bt-save");
        assertEquals(1000, debt("card").path("balance").asInt());
        assertEquals("1", js("document.getElementById('f-total-balance').value"));
        value("f-total-balance", "1000"); value("f-limit", "yanlış"); click("#bt-save");
        assertEquals("yanlış", js("document.getElementById('f-limit').value"));
        value("f-limit", "50000");
        Path backup = Files.createDirectory(file.resolveSibling("ledger.json.bak"));
        Files.writeString(backup.resolve("keep"), "keep");
        value("f-note", "Yeniden dene"); click("#bt-save");
        assertEquals(3, state().path("debts").size());
        assertEquals("Yeniden dene", js("document.getElementById('f-note').value"));
        Files.delete(backup.resolve("keep")); Files.delete(backup);
        click("#bt-save");
        assertEquals(3, state().path("debts").size());
        assertEquals("Yeniden dene", debt("card").path("note").asText());
    }

    private void installment(String amount, String count, String paid) throws Exception {
        click("[data-tab=taksit]"); value("ip-card", "card"); value("ip-name", "Kitap alışverişi");
        value("ip-amount", amount); value("ip-count", count); value("ip-paid", paid); value("ip-date", "2027-01-31");
        click("#bt-installment-save");
    }

    private String wallet() throws Exception {
        click("[data-tab=kasa]"); value("ac-name", "Cüzdan"); value("ac-balance", "100"); click("#bt-acc-save");
        return state().path("accounts").get(1).path("id").asText();
    }

    private void transfer(String target, String amount, String reserved) throws Exception {
        click("[data-tab=kasa]"); click("#bt-transfer-open");
        value("tr-from", "bank"); value("tr-to", target); value("tr-amount", amount); value("tr-reserved", reserved);
        value("tr-date", "2026-11-22"); value("tr-note", "Cüzdana aktarım"); click("#bt-transfer-save");
    }

    private void statement(String csv) throws Exception {
        click("[data-tab=ekstre]"); value("st-text", csv); click("#bt-statement-prepare");
        value("st-account", "bank"); click("#bt-statement-compare");
    }

    @Test void installmentDatesAmountsAndProgressRemainSeparateFromActualCardDebt() throws Exception {
        open(true);
        installment("100", "3", "1");
        assertEquals(1, state().path("installments").size());
        JsonNode schedule = mapper.readTree((String)js("JSON.stringify(window.financeTools.installments(JSON.parse(window.financeApp.exportState()).installments[0]))"));
        assertEquals("2027-02-28", schedule.get(1).path("date").asText());
        assertEquals("2027-03-31", schedule.get(2).path("date").asText());
        assertEquals(33.33, schedule.get(1).path("amount").asDouble(), .00001);
        assertEquals(33.34, schedule.get(2).path("amount").asDouble(), .00001);
        assertEquals(4000, summary().path("debt").asInt());
        String id=state().path("installments").get(0).path("id").asText();
        click("[data-editinstallment=\""+id+"\"]"); value("ip-paid", "2"); click("#bt-installment-save");
        assertEquals(2, state().path("installments").get(0).path("paidCount").asInt());
        capture("finance-installments");
        close(); open(false);
        assertEquals(2, state().path("installments").get(0).path("paidCount").asInt());
        click("[data-tab=taksit]"); click("[data-delinstallment=\""+id+"\"]");
        assertEquals(0, state().path("installments").size());
        installment("0,01", "3", "0");
        assertEquals(0, state().path("installments").size());
        assertEquals(4000, summary().path("debt").asInt());
    }

    @Test void transferEditAndDeletePreserveTotalCashAndMoveReservedMoney() throws Exception {
        open(true); String wallet=wallet();
        transfer(wallet,"120","20");
        assertEquals(1, state().path("flows").size());
        assertEquals("transfer", state().path("flows").get(0).path("type").asText());
        assertEquals(5100, summary().path("cash").asInt());
        assertEquals(4880, state().path("accounts").get(0).path("balance").asInt());
        assertEquals(480, state().path("accounts").get(0).path("kisitli").asInt());
        assertEquals(20, state().path("accounts").get(1).path("kisitli").asInt());
        String id=state().path("flows").get(0).path("id").asText();
        click("[data-tab=akis]");
        assertEquals(0, ((Number)js("JSON.parse(window.financeApp.exportState()).flows.filter(f=>f.type==='in'||f.type==='out').length")).intValue());
        click("[data-editflow=\""+id+"\"]"); value("tr-amount", "150"); value("tr-reserved", "50"); click("#bt-transfer-save");
        assertEquals(4850, state().path("accounts").get(0).path("balance").asInt());
        assertEquals(450, state().path("accounts").get(0).path("kisitli").asInt());
        close(); open(false);
        assertEquals(5100, summary().path("cash").asInt());
        click("[data-tab=akis]");value("fl-account",wallet);value("fl-amount","200");click("#bt-flow-add");
        String spent=state().toString(), expense=state().path("flows").get(1).path("id").asText();
        click("[data-tab=kasa]");click("[data-deltransfer=\""+id+"\"]");
        assertEquals(spent,state().toString());
        click("[data-tab=akis]");click("[data-delflow=\""+expense+"\"]");
        click("[data-tab=kasa]"); click("[data-deltransfer=\""+id+"\"]");
        assertEquals(0, state().path("flows").size());
        assertEquals(5000, state().path("accounts").get(0).path("balance").asInt());
        assertEquals(500, state().path("accounts").get(0).path("kisitli").asInt());
        assertEquals(100, state().path("accounts").get(1).path("balance").asInt());
    }

    @Test void invalidAndFailedTransferLeavesBothAccountsUntouched() throws Exception {
        open(true); String wallet=wallet();
        String before=state().toString();
        transfer("bank","50","0");
        assertEquals(before,state().toString());
        value("tr-to",wallet); value("tr-amount","4600"); click("#bt-transfer-save");
        assertEquals(before,state().toString());
        value("tr-amount","1"); value("tr-reserved","0");
        Path backup=file.resolveSibling("ledger.json.bak"); Files.deleteIfExists(backup); Files.createDirectory(backup);
        Files.writeString(backup.resolve("keep"),"keep");
        click("#bt-transfer-save");
        assertEquals(before,state().toString());
        assertEquals("1",js("document.getElementById('tr-amount').value"));
    }

    @Test void statementMatchesDuplicatesOnceAndReportsAmountAndDescriptionDifferencesWithoutWritingMoney() throws Exception {
        var data=FinanceTestData.populated();
        for(String[] row:new String[][]{{"market","out","25.50","Market"},{"rent","out","99","Kira"},{"shop","out","5","Ödeme"},{"only","out","2","Yalnız yerel"}}){
            data.withArray("flows").addObject().put("id",row[0]).put("type",row[1]).put("amount",Double.parseDouble(row[2]))
                    .put("note",row[3]).put("date","2026-11-22").put("accountId","bank");
        }
        var storage=new FinanceStorageService(directory.resolve("ledger.json")); storage.load();storage.save(data.toString());
        open(true);
        String cashBefore=state().path("accounts").toString(), flowsBefore=state().path("flows").toString();
        statement("tarih;aciklama;tutar\n22.11.2026;Market;-25,50\n22.11.2026;Market;-25,50\n22.11.2026;Kira;-100\n22.11.2026;Alışveriş;-5");
        String text=(String)js("document.getElementById('bt-ekstre').textContent");
        assertTrue(text.contains("1/4")); assertTrue(text.contains("Tutar farklı"));
        assertTrue(text.contains("Açıklama farklı")); assertTrue(text.contains("Yalnızca ekstrede")); assertTrue(text.contains("Yalnızca yerelde"));
        click("#bt-statement-save"); click("#bt-statement-save");
        assertEquals(1,state().path("statements").size());
        assertEquals(cashBefore,state().path("accounts").toString());
        assertEquals(flowsBefore,state().path("flows").toString());
        js("document.querySelector('#bt-ekstre .bt-hero-grid').scrollIntoView({block:'center'})");
        capture("finance-statement-comparison");
        close();open(false);
        String id=state().path("statements").get(0).path("id").asText();
        click("[data-tab=ekstre]");click("[data-openstatement=\""+id+"\"]");
        assertTrue(((String)js("document.getElementById('bt-ekstre').textContent")).contains("1/4"));
    }

    @Test void splitStatementCsvAndMalformedDatesAreHandledAndAllFeaturesRoundTrip() throws Exception {
        open(true); String wallet=wallet(); installment("100","3","0"); transfer(wallet,"100","0");
        statement("İşlem Tarihi,Açıklama,Borç,Alacak\n22.11.2026,Cüzdana aktarım,100,\n22.11.2026,\"Market, alışveriş\",\"25,50\",\n");
        assertTrue(((String)js("document.getElementById('bt-ekstre').textContent")).contains("1/2"));
        value("st-opening","5100");value("st-closing","4974,50");click("#bt-statement-compare");
        click("#bt-statement-save");
        String csv=(String)js("window.financeApp.exportCsv()");
        assertEquals(true,js("window.financeApp.importText("+mapper.writeValueAsString(csv)+",'replace')"));
        assertEquals(1,state().path("installments").size());assertEquals(1,state().path("statements").size());
        assertEquals("transfer",state().path("flows").get(0).path("type").asText());
        assertEquals(5100,summary().path("cash").asInt());
        String saved=state().toString();
        assertEquals(true,js("window.financeApp.importText("+mapper.writeValueAsString(saved)+",'merge')"));
        assertEquals(saved,state().toString());
        statement("tarih;aciklama;tutar\n31.02.2026;Bozuk;-1");
        assertTrue(((String)js("document.getElementById('bt-error').textContent")).contains("Geçersiz tarih"));
        assertEquals(saved,state().toString());
    }
}
