package com.sn.datechecker.app.views;

import com.sn.datechecker.app.AppConfig;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.DirectoryChooser;

import java.io.File;

/**
 * Settings screen — report/screenshot directories, preferences.
 */
public class SettingsView {

    private final AppConfig config;
    private final VBox view;

    public SettingsView(AppConfig config) {
        this.config = config;
        this.view = new VBox(16);
        view.setPadding(new Insets(10));

        Label header = new Label("Settings");
        header.getStyleClass().add("section-header");

        // ── Report Directory ──
        Label reportLabel = new Label("Report Output Directory");
        reportLabel.getStyleClass().add("field-label");

        TextField reportDirField = new TextField(config.get(AppConfig.REPORT_DIR));
        HBox.setHgrow(reportDirField, Priority.ALWAYS);

        Button reportBrowse = new Button("Browse...");
        reportBrowse.setOnAction(e -> {
            DirectoryChooser dc = new DirectoryChooser();
            dc.setTitle("Select Report Directory");
            File dir = dc.showDialog(view.getScene().getWindow());
            if (dir != null) reportDirField.setText(dir.getAbsolutePath());
        });

        HBox reportRow = new HBox(8, reportDirField, reportBrowse);

        // ── Screenshot Directory ──
        Label ssLabel = new Label("Screenshot Directory");
        ssLabel.getStyleClass().add("field-label");

        TextField ssDirField = new TextField(config.get(AppConfig.SCREENSHOT_DIR));
        HBox.setHgrow(ssDirField, Priority.ALWAYS);

        Button ssBrowse = new Button("Browse...");
        ssBrowse.setOnAction(e -> {
            DirectoryChooser dc = new DirectoryChooser();
            dc.setTitle("Select Screenshot Directory");
            File dir = dc.showDialog(view.getScene().getWindow());
            if (dir != null) ssDirField.setText(dir.getAbsolutePath());
        });

        HBox ssRow = new HBox(8, ssDirField, ssBrowse);

        // ── Auto Report ──
        CheckBox autoReport = new CheckBox("Automatically report bugs to tracker after scan");
        autoReport.setSelected(Boolean.parseBoolean(config.get(AppConfig.TRACKER_AUTO)));

        // ── About ──
        Label aboutHeader = new Label("About");
        aboutHeader.getStyleClass().add("field-label");

        Label aboutText = new Label(
                "ServiceNow Date Format Checker v1.0.0\n"
                + "Selenium-based i18n date validation tool.\n\n"
                + "Detects non-localized date/time formats on ServiceNow pages\n"
                + "and reports them to the i18n Global Tracker.\n\n"
                + "Supports 30+ locales including CJK, RTL, and European languages."
        );
        aboutText.setWrapText(true);
        aboutText.getStyleClass().add("section-desc");

        // ── Save ──
        Label statusLabel = new Label();
        Button saveBtn = new Button("💾 Save Settings");
        saveBtn.getStyleClass().add("btn-success");
        saveBtn.setOnAction(e -> {
            config.set(AppConfig.REPORT_DIR, reportDirField.getText().trim());
            config.set(AppConfig.SCREENSHOT_DIR, ssDirField.getText().trim());
            config.set(AppConfig.TRACKER_AUTO, String.valueOf(autoReport.isSelected()));
            config.save();
            statusLabel.setText("✅ Settings saved!");
            statusLabel.setStyle("-fx-text-fill: #2e7d32;");
        });

        HBox saveRow = new HBox(12, saveBtn, statusLabel);
        saveRow.setAlignment(Pos.CENTER_LEFT);

        view.getChildren().addAll(
                header,
                reportLabel, reportRow,
                ssLabel, ssRow,
                new Separator(),
                autoReport,
                new Separator(),
                aboutHeader, aboutText,
                new Separator(),
                saveRow
        );
    }

    public Node getView() { return view; }
}
