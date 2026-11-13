package org.example;

import javafx.concurrent.Task;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.*;
import java.nio.file.*;
import java.time.LocalTime;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.function.Consumer;

public final class SettingsModuleView {
    private final Path profile;
    private final FocusTimerService focus;
    private final GoogleCalendarService calendar;
    private final GoogleClassroomService classroom;
    private final DriveSyncService drive;
    private final Consumer<Boolean> busyChanged;
    private final VBox root = new VBox(16);
    private final Label status = new Label();
    private boolean busy;
    private final Runnable requireRestart;
    private UpdateSettingsView updateView;
    private UpdateController updates;
    SettingsModuleView updates(UpdateController controller) { updates = controller; return this; }
    public SettingsModuleView(Path profile, FocusTimerService focus, GoogleCalendarService calendar,
                              GoogleClassroomService classroom, Consumer<Boolean> busyChanged, Runnable requireRestart) {
        this(profile,focus,calendar,classroom,busyChanged,requireRestart,null);
    }
    public SettingsModuleView(Path profile, FocusTimerService focus, GoogleCalendarService calendar,
                              GoogleClassroomService classroom, Consumer<Boolean> busyChanged, Runnable requireRestart, DriveSyncService drive) {
        this.profile = profile; this.focus = focus; this.calendar = calendar; this.classroom = classroom; this.busyChanged = busyChanged;
        this.drive=drive;
        this.requireRestart = requireRestart;
    }
    public VBox build() {
        root.setId("settings-module"); status.setId("settings-status"); status.setWrapText(true);
        AppPreferences preferences;
        try { preferences = AppPreferences.load(profile); }
        catch (Exception error) { root.getChildren().setAll(new Label("Ayarlar okunamadı: " + error.getMessage())); return root; }
        Label path = new Label(profile.toString()); path.setWrapText(true); path.setId("settings-data-path");
        Button open = new Button("Veri klasörünü aç"), move = new Button("Veri klasörünü değiştir");
        open.setOnAction(e -> { try { Files.createDirectories(profile); DesktopIntegration.open(profile); } catch (Exception error) { status.setText(error.getMessage()); } });
        move.setOnAction(e -> moveProfile());
        move.setDisable(System.getProperty("academic.os.home") != null);
        Spinner<Integer> duration = new Spinner<>(1, 180, preferences.focusMinutes()); duration.setEditable(true); duration.setId("settings-focus-minutes");
        Spinner<Integer> lead = new Spinner<>(0, 1440, preferences.reminderMinutes()); lead.setEditable(true); lead.setId("settings-reminder-minutes");
        TextField time = new TextField(preferences.dateOnlyReminderTime()); time.setId("settings-date-reminder-time");
        CheckBox notify = new CheckBox("Teslim ve çalışma hatırlatmalarını göster"); notify.setSelected(preferences.remindersEnabled()); notify.setId("settings-reminders");
        CheckBox background = new CheckBox("Pencere kapatılınca sistem tepsisinde çalışmaya devam et"); background.setSelected(preferences.keepRunningOnClose()); background.setId("settings-background");
        Label tray = new Label(java.awt.SystemTray.isSupported() ? "Bildirimler uygulama sistem tepsisindeyken de çalışır. Tamamen çıkış yapıldığında durur."
                : "Bu ortam sistem tepsisini desteklemiyor; bildirimler yalnızca pencere açıkken çalışır."); tray.setWrapText(true);
        Button save = new Button("Tercihleri kaydet"); save.setId("settings-save");
        save.setOnAction(e -> {
            try {
                duration.commitValue(); lead.commitValue(); LocalTime.parse(time.getText().trim());
                if (focus.running() && duration.getValue() * 60000L != focus.durationMillis()) throw new IllegalStateException("Önce odak sayacını duraklat.");
                new AppPreferences(1, duration.getValue(), notify.isSelected(), lead.getValue(), time.getText().trim(), background.isSelected()).save(profile);
                focus.configureMinutes(duration.getValue()); status.setText("Çalışma ve bildirim tercihleri kaydedildi.");
            } catch (Exception error) { status.setText("Ayarlar kaydedilemedi: " + error.getMessage()); }
        });
        VBox work = panel("Çalışma ve bildirimler", new Label("Odak süresi (dakika)"), duration, notify,
                new Label("Kaç dakika önce hatırlatılsın?"), lead, new Label("Saati olmayan teslimler için hatırlatma saati"), time, background, tray, save);
        Button export = new Button("Tüm kayıtları yedekle"), restore = new Button("Yedekten geri yükle"); export.setId("backup-export"); restore.setId("backup-restore");
        export.setOnAction(e -> export()); restore.setOnAction(e -> restore());
        Label backupHint = new Label("Dersler, görevler, çalışmalar, finans, notlar ve tercihler ZIP dosyasına alınır. Google giriş tokenları yedeğe eklenmez."); backupHint.setWrapText(true);
        Button credentials = new Button("Google istemci dosyasını seç"), disconnect = new Button("Google hesaplarından çıkış yap");
        credentials.setId("settings-google-credentials"); disconnect.setId("settings-google-disconnect");
        credentials.setOnAction(e -> importCredentials()); disconnect.setOnAction(e -> run(() -> {
            calendar.disconnect(); classroom.disconnect();
            if (drive != null) drive.disconnect();
            new GoogleCalendarOAuthService().forget(profile.resolve("config/google-calendar-tokens"));
            new GoogleClassroomOAuthService().forget(profile.resolve("config/google-calendar-tokens"));
            new GoogleDriveOAuthService().forget(profile.resolve("config/google-calendar-tokens"));
            return "Yerel Google bağlantıları kaldırıldı. Takvim, Classroom veya Google senkronizasyonu ekranından yeniden hesap seçebilirsin.";
        }, status::setText));
        Label googleHint = new Label("Google Calendar, Classroom ve Drive API'ları açık bir Google Cloud masaüstü OAuth istemcisi gerekir. Her kullanıcı kendi Google hesabıyla giriş yapar."); googleHint.setWrapText(true);
        updateView = new UpdateSettingsView(updates);
        VBox content = new VBox(18, updateView.build(), panel("Veri klasörü", path, new FlowPane(10, 8, open, move)), work,
                plannerSettings(), panel("Yedekleme", backupHint, new FlowPane(10, 8, export, restore)), panel("Google bağlantıları", googleHint, credentials, disconnect), status);
        content.setMaxWidth(760); ScrollPane scroll = new ScrollPane(content); scroll.setFitToWidth(true);
        root.getChildren().setAll(scroll); VBox.setVgrow(scroll, Priority.ALWAYS); return root;
    }
    private VBox plannerSettings() {
        CheckBox writes=new CheckBox("Ajanların doğrudan değişiklik yapmasına izin ver");writes.setId("settings-agent-writes");
        CheckBox pomodoro=new CheckBox("Pomodoro döngüsü kullan");pomodoro.setId("settings-pomodoro");
        CheckBox deadlines=new CheckBox("Odak sırasında teslim uyarılarını göster");
        ComboBox<String> start=new ComboBox<>(),end=new ComboBox<>();for(int h=0;h<24;h++){start.getItems().add(String.format(java.util.Locale.ROOT,"%02d:00",h));end.getItems().add(String.format(java.util.Locale.ROOT,"%02d:00",h));}end.getItems().add("24:00");
        Spinner<Integer> daily=new Spinner<>(15,1440,240,15),min=new Spinner<>(15,180,15,15),max=new Spinner<>(15,180,120,15),work=new Spinner<>(1,180,25),shortBreak=new Spinner<>(1,60,5),longBreak=new Spinner<>(1,120,15),cycles=new Spinner<>(1,12,4);
        TextArea closed=new TextArea();closed.setPrefRowCount(3);closed.setId("settings-closed-times");closed.setPromptText("2026-11-13 18:00 23:59\ngün 5 18:00 23:59");
        Button save=new Button("Planlama tercihlerini kaydet");save.setId("settings-planner-save");
        VBox box=panel("Haftalık plan ve ajanlar",writes,new Label("Çalışma saatleri"),new HBox(8,start,end),new Label("Günlük azami dakika"),daily,new Label("En kısa / en uzun blok"),new HBox(8,min,max),new Label("Kapalı aralıklar: tarih veya gün 1–7, başlangıç, bitiş"),closed,pomodoro,new Label("Çalışma / kısa mola / uzun mola / döngü"),new FlowPane(8,8,work,shortBreak,longBreak,cycles),deadlines,save);
        PlanningPreferences[] loaded={PlanningPreferences.defaults()};box.setDisable(true);
        Task<PlanningPreferences> task=new Task<>(){@Override protected PlanningPreferences call()throws Exception{return PlanningPreferences.load(profile);}};
        task.setOnSucceeded(e->{var p=task.getValue();loaded[0]=p;writes.setSelected(p.agentWrites());pomodoro.setSelected(p.pomodoro());deadlines.setSelected(p.deadlineNotifications());start.setValue(p.start());end.setValue(p.end());daily.getValueFactory().setValue(p.dailyMinutes());min.getValueFactory().setValue(p.minMinutes());max.getValueFactory().setValue(p.maxMinutes());work.getValueFactory().setValue(p.workMinutes());shortBreak.getValueFactory().setValue(p.shortBreak());longBreak.getValueFactory().setValue(p.longBreak());cycles.getValueFactory().setValue(p.cycles());closed.setText(p.closed().stream().map(c->(c.date()==null||c.date().isBlank()?"gün "+c.day():c.date())+" "+c.start()+" "+c.end()).collect(java.util.stream.Collectors.joining("\n")));box.setDisable(false);});task.setOnFailed(e->status.setText("Planlama ayarları okunamadı; dosya korunuyor: "+task.getException().getMessage()));Thread.ofVirtual().start(task);
        save.setOnAction(e->{try{var old=loaded[0];java.util.List<PlanningPreferences.Closed> times=new java.util.ArrayList<>();for(String line:closed.getText().lines().filter(s->!s.isBlank()).toList()){String[] parts=line.trim().split("\\s+");if(parts.length==4&&parts[0].equalsIgnoreCase("gün"))times.add(new PlanningPreferences.Closed("",Integer.parseInt(parts[1]),parts[2],parts[3]));else if(parts.length==3)times.add(new PlanningPreferences.Closed(parts[0],0,parts[1],parts[2]));else throw new IllegalArgumentException("Kapalı aralık: tarih başlangıç bitiş veya gün 1–7 başlangıç bitiş");}
            var next=new PlanningPreferences(1,old.timezone(),start.getValue(),end.getValue(),daily.getValue(),min.getValue(),max.getValue(),times,writes.isSelected(),pomodoro.isSelected(),work.getValue(),shortBreak.getValue(),longBreak.getValue(),cycles.getValue(),deadlines.isSelected());next.validate();run(()->{new PlannerService(profile).execute(new PlannerCommand.SetPreferences(next));return next;},p->{loaded[0]=p;status.setText("Planlama tercihleri kaydedildi.");});}catch(Exception failure){status.setText(failure.getMessage());}});return box;
    }
    private void export() {
        FileChooser chooser = zipChooser("Yedeği kaydet"); chooser.setInitialFileName("academic-os-" + java.time.LocalDate.now() + ".zip");
        var file = chooser.showSaveDialog(window()); if (file == null) return;
        run(() -> { var preview = new BackupService(profile).exportTo(file.toPath()); return preview.files() + " dosya yedeklendi: " + file; }, status::setText);
    }
    private void restore() {
        var file = zipChooser("Yedek dosyasını seç").showOpenDialog(window()); if (file == null) return;
        run(() -> new BackupService(profile).inspect(file.toPath()), preview -> {
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION, preview.files() + " dosya geri yüklenecek. Mevcut kayıtlar yedekteki durumla değiştirilecek. "
                    + "Önce mevcut veriler otomatik yedeklenecek.", ButtonType.OK, ButtonType.CANCEL);
            UiTheme.styleDialog(confirm, root);
            if (confirm.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) run(() -> {
                Path before = new BackupService(profile).restoreFrom(file.toPath());
                return "Geri yüklendi. Uygulamayı yeniden başlat. Önceki durum yedeği: " + before;
            }, message -> { status.setText(message); requireRestart.run(); });
        });
    }
    private void importCredentials() {
        FileChooser chooser = new FileChooser(); chooser.setTitle("Google masaüstü OAuth istemci JSON dosyası");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("JSON", "*.json")); var file = chooser.showOpenDialog(window()); if (file == null) return;
        run(() -> {
            byte[] bytes = FinanceStorageService.readLimited(file.toPath());
            var tree = new com.fasterxml.jackson.databind.ObjectMapper().readTree(bytes);
            if (!tree.path("installed").hasNonNull("client_id") || !tree.path("installed").hasNonNull("client_secret"))
                throw new IllegalArgumentException("Google Cloud'dan indirilen Masaüstü uygulaması türündeki istemci dosyasını seç.");
            calendar.disconnect(); classroom.disconnect();
            if (drive != null) drive.disconnect();
            new GoogleCalendarOAuthService().forget(profile.resolve("config/google-calendar-tokens"));
            new GoogleClassroomOAuthService().forget(profile.resolve("config/google-calendar-tokens"));
            new GoogleDriveOAuthService().forget(profile.resolve("config/google-calendar-tokens"));
            AtomicFileWriter.write(profile.resolve("config/google-calendar-credentials.json"), bytes);
            AtomicFileWriter.write(profile.resolve("config/google-classroom-credentials.json"), bytes);
            return "Google istemcisi kaydedildi. Takvim ve Classroom ekranlarından hesabını bağla.";
        }, status::setText);
    }
    private void moveProfile() {
        DirectoryChooser chooser = new DirectoryChooser(); chooser.setTitle("Yeni, boş veri klasörünü seç");
        var folder = chooser.showDialog(window()); if (folder == null) return;
        Path destination = folder.toPath().toAbsolutePath().normalize();
        run(() -> {
            AppPaths.validateDestination(destination);
            if (destination.startsWith(profile.toAbsolutePath().normalize()) || profile.toAbsolutePath().normalize().startsWith(destination))
                throw new IllegalArgumentException("Yeni klasör mevcut veri klasörünün dışında olmalı.");
            try (var paths = Files.list(destination)) { if (paths.findAny().isPresent()) throw new IllegalArgumentException("Yeni veri klasörü boş olmalı."); }
            Path transfer = profile.resolve("backups/profile-transfer-" + UUID.randomUUID() + ".zip");
            new BackupService(profile).exportTo(transfer); new BackupService(destination).restoreFrom(transfer);
            Path config = profile.resolve("config");
            if (Files.exists(config)) try (var paths = Files.walk(config)) {
                for (Path source : paths.toList()) {
                    if (Files.isSymbolicLink(source)) throw new IllegalArgumentException("Google ayarları bağlantılı dosya içeriyor.");
                    if (!Files.isRegularFile(source)) continue;
                    String name = source.getFileName().toString();
                    if (!name.equals("app-config.json") && !name.equals("profile-location.properties") && !name.endsWith(".lock"))
                        AtomicFileWriter.write(destination.resolve("config").resolve(config.relativize(source)), Files.readAllBytes(source));
                }
            }
            AppPaths.selectRoot(destination);
            return "Veriler kopyalandı. Yeni klasörü kullanmak için yeniden başlat: " + destination;
        }, message -> { status.setText(message); requireRestart.run(); });
    }
    private <T> void run(Callable<T> operation, Consumer<T> done) {
        if (busy) return; busy = true; busyChanged.accept(true); status.setText("İşlem sürüyor…");
        Task<T> task = new Task<>() { @Override protected T call() throws Exception { return operation.call(); } };
        task.setOnSucceeded(e -> { busy = false; busyChanged.accept(false); done.accept(task.getValue()); });
        task.setOnFailed(e -> { busy = false; busyChanged.accept(false); status.setText("İşlem tamamlanamadı: " + task.getException().getMessage()); });
        Thread.ofVirtual().name("settings-operation").start(task);
    }
    private FileChooser zipChooser(String title) { FileChooser chooser = new FileChooser(); chooser.setTitle(title); chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Academic OS yedeği", "*.zip")); return chooser; }
    private Window window() { return root.getScene().getWindow(); }
    private VBox panel(String title, javafx.scene.Node... nodes) {
        Label header = new Label(title); header.getStyleClass().add("panel-baslik"); VBox box = new VBox(10, header); box.getChildren().addAll(nodes); box.getStyleClass().add("panel"); return box;
    }
    public void dispose() { if (busy) throw new IllegalStateException("Ayarlar/yedekleme işleminin tamamlanmasını bekle."); if (updateView != null) updateView.dispose(); }
}
