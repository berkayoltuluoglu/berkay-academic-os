package org.example;

import javafx.beans.value.ChangeListener;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.web.WebView;

public class WebModuleView {
    private WebView currentView;
    private ChangeListener<String> locationListener;
    private Button back;
    private Button forward;

    public SplitPane build(ModuleItem module, WebView webView, TextArea notesArea,
                           Runnable saveAction, Runnable clearAction) {
        dispose();
        currentView = webView;
        var engine = webView.getEngine();
        var history = engine.getHistory();
        back = new Button("Geri");
        forward = new Button("İleri");
        Button reload = new Button("Yenile");
        UiTheme.iconOnly(back, "arrow-left");
        UiTheme.iconOnly(forward, "arrow-right");
        UiTheme.iconOnly(reload, "refresh-cw");
        back.disableProperty().bind(history.currentIndexProperty().lessThanOrEqualTo(0));
        forward.disableProperty().bind(javafx.beans.binding.Bindings.createBooleanBinding(
                () -> history.getCurrentIndex() >= history.getEntries().size() - 1,
                history.currentIndexProperty(), history.getEntries()));
        back.setOnAction(e -> history.go(-1));
        forward.setOnAction(e -> history.go(1));
        reload.setOnAction(e -> engine.reload());
        TextField address = new TextField(module.getTarget());
        address.setAccessibleText("Web adresi");
        address.getStyleClass().add("adres-cubugu");
        HBox.setHgrow(address, Priority.ALWAYS);
        locationListener = (obs, oldValue, value) -> address.setText(value);
        engine.locationProperty().addListener(locationListener);
        address.setOnAction(e -> {
            String target = address.getText().trim();
            if (!target.contains(":")) target = "https://" + target;
            engine.load(target);
        });
        Button external = new Button("Tarayıcıda aç");
        UiTheme.decorate(external, "external-link", "flat");
        external.setOnAction(e -> {
            try {
                new ProcessBuilder("xdg-open", engine.getLocation()).start();
            } catch (Exception ex) {
                Alert alert = new Alert(Alert.AlertType.ERROR, "Tarayıcı açılamadı: " + ex.getMessage());
                alert.initOwner(webView.getScene().getWindow());
                alert.show();
            }
        });
        HBox navigation = new HBox(5, back, forward, reload, address, external);
        navigation.setAlignment(Pos.CENTER_LEFT);
        navigation.getStyleClass().addAll("panel", "web-cubugu");
        VBox browser = new VBox(12, navigation, webView);
        browser.setMinWidth(280);
        VBox.setVgrow(webView, Priority.ALWAYS);

        Label notesTitle = new Label(module.getName() + " Notları");
        notesTitle.getStyleClass().add("panel-baslik");
        Label hint = new Label("Değişiklikler otomatik kaydedilir.");
        hint.getStyleClass().add("soluk");
        hint.setWrapText(true);
        Button save = new Button("Kaydet");
        Button clear = new Button("Temizle");
        UiTheme.decorate(save, "save", "accent");
        UiTheme.decorate(clear, "trash-2", "flat");
        save.setOnAction(e -> saveAction.run());
        clear.setOnAction(e -> clearAction.run());
        FlowPane toolbar = new FlowPane(8, 8, save, clear);
        VBox notes = new VBox(12, notesTitle, hint, toolbar, notesArea);
        notes.getStyleClass().add("panel");
        notes.setMinWidth(200);
        notes.setPrefWidth(300);
        VBox.setVgrow(notesArea, Priority.ALWAYS);
        SplitPane split = new SplitPane(browser, notes);
        split.getStyleClass().add("workspace-split");
        split.setDividerPositions(0.73);
        if (module.getTarget() != null && !module.getTarget().isBlank()) engine.load(module.getTarget());
        return split;
    }

    public void dispose() {
        if (currentView != null && locationListener != null)
            currentView.getEngine().locationProperty().removeListener(locationListener);
        if (back != null) back.disableProperty().unbind();
        if (forward != null) forward.disableProperty().unbind();
        currentView = null;
        locationListener = null;
    }
}
