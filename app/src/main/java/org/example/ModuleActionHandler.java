package org.example;

import javafx.geometry.Pos;
import javafx.scene.layout.VBox;

import java.nio.file.Path;

public class ModuleActionHandler {

    private final ScriptModuleRunner scriptModuleRunner;
    private final UartConsoleView uartConsoleView;
    private final ReferenceLibraryView referenceLibraryView;
    private final CalendarModuleView calendarModuleView;
    private final Path projectRoot;
    private ScriptModuleView scriptView;
    private FinanceModuleView financeView;
    private CourseModuleView courseView;
    private TodayModuleView todayView;
    private final java.util.function.Consumer<String> navigate;
    private final FocusTimerService focus;
    private WeeklyAgendaView weeklyView;
    FocusTimerService focusTimer() { return focus; }
    void startFocusShortcut(javafx.stage.Window owner, java.util.function.Consumer<String> failure) {
        javafx.concurrent.Task<Void> task=new javafx.concurrent.Task<>() {
            @Override protected Void call() throws Exception {
                if(focus.running())return null;
                var now=java.time.LocalDateTime.now();
                var block=new StudyStorageService(projectRoot.resolve("data/study/sessions.json")).load().stream()
                    .filter(s->!s.done()&&!java.time.LocalDate.parse(s.date()).atTime(java.time.LocalTime.parse(s.end())).isBefore(now))
                    .sorted(java.util.Comparator.comparing(StudySession::date).thenComparing(StudySession::start)).findFirst()
                    .orElseThrow(()->new IllegalStateException("Önce haftalık planda bir çalışma bloğu oluştur"));
                var prefs=PlanningPreferences.load(projectRoot);
                focus.startSession(block.id(),prefs.pomodoro()?prefs.workMinutes():Math.min(180,Math.max(1,PlanningPreferences.minute(block.end())-PlanningPreferences.minute(block.start())-block.actualMinutes())));return null;
            }
        };
        task.setOnSucceeded(e->new FocusModeView(projectRoot,focus).show(owner));
        task.setOnFailed(e->failure.accept(task.getException().getMessage()));Thread.ofVirtual().start(task);
    }
    private ClassroomModuleView classroomView;
    private SettingsModuleView settingsView;
    private final GoogleClassroomService classroomService;
    private final java.util.function.Consumer<Boolean> busyUi;
    private final Runnable requireRestart;
    private final CloudSyncService cloud;
    private CloudSyncModuleView cloudView;

    public ModuleActionHandler(
            ScriptModuleRunner scriptModuleRunner,
            UartConsoleView uartConsoleView,
            ReferenceLibraryView referenceLibraryView,
            CalendarModuleView calendarModuleView,
            Path projectRoot,
            java.util.function.Consumer<String> navigate
    ) {
        this(scriptModuleRunner, uartConsoleView, referenceLibraryView, calendarModuleView, projectRoot, navigate, busy -> {}, () -> {});
    }
    public ModuleActionHandler(ScriptModuleRunner scriptModuleRunner, UartConsoleView uartConsoleView,
                               ReferenceLibraryView referenceLibraryView, CalendarModuleView calendarModuleView,
                               Path projectRoot, java.util.function.Consumer<String> navigate,
                               java.util.function.Consumer<Boolean> busyUi, Runnable requireRestart) {
        this.scriptModuleRunner = scriptModuleRunner;
        this.uartConsoleView = uartConsoleView;
        this.referenceLibraryView = referenceLibraryView;
        this.calendarModuleView = calendarModuleView;
        this.projectRoot = projectRoot;
        this.navigate = navigate;
        this.focus = new FocusTimerService(projectRoot.resolve("data/focus.properties"));
        this.classroomService = new GoogleClassroomService(projectRoot);
        this.busyUi = busyUi; this.requireRestart = requireRestart;
        cloud = new CloudSyncService(projectRoot,calendarModuleView.googleService(),new DriveSyncService(projectRoot));
        cloud.addDataListener(() -> javafx.application.Platform.runLater(() -> {
            calendarModuleView.refreshFromBackground();
            if (todayView != null) todayView.refreshFromBackground();
            if (weeklyView != null) weeklyView.refreshFromBackground();
        }));
        try {
            focus.creditCompleted();
            int minutes = AppPreferences.load(projectRoot).focusMinutes();
            if (!focus.running() && focus.remainingMillis()==focus.durationMillis() && !focus.targetKind().equals("session")) focus.configureMinutes(minutes);
        } catch (Exception ignored) { /* Settings and the focus card expose invalid stored preferences. */ }
    }

    public boolean handleInternalModule(
            ModuleItem module,
            VBox centerArea,
            StatusUpdater statusUpdater,
            TextCenterShower textCenterShower
    ) {
        try {
            String moduleId = module.getId();

            if (moduleId == null || moduleId.isBlank()) {
                textCenterShower.show("Internal modül kimliği tanımsız");
                statusUpdater.update("Durum: Hata");
                return true;
            }

            switch (moduleId.toLowerCase()) {
                case "sync":
                    cloudView = new CloudSyncModuleView(cloud); VBox syncPanel=cloudView.build(); centerArea.getChildren().setAll(syncPanel);
                    VBox.setVgrow(syncPanel,javafx.scene.layout.Priority.ALWAYS);statusUpdater.update("Durum: Google senkronizasyonu açık");return true;
                case "today":
                    todayView = new TodayModuleView(projectRoot, navigate, focus, false);
                    VBox todayPanel = todayView.build();
                    centerArea.getChildren().setAll(todayPanel);
                    VBox.setVgrow(todayPanel, javafx.scene.layout.Priority.ALWAYS);
                    statusUpdater.update("Durum: Bugün açık");
                    return true;
                case "agenda":
                    todayView = new TodayModuleView(projectRoot, navigate, focus, true);
                    VBox agendaPanel = todayView.build();
                    javafx.scene.control.TabPane agendaTabs = new javafx.scene.control.TabPane();
                    var daily = new javafx.scene.control.Tab("Günlük", agendaPanel); daily.setClosable(false);
                    weeklyView = new WeeklyAgendaView(projectRoot, calendarModuleView.googleService(), date -> {
                        todayView.showDate(date); agendaTabs.getSelectionModel().select(daily);
                    }, navigate, focus);
                    var weekly = new javafx.scene.control.Tab("Haftalık", weeklyView.build()); weekly.setClosable(false);
                    agendaTabs.getTabs().setAll(daily, weekly); agendaTabs.getSelectionModel().select(weekly); centerArea.getChildren().setAll(agendaTabs);
                    VBox.setVgrow(agendaTabs, javafx.scene.layout.Priority.ALWAYS);
                    statusUpdater.update("Durum: Ajanda açık");
                    return true;
                case "clasroom":
                case "classroom":
                    classroomView = new ClassroomModuleView(classroomService, navigate);
                    VBox classroomPanel = classroomView.build(); centerArea.getChildren().setAll(classroomPanel);
                    VBox.setVgrow(classroomPanel, javafx.scene.layout.Priority.ALWAYS);
                    statusUpdater.update("Durum: Classroom açık"); return true;
                case "settings":
                    settingsView = new SettingsModuleView(projectRoot, focus, calendarModuleView.googleService(), classroomService, busyUi, requireRestart, cloud.drive());
                    VBox settings = settingsView.build(); centerArea.getChildren().setAll(settings); VBox.setVgrow(settings, javafx.scene.layout.Priority.ALWAYS);
                    statusUpdater.update("Durum: Ayarlar açık"); return true;
                case "tasks":
                    VBox tasksPanel = calendarModuleView.buildTasks(projectRoot.resolve("data/tasks.json"));
                    centerArea.getChildren().setAll(tasksPanel);
                    VBox.setVgrow(tasksPanel, javafx.scene.layout.Priority.ALWAYS);
                    statusUpdater.update("Durum: Görevler açık");
                    return true;
                case "courses":
                    courseView = new CourseModuleView(projectRoot.resolve("data/courses/academic.json"));
                    VBox coursePanel = courseView.build();
                    centerArea.getChildren().setAll(coursePanel);
                    VBox.setVgrow(coursePanel, javafx.scene.layout.Priority.ALWAYS);
                    statusUpdater.update("Durum: Dersler açık");
                    return true;
                case "finance":
                    financeView = new FinanceModuleView();
                    VBox financePanel = financeView.build(projectRoot.resolve("data/finance/ledger.json"));
                    centerArea.getChildren().setAll(financePanel);
                    VBox.setVgrow(financePanel, javafx.scene.layout.Priority.ALWAYS);
                    statusUpdater.update("Durum: Borç Defteri açık");
                    return true;

                case "uart":
                    centerArea.getChildren().clear();
                    centerArea.getChildren().add(uartConsoleView.build());
                    VBox.setVgrow(centerArea.getChildren().getFirst(), javafx.scene.layout.Priority.ALWAYS);
                    centerArea.setAlignment(Pos.TOP_LEFT);
                    statusUpdater.update("Durum: UART Konsolu açık");
                    return true;

                case "reference":
                    centerArea.getChildren().clear();
                    centerArea.getChildren().add(referenceLibraryView.build(projectRoot.resolve("data/reference-topics.json")));
                    VBox.setVgrow(centerArea.getChildren().getFirst(), javafx.scene.layout.Priority.ALWAYS);
                    centerArea.setAlignment(Pos.TOP_LEFT);
                    statusUpdater.update("Durum: Referans açık");
                    return true;

                case "calendar":
                    centerArea.getChildren().clear();
                    centerArea.getChildren().add(calendarModuleView.build(projectRoot.resolve("data/tasks.json")));
                    VBox.setVgrow(centerArea.getChildren().getFirst(), javafx.scene.layout.Priority.ALWAYS);
                    centerArea.setAlignment(Pos.TOP_LEFT);
                    statusUpdater.update("Durum: Takvim açık");
                    return true;

                default:
                    textCenterShower.show(module.getMessage() != null ? module.getMessage() : "Internal modül açıldı");
                    statusUpdater.update("Durum: " + module.getName() + " aktif");
                    return true;
            }
        } catch (Exception e) {
            textCenterShower.show("Internal modül açılırken hata: " + e.getMessage());
            statusUpdater.update("Durum: Hata");
            e.printStackTrace();
            return true;
        }
    }

    public void handleScriptModule(ModuleItem module, VBox centerArea, StatusUpdater statusUpdater) {
        cancelScript();
        scriptView = new ScriptModuleView();
        VBox view = scriptView.build(module, projectRoot, scriptModuleRunner, statusUpdater);
        centerArea.getChildren().setAll(view);
        VBox.setVgrow(view, javafx.scene.layout.Priority.ALWAYS);
    }

    public void leaveModule() {
        if (cloudView != null) { cloudView.dispose(); cloudView=null; }
        calendarModuleView.dispose();
        if (settingsView != null) { settingsView.dispose(); settingsView = null; }
        if (classroomView != null) { classroomView.dispose(); classroomView = null; }
        if (weeklyView != null) { weeklyView.dispose(); weeklyView = null; }
        if (courseView != null) { courseView.dispose(); courseView = null; }
        if (todayView != null) { todayView.dispose(); todayView = null; }
        cancelScript();
        if (financeView != null) {
            financeView.dispose();
            financeView = null;
        }
    }
    void startBackgroundSync() { cloud.start(); }
    void stopBackgroundSync() { cloud.close(); }

    public void cancelScript() {
        if (scriptView != null) {
            scriptView.dispose();
            scriptView = null;
        }
    }

    public boolean handleExternalWebModule(
            ModuleItem module,
            TextCenterShower textCenterShower,
            StatusUpdater statusUpdater
    ) {
        try {
            if (module.getTarget() == null || module.getTarget().isBlank()) {
                textCenterShower.show("Web hedefi tanımlanmamış");
                statusUpdater.update("Durum: Hata");
                return true;
            }

            DesktopIntegration.browse(module.getTarget());
            textCenterShower.show(module.getMessage() != null ? module.getMessage() : module.getName() + " dış tarayıcıda açıldı");
            statusUpdater.update("Durum: " + module.getName() + " dış tarayıcıda açıldı");
            return true;
        } catch (Exception e) {
            textCenterShower.show("İşlem hatası: " + e.getMessage());
            statusUpdater.update("Durum: Hata");
            e.printStackTrace();
            return true;
        }
    }

    @FunctionalInterface
    public interface StatusUpdater {
        void update(String text);
    }

    @FunctionalInterface
    public interface TextCenterShower {
        void show(String text);
    }
}
