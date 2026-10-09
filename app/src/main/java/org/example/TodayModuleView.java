package org.example;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.concurrent.Task;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.util.Duration;
import java.nio.file.Path;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.function.Consumer;

public final class TodayModuleView {
    private final Path projectRoot;
    private final Consumer<String> navigate;
    private final VBox root = new VBox(12), content = new VBox(14);
    private final Label status = new Label();
    private final Button refresh = new Button("Yenile");
    private Task<TodayService.Snapshot> pending;
    private boolean disposed;
    private LocalDate displayedDate;
    private final Timeline midnight = new Timeline(new KeyFrame(Duration.minutes(1), e -> {
        if (!LocalDate.now().equals(displayedDate)) refresh();
    }));

    public TodayModuleView(Path projectRoot, Consumer<String> navigate) { this.projectRoot = projectRoot; this.navigate = navigate; }

    public VBox build() {
        UiTheme.decorate(refresh, "refresh-cw", "flat"); refresh.setId("today-refresh");
        refresh.setOnAction(e -> refresh());
        status.setWrapText(true); status.setId("today-status");
        ScrollPane scroll = new ScrollPane(content); scroll.setFitToWidth(true); scroll.getStyleClass().add("content-scroll");
        root.getChildren().setAll(new HBox(12, refresh, status), scroll); VBox.setVgrow(scroll, Priority.ALWAYS);
        midnight.setCycleCount(Timeline.INDEFINITE); midnight.play();
        refresh(); return root;
    }

    private void refresh() {
        if (disposed) return;
        if (pending != null) pending.cancel();
        LocalDate today = LocalDate.now();
        refresh.setDisable(true); status.setText("Günlük özet yükleniyor…");
        Task<TodayService.Snapshot> task = new Task<>() {
            @Override protected TodayService.Snapshot call() { return new TodayService().load(projectRoot, today); }
        };
        pending = task;
        task.setOnSucceeded(e -> {
            if (disposed || pending != task) return;
            displayedDate = today;
            render(task.getValue()); refresh.setDisable(false);
            status.setText(today.format(DateTimeFormatter.ofPattern("d MMMM yyyy, EEEE", Locale.forLanguageTag("tr"))));
        });
        task.setOnFailed(e -> {
            if (disposed || pending != task) return;
            refresh.setDisable(false); status.setText("Özet yüklenemedi: " + task.getException().getMessage());
        });
        Thread.ofVirtual().name("today-summary").start(task);
    }

    private void render(TodayService.Snapshot snapshot) {
        content.getChildren().clear();
        Label intro = new Label("Bugünün dersleri ve önümüzdeki 7 gün"); intro.getStyleClass().add("welcome-title"); intro.setWrapText(true);
        Label hint = new Label("Geciken görevler ve ödemeler de listelenir. Değişiklikleri ilgili modülde yapabilirsin.");
        hint.getStyleClass().add("soluk"); hint.setWrapText(true);
        FlowPane shortcuts = new FlowPane(10, 10);
        for (String[] item : new String[][]{{"Görev planı", "calendar"}, {"Dersler", "courses"}, {"Borç Defteri", "finance"}}) {
            Button b = new Button(item[0]); b.setOnAction(e -> navigate.accept(item[1])); shortcuts.getChildren().add(b);
        }
        content.getChildren().addAll(intro, hint, shortcuts,
                section("Bugünkü dersler", snapshot.lessons(), snapshot.date(), "Bugün programda ders yok"),
                section("Görevler", snapshot.tasks(), snapshot.date(), "Yaklaşan veya geciken görev yok"),
                section("Ödev ve sınavlar", snapshot.coursework(), snapshot.date(), "Yaklaşan veya geciken teslim yok"),
                section("Ödeme hatırlatmaları", snapshot.payments(), snapshot.date(), "Yaklaşan veya geciken ödeme planı yok"));
    }

    private VBox section(String title, TodayService.Section section, LocalDate today, String empty) {
        Label heading = new Label(title + (section.error() == null ? " · " + section.entries().size() : ""));
        heading.getStyleClass().add("panel-baslik"); VBox box = new VBox(10, heading); box.getStyleClass().add("panel");
        if (section.error() != null) {
            Label error = new Label(section.error()); error.setWrapText(true); error.getStyleClass().add("form-error"); box.getChildren().add(error);
        } else if (section.entries().isEmpty()) {
            Label label = new Label(empty); label.getStyleClass().add("soluk"); box.getChildren().add(label);
        } else {
            NumberFormat money = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("tr"));
            // Keep the summary cheap even when years of overdue items accumulate.
            for (var entry : section.entries().stream().limit(50).toList()) {
                String due = entry.date().equals(today) ? "Bugün" : entry.date().format(DateTimeFormatter.ofPattern("d MMM", Locale.forLanguageTag("tr")));
                if (entry.date().isBefore(today)) due += " · Gecikti";
                Label name = new Label(entry.title()); name.setWrapText(true);
                Label detail = new Label(due + " · " + entry.detail() + (entry.amount() == null ? "" : " · " + money.format(entry.amount())));
                detail.setWrapText(true); detail.getStyleClass().add(entry.date().isBefore(today) ? "form-error" : "soluk");
                VBox text = new VBox(3, name, detail); text.setMinWidth(0); HBox.setHgrow(text, Priority.ALWAYS);
                Button open = new Button("Aç"); open.setAccessibleText(entry.title() + " kaydının modülünü aç");
                open.setOnAction(e -> navigate.accept(entry.module()));
                HBox row = new HBox(12, text, open); row.getStyleClass().add("today-row"); box.getChildren().add(row);
            }
            if (section.entries().size() > 50) {
                Label more = new Label((section.entries().size() - 50) + " kayıt daha var. Tümünü ilgili modülde görebilirsin.");
                more.setWrapText(true); more.getStyleClass().add("soluk"); box.getChildren().add(more);
            }
        }
        return box;
    }

    public void dispose() { disposed = true; midnight.stop(); if (pending != null) pending.cancel(); }
}
