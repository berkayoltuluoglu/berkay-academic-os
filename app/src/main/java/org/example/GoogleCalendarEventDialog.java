package org.example;

import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import java.util.function.Consumer;

/** Keeps the form open on validation/API failure and closes only after Google's write succeeds. */
final class GoogleCalendarEventDialog {
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");
    private final Dialog<Void> dialog = new Dialog<>();
    private boolean saving;

    GoogleCalendarEventDialog(Node owner, GoogleCalendarService service, CalendarEventItem original,
                              LocalDate selectedDate, Consumer<Boolean> busyChanged,
                              Consumer<CalendarEventItem> saved) {
        boolean editing = original != null;
        String eventId = UUID.randomUUID().toString().replace("-", "");
        dialog.setTitle(editing ? "Google etkinliğini düzenle" : "Google etkinliği ekle");
        dialog.setHeaderText(service.getAccountEmail() + " · Ana takvim"
                + (editing && original.isRecurringInstance() ? "\nYalnızca seçili tekrar değiştirilecek." : ""));
        UiTheme.styleDialog(dialog, owner);
        dialog.getDialogPane().setId("google-event-dialog");

        TextField title = field("google-event-title", editing ? original.getTitle() : "");
        DatePicker startDate = new DatePicker(editing ? original.getDate() : selectedDate);
        startDate.setId("google-event-start-date");
        DatePicker endDate = new DatePicker(editing ? original.getEndDate() : selectedDate);
        endDate.setId("google-event-end-date");
        startDate.setEditable(false);
        endDate.setEditable(false);
        TextField startTime = field("google-event-start-time", editing && original.getStartTime() != null
                ? original.getStartTime().format(TIME_FORMAT) : "09:00");
        TextField endTime = field("google-event-end-time", editing && original.getEndTime() != null
                ? original.getEndTime().format(TIME_FORMAT) : "10:00");
        startTime.setPromptText("SS:DD");
        endTime.setPromptText("SS:DD");
        CheckBox allDay = new CheckBox("Tüm gün");
        allDay.setId("google-event-all-day");
        allDay.setSelected(editing && original.isAllDay());
        startTime.disableProperty().bind(allDay.selectedProperty());
        endTime.disableProperty().bind(allDay.selectedProperty());
        TextField location = field("google-event-location", editing ? original.getLocation() : "");
        TextArea description = new TextArea(editing && original.getDescription() != null ? original.getDescription() : "");
        description.setId("google-event-description");
        description.setPrefRowCount(3);
        description.setWrapText(true);
        Label dateHint = new Label("Tüm gün etkinliklerde bitiş tarihi son günü de içerir. Saatler: "
                + java.time.ZoneId.systemDefault().getId());
        dateHint.setWrapText(true);
        dateHint.getStyleClass().add("soluk");
        Label errorLabel = new Label();
        errorLabel.setId("google-event-error");
        errorLabel.setWrapText(true);
        errorLabel.setStyle("-fx-text-fill: #b91c1c;");

        GridPane form = new GridPane();
        form.setHgap(12);
        form.setVgap(10);
        form.addRow(0, new Label("Başlık"), title);
        form.addRow(1, new Label("Başlangıç tarihi"), startDate);
        form.addRow(2, new Label("Bitiş tarihi"), endDate);
        form.addRow(3, new Label("Süre"), allDay);
        form.addRow(4, new Label("Başlangıç saati"), startTime);
        form.addRow(5, new Label("Bitiş saati"), endTime);
        form.addRow(6, new Label("Konum"), location);
        form.addRow(7, new Label("Açıklama"), description);
        VBox content = new VBox(12, form, dateHint, errorLabel);
        content.setPadding(new Insets(12));
        content.setPrefWidth(520);
        dialog.getDialogPane().setContent(content);
        ButtonType saveType = new ButtonType("Google'a kaydet", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveType, ButtonType.CANCEL);
        Button save = (Button) dialog.getDialogPane().lookupButton(saveType);
        Button cancel = (Button) dialog.getDialogPane().lookupButton(ButtonType.CANCEL);
        save.setId("google-event-save");
        cancel.setText("Vazgeç");
        cancel.setId("google-event-cancel");
        dialog.setOnCloseRequest(event -> { if (saving) event.consume(); });

        startDate.valueProperty().addListener((observable, previous, value) -> {
            if (value != null && (endDate.getValue() == null || endDate.getValue().isBefore(value))) endDate.setValue(value);
        });
        save.addEventFilter(ActionEvent.ACTION, event -> {
            event.consume();
            if (saving) return;
            CalendarEventDraft draft;
            try {
                draft = new CalendarEventDraft(title.getText(), description.getText(), location.getText(),
                        startDate.getValue(), endDate.getValue(), allDay.isSelected() ? null : parseTime(startTime.getText()),
                        allDay.isSelected() ? null : parseTime(endTime.getText()), allDay.isSelected());
            } catch (IllegalArgumentException failure) {
                errorLabel.setText(failure.getMessage());
                return;
            }
            saving = true;
            form.setDisable(true);
            save.setDisable(true);
            cancel.setDisable(true);
            errorLabel.setText("Google'a kaydediliyor...");
            busyChanged.accept(true);
            Task<CalendarEventItem> write = new Task<>() {
                @Override protected CalendarEventItem call() throws Exception {
                    return editing ? service.updateEvent(original, draft) : service.createEvent(eventId, draft);
                }
            };
            write.setOnSucceeded(done -> {
                saving = false;
                busyChanged.accept(false);
                dialog.close();
                saved.accept(write.getValue());
            });
            write.setOnFailed(done -> {
                saving = false;
                form.setDisable(false);
                save.setDisable(false);
                cancel.setDisable(false);
                busyChanged.accept(false);
                errorLabel.setText(write.getException().getMessage());
            });
            Thread worker = new Thread(write, "google-calendar-save");
            worker.setDaemon(true);
            worker.start();
        });
    }

    void show() { dialog.show(); }

    private static TextField field(String id, String value) {
        TextField field = new TextField(value == null ? "" : value);
        field.setId(id);
        return field;
    }

    private static LocalTime parseTime(String text) {
        try {
            if (text == null || !text.trim().matches("\\d{2}:\\d{2}")) throw new IllegalArgumentException();
            return LocalTime.parse(text.trim());
        } catch (IllegalArgumentException | java.time.format.DateTimeParseException error) {
            throw new IllegalArgumentException("Saatleri SS:DD biçiminde yaz (ör. 14:30).");
        }
    }
}
