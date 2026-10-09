package org.example;

import atlantafx.base.theme.PrimerDark;
import atlantafx.base.theme.PrimerLight;
import javafx.application.Application;
import javafx.scene.Parent;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.Tooltip;
import javafx.scene.text.Font;
import org.kordamp.ikonli.javafx.FontIcon;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/** Shared native theme; financial HTML keeps its own stylesheet. */
final class UiTheme {
    private final Path settingsFile;
    private boolean dark;

    UiTheme(Path settingsFile, String defaultTheme) {
        this.settingsFile = settingsFile;
        dark = "dark".equalsIgnoreCase(defaultTheme);
        if (Files.isRegularFile(settingsFile)) {
            try (var reader = Files.newBufferedReader(settingsFile)) {
                Properties settings = new Properties();
                settings.load(reader);
                dark = "dark".equals(settings.getProperty("theme", defaultTheme));
            } catch (IOException ignored) { /* Keep the configured default. */ }
        }
    }

    static void loadFonts() {
        for (String name : new String[]{"IBMPlexSans-Regular", "IBMPlexSans-Medium",
                "IBMPlexSans-SemiBold", "IBMPlexSans-Bold", "IBMPlexMono-Regular", "IBMPlexMono-Medium"}) {
            try (var input = UiTheme.class.getResourceAsStream("/ui/fonts/" + name + ".ttf")) {
                if (input != null) Font.loadFont(input, 13);
            } catch (IOException ignored) { /* System fonts remain available. */ }
        }
    }

    void apply(Parent root) {
        String stylesheet = dark ? new PrimerDark().getUserAgentStylesheet()
                : new PrimerLight().getUserAgentStylesheet();
        if (!stylesheet.equals(Application.getUserAgentStylesheet()))
            Application.setUserAgentStylesheet(stylesheet);
        if (!root.getStyleClass().contains("uygulama")) root.getStyleClass().add("uygulama");
        if (dark && !root.getStyleClass().contains("dark")) root.getStyleClass().add("dark");
        if (!dark) root.getStyleClass().remove("dark");
    }

    void setDark(boolean value, Parent root) throws IOException {
        // Save before applying so the switch and stored preference cannot diverge on failure.
        AtomicFileWriter.writeText(settingsFile, "theme=" + (value ? "dark" : "light") + "\n");
        dark = value;
        apply(root);
    }

    boolean isDark() { return dark; }

    static FontIcon icon(String name) { return new FontIcon("fth-" + name); }

    static void decorate(ButtonBase button, String icon, String... styles) {
        button.setGraphic(icon(icon));
        button.getStyleClass().addAll(styles);
    }

    static void iconOnly(ButtonBase button, String icon) {
        String label = button.getText();
        decorate(button, icon, "button-icon", "flat");
        button.setTooltip(new Tooltip(label));
        button.setAccessibleText(label);
        button.setText(null);
    }
}
