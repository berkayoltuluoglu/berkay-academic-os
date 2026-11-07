package org.example;

import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.DirectoryChooser;
import java.nio.file.Path;

final class CloudSyncModuleView {
    private final CloudSyncService cloud;
    private final VBox root=new VBox(12);
    private final Label calendarStatus=new Label(),driveStatus=new Label(),driveAccount=new Label(),localFolder=new Label(),message=new Label();
    private final ListView<DriveSyncService.Conflict> conflicts=new ListView<>();
    private final ListView<CalendarWriteQueue.Operation> queue=new ListView<>();
    private final CheckBox calendarAuto=new CheckBox("Takvimi 30 saniyede bir otomatik senkronize et"),driveAuto=new CheckBox("Drive'ı 30 saniyede bir otomatik senkronize et");
    private Task<Void> pending;
    private boolean authorizing,disposed;
    private final Runnable listener=()->Platform.runLater(()->{if(!disposed&&pending==null)render();});
    CloudSyncModuleView(CloudSyncService cloud){this.cloud=cloud;}
    VBox build(){
        root.setId("cloud-sync-view");calendarStatus.setId("sync-calendar-status");driveStatus.setId("sync-drive-status");message.setId("sync-message");
        calendarAuto.setId("sync-calendar-automatic");driveAuto.setId("sync-drive-automatic");driveAccount.setId("sync-drive-account");
        for(var label:new Label[]{calendarStatus,driveStatus,driveAccount,localFolder,message})label.setWrapText(true);
        queue.setId("sync-calendar-queue");conflicts.setId("sync-drive-conflicts");calendarAuto.setSelected(cloud.calendarAutomatic());
        calendarAuto.setOnAction(e->{try{cloud.calendarAutomatic(calendarAuto.isSelected());}catch(Exception error){message.setText(error.getMessage());}});
        driveAuto.setOnAction(e->{boolean enabled=driveAuto.isSelected();run(()->cloud.drive().automatic(enabled),false);});
        Button calendarRefresh=button("Takvimi senkronize et","sync-calendar-now",()->run(cloud::synchronizeCalendarNow,false));
        Button discard=button("Bekleyen işlemi kaldır","sync-calendar-discard",()->{var selected=queue.getSelectionModel().getSelectedItem();if(selected==null)return;
            if(confirm("Bu bekleyen yerel değişiklik kaldırılsın mı? Google'daki mevcut sürüm korunur."))run(()->((CalendarSyncService)cloud.calendar()).discard(selected.id()),false);});
        Button retry=button("Yerel değişikliği gönder","sync-calendar-retry",()->{var selected=queue.getSelectionModel().getSelectedItem();if(selected==null)return;
            if(confirm("Google'daki güncel sürüme seçili yerel değişiklik uygulansın mı?"))run(()->((CalendarSyncService)cloud.calendar()).retry(selected.id(),true),false);});
        queue.setCellFactory(v->new ListCell<>(){@Override protected void updateItem(CalendarWriteQueue.Operation op,boolean empty){super.updateItem(op,empty);setText(empty||op==null?null:op.draft().title()+" · "+op.action()+"\n"+op.account()+" · "+(op.blocked()?"Çözüm bekliyor":"Gönderim bekliyor")+(op.error().isBlank()?"":"\n"+op.error()));}});
        Label calendarHint=new Label("İnternet yokken değişiklikler yerelde tutulur. Bağlantı gelince aynı hesaba gönderilir; başka yerde değişmiş kayıtlar çözüm bekler.");calendarHint.setWrapText(true);
        VBox calendarPane=new VBox(10,calendarAuto,calendarStatus,calendarHint,calendarRefresh,queue,new FlowPane(8,8,discard,retry));VBox.setVgrow(queue,Priority.ALWAYS);
        Button connect=button("Drive'a bağlan","sync-drive-connect",()->run(()->cloud.drive().connect(),true));
        Button cancel=button("Google girişini iptal et","sync-drive-cancel",()->{cloud.drive().cancelAuthorization();if(authorizing&&pending!=null)pending.cancel(true);});
        Button sync=button("Drive'ı senkronize et","sync-drive-now",()->run(cloud::synchronizeDriveNow,false));
        Button folder=button("Yerel klasör seç","sync-drive-folder",()->{DirectoryChooser chooser=new DirectoryChooser();chooser.setTitle("Senkronize edilecek belge klasörü");var selected=chooser.showDialog(root.getScene().getWindow());if(selected!=null)run(()->cloud.drive().chooseDirectory(selected.toPath()),false);});
        Button open=button("Yerel klasörü aç","sync-drive-open",()->{try{java.nio.file.Files.createDirectories(cloud.drive().localDirectory());DesktopIntegration.open(cloud.drive().localDirectory());}catch(Exception error){message.setText(error.getMessage());}});
        Button web=button("Drive klasörünü aç","sync-drive-web",()->{try{DesktopIntegration.browse(cloud.drive().folderLink());}catch(Exception error){message.setText(error.getMessage());}});
        Button disconnect=button("Drive bağlantısını kaldır","sync-drive-disconnect",()->run(()->cloud.drive().disconnect(),false));
        conflicts.setCellFactory(v->new ListCell<>(){@Override protected void updateItem(DriveSyncService.Conflict c,boolean empty){super.updateItem(c,empty);setText(empty||c==null?null:c.path()+"\n"+c.reason());}});
        Button local=button("Yerel sürümü kullan","sync-drive-keep-local",()->resolve(true));Button remote=button("Drive sürümünü kullan","sync-drive-keep-remote",()->resolve(false));
        Label driveHint=new Label("Academic OS aracılığıyla oluşturulan dosyalar kendi Drive klasöründe iki yönlü senkronize edilir. İki tarafta değişen içerikler otomatik ezilmez; Drive kopyası ve yerel dosya korunur. Silinen dosyalar geri alınabilir kopyaya/Drive çöp kutusuna taşınır.");driveHint.setWrapText(true);
        VBox drivePane=new VBox(10,new FlowPane(8,8,connect,cancel,sync,disconnect),driveAccount,driveAuto,localFolder,new FlowPane(8,8,folder,open,web),driveStatus,driveHint,new Label("Çözüm bekleyen dosyalar"),conflicts,new FlowPane(8,8,local,remote));VBox.setVgrow(conflicts,Priority.ALWAYS);
        TabPane tabs=new TabPane(new Tab("Google Drive",drivePane),new Tab("Takvim ve bekleyen değişiklikler",calendarPane));tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        root.getChildren().setAll(tabs,message);VBox.setVgrow(tabs,Priority.ALWAYS);cloud.addListener(listener);render();return root;
    }
    private void resolve(boolean local){var selected=conflicts.getSelectionModel().getSelectedItem();if(selected==null)return;
        if(confirm(selected.path()+": "+(local?"yerel":"Drive")+" sürümü kullanılsın mı? Mevcut kopyalar korunur."))run(()->cloud.drive().resolve(selected.path(),local),false);}
    private void render(){calendarStatus.setText(cloud.calendarStatus());driveStatus.setText(cloud.driveStatus());
        driveAccount.setText(cloud.drive().isConnected()?cloud.drive().getAccountEmail():"Drive bağlı değil");
        try{driveAuto.setSelected(cloud.drive().automatic());localFolder.setText("Yerel klasör: "+cloud.drive().localDirectory());conflicts.getItems().setAll(cloud.drive().conflicts());
            if(cloud.calendar() instanceof CalendarSyncService calendar)queue.getItems().setAll(calendar.operations());}
        catch(Exception error){message.setText(error.getMessage());}
    }
    private void run(Action action,boolean authorize){if(pending!=null)return;authorizing=authorize;message.setText(authorize?"Google izni bekleniyor…":"İşlem sürüyor…");
        Task<Void> task=new Task<>(){@Override protected Void call()throws Exception{action.run();return null;}};pending=task;
        busy(true);
        task.setOnSucceeded(e->{pending=null;authorizing=false;if(!disposed){busy(false);render();message.setText("İşlem tamamlandı.");}});
        task.setOnFailed(e->{pending=null;authorizing=false;if(!disposed){busy(false);render();message.setText(task.getException().getMessage());}});
        task.setOnCancelled(e->{pending=null;authorizing=false;if(!disposed){busy(false);render();message.setText("Giriş iptal edildi.");}});Thread.ofVirtual().name("cloud-sync-action").start(task);
    }
    private void busy(boolean value){for(var node:root.lookupAll(".button"))if(node instanceof Button button&&!"sync-drive-cancel".equals(button.getId()))button.setDisable(value);calendarAuto.setDisable(value);driveAuto.setDisable(value);}
    private Button button(String label,String id,Runnable action){Button result=new Button(label);result.setId(id);result.setOnAction(e->{try{action.run();}catch(Exception error){message.setText(error.getMessage());}});return result;}
    private boolean confirm(String text){var dialog=new Alert(Alert.AlertType.CONFIRMATION,text,ButtonType.YES,ButtonType.NO);dialog.initOwner(root.getScene().getWindow());return dialog.showAndWait().orElse(ButtonType.NO)==ButtonType.YES;}
    void dispose(){if(pending!=null&&!authorizing)throw new IllegalStateException("Dosya senkronizasyonunun tamamlanmasını bekle.");disposed=true;cloud.removeListener(listener);
        if(pending!=null){cloud.drive().cancelAuthorization();pending.cancel(true);}}
    @FunctionalInterface private interface Action{void run()throws Exception;}
}
