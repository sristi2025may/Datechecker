package com.sn.datechecker.report;

import com.sn.datechecker.model.DateIssue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Generates an HTML report of date format issues found during scanning.
 */
public class ReportGenerator {

    private static final Logger log = LoggerFactory.getLogger(ReportGenerator.class);

    /**
     * Generate an HTML report from the collected issues.
     *
     * @param issues  all issues found across pages
     * @param locale  the locale used for scanning
     * @param reportDir directory to save the report
     * @return the path to the generated report file
     */
    public static String generateReport(List<DateIssue> issues, String locale, String reportDir) {
        return generateReport(issues, locale, reportDir, null, null, null, null, null);
    }

    /**
     * Generate an HTML report with bug-reporting integration.
     *
     * @param issues            all issues found across pages
     * @param locale            the locale used for scanning
     * @param reportDir         directory to save the report
     * @param trackerInstanceUrl  i18n tracker instance URL (e.g. https://i18ntest.service-now.com)
     * @param trackerTable      tracker table name
     * @param trackerUsername   tracker credentials
     * @param trackerPassword   tracker credentials
     * @param testInstanceUrl   the test instance URL (source of issues)
     * @return the path to the generated report file
     */
    public static String generateReport(List<DateIssue> issues, String locale, String reportDir,
                                         String trackerInstanceUrl, String trackerTable,
                                         String trackerUsername, String trackerPassword,
                                         String testInstanceUrl) {
        File dir = new File(reportDir);
        if (!dir.exists()) {
            dir.mkdirs();
        }

        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String fileName = "date-check-report_" + locale + "_" + timestamp + ".html";
        File reportFile = new File(dir, fileName);

        // Group issues by page URL
        Map<String, List<DateIssue>> byPage = issues.stream()
                .collect(Collectors.groupingBy(
                        i -> i.getPageUrl() != null ? i.getPageUrl() : "Unknown"));

        try (PrintWriter pw = new PrintWriter(new FileWriter(reportFile))) {
            pw.println("<!DOCTYPE html>");
            pw.println("<html lang=\"en\">");
            pw.println("<head>");
            pw.println("<meta charset=\"UTF-8\">");
            pw.println("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">");
            pw.println("<title>SN Date Format Check Report — " + escapeHtml(locale) + "</title>");
            pw.println("<style>");
            pw.println(CSS);
            pw.println("</style>");
            pw.println("</head>");
            pw.println("<body>");

            boolean trackerEnabled = trackerInstanceUrl != null && !trackerInstanceUrl.isEmpty()
                    && trackerUsername != null && !trackerUsername.isEmpty();

            // Build the tracker form URL once (same for all bugs)
            String trackerFormUrl = "";
            if (trackerEnabled) {
                String cleanTracker = trackerInstanceUrl.endsWith("/")
                        ? trackerInstanceUrl.substring(0, trackerInstanceUrl.length() - 1) : trackerInstanceUrl;
                trackerFormUrl = cleanTracker + "/now/nav/ui/classic/params/target/"
                        + trackerTable + ".do"
                        + "%3Fsys_id%3D-1"
                        + "%26sysparm_stack%3D" + trackerTable + "_list.do";
            }

            // Header
            pw.println("<div class=\"header\">");
            pw.println("<h1>ServiceNow Date Format Check Report</h1>");
            pw.println("<div class=\"meta\">");
            pw.println("<span>Locale: <code>" + escapeHtml(locale) + "</code></span>");
            pw.println("<span>Generated: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) + "</span>");
            pw.println("<span>Total Issues: <strong>" + issues.size() + "</strong></span>");
            pw.println("<span>Pages Scanned: <strong>" + byPage.size() + "</strong></span>");
            pw.println("</div>");
            if (trackerEnabled && !issues.isEmpty()) {
                pw.println("<div style=\"margin-top:12px\">");
                pw.println("<button class=\"btn-report-all\" onclick=\"reportAllBugs()\">🐛 Report All Bugs to Global Tracker</button>");
                pw.println("<span id=\"report-all-status\" class=\"report-status\"></span>");
                pw.println("</div>");
            }
            pw.println("</div>");

            // Summary
            if (issues.isEmpty()) {
                pw.println("<div class=\"success-banner\">&#10004; No date format issues found.</div>");
            } else {
                pw.println("<div class=\"error-banner\">&#10060; " + issues.size() +
                        " date format issue" + (issues.size() != 1 ? "s" : "") + " found across " +
                        byPage.size() + " page" + (byPage.size() != 1 ? "s" : "") + ".</div>");
            }

            // Issues by page
            for (Map.Entry<String, List<DateIssue>> entry : byPage.entrySet()) {
                String pageUrl = entry.getKey();
                List<DateIssue> pageIssues = entry.getValue();

                pw.println("<div class=\"page-section\">");
                pw.println("<h2 class=\"page-url\">" + escapeHtml(pageUrl) +
                        " <span class=\"badge\">" + pageIssues.size() + "</span></h2>");

                pw.println("<table>");
                pw.println("<thead><tr>");
                pw.println("<th>#</th><th>Found</th><th>Reason</th><th>Expected</th><th>Type</th><th>Fix Suggestion</th>"
                        + (trackerEnabled ? "<th>Action</th>" : ""));
                pw.println("</tr></thead>");
                pw.println("<tbody>");

                int idx = 1;
                for (DateIssue issue : pageIssues) {
                    String issueId = "issue-" + System.identityHashCode(issue);
                    pw.println("<tr id=\"" + issueId + "\">");
                    pw.println("<td>" + idx++ + "</td>");
                    pw.println("<td class=\"found\">" + escapeHtml(issue.getFound()) + "</td>");
                    pw.println("<td>" + escapeHtml(issue.getReason()) + "</td>");
                    pw.println("<td class=\"expected\">" + escapeHtml(issue.getExpected()) + "</td>");
                    pw.println("<td><span class=\"type-badge type-" + escapeHtml(issue.getType()) + "\">" +
                            escapeHtml(issue.getType()) + "</span></td>");
                    pw.println("<td><code class=\"suggestion\">" +
                            escapeHtml(issue.getSuggestion()).replace("\n", "<br>") + "</code></td>");
                    if (trackerEnabled) {
                        // Use <a> with href+target instead of window.open (blocked from file:// origin)
                        pw.println("<td>");
                        pw.println("<a class=\"btn-report\" "
                                + "href=\"" + escapeHtml(trackerFormUrl) + "\" "
                                + "target=\"_blank\" rel=\"noopener\" "
                                + "data-found=\"" + escapeHtml(issue.getFound()) + "\" "
                                + "data-type=\"" + escapeHtml(issue.getType()) + "\" "
                                + "data-reason=\"" + escapeHtml(issue.getReason()) + "\" "
                                + "data-expected=\"" + escapeHtml(issue.getExpected()) + "\" "
                                + "data-suggestion=\"" + escapeHtml(issue.getSuggestion()).replace("\n", " | ") + "\" "
                                + "data-pageurl=\"" + escapeHtml(issue.getPageUrl()) + "\" "
                                + "onclick=\"reportBug(this)\">🐛 Report Bug</a>");
                        pw.println("<span id=\"status-" + issueId + "\" class=\"report-status\"></span>");
                        pw.println("</td>");
                    }
                    pw.println("</tr>");
                }

                pw.println("</tbody></table>");

                // Embed screenshot if available
                String screenshotPath = pageIssues.stream()
                        .map(DateIssue::getScreenshotPath)
                        .filter(p -> p != null && !p.isEmpty())
                        .findFirst().orElse(null);
                if (screenshotPath != null) {
                    String imgTag = buildScreenshotImgTag(screenshotPath);
                    if (imgTag != null) {
                        pw.println("<div class=\"screenshot-section\">");
                        pw.println("<h3>Page Screenshot</h3>");
                        pw.println(imgTag);
                        pw.println("</div>");
                    }
                }

                pw.println("</div>");
            }

            // Embedded JavaScript for bug reporting
            if (trackerEnabled) {
                pw.println("<script>");
                pw.println(buildTrackerScript(
                        trackerInstanceUrl, trackerTable,
                        trackerUsername, trackerPassword,
                        locale, testInstanceUrl));
                pw.println("</script>");
            }

            pw.println("</body></html>");

        } catch (IOException e) {
            log.error("Failed to generate report: {}", e.getMessage());
            throw new RuntimeException("Report generation failed", e);
        }

        log.info("Report generated: {}", reportFile.getAbsolutePath());
        return reportFile.getAbsolutePath();
    }

    /**
     * Encode a screenshot as a Base64 data URI img tag, or fall back to a file link.
     */
    private static String buildScreenshotImgTag(String screenshotPath) {
        try {
            Path path = Path.of(screenshotPath);
            if (Files.exists(path)) {
                byte[] bytes = Files.readAllBytes(path);
                String base64 = Base64.getEncoder().encodeToString(bytes);
                return "<img class=\"screenshot\" src=\"data:image/png;base64," + base64 +
                        "\" alt=\"Page screenshot\">";
            }
        } catch (IOException e) {
            log.warn("Could not embed screenshot: {}", e.getMessage());
        }
        // Fallback: link to the file
        return "<a href=\"file://" + escapeHtml(screenshotPath) + "\">View Screenshot</a>";
    }

    /**
     * Build the JavaScript block that powers the "Report Bug" buttons in the HTML report.
     * Opens the ServiceNow form in a new tab with pre-populated fields.
     * This avoids CORS issues that occur when using fetch() from a file:// origin.
     */
    private static String buildTrackerScript(String instanceUrl, String table,
                                              String username, String password,
                                              String locale, String testInstanceUrl) {
        String cleanUrl = instanceUrl.endsWith("/")
                ? instanceUrl.substring(0, instanceUrl.length() - 1) : instanceUrl;

        return """
            var TRACKER_INSTANCE = '%s';
            var TRACKER_TABLE = '%s';
            var LOCALE = '%s';
            var TEST_INSTANCE = '%s';

            function buildDescription(btn) {
                var found = btn.getAttribute('data-found') || '';
                var type = btn.getAttribute('data-type') || '';
                var reason = btn.getAttribute('data-reason') || '';
                var expected = btn.getAttribute('data-expected') || '';
                var suggestion = btn.getAttribute('data-suggestion') || '';
                var pageUrl = btn.getAttribute('data-pageurl') || '';

                var shortDesc = '[' + LOCALE.toUpperCase() + '] Wrong date format: "' + found + '"';
                var desc = '== Date/Time Format Defect (Auto-Detected) ==\\n\\n'
                    + 'Language tested : ' + LOCALE + '\\n'
                    + 'Test instance   : ' + TEST_INSTANCE + '\\n'
                    + 'Page URL        : ' + pageUrl + '\\n\\n'
                    + '--- Issue ---\\n'
                    + 'Found           : ' + found + '\\n'
                    + 'Issue type      : ' + type + '\\n'
                    + 'Reason          : ' + reason + '\\n'
                    + 'Expected format : ' + expected + '\\n\\n'
                    + '--- How to Fix (KB0562050) ---\\n'
                    + suggestion.replace(/ \\| /g, '\\n') + '\\n\\n'
                    + '--- Steps to Reproduce ---\\n'
                    + '1. Log in to ' + TEST_INSTANCE + '\\n'
                    + '2. Set user language to ' + LOCALE + '\\n'
                    + '3. Navigate to ' + pageUrl + '\\n'
                    + '4. Observe the date/time value: "' + found + '"\\n'
                    + '5. The value is not localized for the selected language.\\n\\n'
                    + 'Detected by: SN Date Format Checker (Selenium automation)';

                return { short_description: shortDesc, description: desc };
            }

            // Strip chars that break sysparm_query structure and truncate
            function clean(s, maxLen) {
                return s.replace(/[\\^&=\\n\\r#?]/g, ' ').replace(/\\s+/g, ' ').trim().substring(0, maxLen || 200);
            }

            function reportBug(btn) {
                var found    = btn.getAttribute('data-found') || '';
                var reason   = btn.getAttribute('data-reason') || '';
                var expected = btn.getAttribute('data-expected') || '';
                var suggestion = btn.getAttribute('data-suggestion') || '';
                var pageUrl  = btn.getAttribute('data-pageurl') || '';

                // Build pre-populated form URL — onclick updates href before browser follows it
                var shortDesc = '[' + LOCALE.toUpperCase() + '] ' + clean(reason, 150);
                var classicUrl = TRACKER_TABLE + '.do?sys_id=-1'
                    + '&sysparm_query=short_description=' + clean(shortDesc, 160)
                    + '^type=i18n'
                    + '^sub_problem_type_i18n=date_time'
                    + '^actual_source=' + clean(found, 100)
                    + '^expected_source=' + clean(expected, 100)
                    + '^comments=' + clean(suggestion, 300)
                    + '^repro_steps=' + clean(pageUrl, 300)
                    + '^direct_link=' + clean(pageUrl, 300)
                    + '&sysparm_stack=' + TRACKER_TABLE + '_list.do';
                btn.href = TRACKER_INSTANCE + '/now/nav/ui/classic/params/target/'
                    + encodeURIComponent(classicUrl);

                // Update button state
                btn.textContent = '✅ Opened';
                btn.style.background = '#4caf50';
                btn.closest('tr').style.background = '#e8f5e9';
                var statusEl = btn.nextElementSibling;
                statusEl.textContent = ' Form opened — fields pre-populated';
                statusEl.className = 'report-status success';
            }

            function reportAllBugs() {
                var links = document.querySelectorAll('.btn-report');
                var statusEl = document.getElementById('report-all-status');
                statusEl.textContent = ' Opening ' + links.length + ' bug(s)...';
                statusEl.className = 'report-status';

                // Open each bug in a new tab with its own pre-populated fields
                var delay = 0;
                for (var i = 0; i < links.length; i++) {
                    (function(link, idx) {
                        setTimeout(function() {
                            reportBug(link);
                            window.open(link.href, '_blank');
                            if (idx === links.length - 1) {
                                statusEl.textContent = ' ✅ Opened ' + links.length + ' bug(s) with all fields pre-populated';
                                statusEl.className = 'report-status success';
                            }
                        }, delay);
                    })(links[i], i);
                    delay += 1000;
                }
            }
            """.formatted(cleanUrl, table, locale,
                testInstanceUrl != null ? testInstanceUrl : "");
    }

    private static String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private static final String CSS = """
            * { box-sizing: border-box; margin: 0; padding: 0; }
            body {
                font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
                background: #f5f5f5;
                color: #333;
                padding: 24px;
            }
            .header {
                background: #1a1a2e;
                color: #fff;
                padding: 24px 32px;
                border-radius: 10px;
                margin-bottom: 20px;
            }
            .header h1 { font-size: 22px; margin-bottom: 10px; }
            .meta { display: flex; gap: 24px; font-size: 13px; color: #aaa; flex-wrap: wrap; }
            .meta code { background: rgba(255,255,255,0.15); padding: 2px 6px; border-radius: 4px; color: #fff; }
            .success-banner {
                background: #e8f5e9; color: #2e7d32; padding: 14px 20px;
                border-radius: 8px; font-size: 15px; font-weight: 600; margin-bottom: 20px;
            }
            .error-banner {
                background: #ffebee; color: #c62828; padding: 14px 20px;
                border-radius: 8px; font-size: 15px; font-weight: 600; margin-bottom: 20px;
            }
            .page-section {
                background: #fff; border-radius: 10px; padding: 20px;
                margin-bottom: 20px; box-shadow: 0 1px 4px rgba(0,0,0,0.08);
            }
            .page-url {
                font-size: 14px; color: #1565c0; margin-bottom: 14px;
                word-break: break-all;
            }
            .badge {
                background: #e53935; color: #fff; font-size: 12px;
                padding: 2px 8px; border-radius: 10px; margin-left: 8px;
            }
            table { width: 100%; border-collapse: collapse; font-size: 13px; }
            th {
                text-align: left; padding: 8px 10px; background: #f5f5f5;
                border-bottom: 2px solid #ddd; font-weight: 600; color: #555;
            }
            td { padding: 8px 10px; border-bottom: 1px solid #eee; vertical-align: top; }
            tr:hover { background: #fafafa; }
            .found {
                color: #c62828; font-weight: 600;
                background: #ffcdd2; padding: 2px 6px; border-radius: 3px;
                text-decoration: line-through; text-decoration-color: #e53935;
            }
            .expected {
                color: #2e7d32; font-weight: 600;
                background: #c8e6c9; padding: 2px 6px; border-radius: 3px;
            }
            .type-badge {
                font-size: 11px; padding: 2px 6px; border-radius: 3px;
                font-weight: 600; text-transform: uppercase;
            }
            .type-dom { background: #e3f2fd; color: #1565c0; }
            .type-svg { background: #f3e5f5; color: #7b1fa2; }
            .type-input { background: #fff3e0; color: #e65100; }
            .type-attribute { background: #e0f2f1; color: #00695c; }
            .suggestion {
                font-size: 11px; color: #1a237e; background: #e8eaf6;
                padding: 4px 8px; border-radius: 4px; display: inline-block;
                word-break: break-all; line-height: 1.6;
            }
            .screenshot-section {
                margin-top: 16px; padding-top: 16px;
                border-top: 1px solid #eee;
            }
            .screenshot-section h3 {
                font-size: 13px; color: #555; margin-bottom: 10px;
            }
            .screenshot {
                max-width: 100%; border: 1px solid #ddd;
                border-radius: 6px; box-shadow: 0 2px 8px rgba(0,0,0,0.1);
            }
            .btn-report, .btn-report-all {
                background: #1565c0; color: #fff; border: none; padding: 6px 14px;
                border-radius: 5px; cursor: pointer; font-size: 12px; font-weight: 600;
                white-space: nowrap; transition: background 0.2s;
                text-decoration: none; display: inline-block;
            }
            .btn-report:hover, .btn-report-all:hover { background: #0d47a1; }
            .btn-report:disabled { opacity: 0.7; cursor: not-allowed; }
            .btn-report-all { font-size: 14px; padding: 10px 20px; }
            .report-status { font-size: 11px; margin-left: 6px; }
            .report-status.success { color: #2e7d32; font-weight: 600; }
            .report-status.error { color: #c62828; font-weight: 600; }
            """;
}
