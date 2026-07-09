package com.sn.datechecker.app.views;

import com.sn.datechecker.app.AppConfig;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Base64;

/**
 * Connection setup screen — configure ServiceNow instance URL and credentials.
 */
public class ConnectionView {

    private final AppConfig config;
    private final VBox view;

    private final TextField instanceUrlField;
    private final TextField usernameField;
    private final PasswordField passwordField;
    private final Label statusLabel;

    // Tracker fields
    private final TextField trackerUrlField;
    private final TextField trackerUsernameField;
    private final PasswordField trackerPasswordField;
    private final TextField trackerTableField;

    public ConnectionView(AppConfig config) {
        this.config = config;
        this.view = new VBox(20);
        view.setPadding(new Insets(10));
        view.setAlignment(Pos.TOP_LEFT);

        // ── Test Instance Section ──
        Label header = new Label("Test Instance Connection");
        header.getStyleClass().add("section-header");

        Label desc = new Label("Configure the ServiceNow instance to scan for date format issues.");
        desc.getStyleClass().add("section-desc");
        desc.setWrapText(true);

        instanceUrlField = new TextField(config.get(AppConfig.INSTANCE_URL));
        instanceUrlField.setPromptText("https://your-instance.service-now.com");

        usernameField = new TextField(config.get(AppConfig.USERNAME));
        usernameField.setPromptText("admin");

        passwordField = new PasswordField();
        passwordField.setText(config.get(AppConfig.PASSWORD));
        passwordField.setPromptText("Password");

        // Visible password field + toggle
        TextField passwordVisible = new TextField(config.get(AppConfig.PASSWORD));
        passwordVisible.setPromptText("Password");
        passwordVisible.setVisible(false);
        passwordVisible.setManaged(false);

        // Bind both fields together
        passwordField.textProperty().addListener((o, ov, nv) -> passwordVisible.setText(nv));
        passwordVisible.textProperty().addListener((o, ov, nv) -> passwordField.setText(nv));

        StackPane passwordStack = new StackPane(passwordField, passwordVisible);

        CheckBox showPass = new CheckBox("Show");
        showPass.setOnAction(e -> {
            boolean show = showPass.isSelected();
            passwordField.setVisible(!show);
            passwordField.setManaged(!show);
            passwordVisible.setVisible(show);
            passwordVisible.setManaged(show);
        });

        HBox passwordRow = new HBox(8, passwordStack, showPass);
        passwordRow.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        HBox.setHgrow(passwordStack, Priority.ALWAYS);

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(10);
        grid.add(new Label("Instance URL"), 0, 0);
        grid.add(instanceUrlField, 1, 0);
        grid.add(new Label("Username"), 0, 1);
        grid.add(usernameField, 1, 1);
        grid.add(new Label("Password"), 0, 2);
        grid.add(passwordRow, 1, 2);

        ColumnConstraints labelCol = new ColumnConstraints(120);
        ColumnConstraints fieldCol = new ColumnConstraints();
        fieldCol.setHgrow(Priority.ALWAYS);
        grid.getColumnConstraints().addAll(labelCol, fieldCol);

        // ── Tracker Section ──
        Label trackerHeader = new Label("i18n Tracker Connection");
        trackerHeader.getStyleClass().add("section-header");

        Label trackerDesc = new Label("Configure the i18n Global Tracker instance for bug reporting.");
        trackerDesc.getStyleClass().add("section-desc");
        trackerDesc.setWrapText(true);

        trackerUrlField = new TextField(config.get(AppConfig.TRACKER_URL));
        trackerUrlField.setPromptText("https://i18ntest.service-now.com");

        trackerUsernameField = new TextField(config.get(AppConfig.TRACKER_USERNAME));
        trackerUsernameField.setPromptText("user@servicenow.com");

        trackerPasswordField = new PasswordField();
        trackerPasswordField.setText(config.get(AppConfig.TRACKER_PASSWORD));

        trackerTableField = new TextField(config.get(AppConfig.TRACKER_TABLE));
        trackerTableField.setPromptText("x_all_language_tra_all_language_tracker");

        GridPane trackerGrid = new GridPane();
        trackerGrid.setHgap(12);
        trackerGrid.setVgap(10);
        trackerGrid.add(new Label("Tracker URL"), 0, 0);
        trackerGrid.add(trackerUrlField, 1, 0);
        trackerGrid.add(new Label("Username"), 0, 1);
        trackerGrid.add(trackerUsernameField, 1, 1);
        trackerGrid.add(new Label("Password"), 0, 2);
        trackerGrid.add(trackerPasswordField, 1, 2);
        trackerGrid.add(new Label("Table"), 0, 3);
        trackerGrid.add(trackerTableField, 1, 3);
        trackerGrid.getColumnConstraints().addAll(
                new ColumnConstraints(120),
                new ColumnConstraints() {{ setHgrow(Priority.ALWAYS); }}
        );

        // Buttons
        Button testBtn = new Button("🔌 Test Connection");
        testBtn.getStyleClass().add("btn-primary");
        testBtn.setOnAction(e -> testConnection());

        Button saveBtn = new Button("💾 Save Configuration");
        saveBtn.getStyleClass().add("btn-success");
        saveBtn.setOnAction(e -> saveConfig());

        statusLabel = new Label();
        statusLabel.getStyleClass().add("status-label");

        HBox buttons = new HBox(12, testBtn, saveBtn, statusLabel);
        buttons.setAlignment(Pos.CENTER_LEFT);

        view.getChildren().addAll(
                header, desc, grid,
                new Separator(),
                trackerHeader, trackerDesc, trackerGrid,
                new Separator(),
                buttons
        );
    }

    private void testConnection() {
        String url = instanceUrlField.getText().trim();
        if (url.isBlank()) {
            statusLabel.setText("❌ Please enter an instance URL");
            statusLabel.setStyle("-fx-text-fill: #c62828;");
            return;
        }
        statusLabel.setText("⏳ Testing...");
        statusLabel.setStyle("-fx-text-fill: #1565c0;");

        new Thread(() -> {
            try {
                String testUrl = url.endsWith("/") ? url + "api/now/table/sys_properties?sysparm_limit=1"
                        : url + "/api/now/table/sys_properties?sysparm_limit=1";
                HttpURLConnection conn = (HttpURLConnection) new URL(testUrl).openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("Accept", "application/json");
                String auth = usernameField.getText() + ":" + passwordField.getText();
                conn.setRequestProperty("Authorization", "Basic " +
                        Base64.getEncoder().encodeToString(auth.getBytes()));
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);
                int code = conn.getResponseCode();
                conn.disconnect();

                javafx.application.Platform.runLater(() -> {
                    if (code == 200) {
                        statusLabel.setText("✅ Connected successfully!");
                        statusLabel.setStyle("-fx-text-fill: #2e7d32;");
                    } else {
                        statusLabel.setText("⚠️ HTTP " + code + " — check credentials");
                        statusLabel.setStyle("-fx-text-fill: #e65100;");
                    }
                });
            } catch (Exception ex) {
                javafx.application.Platform.runLater(() -> {
                    statusLabel.setText("❌ " + ex.getMessage());
                    statusLabel.setStyle("-fx-text-fill: #c62828;");
                });
            }
        }).start();
    }

    private void saveConfig() {
        config.set(AppConfig.INSTANCE_URL, instanceUrlField.getText().trim());
        config.set(AppConfig.USERNAME, usernameField.getText().trim());
        config.set(AppConfig.PASSWORD, passwordField.getText());
        config.set(AppConfig.TRACKER_URL, trackerUrlField.getText().trim());
        config.set(AppConfig.TRACKER_USERNAME, trackerUsernameField.getText().trim());
        config.set(AppConfig.TRACKER_PASSWORD, trackerPasswordField.getText());
        config.set(AppConfig.TRACKER_TABLE, trackerTableField.getText().trim());
        config.save();
        statusLabel.setText("✅ Configuration saved!");
        statusLabel.setStyle("-fx-text-fill: #2e7d32;");
    }

    public Node getView() {
        return view;
    }
}
