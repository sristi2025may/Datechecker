package com.sn.datechecker.pages;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.sn.datechecker.model.DateIssue;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.Select;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Type;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Page Object for interacting with the SN Date Format Checker Chrome extension popup.
 *
 * Opens the extension's popup.html in a new tab, selects the target locale,
 * triggers a scan via chrome.scripting API (targeting the ServiceNow tab),
 * and collects the results.
 */
public class ExtensionPopupPage {

    private static final Logger log = LoggerFactory.getLogger(ExtensionPopupPage.class);
    private static final Gson gson = new Gson();

    private final WebDriver driver;
    private final WebDriverWait wait;
    private String extensionId;
    private String snTabHandle;
    private String popupTabHandle;

    public ExtensionPopupPage(WebDriver driver) {
        this.driver = driver;
        this.wait = new WebDriverWait(driver, Duration.ofSeconds(30));
    }

    public ExtensionPopupPage(WebDriver driver, String extensionId) {
        this(driver);
        this.extensionId = extensionId;
    }

    /**
     * Detect the extension ID using Chrome DevTools Protocol.
     * Retries up to 15 times (1 second apart) because the extension's service worker
     * may not be registered immediately after Chrome launches.
     * Falls back to navigating to chrome://extensions if CDP targets don't reveal it.
     */
    @SuppressWarnings("unchecked")
    public String findExtensionId() {
        if (extensionId != null) return extensionId;

        // Phase 1: Try CDP Target.getTargets (fast, non-intrusive)
        int maxRetries = 5;
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                ChromeDriver chromeDriver = (ChromeDriver) driver;
                Map<String, Object> result = chromeDriver.executeCdpCommand(
                        "Target.getTargets", Map.of());
                List<Map<String, Object>> targets =
                        (List<Map<String, Object>>) result.get("targetInfos");

                log.info("CDP Target.getTargets (attempt {}/{}): found {} targets",
                        attempt, maxRetries, targets.size());

                for (Map<String, Object> target : targets) {
                    String url = (String) target.get("url");
                    String type = (String) target.get("type");
                    String title = (String) target.get("title");
                    log.info("  Target: type={}, title={}, url={}", type, title, url);
                    if (url != null && url.startsWith("chrome-extension://")) {
                        extensionId = url.replace("chrome-extension://", "").split("/")[0];
                        log.info("Detected extension ID: {} (type: {}, url: {})",
                                extensionId, type, url);
                        return extensionId;
                    }
                }

                if (attempt < maxRetries) {
                    log.info("Extension target not found yet, retrying in 1 second...");
                    Thread.sleep(1000);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.warn("CDP attempt {} failed: {}", attempt, e.getMessage());
                try { Thread.sleep(1000); } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt(); break;
                }
            }
        }

        // Phase 2: Fallback — navigate to chrome://extensions and extract ID from the page
        log.info("Trying fallback: extracting extension ID from chrome://extensions page");
        try {
            String originalUrl = driver.getCurrentUrl();
            driver.get("chrome://extensions");
            Thread.sleep(2000);

            JavascriptExecutor js = (JavascriptExecutor) driver;
            // chrome://extensions uses nested shadow DOMs; query through them
            String extractScript =
                "try {" +
                "  var mgr = document.querySelector('extensions-manager');" +
                "  if (!mgr || !mgr.shadowRoot) return 'no-manager';" +
                "  var itemList = mgr.shadowRoot.querySelector('extensions-item-list');" +
                "  if (!itemList || !itemList.shadowRoot) return 'no-item-list';" +
                "  var items = itemList.shadowRoot.querySelectorAll('extensions-item');" +
                "  if (!items || items.length === 0) return 'no-items';" +
                "  var ids = [];" +
                "  for (var i = 0; i < items.length; i++) { ids.push(items[i].id); }" +
                "  return ids.join(',');" +
                "} catch(e) { return 'error:' + e.message; }";

            Object result = js.executeScript(extractScript);
            String resultStr = result != null ? result.toString() : "";
            log.info("chrome://extensions result: {}", resultStr);

            if (!resultStr.isEmpty() && !resultStr.startsWith("no-") && !resultStr.startsWith("error:")) {
                // Take the first extension ID (filter out Chrome's built-in ones if needed)
                String[] ids = resultStr.split(",");
                for (String id : ids) {
                    id = id.trim();
                    if (!id.isEmpty()) {
                        extensionId = id;
                        log.info("Extension ID from chrome://extensions: {}", extensionId);
                        // Navigate back to original page
                        if (originalUrl != null && !originalUrl.startsWith("chrome://")) {
                            driver.get(originalUrl);
                        }
                        return extensionId;
                    }
                }
            }

            // Navigate back
            if (originalUrl != null && !originalUrl.startsWith("chrome://")) {
                driver.get(originalUrl);
            }
        } catch (Exception e) {
            log.error("Fallback extension ID detection failed: {}", e.getMessage());
        }

        throw new RuntimeException(
                "Could not find Chrome extension ID. " +
                "Ensure the extension is loaded via extension.path in config.properties.");
    }

    /**
     * Open the extension popup in a new browser tab.
     * Must be called when the ServiceNow page is the active tab.
     */
    public void openPopup() {
        snTabHandle = driver.getWindowHandle();
        String extId = findExtensionId();
        String popupUrl = "chrome-extension://" + extId + "/popup.html";

        log.info("Opening extension popup: {}", popupUrl);

        // Record existing handles
        Set<String> handlesBefore = driver.getWindowHandles();

        // Open popup in a new tab
        ((JavascriptExecutor) driver).executeScript(
                "window.open(arguments[0], '_blank');", popupUrl);

        // Wait for the new tab to appear
        new WebDriverWait(driver, Duration.ofSeconds(10)).until(
                d -> d.getWindowHandles().size() > handlesBefore.size());

        // Switch to the new popup tab
        for (String handle : driver.getWindowHandles()) {
            if (!handlesBefore.contains(handle)) {
                popupTabHandle = handle;
                driver.switchTo().window(popupTabHandle);
                break;
            }
        }

        // Wait for popup DOM to be ready
        wait.until(ExpectedConditions.presenceOfElementLocated(By.id("locale")));
        wait.until(ExpectedConditions.presenceOfElementLocated(By.id("scanBtn")));
        log.info("Extension popup loaded successfully");
    }

    /**
     * Select the target locale from the extension's dropdown.
     */
    public void selectLocale(String locale) {
        log.info("Selecting locale '{}' in extension popup", locale);
        WebElement localeSelect = wait.until(
                ExpectedConditions.elementToBeClickable(By.id("locale")));
        Select select = new Select(localeSelect);
        select.selectByValue(locale);

        // Wait a moment for the reference table to update
        try { Thread.sleep(500); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        log.info("Locale '{}' selected in extension popup", locale);
    }

    /**
     * Trigger a scan using the extension's chrome.scripting API.
     *
     * Executed from the popup tab context, which has full extension permissions.
     * Finds the ServiceNow tab by URL, runs the content script's scan function on it,
     * then updates the popup UI with the results.
     *
     * @param locale the target locale code
     * @return list of DateIssue objects found on the ServiceNow page
     */
    public List<DateIssue> triggerScan(String locale) {
        log.info("Triggering scan via extension for locale: {}", locale);

        // Ensure we're on the popup tab
        if (popupTabHandle != null) {
            driver.switchTo().window(popupTabHandle);
        }

        JavascriptExecutor js = (JavascriptExecutor) driver;

        // Execute scan from the popup's extension context:
        // 1. Find the ServiceNow tab
        // 2. Run __snDateScan() on it via chrome.scripting.executeScript
        // 3. Update the popup UI with results
        // 4. Return results as JSON
        String scanScript =
            "var callback = arguments[arguments.length - 1];" +
            "var locale = arguments[0];" +
            "(async function() {" +
            "  try {" +
            // Find the ServiceNow tab
            "    var tabs = await chrome.tabs.query({});" +
            "    var snTab = null;" +
            "    for (var i = 0; i < tabs.length; i++) {" +
            "      if (tabs[i].url && tabs[i].url.includes('service-now.com') " +
            "          && !tabs[i].url.includes('chrome-extension')) {" +
            "        snTab = tabs[i]; break;" +
            "      }" +
            "    }" +
            "    if (!snTab) {" +
            "      callback(JSON.stringify({error:'No ServiceNow tab found'}));" +
            "      return;" +
            "    }" +
            // Run scan on the SN tab via chrome.scripting
            "    var results = await chrome.scripting.executeScript({" +
            "      target: { tabId: snTab.id, allFrames: true }," +
            "      func: function(loc) {" +
            "        if (typeof window.__snDateScan === 'function') return window.__snDateScan(loc);" +
            "        return [];" +
            "      }," +
            "      args: [locale]" +
            "    });" +
            // Aggregate issues from all frames
            "    var allIssues = [];" +
            "    for (var i = 0; i < results.length; i++) {" +
            "      var frame = results[i];" +
            "      if (frame.result && Array.isArray(frame.result)) {" +
            "        for (var j = 0; j < frame.result.length; j++) allIssues.push(frame.result[j]);" +
            "      }" +
            "    }" +
            // Update popup UI — status bar
            "    var statusDiv = document.getElementById('status');" +
            "    if (allIssues.length > 0) {" +
            "      statusDiv.className = 'error';" +
            "      statusDiv.textContent = 'date format issues found ' + allIssues.length;" +
            "      statusDiv.style.display = 'block';" +
            "    } else {" +
            "      statusDiv.className = 'success';" +
            "      statusDiv.textContent = 'No date format issues found on this page.';" +
            "      statusDiv.style.display = 'block';" +
            "    }" +
            // Update popup UI — render issue rows
            "    if (typeof renderIssues === 'function') {" +
            "      renderIssues(allIssues);" +
            "    }" +
            // Update badge
            "    try {" +
            "      if (allIssues.length > 0) {" +
            "        chrome.action.setBadgeText({ text: String(allIssues.length), tabId: snTab.id });" +
            "        chrome.action.setBadgeBackgroundColor({ color: '#e53935', tabId: snTab.id });" +
            "      } else {" +
            "        chrome.action.setBadgeText({ text: '', tabId: snTab.id });" +
            "      }" +
            "    } catch(be) {}" +
            "    callback(JSON.stringify(allIssues));" +
            "  } catch(e) {" +
            "    callback(JSON.stringify({error: e.message}));" +
            "  }" +
            "})();";

        Object result = js.executeAsyncScript(scanScript, locale);

        if (result == null) {
            log.warn("Extension scan returned null");
            return Collections.emptyList();
        }

        String jsonStr = result.toString();
        log.info("Extension scan result (first 300 chars): {}",
                jsonStr.substring(0, Math.min(jsonStr.length(), 300)));

        // Check for error response
        if (jsonStr.contains("\"error\"") && !jsonStr.startsWith("[")) {
            log.error("Extension scan error: {}", jsonStr);
            return Collections.emptyList();
        }

        try {
            Type listType = new TypeToken<List<DateIssue>>() {}.getType();
            List<DateIssue> issues = gson.fromJson(jsonStr, listType);
            log.info("Extension scan found {} issue(s)", issues != null ? issues.size() : 0);
            return issues != null ? issues : Collections.emptyList();
        } catch (Exception e) {
            log.error("Failed to parse extension scan results: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Switch focus back to the ServiceNow page tab.
     */
    public void switchToSnTab() {
        if (snTabHandle != null) {
            driver.switchTo().window(snTabHandle);
            log.info("Switched back to ServiceNow tab");
        }
    }

    /**
     * Switch focus to the extension popup tab.
     */
    public void switchToPopupTab() {
        if (popupTabHandle != null) {
            driver.switchTo().window(popupTabHandle);
        }
    }

    /**
     * Close the popup tab and switch back to the ServiceNow tab.
     */
    public void close() {
        if (popupTabHandle != null) {
            try {
                driver.switchTo().window(popupTabHandle);
                driver.close();
            } catch (Exception e) {
                log.warn("Could not close popup tab: {}", e.getMessage());
            }
            popupTabHandle = null;
        }
        if (snTabHandle != null) {
            driver.switchTo().window(snTabHandle);
        }
        log.info("Extension popup closed");
    }

    public String getExtensionId() {
        return extensionId;
    }

    public String getSnTabHandle() {
        return snTabHandle;
    }
}
