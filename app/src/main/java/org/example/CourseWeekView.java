package org.example;

import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.shape.Rectangle;
import java.time.*;
import java.time.format.*;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.function.Consumer;
import static org.example.CourseData.*;

/** A time-proportional weekly grid, with colliding meetings placed beside each other. */
final class CourseWeekView extends VBox {
    private static final Locale TR = Locale.forLanguageTag("tr");
    private static final double GUTTER = 52, HEADER = 44;
    private double scale = 1.25;
    private final Pane grid = new Pane();
    private final ScrollPane scroll = new ScrollPane(grid);
    private final Label title = new Label(), hint = new Label();
    private final CheckBox weekend = new CheckBox("Hafta sonu");
    private final Consumer<Course> edit;
    private final Consumer<Work> editWork;
    private CourseData data = CourseData.empty();
    private LocalDate week = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    private LocalDate periodStart, periodEnd;
    private record Meeting(Course course, Lesson lesson, Work exam) {}

    CourseWeekView(Consumer<Course> edit) {
        this(edit, w -> {});
    }
    CourseWeekView(Consumer<Course> edit, Consumer<Work> editWork) {
        this.editWork = editWork;
        this.edit = edit; setSpacing(10); setMinWidth(360); getStyleClass().add("planner-calendar");
        setId("planner-calendar"); grid.setId("planner-time-grid");
        Button previous = new Button("‹"), next = new Button("›"), today = new Button("Bu hafta");
        previous.setAccessibleText("Önceki hafta"); next.setAccessibleText("Sonraki hafta");
        previous.setOnAction(e -> { week = week.minusWeeks(1); draw(); });
        next.setOnAction(e -> { week = week.plusWeeks(1); draw(); });
        today.setOnAction(e -> { showDate(LocalDate.now()); draw(); });
        weekend.setOnAction(e -> draw());
        title.getStyleClass().add("panel-baslik"); title.setId("planner-week-title");
        FlowPane tools = new FlowPane(7, 6, previous, today, next, title, weekend);
        hint.setWrapText(true); hint.getStyleClass().add("soluk"); hint.setId("planner-week-hint");
        scroll.setFitToWidth(true); scroll.getStyleClass().add("content-scroll");
        scroll.viewportBoundsProperty().addListener((o, a, b) -> {
            if (Math.abs(a.getWidth() - b.getWidth()) > 1 || Math.abs(a.getHeight() - b.getHeight()) > 1) draw();
        });
        VBox.setVgrow(scroll, Priority.ALWAYS); getChildren().setAll(tools, hint, scroll);
    }

    void showDate(LocalDate date) { week = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)); }
    void update(CourseData data, LocalDate start, LocalDate end) {
        this.data = data;
        boolean changed = !Objects.equals(periodStart, start) || !Objects.equals(periodEnd, end);
        periodStart = start; periodEnd = end;
        if (changed && start != null && end != null && !end.isBefore(start) && (week.isBefore(start) || week.isAfter(end))) {
            // Start with a complete teaching week so early-week choices are immediately visible.
            LocalDate firstMonday = start.with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY));
            showDate(firstMonday.isAfter(end) ? start : firstMonday);
        }
        draw();
    }

    private void draw() {
        grid.getChildren().clear();
        var dateFormat = DateTimeFormatter.ofPattern("d MMM", TR);
        title.setText(week.format(dateFormat) + " – " + week.plusDays(6).format(dateFormat));
        List<Meeting> meetings = new ArrayList<>();
        for (Course c : data.courses()) if (c.planned()) for (Lesson l : c.lessons()) {
            LocalDate date = week.plusDays(l.day() - 1);
            if (CourseSchedule.occursOn(data, c, l, date)) meetings.add(new Meeting(c, l, null));
        }
        for (Work w : data.work()) if (w.scheduledExam()) {
            LocalDate date = LocalDate.parse(w.date());
            if (!date.isBefore(week) && !date.isAfter(week.plusDays(6)))
                data.courses().stream().filter(c -> c.id().equals(w.courseId())).findFirst().ifPresent(c ->
                    meetings.add(new Meeting(c, new Lesson(date.getDayOfWeek().getValue(), w.dueTime(), w.endTime()), w)));
        }
        boolean hasWeekend = meetings.stream().anyMatch(m -> m.lesson().day() > 5);
        int days = weekend.isSelected() || hasWeekend ? 7 : 5;
        int first = Math.min(8 * 60, meetings.stream().mapToInt(m -> minutes(m.lesson().start()) / 60 * 60).min().orElse(8 * 60));
        int last = Math.max(18 * 60, meetings.stream().mapToInt(m -> (minutes(m.lesson().end()) + 59) / 60 * 60).max().orElse(18 * 60));
        double width = Math.max(GUTTER + days * 128, scroll.getViewportBounds().getWidth());
        scale = Math.max(.65, Math.min(1.25, (scroll.getViewportBounds().getHeight() - HEADER - 24) / (last - first)));
        double column = (width - GUTTER) / days, height = HEADER + (last - first) * scale + 24;
        grid.setMinSize(GUTTER + days * 128, height); grid.setPrefSize(width, height);
        int conflicts = 0;
        for (int i = 0; i < meetings.size(); i++) for (int j = i + 1; j < meetings.size(); j++)
            if (CourseCatalogService.overlaps(meetings.get(i).lesson(), meetings.get(j).lesson())) conflicts++;
        hint.setText((meetings.isEmpty() ? "Bu haftada ders / sınav yok. Soldan bir şube seç veya dönemin haftasına geç."
                : "Ders veya sınav bloğuna tıklayarak ayrıntıları düzenleyebilirsin.")
                + (conflicts == 0 ? "" : "  ⚠ " + conflicts + " saat çakışması")
                + (hasWeekend && !weekend.isSelected() ? " · Hafta sonu dersleri gösteriliyor" : ""));
        for (int day = 1; day <= days; day++) {
            Label label = new Label(DayOfWeek.of(day).getDisplayName(TextStyle.SHORT, TR) + " " + week.plusDays(day - 1).getDayOfMonth());
            label.getStyleClass().add("planner-day");
            if (week.plusDays(day - 1).equals(LocalDate.now())) label.getStyleClass().add("planner-today");
            place(label, GUTTER + (day - 1) * column, 0, column, HEADER);
            Region vertical = new Region(); vertical.getStyleClass().add("planner-grid-line");
            place(vertical, GUTTER + (day - 1) * column, HEADER, 1, height - HEADER);
        }
        for (int time = first; time <= last; time += 60) {
            double y = HEADER + (time - first) * scale;
            Label label = new Label(String.format("%02d:00", time / 60)); label.getStyleClass().add("planner-hour");
            place(label, 0, y - 9, GUTTER - 5, 20);
            Region line = new Region(); line.getStyleClass().add("planner-grid-line"); place(line, GUTTER, y, width - GUTTER, 1);
        }
        for (int day = 1; day <= days; day++) {
            int selectedDay = day;
            var ordered = meetings.stream().filter(m -> m.lesson().day() == selectedDay)
                    .sorted(Comparator.comparingInt(m -> minutes(m.lesson().start()))).toList();
            List<Meeting> cluster = new ArrayList<>(); int clusterEnd = -1;
            for (Meeting m : ordered) {
                if (!cluster.isEmpty() && minutes(m.lesson().start()) >= clusterEnd) {
                    drawCluster(cluster, column, first); cluster.clear(); clusterEnd = -1;
                }
                cluster.add(m); clusterEnd = Math.max(clusterEnd, minutes(m.lesson().end()));
            }
            drawCluster(cluster, column, first);
        }
    }

    private void drawCluster(List<Meeting> cluster, double column, int first) {
        List<Integer> laneEnds = new ArrayList<>(), lanes = new ArrayList<>();
        for (Meeting m : cluster) {
            int lane = 0;
            while (lane < laneEnds.size() && laneEnds.get(lane) > minutes(m.lesson().start())) lane++;
            if (lane == laneEnds.size()) laneEnds.add(0);
            laneEnds.set(lane, minutes(m.lesson().end())); lanes.add(lane);
        }
        for (int i = 0; i < cluster.size(); i++) {
            Meeting m = cluster.get(i); Course c = m.course(); Lesson l = m.lesson();
            double laneWidth = column / laneEnds.size();
            String room = l.room().isBlank() ? c.room() : l.room();
            String text = (c.code().isBlank() ? c.name() : c.code()) + "\n" + l.start() + "–" + l.end() + "\n" + c.name()
                    + (room.isBlank() ? "" : "\n" + room);
            if (m.exam() != null) text = "Sınav · " + m.exam().title() + "\n" + l.start() + "–" + l.end() + "\n" + c.name();
            Button block = new Button(text); block.setWrapText(true); block.setId("planner-block-" + c.id() + "-" + l.day() + "-" + l.start().replace(':','-'));
            if (m.exam() != null) block.setId("planner-exam-" + m.exam().id());
            block.setUserData(c.id()); block.setAccessibleText((m.exam() == null ? c.name() : "Sınav · " + m.exam().title()) + " " + l.start() + " " + l.end());
            block.getStyleClass().addAll("planner-block", "planner-color-" + Math.floorMod(c.code().hashCode(), 5));
            if (m.exam() != null) block.getStyleClass().add("exam-event");
            if (laneEnds.size() > 1) block.getStyleClass().add("planner-block-conflict");
            block.setTooltip(new Tooltip((m.exam() == null ? "" : "Sınav · " + m.exam().title() + "\n") + c + "\n" + c.section() + " · " + c.instructor() + "\n" + l.start() + "–" + l.end()));
            block.setOnAction(e -> { if (m.exam() == null) edit.accept(c); else editWork.accept(m.exam()); });
            double h = Math.max(12, (minutes(l.end()) - minutes(l.start())) * scale - 3);
            Rectangle clip = new Rectangle(); clip.widthProperty().bind(block.widthProperty()); clip.heightProperty().bind(block.heightProperty()); block.setClip(clip);
            place(block, GUTTER + (l.day() - 1) * column + lanes.get(i) * laneWidth + 3,
                    HEADER + (minutes(l.start()) - first) * scale + 2, laneWidth - 6, h);
        }
    }

    private void place(Region node, double x, double y, double width, double height) {
        node.setManaged(false); node.resizeRelocate(x, y, width, height); grid.getChildren().add(node);
    }
    private static int minutes(String time) { LocalTime t = LocalTime.parse(time); return t.getHour() * 60 + t.getMinute(); }
}
