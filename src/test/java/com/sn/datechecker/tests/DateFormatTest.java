package com.sn.datechecker.tests;

import com.sn.datechecker.config.TestConfig;
import com.sn.datechecker.driver.DriverFactory;
import com.sn.datechecker.model.DateIssue;
import com.sn.datechecker.pages.SNLoginPage;
import com.sn.datechecker.report.ReportGenerator;
import com.sn.datechecker.scanner.DateFormatChecker;
import com.sn.datechecker.scanner.DateFormatScanner;
import com.sn.datechecker.screenshot.ScreenshotCapture;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.ITestContext;
import org.testng.annotations.*;

import java.util.ArrayList;
import java.util.List;

/**
 * TestNG test class that scans ServiceNow pages for date format issues.
 *
 * Configurable via src/test/resources/config.properties.
 * Run with: mvn test
 */
public class DateFormatTest {

    private static final Logger log = LoggerFactory.getLogger(DateFormatTest.class);

    private WebDriver driver;
    private final List<DateIssue> allIssues = new ArrayList<>();
    private String locale;
    private final DateFormatChecker dateFormatChecker = new DateFormatChecker();

    @BeforeClass
    public void setUp() {
        locale = TestConfig.getLocale();
        log.info("=== ServiceNow Date Format Checker ===");
        log.info("Locale: {}", locale);
        log.info("Instance: {}", TestConfig.getInstanceUrl());
        log.info("Browser: {} (headless: {})", TestConfig.getBrowser(), TestConfig.isHeadless());

        driver = DriverFactory.createDriver();

        // Login to ServiceNow
        String username = TestConfig.getUsername();
        String password = TestConfig.getPassword();
        if (!username.isEmpty() && !password.isEmpty()) {
            SNLoginPage loginPage = new SNLoginPage(driver);
            loginPage.navigateTo(TestConfig.getInstanceUrl());
            loginPage.login(username, password, locale);

            // Set user language preference via SN API so all pages render in target locale
            setUserLanguagePreference(locale);
        } else {
            log.warn("No credentials configured. Navigating directly (SSO or public pages).");
            driver.get(TestConfig.getInstanceUrl());
        }
    }

    /**
     * Set the logged-in user's language preference via ServiceNow so all pages
     * render in the target locale (not just the login page).
     *
     * For Polaris / Next Experience pages, the language is determined by the
     * user's {@code preferred_language} field on {@code sys_user}, NOT by URL
     * parameters. We therefore:
     * 1. Navigate to a classic UI page with {@code sysparm_language} to set the session language.
     * 2. From that classic page (where the {@code g_ck} CSRF token is available),
     *    update the user's {@code preferred_language} via REST API.
     * 3. Set the UI user preference as well.
     * 4. Navigate to Polaris home and verify.
     */
    private void setUserLanguagePreference(String locale) {
        try {
            String baseUrl = TestConfig.getInstanceUrl();
            JavascriptExecutor js = (JavascriptExecutor) driver;
            log.info("Setting user language preference to: {}", locale);

            // Step 1: Navigate to classic UI page with sysparm_language
            // This reliably sets the session language in ServiceNow
            driver.get(baseUrl + "/navpage.do?sysparm_language=" + locale);
            try { Thread.sleep(3000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }

            // Step 2: Update user's preferred_language field on sys_user via REST API
            // This is what Polaris / Next Experience reads to determine the UI language
            try {
                String username = TestConfig.getUsername();
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
                log.info("User preferred_language update: {}", result);
            } catch (Exception e) {
                log.warn("Could not update user preferred_language via REST: {}", e.getMessage());
            }

            // Step 3: Also set the UI user preference as a fallback
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
            } catch (Exception e) {
                log.warn("Could not set UI language preference: {}", e.getMessage());
            }

            // Step 4: Navigate to Polaris home to apply the language change
            driver.get(baseUrl + "/now/nav/ui/home");
            try { Thread.sleep(5000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }

            // Step 5: Verify language
            String docLang = (String) js.executeScript("return document.documentElement.lang || ''");
            log.info("Document language after preference set: '{}'", docLang);

            if (docLang.isEmpty() || docLang.startsWith("en")) {
                log.warn("⚠ Language may not have changed. Document lang='{}', expected '{}'", docLang, locale);
            } else {
                log.info("✅ Page language successfully changed to: {}", docLang);
            }

        } catch (Exception e) {
            log.warn("Could not set user language preference: {}", e.getMessage());
            log.warn("Pages may render in the default language.");
        }
    }

    @DataProvider(name = "pageUrls")
    public Object[][] pageUrls() {
        List<String> paths = TestConfig.getPagePaths();
        Object[][] data = new Object[paths.size()][1];
        for (int i = 0; i < paths.size(); i++) {
            String path = paths.get(i);
            if (path.startsWith("http")) {
                data[i][0] = path;
            } else {
                String base = TestConfig.getInstanceUrl();
                if (base.endsWith("/") && path.startsWith("/")) {
                    path = path.substring(1);
                }
                data[i][0] = base + path;
            }
        }
        return data;
    }

    @Test(dataProvider = "pageUrls", description = "Scan page for non-localized date formats")
    public void testDateFormatsOnPage(String pageUrl) {
        log.info("--- Scanning: {} ---", pageUrl);

        // Navigate to the page
        driver.get(pageUrl);

        // Wait for page to fully load (dynamic content, charts, etc.)
        try {
            Thread.sleep(TestConfig.getScanDelayMs());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Verify the page is in the target language; retry if still in English
        if (!"en".equals(locale)) {
            verifyPageLanguage(locale, pageUrl);
        }

        List<DateIssue> issues = dateFormatChecker.scan(driver, locale);

        issues.forEach(issue -> issue.setPageUrl(pageUrl));
        allIssues.addAll(issues);

        // Log each issue
        for (DateIssue issue : issues) {
            log.warn("  ISSUE: {}", issue);
        }

        // Highlight issues with red border boxes on the page before screenshot
        if (!issues.isEmpty()) {
            dateFormatChecker.highlightIssues(driver, issues);
        }

        // Always capture screenshot of the page (shows highlights if issues found)
        String screenshotPath = ScreenshotCapture.capture(
                driver, TestConfig.getScreenshotDir(), locale, pageUrl);
        if (screenshotPath != null) {
            log.info("  📸 Screenshot saved: {}", screenshotPath);
        }

        if (issues.isEmpty()) {
            log.info("  ✅ No date format issues found on this page.");
        } else {
            log.warn("  ❌ {} issue(s) found on: {}", issues.size(), pageUrl);
            if (screenshotPath != null) {
                issues.forEach(issue -> issue.setScreenshotPath(screenshotPath));
            }
        }

        // Fail the test if issues are found
        Assert.assertEquals(issues.size(), 0,
                "Found " + issues.size() + " date format issue(s) on " + pageUrl);
    }

    /**
     * Verify the page is rendering in the target language.
     * If still in English, retry by setting language via classic navigation and reload.
     */
    private void verifyPageLanguage(String targetLocale, String pageUrl) {
        try {
            JavascriptExecutor js = (JavascriptExecutor) driver;
            String docLang = (String) js.executeScript(
                "return document.documentElement.lang || ''");

            // If page is already in a non-English language, we're good
            if (!docLang.isEmpty() && !docLang.startsWith("en")) {
                log.info("Page language verified: '{}'", docLang);
                return;
            }

            log.warn("⚠ Page is in '{}' but expected '{}'. Retrying language switch...",
                     docLang, targetLocale);

            // Retry: navigate to classic page with sysparm_language to force session language
            String baseUrl = TestConfig.getInstanceUrl();
            driver.get(baseUrl + "/navpage.do?sysparm_language=" + targetLocale);
            try { Thread.sleep(2000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }

            // Navigate back to the target page
            driver.get(pageUrl);
            try { Thread.sleep(TestConfig.getScanDelayMs()); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }

            docLang = (String) js.executeScript("return document.documentElement.lang || ''");
            if (!docLang.isEmpty() && !docLang.startsWith("en")) {
                log.info("✅ Language switched to '{}' after retry", docLang);
            } else {
                log.warn("⚠ Language still '{}' after retry. Date checks may produce false positives.", docLang);
            }
        } catch (Exception e) {
            log.warn("Could not verify page language: {}", e.getMessage());
        }
    }

    @AfterClass
    public void tearDown(ITestContext context) {
        // Generate HTML report with all issues collected
        if (!allIssues.isEmpty()) {
            String reportPath = ReportGenerator.generateReport(
                    allIssues, locale, TestConfig.getReportDir(),
                    TestConfig.getTrackerInstanceUrl(),
                    TestConfig.getTrackerTable(),
                    TestConfig.getTrackerUsername(),
                    TestConfig.getTrackerPassword(),
                    TestConfig.getInstanceUrl());
            log.info("=== HTML Report: {} ===", reportPath);

            // Auto-report bugs to Global Tracker if enabled
            if (TestConfig.isAutoReportEnabled()) {
                autoReportBugs();
            }
        } else {
            log.info("=== No issues found. No report generated. ===");
        }

        log.info("=== Total issues across all pages: {} ===", allIssues.size());
        DriverFactory.quitDriver();
    }

    /**
     * Automatically report all detected issues to the i18n Global Tracker.
     */
    private void autoReportBugs() {
        String trackerUser = TestConfig.getTrackerUsername();
        String trackerPass = TestConfig.getTrackerPassword();
        if (trackerUser.isEmpty() || trackerPass.isEmpty()) {
            log.warn("Tracker credentials not configured — skipping auto-report.");
            return;
        }

        log.info("=== Auto-reporting {} issue(s) to Global Tracker ===", allIssues.size());
        com.sn.datechecker.report.BugReporter reporter = new com.sn.datechecker.report.BugReporter(
                TestConfig.getTrackerInstanceUrl(),
                TestConfig.getTrackerTable(),
                trackerUser, trackerPass);

        for (DateIssue issue : allIssues) {
            String sysId = reporter.reportBug(issue, locale, TestConfig.getInstanceUrl());
            if (sysId != null && issue.getScreenshotPath() != null) {
                reporter.attachScreenshot(sysId, issue.getScreenshotPath());
            }
        }
        log.info("=== Auto-report complete ===");
    }
}
