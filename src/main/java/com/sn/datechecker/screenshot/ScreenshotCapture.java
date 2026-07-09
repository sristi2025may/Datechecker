package com.sn.datechecker.screenshot;

import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chromium.ChromiumDriver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * Captures screenshots of pages where date format issues are found.
 */
public class ScreenshotCapture {

    private static final Logger log = LoggerFactory.getLogger(ScreenshotCapture.class);

    /**
     * Capture a screenshot of the current page.
     *
     * @param driver       the WebDriver instance
     * @param screenshotDir directory to save screenshots
     * @param locale       the locale being tested
     * @param pageUrl      the URL of the page (used for filename)
     * @return the absolute path to the saved screenshot, or null if capture failed
     */
    public static String capture(WebDriver driver, String screenshotDir, String locale, String pageUrl) {
        try {
            File dir = new File(screenshotDir);
            if (!dir.isAbsolute()) {
                dir = new File(System.getProperty("user.dir"), screenshotDir);
            }
            if (!dir.exists()) {
                boolean created = dir.mkdirs();
                log.info("Screenshot directory created: {} (success: {})", dir.getAbsolutePath(), created);
            }

            // Build a safe filename from the page URL
            String safeName = pageUrl
                    .replaceAll("https?://", "")
                    .replaceAll("[^a-zA-Z0-9_\\-]", "_");
            // Truncate if too long
            if (safeName.length() > 100) {
                safeName = safeName.substring(0, 100);
            }

            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String fileName = "screenshot_" + locale + "_" + safeName + "_" + timestamp + ".png";

            Path destination = dir.toPath().resolve(fileName);

            // Try full-page screenshot via CDP (Chrome/Chromium only)
            boolean saved = false;
            if (driver instanceof ChromiumDriver) {
                try {
                    ChromiumDriver cdpDriver = (ChromiumDriver) driver;

                    // 1. Get full page dimensions
                    Map<String, Object> metrics = cdpDriver.executeCdpCommand(
                            "Page.getLayoutMetrics", new HashMap<>());
                    @SuppressWarnings("unchecked")
                    Map<String, Object> contentSize = (Map<String, Object>) metrics.get("contentSize");
                    int fullWidth = ((Number) contentSize.get("width")).intValue();
                    int fullHeight = ((Number) contentSize.get("height")).intValue();
                    log.info("Full page dimensions: {}x{}", fullWidth, fullHeight);

                    // 2. Expand viewport to full page size
                    Map<String, Object> deviceParams = new HashMap<>();
                    deviceParams.put("width", fullWidth);
                    deviceParams.put("height", fullHeight);
                    deviceParams.put("deviceScaleFactor", 1);
                    deviceParams.put("mobile", false);
                    cdpDriver.executeCdpCommand(
                            "Emulation.setDeviceMetricsOverride", deviceParams);

                    // 3. Capture the full-page screenshot
                    Map<String, Object> screenshotParams = new HashMap<>();
                    screenshotParams.put("captureBeyondViewport", true);
                    Map<String, Object> result = cdpDriver.executeCdpCommand(
                            "Page.captureScreenshot", screenshotParams);
                    String base64 = (String) result.get("data");
                    byte[] imageBytes = Base64.getDecoder().decode(base64);
                    Files.write(destination, imageBytes);
                    saved = true;

                    // 4. Reset viewport back to normal
                    cdpDriver.executeCdpCommand(
                            "Emulation.clearDeviceMetricsOverride", new HashMap<>());

                    log.info("Full-page screenshot captured via CDP ({}x{})", fullWidth, fullHeight);
                } catch (Exception cdpEx) {
                    log.warn("CDP full-page screenshot failed, falling back: {}", cdpEx.getMessage());
                    // Try to reset viewport in case it was modified
                    try {
                        ((ChromiumDriver) driver).executeCdpCommand(
                                "Emulation.clearDeviceMetricsOverride", new HashMap<>());
                    } catch (Exception ignored) {}
                }
            }

            // Fallback: standard viewport screenshot
            if (!saved) {
                File screenshotFile = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
                Files.copy(screenshotFile.toPath(), destination, StandardCopyOption.REPLACE_EXISTING);
            }

            log.info("Screenshot saved: {}", destination.toAbsolutePath());
            return destination.toAbsolutePath().toString();

        } catch (IOException e) {
            log.error("Failed to save screenshot: {}", e.getMessage());
            return null;
        } catch (Exception e) {
            log.error("Failed to capture screenshot: {}", e.getMessage());
            return null;
        }
    }
}
