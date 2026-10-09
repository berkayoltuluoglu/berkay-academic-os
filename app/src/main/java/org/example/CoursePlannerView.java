package org.example;

import javafx.scene.control.*;
import javafx.scene.layout.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Consumer;
import static org.example.CourseData.*;

/** Persistent planning surface: selecting a section does not destroy search, expansion or scroll state. */
final class CoursePlannerView extends SplitPane {
    private record Group(String code, String name, List<Section> sections) {}
    private final TextField search = new TextField();
    private final CheckBox selectedOnly = new CheckBox("Seçtiklerim"), compatible = new CheckBox("Çakışmayanlar");
    private final DatePicker start = new DatePicker(LocalDate.now()), end = new DatePicker(LocalDate.now().plusMonths(4));
    private final Label summary = new Label(), message = new Label(), count = new Label();
    private final ListView<Group> list = new ListView<>();
    private final Set<String> expanded = new HashSet<>();
    private final Set<String> collapsed = new HashSet<>();
    private final CourseWeekView calendar;
    private final Consumer<CourseData> save;
    private CourseData data = CourseData.empty();
    private boolean initialized, updating;

    CoursePlannerView(Consumer<CourseData> save, Consumer<Course> edit) {
        this.save = save;
        setId("course-planner"); getStyleClass().add("course-planner");
        calendar = new CourseWeekView(edit);
        search.setPromptText("Ders kodu, adı veya hoca ara"); search.setId("section-search");
        Button clear = new Button("Temizle"); clear.setOnAction(e -> search.clear());
        HBox searchRow = new HBox(6, search, clear); HBox.setHgrow(search, Priority.ALWAYS);
        selectedOnly.setId("planner-selected-only"); compatible.setId("planner-compatible");
        start.setId("section-start"); end.setId("section-end");
        start.setPrefWidth(145); end.setPrefWidth(145);
        VBox dates = new VBox(6, new Label("Yeni seçimlerin dönem başlangıcı / bitişi"), new FlowPane(6, 6, start, end));
        TitledPane period = new TitledPane("Dönem tarihleri", dates); period.setExpanded(false);
        summary.setId("planner-summary"); summary.getStyleClass().add("planner-summary"); summary.setWrapText(true);
        message.setWrapText(true); message.setId("section-status"); message.getStyleClass().add("soluk");
        message.setText("Şubeye tıkla; programı yanında gör. Seçimi kaldırmak ödevleri silmez.");
        list.setId("section-list"); list.getStyleClass().add("planner-list");
        list.setPlaceholder(new Label("CSV yükleyerek derslerini seç\nveya Ders ekle ile kendin gir."));
        list.setCellFactory(v -> new ListCell<>() {
            @Override protected void updateItem(Group group, boolean empty) {
                super.updateItem(group, empty); setText(null); setGraphic(null);
                if (!empty && group != null) setGraphic(card(group));
            }
        });
        VBox sidebar = new VBox(10, summary, searchRow, new FlowPane(10, 6, selectedOnly, compatible), period, count, list, message);
        sidebar.getStyleClass().add("planner-sidebar"); sidebar.setMinWidth(310); sidebar.setPrefWidth(360);
        VBox.setVgrow(list, Priority.ALWAYS);
        getItems().setAll(sidebar, calendar); setDividerPositions(.34);
        SplitPane.setResizableWithParent(sidebar, false);
        search.textProperty().addListener((o, a, b) -> refresh());
        selectedOnly.setOnAction(e -> refresh()); compatible.setOnAction(e -> refresh());
        start.valueProperty().addListener((o, a, b) -> periodChanged());
        end.valueProperty().addListener((o, a, b) -> periodChanged());
    }

    void update(CourseData value) {
        if (message.getText().contains("seçiliyor") || message.getText().contains("çıkarılıyor"))
            message.setText("Şubeye tıkla; programı yanında gör. Seçimi kaldırmak ödevleri silmez.");
        data = value; updating = true;
        if (!initialized) {
            LocalDate today = LocalDate.now();
            var current = data.courses().stream().filter(c -> !LocalDate.parse(c.endDate()).isBefore(today))
                    .min(Comparator.comparing(Course::startDate));
            current.ifPresent(c -> { start.setValue(LocalDate.parse(c.startDate())); end.setValue(LocalDate.parse(c.endDate())); });
            initialized = true;
        }
        updating = false;
        refresh();
    }

    private void periodChanged() {
        if (updating) return;
        if (start.getValue() != null && end.getValue() != null && !end.getValue().isBefore(start.getValue())) {
            calendar.showDate(start.getValue());
            message.setText("Yeni seçimler bu döneme eklenir. Kayıtlı derslerin tarihleri kendiliğinden değişmez.");
        } else message.setText("Dönem başlangıcını ve bitişini kontrol et.");
        refresh();
    }

    private void refresh() {
        var active = CourseCatalogService.active(data, start.getValue(), end.getValue());
        summary.setText(active.size() + " ders seçili · " + total(active, false) + " kredi · " + total(active, true) + " AKTS");
        Map<String, List<Section>> groups = new TreeMap<>();
        String query = CourseCsvImporter.normalize(search.getText());
        for (Section s : data.sections()) {
            boolean chosen = selected(s) != null;
            if (selectedOnly.isSelected() && !chosen) continue;
            if (compatible.isSelected() && !chosen && !conflicts(s).isEmpty()) continue;
            if (!CourseCsvImporter.normalize(s.fullCode() + " " + s.name() + " " + s.instructor()).contains(query)) continue;
            groups.computeIfAbsent(s.code(), k -> new ArrayList<>()).add(s);
        }
        List<Group> next = groups.entrySet().stream().map(e -> new Group(e.getKey(), e.getValue().getFirst().name(), List.copyOf(e.getValue()))).toList();
        if (list.getItems().equals(next)) list.refresh(); else list.getItems().setAll(next);
        count.setText(next.size() + " ders · " + next.stream().mapToInt(g -> g.sections().size()).sum() + " şube");
        calendar.update(data, start.getValue(), end.getValue());
    }

    private VBox card(Group group) {
        VBox box = new VBox(7); box.getStyleClass().add("planner-course-card");
        box.prefWidthProperty().bind(list.widthProperty().subtract(38)); box.setMaxWidth(Double.MAX_VALUE);
        boolean chosen = group.sections().stream().anyMatch(s -> selected(s) != null);
        boolean open = !collapsed.contains(group.code()) && (expanded.contains(group.code()) || !search.getText().isBlank() || chosen || selectedOnly.isSelected());
        Button heading = new Button((open ? "▾ " : "▸ ") + group.code() + " · " + group.name() + "\n" + group.sections().size() + " şube");
        heading.getStyleClass().add("planner-course-heading"); heading.setWrapText(true); heading.setMinWidth(0); heading.setMaxWidth(Double.MAX_VALUE);
        heading.setAccessibleText(group.code() + " şubelerini göster");
        heading.setOnAction(e -> {
            if (open) { expanded.remove(group.code()); collapsed.add(group.code()); }
            else { collapsed.remove(group.code()); expanded.add(group.code()); }
            list.refresh();
        });
        box.getChildren().add(heading);
        if (open) for (Section s : group.sections()) {
            Course current = selected(s);
            var collisions = conflicts(s);
            ToggleButton toggle = new ToggleButton((current == null ? "○ " : "✓ ") + s.fullCode() + " · " + s.instructor()
                    + "\n" + CourseModuleView.lessonDescription(s.lessons())
                    + "\n" + amount(s.credit()) + " kredi · " + amount(s.ects()) + " AKTS"
                    + (collisions.isEmpty() ? "" : "\n⚠ " + collisions.stream().map(Course::code).distinct().collect(java.util.stream.Collectors.joining(", ")) + " ile çakışıyor"));
            toggle.setId("section-toggle-" + s.id()); toggle.setUserData(s.id());
            toggle.setSelected(current != null); toggle.setWrapText(true); toggle.setMinWidth(0); toggle.setMaxWidth(Double.MAX_VALUE);
            toggle.getStyleClass().add("planner-section");
            if (!collisions.isEmpty()) toggle.getStyleClass().add("planner-section-conflict");
            toggle.setAccessibleText(s.fullCode() + (current == null ? " şubesini seç" : " seçimini kaldır"));
            toggle.setOnAction(e -> {
                try {
                    expanded.add(group.code());
                    if (current == null) {
                        save.accept(CourseCatalogService.select(data, s.id(), start.getValue(), end.getValue()));
                        message.setText(collisions.isEmpty() ? "Şube seçiliyor…" : "Seçilen şubenin çakışmaları çizelgede işaretlenir.");
                    } else {
                        save.accept(CourseCatalogService.unplan(data, current.id()));
                        message.setText("Ders programdan çıkarılıyor; ödev ve sınavlar korunuyor.");
                    }
                } catch (Exception ex) { message.setText(ex.getMessage()); toggle.setSelected(current != null); }
            });
            box.getChildren().add(toggle);
        }
        return box;
    }

    private Course selected(Section section) {
        return CourseCatalogService.active(data, start.getValue(), end.getValue()).stream()
                .filter(c -> c.sectionId().equals(section.id())).findFirst().orElse(null);
    }
    private List<Course> conflicts(Section section) {
        return CourseCatalogService.conflicts(data, section, start.getValue(), end.getValue());
    }
    private static String total(List<Course> courses, boolean ects) {
        BigDecimal total = BigDecimal.ZERO; int missing = 0;
        for (Course c : courses) {
            BigDecimal value = ects ? c.ects() : c.credit();
            if (value == null) missing++; else total = total.add(value);
        }
        return amount(total) + (missing == 0 ? "" : " (+" + missing + " bilinmiyor)");
    }
    private static String amount(BigDecimal value) { return value == null ? "?" : value.stripTrailingZeros().toPlainString(); }
}
