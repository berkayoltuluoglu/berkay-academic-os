package org.example;

import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import java.util.*;
import java.util.function.Consumer;

public class SidebarBuilder {
    private final Map<ModuleItem, ToggleButton> buttons = new LinkedHashMap<>();
    private final Map<String, VBox> sections = new LinkedHashMap<>();
    private final Map<String, ToggleButton> groupButtons = new LinkedHashMap<>();
    private final Set<String> expandedGroups = new HashSet<>();
    private final ToggleGroup selection = new ToggleGroup();
    private final Label empty = new Label("Eşleşen modül yok");
    private String currentQuery = "";

    public VBox build(AppConfig config, Consumer<ModuleItem> onClick) {
        buttons.clear();
        sections.clear();
        groupButtons.clear();
        selection.getToggles().clear();
        VBox sidebar = new VBox(16);
        sidebar.getStyleClass().add("kenar-cubugu");
        sidebar.setPrefWidth(218);
        sidebar.setMinWidth(184);
        sidebar.setMaxWidth(218);

        VBox menu = new VBox(3);
        if (config != null && config.getModules() != null) {
            for (String group : List.of("Çalışma alanı", "Araçlar", "Web")) {
                Label title = new Label(group);
                title.getStyleClass().add("grup-basligi");
                title.setManaged(false); title.setVisible(false);
                VBox section = new VBox(3, title);
                sections.put(group, section);
                if (!group.equals("Çalışma alanı")) {
                    ToggleButton expand = new ToggleButton(group, UiTheme.icon("chevron-right"));
                    expand.getStyleClass().add("nav-group-toggle"); expand.setMaxWidth(Double.MAX_VALUE);
                    expand.setSelected(expandedGroups.contains(group));
                    expand.setOnAction(e -> {
                        if (expand.isSelected()) expandedGroups.add(group); else expandedGroups.remove(group);
                        filter(currentQuery);
                    });
                    groupButtons.put(group, expand); menu.getChildren().add(expand);
                }
                menu.getChildren().add(section);
            }
            List<String> primary = List.of("today", "agenda", "courses", "tasks", "finance");
            var ordered = new ArrayList<>(config.getModules());
            ordered.sort(Comparator.comparingInt(m -> primary.contains(m.getId()) ? primary.indexOf(m.getId()) : 10));
            for (ModuleItem module : ordered) {
                if (!module.isEnabled()) continue;
                String type = Objects.toString(module.getType(), "");
                String group = type.contains("web") ? "Web"
                        : primary.contains(module.getId()) ? "Çalışma alanı" : "Araçlar";
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
        sidebar.getChildren().add(scroll);
        filter("");
        return sidebar;
    }

    void select(ModuleItem module) {
        selection.selectToggle(module == null ? null : buttons.entrySet().stream()
                .filter(entry -> entry.getKey() == module || Objects.equals(entry.getKey().getId(), module.getId()))
                .map(Map.Entry::getValue).findFirst().orElse(null));
    }

    void filter(String query) {
        currentQuery = query;
        String needle = normalize(query);
        buttons.forEach((module, button) -> {
            boolean match = normalize(module.getName() + " " + module.getCategory()).contains(needle);
            button.setVisible(match);
            button.setManaged(match);
        });
        sections.forEach((name, section) -> {
            boolean matches = section.getChildren().stream().skip(1).anyMatch(javafx.scene.Node::isVisible);
            boolean visible = matches && (name.equals("Çalışma alanı") || !needle.isEmpty() || expandedGroups.contains(name));
            section.setVisible(visible);
            section.setManaged(visible);
            var group = groupButtons.get(name);
            if (group != null) { group.setVisible(matches); group.setManaged(matches); }
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
            case "sun" -> "home";
            case "tasks" -> "check-square";
            case "courses" -> "book-open";
            case "folder" -> "folder";
            case "terminal" -> "terminal";
            case "serial" -> "cpu";
            case "book" -> "bookmark";
            case "calendar" -> "calendar";
            case "wallet" -> "credit-card";
            case "cloud" -> "cloud";
            case "play" -> "youtube";
            case "bot" -> "message-circle";
            default -> "globe";
        };
    }
}
