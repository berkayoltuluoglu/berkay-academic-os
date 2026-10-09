package org.example;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.io.IOException;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class UartConsoleView {

    private static final int MAX_LOG_CHARS = 120_000;
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final SerialPortService serialPortService = new SerialPortService();
    private final ObservableList<String> portItems = FXCollections.observableArrayList();

    public VBox build() {
        ComboBox<String> portCombo = new ComboBox<>(portItems);
        portCombo.setPromptText("Port seç");
        portCombo.setPrefWidth(190);
        portCombo.setEditable(true);

        ComboBox<String> baudCombo = new ComboBox<>();
        baudCombo.getItems().addAll("9600", "19200", "38400", "57600", "115200", "230400", "460800", "921600");
        baudCombo.setValue("115200");
        baudCombo.setEditable(true);
        baudCombo.setPrefWidth(120);

        ComboBox<String> lineEndingCombo = new ComboBox<>();
        lineEndingCombo.getItems().addAll("Yok", "LF", "CR", "CRLF");
        lineEndingCombo.setValue("LF");
        lineEndingCombo.setPrefWidth(90);

        CheckBox timestampCheck = new CheckBox("Zaman damgası");
        timestampCheck.setSelected(true);

        CheckBox autoScrollCheck = new CheckBox("Otomatik kaydır");
        autoScrollCheck.setSelected(true);

        Button scanButton = new Button("Portları Tara");
        Button connectButton = new Button("Bağlan");
        Button disconnectButton = new Button("Kes");
        disconnectButton.setDisable(true);

        Label connectionLabel = new Label("Durum: Bağlı değil");
        connectionLabel.setStyle("-fx-font-weight: bold;");

        UiTheme.decorate(scanButton, "refresh-cw", "flat");
        UiTheme.decorate(connectButton, "power", "accent");
        UiTheme.decorate(disconnectButton, "x", "danger");
        connectionLabel.getStyleClass().add("baglanti-durumu");
        FlowPane topBar = new FlowPane(10, 8,
                new Label("Port"), portCombo, new Label("Baud"), baudCombo,
                scanButton, connectButton, disconnectButton);
        topBar.getStyleClass().addAll("panel", "uart-ust");

        TextArea logArea = new TextArea();
        logArea.setEditable(false);
        logArea.getStyleClass().add("terminal");
        logArea.setWrapText(true);
        logArea.setPromptText("Gelen ve gönderilen UART verileri burada görüntülenecek...");
        VBox.setVgrow(logArea, Priority.ALWAYS);

        TextField inputField = new TextField();
        inputField.setPromptText("Gönderilecek veri…");
        inputField.getStyleClass().add("terminal-girdi");
        HBox.setHgrow(inputField, Priority.ALWAYS);

        Button sendButton = new Button("Gönder");
        sendButton.setDisable(true);
        Button clearButton = new Button("Log Temizle");

        UiTheme.decorate(sendButton, "send", "accent");
        UiTheme.decorate(clearButton, "trash-2", "flat");
        HBox bottomBar = new HBox(10, inputField, lineEndingCombo, sendButton);
        bottomBar.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        FlowPane options = new FlowPane(14, 8, timestampCheck, autoScrollCheck, clearButton, connectionLabel);
        options.getStyleClass().add("console-options");
        VBox root = new VBox(12, topBar, options, logArea, bottomBar);

        Runnable updateConnectionUi = () -> {
            boolean connected = serialPortService.isConnected();
            connectionLabel.pseudoClassStateChanged(javafx.css.PseudoClass.getPseudoClass("bagli"), connected);
            connectButton.setDisable(connected);
            disconnectButton.setDisable(!connected);
            sendButton.setDisable(!connected);
            scanButton.setDisable(connected);
            portCombo.setDisable(connected);
            baudCombo.setDisable(connected);

            if (connected) {
                connectionLabel.setText("Durum: Bağlı -> "
                        + serialPortService.getConnectedPortName()
                        + " @ "
                        + serialPortService.getConnectedBaudRate());
            } else {
                connectionLabel.setText("Durum: Bağlı değil");
            }
        };

        Runnable scanPorts = () -> {
            scanButton.setDisable(true);
            appendLog(logArea, "SİSTEM", "Portlar taranıyor...", timestampCheck.isSelected(), autoScrollCheck.isSelected());

            Task<List<String>> scanTask = new Task<>() {
                @Override
                protected List<String> call() {
                    return serialPortService.listAvailablePorts();
                }
            };

            scanTask.setOnSucceeded(event -> {
                List<String> ports = scanTask.getValue();
                portItems.setAll(ports);
                if (!portItems.isEmpty() && (portCombo.getValue() == null || portCombo.getValue().isBlank())) {
                    portCombo.getSelectionModel().selectFirst();
                }
                appendLog(logArea, "SİSTEM", ports.size() + " port bulundu.", timestampCheck.isSelected(), autoScrollCheck.isSelected());
                updateConnectionUi.run();
            });

            scanTask.setOnFailed(event -> {
                appendLog(logArea, "HATA", "Port tarama başarısız: " + safeMessage(scanTask.getException()), timestampCheck.isSelected(), autoScrollCheck.isSelected());
                updateConnectionUi.run();
            });

            Thread thread = new Thread(scanTask, "uart-port-scan");
            thread.setDaemon(true);
            thread.start();
        };

        scanButton.setOnAction(e -> scanPorts.run());

        connectButton.setOnAction(e -> {
            String selectedPort = portCombo.getEditor().getText();
            int baudRate;

            if (selectedPort == null || selectedPort.isBlank()) {
                appendLog(logArea, "UART", "Önce bir port seç.", timestampCheck.isSelected(), autoScrollCheck.isSelected());
                return;
            }

            try {
                baudRate = parseBaudRate(baudCombo.getEditor().getText());
            } catch (IllegalArgumentException ex) {
                appendLog(logArea, "UART HATA", ex.getMessage(), timestampCheck.isSelected(), autoScrollCheck.isSelected());
                return;
            }

            connectButton.setDisable(true);
            appendLog(logArea, "UART", selectedPort + " portuna bağlanılıyor...", timestampCheck.isSelected(), autoScrollCheck.isSelected());

            Task<Void> connectTask = new Task<>() {
                @Override
                protected Void call() throws Exception {
                    serialPortService.connect(
                            selectedPort.trim(),
                            baudRate,
                            text -> Platform.runLater(() -> appendRaw(logArea, text, autoScrollCheck.isSelected())),
                            error -> Platform.runLater(() -> {
                                appendLog(logArea, "UART HATA", error, timestampCheck.isSelected(), autoScrollCheck.isSelected());
                                updateConnectionUi.run();
                            })
                    );
                    return null;
                }
            };

            connectTask.setOnSucceeded(event -> {
                appendLog(logArea, "UART", "Port açıldı: " + selectedPort.trim(), timestampCheck.isSelected(), autoScrollCheck.isSelected());
                updateConnectionUi.run();
            });

            connectTask.setOnFailed(event -> {
                appendLog(logArea, "UART HATA", safeMessage(connectTask.getException()), timestampCheck.isSelected(), autoScrollCheck.isSelected());
                updateConnectionUi.run();
            });

            Thread thread = new Thread(connectTask, "uart-connect");
            thread.setDaemon(true);
            thread.start();
        });

        disconnectButton.setOnAction(e -> {
            try {
                serialPortService.disconnect();
                appendLog(logArea, "UART", "Bağlantı kapatıldı.", timestampCheck.isSelected(), autoScrollCheck.isSelected());
            } catch (IOException ex) {
                appendLog(logArea, "UART HATA", ex.getMessage(), timestampCheck.isSelected(), autoScrollCheck.isSelected());
            } finally {
                updateConnectionUi.run();
            }
        });

        Runnable sendCurrentText = () -> {
            String text = inputField.getText();
            if (text == null || text.isEmpty()) {
                return;
            }

            try {
                String payload = text + resolveLineEnding(lineEndingCombo.getValue());
                serialPortService.send(payload);
                appendLog(logArea, "TX", text, timestampCheck.isSelected(), autoScrollCheck.isSelected());
                inputField.clear();
            } catch (IOException ex) {
                appendLog(logArea, "UART HATA", ex.getMessage(), timestampCheck.isSelected(), autoScrollCheck.isSelected());
                updateConnectionUi.run();
            }
        };

        sendButton.setOnAction(e -> sendCurrentText.run());
        inputField.setOnAction(e -> sendCurrentText.run());
        clearButton.setOnAction(e -> logArea.clear());

        scanPorts.run();
        updateConnectionUi.run();
        return root;
    }

    private int parseBaudRate(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Baud rate boş olamaz.");
        }

        try {
            int baudRate = Integer.parseInt(value.trim());
            if (baudRate <= 0) {
                throw new IllegalArgumentException("Baud rate pozitif olmalı.");
            }
            return baudRate;
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Baud rate sayı olmalı: " + value);
        }
    }

    private String resolveLineEnding(String selected) {
        if ("CR".equals(selected)) {
            return "\r";
        }
        if ("CRLF".equals(selected)) {
            return "\r\n";
        }
        if ("Yok".equals(selected)) {
            return "";
        }
        return "\n";
    }

    private void appendLog(TextArea logArea, String tag, String message, boolean timestamp, boolean autoScroll) {
        String prefix = timestamp ? "[" + LocalTime.now().format(TIME_FORMATTER) + "] " : "";
        appendRaw(logArea, prefix + "[" + tag + "] " + message + "\n", autoScroll);
    }

    private void appendRaw(TextArea logArea, String text, boolean autoScroll) {
        if (text == null || text.isEmpty()) {
            return;
        }

        logArea.appendText(text);
        if (logArea.getLength() > MAX_LOG_CHARS) {
            int removeCount = Math.min(logArea.getLength() - MAX_LOG_CHARS, logArea.getLength());
            logArea.deleteText(0, removeCount);
        }
        if (autoScroll) {
            logArea.positionCaret(logArea.getLength());
        }
    }

    private String safeMessage(Throwable throwable) {
        if (throwable == null) {
            return "Bilinmeyen hata";
        }
        String message = throwable.getMessage();
        return message == null || message.isBlank() ? throwable.getClass().getSimpleName() : message;
    }
}