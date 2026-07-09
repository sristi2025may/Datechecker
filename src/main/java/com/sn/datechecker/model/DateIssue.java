package com.sn.datechecker.model;

/**
 * Represents a single date format issue found on a ServiceNow page.
 */
public class DateIssue {

    private String found;
    private String reason;
    private String expected;
    private String suggestion;
    private String xpath;
    private String type;
    private String context;
    private String frameUrl;
    private String pageUrl;
    private String screenshotPath;

    public DateIssue() {}

    public String getFound() { return found; }
    public void setFound(String found) { this.found = found; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public String getExpected() { return expected; }
    public void setExpected(String expected) { this.expected = expected; }

    public String getSuggestion() { return suggestion; }
    public void setSuggestion(String suggestion) { this.suggestion = suggestion; }

    public String getXpath() { return xpath; }
    public void setXpath(String xpath) { this.xpath = xpath; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getContext() { return context; }
    public void setContext(String context) { this.context = context; }

    public String getFrameUrl() { return frameUrl; }
    public void setFrameUrl(String frameUrl) { this.frameUrl = frameUrl; }

    public String getPageUrl() { return pageUrl; }
    public void setPageUrl(String pageUrl) { this.pageUrl = pageUrl; }

    public String getScreenshotPath() { return screenshotPath; }
    public void setScreenshotPath(String screenshotPath) { this.screenshotPath = screenshotPath; }

    @Override
    public String toString() {
        return String.format("[%s] Found: \"%s\" | Reason: %s | Expected: \"%s\"",
                type, found, reason, expected);
    }
}
