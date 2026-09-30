package org.example;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

public class CalendarModuleView {

    private final ObservableList<TaskItem> allTasks = FXCollections.observableArrayList();
    private final ObservableList<TaskItem> filteredTasks = FXCollections.observableArrayList();
    private final ObservableList<CalendarEventItem> selectedDayEvents = FXCollections.observableArrayList();

    private final GoogleCalendarService googleCalendarService = new GoogleCalendarService();
    private final Map<YearMonth, List<CalendarEventItem>> googleEventsCache = new HashMap<>();
    private final AtomicLong calendarLoadSequence = new AtomicLong(0);

    private YearMonth currentMonth = YearMonth.now();
    private LocalDate selectedDate = LocalDate.now();

    private Label monthLabel;
    private Label calendarInfoLabel;
    private GridPane calendarGrid;
    private ListView<CalendarEventItem> eventListView;
    private TabPane mainTabs;
    private Tab taskTab;
    private ComboBox<String> taskFilterCombo;
    private DatePicker taskDatePicker;
    private ListView<TaskItem> taskListView;
    private Label taskInfoLabel;
    private Button connectGoogleButton;

    public VBox build() {
        return build(resolveDefaultDataFile());
    }

    public VBox build(Path tasksFilePath) {
        TaskStorageService storageService = new TaskStorageService(tasksFilePath);
        allTasks.setAll(storageService.loadTasks());

        mainTabs = new TabPane();
        Tab calendarTab = new Tab("Takvim");
        taskTab = new Tab("Görev Planı");
        calendarTab.setClosable(false);
        taskTab.setClosable(false);

        calendarTab.setContent(buildCalendarTab());
        taskTab.setContent(buildTaskTab(storageService));

        mainTabs.getTabs().addAll(calendarTab, taskTab);

        VBox root = new VBox(mainTabs);
        VBox.setVgrow(mainTabs, Priority.ALWAYS);

        refreshCalendarView();
        applyTaskFilter("Seçili Gün");
        return root;
    }

    private VBox buildCalendarTab() {
        Label title = new Label("Google Calendar Takvimi");
        title.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");

        Button prevButton = new Button("<");
        Button todayButton = new Button("Bugün");
        Button nextButton = new Button(">");

        connectGoogleButton = new Button("Google Calendar'a Bağlan");

        monthLabel = new Label();
        monthLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");

        calendarInfoLabel = new Label("Hazır");
        calendarInfoLabel.setWrapText(true);

        HBox header = new HBox(10, title, prevButton, todayButton, nextButton, connectGoogleButton, monthLabel, calendarInfoLabel);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(8));

        calendarGrid = new GridPane();
        calendarGrid.setHgap(6);
        calendarGrid.setVgap(6);
        calendarGrid.setPadding(new Insets(10));
        calendarGrid.setStyle("-fx-background-color: #f7f7f7; -fx-border-color: #dddddd;");

        List<String> weekdays = List.of("Pzt", "Sal", "Çar", "Per", "Cum", "Cmt", "Paz");
        for (int i = 0; i < weekdays.size(); i++) {
            Label dayLabel = new Label(weekdays.get(i));
            dayLabel.setAlignment(Pos.CENTER);
            dayLabel.setMaxWidth(Double.MAX_VALUE);
            dayLabel.setStyle("-fx-font-weight: bold;");
            calendarGrid.add(dayLabel, i, 0);
            GridPane.setHgrow(dayLabel, Priority.ALWAYS);
        }

        eventListView = new ListView<>(selectedDayEvents);
        eventListView.setCellFactory(param -> new ListCell<>() {
            @Override
            protected void updateItem(CalendarEventItem item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.toDisplayText());
            }
        });

        VBox eventsBox = new VBox(8, new Label("Seçili Gün Etkinlikleri"), eventListView);
        VBox.setVgrow(eventListView, Priority.ALWAYS);
        eventsBox.setPadding(new Insets(10));

        connectGoogleButton.setOnAction(e -> connectGoogleAsync());

        prevButton.setOnAction(e -> {
            currentMonth = currentMonth.minusMonths(1);
            selectedDate = currentMonth.atDay(1);
            refreshCalendarView();
        });

        todayButton.setOnAction(e -> {
            currentMonth = YearMonth.now();
            selectedDate = LocalDate.now();
            refreshCalendarView();
        });

        nextButton.setOnAction(e -> {
            currentMonth = currentMonth.plusMonths(1);
            selectedDate = currentMonth.atDay(1);
            refreshCalendarView();
        });

        BorderPane content = new BorderPane();
        content.setTop(header);
        content.setCenter(calendarGrid);
        content.setBottom(eventsBox);
        BorderPane.setMargin(eventsBox, new Insets(0, 0, 8, 0));

        VBox wrapper = new VBox(content);
        VBox.setVgrow(content, Priority.ALWAYS);
        return wrapper;
    }

    private VBox buildTaskTab(TaskStorageService storageService) {
        Label title = new Label("Görev Planı");
        title.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");

        taskFilterCombo = new ComboBox<>();
        taskFilterCombo.getItems().addAll("Bugün", "Bu Hafta", "Tümü", "Seçili Gün");
        taskFilterCombo.setValue("Seçili Gün");

        Button refreshButton = new Button("Yenile");
        Button useSelectedDayButton = new Button("Takvimdeki Günü Kullan");
        taskInfoLabel = new Label("Hazır");

        HBox topBar = new HBox(10, title, taskFilterCombo, refreshButton, useSelectedDayButton, taskInfoLabel);
        topBar.setPadding(new Insets(10));
        topBar.setAlignment(Pos.CENTER_LEFT);

        taskListView = new ListView<>(filteredTasks);
        taskListView.setCellFactory(param -> new ListCell<>() {
            @Override
            protected void updateItem(TaskItem item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    String marker = item.getDate().equals(selectedDate) ? "● " : "";
                    setText(marker + item.getTitle() + " | " + item.getDate() + " | " + item.getStatus());
                }
            }
        });

        Label detailNameValue = new Label("-");
        Label detailDateValue = new Label("-");
        Label detailStatusValue = new Label("-");

        TextArea detailDescriptionArea = new TextArea();
        detailDescriptionArea.setEditable(false);
        detailDescriptionArea.setWrapText(true);

        GridPane detailGrid = new GridPane();
        detailGrid.setHgap(10);
        detailGrid.setVgap(10);
        detailGrid.addRow(0, new Label("Başlık:"), detailNameValue);
        detailGrid.addRow(1, new Label("Tarih:"), detailDateValue);
        detailGrid.addRow(2, new Label("Durum:"), detailStatusValue);

        VBox detailPane = new VBox(10, new Label("Görev Detayı"), detailGrid, new Label("Açıklama:"), detailDescriptionArea);
        detailPane.setPadding(new Insets(10));
        VBox.setVgrow(detailDescriptionArea, Priority.ALWAYS);

        TextField titleField = new TextField();
        taskDatePicker = new DatePicker(selectedDate);

        ComboBox<String> statusCombo = new ComboBox<>();
        statusCombo.getItems().addAll("Bekliyor", "Yapılıyor", "Tamamlandı");
        statusCombo.setValue("Bekliyor");

        TextArea descriptionField = new TextArea();
        descriptionField.setWrapText(true);
        descriptionField.setPrefRowCount(6);

        Button addButton = new Button("Yeni Ekle");
        Button updateButton = new Button("Seçiliyi Güncelle");
        Button deleteButton = new Button("Sil");
        Button markDoneButton = new Button("Tamamlandı Yap");

        HBox actionBar = new HBox(10, addButton, updateButton, deleteButton, markDoneButton);

        VBox editorPane = new VBox(
                10,
                new Label("Görev Düzenleyici"),
                new Label("Başlık"), titleField,
                new Label("Tarih"), taskDatePicker,
                new Label("Durum"), statusCombo,
                new Label("Açıklama"), descriptionField,
                actionBar
        );
        editorPane.setPadding(new Insets(10));

        SplitPane splitPane = new SplitPane(taskListView, detailPane, editorPane);
        splitPane.setDividerPositions(0.32, 0.66);

        BorderPane content = new BorderPane();
        content.setTop(topBar);
        content.setCenter(splitPane);
        BorderPane.setMargin(splitPane, new Insets(0, 0, 10, 0));

        VBox wrapper = new VBox(content);
        VBox.setVgrow(content, Priority.ALWAYS);
        VBox.setVgrow(splitPane, Priority.ALWAYS);

        refreshButton.setOnAction(e -> {
            try {
                List<TaskItem> loaded = storageService.loadTasks();
                allTasks.setAll(loaded);
                applyTaskFilter(taskFilterCombo.getValue());
                refreshCalendarView();
                taskInfoLabel.setText("Görevler diskten yeniden yüklendi");
            } catch (IllegalStateException ex) {
                taskInfoLabel.setText(ex.getMessage());
            }
        });

        useSelectedDayButton.setOnAction(e -> {
            taskDatePicker.setValue(selectedDate);
            taskFilterCombo.setValue("Seçili Gün");
            applyTaskFilter("Seçili Gün");
        });

        taskFilterCombo.setOnAction(e -> applyTaskFilter(taskFilterCombo.getValue()));

        taskListView.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal == null) {
                detailNameValue.setText("-");
                detailDateValue.setText("-");
                detailStatusValue.setText("-");
                detailDescriptionArea.clear();
                titleField.clear();
                descriptionField.clear();
                statusCombo.setValue("Bekliyor");
                taskDatePicker.setValue(selectedDate);
                return;
            }

            detailNameValue.setText(newVal.getTitle());
            detailDateValue.setText(newVal.getDate().toString());
            detailStatusValue.setText(newVal.getStatus());
            detailDescriptionArea.setText(newVal.getDescription());

            titleField.setText(newVal.getTitle());
            taskDatePicker.setValue(newVal.getDate());
            statusCombo.setValue(newVal.getStatus());
            descriptionField.setText(newVal.getDescription());
        });

        addButton.setOnAction(e -> {
            String titleText = titleField.getText();
            if (titleText == null || titleText.isBlank()) {
                taskInfoLabel.setText("Başlık boş olamaz");
                return;
            }

            LocalDate date = taskDatePicker.getValue() == null ? selectedDate : taskDatePicker.getValue();
            TaskItem newTask = new TaskItem(
                    titleText.trim(),
                    date,
                    descriptionField.getText() == null ? "" : descriptionField.getText().trim(),
                    statusCombo.getValue() == null ? "Bekliyor" : statusCombo.getValue()
            );

            List<TaskItem> proposed = new ArrayList<>(allTasks);
            proposed.add(newTask);
            if (commitTasks(storageService, proposed, "Yeni görev kaydedildi")) {
                taskListView.getSelectionModel().select(newTask);
            }
        });

        updateButton.setOnAction(e -> {
            TaskItem selectedTask = taskListView.getSelectionModel().getSelectedItem();
            if (selectedTask == null) {
                taskInfoLabel.setText("Güncellemek için görev seç");
                return;
            }

            String titleText = titleField.getText();
            if (titleText == null || titleText.isBlank()) {
                taskInfoLabel.setText("Başlık boş olamaz");
                return;
            }

            TaskItem updated = new TaskItem(
                    titleText.trim(),
                    taskDatePicker.getValue() == null ? selectedDate : taskDatePicker.getValue(),
                    descriptionField.getText() == null ? "" : descriptionField.getText().trim(),
                    statusCombo.getValue() == null ? "Bekliyor" : statusCombo.getValue()
            );
            List<TaskItem> proposed = new ArrayList<>(allTasks);
            proposed.set(proposed.indexOf(selectedTask), updated);
            if (commitTasks(storageService, proposed, "Görev güncellendi")) {
                taskListView.getSelectionModel().select(updated);
            }
        });

        deleteButton.setOnAction(e -> {
            TaskItem selectedTask = taskListView.getSelectionModel().getSelectedItem();
            if (selectedTask == null) {
                taskInfoLabel.setText("Silmek için görev seç");
                return;
            }

            List<TaskItem> proposed = new ArrayList<>(allTasks);
            proposed.remove(selectedTask);
            commitTasks(storageService, proposed, "Görev silindi");
        });

        markDoneButton.setOnAction(e -> {
            TaskItem selectedTask = taskListView.getSelectionModel().getSelectedItem();
            if (selectedTask == null) {
                taskInfoLabel.setText("Tamamlandı yapmak için görev seç");
                return;
            }

            TaskItem completed = new TaskItem(selectedTask.getTitle(), selectedTask.getDate(),
                    selectedTask.getDescription(), "Tamamlandı");
            List<TaskItem> proposed = new ArrayList<>(allTasks);
            proposed.set(proposed.indexOf(selectedTask), completed);
            if (commitTasks(storageService, proposed, "Görev tamamlandı olarak kaydedildi")) {
                taskListView.getSelectionModel().select(completed);
            }
        });

        return wrapper;
    }

    private void refreshCalendarView() {
        monthLabel.setText(formatMonth(currentMonth));

        List<CalendarEventItem> cachedEvents = googleEventsCache.getOrDefault(currentMonth, List.of());
        renderCalendarWithEvents(cachedEvents);
        updateCalendarHeaderOnly();

        if (googleCalendarService.isConnected()) {
            loadGoogleEventsAsync(currentMonth);
        }
    }

    private void renderCalendarWithEvents(List<CalendarEventItem> monthEvents) {
        Map<LocalDate, List<CalendarEventItem>> eventsByDate = monthEvents.stream()
                .collect(Collectors.groupingBy(CalendarEventItem::getDate));

        Set<LocalDate> taskDates = allTasks.stream().map(TaskItem::getDate).collect(Collectors.toSet());
        rebuildCalendarGrid(eventsByDate, taskDates);

        selectedDayEvents.setAll(eventsByDate.getOrDefault(selectedDate, List.of()));

        if (taskDatePicker != null && mainTabs.getSelectionModel().getSelectedItem() == taskTab) {
            taskDatePicker.setValue(selectedDate);
        }
    }

    private void connectGoogleAsync() {
        connectGoogleButton.setDisable(true);
        calendarInfoLabel.setText("Google Calendar bağlantısı başlatılıyor...");

        Task<Void> connectTask = new Task<>() {
            @Override
            protected Void call() throws Exception {
                googleCalendarService.connect();
                return null;
            }
        };

        connectTask.setOnSucceeded(e -> {
            connectGoogleButton.setDisable(false);
            googleEventsCache.clear();
            calendarInfoLabel.setText("Google Calendar bağlantısı başarılı. Etkinlikler yükleniyor...");
            refreshCalendarView();
        });

        connectTask.setOnFailed(e -> {
            connectGoogleButton.setDisable(false);
            Throwable ex = connectTask.getException();
            if (ex != null) {
                ex.printStackTrace();
                calendarInfoLabel.setText("Bağlantı hatası: " + ex.getClass().getSimpleName() + " - " + ex.getMessage());
            } else {
                calendarInfoLabel.setText("Bağlantı hatası: bilinmeyen hata");
            }
        });

        Thread thread = new Thread(connectTask, "google-calendar-connect");
        thread.setDaemon(true);
        thread.start();
    }

    private void loadGoogleEventsAsync(YearMonth monthToLoad) {
        long requestId = calendarLoadSequence.incrementAndGet();
        calendarInfoLabel.setText(googleCalendarService.getSourceHint() + " | Etkinlikler yükleniyor: " + formatMonth(monthToLoad));

        Task<List<CalendarEventItem>> loadTask = new Task<>() {
            @Override
            protected List<CalendarEventItem> call() {
                return googleCalendarService.loadEventsForMonth(monthToLoad);
            }
        };

        loadTask.setOnSucceeded(e -> {
            if (requestId != calendarLoadSequence.get()) {
                return;
            }
            googleEventsCache.put(monthToLoad, loadTask.getValue());
            if (monthToLoad.equals(currentMonth)) {
                renderCalendarWithEvents(loadTask.getValue());
                updateCalendarHeaderOnly();
            }
        });

        loadTask.setOnFailed(e -> {
            if (requestId != calendarLoadSequence.get()) {
                return;
            }
            Throwable ex = loadTask.getException();
            if (ex != null) {
                ex.printStackTrace();
                calendarInfoLabel.setText("Etkinlik yükleme hatası: " + ex.getClass().getSimpleName() + " - " + ex.getMessage());
            } else {
                calendarInfoLabel.setText("Etkinlik yükleme hatası: bilinmeyen hata");
            }
        });

        Thread thread = new Thread(loadTask, "google-calendar-load-" + monthToLoad);
        thread.setDaemon(true);
        thread.start();
    }

    private void updateCalendarHeaderOnly() {
        monthLabel.setText(formatMonth(currentMonth));

        String error = googleCalendarService.getLastErrorMessage();
        if (error == null || error.isBlank()) {
            calendarInfoLabel.setText(googleCalendarService.getSourceHint() + " | Seçili gün: " + selectedDate);
        } else {
            calendarInfoLabel.setText("Takvim hatası: " + error);
        }
    }

    private void rebuildCalendarGrid(Map<LocalDate, List<CalendarEventItem>> eventsByDate, Set<LocalDate> taskDates) {
        calendarGrid.getChildren().removeIf(node -> {
            Integer row = GridPane.getRowIndex(node);
            return row != null && row > 0;
        });

        LocalDate firstDay = currentMonth.atDay(1);
        int column = convertDayOfWeek(firstDay.getDayOfWeek());
        int row = 1;

        for (int day = 1; day <= currentMonth.lengthOfMonth(); day++) {
            LocalDate date = currentMonth.atDay(day);
            int eventCount = eventsByDate.getOrDefault(date, List.of()).size();
            long taskCount = allTasks.stream().filter(t -> date.equals(t.getDate())).count();

            Button button = new Button();
            button.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
            button.setMinHeight(76);

            StringBuilder text = new StringBuilder();
            text.append(day);
            if (eventCount > 0) text.append("\nGC: ").append(eventCount);
            if (taskCount > 0) text.append("\nGT: ").append(taskCount);
            button.setText(text.toString());

            String style = "-fx-background-radius: 8; -fx-padding: 6;";
            if (date.equals(LocalDate.now())) {
                style += "-fx-border-color: #2a7fff; -fx-border-width: 2;";
            }
            if (date.equals(selectedDate)) {
                style += "-fx-background-color: #dbeafe;";
            } else if (eventCount > 0 && taskCount > 0) {
                style += "-fx-background-color: #e9fce9;";
            } else if (eventCount > 0) {
                style += "-fx-background-color: #fff3cd;";
            } else if (taskCount > 0) {
                style += "-fx-background-color: #e8f0ff;";
            }
            button.setStyle(style);

            button.setOnMouseClicked(e -> {
                selectedDate = date;
                selectedDayEvents.setAll(eventsByDate.getOrDefault(selectedDate, List.of()));
                updateCalendarHeaderOnly();

                if (e.getClickCount() == 2) {
                    taskFilterCombo.setValue("Seçili Gün");
                    applyTaskFilter("Seçili Gün");

                    if (taskDatePicker != null) {
                        taskDatePicker.setValue(selectedDate);
                    }

                    mainTabs.getSelectionModel().select(taskTab);
                }
            });

            calendarGrid.add(button, column, row);
            GridPane.setHgrow(button, Priority.ALWAYS);
            GridPane.setVgrow(button, Priority.ALWAYS);

            column++;
            if (column > 6) {
                column = 0;
                row++;
            }
        }
    }

    private void applyTaskFilter(String filter) {
        filteredTasks.clear();
        LocalDate today = LocalDate.now();

        switch (filter == null ? "Tümü" : filter) {
            case "Bugün":
                for (TaskItem task : allTasks) if (task.getDate().equals(today)) filteredTasks.add(task);
                break;
            case "Bu Hafta":
                LocalDate endOfWeek = today.plusDays(6);
                for (TaskItem task : allTasks) {
                    if (!task.getDate().isBefore(today) && !task.getDate().isAfter(endOfWeek)) filteredTasks.add(task);
                }
                break;
            case "Seçili Gün":
                for (TaskItem task : allTasks) if (task.getDate().equals(selectedDate)) filteredTasks.add(task);
                break;
            default:
                filteredTasks.addAll(allTasks);
                break;
        }

        filteredTasks.sort(Comparator.comparing(TaskItem::getDate).thenComparing(TaskItem::getTitle));
        if (taskInfoLabel != null) {
            taskInfoLabel.setText(selectedDate + " için " + filteredTasks.size() + " görev");
        }
    }

    private int convertDayOfWeek(DayOfWeek dayOfWeek) {
        return switch (dayOfWeek) {
            case MONDAY -> 0;
            case TUESDAY -> 1;
            case WEDNESDAY -> 2;
            case THURSDAY -> 3;
            case FRIDAY -> 4;
            case SATURDAY -> 5;
            case SUNDAY -> 6;
        };
    }

    private String formatMonth(YearMonth month) {
        String[] months = {"Ocak", "Şubat", "Mart", "Nisan", "Mayıs", "Haziran",
                "Temmuz", "Ağustos", "Eylül", "Ekim", "Kasım", "Aralık"};
        return months[month.getMonthValue() - 1] + " " + month.getYear();
    }

    private Path resolveDefaultDataFile() {
        Path workingDir = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
        Path projectRoot = workingDir;
        if (workingDir.getFileName() != null && "app".equals(workingDir.getFileName().toString())) {
            projectRoot = workingDir.getParent();
        }
        return projectRoot.resolve("data/tasks.json");
    }

    private boolean commitTasks(TaskStorageService storage, List<TaskItem> proposed, String message) {
        proposed.sort(Comparator.comparing(TaskItem::getDate).thenComparing(TaskItem::getTitle));
        try {
            storage.saveTasks(proposed);
        } catch (IllegalStateException ex) {
            taskInfoLabel.setText(ex.getMessage());
            return false;
        }
        allTasks.setAll(proposed);
        applyTaskFilter(taskFilterCombo.getValue());
        refreshCalendarView();
        taskInfoLabel.setText(message);
        return true;
    }
}
