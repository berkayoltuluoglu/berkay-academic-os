package org.example;

import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.input.*;
import javafx.scene.layout.*;
import java.time.*;
import java.util.*;
import java.util.function.Consumer;
import static org.example.DailyAgendaService.Event;

/** Time-proportional daily layout. Only saved work blocks can be dragged. */
final class DailyTimelineView extends VBox {
    record Move(Event event, LocalTime start) {}
    private static final double GUTTER = 58;
    private final Pane grid = new Pane();
    private final Consumer<Event> open;
    private final Consumer<Move> move;
    private List<Event> events = List.of();
    private List<DailyAgendaService.Todo> deadlines = List.of();
    private Consumer<DailyAgendaService.Todo> openDeadline = t -> {};
    private LocalDate date = LocalDate.now();
    private int first, last;
    private double scale;

    DailyTimelineView(Consumer<Event> open, Consumer<Move> move) {
        this.open = open; this.move = move;
        setSpacing(18); setMinWidth(0); grid.setId("today-time-grid");
        Label title = new Label("Günün akışı"); title.getStyleClass().add("silver-section-title");
        getChildren().setAll(title, grid);
        grid.widthProperty().addListener((o, a, b) -> draw());
        grid.setOnDragOver(e -> {
            if (dragged(e.getDragboard()) != null) { e.acceptTransferModes(TransferMode.MOVE); e.consume(); }
        });
        grid.setOnDragDropped(e -> {
            Event event = dragged(e.getDragboard());
            if (event == null) return;
            int duration = minutes(event.end()) - minutes(event.start());
            int minute = (int) (Math.round((first + e.getY() / scale) / 15.0) * 15);
            minute = Math.max(0, Math.min(1439 - duration, minute));
            move.accept(new Move(event, LocalTime.of(minute / 60, minute % 60)));
            e.setDropCompleted(true); e.consume();
        });
    }

    void update(LocalDate date, List<Event> events) { update(date, events, List.of()); }
    /** Deadlines are open coursework/tasks due on {@code date}; timed ones are drawn as red markers. */
    void update(LocalDate date, List<Event> events, List<DailyAgendaService.Todo> deadlines) {
        this.date = date; this.events = events; this.deadlines = List.copyOf(deadlines); draw();
    }
    void onDeadline(Consumer<DailyAgendaService.Todo> open) { this.openDeadline = open; }
    private Event dragged(Dragboard board) {
        if (!board.hasString()) return null;
        return events.stream().filter(e -> e.study() && !e.done() && ("study:" + e.id()).equals(board.getString())).findFirst().orElse(null);
    }

    void draw() {
        grid.getChildren().clear();
        first = Math.min(8 * 60, events.stream().mapToInt(e -> minutes(e.start()) / 60 * 60).min().orElse(8 * 60));
        last = Math.max(18 * 60, events.stream().mapToInt(e -> (minutes(e.end()) + 59) / 60 * 60).max().orElse(18 * 60));
        for (var d : deadlines) if (d.dueTime() != null) { int m = minutes(d.dueTime()); first = Math.min(first, m / 60 * 60); last = Math.max(last, Math.min(24 * 60, (m + 59) / 60 * 60)); }
        if (date.equals(LocalDate.now())) {
            int hour = LocalTime.now().getHour();
            first = Math.min(first, hour * 60); last = Math.max(last, Math.min(24, hour + 1) * 60);
        }
        // ~0.9 px/min for a full 08–24 day: a one-hour block fits its title and time on two lines.
        scale = Math.min(1.0, 860.0 / (last - first));
        double width = Math.max(260, grid.getWidth());
        // Room under the last hour so a deadline label at 23:59 does not cover the evening's blocks.
        double height = (last - first) * scale + (deadlines.stream().anyMatch(d -> d.dueTime() != null) ? 36 : 12);
        grid.setMinHeight(height); grid.setPrefHeight(height);
        for (int time = first; time <= last; time += 60) {
            double y = (time - first) * scale;
            Label hour = new Label(String.format("%02d:00", time / 60)); hour.getStyleClass().add("silver-hour");
            place(hour, 0, y - 8, GUTTER - 8, 20);
            Region line = new Region(); line.getStyleClass().add("silver-time-line");
            place(line, GUTTER, y, width - GUTTER, 1);
        }
        var ordered = events.stream().sorted(Comparator.comparing(Event::start)).toList();
        List<Event> cluster = new ArrayList<>(); int end = -1;
        for (var event : ordered) {
            if (!cluster.isEmpty() && minutes(event.start()) >= end) { drawCluster(cluster, width); cluster.clear(); end = -1; }
            cluster.add(event); end = Math.max(end, minutes(event.end()));
        }
        drawCluster(cluster, width);
        if (events.isEmpty()) {
            Label empty = new Label("Bugün için planlanmış etkinlik yok"); empty.getStyleClass().add("soluk"); empty.setWrapText(true);
            place(empty, GUTTER + 14, 75, width - GUTTER - 28, 50);
        }
        drawDeadlines(width);
        LocalTime now = LocalTime.now();
        int minute = minutes(now);
        if (date.equals(LocalDate.now()) && minute >= first && minute <= last) {
            double y = (minute - first) * scale;
            Region line = new Region(); line.getStyleClass().add("silver-now-line"); line.setMouseTransparent(true);
            place(line, GUTTER, y, width - GUTTER, 1);
            Label time = new Label(now.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm")));
            time.getStyleClass().add("silver-now-time"); time.setMouseTransparent(true);
            place(time, 0, y - 10, GUTTER - 14, 22);
        }
    }

    private void drawCluster(List<Event> cluster, double width) {
        List<Integer> ends = new ArrayList<>(), lanes = new ArrayList<>();
        for (var e : cluster) {
            int lane = 0; while (lane < ends.size() && ends.get(lane) > minutes(e.start())) lane++;
            if (lane == ends.size()) ends.add(0);
            ends.set(lane, minutes(e.end())); lanes.add(lane);
        }
        for (int i = 0; i < cluster.size(); i++) {
            var event = cluster.get(i); double laneWidth = (width - GUTTER) / ends.size();
            Label name = new Label(event.title()); name.getStyleClass().add("silver-event-name"); name.setMaxWidth(Double.MAX_VALUE);
            Label detail = new Label(event.start() + "–" + event.end() + (event.detail().isBlank() ? "" : " · " + event.detail()));
            detail.getStyleClass().add("silver-event-detail"); detail.setMaxWidth(Double.MAX_VALUE);
            VBox text = new VBox(4, name, detail); text.setMinWidth(0); HBox.setHgrow(text, Priority.ALWAYS);
            HBox row = new HBox(8, text); row.setAlignment(Pos.CENTER_LEFT);
            if (event.study()) row.getChildren().add(UiTheme.icon("more-vertical"));
            Button block = new Button(); block.setGraphic(row);
            block.getStyleClass().addAll("silver-event", event.study() ? "study-event" : "lesson-event");
            if (event.done()) block.getStyleClass().add("completed-event");
            if (cluster.stream().anyMatch(other -> other != event && DailyAgendaService.overlaps(event, other)))
                block.getStyleClass().add("conflicting-event");
            block.setId("today-event-" + event.id() + "-" + event.start().toString().replace(':', '-'));
            block.setUserData(event); block.setAccessibleText(event.title() + " " + event.start() + "–" + event.end());
            block.setTooltip(new Tooltip(event.title() + "\n" + detail.getText() + (event.study() ? "\nTaşımak için sürükle; düzenlemek için tıkla" : "")));
            block.setOnAction(e -> open.accept(event));
            if (event.study() && !event.done()) block.setOnDragDetected(e -> {
                var board = block.startDragAndDrop(TransferMode.MOVE); var data = new ClipboardContent();
                data.putString("study:" + event.id()); board.setContent(data); e.consume();
            });
            double height = Math.max(22, (minutes(event.end()) - minutes(event.start())) * scale - 6);
            if (height < 44) { text.getChildren().setAll(new Label(name.getText() + "  ·  " + detail.getText()) {{ getStyleClass().add("silver-event-name"); setMinWidth(0); }}); }
            place(block, GUTTER + lanes.get(i) * laneWidth + 12, (minutes(event.start()) - first) * scale + 3, laneWidth - 14, height);
            var clip = new javafx.scene.shape.Rectangle(); clip.widthProperty().bind(block.widthProperty());
            clip.heightProperty().bind(block.heightProperty()); block.setClip(clip);
        }
    }

    private void drawDeadlines(double width) {
        var timed = deadlines.stream().filter(d -> d.dueTime() != null).sorted(Comparator.comparing(DailyAgendaService.Todo::dueTime)).toList();
        for (var d : timed) {
            double y = Math.min((minutes(d.dueTime()) - first) * scale, (last - first) * scale);
            Region line = new Region(); line.getStyleClass().add("today-deadline-line"); line.setMouseTransparent(true);
            place(line, GUTTER, y, width - GUTTER, 2);
            Button tag = new Button("▲ " + d.dueTime() + " · " + d.title()); tag.getStyleClass().add("today-deadline-tag");
            tag.setId("today-deadline-" + d.id()); tag.setTooltip(new Tooltip(d.title() + (d.detail().isBlank() ? "" : "\n" + d.detail())));
            tag.setOnAction(e -> openDeadline.accept(d));
            tag.setMinWidth(0); tag.setTextOverrun(OverrunStyle.ELLIPSIS); tag.setAlignment(Pos.CENTER_LEFT);
            place(tag, GUTTER + 12, y + 4, Math.min(width - GUTTER - 16, 420), 22);
        }
    }
    private void place(Region region, double x, double y, double w, double h) {
        region.setManaged(false); region.resizeRelocate(x, y, w, h); grid.getChildren().add(region);
    }
    private static int minutes(LocalTime time) { return time.getHour() * 60 + time.getMinute(); }
}
