package org.example;

import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import java.util.*;
import java.util.function.Consumer;

public class SidebarBuilder {
    private final Map<ModuleItem, ToggleButton> buttons = new LinkedHashMap<>();
    private final Map<String, VBox> sections = new LinkedHashMap<>();
    private final ToggleGroup selection = new ToggleGroup();
    private final Label empty = new Label("Eşleşen modül yok");

    public VBox build(AppConfig config, Consumer<ModuleItem> onClick) {
        buttons.clear();
        sections.clear();
        selection.getToggles().clear();
        VBox sidebar = new VBox(22);
        sidebar.getStyleClass().add("kenar-cubugu");
        sidebar.setPrefWidth(236);
        sidebar.setMinWidth(210);
        sidebar.setMaxWidth(236);
        StackPane badge = new StackPane(UiTheme.icon("book-open"));
        badge.getStyleClass().add("logo-kutu");
        Label name = new Label("Berkay");
        name.getStyleClass().add("logo-ad");
        Label subtitle = new Label("Academic OS");
        subtitle.getStyleClass().add("logo-alt");
        HBox logo = new HBox(12, badge, new VBox(1, name, subtitle));
        logo.setAlignment(Pos.CENTER_LEFT);
        logo.getStyleClass().add("logo");

        VBox menu = new VBox(3);
        if (config != null && config.getModules() != null) {
            for (String group : List.of("Çalışma alanı", "Finans", "Web")) {
                Label title = new Label(group);
                title.getStyleClass().add("grup-basligi");
                VBox section = new VBox(3, title);
                sections.put(group, section);
                menu.getChildren().add(section);
            }
            for (ModuleItem module : config.getModules()) {
                if (!module.isEnabled()) continue;
                String type = Objects.toString(module.getType(), "");
                String group = type.contains("web") ? "Web"
                        : "finance".equals(module.getId()) ? "Finans" : "Çalışma alanı";
                ToggleButton button = new ToggleButton(module.getName(), UiTheme.icon(moduleIcon(module)));
                button.setId("nav-" + module.getId());
                button.getStyleClass().add("nav");
                button.setMaxWidth(Double.MAX_VALUE);
                button.setToggleGroup(selection);
                button.setOnAction(e -> onClick.accept(module));
                sections.get(group).getChildren().add(button);
                buttons.put(module, button);
            }
        }
        empty.getStyleClass().add("tema-ad");
        menu.getChildren().add(empty);
        ScrollPane scroll = new ScrollPane(menu);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.getStyleClass().add("menu-kaydirma");
        VBox.setVgrow(scroll, Priority.ALWAYS);
        sidebar.getChildren().addAll(logo, scroll);
        filter("");
        return sidebar;
    }

    void select(ModuleItem module) {
        selection.selectToggle(module == null ? null : buttons.entrySet().stream()
                .filter(entry -> entry.getKey() == module || Objects.equals(entry.getKey().getId(), module.getId()))
                .map(Map.Entry::getValue).findFirst().orElse(null));
    }

    void filter(String query) {
        String needle = normalize(query);
        buttons.forEach((module, button) -> {
            boolean match = normalize(module.getName() + " " + module.getCategory()).contains(needle);
            button.setVisible(match);
            button.setManaged(match);
        });
        sections.values().forEach(section -> {
            boolean visible = section.getChildren().stream().skip(1).anyMatch(javafx.scene.Node::isVisible);
            section.setVisible(visible);
            section.setManaged(visible);
        });
        boolean noResults = buttons.values().stream().noneMatch(javafx.scene.Node::isVisible);
        empty.setVisible(noResults);
        empty.setManaged(noResults);
    }

    void openFirstMatch() {
        buttons.values().stream().filter(javafx.scene.Node::isVisible).findFirst().ifPresent(ButtonBase::fire);
    }

    private static String normalize(String value) {
        return Objects.toString(value, "").toLowerCase(Locale.forLanguageTag("tr"));
    }

    private String moduleIcon(ModuleItem module) {
        return switch (Objects.toString(module.getIcon(), "")) {
            case "sun" -> "sun";
            case "courses" -> "book-open";
            case "folder" -> "folder";
            case "terminal" -> "terminal";
            case "serial" -> "cpu";
            case "book" -> "bookmark";
            case "calendar" -> "calendar";
            case "wallet" -> "credit-card";
            case "play" -> "youtube";
            case "bot" -> "message-circle";
            default -> "globe";
        };
    }
}
