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
    private boolean reducedMotion;

    UiTheme(Path settingsFile, String defaultTheme) {
        this.settingsFile = settingsFile;
        dark = "dark".equalsIgnoreCase(defaultTheme);
        if (Files.isRegularFile(settingsFile)) {
            try (var reader = Files.newBufferedReader(settingsFile)) {
                Properties settings = new Properties();
                settings.load(reader);
                dark = "dark".equals(settings.getProperty("theme", defaultTheme));
                reducedMotion = Boolean.parseBoolean(settings.getProperty("reducedMotion", "false"));
            } catch (IOException ignored) { /* Keep the configured default. */ }
        }
    }

    static void loadFonts() {
        for (String name : new String[]{"IBMPlexSans-Regular", "IBMPlexSans-Medium",
                "IBMPlexSans-SemiBold", "IBMPlexSans-Bold", "IBMPlexMono-Regular", "IBMPlexMono-Medium",
                "Poppins-Regular", "Poppins-Medium", "Poppins-SemiBold", "Poppins-Bold"}) {
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
        if (reducedMotion && !root.getStyleClass().contains("reduced-motion")) root.getStyleClass().add("reduced-motion");
        if (!reducedMotion) root.getStyleClass().remove("reduced-motion");
    }

    void setDark(boolean value, Parent root) throws IOException {
        // Save before applying so the switch and stored preference cannot diverge on failure.
        save(value, reducedMotion);
        dark = value;
        apply(root);
    }

    boolean isDark() { return dark; }
    boolean isReducedMotion() { return reducedMotion; }

    void setReducedMotion(boolean value, Parent root) throws IOException {
        save(dark, value); reducedMotion = value; apply(root);
    }

    private void save(boolean dark, boolean reducedMotion) throws IOException {
        AtomicFileWriter.writeText(settingsFile, "theme=" + (dark ? "dark" : "light")
                + "\nreducedMotion=" + reducedMotion + "\n");
    }

    static void styleDialog(javafx.scene.control.Dialog<?> dialog, javafx.scene.Node owner) {
        if (owner.getScene() != null) {
            dialog.initOwner(owner.getScene().getWindow());
            dialog.getDialogPane().getStyleClass().add("uygulama");
            if (owner.getScene().getRoot().getStyleClass().contains("dark")) dialog.getDialogPane().getStyleClass().add("dark");
        }
        dialog.getDialogPane().getStylesheets().add(UiTheme.class.getResource("/ui/style.css").toExternalForm());
    }

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
