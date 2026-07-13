package com.sn.datechecker.app;

import com.sn.datechecker.driver.DriverFactory;
import com.sn.datechecker.model.DateIssue;
import com.sn.datechecker.report.ReportGenerator;
import com.sn.datechecker.scanner.DateFormatChecker;
import javafx.application.Platform;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeOptions;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Background scan service that runs Selenium scans and reports progress to the UI.
 */
public class ScanService {

    private final AppConfig config;
    private final ObservableList<DateIssue> issues = FXCollections.observableArrayList();
    private final StringProperty statusMessage = new SimpleStringProperty("Ready");
    private final DoubleProperty progress = new SimpleDoubleProperty(0);
    private final BooleanProperty running = new SimpleBooleanProperty(false);
    private final StringProperty logText = new SimpleStringProperty("");
    private final StringProperty reportPath = new SimpleStringProperty("");

    private final AtomicBoolean cancelRequested = new AtomicBoolean(false);
    private WebDriver driver;

    public ScanService(AppConfig config) {
        this.config = config;
    }

    public void startScan() {
        if (running.get()) return;

        cancelRequested.set(false);
        running.set(true);
        issues.clear();
        logText.set("");
        progress.set(0);
        reportPath.set("");

        Thread scanThread = new Thread(this::runScan, "DateChecker-Scan");
        scanThread.setDaemon(true);
        scanThread.start();
    }

    public void cancelScan() {
        cancelRequested.set(true);
        log("⏹ Cancel requested...");
    }

    private void runScan() {
        try {
            updateStatus("Initializing browser...");
            log("🚀 Starting scan...");

            String browser = config.get(AppConfig.BROWSER);
            boolean headless = Boolean.parseBoolean(config.get(AppConfig.HEADLESS));
            String locale = config.get(AppConfig.LOCALE);
            String instanceUrl = config.get(AppConfig.INSTANCE_URL);
            String username = config.get(AppConfig.USERNAME);
            String password = config.get(AppConfig.PASSWORD);
            List<String> pages = config.getPages();

            if (instanceUrl.isBlank() || pages.isEmpty()) {
                log("❌ Error: Instance URL and at least one page URL are required.");
                updateStatus("Error: Missing configuration");
                Platform.runLater(() -> running.set(false));
                return;
            }

            // Setup browser
            log("🌐 Launching " + browser + (headless ? " (headless)" : "") + "...");
            ChromeOptions options = new ChromeOptions();
            if (headless) options.addArguments("--headless=new");
            options.addArguments("--window-size=1920,1080");
            options.addArguments("--disable-gpu");
            options.addArguments("--no-sandbox");

            String extensionPath = config.get(AppConfig.EXTENSION_PATH);
            if (extensionPath != null && !extensionPath.isBlank()) {
                File ext = new File(extensionPath);
                if (ext.exists()) {
                    options.addArguments("--load-extension=" + ext.getAbsolutePath());
                    log("📦 Loaded extension: " + extensionPath);
                }
            }

            io.github.bonigarcia.wdm.WebDriverManager.chromedriver().setup();
            driver = new org.openqa.selenium.chrome.ChromeDriver(options);

            int timeout = Integer.parseInt(config.get(AppConfig.PAGE_TIMEOUT));
            driver.manage().timeouts().pageLoadTimeout(java.time.Duration.ofSeconds(timeout));

            // Login
            log("🔐 Logging in to " + instanceUrl + "...");
            log("   Username: '" + username + "', Password length: " + password.length());
            if (password.isEmpty()) {
                log("   ⚠️ WARNING: Password is empty! Check config.properties.");
            }
            updateStatus("Logging in...");
            driver.get(instanceUrl + "/login.do");
            Thread.sleep(3000);

            try {
                org.openqa.selenium.support.ui.WebDriverWait wait =
                        new org.openqa.selenium.support.ui.WebDriverWait(
                                driver, java.time.Duration.ofSeconds(10));

                org.openqa.selenium.WebElement userField = wait.until(
                        org.openqa.selenium.support.ui.ExpectedConditions.presenceOfElementLocated(
                                org.openqa.selenium.By.id("user_name")));
                userField.clear();
                userField.sendKeys(username);
                log("   Entered username: " + username);

                org.openqa.selenium.WebElement passField = driver.findElement(
                        org.openqa.selenium.By.id("user_password"));
                passField.clear();
                passField.sendKeys(password);
                log("   Entered password (" + password.length() + " chars)");

                // Click the login button instead of form submit
                try {
                    org.openqa.selenium.WebElement loginBtn = driver.findElement(
                            org.openqa.selenium.By.id("sysverb_login"));
                    loginBtn.click();
                } catch (Exception btnEx) {
                    passField.submit();
                }
                Thread.sleep(5000);

                // Verify login by checking if we're still on login page
                String currentUrl = driver.getCurrentUrl();
                if (currentUrl.contains("login.do") || currentUrl.contains("login_redirect")) {
                    log("   ⚠️ Still on login page — credentials may be wrong");
                } else {
                    log("   ✅ Login successful (redirected to: " +
                            currentUrl.substring(0, Math.min(80, currentUrl.length())) + ")");
                }
            } catch (Exception e) {
                log("⚠️ Login form not found, may already be logged in: " + e.getMessage());
            }

            if (cancelRequested.get()) { cleanup(); return; }

            // Set language — full 5-step approach for Polaris compatibility
            log("🌍 Setting language to: " + locale);
            updateStatus("Setting language to " + locale + "...");

            // Step 1: Classic UI with sysparm_language (sets session language)
            driver.get(instanceUrl + "/navpage.do?sysparm_language=" + locale);
            Thread.sleep(3000);
            log("   Step 1/5: Classic UI session language set");

            org.openqa.selenium.JavascriptExecutor js =
                    (org.openqa.selenium.JavascriptExecutor) driver;

            // Step 2: Update preferred_language on sys_user via REST API (Polaris reads this)
            try {
                Object result = js.executeScript(
                    "var token = '';" +
                    "try { token = typeof g_ck !== 'undefined' ? g_ck : ''; } catch(e) {}" +
                    "if (!token) try { var m = document.cookie.match(/ck=([^;]+)/); if(m) token = m[1]; } catch(e) {}" +
                    "var xhr = new XMLHttpRequest();" +
                    "xhr.open('GET', '/api/now/table/sys_user?sysparm_query=user_name=" + username +
                        "&sysparm_fields=sys_id&sysparm_limit=1', false);" +
                    "xhr.setRequestHeader('Accept', 'application/json');" +
                    "xhr.setRequestHeader('X-UserToken', token);" +
                    "xhr.send();" +
                    "if (xhr.status === 200) {" +
                    "  var data = JSON.parse(xhr.responseText);" +
                    "  if (data.result && data.result.length > 0) {" +
                    "    var uid = data.result[0].sys_id;" +
                    "    var xhr2 = new XMLHttpRequest();" +
                    "    xhr2.open('PATCH', '/api/now/table/sys_user/' + uid, false);" +
                    "    xhr2.setRequestHeader('Content-Type', 'application/json');" +
                    "    xhr2.setRequestHeader('Accept', 'application/json');" +
                    "    xhr2.setRequestHeader('X-UserToken', token);" +
                    "    xhr2.send(JSON.stringify({preferred_language: '" + locale + "'}));" +
                    "    return 'user_updated:' + xhr2.status;" +
                    "  }" +
                    "  return 'no_user_found';" +
                    "}" +
                    "return 'api_error:' + xhr.status;");
                log("   Step 2/5: sys_user preferred_language → " + result);
            } catch (Exception e) {
                log("   ⚠️ Step 2: Could not update preferred_language: " + e.getMessage());
            }

            // Step 3: Set UI user preference as fallback
            try {
                js.executeScript(
                    "var token = '';" +
                    "try { token = typeof g_ck !== 'undefined' ? g_ck : ''; } catch(e) {}" +
                    "if (!token) try { var m = document.cookie.match(/ck=([^;]+)/); if(m) token = m[1]; } catch(e) {}" +
                    "var xhr = new XMLHttpRequest();" +
                    "xhr.open('PUT', '/api/now/ui/user_preference/language', false);" +
                    "xhr.setRequestHeader('Content-Type', 'application/json');" +
                    "xhr.setRequestHeader('X-UserToken', token);" +
                    "xhr.send(JSON.stringify({value: '" + locale + "'}));");
                log("   Step 3/5: UI user preference set");
            } catch (Exception e) {
                log("   ⚠️ Step 3: Could not set UI preference: " + e.getMessage());
            }

            // Step 4: Navigate to Polaris home to apply changes
            driver.get(instanceUrl + "/now/nav/ui/home");
            Thread.sleep(5000);
            log("   Step 4/5: Navigated to Polaris home");

            // Step 5: Verify language
            String docLang = (String) js.executeScript(
                    "return document.documentElement.lang || ''");
            if (docLang != null && !docLang.isEmpty() && !docLang.startsWith("en")) {
                log("   Step 5/5: ✅ Language verified: " + docLang);
            } else {
                log("   Step 5/5: ⚠️ Language may not have changed (lang='" + docLang + "')");
                // Retry: navigate classic again
                driver.get(instanceUrl + "/navpage.do?sysparm_language=" + locale);
                Thread.sleep(3000);
                driver.get(instanceUrl + "/now/nav/ui/home");
                Thread.sleep(5000);
                docLang = (String) js.executeScript(
                        "return document.documentElement.lang || ''");
                log("   Retry result: lang='" + docLang + "'");
            }

            // Scan pages
            DateFormatChecker checker = new DateFormatChecker();
            List<DateIssue> allIssues = new ArrayList<>();
            int scanDelay = Integer.parseInt(config.get(AppConfig.SCAN_DELAY));

            for (int i = 0; i < pages.size(); i++) {
                if (cancelRequested.get()) { cleanup(); return; }

                String pageUrl = pages.get(i).trim();
                if (pageUrl.isBlank()) continue;

                double pct = (double) (i) / pages.size();
                Platform.runLater(() -> progress.set(pct));
                updateStatus("Scanning page " + (i + 1) + "/" + pages.size() + "...");
                log("📄 Scanning: " + pageUrl);

                driver.get(pageUrl);

                // Wait until page is fully loaded
                org.openqa.selenium.JavascriptExecutor pageJs =
                        (org.openqa.selenium.JavascriptExecutor) driver;

                // 1) Wait for document.readyState == "complete"
                org.openqa.selenium.support.ui.WebDriverWait pageWait =
                        new org.openqa.selenium.support.ui.WebDriverWait(
                                driver, java.time.Duration.ofSeconds(30));
                pageWait.until(d -> "complete".equals(
                        ((org.openqa.selenium.JavascriptExecutor) d)
                                .executeScript("return document.readyState")));
                log("   DOM ready");

                // 2) Initial wait for Polaris/React to start rendering
                Thread.sleep(5000);

                // 3) Wait for page content to stabilize (text length stops changing)
                int prevLen = 0;
                int stableCount = 0;
                for (int attempt = 0; attempt < 10; attempt++) {
                    Object lenObj = pageJs.executeScript(
                            "return (document.body ? document.body.innerText.length : 0)");
                    int curLen = lenObj != null ? ((Number) lenObj).intValue() : 0;
                    if (curLen > 0 && curLen == prevLen) {
                        stableCount++;
                        if (stableCount >= 2) break; // Content stable for 2 consecutive checks
                    } else {
                        stableCount = 0;
                    }
                    prevLen = curLen;
                    Thread.sleep(2000);
                }
                log("   Page content stabilized (text length: " + prevLen + " chars)");

                // 4) Extra settle for any remaining animations/lazy content
                Thread.sleep(Math.max(scanDelay, 2000));

                List<DateIssue> pageIssues = checker.scan(driver, locale);
                log("   Found " + pageIssues.size() + " issue(s)");

                // Highlight issues with red boxes, then take screenshot
                if (!pageIssues.isEmpty()) {
                    try {
                        // Highlight issues on page with red border boxes
                        checker.highlightIssues(driver, pageIssues);
                        log("   🔴 Highlighted " + pageIssues.size() + " issue(s) with red boxes");
                        Thread.sleep(500); // Let rendering settle

                        String screenshotDir = config.get(AppConfig.SCREENSHOT_DIR);
                        Files.createDirectories(Paths.get(screenshotDir));
                        File screenshot = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
                        String screenshotName = "page_" + (i + 1) + "_" + System.currentTimeMillis() + ".png";
                        Path dest = Paths.get(screenshotDir, screenshotName);
                        Files.copy(screenshot.toPath(), dest);
                        for (DateIssue issue : pageIssues) {
                            issue.setScreenshotPath(dest.toString());
                        }
                        log("   📸 Screenshot saved (with highlights)");
                    } catch (Exception e) {
                        log("   ⚠️ Screenshot failed: " + e.getMessage());
                    }
                }

                allIssues.addAll(pageIssues);
                Platform.runLater(() -> {
                    issues.setAll(allIssues);
                });
            }

            Platform.runLater(() -> progress.set(1.0));

            // Generate report
            log("📝 Generating report...");
            updateStatus("Generating report...");
            String reportDir = config.get(AppConfig.REPORT_DIR);
            Files.createDirectories(Paths.get(reportDir));

            String trackerUrl = config.get(AppConfig.TRACKER_URL);
            String trackerTable = config.get(AppConfig.TRACKER_TABLE);
            String trackerUser = config.get(AppConfig.TRACKER_USERNAME);
            String trackerPass = config.get(AppConfig.TRACKER_PASSWORD);

            String reportFile = ReportGenerator.generateReport(
                    allIssues, locale, reportDir,
                    trackerUrl, trackerTable, trackerUser, trackerPass,
                    instanceUrl
            );
            Platform.runLater(() -> reportPath.set(reportFile));
            log("✅ Report saved: " + reportFile);

            // Summary
            log("\n═══════════════════════════════════════");
            log("  Scan Complete!");
            log("  Pages scanned: " + pages.size());
            log("  Issues found:  " + allIssues.size());
            log("═══════════════════════════════════════");
            updateStatus("Done — " + allIssues.size() + " issue(s) found");

        } catch (Exception e) {
            log("❌ Error: " + e.getMessage());
            updateStatus("Error: " + e.getMessage());
        } finally {
            cleanup();
            Platform.runLater(() -> running.set(false));
        }
    }

    private void cleanup() {
        if (driver != null) {
            try {
                driver.quit();
            } catch (Exception ignored) {}
            driver = null;
        }
    }

    private void log(String message) {
        Platform.runLater(() -> logText.set(logText.get() + message + "\n"));
    }

    private void updateStatus(String message) {
        Platform.runLater(() -> statusMessage.set(message));
    }

    // ──── Observable properties for UI binding ────
    public ObservableList<DateIssue> getIssues() { return issues; }
    public StringProperty statusMessageProperty() { return statusMessage; }
    public DoubleProperty progressProperty() { return progress; }
    public BooleanProperty runningProperty() { return running; }
    public StringProperty logTextProperty() { return logText; }
    public StringProperty reportPathProperty() { return reportPath; }
}
