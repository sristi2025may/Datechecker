package com.sn.datechecker.app.views;

import com.sn.datechecker.app.AppConfig;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.DirectoryChooser;

import java.io.File;
import java.util.Arrays;
import java.util.List;

/**
 * Scan configuration screen — locale, pages, browser settings.
 */
public class ScanConfigView {

    private final AppConfig config;
    private final VBox view;

    // ServiceNow Language Pack — 24 supported languages + 3 SN alias codes
    private static final String[] LOCALES = {
            "ar",       // Arabic
            "pt-BR",    // Brazilian Portuguese (SN alias: pb)
            "zh-CN",    // Chinese (Simplified)
            "zh-Hant",  // Chinese (Traditional) (SN alias: zt)
            "cs",       // Czech
            "nl",       // Dutch
            "fi",       // Finnish
            "fr",       // French
            "fr-CA",    // French Canadian (SN alias: fq)
            "de",       // German
            "he",       // Hebrew
            "hu",       // Hungarian
            "it",       // Italian
            "ja",       // Japanese
            "ko",       // Korean
            "nb",       // Norwegian
            "pl",       // Polish
            "pt",       // Portuguese
            "ru",       // Russian
            "es",       // Spanish
            "sv",       // Swedish
            "th",       // Thai
            "tr"        // Turkish
    };

    public ScanConfigView(AppConfig config) {
        this.config = config;
        this.view = new VBox(16);
        view.setPadding(new Insets(10));

        Label header = new Label("Scan Configuration");
        header.getStyleClass().add("section-header");

        // ── Locale ──
        ComboBox<String> localePicker = new ComboBox<>(FXCollections.observableArrayList(LOCALES));
        localePicker.setValue(config.get(AppConfig.LOCALE));
        localePicker.setPrefWidth(200);
        HBox localeRow = new HBox(12, new Label("Target Locale:"), localePicker);
        localeRow.setAlignment(Pos.CENTER_LEFT);

        // ── Pages ──
        Label pagesLabel = new Label("Pages to Scan");
        pagesLabel.getStyleClass().add("field-label");
        Label pagesDesc = new Label("Enter full URLs, one per line.");
        pagesDesc.getStyleClass().add("section-desc");
        pagesDesc.setWrapText(true);

        TextArea pagesArea = new TextArea();
        pagesArea.setPrefRowCount(6);
        pagesArea.setPromptText("https://your-instance.service-now.com/now/cmdb/home");
        pagesArea.setWrapText(true);
        List<String> existing = config.getPages();
        if (!existing.isEmpty()) pagesArea.setText(String.join("\n", existing));

        // ── Browser ──
        Label browserLabel = new Label("Browser Settings");
        browserLabel.getStyleClass().add("field-label");

        ComboBox<String> browserPicker = new ComboBox<>(
                FXCollections.observableArrayList("chrome", "firefox", "edge"));
        browserPicker.setValue(config.get(AppConfig.BROWSER));

        CheckBox headlessCheck = new CheckBox("Headless mode");
        headlessCheck.setSelected(Boolean.parseBoolean(config.get(AppConfig.HEADLESS)));

        HBox browserRow = new HBox(12, new Label("Browser:"), browserPicker, headlessCheck);
        browserRow.setAlignment(Pos.CENTER_LEFT);

        // ── Timing ──
        TextField timeoutField = new TextField(config.get(AppConfig.PAGE_TIMEOUT));
        timeoutField.setPrefWidth(80);
        TextField delayField = new TextField(config.get(AppConfig.SCAN_DELAY));
        delayField.setPrefWidth(80);

        HBox timingRow = new HBox(20,
                new HBox(8, new Label("Timeout (sec):"), timeoutField),
                new HBox(8, new Label("Scan delay (ms):"), delayField));
        timingRow.setAlignment(Pos.CENTER_LEFT);

        // ── Extension ──
        TextField extensionField = new TextField(config.get(AppConfig.EXTENSION_PATH));
        extensionField.setPromptText("Path to sn-date-checker extension folder");
        HBox.setHgrow(extensionField, Priority.ALWAYS);

        Button browseBtn = new Button("Browse...");
        browseBtn.setOnAction(e -> {
            DirectoryChooser dc = new DirectoryChooser();
            dc.setTitle("Select Chrome Extension Folder");
            File dir = dc.showDialog(view.getScene().getWindow());
            if (dir != null) extensionField.setText(dir.getAbsolutePath());
        });
        HBox extRow = new HBox(8, extensionField, browseBtn);
        extRow.setAlignment(Pos.CENTER_LEFT);

        // ── Save ──
        Label statusLabel = new Label();
        Button saveBtn = new Button("💾 Save Configuration");
        saveBtn.getStyleClass().add("btn-success");
        saveBtn.setOnAction(e -> {
            config.set(AppConfig.LOCALE, localePicker.getValue());
            String rawPages = pagesArea.getText().trim();
            if (!rawPages.isBlank()) {
                List<String> pages = Arrays.stream(rawPages.split("\\n"))
                        .map(String::trim).filter(s -> !s.isBlank()).toList();
                config.setPages(pages);
            } else {
                config.setPages(List.of());
            }
            config.set(AppConfig.BROWSER, browserPicker.getValue());
            config.set(AppConfig.HEADLESS, String.valueOf(headlessCheck.isSelected()));
            config.set(AppConfig.PAGE_TIMEOUT, timeoutField.getText().trim());
            config.set(AppConfig.SCAN_DELAY, delayField.getText().trim());
            config.set(AppConfig.EXTENSION_PATH, extensionField.getText().trim());
            config.save();
            statusLabel.setText("✅ Saved!");
            statusLabel.setStyle("-fx-text-fill: #2e7d32;");
        });

        HBox saveRow = new HBox(12, saveBtn, statusLabel);
        saveRow.setAlignment(Pos.CENTER_LEFT);

        view.getChildren().addAll(
                header,
                localeRow, new Separator(),
                pagesLabel, pagesDesc, pagesArea, new Separator(),
                browserLabel, browserRow, timingRow, new Separator(),
                new Label("Chrome Extension (optional)"), extRow, new Separator(),
                saveRow
        );
    }

    public Node getView() { return view; }
}
