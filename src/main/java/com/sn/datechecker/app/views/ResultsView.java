package com.sn.datechecker.app.views;

import com.sn.datechecker.app.AppConfig;
import com.sn.datechecker.app.ScanService;
import com.sn.datechecker.model.DateIssue;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.*;
import javafx.scene.web.WebView;
import javafx.stage.Stage;

import java.awt.Desktop;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.file.*;
import java.util.Base64;
import java.util.List;
import java.util.stream.Stream;

/**
 * Results dashboard — issues table, report viewer, bug reporting.
 */
public class ResultsView {

    private final AppConfig config;
    private final Stage stage;
    private final VBox view;
    private final TableView<DateIssue> table;
    private final ObservableList<DateIssue> issueList = FXCollections.observableArrayList();
    private final Label summaryLabel;
    private WebView reportWebView;
    private String latestReportPath;

    public ResultsView(AppConfig config, Stage stage) {
        this.config = config;
        this.stage = stage;
        this.view = new VBox(12);
        view.setPadding(new Insets(10));

        Label header = new Label("Results Dashboard");
        header.getStyleClass().add("section-header");

        summaryLabel = new Label("No scan results yet. Run a scan first.");
        summaryLabel.getStyleClass().add("section-desc");
        summaryLabel.setWrapText(true);

        // ── Issues Table ──
        table = new TableView<>(issueList);
        table.setPlaceholder(new Label("No issues found"));
        table.getStyleClass().add("results-table");

        TableColumn<DateIssue, String> colFound = new TableColumn<>("Found");
        colFound.setCellValueFactory(new PropertyValueFactory<>("found"));
        colFound.setPrefWidth(160);

        TableColumn<DateIssue, String> colReason = new TableColumn<>("Reason");
        colReason.setCellValueFactory(new PropertyValueFactory<>("reason"));
        colReason.setPrefWidth(250);

        TableColumn<DateIssue, String> colExpected = new TableColumn<>("Expected");
        colExpected.setCellValueFactory(new PropertyValueFactory<>("expected"));
        colExpected.setPrefWidth(160);

        TableColumn<DateIssue, String> colType = new TableColumn<>("Type");
        colType.setCellValueFactory(new PropertyValueFactory<>("type"));
        colType.setPrefWidth(100);

        TableColumn<DateIssue, String> colPage = new TableColumn<>("Page");
        colPage.setCellValueFactory(cd -> {
            String url = cd.getValue().getPageUrl();
            if (url != null && url.length() > 60) url = "..." + url.substring(url.length() - 57);
            return new SimpleStringProperty(url);
        });
        colPage.setPrefWidth(200);

        table.getColumns().addAll(List.of(colFound, colReason, colExpected, colType, colPage));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        VBox.setVgrow(table, Priority.ALWAYS);

        // ── Action Buttons ──
        Button openReportBtn = new Button("📄 Open HTML Report");
        openReportBtn.getStyleClass().add("btn-primary");
        openReportBtn.setOnAction(e -> openLatestReport());

        Button reportBugBtn = new Button("🐛 Report Selected Bug");
        reportBugBtn.getStyleClass().add("btn-warning");
        reportBugBtn.setOnAction(e -> reportSelectedBug());

        Button refreshBtn = new Button("🔄 Refresh");
        refreshBtn.setOnAction(e -> refresh());

        HBox actions = new HBox(12, openReportBtn, reportBugBtn, refreshBtn);
        actions.setAlignment(Pos.CENTER_LEFT);

        // ── Report preview (tab pane) ──
        TabPane tabs = new TabPane();
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        Tab tableTab = new Tab("Issues Table", table);
        Tab reportTab = new Tab("Report Preview");

        reportWebView = new WebView();
        reportTab.setContent(reportWebView);

        tabs.getTabs().addAll(tableTab, reportTab);
        VBox.setVgrow(tabs, Priority.ALWAYS);

        view.getChildren().addAll(header, summaryLabel, actions, tabs);
    }

    public void refresh() {
        // Find latest report
        String reportDir = config.get(AppConfig.REPORT_DIR);
        try {
            Path dir = Paths.get(reportDir);
            if (Files.exists(dir)) {
                try (Stream<Path> files = Files.list(dir)) {
                    Path latest = files
                            .filter(f -> f.toString().endsWith(".html"))
                            .max((a, b) -> {
                                try { return Files.getLastModifiedTime(a).compareTo(Files.getLastModifiedTime(b)); }
                                catch (Exception ex) { return 0; }
                            })
                            .orElse(null);
                    if (latest != null) {
                        latestReportPath = latest.toString();
                        reportWebView.getEngine().load(latest.toUri().toString());
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Failed to load report: " + e.getMessage());
        }

        updateSummary();
    }

    public void setIssues(List<DateIssue> issues) {
        issueList.setAll(issues);
        updateSummary();
    }

    private void updateSummary() {
        if (issueList.isEmpty()) {
            summaryLabel.setText("No issues found in the latest scan.");
        } else {
            long pages = issueList.stream().map(DateIssue::getPageUrl).distinct().count();
            summaryLabel.setText(String.format("Found %d issue(s) across %d page(s).",
                    issueList.size(), pages));
        }
    }

    private void openLatestReport() {
        if (latestReportPath != null) {
            try {
                Desktop.getDesktop().browse(new File(latestReportPath).toURI());
            } catch (Exception e) {
                showAlert("Could not open report: " + e.getMessage());
            }
        } else {
            showAlert("No report available. Run a scan first.");
        }
    }

    private void reportSelectedBug() {
        DateIssue selected = table.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showAlert("Please select an issue from the table first.");
            return;
        }

        String trackerUrl = config.get(AppConfig.TRACKER_URL);
        String trackerTable = config.get(AppConfig.TRACKER_TABLE);
        if (trackerUrl.isBlank()) {
            showAlert("Tracker URL not configured. Go to Connection settings.");
            return;
        }

        String cleanUrl = trackerUrl.endsWith("/")
                ? trackerUrl.substring(0, trackerUrl.length() - 1) : trackerUrl;
        String locale = config.get(AppConfig.LOCALE);
        String shortDesc = "[" + locale.toUpperCase() + "] " + clean(selected.getReason());

        // Build pre-populated form URL
        String classicUrl = trackerTable + ".do?sys_id=-1"
                + "&sysparm_query=short_description=" + clean(shortDesc)
                + "^u_current=" + clean(selected.getFound())
                + "^u_expected=" + clean(selected.getExpected())
                + "^u_additional_details=" + clean(selected.getSuggestion())
                + "^u_steps_to_reproduce=" + clean(selected.getPageUrl())
                + "^direct_link=" + clean(selected.getPageUrl())
                + "&sysparm_stack=" + trackerTable + "_list.do";

        String formUrl = cleanUrl + "/now/nav/ui/classic/params/target/"
                + java.net.URLEncoder.encode(classicUrl, java.nio.charset.StandardCharsets.UTF_8);

        // Copy screenshot path to clipboard for easy attachment
        String screenshotPath = selected.getScreenshotPath();
        String screenshotMsg = "";
        if (screenshotPath != null && !screenshotPath.isBlank() && new File(screenshotPath).exists()) {
            javafx.scene.input.Clipboard clipboard = javafx.scene.input.Clipboard.getSystemClipboard();
            javafx.scene.input.ClipboardContent content = new javafx.scene.input.ClipboardContent();
            content.putString(screenshotPath);

            // Also put the screenshot image on the clipboard so user can paste it directly
            try {
                javafx.scene.image.Image img = new javafx.scene.image.Image(
                        new File(screenshotPath).toURI().toString());
                content.putImage(img);
            } catch (Exception ignored) {}

            clipboard.setContent(content);
            screenshotMsg = "\n\n📎 Screenshot copied to clipboard — paste it (Cmd+V) "
                    + "into the form's attachment area.\nPath: " + screenshotPath;
        }

        try {
            Desktop.getDesktop().browse(new URI(formUrl));
            showAlert("✅ Form opened with pre-populated fields." + screenshotMsg);
        } catch (Exception e) {
            showAlert("Could not open tracker: " + e.getMessage());
        }
    }

    private String clean(String s) {
        if (s == null) return "";
        return s.replaceAll("[\\^&=\\n\\r]", " ").replaceAll("\\s+", " ").trim();
    }

    private void showAlert(String msg) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION, msg, ButtonType.OK);
        alert.setHeaderText(null);
        alert.showAndWait();
    }

    public Node getView() { return view; }
}
