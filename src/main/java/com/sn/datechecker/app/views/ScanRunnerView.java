package com.sn.datechecker.app.views;

import com.sn.datechecker.app.AppConfig;
import com.sn.datechecker.app.ScanService;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;

/**
 * Scan runner screen — start/stop scan, progress bar, live log output.
 */
public class ScanRunnerView {

    private final AppConfig config;
    private final ScanService scanService;
    private final ResultsView resultsView;
    private final VBox view;

    public ScanRunnerView(AppConfig config, ScanService scanService, ResultsView resultsView) {
        this.config = config;
        this.scanService = scanService;
        this.resultsView = resultsView;
        this.view = new VBox(16);
        view.setPadding(new Insets(10));

        Label header = new Label("Run Scan");
        header.getStyleClass().add("section-header");

        // ── Summary of what will be scanned ──
        Label summaryLabel = new Label();
        summaryLabel.getStyleClass().add("section-desc");
        summaryLabel.setWrapText(true);
        updateSummary(summaryLabel);

        // Refresh summary when view becomes visible
        view.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene != null) {
                view.visibleProperty().addListener((o, wasVisible, isVisible) -> {
                    if (isVisible) updateSummary(summaryLabel);
                });
            }
        });

        // ── Buttons ──
        Button startBtn = new Button("▶️  Start Scan");
        startBtn.getStyleClass().add("btn-primary");
        startBtn.setPrefWidth(160);
        startBtn.setPrefHeight(40);

        Button cancelBtn = new Button("⏹  Cancel");
        cancelBtn.getStyleClass().add("btn-danger");
        cancelBtn.setPrefWidth(120);
        cancelBtn.setPrefHeight(40);
        cancelBtn.setDisable(true);

        HBox buttonRow = new HBox(12, startBtn, cancelBtn);
        buttonRow.setAlignment(Pos.CENTER_LEFT);

        // ── Progress ──
        ProgressBar progressBar = new ProgressBar(0);
        progressBar.setMaxWidth(Double.MAX_VALUE);
        progressBar.setPrefHeight(24);

        Label statusLabel = new Label("Ready");
        statusLabel.getStyleClass().add("status-label");

        HBox progressRow = new HBox(12, progressBar, statusLabel);
        progressRow.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(progressBar, Priority.ALWAYS);

        // ── Log Output ──
        Label logLabel = new Label("Log Output");
        logLabel.getStyleClass().add("field-label");

        TextArea logArea = new TextArea();
        logArea.setEditable(false);
        logArea.setWrapText(true);
        logArea.getStyleClass().add("log-area");
        VBox.setVgrow(logArea, Priority.ALWAYS);

        // ── Bindings ──
        progressBar.progressProperty().bind(scanService.progressProperty());
        statusLabel.textProperty().bind(scanService.statusMessageProperty());

        scanService.logTextProperty().addListener((obs, oldVal, newVal) -> {
            logArea.setText(newVal);
            logArea.setScrollTop(Double.MAX_VALUE);
            logArea.positionCaret(newVal.length());
        });

        scanService.runningProperty().addListener((obs, wasRunning, isRunning) -> {
            startBtn.setDisable(isRunning);
            cancelBtn.setDisable(!isRunning);
            if (!isRunning) {
                // Pass issues to results view and refresh
                resultsView.setIssues(new java.util.ArrayList<>(scanService.getIssues()));
                resultsView.refresh();
            }
        });

        startBtn.setOnAction(e -> {
            updateSummary(summaryLabel);
            logArea.clear();
            scanService.startScan();
        });

        cancelBtn.setOnAction(e -> scanService.cancelScan());

        view.getChildren().addAll(
                header, summaryLabel, new Separator(),
                buttonRow, progressRow, new Separator(),
                logLabel, logArea
        );
    }

    private void updateSummary(Label label) {
        String locale = config.get(AppConfig.LOCALE);
        int pageCount = config.getPages().size();
        String instance = config.get(AppConfig.INSTANCE_URL);
        boolean headless = Boolean.parseBoolean(config.get(AppConfig.HEADLESS));
        label.setText(String.format(
                "Instance: %s  |  Locale: %s  |  Pages: %d  |  Browser: %s%s",
                instance.isBlank() ? "(not set)" : instance,
                locale, pageCount,
                config.get(AppConfig.BROWSER),
                headless ? " (headless)" : ""));
    }

    public Node getView() { return view; }
}
