package org.example;

import javafx.beans.value.ChangeListener;
import javafx.scene.control.*;
import javafx.scene.layout.*;

/** Settings panel and explicit install/uninstall confirmation. */
final class UpdateSettingsView {
    private final UpdateController updates;
    private ChangeListener<UpdateController.State> listener;
    UpdateSettingsView(UpdateController updates) { this.updates = updates; }
    VBox build() {
        Label title = new Label("Uygulama ve güncellemeler"); title.getStyleClass().add("panel-baslik");
        Label version = new Label("Academic OS · " + ReleaseVersion.current()); version.setId("settings-app-version");
        VBox panel = new VBox(10, title, version); panel.getStyleClass().add("panel"); panel.setId("settings-updates");
        if (updates == null || !updates.supported()) {
            Label hint = new Label("Uygulama içi güncelleme ve kaldırma, kurulu Windows sürümünde kullanılabilir."); hint.setWrapText(true);
            panel.getChildren().add(hint); return panel;
        }
        CheckBox check = new CheckBox("Açılışta güncellemeleri kontrol et"); check.setId("updates-startup"); check.setSelected(updates.preferences().checkOnStartup());
        CheckBox automatic = new CheckBox("Güncellemeleri otomatik indir"); automatic.setId("updates-auto-download"); automatic.setSelected(updates.preferences().autoDownload());
        Label preferenceStatus = new Label(); preferenceStatus.setWrapText(true);
        Runnable save = () -> {
            try { updates.savePreferences(new UpdatePreferences(check.isSelected(), automatic.isSelected())); preferenceStatus.setText("Güncelleme tercihleri kaydedildi."); }
            catch (Exception error) {
                check.setSelected(updates.preferences().checkOnStartup()); automatic.setSelected(updates.preferences().autoDownload());
                preferenceStatus.setText("Tercihler kaydedilemedi: " + error.getMessage());
            }
        };
        check.setOnAction(event -> save.run()); automatic.setOnAction(event -> save.run());
        Button refresh = new Button("Şimdi kontrol et"), download = new Button("Güncellemeyi indir"), cancel = new Button("İndirmeyi iptal et"), install = new Button("Yeniden başlat ve güncelle"), uninstall = new Button("Uygulamayı kaldır");
        refresh.setId("updates-check"); download.setId("updates-download"); cancel.setId("updates-cancel"); install.setId("updates-install"); uninstall.setId("updates-uninstall");
        refresh.setOnAction(event -> updates.check()); download.setOnAction(event -> updates.download()); cancel.setOnAction(event -> updates.cancel());
        install.setOnAction(event -> {
            if (confirm(panel, "Güncellemeyi kur", "Bekleyen değişiklikler kaydedilecek, uygulama tamamen kapanacak ve güncelleme kurulacak. Başarılı kurulumdan sonra yeniden açılacak.")) updates.install();
        });
        uninstall.setOnAction(event -> {
            if (confirm(panel, "Academic OS'u kaldır", "Uygulama kapanacak ve bilgisayardan kaldırılacak. Görevlerin, derslerin ve diğer kayıtların veri klasöründe korunacak.")) updates.uninstall();
        });
        Label status = new Label(); status.setWrapText(true); status.setId("updates-status");
        TextArea notes = new TextArea(); notes.setEditable(false); notes.setWrapText(true); notes.setPrefRowCount(4); notes.setId("updates-notes");
        ProgressBar progress = new ProgressBar(0); progress.setMaxWidth(Double.MAX_VALUE); progress.setId("updates-progress");
        listener = (observable, before, state) -> {
            status.setText(state.message()); progress.setProgress(state.progress());
            boolean busy = state.phase() == UpdateController.Phase.CHECKING || state.phase() == UpdateController.Phase.DOWNLOADING || state.phase() == UpdateController.Phase.INSTALLING;
            refresh.setDisable(busy); download.setDisable(busy || state.release() == null || state.phase() == UpdateController.Phase.READY);
            cancel.setDisable(state.phase() != UpdateController.Phase.DOWNLOADING); install.setDisable(state.phase() != UpdateController.Phase.READY);
            uninstall.setDisable(busy || !updates.canUninstall());
            notes.setText(state.release() == null ? "" : state.release().notes());
            notes.setManaged(!notes.getText().isBlank()); notes.setVisible(notes.isManaged());
        };
        updates.stateProperty().addListener(listener); listener.changed(updates.stateProperty(), null, updates.state());
        Label hint = new Label("İndirilen setup SHA-256 ile doğrulanır. Kurulum sen onayladığında başlar. Kaldırıcı ayrıca program klasöründe Uninstall.exe olarak bulunur."); hint.setWrapText(true);
        panel.getChildren().addAll(check, automatic, preferenceStatus, status, progress, notes, new FlowPane(8, 8, refresh, download, cancel, install), hint, uninstall);
        return panel;
    }
    private static boolean confirm(VBox panel, String title, String message) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, message, ButtonType.OK, ButtonType.CANCEL);
        alert.setTitle(title); alert.setHeaderText(title); alert.initOwner(panel.getScene().getWindow()); UiTheme.styleDialog(alert, panel);
        return alert.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK;
    }
    void dispose() { if (updates != null && listener != null) updates.stateProperty().removeListener(listener); }
}
