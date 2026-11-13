package org.example;

import javafx.animation.*;
import javafx.concurrent.Task;
import javafx.geometry.*;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.input.*;
import javafx.scene.layout.*;
import javafx.util.Duration;
import java.nio.file.Path;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Consumer;
import static org.example.PlannerCommand.*;

/** A seven-day time grid. IO and commands always run off the JavaFX thread. */
public final class WeeklyAgendaView {
    private static final int FIRST=480, LAST=1439;
    private static final double SCALE=0.85;
    private static final Locale TR=Locale.forLanguageTag("tr");
    private final Path rootPath;
    private final Consumer<LocalDate> openDay;
    private final Consumer<String> navigate;
    private final PlannerService planner;
    private final FocusTimerService focus;
    private final VBox root=new VBox(10), pool=new VBox(8), inspector=new VBox(10);
    private final HBox grid=new HBox();
    private final HBox dayHeaders=new HBox();
    private final SplitPane layout=new SplitPane();
    private final ToggleButton detailsToggle=new ToggleButton("Ayrıntılar");
    private ScrollPane inspectorScroll;
    private ScrollPane deadlineHeaderScroll;
    private final Label title=new Label(), status=new Label(), statistics=new Label();
    private final Button proposalsButton=new Button("Öneriler");
    private List<PlanProposal> proposals=List.of();
    private String proposalsStamp="";
    private final TextField search=new TextField();
    private final PauseTransition debounce=new PauseTransition(Duration.millis(400));
    private final Timeline autoScroll=new Timeline();private double scrollDy,scrollDx;
    private final Timeline watch=new Timeline(new KeyFrame(Duration.seconds(3),e->checkChanges()));
    private LocalDate week=LocalDate.now().with(java.time.temporal.TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    private PlanningSnapshot snapshot;
    private Task<?> pending;
    private boolean disposed,saving,checking,alt,openFocus;
    private String selected="",notice="",inspectedProposal="",selectedPool="";
    private Runnable inspectorSave;
    private final ArrayDeque<Io> queued=new ArrayDeque<>();
    private LocalDate requestedWeek;
    private Map<String,String> notes=Map.of();
    private List<TaskItem> tasks=List.of();
    private CourseData courses=CourseData.empty();
    private record Loaded(PlanningSnapshot snapshot,Map<String,String> notes,List<TaskItem> tasks,CourseData courses,List<PlanProposal> proposals) {}
    public WeeklyAgendaView(Path root, GoogleCalendarService google, Consumer<LocalDate> openDay, Consumer<String> navigate) {
        this(root,google,openDay,navigate,new FocusTimerService(root.resolve("data/focus.properties")));
    }
    WeeklyAgendaView(Path root,GoogleCalendarService google,Consumer<LocalDate> openDay,Consumer<String> navigate,FocusTimerService focus) {
        rootPath=root;this.openDay=openDay;this.navigate=navigate;this.focus=focus;planner=new PlannerService(root);
    }
    public VBox build() {
        root.setId("weekly-agenda");root.setMinWidth(0);status.setId("weekly-agenda-status");status.setWrapText(true);
        Button previous=button("Önceki hafta","week-previous",()->changeWeek(week.minusWeeks(1))),next=button("Sonraki hafta","week-next",()->changeWeek(week.plusWeeks(1))),
                current=button("Bu hafta","week-current",()->changeWeek(LocalDate.now())),refresh=button("Yenile","week-refresh",this::load),
                undo=button("Geri al","planner-undo",()->run(()->planner.undo())),redo=button("Yinele","planner-redo",()->run(()->planner.redo()));
        proposalsButton.setId("planner-proposals");proposalsButton.setOnAction(e->showProposals());
        Button automatic=button("Otomatik yerleştir","planner-suggest",()->run(()->{
            var suggestion=new AutomaticPlanner().suggest(planner.snapshot(week));
            if(suggestion.proposal()==null){notice=suggestion.unscheduled().isEmpty()?suggestion.message():"Çalışma yerleştirilemedi · "+String.join("; ",suggestion.unscheduled().stream().map(item->item.title()+": "+item.reason()).toList());return null;}
            new ProposalService(rootPath).propose(suggestion.proposal());notice="Öneri hazır · "+suggestion.unscheduled().size()+" iş için yeterli yer bulunamadı";return null;
        }));
        UiTheme.iconOnly(previous,"chevron-left");UiTheme.iconOnly(next,"chevron-right");
        UiTheme.iconOnly(refresh,"refresh-cw");UiTheme.iconOnly(undo,"corner-up-left");UiTheme.iconOnly(redo,"corner-up-right");
        automatic.getStyleClass().add("planner-primary");title.getStyleClass().add("planner-week-title");
        HBox navigation=new HBox(6,previous,current,next,title);navigation.setAlignment(Pos.CENTER_LEFT);
        detailsToggle.setId("planner-details-toggle");detailsToggle.setAccessibleText("Ayrıntı panelini aç veya kapat");
        detailsToggle.selectedProperty().addListener((o,a,b)->setInspectorVisible(b));
        HBox tools=new HBox(6,refresh,undo,redo,detailsToggle,proposalsButton,automatic);tools.setAlignment(Pos.CENTER_RIGHT);
        BorderPane toolbar=new BorderPane();toolbar.setLeft(navigation);toolbar.setRight(tools);toolbar.getStyleClass().add("planner-toolbar");
        toolbar.widthProperty().addListener((o,a,b)->{
            if(b.doubleValue()<1000&&toolbar.getBottom()==null){toolbar.setRight(null);toolbar.setBottom(tools);tools.setAlignment(Pos.CENTER_LEFT);BorderPane.setMargin(tools,new Insets(8,0,0,0));}
            else if(b.doubleValue()>=1000&&toolbar.getRight()==null){toolbar.setBottom(null);toolbar.setRight(tools);tools.setAlignment(Pos.CENTER_RIGHT);BorderPane.setMargin(tools,Insets.EMPTY);}
        });
        statistics.getStyleClass().add("planner-week-summary");statistics.setWrapText(true);statistics.setMaxWidth(Double.MAX_VALUE);
        root.getChildren().addAll(toolbar,statistics);
        pool.setId("planner-pool");pool.setMinWidth(0);pool.setMaxWidth(Double.MAX_VALUE);
        search.setPromptText("İş, ders, kaynak, öncelik ara");search.setId("planner-search");search.textProperty().addListener((o,a,b)->renderPool());
        TextField quick=new TextField();quick.setId("planner-quick-add");quick.setPromptText("Fizik raporu cuma 17:00 2s");
        quick.setOnAction(e->{if(!quick.getText().isBlank()){var value=QuickTaskParser.parse(quick.getText(),LocalDate.now());quick.clear();run(()->planner.execute(new CreateTask(value)));}});
        Label poolTitle=new Label("Plan havuzu");poolTitle.getStyleClass().add("silver-section-title");
        Label historyHint=new Label("Gecikmiş işler: yalnızca son 7 gün");historyHint.getStyleClass().add("soluk");historyHint.setWrapText(true);
        VBox left=new VBox(10,poolTitle,quick,search,historyHint,scroll(pool));left.setMinWidth(228);left.setPrefWidth(280);left.setMaxWidth(340);left.getStyleClass().add("planner-pool-panel");VBox.setVgrow(left.getChildren().getLast(),Priority.ALWAYS);
        grid.setId("planner-week-grid");grid.setMinWidth(48+7*112);
        ScrollPane canvas=scroll(grid);canvas.setFitToHeight(false);canvas.setPannable(false);canvas.setMinWidth(300);canvas.setId("planner-canvas");installAutoScroll(canvas);
        ScrollPane header=scroll(dayHeaders);header.setId("planner-deadline-header");header.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);header.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        deadlineHeaderScroll=header;
        dayHeaders.minWidthProperty().bind(grid.widthProperty());dayHeaders.prefWidthProperty().bind(grid.widthProperty());dayHeaders.maxWidthProperty().bind(grid.widthProperty());
        header.hvalueProperty().bindBidirectional(canvas.hvalueProperty());
        VBox calendar=new VBox(header,canvas);calendar.setMinWidth(300);calendar.getStyleClass().add("planner-calendar-panel");VBox.setVgrow(canvas,Priority.ALWAYS);
        inspector.setId("planner-inspector");inspector.setMinWidth(0);inspector.getChildren().add(new Label("İncelemek için bir blok seç"));
        inspectorScroll=scroll(inspector);inspectorScroll.setMinWidth(248);inspectorScroll.setPrefWidth(290);inspectorScroll.setMaxWidth(360);
        SplitPane.setResizableWithParent(left,false);SplitPane.setResizableWithParent(inspectorScroll,false);
        layout.getItems().setAll(left,calendar);layout.setId("planner-layout");layout.widthProperty().addListener((o,a,b)->sizePanels());
        VBox.setVgrow(layout,Priority.ALWAYS);root.getChildren().addAll(layout,status);
        root.addEventFilter(KeyEvent.KEY_PRESSED,e->{alt=e.isAltDown();if(e.isControlDown() && e.getCode()==KeyCode.Z && !(e.getTarget() instanceof TextInputControl)) {if(e.isShiftDown())run(()->planner.redo());else run(()->planner.undo());e.consume();}});
        root.addEventFilter(KeyEvent.KEY_RELEASED,e->alt=e.isAltDown());
        watch.setCycleCount(Animation.INDEFINITE);watch.play();load();return root;
    }
    private void setInspectorVisible(boolean visible) {
        if(inspectorScroll==null)return;
        if(visible&&!layout.getItems().contains(inspectorScroll))layout.getItems().add(inspectorScroll);
        else if(!visible&&layout.getItems().contains(inspectorScroll)){flushInspector();layout.getItems().remove(inspectorScroll);}
        if(detailsToggle.isSelected()!=visible)detailsToggle.setSelected(visible);
        sizePanels();
        layout.applyCss();layout.layout();
    }
    private void sizePanels() {
        double width=layout.getWidth();if(width<=0)return;
        double left=Math.max(228,Math.min(320,width*.20)),right=Math.max(248,Math.min(340,width*.22));
        if(layout.getItems().size()==3)layout.setDividerPositions(left/width,1-right/width);
        else layout.setDividerPositions(left/width);
    }
    private void changeWeek(LocalDate date) {
        var monday=date.with(java.time.temporal.TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        if(saving||inspectorSave!=null){requestedWeek=monday;flushInspector();return;}
        week=monday;selected="";load();
    }
    private void load() { if(!disposed&&!saving)run(null); }
    private void run(Io action) {
        if(disposed)return;
        if(saving){if(action!=null)queued.addLast(action);return;}
        if(inspectorSave!=null){if(action!=null)queued.addLast(action);flushInspector();return;}
        debounce.stop();
        if(pending!=null)pending.cancel();
        saving=action!=null;root.setDisable(saving);grid.setDisable(saving);status.setText(saving?"Kaydediliyor…":"Plan yükleniyor…");
        LocalDate date=week;
        Task<Loaded> task=new Task<>(){@Override protected Loaded call()throws Exception{
            if(action!=null)action.run();
            new ProposalService(rootPath).archive();
            return new Loaded(planner.snapshot(date),PlannerService.notes(rootPath).notes(),planner.snapshotTasks(),planner.snapshotCourses(),new ProposalService(rootPath).list("pending"));
        }};pending=task;
        task.setOnSucceeded(e->{if(disposed||pending!=task)return;saving=false;root.setDisable(false);grid.setDisable(false);snapshot=task.getValue().snapshot();notes=task.getValue().notes();tasks=task.getValue().tasks();courses=task.getValue().courses();int previousCount=proposals.size();proposals=task.getValue().proposals();proposalsStamp=stamp(proposals);if(proposals.size()>previousCount)notice="Yeni ajan önerisi var · Öneriler düğmesinden incele";render();if(openFocus){openFocus=false;new FocusModeView(rootPath,focus).show(root.getScene().getWindow());}if(!queued.isEmpty())run(queued.removeFirst());else if(requestedWeek!=null){week=requestedWeek;requestedWeek=null;selected="";load();}});
        task.setOnFailed(e->{if(disposed||pending!=task)return;saving=false;root.setDisable(false);grid.setDisable(false);status.setText("Kaydedilemedi: "+task.getException().getMessage());});
        Thread.ofVirtual().name("weekly-planner").start(task);
    }
    private void checkChanges() {
        if(disposed||saving||checking||snapshot==null||debounce.getStatus()==Animation.Status.RUNNING||pending!=null&&pending.isRunning())return;
        checking=true;String before=snapshot.fingerprint()+":"+proposalsStamp;LocalDate date=week;
        Task<String> check=new Task<>(){@Override protected String call()throws Exception{return PlannerService.fingerprint(rootPath,date)+":"+stamp(new ProposalService(rootPath).list("pending"));}};
        check.setOnSucceeded(e->{checking=false;if(!disposed&&!saving&&!before.equals(check.getValue())){notice="Dış değişiklikler alındı";load();}});
        check.setOnFailed(e->{checking=false;if(!disposed)status.setText("Yenileme hatası: "+check.getException().getMessage());});Thread.ofVirtual().start(check);
    }
    void refreshFromBackground(){load();}
    private void render() {
        title.setText(week.format(DateTimeFormatter.ofPattern("d MMM",TR))+" — "+week.plusDays(6).format(DateTimeFormatter.ofPattern("d MMM yyyy",TR)));
        int planned=snapshot.blocks().stream().mapToInt(b->minute(b.end())-minute(b.start())).sum(), actual=snapshot.blocks().stream().mapToInt(StudySession::studiedMinutes).sum();
        Map<String,Integer> byCourse=new LinkedHashMap<>();for(var block:snapshot.blocks()){String courseName=courses.courses().stream().filter(c->c.id().equals(block.courseId())).map(CourseData.Course::name).findFirst().orElse("Bağımsız");byCourse.merge(courseName,minute(block.end())-minute(block.start()),Integer::sum);}
        String distribution=byCourse.entrySet().stream().limit(3).map(e->e.getKey()+" "+duration(e.getValue())).collect(java.util.stream.Collectors.joining(" · "));
        statistics.setText("Bu hafta planlanan "+duration(planned)+" · gerçekleşen "+duration(actual)+(distribution.isBlank()?"":" · "+distribution));
        proposalsButton.setText("Öneriler · "+proposals.size());renderPool();renderGrid();showSelected();if(!selectedPool.isBlank())snapshot.pool().stream().filter(p->(p.kind()+":"+p.id()).equals(selectedPool)).findFirst().ifPresent(this::inspectPool);if(!inspectedProposal.isBlank()&&proposals.stream().noneMatch(p->p.id().equals(inspectedProposal)))showProposals();
        status.setText(snapshot.errors().isEmpty()?(notice.isBlank()?"Dersler, çalışmalar, görevler, Google ve Classroom teslimleri birlikte gösteriliyor.":notice):String.join(" · ",snapshot.errors()));notice="";
    }
    private void renderPool() {
        pool.getChildren().clear();if(snapshot==null)return;
        String query=search.getText().toLowerCase(TR);
        String[] groups={"Gecikmiş","Bu hafta","Gelecek hafta","Daha sonra","Tarihsiz"};
        for(int group=0;group<groups.length;group++) {
            VBox cards=new VBox(6);
            for(var item:snapshot.pool()) if(recent(item) && group(item)==group && (item.title()+" "+item.kind()+" "+item.priority()+" "+item.courseId()).toLowerCase(TR).contains(query)) {
                Label name=new Label(item.title());name.setWrapText(true);name.setMinWidth(0);name.setMaxWidth(Double.MAX_VALUE);HBox.setHgrow(name,Priority.ALWAYS);
                Label detail=new Label((item.date().isBlank()?"Tarihsiz":item.date()+" "+poolTime(item))+"\n"+(item.estimatedMinutes()==0?"Tahmin yok":duration(item.remainingMinutes())+" kaldı")+" · "+duration(item.plannedMinutes())+" planlı");detail.setWrapText(true);detail.getStyleClass().add("soluk");
                CheckBox done=new CheckBox();done.setAccessibleText(item.title()+" tamamlandı");done.setDisable(!Set.of("task","work","classroom").contains(item.kind()));
                done.setOnAction(e->run(()->{
                    if(item.kind().equals("classroom"))planner.execute(List.of(new ImportClassroomWork(item.sourceCourseId(),item.sourceWorkId()),new CompleteItem("work",item.id(),true)),"user","Classroom işi tamamlandı");
                    else planner.execute(new CompleteItem(item.kind(),item.id(),true));
                    return null;
                }));
                Label source=new Label("● "+item.priority()+" · "+switch(item.kind()){case "task"->"Görev";case "work"->"Ödev / sınav";case "classroom"->"Classroom";default->"Başvuru";});source.getStyleClass().add(item.priority().equals("Yüksek")?"form-error":"soluk");
                ProgressBar progress=new ProgressBar(item.estimatedMinutes()==0?0:Math.min(1,(double)item.studiedMinutes()/item.estimatedMinutes()));progress.setMaxWidth(Double.MAX_VALUE);
                VBox card=new VBox(4,new HBox(6,done,name),source,detail,progress);card.getStyleClass().addAll("planner-pool-card","panel");card.setId("pool-"+item.id());
                card.setAccessibleText(item.title()+" · "+detail.getText());
                if(item.unplannedMinutes()==0)card.setOpacity(.55);
                card.setOnDragDetected(e->{var board=card.startDragAndDrop(TransferMode.COPY);var content=new ClipboardContent();content.putString("pool:"+item.kind()+":"+item.id());board.setContent(content);e.consume();});
                card.setOnMouseClicked(e->{setInspectorVisible(true);inspectPool(item);});cards.getChildren().add(card);
            }
            if(!cards.getChildren().isEmpty()){Label groupName=new Label(groups[group]);groupName.getStyleClass().add("silver-section-title");pool.getChildren().addAll(groupName,cards);}
        }
        if(pool.getChildren().isEmpty())pool.getChildren().add(new Label("Planlanacak iş yok"));
    }
    private LocalDate today(){return LocalDate.now(ZoneId.of(snapshot.timezone()));}
    private boolean recent(PlanningSnapshot.PoolItem item){return item.date().isBlank()||!LocalDate.parse(item.date()).isBefore(today().minusDays(7));}
    private int group(PlanningSnapshot.PoolItem item) {if(item.date().isBlank())return 4;LocalDate d=LocalDate.parse(item.date());return d.isBefore(today())?0:!d.isAfter(week.plusDays(6))?1:!d.isAfter(week.plusDays(13))?2:3;}
    private void inspectPool(PlanningSnapshot.PoolItem item) {
        flushInspector();inspectedProposal="";selected="";selectedPool=item.kind()+":"+item.id();
        inspector.getChildren().setAll(new Label("İş ayrıntıları"),new Label(item.title()));
        if(Set.of("task","work").contains(item.kind())){
            CourseData.Work source = item.kind().equals("work") ? courses.work().stream().filter(w -> w.id().equals(item.id())).findFirst().orElse(null) : null;
            boolean exam = source != null && "Sınav".equals(source.kind());
            TextField name=new TextField(item.title());name.setId("pool-title");
            DatePicker due=new DatePicker(item.date().isBlank()?null:LocalDate.parse(item.date()));due.setId("pool-due-date");
            ComboBox<String> dueTime=times(item.dueTime().isBlank()?"17:00":item.dueTime());dueTime.getItems().addFirst("");dueTime.setValue(item.dueTime());dueTime.setId("pool-due-time");
            ComboBox<String> endTime=times(source == null ? "" : source.endTime());endTime.setId("pool-exam-end");
            ComboBox<String> priority=new ComboBox<>();priority.getItems().setAll("Düşük","Normal","Yüksek");priority.setValue(item.priority());priority.setId("pool-priority");
            Spinner<Integer> estimate=new Spinner<>(0,100000,item.estimatedMinutes(),15);estimate.setId("pool-estimate");
            TextArea note=new TextArea(item.kind().equals("task")?tasks.stream().filter(t->t.getId().equals(item.id())).map(TaskItem::getDescription).findFirst().orElse(""):notes.getOrDefault(item.id(),""));note.setPrefRowCount(3);note.setId("pool-note");
            Runnable update=()->{inspectorSave=()->{
                String dateValue=due.getValue()==null?"":due.getValue().toString(),timeValue=Objects.toString(dueTime.getValue(),""),priorityValue=priority.getValue(),titleValue=name.getText(),noteValue=note.getText();int estimated=estimate.getValue();
                if(item.kind().equals("task"))tasks.stream().filter(t->t.getId().equals(item.id())).findFirst().ifPresent(t->{var value=t.copy();value.setTitle(titleValue);value.setDescription(noteValue);value.setEstimatedMinutes(estimated);value.setDate(dateValue.isBlank()?null:LocalDate.parse(dateValue));value.setDueTime(timeValue.isBlank()?null:LocalTime.parse(timeValue));value.setPriority(priorityValue);run(()->planner.execute(new UpdateTask(value)));});
                else courses.work().stream().filter(w->w.id().equals(item.id())).findFirst().ifPresent(w->{var value=new CourseData.Work(w.id(),w.courseId(),titleValue,w.kind(),dateValue,w.done(),timeValue,estimated,w.loggedMinutes(),priorityValue,Objects.toString(endTime.getValue(),""));run(()->planner.execute(List.of(new UpdateWork(value),new SetNote(item.id(),noteValue)),"user","İş ayrıntıları"));});
            };debounce.setOnFinished(e->flushInspector());debounce.playFromStart();};
            name.textProperty().addListener((o,a,b)->update.run());note.textProperty().addListener((o,a,b)->update.run());due.valueProperty().addListener((o,a,b)->update.run());dueTime.valueProperty().addListener((o,a,b)->update.run());priority.valueProperty().addListener((o,a,b)->update.run());estimate.valueProperty().addListener((o,a,b)->update.run());
            endTime.valueProperty().addListener((o,a,b)->update.run());
            inspector.getChildren().addAll(name,new Label(exam ? "Sınav tarihi / başlangıcı" : "Son tarih / saat"),due,dueTime);
            if(exam)inspector.getChildren().addAll(new Label("Sınav bitişi"),endTime);
            inspector.getChildren().addAll(new Label("Öncelik"),priority,new Label("Tahmini dakika"),estimate,new Label("Not"),note);
        }else inspector.getChildren().add(new Label("Son tarih: "+(item.date().isBlank()?"Tarihsiz":item.date()+" "+item.dueTime())));
        Button focusNow=button("Şimdi odaklan ▶","pool-focus",()->run(()->{
            if(focus.running())throw new IllegalStateException("Önce mevcut odağı bitir veya duraklat");
            var now=LocalDateTime.now(ZoneId.of(snapshot.timezone()));int startMinute=(now.getHour()*60+now.getMinute()+14)/15*15;
            int minutes=Math.min(120,Math.max(15,item.unplannedMinutes()>0?item.unplannedMinutes():60));
            var oldIds=new StudyStorageService(rootPath.resolve("data/study/sessions.json")).load().stream().map(StudySession::id).collect(java.util.stream.Collectors.toSet());
            var result=planner.execute(new ScheduleBlock(item.kind(),item.id(),now.toLocalDate().toString(),PlanningPreferences.time(Math.min(1424,startMinute)),minutes,item.title(),ConflictPolicy.NEAREST));
            var block=result.blocks().stream().filter(b->!oldIds.contains(b.id())).findFirst().orElseThrow();focus.startSession(block.id(),snapshot.preferences().pomodoro()?snapshot.preferences().workMinutes():minutes);openFocus=true;return result;
        }));
        Button plan=button("İlk boş aralığa yerleştir","pool-schedule",()->{var slot=snapshot.freeSlots().stream().filter(v->v.minutes()>=15).findFirst().orElse(null);if(slot!=null)schedule(item,slot.date(),slot.start(),false);else status.setText("Uygun boş aralık yok");});inspector.getChildren().addAll(focusNow,plan);
    }
    private void renderGrid() {
        grid.getChildren().clear();dayHeaders.getChildren().clear();
        int maxDeadlines=0;
        for(int offset=0;offset<7;offset++){String iso=week.plusDays(offset).toString();maxDeadlines=Math.max(maxDeadlines,(int)snapshot.pool().stream().filter(item->item.date().equals(iso)).count());}
        double deadlineHeight=Math.max(32,Math.min(2,maxDeadlines)*52+(maxDeadlines>2?32:4));
        double headerHeight=34+5+deadlineHeight+4;
        deadlineHeaderScroll.setMinHeight(headerHeight);deadlineHeaderScroll.setPrefHeight(headerHeight);deadlineHeaderScroll.setMaxHeight(headerHeight);
        VBox corner=new VBox(5,new Label(""),new Label("Son\nteslimler"));corner.getStyleClass().add("planner-deadline-corner");corner.setMinWidth(48);corner.setPrefWidth(48);corner.setMaxWidth(48);dayHeaders.getChildren().add(corner);
        Pane hours=new Pane();hours.setMinWidth(48);hours.setPrefWidth(48);hours.setMaxWidth(48);hours.setMinHeight((LAST-FIRST)*SCALE+20);
        for(int h=8;h<=24;h++){Label label=new Label(String.format(Locale.ROOT,"%02d:00",h));label.getStyleClass().add("silver-hour");place(hours,label,0,(h*60-FIRST)*SCALE,45,20);}grid.getChildren().add(hours);
        for(int offset=0;offset<7;offset++){
            LocalDate date=week.plusDays(offset);String iso=date.toString();
            Button day=button(date.format(DateTimeFormatter.ofPattern("EEE d MMM",TR)),"planner-date-"+iso,()->openDay.accept(date));day.setMaxWidth(Double.MAX_VALUE);
            day.getStyleClass().add("planner-day-heading");
            Pane pane=new Pane(){@Override protected void layoutChildren(){for(Node node:getChildren())if(node instanceof Region region)region.resize(Math.max(40,getWidth()-region.getLayoutX()-4),region.getHeight());}};pane.setId("planner-day-"+iso);pane.setPrefSize(145,(LAST-FIRST)*SCALE+20);pane.setMinSize(112,(LAST-FIRST)*SCALE+20);HBox.setHgrow(pane,Priority.ALWAYS);
            VBox deadlines=deadlineLane(iso,deadlineHeight);
            VBox columnHeader=new VBox(5,day,deadlines);columnHeader.setMinWidth(0);columnHeader.prefWidthProperty().bind(pane.widthProperty());columnHeader.maxWidthProperty().bind(pane.widthProperty());dayHeaders.getChildren().add(columnHeader);
            if(date.equals(today()))day.getStyleClass().add("planner-today");
            for(int h=8;h<24;h++){Region line=new Region();line.getStyleClass().add("silver-time-line");line.setMouseTransparent(true);line.prefWidthProperty().bind(pane.widthProperty());place(pane,line,0,(h*60-FIRST)*SCALE,145,1);}
            for(var fixed:snapshot.fixedBlocks())if(fixed.date().equals(iso))drawFixed(pane,fixed);
            for(var block:snapshot.blocks())if(block.date().equals(iso))drawBlock(pane,block);
            LocalTime nowTime=LocalTime.now(ZoneId.of(snapshot.timezone()));
            if(date.equals(today())&&nowTime.isAfter(LocalTime.of(8,0))){Region now=new Region();now.getStyleClass().add("silver-now-line");now.setMouseTransparent(true);place(pane,now,0,y(nowTime.withSecond(0).withNano(0).toString()),145,2);}
            for(var proposal:proposals)for(int i=0;i<proposal.commands().size();i++){
                var action=proposal.commands().get(i);var preview=action;
                if(action.type().equals("move")||action.type().equals("resize")){var original=snapshot.blocks().stream().filter(b->b.id().equals(action.id())).findFirst().orElse(null);if(original!=null)preview=new PlanProposal.Action("schedule",action.id(),"","",original.title(),action.type().equals("move")?action.date():original.date(),action.type().equals("move")?action.start():original.start(),action.type().equals("resize")?action.minutes():minute(original.end())-minute(original.start()),action.reason(),null);}
                if(preview.type().equals("schedule")&&iso.equals(preview.date()))drawGhost(pane,proposal,i,preview);
            }
            installDayGestures(pane,date);grid.getChildren().add(pane);
        }
    }
    private VBox deadlineLane(String date,double height) {
        var items=snapshot.pool().stream().filter(item->item.date().equals(date))
                .sorted(Comparator.comparing((PlanningSnapshot.PoolItem item)->item.dueTime().isBlank()?"23:59":item.dueTime()).thenComparing(PlanningSnapshot.PoolItem::title)).toList();
        VBox lane=new VBox(4);lane.setId("planner-deadlines-"+date);lane.getStyleClass().add("planner-deadline-lane");lane.setMinHeight(height);lane.setPrefHeight(height);lane.setMaxHeight(height);
        for(var item:items.stream().limit(2).toList())lane.getChildren().add(deadlineButton(item));
        if(items.size()>2){Button more=button("+"+(items.size()-2)+" teslim daha","planner-more-deadlines-"+date,()->{
            flushInspector();setInspectorVisible(true);selected="";selectedPool="";inspectedProposal="";
            Label heading=new Label(LocalDate.parse(date).format(DateTimeFormatter.ofPattern("d MMMM · 'Son teslimler'",TR)));heading.setWrapText(true);
            inspector.getChildren().setAll(heading);items.forEach(item->{Button entry=deadlineButton(item);entry.setId("planner-inspector-deadline-"+item.id());inspector.getChildren().add(entry);});
        });more.setMinHeight(24);more.setPrefHeight(24);more.setMaxHeight(24);more.getStyleClass().add("planner-deadline-more");lane.getChildren().add(more);}
        if(items.isEmpty()){Label empty=new Label("Teslim yok");empty.getStyleClass().add("planner-no-deadline");lane.getChildren().add(empty);}
        return lane;
    }
    private Button deadlineButton(PlanningSnapshot.PoolItem item) {
        Label name=new Label(item.title());name.setMinWidth(0);name.setMaxWidth(Double.MAX_VALUE);name.getStyleClass().add("planner-deadline-name");
        String time=item.dueTime().isBlank()?"Gün içinde":poolTime(item);
        String kind = courses.work().stream().anyMatch(w -> item.kind().equals("work") && w.id().equals(item.id()) && "Sınav".equals(w.kind())) ? "Sınav" : "Son teslim";
        Label due=new Label(time+" · "+kind);due.getStyleClass().add("planner-deadline-time");
        VBox content=new VBox(2,name,due);content.setMinWidth(0);content.setMaxWidth(Double.MAX_VALUE);
        Button result=button("","planner-deadline-"+item.id(),()->{setInspectorVisible(true);inspectPool(item);});result.setGraphic(content);result.setMaxWidth(Double.MAX_VALUE);result.setMinWidth(0);result.setPrefHeight(48);result.setMinHeight(48);result.setMaxHeight(48);result.getStyleClass().add("planner-deadline-card");
        result.setAccessibleText(item.title()+" · "+item.date()+" · "+kind+" "+time);result.setTooltip(new Tooltip(item.title()+"\n"+kind+": "+item.date()+" "+time));return result;
    }
    private String poolTime(PlanningSnapshot.PoolItem item) {
        return item.kind().equals("work") ? courses.work().stream().filter(w -> w.id().equals(item.id())).map(CourseData.Work::timeLabel).findFirst().orElse(item.dueTime()) : item.dueTime();
    }
    private void drawFixed(Pane pane,PlanningSnapshot.Block fixed){
        Button b=new Button((fixed.kind().equals("Sınav")?"Sınav · ":"")+fixed.title()+"\n"+fixed.start()+"–"+fixed.end());b.setWrapText(true);b.getStyleClass().addAll("silver-event","lesson-event","planner-fixed");
        if(fixed.kind().equals("Sınav"))b.getStyleClass().add("exam-event");
        b.setAccessibleText("Sabit "+fixed.kind()+" · "+fixed.title());b.setId("fixed-"+fixed.id());
        b.setOnAction(e->{setInspectorVisible(true);selected="";selectedPool="";inspectedProposal="";inspector.getChildren().setAll(new Label(fixed.title()),new Label(fixed.kind()+" · Sabit blok"),button("Kaynakta aç","planner-open-source",()->navigate.accept(fixed.kind().equals("Google")?"calendar":"courses")));});
        place(pane,b,2,y(fixed.start()),141,Math.max(22,(Math.min(LAST,minute(fixed.end()))-Math.max(FIRST,minute(fixed.start())))*SCALE-3));
    }
    private void drawBlock(Pane pane,StudySession b){
        Label label=new Label(b.title()+(b.done()?" ✓":""));label.setMouseTransparent(true);label.getStyleClass().add("silver-event-name");
        Label timeLabel=new Label(b.start()+"–"+b.end());timeLabel.setMouseTransparent(true);timeLabel.getStyleClass().add("silver-event-detail");
        Region handle=new Region();handle.setPrefHeight(4);handle.getStyleClass().add("planner-resize");
        VBox box=new VBox(label,timeLabel,new Region(),handle);VBox.setVgrow(box.getChildren().get(2),Priority.ALWAYS);box.setFocusTraversable(true);box.setId("planner-block-"+b.id());box.setUserData(b);box.setAccessibleText(b.title()+" · "+b.date()+" "+b.start()+"–"+b.end());box.getStyleClass().addAll("silver-event","study-event","planner-block");
        if(b.id().equals(selected))box.getStyleClass().add("planner-selected");if(b.done())box.getStyleClass().add("completed-event");
        boolean conflict=snapshot.fixedBlocks().stream().anyMatch(f->f.date().equals(b.date())&&minute(f.start())<minute(b.end())&&minute(f.end())>minute(b.start())) || snapshot.blocks().stream().anyMatch(o->!o.id().equals(b.id())&&o.date().equals(b.date())&&!o.done()&&minute(o.start())<minute(b.end())&&minute(o.end())>minute(b.start()));
        if(conflict)box.getStyleClass().add("conflicting-event");
        Tooltip.install(box,new Tooltip(b.title()+"\nSürükle: taşı · Alt: çakışmaya izin ver\nAlt+ok: taşı · Shift+Alt+ok: süre"));
        box.setOnMouseClicked(e->{flushInspector();setInspectorVisible(true);inspectedProposal="";selectedPool="";selected=b.id();showSelected();box.requestFocus();if(e.getClickCount()==2)inlineTitle(pane,b);e.consume();});
        box.setOnDragDetected(e->{if(e.getY()>box.getHeight()-10)return;var board=box.startDragAndDrop(TransferMode.MOVE);var content=new ClipboardContent();content.putString("study:"+b.id());board.setContent(content);e.consume();});
        double[] resize={-1};handle.setOnMousePressed(e->{resize[0]=e.getSceneY();e.consume();});
        handle.setOnMouseDragged(e->{if(resize[0]>=0){double delta=e.getSceneY()-resize[0];box.setPrefHeight(Math.max(15*SCALE,(minute(b.end())-minute(b.start()))*SCALE+delta));box.resize(box.getWidth(),box.getPrefHeight());}e.consume();});
        handle.setOnMouseReleased(e->{if(resize[0]>=0){int length=Math.max(15,snap(minute(b.end())-minute(b.start())+(int)((e.getSceneY()-resize[0])/SCALE)));resize[0]=-1;resize(b.id(),length,e.isAltDown());}e.consume();});
        box.setOnKeyPressed(e->{if(e.getTarget() instanceof TextInputControl)return;if(e.getCode()==KeyCode.DELETE){run(()->planner.execute(new DeleteBlock(b.id())));e.consume();}
            else if(e.getCode()==KeyCode.ENTER){setInspectorVisible(true);selected=b.id();showSelected();e.consume();}
            else if(e.isAltDown()&&Set.of(KeyCode.UP,KeyCode.DOWN,KeyCode.LEFT,KeyCode.RIGHT).contains(e.getCode())){
                if(e.isShiftDown()&&(e.getCode()==KeyCode.UP||e.getCode()==KeyCode.DOWN))resize(b.id(),minute(b.end())-minute(b.start())+(e.getCode()==KeyCode.UP?-15:15),false);
                else{LocalDate date=LocalDate.parse(b.date());int time=minute(b.start());if(e.getCode()==KeyCode.LEFT)date=date.minusDays(1);if(e.getCode()==KeyCode.RIGHT)date=date.plusDays(1);if(e.getCode()==KeyCode.UP)time-=15;if(e.getCode()==KeyCode.DOWN)time+=15;if(time>=0&&time<1440)move(b.id(),date.toString(),PlanningPreferences.time(time),false);}e.consume();
            }else if(Set.of(KeyCode.UP,KeyCode.DOWN,KeyCode.LEFT,KeyCode.RIGHT).contains(e.getCode())){int i=snapshot.blocks().indexOf(b)+(e.getCode()==KeyCode.UP||e.getCode()==KeyCode.LEFT?-1:1);if(i>=0&&i<snapshot.blocks().size()){Node next=root.lookup("#planner-block-"+snapshot.blocks().get(i).id());if(next!=null)next.requestFocus();}e.consume();}});
        ContextMenu menu=new ContextMenu();menu.getItems().addAll(menu("Odakla",()->startFocus(b)),menu("Tamamlandı",()->run(()->planner.execute(new CompleteBlock(b.id(),b.actualMinutes(),true)))),menu("Çoğalt",()->createRange(LocalDate.parse(b.date()),minute(b.end()),minute(b.end())+minute(b.end())-minute(b.start()),b.title())),menu("Sonraki boş aralık",()->{var slot=snapshot.freeSlots().stream().filter(s->s.minutes()>=minute(b.end())-minute(b.start())).findFirst().orElse(null);if(slot!=null)move(b.id(),slot.date(),slot.start(),false);}),menu("Sil",()->run(()->planner.execute(new DeleteBlock(b.id())))));
        box.setOnContextMenuRequested(e->menu.show(box,e.getScreenX(),e.getScreenY()));
        place(pane,box,4,y(b.start()),137,Math.max(22,(minute(b.end())-minute(b.start()))*SCALE-3));
    }
    private void installDayGestures(Pane pane,LocalDate date){
        Region preview=new Region();preview.setMouseTransparent(true);
        pane.setOnDragOver(e->{if(e.getDragboard().hasString()&&(e.getDragboard().getString().startsWith("study:")||e.getDragboard().getString().startsWith("pool:")||e.getDragboard().getString().startsWith("proposal:"))){e.acceptTransferModes(TransferMode.MOVE,TransferMode.COPY);if(!pane.getStyleClass().contains("planner-drop-target"))pane.getStyleClass().add("planner-drop-target");
            String payload=e.getDragboard().getString();int length=60;String moving=payload.startsWith("study:")?payload.substring(6):"";
            var block=snapshot.blocks().stream().filter(b->b.id().equals(moving)).findFirst().orElse(null);if(block!=null)length=minute(block.end())-minute(block.start());
            if(payload.startsWith("pool:")){String[] parts=payload.split(":",3);var item=snapshot.pool().stream().filter(p->p.id().equals(parts[2])).findFirst().orElse(null);if(item!=null)length=Math.max(15,Math.min(120,item.unplannedMinutes()>0?item.unplannedMinutes():item.remainingMinutes()>0?item.remainingMinutes():60));}
            int from=Math.max(0,snap(FIRST+(int)(e.getY()/SCALE))),to=from+length;
            boolean collision=snapshot.fixedBlocks().stream().anyMatch(b->b.date().equals(date.toString())&&from<minute(b.end())&&to>minute(b.start()))||snapshot.blocks().stream().anyMatch(b->!b.id().equals(moving)&&b.date().equals(date.toString())&&!b.done()&&from<minute(b.end())&&to>minute(b.start()));
            preview.getStyleClass().setAll(collision?"planner-conflict-preview":"planner-selection");if(!pane.getChildren().contains(preview))pane.getChildren().add(preview);preview.setManaged(false);preview.resizeRelocate(3,(from-FIRST)*SCALE,139,Math.max(15,length*SCALE));e.consume();}});
        pane.setOnDragExited(e->{pane.getStyleClass().removeAll("planner-drop-target");pane.getChildren().remove(preview);});
        pane.setOnDragDropped(e->{String data=e.getDragboard().getString();int time=Math.min(1425,Math.max(0,snap(FIRST+(int)(e.getY()/SCALE))));
            if(data!=null&&data.startsWith("study:"))move(data.substring(6),date.toString(),PlanningPreferences.time(time),alt);
            else if(data!=null&&data.startsWith("pool:")){String[] parts=data.split(":",3);snapshot.pool().stream().filter(p->p.kind().equals(parts[1])&&p.id().equals(parts[2])).findFirst().ifPresent(item->schedule(item,date.toString(),PlanningPreferences.time(time),alt));}
            else if(data!=null&&data.startsWith("proposal:")){String[] parts=data.split(":",3);var p=proposals.stream().filter(v->v.id().equals(parts[1])).findFirst().orElseThrow();int index=Integer.parseInt(parts[2]);var a=p.commands().get(index);var changed=new PlanProposal.Action(a.type(),a.id(),a.kind(),a.itemId(),a.title(),date.toString(),PlanningPreferences.time(time),a.minutes(),a.reason(),a.details());run(()->new ProposalService(rootPath).decide(p.id(),Map.of(index,changed),"Hayalet blok taşınarak kabul edildi"));}
            e.setDropCompleted(data!=null);e.consume();pane.getStyleClass().removeAll("planner-drop-target");pane.getChildren().remove(preview);});
        double[] origin={-1};Region selection=new Region();selection.getStyleClass().add("planner-selection");selection.setMouseTransparent(true);
        pane.setOnMousePressed(e->{if(e.getTarget()==pane&&e.getButton()==MouseButton.PRIMARY){origin[0]=e.getY();place(pane,selection,3,origin[0],139,15*SCALE);e.consume();}});
        pane.setOnMouseDragged(e->{if(origin[0]>=0){double from=Math.min(origin[0],e.getY());selection.resizeRelocate(3,from,139,Math.max(15*SCALE,Math.abs(e.getY()-origin[0])));e.consume();}});
        pane.setOnMouseReleased(e->{if(origin[0]>=0){int start=snap(FIRST+(int)(Math.min(origin[0],e.getY())/SCALE)),end=snap(FIRST+(int)(Math.max(origin[0],e.getY())/SCALE));origin[0]=-1;pane.getChildren().remove(selection);inlineCreate(pane,date,start,Math.max(start+15,end));e.consume();}});
    }
    private void inlineCreate(Pane pane,LocalDate date,int start,int end){TextField name=new TextField();name.setId("planner-inline-title");name.setPromptText("Başlık → Enter");place(pane,name,3,(start-FIRST)*SCALE,139,32);name.requestFocus();name.setOnKeyPressed(e->{if(e.getCode()==KeyCode.ESCAPE)pane.getChildren().remove(name);});name.setOnAction(e->{if(!name.getText().isBlank())createRange(date,start,Math.min(1439,end),name.getText().trim());});}
    private void inlineTitle(Pane pane,StudySession b){TextField name=new TextField(b.title());name.setId("planner-inline-edit");place(pane,name,4,y(b.start()),137,32);name.requestFocus();name.selectAll();name.setOnKeyPressed(e->{if(e.getCode()==KeyCode.ESCAPE)pane.getChildren().remove(name);});name.setOnAction(e->{if(!name.getText().isBlank()){var updated=new StudySession(b.id(),name.getText().trim(),b.date(),b.start(),b.end(),b.courseId(),b.done(),b.taskId(),b.workId(),b.actualMinutes());run(()->planner.execute(new SaveBlock(updated,false,ConflictPolicy.NEAREST)));}});}
    void createRange(LocalDate date,int start,int end,String title){run(()->record(planner.execute(new ScheduleBlock("","",date.toString(),PlanningPreferences.time(start),end-start,title,ConflictPolicy.NEAREST))));}
    void move(String id,String date,String start,boolean allow){setInspectorVisible(true);selected=id;run(()->record(planner.execute(new MoveBlock(id,date,start,allow?ConflictPolicy.ALLOW:ConflictPolicy.NEAREST))));}
    void resize(String id,int minutes,boolean allow){setInspectorVisible(true);selected=id;run(()->record(planner.execute(new ResizeBlock(id,minutes,allow?ConflictPolicy.ALLOW:ConflictPolicy.NEAREST))));}
    private void schedule(PlanningSnapshot.PoolItem item,String date,String start,boolean allow){int duration=Math.min(120,item.unplannedMinutes()>0?item.unplannedMinutes():item.remainingMinutes()>0?item.remainingMinutes():60);duration=Math.max(15,duration);int minutes=duration;run(()->record(planner.execute(new ScheduleBlock(item.kind(),item.id(),date,start,minutes,item.title(),allow?ConflictPolicy.ALLOW:ConflictPolicy.NEAREST))));}
    private PlannerService.Result record(PlannerService.Result result){notice=result.notices().isEmpty()?"Plan kaydedildi · Geri al":String.join(" · ",result.notices());return result;}
    private void showSelected(){if(snapshot==null)return;snapshot.blocks().stream().filter(b->b.id().equals(selected)).findFirst().ifPresent(this::inspectBlock);}
    private void inspectBlock(StudySession b){
        TextField name=new TextField(b.title());name.setId("planner-title");DatePicker date=new DatePicker(LocalDate.parse(b.date()));date.setId("planner-date");
        ComboBox<String> start=times(b.start()),end=times(b.end());start.setId("planner-start");end.setId("planner-end");
        TextArea note=new TextArea(notes.getOrDefault(b.id(),""));note.setPrefRowCount(3);note.setWrapText(true);note.setId("planner-note");
        Spinner<Integer> actual=new Spinner<>(0,1440,b.actualMinutes(),5);actual.setId("planner-actual");
        CheckBox done=new CheckBox("Tamamlandı");done.setSelected(b.done());done.setId("planner-done");
        ComboBox<PlanningSnapshot.PoolItem> target=new ComboBox<>();target.getItems().add(null);target.getItems().addAll(snapshot.pool().stream().filter(p->Set.of("task","work").contains(p.kind())).toList());target.setConverter(new javafx.util.StringConverter<>(){public String toString(PlanningSnapshot.PoolItem p){return p==null?"Bağsız çalışma":p.title();}public PlanningSnapshot.PoolItem fromString(String s){return null;}});target.setMaxWidth(Double.MAX_VALUE);target.setId("planner-target");
        if(!b.taskId().isBlank()&&target.getItems().stream().filter(Objects::nonNull).noneMatch(p->p.id().equals(b.taskId())))tasks.stream().filter(t->t.getId().equals(b.taskId())).findFirst().ifPresent(t->target.getItems().add(new PlanningSnapshot.PoolItem("task",t.getId(),t.getTitle(),t.getDate()==null?"":t.getDate().toString(),Objects.toString(t.getDueTime(),""),"",t.getPriority(),t.getEstimatedMinutes(),t.getLoggedMinutes(),0,0,"","")));
        if(!b.workId().isBlank()&&target.getItems().stream().filter(Objects::nonNull).noneMatch(p->p.id().equals(b.workId())))courses.work().stream().filter(w->w.id().equals(b.workId())).findFirst().ifPresent(w->target.getItems().add(new PlanningSnapshot.PoolItem("work",w.id(),w.title(),w.date(),w.dueTime(),w.courseId(),w.priority(),w.estimatedMinutes(),w.loggedMinutes(),0,0,"","")));
        target.getItems().stream().filter(Objects::nonNull).filter(p->p.id().equals(b.taskId())||p.id().equals(b.workId())).findFirst().ifPresent(target::setValue);
        Label error=new Label();error.getStyleClass().add("form-error");error.setWrapText(true);error.setId("planner-field-error");
        ComboBox<CourseData.Course> course=new ComboBox<>();course.getItems().add(null);course.getItems().addAll(courses.courses());course.setPromptText("Ders (isteğe bağlı)");course.setMaxWidth(Double.MAX_VALUE);courses.courses().stream().filter(c->c.id().equals(b.courseId())).findFirst().ifPresent(course::setValue);course.setId("planner-course");
        Runnable save=()->{inspectorSave=()->{try{
            if(name.getText().isBlank()||date.getValue()==null||minute(end.getValue())<=minute(start.getValue()))throw new IllegalArgumentException("Başlık, tarih ve bitiş saatini kontrol et");
            var t=target.getValue();String task=t!=null&&t.kind().equals("task")?t.id():"",work=t!=null&&t.kind().equals("work")?t.id():"",courseId=t==null?(course.getValue()==null?"":course.getValue().id()):t.courseId();
            var value=new StudySession(b.id(),name.getText().trim(),date.getValue().toString(),start.getValue(),end.getValue(),courseId,done.isSelected(),task,work,actual.getValue());String text=note.getText();run(()->record(planner.execute(List.of(new SaveBlock(value,false,ConflictPolicy.NEAREST),new SetNote(b.id(),text)),"user","İnceleme paneli")));
        }catch(Exception failure){error.setText(failure.getMessage());}};debounce.setOnFinished(e->flushInspector());debounce.playFromStart();};
        name.textProperty().addListener((o,a,v)->save.run());date.valueProperty().addListener((o,a,v)->save.run());start.valueProperty().addListener((o,a,v)->save.run());end.valueProperty().addListener((o,a,v)->save.run());target.valueProperty().addListener((o,a,v)->save.run());note.textProperty().addListener((o,a,v)->save.run());actual.valueProperty().addListener((o,a,v)->save.run());done.selectedProperty().addListener((o,a,v)->save.run());
        course.valueProperty().addListener((o,a,v)->save.run());
        VBox advanced=new VBox(8,new Label("Ders"),course,new Label("Gerçekleşen dakika"),actual,done);TitledPane details=new TitledPane("Ayrıntılar",advanced);details.setExpanded(false);
        inspector.getChildren().setAll(new Label("İnceleme"),name,date,new Label("Başlangıç / bitiş"),new HBox(5,start,end),new Label("Bağlı iş"),target,new Label("Not"),note,details,error,button("Odakla ▶","planner-focus",()->startFocus(b)),button("Sil","planner-delete",()->run(()->planner.execute(new DeleteBlock(b.id())))));
        if(!b.taskId().isBlank())tasks.stream().filter(t->t.getId().equals(b.taskId())).findFirst().ifPresent(t->{for(var sub:t.getSubtasks()){CheckBox check=new CheckBox(sub.title());check.setSelected(sub.done());check.setOnAction(e->{var changed=t.copy();changed.setSubtasks(t.getSubtasks().stream().map(s->s.id().equals(sub.id())?new TaskItem.Subtask(s.id(),s.title(),check.isSelected()):s).toList());run(()->planner.execute(new UpdateTask(changed)));});inspector.getChildren().add(check);}});
    }
    private void flushInspector(){if(inspectorSave==null)return;debounce.stop();Runnable save=inspectorSave;inspectorSave=null;save.run();}
    private static String stamp(List<PlanProposal> values){return values.stream().map(p->p.id()+":"+p.status()).collect(java.util.stream.Collectors.joining("|"));}
    private void drawGhost(Pane pane,PlanProposal proposal,int index,PlanProposal.Action action){
        if(action.start()==null||action.minutes()==null)return;
        Button ghost=new Button(action.title()+"\n◇ "+proposal.agent());ghost.setWrapText(true);ghost.setId("proposal-ghost-"+proposal.id()+"-"+index);ghost.getStyleClass().addAll("silver-event","study-event","planner-ghost");ghost.setTooltip(new Tooltip(action.reason()));ghost.setOnAction(e->inspectProposal(proposal));
        if(!proposal.commands().get(index).type().equals("resize"))ghost.setOnDragDetected(e->{var board=ghost.startDragAndDrop(TransferMode.MOVE);var data=new ClipboardContent();data.putString("proposal:"+proposal.id()+":"+index);board.setContent(data);e.consume();});
        place(pane,ghost,8,y(action.start()),132,Math.max(24,action.minutes()*SCALE-4));
    }
    private void showProposals(){flushInspector();setInspectorVisible(true);inspectedProposal="";selected="";selectedPool="";inspector.getChildren().setAll(new Label("Öneri kutusu"));for(var p:proposals)inspector.getChildren().add(button(p.agent()+" · "+p.commands().size()+" işlem","proposal-open-"+p.id(),()->inspectProposal(p)));if(proposals.isEmpty())inspector.getChildren().add(new Label("Bekleyen öneri yok"));}
    private void inspectProposal(PlanProposal proposal){
        flushInspector();setInspectorVisible(true);inspectedProposal=proposal.id();selected="";selectedPool="";inspector.getChildren().setAll(new Label(proposal.agent()),new Label(proposal.note()));
        Label warning=new Label(proposal.fingerprint().equals(snapshot.fingerprint())?"":"Snapshot değişti; komutlar yeniden doğrulanacak");warning.setWrapText(true);inspector.getChildren().add(warning);
        TextArea feedback=new TextArea();feedback.setId("proposal-feedback");feedback.setPrefRowCount(2);feedback.setPromptText("Ajana not (isteğe bağlı)");
        Map<Integer,CheckBox> choices=new LinkedHashMap<>();
        for(int i=0;i<proposal.commands().size();i++){var a=proposal.commands().get(i);CheckBox check=new CheckBox(a.type()+" · "+Objects.toString(a.title(),Objects.toString(a.id(),""))+"\n"+Objects.toString(a.date(),"")+" "+Objects.toString(a.start(),""));check.setSelected(true);check.setWrapText(true);check.setId("proposal-choice-"+i);choices.put(i,check);Label reason=new Label(a.reason());reason.setWrapText(true);reason.getStyleClass().add("soluk");inspector.getChildren().addAll(check,reason);}
        Button accept=button("Seçilenleri uygula","proposal-accept",()->{Map<Integer,PlanProposal.Action> selected=new LinkedHashMap<>();choices.forEach((i,c)->{if(c.isSelected()&&!c.isDisabled())selected.put(i,proposal.commands().get(i));});String note=feedback.getText();run(()->new ProposalService(rootPath).decide(proposal.id(),selected,note));});
        Button reject=button("Reddet","proposal-reject",()->{String note=feedback.getText();run(()->new ProposalService(rootPath).decide(proposal.id(),Map.of(),note));});accept.setDisable(true);inspector.getChildren().addAll(feedback,accept,reject);
        Task<ProposalService.Validation> validation=new Task<>(){@Override protected ProposalService.Validation call()throws Exception{return new ProposalService(rootPath).validate(proposal);}};
        validation.setOnSucceeded(e->{if(disposed)return;accept.setDisable(false);for(var issue:validation.getValue().issues()){var check=choices.get(issue.index());if(check!=null){check.setSelected(false);check.setDisable(true);check.setTooltip(new Tooltip(issue.message()));}else accept.setDisable(true);}warning.setText((validation.getValue().stale()?"Snapshot değişti · ":"")+(validation.getValue().valid()?"Tüm komutlar uygun":validation.getValue().issues().size()+" işlem uygulanamaz"));});validation.setOnFailed(e->warning.setText(validation.getException().getMessage()));Thread.ofVirtual().start(validation);
    }
    private void startFocus(StudySession block){run(()->{var preferences=PlanningPreferences.load(rootPath);focus.startSession(block.id(),preferences.pomodoro()?preferences.workMinutes():Math.min(180,Math.max(1,minute(block.end())-minute(block.start())-block.actualMinutes())));openFocus=true;return null;});}
    private static ComboBox<String> times(String value){ComboBox<String> box=new ComboBox<>();for(int m=0;m<1440;m+=15)box.getItems().add(PlanningPreferences.time(m));if(!box.getItems().contains(value))box.getItems().add(value);box.setValue(value);box.setMaxWidth(105);return box;}
    private static Button button(String text,String id,Runnable action){Button b=new Button(text);b.setId(id);b.setAccessibleText(text);b.setOnAction(e->action.run());return b;}
    private static MenuItem menu(String title,Runnable action){MenuItem item=new MenuItem(title);item.setOnAction(e->action.run());return item;}
    /** Scrolls the week grid while a drag (pool card, block move/resize, range selection) nears a viewport edge. */
    private void installAutoScroll(ScrollPane canvas){
        double edge=48;
        java.util.function.BiConsumer<Double,Double> track=(sceneX,sceneY)->{
            var bounds=canvas.getLayoutBounds();Point2D p=canvas.sceneToLocal(sceneX,sceneY);if(p==null)return;
            scrollDy=p.getY()<edge?-(edge-p.getY()):p.getY()>bounds.getHeight()-edge?p.getY()-(bounds.getHeight()-edge):0;
            scrollDx=p.getX()<edge?-(edge-p.getX()):p.getX()>bounds.getWidth()-edge?p.getX()-(bounds.getWidth()-edge):0;
            if((scrollDy!=0||scrollDx!=0)&&autoScroll.getStatus()!=Animation.Status.RUNNING)autoScroll.play();
        };
        autoScroll.getKeyFrames().setAll(new KeyFrame(Duration.millis(30),e->{
            if(scrollDy==0&&scrollDx==0){autoScroll.stop();return;}
            var content=canvas.getContent().getLayoutBounds();var view=canvas.getViewportBounds();
            double h=content.getHeight()-view.getHeight(),w=content.getWidth()-view.getWidth();
            if(h>0&&scrollDy!=0)canvas.setVvalue(Math.max(0,Math.min(1,canvas.getVvalue()+Math.signum(scrollDy)*Math.min(24,Math.abs(scrollDy)/2+4)/h)));
            if(w>0&&scrollDx!=0)canvas.setHvalue(Math.max(0,Math.min(1,canvas.getHvalue()+Math.signum(scrollDx)*Math.min(24,Math.abs(scrollDx)/2+4)/w)));
        }));autoScroll.setCycleCount(Animation.INDEFINITE);
        canvas.addEventFilter(DragEvent.DRAG_OVER,e->track.accept(e.getSceneX(),e.getSceneY()));
        canvas.addEventFilter(MouseEvent.MOUSE_DRAGGED,e->track.accept(e.getSceneX(),e.getSceneY()));
        Runnable stop=()->{scrollDy=0;scrollDx=0;autoScroll.stop();};
        canvas.addEventFilter(DragEvent.DRAG_EXITED,e->stop.run());canvas.addEventFilter(DragEvent.DRAG_DROPPED,e->stop.run());
        canvas.addEventFilter(MouseEvent.MOUSE_RELEASED,e->stop.run());canvas.addEventHandler(DragEvent.DRAG_DONE,e->stop.run());
    }
    private static ScrollPane scroll(Node node){ScrollPane scroll=new ScrollPane(node);scroll.setFitToWidth(true);return scroll;}
    private static void place(Pane pane,Region child,double x,double y,double width,double height){child.setManaged(false);child.resizeRelocate(x,Math.max(0,y),width,height);pane.getChildren().add(child);}
    private static int minute(String time){return PlanningPreferences.minute(time);}
    private static int snap(int value){return (int)(Math.round(value/15d)*15);}
    private static double y(String time){return (minute(time)-FIRST)*SCALE;}
    private static String duration(int minutes){return minutes/60+"s "+minutes%60+"dk";}
    public void dispose(){flushInspector();if(saving)throw new IllegalStateException("Kayıt sürüyor; tamamlanmasını bekle");disposed=true;debounce.stop();watch.stop();autoScroll.stop();if(pending!=null)pending.cancel();}
    @FunctionalInterface private interface Io{Object run()throws Exception;}
}
