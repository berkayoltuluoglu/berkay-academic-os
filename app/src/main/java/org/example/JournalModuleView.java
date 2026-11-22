package org.example;

import javafx.animation.*;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.*;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.input.*;
import javafx.scene.layout.*;
import javafx.util.Duration;
import javafx.util.StringConverter;
import java.nio.file.Path;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import static org.example.PlannerCommand.*;

/** Native implementation of the user's Claude task-list and completed-activity design. */
final class JournalModuleView {
    private static final Locale TR=Locale.forLanguageTag("tr");
    private final Path profile;
    private final PlannerService planner;
    private final VBox root=new VBox(12), listsPane=new VBox(10), taskPane=new VBox(10), diaryPane=new VBox(12);
    private final BorderPane workspace=new BorderPane();
    private final StackPane canvas=new StackPane();
    private final VBox details=new VBox(10), entries=new VBox(14), summary=new VBox(12);
    private final ScrollPane detailScroll=scroll(details);
    private Runnable afterLoad;
    private String layoutKey="";
    private final ListView<JournalStore.TaskList> lists=new ListView<>();
    private final ListView<TaskItem> openTasks=new ListView<>(), doneTasks=new ListView<>();
    private final TitledPane completed=new TitledPane();
    private final TextField quick=new TextField(), newList=new TextField(), logInput=new TextField();
    private final Label title=new Label(), taskSummary=new Label(), preview=new Label(), error=new Label(), total=new Label(), linked=new Label();
    private final DatePicker day=new DatePicker(LocalDate.now(ZoneId.of("Europe/Istanbul")));
    private final HBox toast=new HBox(10);
    private final PauseTransition toastDelay=new PauseTransition(Duration.seconds(7));
    private final Timeline watch=new Timeline(new KeyFrame(Duration.seconds(5),event->refreshFromBackground()));
    private JournalTimeline.Snapshot data;
    private Task<?> pending;
    private boolean diary,disposed,rendering,dirty,writing;
    private TaskMetadataEditor detailMetadata;
    private String listId="genel",selected="",logTask="",editId="",editFocus="";
    private TextField detailTitle;
    private TextArea detailNote;
    private DatePicker detailDate;
    private ComboBox<JournalStore.TaskList> detailList;
    private ComboBox<String> detailCourse;

    JournalModuleView(Path profile,boolean diary){this.profile=profile;this.diary=diary;planner=new PlannerService(profile);}
    VBox build(){
        root.setId("journal-workspace");root.getStyleClass().add("journal-workspace");root.setMinWidth(0);
        taskPane.setMinWidth(0);diaryPane.setMinWidth(0);taskPane.getStyleClass().add("journal-task-pane");diaryPane.getStyleClass().add("journal-diary-pane");
        title.getStyleClass().add("journal-title");
        Button tasks=button("Yapılacaklar","journal-tasks",()->switchMode(false));
        Button log=button("Bugün Ne Yaptım?","journal-diary",()->switchMode(true));
        tasks.getStyleClass().add("journal-tab");log.getStyleClass().add("journal-tab");
        Button refresh=button("Yenile","journal-refresh",()->{if(dirty)showError("Önce açık ayrıntıları kaydet veya değişiklikten vazgeç.");else load(null);});UiTheme.iconOnly(refresh,"refresh-cw");
        Button undo=button("Geri al","journal-undo",()->write(()->planner.undo(),()->notifyUser("İşlem geri alındı")));
        Button redo=button("Yinele","journal-redo",()->write(()->planner.redo(),()->notifyUser("İşlem yinelendi")));
        UiTheme.iconOnly(undo,"corner-up-left");UiTheme.iconOnly(redo,"corner-up-right");
        Region spacer=new Region();HBox.setHgrow(spacer,Priority.ALWAYS);
        HBox heading=new HBox(8,tasks,log,spacer,undo,redo,refresh);heading.setAlignment(Pos.CENTER_LEFT);
        quick.setId("journal-quick");quick.setPromptText("+ Görev ekle — başlığı yaz, Enter'a bas");quick.getStyleClass().add("journal-quick");
        quick.setOnAction(e->addTask());
        openTasks.setId("journal-open-tasks");doneTasks.setId("journal-done-tasks");
        for(var view:List.of(openTasks,doneTasks)){
            view.getStyleClass().add("journal-task-list");view.setCellFactory(v->taskCell());
            view.setPlaceholder(new Label("Bu listede görev yok. Başlığı yukarıya yazabilirsin."));
            view.getSelectionModel().selectedItemProperty().addListener((o,a,b)->{if(!rendering&&b!=null)selectTask(b);});
            view.addEventFilter(KeyEvent.KEY_PRESSED,e->{var task=view.getSelectionModel().getSelectedItem();if(task==null)return;
                if(e.getCode()==KeyCode.SPACE){e.consume();toggle(task);}else if(e.getCode()==KeyCode.S){e.consume();startLog(task);}else if(e.getCode()==KeyCode.DELETE){e.consume();deleteTask(task);}else if(e.getCode()==KeyCode.F2){e.consume();selectTask(task);if(detailTitle!=null)detailTitle.requestFocus();}});
        }
        completed.setText("Tamamlananlar");completed.setContent(doneTasks);completed.setExpanded(false);doneTasks.setPrefHeight(150);
        HBox taskHeading=new HBox(8,title,space(),taskSummary);taskHeading.setAlignment(Pos.CENTER_LEFT);
        Label hint=new Label("Tarih ve ders isteğe bağlı; görevi seçince ayrıntılardan ekleyebilirsin.");hint.getStyleClass().add("soluk");hint.setWrapText(true);
        taskPane.getChildren().setAll(taskHeading,quick,hint,openTasks,completed);VBox.setVgrow(openTasks,Priority.ALWAYS);
        buildLists();buildDiary();
        details.getStyleClass().add("journal-details");details.setMinWidth(285);details.setPrefWidth(300);details.setMaxWidth(320);detailScroll.setMinWidth(285);detailScroll.setPrefWidth(300);detailScroll.setMaxWidth(320);detailScroll.getStyleClass().add("journal-detail-scroll");
        summary.getStyleClass().add("journal-summary");summary.setPrefWidth(250);summary.setMinWidth(220);summary.setMaxWidth(270);
        workspace.setMinWidth(0);canvas.getChildren().add(workspace);VBox.setVgrow(canvas,Priority.ALWAYS);
        root.widthProperty().addListener((o,a,b)->arrange());
        error.setId("journal-error");error.setWrapText(true);error.getStyleClass().add("journal-error");
        error.managedProperty().bind(error.textProperty().isNotEmpty());error.visibleProperty().bind(error.managedProperty());
        toast.setId("journal-toast");toast.getStyleClass().add("journal-toast");toast.setAlignment(Pos.CENTER_LEFT);toast.setVisible(false);toast.setManaged(false);
        toastDelay.setOnFinished(e->{toast.setVisible(false);toast.setManaged(false);});
        root.getChildren().setAll(heading,canvas,error,toast);
        root.addEventFilter(KeyEvent.KEY_PRESSED,this::keys);
        watch.setCycleCount(Timeline.INDEFINITE);watch.play();load(null);return root;
    }
    private void buildLists(){
        listsPane.getStyleClass().add("journal-lists");listsPane.setPrefWidth(190);listsPane.setMinWidth(150);listsPane.setMaxWidth(210);
        lists.setId("journal-lists");lists.setEditable(true);lists.setCellFactory(v->new ListCell<>(){
            private TextField editor;
            @Override protected void updateItem(JournalStore.TaskList item,boolean empty){super.updateItem(item,empty);setGraphic(null);setText(empty||item==null?null:item.name()+"  "+(data==null?"":data.tasks().stream().filter(t->!done(t)&&data.journal().placement(t.getId()).listId().equals(item.id())).count()));}
            @Override public void startEdit(){if(getItem()==null)return;super.startEdit();var old=getItem();editor=new TextField(old.name());setText(null);setGraphic(editor);editor.setOnAction(e->write(()->planner.execute(new RenameList(old.id(),editor.getText())),()->{cancelEdit();notifyUser("Liste adı güncellendi");}));editor.setOnKeyPressed(e->{if(e.getCode()==KeyCode.ESCAPE)cancelEdit();});Platform.runLater(()->{editor.requestFocus();editor.selectAll();});}
            @Override public void cancelEdit(){super.cancelEdit();updateItem(getItem(),false);}
        });
        lists.getSelectionModel().selectedItemProperty().addListener((o,a,b)->{if(!rendering&&b!=null){withCleanDetails(()->{listId=b.id();selected="";render();});}});
        lists.setOnMouseClicked(e->{if(e.getClickCount()==2&&lists.getSelectionModel().getSelectedIndex()>=0)lists.edit(lists.getSelectionModel().getSelectedIndex());});
        newList.setId("journal-new-list");newList.setPromptText("+ Yeni liste — Enter");newList.setOnAction(e->{String name=newList.getText().trim(),id=UUID.randomUUID().toString();write(()->planner.execute(new CreateList(id,name)),()->{listId=id;newList.clear();notifyUser("Liste oluşturuldu");render();});});
        Button rename=button("Adlandır","journal-rename-list",()->lists.edit(lists.getSelectionModel().getSelectedIndex()));
        Button remove=button("Listeyi sil","journal-delete-list",()->{if(!listId.equals("genel")&&confirm("Liste silinsin mi? Görevleri Genel listesine taşınacak."))write(()->planner.execute(new DeleteList(listId)),()->{listId="genel";render();notifyUser("Liste kaldırıldı");});});
        listsPane.getChildren().setAll(new Label("LİSTELER"),lists,newList,new HBox(5,rename,remove));VBox.setVgrow(lists,Priority.ALWAYS);
    }
    private void buildDiary(){
        day.setId("journal-date");day.setEditable(false);day.setPrefWidth(170);
        day.setConverter(dateConverter());
        day.setDayCellFactory(p->new DateCell(){@Override public void updateItem(LocalDate value,boolean empty){super.updateItem(value,empty);setDisable(!empty&&value.isAfter(today()));}});
        day.valueProperty().addListener((o,a,b)->{if(!rendering&&b!=null){if(b.isAfter(today())){day.setValue(a);return;}editId="";editFocus="";load(null);}});
        Button previous=button("Önceki gün","journal-previous",()->shiftDay(-1));UiTheme.iconOnly(previous,"chevron-left");
        Button next=button("Sonraki gün","journal-next",()->shiftDay(1));UiTheme.iconOnly(next,"chevron-right");
        Label heading=new Label("Bugün Ne Yaptım?");heading.getStyleClass().add("journal-title");total.setId("journal-day-total");total.getStyleClass().add("journal-total");
        HBox top=new HBox(8,heading,space(),total);top.setAlignment(Pos.CENTER_LEFT);
        HBox dates=new HBox(6,previous,day,next,button("Bugün","journal-today",()->day.setValue(today())));dates.setAlignment(Pos.CENTER_LEFT);
        logInput.setId("journal-input");logInput.setPromptText("10:30–11:15 Sinyaller çalıştım  veya  30 dk kontrol çalıştım");logInput.getStyleClass().add("journal-quick");
        logInput.textProperty().addListener((o,a,b)->updatePreview());logInput.setOnAction(e->saveLog(false));
        logInput.addEventFilter(KeyEvent.KEY_PRESSED,e->{if(e.getCode()==KeyCode.ENTER&&e.isAltDown()){e.consume();saveLog(true);}else if(e.getCode()==KeyCode.ESCAPE){resetLog();}});
        preview.setId("journal-preview");preview.setWrapText(true);preview.getStyleClass().add("soluk");
        linked.setId("journal-linked-task");linked.setWrapText(true);linked.getStyleClass().add("journal-link");linked.managedProperty().bind(linked.textProperty().isNotEmpty());linked.visibleProperty().bind(linked.managedProperty());
        HBox actions=new HBox(8,button("Kaydet","journal-save-log",()->saveLog(false)),button("Vazgeç","journal-cancel-log",this::resetLog),button("Görev bağlantısını kaldır","journal-unlink",()->{if(!editFocus.isBlank())showError("Sayaç kaydının görev bağlantısı odak kaynağında korunur.");else{logTask="";updatePreview();}}));
        diaryPane.getChildren().setAll(top,dates,linked,logInput,preview,actions,scroll(entries));VBox.setVgrow(diaryPane.getChildren().getLast(),Priority.ALWAYS);
    }
    private ListCell<TaskItem> taskCell(){return new ListCell<>(){
        @Override protected void updateItem(TaskItem item,boolean empty){super.updateItem(item,empty);setText(null);setGraphic(null);if(empty||item==null||data==null)return;
            CheckBox check=new CheckBox();check.setSelected(done(item));check.setAccessibleText(item.getTitle()+" tamamlandı");check.setOnAction(e->toggle(item));
            Label name=new Label(item.getTitle());name.setMinWidth(0);name.setMaxWidth(Double.MAX_VALUE);name.setTooltip(new Tooltip(item.getTitle()));HBox.setHgrow(name,Priority.ALWAYS);if(done(item))name.getStyleClass().add("journal-done");
            var p=new StudyProgressService().forTask(item,data.sessions(),data.focus(),data.journal().activities());
            Label time=new Label(p.studiedMinutes()>0?minutes(p.studiedMinutes()):"");time.getStyleClass().add("soluk");
            Label due=new Label(item.getDate()==null?"":item.getDate().format(DateTimeFormatter.ofPattern("d MMM",TR)));if(item.getDate()!=null&&item.getDate().isBefore(today())&&!done(item))due.getStyleClass().add("journal-overdue");
            Button record=button("Süre kaydet","journal-record-"+item.getId(),()->startLog(item));record.getStyleClass().add("journal-small");
            HBox row=new HBox(8,check,name,time,due,record);row.setAlignment(Pos.CENTER_LEFT);row.prefWidthProperty().bind(getListView().widthProperty().subtract(32));row.setMinWidth(0);row.getStyleClass().add("journal-task-row");setGraphic(row);
        }};}
    private void addTask(){
        if(quick.getText().isBlank()){showError("Görev başlığını yaz.");return;}
        var task=new TaskItem(quick.getText().trim(),null,"","Bekliyor");String destination=listId;
        write(()->planner.execute(List.of(new CreateTask(task),new PlaceTask(task.getId(),destination,"")),"user","Hızlı görev ekle"),()->{quick.clear();notifyUser("Görev eklendi");});
    }
    private void toggle(TaskItem task){write(()->planner.execute(new CompleteItem("task",task.getId(),!done(task))),()->notifyUser(done(task)?"Görev yeniden açıldı":"Görev tamamlandı"));}
    private void deleteTask(TaskItem task){if(confirm("“"+task.getTitle()+"” silinsin mi? Günlük kayıtları ve çalışma blokları korunacak."))write(()->planner.execute(new DeleteTask(task.getId())),()->{selected="";dirty=false;render();notifyUser("Görev silindi");});}
    private void selectTask(TaskItem task){withCleanDetails(()->{selected=task.getId();renderDetails();arrange();});}
    private void renderDetails(){
        if(data==null||dirty)return;var task=data.tasks().stream().filter(t->t.getId().equals(selected)).findFirst().orElse(null);details.getChildren().clear();if(task==null){selected="";return;}
        var placement=data.journal().placement(task.getId());
        Label heading=new Label("Görev ayrıntıları");heading.getStyleClass().add("panel-baslik");
        detailTitle=new TextField(task.getTitle());detailTitle.setId("journal-detail-title");detailNote=new TextArea(task.getDescription());detailNote.setId("journal-detail-note");detailNote.setPrefRowCount(4);detailNote.setWrapText(true);
        detailDate=new DatePicker(task.getDate());detailDate.setId("journal-detail-date");detailDate.setEditable(false);
        detailDate.setConverter(dateConverter());
        detailList=new ComboBox<>();detailList.setId("journal-detail-list");detailList.getItems().setAll(data.journal().lists());detailList.setValue(data.journal().lists().stream().filter(l->l.id().equals(placement.listId())).findFirst().orElse(data.journal().lists().getFirst()));detailList.setMaxWidth(Double.MAX_VALUE);
        detailCourse=new ComboBox<>();detailCourse.setId("journal-detail-course");detailCourse.getItems().add("");data.courses().courses().forEach(c->detailCourse.getItems().add(c.id()));detailCourse.setValue(placement.courseId());detailCourse.setMaxWidth(Double.MAX_VALUE);
        detailCourse.setConverter(new StringConverter<>(){public String toString(String id){return data.courses().courses().stream().filter(c->c.id().equals(id)).map(CourseData.Course::name).findFirst().orElse("Ders bağlantısı yok");}public String fromString(String value){return value;}});
        for(var field:List.of(detailTitle.textProperty(),detailNote.textProperty()))field.addListener((o,a,b)->dirty=true);
        detailDate.valueProperty().addListener((o,a,b)->dirty=true);detailList.valueProperty().addListener((o,a,b)->dirty=true);detailCourse.valueProperty().addListener((o,a,b)->dirty=true);
        detailTitle.setOnAction(e->saveDetails(null));
        detailMetadata=new TaskMetadataEditor();detailMetadata.load(task);
        detailMetadata.showProgress(new StudyProgressService().forTask(task,data.sessions(),data.focus(),data.journal().activities()));detailMetadata.onChange(()->dirty=true);
        var advanced=new TitledPane("Saat, öncelik ve alt görevler",detailMetadata);advanced.setId("journal-advanced");advanced.setExpanded(false);
        details.getChildren().addAll(heading,new Label("Başlık"),detailTitle,new Label("Liste"),detailList,new Label("Son tarih · isteğe bağlı"),detailDate,
                button("Tarihi kaldır","journal-clear-due",()->detailDate.setValue(null)),new Label("Ders · isteğe bağlı"),detailCourse,new Label("Not"),detailNote,
                button("Değişiklikleri kaydet","journal-save-details",()->saveDetails(null)),button("Kapat / değişiklikten vazgeç","journal-close-details",()->{dirty=false;selected="";arrange();}),
                button("Süre kaydet","journal-detail-record",()->withCleanDetails(()->startLog(task))),button("Görevi sil","journal-detail-delete",()->deleteTask(task)));
        var history=new VBox(5);data.journal().activities().stream().filter(a->a.taskId().equals(task.getId())&&a.focusId().isBlank()).sorted(Comparator.comparing(JournalStore.Activity::date).reversed()).limit(8).forEach(a->{Button item=button(a.date()+" · "+minutes(a.minutes()),"journal-task-entry-"+a.id(),()->{withCleanDetails(()->{diary=true;logTask=task.getId();rendering=true;day.setValue(LocalDate.parse(a.date()));rendering=false;load(()->editEntry(a.id()));});});history.getChildren().add(item);});
        details.getChildren().addAll(new Label("Bu görevin son çalışma kayıtları"),history);
        details.getChildren().add(advanced);
    }
    private void saveDetails(Runnable next){
        if(data==null||selected.isBlank())return;var old=data.tasks().stream().filter(t->t.getId().equals(selected)).findFirst().orElse(null);if(old==null)return;
        var task=old.copy();task.setTitle(detailTitle.getText().trim());task.setDescription(detailNote.getText());task.setDate(detailDate.getValue());String course=Objects.toString(detailCourse.getValue(),"");var list=detailList.getValue();
        try{detailMetadata.apply(task);}catch(IllegalArgumentException failure){showError(failure.getMessage());return;}
        write(()->planner.execute(List.of(new UpdateTask(task),new PlaceTask(task.getId(),list.id(),course)),"user","Görev ayrıntılarını güncelle"),()->{dirty=false;notifyUser("Ayrıntılar kaydedildi");if(next!=null)next.run();else renderDetails();});
    }
    private void withCleanDetails(Runnable next){if(dirty)saveDetails(next);else next.run();}
    private void switchMode(boolean value){withCleanDetails(()->{diary=value;arrange();if(diary)renderDiary();});}
    private void startLog(TaskItem task){withCleanDetails(()->{diary=true;logTask=task.getId();editId="";editFocus="";rendering=true;day.setValue(today());rendering=false;logInput.setText(task.getTitle());arrange();load(()->{updatePreview();logInput.requestFocus();logInput.positionCaret(0);});});}
    private void updatePreview(){
        linked.setText(logTask.isBlank()?"":data==null?"Göreve bağlı":data.tasks().stream().filter(t->t.getId().equals(logTask)).map(t->"Bağlı görev: "+t.getTitle()+" · tamamlanma durumu değişmez").findFirst().orElse("Görev bağlantısı bulunamadı"));
        if(logInput.getText().isBlank()){preview.setText("Saat aralığı veya yalnızca süre yaz; Enter ile kaydet.");return;}
        try{var parsed=ActivityParser.parse(logInput.getText());String text=(parsed.timed()?parsed.start()+"–"+parsed.end()+" · ":"")+minutes(parsed.minutes())+" · "+parsed.title();
            var conflict=data==null?null:JournalTimeline.conflict(parsed,logTask,editId,data.entries());
            if(!editFocus.isBlank()||conflict!=null)text+="\nSayaç süresi zaten kayıtlı: Enter birleştirir, süre sayaçtan alınır. Ayrı çalışmaysa Alt+Enter.";
            else if(data!=null&&data.entries().stream().anyMatch(e->!e.id().equals(editId)&&parsed.timed()&&!e.start().isBlank()&&parsed.start().compareTo(e.end())<0&&parsed.end().compareTo(e.start())>0))text+="\nBaşka bir kayıtla çakışıyor. Ayrı kaydetmek için Alt+Enter.";
            preview.setText(text);
        }catch(IllegalArgumentException ex){preview.setText(ex.getMessage());}
    }
    private void saveLog(boolean separate){
        if(data==null||busy())return;
        try{var parsed=ActivityParser.parse(logInput.getText());String id=editId.isBlank()?UUID.randomUUID().toString():editId;
            var conflict=separate?null:!editFocus.isBlank()?data.entries().stream().filter(e->e.focusId().equals(editFocus)).findFirst().orElse(null):JournalTimeline.conflict(parsed,logTask,editId,data.entries());
            String focusId=conflict==null?"":conflict.focusId(),taskId=conflict==null?logTask:conflict.taskId();
            var activity=new JournalStore.Activity(id,day.getValue().toString(),parsed.title(),focusId.isBlank()?parsed.start():"",focusId.isBlank()?parsed.end():"",focusId.isBlank()?parsed.minutes():0,taskId,focusId);
            write(()->planner.execute(new SaveActivity(activity,separate)),()->{resetLog();notifyUser(focusId.isBlank()?"Çalışma kaydedildi":"Sayaç kaydıyla birleştirildi; süre bir kez sayıldı");});
        }catch(IllegalArgumentException ex){showError(ex.getMessage());}
    }
    private void resetLog(){editId="";editFocus="";logTask="";logInput.clear();error.setText("");updatePreview();}
    private void editEntry(String id){if(data==null)return;var entry=data.entries().stream().filter(e->e.editId().equals(id)||e.id().equals(id)).findFirst().orElse(null);if(entry==null)return;
        editId=entry.editId();editFocus=entry.focusId();logTask=entry.taskId();String prefix=entry.end().equals("24:00")||entry.start().isBlank()?entry.minutes()+" dk ":entry.start()+"–"+entry.end()+" ";
        logInput.setText(prefix+entry.title());updatePreview();logInput.requestFocus();}
    private void renderDiary(){
        if(data==null)return;entries.getChildren().clear();int all=data.entries().stream().mapToInt(JournalTimeline.Entry::minutes).sum();total.setText(minutes(all));
        for(boolean timed:List.of(true,false)){
            var group=data.entries().stream().filter(e->!e.start().isBlank()==timed).toList();VBox section=new VBox(6);Label name=new Label((timed?"SAATLİ KAYITLAR":"YALNIZCA SÜRE")+" · "+minutes(group.stream().mapToInt(JournalTimeline.Entry::minutes).sum()));name.getStyleClass().add("journal-section-title");section.getChildren().add(name);
            for(var entry:group){Label at=new Label(timed?entry.start()+"–"+entry.end():"Süresiyle");at.setMinWidth(timed?102:74);at.getStyleClass().add("soluk");Label text=new Label(entry.title());text.setMinWidth(0);text.setMaxWidth(Double.MAX_VALUE);text.setWrapText(true);HBox.setHgrow(text,Priority.ALWAYS);
                Label source=new Label(entry.source());source.getStyleClass().add(entry.focusId().isBlank()?"journal-manual-badge":"journal-focus-badge");Label duration=new Label(minutes(entry.minutes()));duration.setMinWidth(52);
                Button edit=button(entry.focusId().isBlank()?"Düzenle":"Not ekle","journal-edit-entry-"+entry.id(),()->editEntry(entry.id()));edit.getStyleClass().add("journal-small");
                HBox row=new HBox(8,at,text,source,duration,edit);row.setId("journal-entry-"+entry.id());row.setAlignment(Pos.CENTER_LEFT);row.getStyleClass().add("journal-entry");
                if(!entry.editId().isBlank()){Button remove=button(entry.focusId().isBlank()?"Sil":"Notu kaldır","journal-delete-entry-"+entry.id(),()->write(()->planner.execute(new DeleteActivity(entry.editId())),()->{resetLog();notifyUser(entry.focusId().isBlank()?"Kayıt silindi":"Sayaç notu kaldırıldı; süre korundu");}));remove.getStyleClass().add("journal-small");row.getChildren().add(remove);}
                section.getChildren().add(row);
            }
            if(group.isEmpty()){Label empty=new Label(timed?"Saat aralığıyla kaydedilmiş çalışma yok.":"Saat belirtmeden süre de kaydedebilirsin.");empty.getStyleClass().add("soluk");section.getChildren().add(empty);}entries.getChildren().add(section);
        }
        summary.getChildren().setAll(new Label("GÜNÜN ÖZETİ"),new Label(day.getValue().format(DateTimeFormatter.ofPattern("d MMMM EEEE",TR))),new Label("Toplam "+minutes(all)),
                wrapped("10:30–11:15 Sinyaller çalıştım\n30 dk kontrol çalıştım\n1,5 saat rapor yazdım"),wrapped("Sayaç kayıtları otomatik görünür. Birleştirme açıklamayı günceller; sayaç süresi odak kaynağında korunur."),wrapped("Aynı anda yapılan ayrı faaliyetleri Alt+Enter ile açıkça ayrı kaydedebilirsin."));
        updatePreview();
    }
    private void render(){
        if(data==null)return;rendering=true;
        if(data.journal().lists().stream().noneMatch(l->l.id().equals(listId)))listId="genel";
        lists.getItems().setAll(data.journal().lists());lists.getItems().stream().filter(l->l.id().equals(listId)).findFirst().ifPresent(l->lists.getSelectionModel().select(l));
        var tasks=data.tasks().stream().filter(t->data.journal().placement(t.getId()).listId().equals(listId)).toList();
        openTasks.getItems().setAll(tasks.stream().filter(t->!done(t)).toList());doneTasks.getItems().setAll(tasks.stream().filter(JournalModuleView::done).toList());
        title.setText(data.journal().lists().stream().filter(l->l.id().equals(listId)).map(JournalStore.TaskList::name).findFirst().orElse("Genel"));taskSummary.setText(openTasks.getItems().size()+" açık · "+doneTasks.getItems().size()+" tamamlandı");completed.setText("Tamamlananlar · "+doneTasks.getItems().size());
        for(var view:List.of(openTasks,doneTasks))view.getItems().stream().filter(t->t.getId().equals(selected)).findFirst().ifPresent(t->view.getSelectionModel().select(t));
        rendering=false;renderDetails();renderDiary();arrange();
    }
    private void arrange(){
        String key=diary+":"+selected.isBlank()+":"+(root.getWidth()>1050);if(key.equals(layoutKey))return;layoutKey=key;
        Node taskTab=root.lookup("#journal-tasks"),logTab=root.lookup("#journal-diary");
        if(taskTab!=null)taskTab.pseudoClassStateChanged(javafx.css.PseudoClass.getPseudoClass("active"),!diary);
        if(logTab!=null)logTab.pseudoClassStateChanged(javafx.css.PseudoClass.getPseudoClass("active"),diary);
        workspace.setLeft(diary?null:listsPane);workspace.setCenter(diary?diaryPane:taskPane);workspace.setRight(null);canvas.getChildren().remove(detailScroll);
        if(diary){if(root.getWidth()>1050)workspace.setRight(summary);}
        else if(!selected.isBlank()){
            if(root.getWidth()>1050)workspace.setRight(detailScroll);
            else {canvas.getChildren().add(detailScroll);StackPane.setAlignment(detailScroll,Pos.CENTER_RIGHT);}
        }
    }
    private void shiftDay(int direction){LocalDate next=day.getValue().plusDays(direction);if(!next.isAfter(today()))day.setValue(next);}
    private LocalDate today(){return LocalDate.now(data==null?ZoneId.of("Europe/Istanbul"):data.timezone());}
    private void load(Runnable after){
        if(disposed)return;if(busy()){if(after!=null)afterLoad=after;return;}LocalDate selectedDay=day.getValue();
        Task<JournalTimeline.Snapshot> task=new Task<>(){@Override protected JournalTimeline.Snapshot call()throws Exception{return JournalTimeline.load(profile,selectedDay);}};
        pending=task;task.setOnSucceeded(e->{if(disposed||pending!=task)return;pending=null;if(!selectedDay.equals(day.getValue())){load(after);return;}data=task.getValue();planner.expectFiles(data.baseline());render();Runnable next=after!=null?after:afterLoad;afterLoad=null;if(next!=null)next.run();});task.setOnFailed(e->{if(disposed||pending!=task)return;pending=null;showError(task.getException().getMessage());});Thread.ofVirtual().name("journal-read").start(task);
    }
    private void write(Action action,Runnable after){
        if(disposed||busy())return;error.setText("");LocalDate selectedDay=day.getValue();writing=true;root.setDisable(true);
        Task<JournalTimeline.Snapshot> task=new Task<>(){@Override protected JournalTimeline.Snapshot call()throws Exception{action.run();return JournalTimeline.load(profile,selectedDay);}};
        pending=task;task.setOnSucceeded(e->{if(disposed||pending!=task)return;pending=null;writing=false;root.setDisable(false);data=task.getValue();planner.expectFiles(data.baseline());render();if(after!=null)after.run();});task.setOnFailed(e->{if(disposed||pending!=task)return;pending=null;writing=false;root.setDisable(false);showError(task.getException().getMessage());});Thread.ofVirtual().name("journal-write").start(task);
    }
    private void keys(KeyEvent e){
        if(e.isControlDown()&&e.getCode()==KeyCode.DIGIT1){e.consume();switchMode(false);}else if(e.isControlDown()&&e.getCode()==KeyCode.DIGIT2){e.consume();switchMode(true);}
        else if(e.isControlDown()&&e.getCode()==KeyCode.N){e.consume();(diary?logInput:quick).requestFocus();}
        else if(e.isAltDown()&&diary&&(e.getCode()==KeyCode.LEFT||e.getCode()==KeyCode.RIGHT)){e.consume();shiftDay(e.getCode()==KeyCode.LEFT?-1:1);}
        else if(e.isControlDown()&&e.getCode()==KeyCode.Z&&!(e.getTarget() instanceof TextInputControl)){e.consume();write(()->e.isShiftDown()?planner.redo():planner.undo(),()->notifyUser("İşlem geçmişi güncellendi"));}
        else if(e.getCode()==KeyCode.ESCAPE&&!diary&&!(e.getTarget() instanceof TextInputControl)){selected="";dirty=false;arrange();}
    }
    private void notifyUser(String text){toastDelay.stop();toast.getChildren().setAll(new Label(text),button("Geri al","journal-toast-undo",()->write(()->planner.undo(),()->{toast.setVisible(false);toast.setManaged(false);})));toast.setVisible(true);toast.setManaged(true);toastDelay.playFromStart();}
    private void showError(String text){error.setText(Objects.toString(text,"İşlem tamamlanamadı; girdin korundu."));}
    private boolean confirm(String text){Alert dialog=new Alert(Alert.AlertType.CONFIRMATION,text,ButtonType.YES,ButtonType.NO);UiTheme.styleDialog(dialog,root);dialog.setHeaderText(null);return dialog.showAndWait().orElse(ButtonType.NO)==ButtonType.YES;}
    private static boolean done(TaskItem task){return "Tamamlandı".equals(task.getStatus());}
    private static Button button(String text,String id,Runnable action){Button button=new Button(text);button.setId(id);button.setOnAction(e->action.run());return button;}
    private static Region space(){Region value=new Region();HBox.setHgrow(value,Priority.ALWAYS);return value;}
    private static Label wrapped(String text){Label value=new Label(text);value.setWrapText(true);value.getStyleClass().add("soluk");return value;}
    private static ScrollPane scroll(Node child){ScrollPane scroll=new ScrollPane(child);scroll.setFitToWidth(true);scroll.setMinWidth(0);scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);scroll.getStyleClass().add("journal-scroll");return scroll;}
    private static String minutes(int value){return value>=60?value/60+" sa"+(value%60==0?"":" "+value%60+" dk"):value+" dk";}
    private boolean busy(){return pending!=null;}
    boolean isBusy(){return busy();}
    void requireIdle(){if(writing||dirty)throw new IllegalStateException("Önce devam eden kaydı bitir; açık ayrıntıları kaydet veya değişiklikten vazgeç.");}
    void refreshFromBackground(){if(!disposed&&!busy()&&!dirty&&logInput.getText().isBlank())load(null);}
    void dispose(){requireIdle();disposed=true;watch.stop();toastDelay.stop();if(pending!=null)pending.cancel(false);}
    private static javafx.util.StringConverter<LocalDate> dateConverter(){return new javafx.util.StringConverter<>(){
        private final java.time.format.DateTimeFormatter format=java.time.format.DateTimeFormatter.ofPattern("dd.MM.uuuu");
        public String toString(LocalDate value){return value==null?"":value.format(format);}
        public LocalDate fromString(String value){return value==null||value.isBlank()?null:LocalDate.parse(value,format);}
    };}
    private interface Action {Object run()throws Exception;}
}
