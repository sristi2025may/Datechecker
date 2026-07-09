package com.sn.datechecker.scanner;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.sn.datechecker.model.DateIssue;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Injects the date-checking JavaScript into a page via Selenium
 * and collects the results.
 */
public class DateFormatScanner {

    private static final Logger log = LoggerFactory.getLogger(DateFormatScanner.class);
    private static final Gson gson = new Gson();
    private static String scannerScript;

    static {
        try {
            scannerScript = loadResource("scanner.js");
        } catch (IOException e) {
            throw new RuntimeException("Failed to load scanner.js from resources", e);
        }
    }

    private static String loadResource(String resourceName) throws IOException {
        try (InputStream is = DateFormatScanner.class.getClassLoader().getResourceAsStream(resourceName)) {
            if (is == null) {
                throw new IOException("Resource not found: " + resourceName);
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                return reader.lines().collect(Collectors.joining("\n"));
            }
        }
    }

    private static final int MAX_RETRIES = 3;
    private static final long[] RETRY_DELAYS_MS = {3000, 5000, 8000};

    /**
     * Scan the current page for date format issues.
     * Waits for the page to be ready and retries if no issues found on first attempt
     * (ServiceNow SPA components render asynchronously).
     *
     * @param driver the WebDriver instance (page must already be loaded)
     * @param locale the target locale code (e.g. "ja", "de", "fr")
     * @return list of DateIssue objects found on the page
     */
    public static List<DateIssue> scan(WebDriver driver, String locale) {
        String pageUrl = driver.getCurrentUrl();
        log.info("Scanning page: {} with locale: {}", pageUrl, locale);

        // Wait for page to be ready (SPA components loaded)
        waitForPageReady(driver);

        List<DateIssue> allIssues = new ArrayList<>();

        // Scan with retries — SN components may still be rendering
        for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
            allIssues.clear();

            // Scan main frame (scanner.js handles shadow DOM + iframes internally)
            List<DateIssue> mainIssues = executeScanner(driver, locale);
            mainIssues.forEach(issue -> issue.setPageUrl(pageUrl));
            allIssues.addAll(mainIssues);
            log.info("Attempt {}: main frame found {} issues", attempt + 1, mainIssues.size());

            // Also scan each iframe via Selenium frame switching
            try {
                int iframeCount = getIframeCount(driver);
                for (int i = 0; i < iframeCount; i++) {
                    try {
                        driver.switchTo().frame(i);
                        List<DateIssue> frameIssues = executeScanner(driver, locale);
                        frameIssues.forEach(issue -> issue.setPageUrl(pageUrl));
                        allIssues.addAll(frameIssues);
                        log.info("  iframe[{}]: {} issues found", i, frameIssues.size());
                        driver.switchTo().defaultContent();
                    } catch (Exception e) {
                        log.warn("  Could not scan iframe[{}]: {}", i, e.getMessage());
                        driver.switchTo().defaultContent();
                    }
                }
            } catch (Exception e) {
                log.warn("Error scanning iframes: {}", e.getMessage());
            }

            if (!allIssues.isEmpty()) {
                break; // Found issues, no need to retry
            }

            // Wait and retry — components may still be loading
            if (attempt < MAX_RETRIES - 1) {
                long delay = RETRY_DELAYS_MS[attempt];
                log.info("No issues found on attempt {}. Waiting {}ms and retrying...", attempt + 1, delay);
                try { Thread.sleep(delay); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            }
        }

        log.info("Total issues on {}: {}", pageUrl, allIssues.size());
        return allIssues;
    }

    /**
     * Wait for ServiceNow page to be ready (document complete + no loading spinners).
     */
    private static void waitForPageReady(WebDriver driver) {
        JavascriptExecutor js = (JavascriptExecutor) driver;
        int maxWait = 30; // seconds
        for (int i = 0; i < maxWait; i++) {
            try {
                Boolean ready = (Boolean) js.executeScript(
                    "return document.readyState === 'complete' && " +
                    "!document.querySelector('.loading-icon, .sn-loading-icon, now-loading-icon, .polaris-loading');"
                );
                if (Boolean.TRUE.equals(ready)) {
                    log.info("Page ready after {}s", i);
                    return;
                }
            } catch (Exception e) { /* ignore */ }
            try { Thread.sleep(1000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }
        log.warn("Page readiness timeout after {}s — proceeding with scan", maxWait);
    }

    private static List<DateIssue> executeScanner(WebDriver driver, String locale) {
        try {
            JavascriptExecutor js = (JavascriptExecutor) driver;
            String wrappedScript = "return " + scannerScript;
            Object result = js.executeScript(wrappedScript, locale);

            if (result == null) {
                return Collections.emptyList();
            }

            String jsonStr = result.toString();
            if (jsonStr.isEmpty() || jsonStr.equals("[]")) {
                return Collections.emptyList();
            }

            Type listType = new TypeToken<List<DateIssue>>() {}.getType();
            List<DateIssue> issues = gson.fromJson(jsonStr, listType);
            return issues != null ? issues : Collections.emptyList();

        } catch (Exception e) {
            log.error("Error executing scanner script: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    private static int getIframeCount(WebDriver driver) {
        try {
            JavascriptExecutor js = (JavascriptExecutor) driver;
            Long count = (Long) js.executeScript("return document.querySelectorAll('iframe').length;");
            return count != null ? count.intValue() : 0;
        } catch (Exception e) {
            return 0;
        }
    }
}
