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

    private final GoogleCalendarService googleCalendarService;
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
    private Button refreshGoogleButton;
    private Label googleAccountLabel;
    private boolean googleConnecting;
    private boolean googleWriting;
    private Button addGoogleEventButton;
    private Button editGoogleEventButton;
    private Button deleteGoogleEventButton;
    private Path profileRoot;
    private Task<Void> connectionTask;
    private Button cancelGoogleButton;
    private Button chooseCalendarsButton;
    private final CheckBox showLocalSources=new CheckBox("Uygulama kayıtları");
    private final Map<YearMonth,CalendarMonthService.Month> monthDetails=new HashMap<>();
    private Task<?> sourcesTask;
    private boolean sourcesBusy;
    private String localPreferenceError;

    public CalendarModuleView() {
        this(new GoogleCalendarService());
    }

    CalendarModuleView(GoogleCalendarService googleCalendarService) {
        this.googleCalendarService = googleCalendarService;
    }
    GoogleCalendarService googleService() { return googleCalendarService; }

    public VBox build() {
        return build(resolveDefaultDataFile());
    }

    public VBox build(Path tasksFilePath) {
        profileRoot = tasksFilePath.toAbsolutePath().getParent();
        if (profileRoot.getFileName().toString().equals("data")) profileRoot = profileRoot.getParent();
        try{showLocalSources.setSelected(new CalendarSourcesStore(profileRoot).showLocal());localPreferenceError=null;}
        catch(java.io.IOException error){showLocalSources.setSelected(true);localPreferenceError=error.getMessage();}
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

    public VBox buildTasks(Path tasksFilePath) {
        VBox view = build(tasksFilePath);
        mainTabs.getSelectionModel().select(taskTab);
        return view;
    }

    private VBox buildCalendarTab() {
        Label title = new Label("Google Calendar Takvimi");
        title.getStyleClass().add("panel-baslik");

        Button prevButton = new Button("<");
        Button todayButton = new Button("Bugün");
        Button nextButton = new Button(">");

        connectGoogleButton = new Button("Google Calendar'a Bağlan");
        connectGoogleButton.setId("google-calendar-connect");
        refreshGoogleButton = new Button("Yenile");
        refreshGoogleButton.setId("google-calendar-refresh");
        cancelGoogleButton = new Button("Bağlantıyı iptal et"); cancelGoogleButton.setId("google-calendar-cancel");
        cancelGoogleButton.setOnAction(e -> {
            googleCalendarService.cancelAuthorization();
            if (connectionTask != null) connectionTask.cancel(true);
        });
        chooseCalendarsButton=new Button("Takvim kaynakları");chooseCalendarsButton.setId("google-calendar-sources");
        UiTheme.decorate(chooseCalendarsButton,"layers","flat");chooseCalendarsButton.setOnAction(e->chooseCalendars());
        showLocalSources.setId("calendar-show-local");showLocalSources.setOnAction(e->{
            try{new CalendarSourcesStore(profileRoot).showLocal(showLocalSources.isSelected());localPreferenceError=null;refreshCalendarView();}
            catch(java.io.IOException error){localPreferenceError=error.getMessage();showLocalSources.setSelected(!showLocalSources.isSelected());calendarInfoLabel.setText(localPreferenceError);}
        });
        googleAccountLabel = new Label();
        googleAccountLabel.setId("google-calendar-account");
        googleAccountLabel.setWrapText(true);
        googleAccountLabel.getStyleClass().add("soluk");

        monthLabel = new Label();
        monthLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");

        calendarInfoLabel = new Label("Hazır");
        calendarInfoLabel.setId("google-calendar-status");
        calendarInfoLabel.setWrapText(true);

        UiTheme.iconOnly(prevButton, "chevron-left");
        UiTheme.iconOnly(nextButton, "chevron-right");
        UiTheme.decorate(connectGoogleButton, "link", "flat");
        UiTheme.decorate(refreshGoogleButton, "refresh-cw", "flat");
        todayButton.getStyleClass().add("accent");
        FlowPane header = new FlowPane(10, 8, prevButton, todayButton, nextButton, monthLabel,
                connectGoogleButton, chooseCalendarsButton, refreshGoogleButton, cancelGoogleButton, showLocalSources, calendarInfoLabel);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(8));
        VBox calendarHeader = new VBox(4, header, googleAccountLabel);
        VBox.setMargin(googleAccountLabel, new Insets(0, 8, 8, 8));
        updateGoogleControls();

        calendarGrid = new GridPane();
        calendarGrid.setHgap(6);
        calendarGrid.setVgap(6);
        calendarGrid.setPadding(new Insets(10));
        calendarGrid.getStyleClass().add("calendar-grid");
        for (int i = 0; i < 7; i++) {
            ColumnConstraints column = new ColumnConstraints();
            column.setPercentWidth(100.0 / 7);
            column.setHgrow(Priority.ALWAYS);
            calendarGrid.getColumnConstraints().add(column);
        }

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
        eventListView.setId("google-calendar-events");
        eventListView.setPlaceholder(new Label("Seçili gün için etkinlik yok"));
        eventListView.setCellFactory(param -> new ListCell<>() {
            @Override
            protected void updateItem(CalendarEventItem item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.toDisplayText());
            }
        });

        addGoogleEventButton = new Button("Etkinlik ekle");
        addGoogleEventButton.setId("google-calendar-add");
        editGoogleEventButton = new Button("Düzenle");
        editGoogleEventButton.setId("google-calendar-edit");
        deleteGoogleEventButton = new Button("Sil");
        deleteGoogleEventButton.setId("google-calendar-delete");
        UiTheme.decorate(addGoogleEventButton, "plus", "accent");
        UiTheme.decorate(editGoogleEventButton, "edit-2", "flat");
        UiTheme.decorate(deleteGoogleEventButton, "trash-2", "flat");
        FlowPane eventActions = new FlowPane(10, 8, new Label("Seçili Gün Etkinlikleri"),
                addGoogleEventButton, editGoogleEventButton, deleteGoogleEventButton);
        eventActions.setAlignment(Pos.CENTER_LEFT);
        eventListView.setMinHeight(65);eventListView.setPrefHeight(165);
        VBox eventsBox = new VBox(8, eventActions, eventListView);eventsBox.setMinHeight(95);
        VBox.setVgrow(eventListView, Priority.ALWAYS);
        eventsBox.getStyleClass().add("panel");
        eventsBox.setPrefHeight(150);
        eventsBox.setMinHeight(95);

        connectGoogleButton.setOnAction(e -> connectGoogleAsync());
        refreshGoogleButton.setOnAction(e -> refreshCalendarView());
        addGoogleEventButton.setOnAction(e -> showGoogleEventDialog(null));
        editGoogleEventButton.setOnAction(e -> showGoogleEventDialog(eventListView.getSelectionModel().getSelectedItem()));
        deleteGoogleEventButton.setOnAction(e -> confirmGoogleEventDeletion());
        eventListView.getSelectionModel().selectedItemProperty().addListener((observable, previous, value) -> updateGoogleControls());
        eventListView.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2 && e.getButton() == javafx.scene.input.MouseButton.PRIMARY
                    && !editGoogleEventButton.isDisabled()) editGoogleEventButton.fire();
        });
        updateGoogleControls();

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
        content.setTop(calendarHeader);
        ScrollPane calendarScroll = new ScrollPane(calendarGrid);
        calendarScroll.setFitToWidth(true);
        calendarScroll.getStyleClass().add("content-scroll");
        calendarScroll.setMinHeight(0);
        SplitPane split=new SplitPane(calendarScroll,eventsBox);split.setOrientation(javafx.geometry.Orientation.VERTICAL);
        split.setDividerPositions(0.67);split.setId("calendar-month-split");content.setCenter(split);
        BorderPane.setMargin(eventsBox, new Insets(0, 0, 8, 0));

        VBox wrapper = new VBox(content);
        VBox.setVgrow(content, Priority.ALWAYS);
        return wrapper;
    }

    private VBox buildTaskTab(TaskStorageService storageService) {
        Label title = new Label("Görev Planı");
        title.getStyleClass().add("panel-baslik");

        taskFilterCombo = new ComboBox<>();
        taskFilterCombo.getItems().addAll("Bugün", "Bu Hafta", "Tümü", "Seçili Gün");
        taskFilterCombo.setValue("Seçili Gün");

        Button refreshButton = new Button("Yenile");
        Button useSelectedDayButton = new Button("Takvimdeki Günü Kullan");
        taskInfoLabel = new Label("Hazır");

        FlowPane topBar = new FlowPane(10, 8, title, taskFilterCombo, refreshButton, useSelectedDayButton, taskInfoLabel);
        topBar.setPadding(new Insets(10));
        topBar.setAlignment(Pos.CENTER_LEFT);

        taskListView = new ListView<>(filteredTasks);
        taskListView.setId("task-list");
        taskListView.setPlaceholder(new Label("Bu filtrede görev yok"));
        taskListView.setCellFactory(param -> new ListCell<>() {
            @Override
            protected void updateItem(TaskItem item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    String marker = item.getDate().equals(selectedDate) ? "● " : "";
                    setText(marker + item.getTitle() + " | " + item.getDate()
                            + (item.getDueTime() == null ? "" : " " + item.getDueTime())
                            + " | " + item.getPriority() + " | " + item.getStatus());
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
        TaskMetadataEditor metadata = new TaskMetadataEditor();

        Button addButton = new Button("Yeni Ekle");
        Button updateButton = new Button("Seçiliyi Güncelle");
        Button deleteButton = new Button("Sil");
        Button markDoneButton = new Button("Tamamlandı Yap");

        UiTheme.decorate(addButton, "plus", "accent");
        UiTheme.decorate(updateButton, "save", "flat");
        UiTheme.decorate(deleteButton, "trash-2", "danger");
        UiTheme.decorate(markDoneButton, "check", "flat");
        FlowPane actionBar = new FlowPane(8, 8, addButton, updateButton, deleteButton, markDoneButton);

        VBox editorPane = new VBox(
                10,
                new Label("Görev Düzenleyici"),
                new Label("Başlık"), titleField,
                new Label("Tarih"), taskDatePicker,
                new Label("Durum"), statusCombo,
                new Label("Açıklama"), descriptionField,
                metadata,
                actionBar
        );
        editorPane.setPadding(new Insets(10));

        detailPane.getStyleClass().add("panel");
        editorPane.getStyleClass().add("panel");
        taskListView.setMinWidth(140);
        detailPane.setMinWidth(180);
        editorPane.setMinWidth(0);
        taskDatePicker.setMinWidth(0);
        taskDatePicker.setMaxWidth(Double.MAX_VALUE);
        descriptionField.setMinWidth(0);
        ScrollPane editorScroll = new ScrollPane(editorPane);
        editorScroll.setFitToWidth(true);
        editorScroll.setMinWidth(230);
        editorScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        editorScroll.getStyleClass().add("content-scroll");
        SplitPane splitPane = new SplitPane(taskListView, detailPane, editorScroll);
        splitPane.getStyleClass().add("workspace-split");
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
                metadata.load(null);
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
            metadata.load(newVal);
            try {
                metadata.showProgress(new StudyProgressService().forTask(newVal,
                        new StudyStorageService(profileRoot.resolve("data/study/sessions.json")).load(), new StudyTimeLogService(profileRoot).load()));
            } catch (Exception error) { taskInfoLabel.setText("Çalışma süresi okunamadı: " + error.getMessage()); }
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

            try { metadata.apply(newTask); }
            catch (IllegalArgumentException error) { taskInfoLabel.setText(error.getMessage()); return; }

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

            TaskItem updated = selectedTask.copy();
            updated.setTitle(titleText.trim());
            updated.setDate(taskDatePicker.getValue() == null ? selectedDate : taskDatePicker.getValue());
            updated.setDescription(descriptionField.getText() == null ? "" : descriptionField.getText().trim());
            updated.setStatus(statusCombo.getValue() == null ? "Bekliyor" : statusCombo.getValue());
            try { metadata.apply(updated); }
            catch (IllegalArgumentException error) { taskInfoLabel.setText(error.getMessage()); return; }
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

            TaskItem completed = selectedTask.copy();
            completed.setStatus("Tamamlandı");
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

        if (!googleConnecting && !googleWriting && !sourcesBusy) {
            loadGoogleEventsAsync(currentMonth);
        }
    }

    private void renderCalendarWithEvents(List<CalendarEventItem> monthEvents) {
        CalendarEventItem selection = eventListView.getSelectionModel().getSelectedItem();
        Map<LocalDate, List<CalendarEventItem>> eventsByDate = new HashMap<>();
        for (LocalDate date = currentMonth.atDay(1); !date.isAfter(currentMonth.atEndOfMonth()); date = date.plusDays(1)) {
            LocalDate day = date;
            eventsByDate.put(day, monthEvents.stream().filter(event -> event.occursOn(day)).toList());
        }

        Set<LocalDate> taskDates = allTasks.stream().map(TaskItem::getDate).collect(Collectors.toSet());
        rebuildCalendarGrid(eventsByDate, taskDates);

        selectedDayEvents.setAll(eventsByDate.getOrDefault(selectedDate, List.of()));
        if (selection != null) {
            selectedDayEvents.stream().filter(item -> Objects.equals(item.key(), selection.key())).findFirst()
                    .ifPresent(item -> eventListView.getSelectionModel().select(item));
        }

        if (taskDatePicker != null && mainTabs.getSelectionModel().getSelectedItem() == taskTab) {
            taskDatePicker.setValue(selectedDate);
        }
    }

    private void chooseCalendars() {
        if(googleConnecting||googleWriting||sourcesBusy)return;sourcesBusy=true;updateGoogleControls();
        Task<List<GoogleCalendarService.Source>> task=new Task<>(){@Override protected List<GoogleCalendarService.Source> call()throws Exception{return googleCalendarService.calendarSources();}};
        sourcesTask=task;
        task.setOnSucceeded(e->{sourcesBusy=false;updateGoogleControls();var available=task.getValue();
            if(available.isEmpty()){calendarInfoLabel.setText("Takvim listesini almak için Google Calendar'a bağlan ve ek okuma izinlerini onayla.");return;}
            Dialog<Set<String>> dialog=new Dialog<>();dialog.setTitle("Gösterilecek Google takvimleri");UiTheme.styleDialog(dialog,eventListView);
            VBox choices=new VBox(9);choices.setPadding(new Insets(12));Map<String,CheckBox> fields=new LinkedHashMap<>();
            for(var source:available){CheckBox checkbox=new CheckBox(source.name()+(source.primary()?" · Ana takvim":""));checkbox.setSelected(source.selected());checkbox.setWrapText(true);fields.put(source.id(),checkbox);choices.getChildren().add(checkbox);}
            Label hint=new Label("Seçili Google takvimleri görünür. Yeni etkinlikler ana takvime yazılır; diğer kaynaklar bu ekranda okunur.");hint.setWrapText(true);choices.getChildren().add(hint);
            ScrollPane scroll=new ScrollPane(choices);scroll.setFitToWidth(true);scroll.setPrefViewportWidth(500);scroll.setPrefViewportHeight(Math.min(420,Math.max(180,available.size()*34)));dialog.getDialogPane().setContent(scroll);
            ButtonType save=new ButtonType("Seçimi kaydet",ButtonBar.ButtonData.OK_DONE);dialog.getDialogPane().getButtonTypes().setAll(ButtonType.CANCEL,save);
            dialog.getDialogPane().lookupButton(save).setId("calendar-sources-save");dialog.getDialogPane().setId("calendar-sources-dialog");
            dialog.setResultConverter(type->{if(type!=save)return null;Set<String> selected=new HashSet<>();fields.forEach((id,box)->{if(box.isSelected())selected.add(id);});return selected;});
            dialog.setOnHidden(done->{if(dialog.getResult()==null)return;sourcesBusy=true;updateGoogleControls();
                Task<Void> write=new Task<>(){@Override protected Void call()throws Exception{googleCalendarService.selectCalendars(dialog.getResult());return null;}};sourcesTask=write;
                write.setOnSucceeded(event->{sourcesBusy=false;updateGoogleControls();googleEventsCache.clear();monthDetails.clear();refreshCalendarView();});
                write.setOnFailed(event->{sourcesBusy=false;updateGoogleControls();calendarInfoLabel.setText(write.getException().getMessage());});Thread.ofVirtual().name("calendar-sources-save").start(write);
            });dialog.show();
        });
        task.setOnFailed(e->{sourcesBusy=false;updateGoogleControls();calendarInfoLabel.setText("Takvim listesi okunamadı: "+task.getException().getMessage());});
        Thread.ofVirtual().name("calendar-sources-load").start(task);
    }

    private void connectGoogleAsync() {
        if (googleConnecting || googleWriting) return;
        googleConnecting = true;
        calendarLoadSequence.incrementAndGet();
        updateGoogleControls();
        calendarInfoLabel.setText("Google Calendar bağlantısı başlatılıyor...");

        Task<Void> connectTask = new Task<>() {
            @Override
            protected Void call() throws Exception {
                googleCalendarService.connect();
                return null;
            }
        };
        connectionTask = connectTask;
        connectTask.setOnCancelled(e -> {
            googleConnecting = false; connectionTask = null; calendarLoadSequence.incrementAndGet(); updateGoogleControls();
            calendarInfoLabel.setText("Google bağlantısı iptal edildi.");
        });

        connectTask.setOnSucceeded(e -> {
            googleConnecting = false;
            updateGoogleControls();
            googleEventsCache.clear();
            calendarInfoLabel.setText("Google Calendar bağlantısı başarılı. Etkinlikler yükleniyor...");
            refreshCalendarView();
        });

        connectTask.setOnFailed(e -> {
            googleConnecting = false;
            updateGoogleControls();
            googleEventsCache.clear();
            renderCalendarWithEvents(List.of());
            Throwable ex = connectTask.getException();
            if (ex != null) {
                calendarInfoLabel.setText("Bağlantı hatası: " + googleCalendarService.getLastErrorMessage());
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

        record LoadResult(CalendarMonthService.Month month, String error) {}
        boolean includeLocal=showLocalSources.isSelected();
        List<CalendarEventItem> confirmedGoogle=googleEventsCache.getOrDefault(monthToLoad,List.of()).stream()
                .filter(item->!item.getCalendarId().startsWith("local:")).toList();
        Task<LoadResult> loadTask = new Task<>() {
            @Override
            protected LoadResult call() {
                synchronized (googleCalendarService) {
                    List<CalendarEventItem> events = googleCalendarService.loadEventsForMonth(monthToLoad);
                    String error=googleCalendarService.getLastErrorMessage();
                    if(error!=null)events=confirmedGoogle;
                    return new LoadResult(new CalendarMonthService().load(profileRoot,monthToLoad,events,includeLocal),error);
                }
            }
        };

        loadTask.setOnSucceeded(e -> {
            if (requestId != calendarLoadSequence.get()) {
                return;
            }
            var month=loadTask.getValue().month();
            monthDetails.put(monthToLoad,month);googleEventsCache.put(monthToLoad,month.items());
            if(monthToLoad.equals(currentMonth)){renderCalendarWithEvents(month.items());updateCalendarHeaderOnly();}

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
        monthLabel.setText(formatMonth(currentMonth));updateGoogleControls();
        var details=monthDetails.get(currentMonth);String message=details==null?"Kayıtlar yükleniyor…":
                "Bu ay "+details.items().size()+" kayıt · "+details.googleCount()+" Google / "+details.localCount()+" uygulama · Seçili gün: "+selectedDate;
        String error=googleCalendarService.getLastErrorMessage();if(error!=null&&!error.isBlank())message="Takvim hatası: "+error+" · "+message;
        else if(!googleCalendarService.isConnected())message+=(googleCalendarService.canWrite()?" · Google çevrimdışı kopyası":" · Diğer Google takvimleri için bağlan");
        if(details!=null&&!details.warnings().isEmpty())message+=" · "+String.join(" · ",details.warnings());
        if(googleCalendarService instanceof CalendarSyncService sync&&sync.pendingCount()>0)message+=" · "+sync.pendingCount()+" değişiklik gönderim bekliyor";
        if(googleCalendarService.getCacheWarning()!=null)message+=" · "+googleCalendarService.getCacheWarning();
        if(localPreferenceError!=null)message+=" · "+localPreferenceError;calendarInfoLabel.setText(message);
    }

    private void updateGoogleControls() {
        googleAccountLabel.setText(googleCalendarService.getSourceHint());
        connectGoogleButton.setText(googleCalendarService.isConnected()
                ? "Bağlantıyı doğrula" : "Google Calendar'a Bağlan");
        boolean busy=googleConnecting||googleWriting||sourcesBusy;
        boolean unavailable = busy || !googleCalendarService.canWrite();
        connectGoogleButton.setDisable(busy);
        if(chooseCalendarsButton!=null)chooseCalendarsButton.setDisable(busy);
        showLocalSources.setDisable(busy);
        cancelGoogleButton.setVisible(googleConnecting); cancelGoogleButton.setManaged(googleConnecting);
        refreshGoogleButton.setDisable(busy);
        if (addGoogleEventButton != null) {
            CalendarEventItem selected = eventListView.getSelectionModel().getSelectedItem();
            boolean noSelection = selected == null || !selected.isPrimaryCalendar() || !selected.isEditable()
                    || selected.getEtag() == null || selected.getEtag().isBlank();
            addGoogleEventButton.setDisable(unavailable);
            editGoogleEventButton.setDisable(unavailable || noSelection);
            deleteGoogleEventButton.setDisable(unavailable || noSelection);
        }
    }

    private void setGoogleWriting(boolean writing) {
        googleWriting = writing;
        if (writing) calendarLoadSequence.incrementAndGet(); // Ignore loads that started before this write.
        updateGoogleControls();
        if (writing) calendarInfoLabel.setText("Google takvimine kaydediliyor...");
        else updateCalendarHeaderOnly();
    }

    private void showGoogleEventDialog(CalendarEventItem original) {
        if (googleConnecting || googleWriting || !googleCalendarService.canWrite()) return;
        new GoogleCalendarEventDialog(eventListView, googleCalendarService, original, selectedDate,
                this::setGoogleWriting, saved -> {
                    YearMonth savedMonth = YearMonth.from(saved.getDate());
                    List<CalendarEventItem> confirmedEvents = new ArrayList<>(googleEventsCache.getOrDefault(savedMonth, List.of()));
                    confirmedEvents.removeIf(item -> item.isPrimaryCalendar() && Objects.equals(item.getId(), saved.getId()));
                    confirmedEvents.add(saved);
                    googleEventsCache.clear();
                    googleEventsCache.put(savedMonth, confirmedEvents);
                    selectedDate = saved.getDate();
                    currentMonth = savedMonth;
                    refreshCalendarView();
                }).show();
    }

    private void confirmGoogleEventDeletion() {
        CalendarEventItem selected = eventListView.getSelectionModel().getSelectedItem();
        if (selected == null || deleteGoogleEventButton.isDisabled()) return;
        Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION);
        confirmation.setTitle("Google etkinliğini sil");
        confirmation.setHeaderText(selected.getTitle());
        confirmation.setContentText(googleCalendarService.getWriteAccount() + " hesabının ana takviminden "
                + (selected.isRecurringInstance() ? "yalnızca seçili tekrar silinsin mi?" : "bu etkinlik silinsin mi?"));
        ButtonType delete = new ButtonType("Google'dan sil", ButtonBar.ButtonData.OK_DONE);
        confirmation.getButtonTypes().setAll(delete, ButtonType.CANCEL);
        UiTheme.styleDialog(confirmation, eventListView);
        confirmation.getDialogPane().setId("google-event-delete-dialog");
        confirmation.getDialogPane().lookupButton(delete).setId("google-event-delete-confirm");
        confirmation.setOnHidden(e -> {
            if (confirmation.getResult() != delete) return;
            setGoogleWriting(true);
            Task<Void> task = new Task<>() {
                @Override protected Void call() throws Exception {
                    googleCalendarService.deleteEvent(selected);
                    return null;
                }
            };
            task.setOnSucceeded(done -> {
                setGoogleWriting(false);
                List<CalendarEventItem> confirmedEvents = new ArrayList<>(googleEventsCache.getOrDefault(currentMonth, List.of()));
                confirmedEvents.removeIf(item -> item.isPrimaryCalendar() && Objects.equals(item.getId(), selected.getId()));
                googleEventsCache.clear();
                googleEventsCache.put(currentMonth, confirmedEvents);
                refreshCalendarView();
            });
            task.setOnFailed(done -> {
                setGoogleWriting(false);
                calendarInfoLabel.setText("Silme başarısız: " + task.getException().getMessage());
            });
            Thread worker = new Thread(task, "google-calendar-delete");
            worker.setDaemon(true);
            worker.start();
        });
        confirmation.show();
    }
    void dispose() {
        if (googleWriting || sourcesBusy) throw new IllegalStateException("Google kaydının tamamlanmasını bekle.");
        if (googleConnecting) { googleCalendarService.cancelAuthorization(); if (connectionTask != null) connectionTask.cancel(true); }
        calendarLoadSequence.incrementAndGet();
    }
    void refreshFromBackground() {
        if (monthLabel != null && monthLabel.getScene() != null && mainTabs.getSelectionModel().getSelectedItem() != taskTab && !googleWriting && !googleConnecting && !sourcesBusy) {
            googleEventsCache.clear(); refreshCalendarView();
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
            var entries=eventsByDate.getOrDefault(date,List.of());int eventCount=entries.size();
            Button button=new Button();button.setId("calendar-day-"+date);button.setMaxSize(Double.MAX_VALUE,Double.MAX_VALUE);button.setMinHeight(106);
            VBox cell=new VBox(4);Label number=new Label(String.valueOf(day));number.getStyleClass().add("calendar-date-label");cell.getChildren().add(number);
            for(var item:entries.stream().limit(3).toList()){
                String time=item.getStartTime()==null?"":item.getStartTime()+" ";Label label=new Label((item.isDone()?"✓ ":"")+time+item.getTitle());
                label.setMaxWidth(Double.MAX_VALUE);label.prefWidthProperty().bind(button.widthProperty().subtract(34));label.setTextOverrun(OverrunStyle.ELLIPSIS);
                var dot=new javafx.scene.shape.Circle(3,javafx.scene.paint.Color.web(item.getSourceColor()));
                HBox line=new HBox(5,dot,label);line.setAlignment(Pos.CENTER_LEFT);HBox.setHgrow(label,Priority.ALWAYS);cell.getChildren().add(line);
            }
            if(entries.size()>3)cell.getChildren().add(new Label("+"+(entries.size()-3)+" kayıt"));
            cell.setAlignment(Pos.TOP_LEFT);button.setGraphic(cell);button.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);button.setAlignment(Pos.TOP_LEFT);
            String tooltip=entries.stream().map(CalendarEventItem::toDisplayText).collect(Collectors.joining("\n"));button.setTooltip(new Tooltip(date+(tooltip.isBlank()?"":"\n"+tooltip)));

            button.getStyleClass().add("calendar-day");
            if (date.equals(LocalDate.now())) button.getStyleClass().add("today");
            if (date.equals(selectedDate)) button.getStyleClass().add("selected-day");
            if (eventCount > 0) button.getStyleClass().add("has-events");
            button.setAccessibleText(date+", "+eventCount+" kayıt. "+tooltip);

            Runnable selectDay=()->{
                selectedDate = date;
                calendarGrid.getChildren().forEach(node -> node.getStyleClass().remove("selected-day"));
                button.getStyleClass().add("selected-day");
                selectedDayEvents.setAll(eventsByDate.getOrDefault(selectedDate, List.of()));
                updateCalendarHeaderOnly();

            };
            button.setOnAction(e->selectDay.run());
            button.setOnMouseClicked(e -> {
                selectDay.run();
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
        return AppPaths.root().resolve("data/tasks.json");
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
