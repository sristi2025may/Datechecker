package com.sn.datechecker.app;

import com.sn.datechecker.app.views.*;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;

/**
 * Main application layout with sidebar navigation and content area.
 */
public class MainView {

    private final BorderPane root;
    private final StackPane contentArea;
    private final AppConfig config;
    private final ScanService scanService;

    // Views
    private final ConnectionView connectionView;
    private final ScanConfigView scanConfigView;
    private final ScanRunnerView scanRunnerView;
    private final ResultsView resultsView;
    private final SettingsView settingsView;

    private ToggleButton activeButton;

    public MainView(Stage stage) {
        this.config = new AppConfig();
        this.scanService = new ScanService(config);
        this.root = new BorderPane();

        // Create views
        this.connectionView = new ConnectionView(config);
        this.scanConfigView = new ScanConfigView(config);
        this.resultsView = new ResultsView(config, stage);
        this.scanRunnerView = new ScanRunnerView(config, scanService, resultsView);
        this.settingsView = new SettingsView(config);

        // Sidebar
        VBox sidebar = createSidebar();
        sidebar.getStyleClass().add("sidebar");

        // Content
        this.contentArea = new StackPane();
        contentArea.getStyleClass().add("content-area");
        contentArea.setPadding(new Insets(20));

        root.setLeft(sidebar);
        root.setCenter(contentArea);

        // Show connection view by default
        showView(connectionView.getView());
    }

    private VBox createSidebar() {
        VBox sidebar = new VBox(4);
        sidebar.setPrefWidth(220);
        sidebar.setPadding(new Insets(16, 12, 16, 12));
        sidebar.setAlignment(Pos.TOP_CENTER);

        // App title
        Label title = new Label("🗓 Date Checker");
        title.getStyleClass().add("sidebar-title");
        title.setMaxWidth(Double.MAX_VALUE);
        title.setAlignment(Pos.CENTER);

        Label subtitle = new Label("ServiceNow i18n Tool");
        subtitle.getStyleClass().add("sidebar-subtitle");
        subtitle.setMaxWidth(Double.MAX_VALUE);
        subtitle.setAlignment(Pos.CENTER);

        Separator sep = new Separator();
        sep.setPadding(new Insets(8, 0, 8, 0));

        ToggleGroup navGroup = new ToggleGroup();

        ToggleButton btnConnection = createNavButton("🔗  Connection", navGroup, connectionView.getView());
        ToggleButton btnScanConfig = createNavButton("⚙️  Scan Config", navGroup, scanConfigView.getView());
        ToggleButton btnRun        = createNavButton("▶️  Run Scan", navGroup, scanRunnerView.getView());
        ToggleButton btnResults    = createNavButton("📊  Results", navGroup, resultsView.getView());
        ToggleButton btnSettings   = createNavButton("🔧  Settings", navGroup, settingsView.getView());

        btnConnection.setSelected(true);
        activeButton = btnConnection;

        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);

        Label version = new Label("v1.0.0");
        version.getStyleClass().add("sidebar-version");

        sidebar.getChildren().addAll(
                title, subtitle, sep,
                btnConnection, btnScanConfig, btnRun, btnResults,
                spacer,
                btnSettings, version
        );

        return sidebar;
    }

    private ToggleButton createNavButton(String text, ToggleGroup group, Node targetView) {
        ToggleButton btn = new ToggleButton(text);
        btn.setToggleGroup(group);
        btn.getStyleClass().add("nav-button");
        btn.setMaxWidth(Double.MAX_VALUE);
        btn.setAlignment(Pos.CENTER_LEFT);

        btn.setOnAction(e -> {
            if (btn.isSelected()) {
                activeButton = btn;
                showView(targetView);
            } else {
                // Prevent deselection
                btn.setSelected(true);
            }
        });

        return btn;
    }

    private void showView(Node view) {
        contentArea.getChildren().setAll(view);
    }

    public BorderPane getRoot() {
        return root;
    }
}
