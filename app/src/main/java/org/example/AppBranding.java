package org.example;

import javafx.scene.Node;
import javafx.scene.image.*;
import javafx.stage.Stage;

/** The user's approved brown command mark, shared by application windows and the title bar. */
final class AppBranding {
    private AppBranding() {}
    static Node graphic() {
        var resource = AppBranding.class.getResource("/branding/logo.png");
        if (resource == null) return UiTheme.icon("command");
        var view = new ImageView(new Image(resource.toExternalForm()));
        view.setFitWidth(30); view.setFitHeight(30); view.setPreserveRatio(true); return view;
    }
    static void apply(Stage stage) {
        var icons = new java.util.ArrayList<Image>();
        for (int size : new int[]{32, 64, 128, 256}) {
            var resource = AppBranding.class.getResource("/branding/logo-" + size + ".png");
            if (resource != null) icons.add(new Image(resource.toExternalForm()));
        }
        if (!icons.isEmpty()) stage.getIcons().setAll(icons);
    }
}
