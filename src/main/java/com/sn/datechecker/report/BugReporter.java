package com.sn.datechecker.report;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sn.datechecker.model.DateIssue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Reports date format issues as defects to the i18n Global Tracker
 * on the i18ntest.service-now.com instance via the Table API.
 *
 * Target table: x_all_language_tra_all_language_tracker
 */
public class BugReporter {

    private static final Logger log = LoggerFactory.getLogger(BugReporter.class);
    private static final Gson gson = new Gson();

    private final String instanceUrl;
    private final String table;
    private final String authHeader;

    /**
     * @param instanceUrl e.g. "https://i18ntest.service-now.com"
     * @param table       e.g. "x_all_language_tra_all_language_tracker"
     * @param username    basic-auth username
     * @param password    basic-auth password
     */
    public BugReporter(String instanceUrl, String table, String username, String password) {
        this.instanceUrl = instanceUrl.endsWith("/")
                ? instanceUrl.substring(0, instanceUrl.length() - 1) : instanceUrl;
        this.table = table;
        String credentials = username + ":" + password;
        this.authHeader = "Basic " + Base64.getEncoder()
                .encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Create a defect in the Global Tracker for a single DateIssue.
     *
     * @param issue      the detected date/time format issue
     * @param locale     the language under test
     * @param testSource the test instance URL (source of the issue)
     * @return the sys_id of the created record, or null on failure
     */
    public String reportBug(DateIssue issue, String locale, String testSource) {
        Map<String, String> fields = buildFields(issue, locale, testSource);
        return createRecord(fields);
    }

    /**
     * Build the field map for the Global Tracker record.
     */
    private Map<String, String> buildFields(DateIssue issue, String locale, String testSource) {
        Map<String, String> fields = new LinkedHashMap<>();

        // Short description — summarises the defect
        String shortDesc = String.format("[%s] Wrong date format on %s: \"%s\"",
                locale.toUpperCase(), pageName(issue.getPageUrl()), issue.getFound());
        fields.put("short_description", shortDesc);

        // Description — full details
        StringBuilder desc = new StringBuilder();
        desc.append("== Date/Time Format Defect (Auto-Detected) ==\n\n");
        desc.append("Language tested : ").append(locale).append("\n");
        desc.append("Test instance   : ").append(testSource).append("\n");
        desc.append("Page URL        : ").append(issue.getPageUrl()).append("\n\n");
        desc.append("--- Issue ---\n");
        desc.append("Found           : ").append(issue.getFound()).append("\n");
        desc.append("Issue type      : ").append(issue.getType()).append("\n");
        desc.append("Reason          : ").append(issue.getReason()).append("\n");
        desc.append("Expected format : ").append(issue.getExpected()).append("\n\n");
        desc.append("--- How to Fix (KB0562050) ---\n");
        desc.append(issue.getSuggestion()).append("\n\n");
        desc.append("--- Steps to Reproduce ---\n");
        desc.append("1. Log in to ").append(testSource).append("\n");
        desc.append("2. Set user language to ").append(locale).append("\n");
        desc.append("3. Navigate to ").append(issue.getPageUrl()).append("\n");
        desc.append("4. Observe the date/time value: \"").append(issue.getFound()).append("\"\n");
        desc.append("5. The value is not localized for the selected language.\n\n");
        desc.append("Detected by: SN Date Format Checker (Selenium automation)\n");
        fields.put("description", desc.toString());

        // Additional standard fields
        fields.put("priority", "3");
        fields.put("state", "1");

        return fields;
    }

    /**
     * Create a record in the tracker table via ServiceNow Table API.
     *
     * @return sys_id of the created record, or null on failure
     */
    private String createRecord(Map<String, String> fields) {
        String endpoint = instanceUrl + "/api/now/table/" + table;
        try {
            URL url = new URL(endpoint);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Accept", "application/json");
            conn.setRequestProperty("Authorization", authHeader);
            conn.setDoOutput(true);

            String body = gson.toJson(fields);
            log.debug("POST {} body: {}", endpoint, body);

            try (OutputStream os = conn.getOutputStream()) {
                os.write(body.getBytes(StandardCharsets.UTF_8));
            }

            int status = conn.getResponseCode();
            String responseBody = readStream(
                    status >= 200 && status < 300 ? conn.getInputStream() : conn.getErrorStream());

            if (status == 201 || status == 200) {
                JsonObject result = JsonParser.parseString(responseBody)
                        .getAsJsonObject().getAsJsonObject("result");
                String sysId = result.get("sys_id").getAsString();
                String number = result.has("number") ? result.get("number").getAsString() : sysId;
                log.info("✅ Bug reported to Global Tracker — {} (sys_id: {})", number, sysId);
                return sysId;
            } else {
                log.error("❌ Failed to report bug (HTTP {}): {}", status, responseBody);
                return null;
            }

        } catch (Exception e) {
            log.error("❌ Error reporting bug to {}: {}", endpoint, e.getMessage());
            return null;
        }
    }

    /**
     * Attach a screenshot to an existing tracker record.
     *
     * @param sysId          sys_id of the record
     * @param screenshotPath local path to the screenshot file
     */
    public void attachScreenshot(String sysId, String screenshotPath) {
        if (sysId == null || screenshotPath == null || screenshotPath.isEmpty()) return;

        String endpoint = instanceUrl + "/api/now/attachment/file"
                + "?table_name=" + table
                + "&table_sys_id=" + sysId
                + "&file_name=screenshot.png";
        try {
            Path path = Path.of(screenshotPath);
            if (!Files.exists(path)) {
                log.warn("Screenshot file not found: {}", screenshotPath);
                return;
            }
            byte[] imageBytes = Files.readAllBytes(path);

            URL url = new URL(endpoint);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "image/png");
            conn.setRequestProperty("Accept", "application/json");
            conn.setRequestProperty("Authorization", authHeader);
            conn.setDoOutput(true);

            try (OutputStream os = conn.getOutputStream()) {
                os.write(imageBytes);
            }

            int status = conn.getResponseCode();
            if (status == 201 || status == 200) {
                log.info("📎 Screenshot attached to tracker record {}", sysId);
            } else {
                String resp = readStream(conn.getErrorStream());
                log.warn("Failed to attach screenshot (HTTP {}): {}", status, resp);
            }

        } catch (Exception e) {
            log.error("Error attaching screenshot: {}", e.getMessage());
        }
    }

    /**
     * Build a URL that opens the tracker form pre-populated with issue data
     * (for use in the HTML report "Report Bug" button as a fallback).
     */
    public String buildPrePopulatedUrl(DateIssue issue, String locale, String testSource) {
        Map<String, String> fields = buildFields(issue, locale, testSource);
        StringBuilder url = new StringBuilder();
        url.append(instanceUrl)
           .append("/now/nav/ui/classic/params/target/")
           .append(table)
           .append(".do?sys_id=-1");
        for (Map.Entry<String, String> entry : fields.entrySet()) {
            url.append("&sysparm_")
               .append(urlEncode(entry.getKey()))
               .append("=")
               .append(urlEncode(entry.getValue()));
        }
        return url.toString();
    }

    private static String pageName(String pageUrl) {
        if (pageUrl == null) return "unknown";
        // Extract last meaningful path segment
        String clean = pageUrl.replaceAll("\\?.*", "").replaceAll("/$", "");
        String[] parts = clean.split("/");
        return parts.length > 0 ? parts[parts.length - 1] : clean;
    }

    private static String readStream(java.io.InputStream is) {
        if (is == null) return "";
        try {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "(unreadable)";
        }
    }

    private static String urlEncode(String s) {
        try {
            return java.net.URLEncoder.encode(s, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return s;
        }
    }
}
