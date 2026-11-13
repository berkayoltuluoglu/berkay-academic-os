package org.example;

import javafx.animation.*;
import javafx.concurrent.Task;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.*;
import javafx.util.Duration;
import java.nio.file.Path;
import java.time.*;
import java.util.*;
import java.util.concurrent.Callable;
import static org.example.PlannerCommand.*;

/** A quiet focus window with durable subtasks, notes and a two-click end review. */
final class FocusModeView {
    private final Path root;
    private final FocusTimerService timer;
    private final Stage stage=new Stage();
    private final VBox content=new VBox(14),details=new VBox(8),review=new VBox(8);
    private final javafx.scene.shape.Arc arc=new javafx.scene.shape.Arc(100,100,84,84,90,-360);
    private final Label clock=new Label(),status=new Label(),name=new Label(),due=new Label();
    private final Spinner<Integer> actual=new Spinner<>(0,1440,0,5);
    private final Timeline pulse=new Timeline(new KeyFrame(Duration.seconds(1),e->pulse()));
    private final PauseTransition debounce=new PauseTransition(Duration.millis(400));
    private StudySession block;
    private PlanningPreferences prefs=PlanningPreferences.defaults();
    private boolean busy,ended;
    private TextArea noteField;
    private String savedNote="";

    FocusModeView(Path root,FocusTimerService timer){this.root=root;this.timer=timer;}
    void show(Window owner){
        stage.initOwner(owner);stage.setTitle("Academic OS · Odak");AppBranding.apply(stage);
        clock.getStyleClass().add("focus-time");name.getStyleClass().add("silver-section-title");
        Button toggle=new Button("Duraklat / devam et"),finish=new Button("Bitir"),rest=new Button("Mola");
        toggle.setOnAction(e->run(()->{timer.toggle();return null;},false));finish.setOnAction(e->run(()->{timer.finish();return null;},true));
        rest.setOnAction(e->run(()->{timer.finish();timer.startBreak(prefs.shortBreak());return null;},true));
        CheckBox mini=new CheckBox("Küçük pencere · her zaman üstte");mini.setOnAction(e->{stage.setAlwaysOnTop(mini.isSelected());stage.setWidth(mini.isSelected()?360:520);stage.setHeight(mini.isSelected()?480:760);});
        content.setStyle("-fx-padding: 24;");content.setId("focus-mode");clock.setId("focus-mode-time");status.setId("focus-status");status.setWrapText(true);due.setWrapText(true);
        var track=new javafx.scene.shape.Circle(100,100,84);track.getStyleClass().add("focus-track");arc.setType(javafx.scene.shape.ArcType.OPEN);arc.getStyleClass().add("focus-progress");Pane graphics=new Pane(track,arc);graphics.setMinSize(200,200);graphics.setMaxSize(200,200);StackPane ring=new StackPane(graphics,clock);ring.setPrefHeight(200);
        content.getChildren().setAll(name,due,ring,new HBox(8,toggle,finish,rest),mini,details,review,status);
        var scene=new Scene(new ScrollPane(content),520,760);scene.getStylesheets().add(UiTheme.class.getResource("/ui/style.css").toExternalForm());stage.setScene(scene);
        stage.setOnCloseRequest(e->{if(busy){e.consume();return;}if(noteField!=null&&!noteField.getText().equals(savedNote)){e.consume();run(()->null,false);}});
        stage.setOnHidden(e->{pulse.stop();debounce.stop();});stage.show();pulse.setCycleCount(Animation.INDEFINITE);pulse.play();load();
    }
    private void load(){run(()->{
        prefs=PlanningPreferences.load(root);
        block=new StudyStorageService(root.resolve("data/study/sessions.json")).load().stream().filter(s->s.id().equals(timer.targetId())).findFirst().orElseThrow(()->new IllegalStateException("Odak bloğu bulunamadı"));
        var tasks=new TaskStorageService(root.resolve("data/tasks.json")).loadTasks();
        return new Loaded(block,tasks,PlannerService.notes(root).notes().getOrDefault(block.id(),""),new CourseStorageService(root.resolve("data/courses/academic.json")).load());
    },false);}
    private record Loaded(StudySession block,List<TaskItem> tasks,String note,CourseData courses){}
    private void render(Loaded loaded){
        block=loaded.block();String course=loaded.courses().courses().stream().filter(c->c.id().equals(block.courseId())).map(CourseData.Course::name).findFirst().orElse("");name.setText(block.title()+(course.isBlank()?"":" · "+course));actual.getValueFactory().setValue(block.actualMinutes());details.getChildren().clear();
        String deadline=loaded.tasks().stream().filter(t->t.getId().equals(block.taskId())&&t.getDate()!=null).map(t->t.getDate()+" "+Objects.toString(t.getDueTime(),"")).findFirst().orElseGet(()->loaded.courses().work().stream().filter(w->w.id().equals(block.workId())).map(w->w.date()+" "+w.dueTime()).findFirst().orElse(""));
        if(deadline.isBlank())due.setText("");else{String[] parts=deadline.trim().split(" ");var at=LocalDate.parse(parts[0]).atTime(parts.length>1?LocalTime.parse(parts[1]):LocalTime.of(23,59));long minutes=java.time.Duration.between(LocalDateTime.now(ZoneId.of(prefs.timezone())),at).toMinutes();due.setText("Son teslim: "+deadline+" · "+(minutes<0?"Gecikmiş":minutes/1440+" gün "+minutes%1440/60+" saat kaldı"));}
        loaded.tasks().stream().filter(t->t.getId().equals(block.taskId())).findFirst().ifPresent(t->{for(var s:t.getSubtasks()){
            CheckBox check=new CheckBox(s.title());check.setSelected(s.done());check.setOnAction(e->{var updated=t.copy();updated.setSubtasks(t.getSubtasks().stream().map(v->v.id().equals(s.id())?new TaskItem.Subtask(v.id(),v.title(),check.isSelected()):v).toList());run(()->new PlannerService(root).execute(new UpdateTask(updated)),false);});details.getChildren().add(check);
        }});
        TextArea note=new TextArea(loaded.note());noteField=note;savedNote=loaded.note();note.setId("focus-note");note.setPrefRowCount(3);note.setPromptText("Hızlı not");note.textProperty().addListener((o,a,b)->{debounce.setOnFinished(e->run(()->null,false));debounce.playFromStart();});details.getChildren().add(note);
    }
    private void pulse(){long seconds=(timer.remainingMillis()+999)/1000;clock.setText(String.format(Locale.ROOT,"%02d:%02d",seconds/60,seconds%60));arc.setLength(-360d*timer.remainingMillis()/timer.durationMillis());stage.setTitle("Odak · "+clock.getText());
        if(seconds==0&&!ended&&!busy){ended=true;run(()->{timer.creditCompleted();return null;},true);}}
    private void review(){
        if(block==null)return;review.getChildren().clear();Label question=new Label(timer.breakMode()?"Mola bittiğinde devam edebilirsin":"Bitti mi?");
        Button done=new Button("Tamamlandı"),more=new Button("Devam edecek"),stuck=new Button("Takıldım");done.setId("focus-review-done");more.setId("focus-review-continue");
        done.setOnAction(e->complete(true,false));more.setOnAction(e->complete(false,true));stuck.setOnAction(e->complete(false,true));
        review.getChildren().addAll(question,new Label("Gerçekleşen dakika"),actual,new HBox(8,done,more,stuck));
        if(prefs.pomodoro()){Button next=new Button(timer.breakMode()?"Çalışmaya dön":"Pomodoro molası");next.setOnAction(e->run(()->{
            if(timer.breakMode()){timer.reset();timer.configureMinutes(prefs.workMinutes());timer.toggle();}
            else{timer.startBreak((timer.completedCycles()+1)%prefs.cycles()==0?prefs.longBreak():prefs.shortBreak());}
            return null;
        },false));review.getChildren().add(next);}
    }
    private void complete(boolean done,boolean suggest){int minutes=actual.getValue();String id=block.id();run(()->{
        var planner=new PlannerService(root);List<PlannerCommand> commands=new ArrayList<>();commands.add(new CompleteBlock(id,minutes,done));
        if(done&&!block.taskId().isBlank())commands.add(new CompleteItem("task",block.taskId(),true));
        if(done&&!block.workId().isBlank())commands.add(new CompleteItem("work",block.workId(),true));
        planner.execute(commands,"user",done?"Odak tamamlandı":"Odak devam edecek");
        return suggest?nextSlot(planner):null;
    },false);}
    private PlanningSnapshot.Slot nextSlot(PlannerService planner)throws Exception{
        LocalDate today=LocalDate.now(ZoneId.of(prefs.timezone()));LocalTime now=LocalTime.now(ZoneId.of(prefs.timezone()));
        var snap=planner.snapshot(today);return snap.freeSlots().stream().filter(s->!LocalDate.parse(s.date()).isBefore(today)&&(!s.date().equals(today.toString())||LocalTime.parse(s.start()).isAfter(now))&&s.minutes()>=prefs.minMinutes()).findFirst().orElse(null);
    }
    private void run(Callable<?> action,boolean showReview){if(busy)return;busy=true;content.setDisable(true);status.setText("Kaydediliyor…");String note=noteField==null?null:noteField.getText();String target=block==null?null:block.id();Task<Object> task=new Task<>(){@Override protected Object call()throws Exception{if(note!=null&&target!=null&&!note.equals(savedNote)){new PlannerService(root).execute(new SetNote(target,note));savedNote=note;}return action.call();}};
        task.setOnSucceeded(e->{busy=false;content.setDisable(false);status.setText("Kaydedildi");if(task.getValue() instanceof Loaded loaded)render(loaded);else if(task.getValue() instanceof PlanningSnapshot.Slot slot){
            Button schedule=new Button(slot.date()+" "+slot.start()+" · Kalanı planla");schedule.setOnAction(event->run(()->new PlannerService(root).execute(new ScheduleBlock(block.taskId().isBlank()?block.workId().isBlank()?"":"work":"task",block.taskId().isBlank()?block.workId():block.taskId(),slot.date(),slot.start(),Math.min(60,slot.minutes()),block.title(),ConflictPolicy.REJECT)),false));review.getChildren().add(schedule);
        }if(showReview){loadForReview();}else if(!(task.getValue() instanceof Loaded)&&timer.running()){ended=false;review.getChildren().clear();} });
        task.setOnFailed(e->{busy=false;content.setDisable(false);status.setText(task.getException().getMessage());});Thread.ofVirtual().name("focus-session").start(task);}
    private void loadForReview(){if(busy)return;Task<StudySession> task=new Task<>(){@Override protected StudySession call()throws Exception{return new StudyStorageService(root.resolve("data/study/sessions.json")).load().stream().filter(s->s.id().equals(timer.targetId())).findFirst().orElseThrow();}};
        busy=true;task.setOnSucceeded(e->{busy=false;block=task.getValue();actual.getValueFactory().setValue(block.actualMinutes());review();});task.setOnFailed(e->{busy=false;content.setDisable(false);status.setText(task.getException().getMessage());});Thread.ofVirtual().start(task);}
}
