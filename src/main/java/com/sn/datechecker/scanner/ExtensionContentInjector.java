package com.sn.datechecker.scanner;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.sn.datechecker.model.DateIssue;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collections;
import java.util.List;

/**
 * Injects the Chrome extension's content.js and highlight.css directly
 * into the page via Selenium's JavascriptExecutor.
 *
 * This bypasses Chrome's extension loading system (which may be blocked
 * by corporate/managed Chrome policies) while providing the same
 * scanning, highlighting, and floating panel UI as the real extension.
 */
public class ExtensionContentInjector {

    private static final Logger log = LoggerFactory.getLogger(ExtensionContentInjector.class);
    private static final Gson gson = new Gson();

    private final String patchedContentScript;
    private final String highlightCss;

    /**
     * Load and prepare the extension's content.js and highlight.css.
     *
     * @param extensionDir path to the unpacked extension directory
     * @throws IOException if files cannot be read
     */
    public ExtensionContentInjector(File extensionDir) throws IOException {
        File contentJs = new File(extensionDir, "content.js");
        File cssFile = new File(extensionDir, "highlight.css");

        if (!contentJs.exists()) {
            throw new IOException("content.js not found in: " + extensionDir.getAbsolutePath());
        }

        String rawScript = Files.readString(contentJs.toPath(), StandardCharsets.UTF_8);
        this.highlightCss = cssFile.exists()
                ? Files.readString(cssFile.toPath(), StandardCharsets.UTF_8)
                : "";

        this.patchedContentScript = patchScript(rawScript);
        log.info("Extension content script loaded from: {}", extensionDir.getAbsolutePath());
    }

    /**
     * Patch the extension's content.js to work outside Chrome extension context:
     * 1. Prepend Chrome API stubs (runtime, storage)
     * 2. Append global scan function exposure
     */
    private String patchScript(String raw) {
        // Chrome API stubs — these replace the real Chrome extension APIs
        // so the content script runs without errors outside extension context
        String stubs =
            "if(typeof chrome==='undefined')var chrome={};" +
            "if(!chrome.runtime)chrome.runtime={" +
            "  sendMessage:function(){}," +
            "  onMessage:{addListener:function(){}}" +
            "};" +
            "if(!chrome.storage)chrome.storage={local:{" +
            "  get:function(k,cb){if(cb)cb({});}," +
            "  set:function(){}" +
            "}};\n";

        // Expose scan() and issues to the global scope so Selenium can call them.
        // These must be inserted INSIDE the IIFE, before the closing })();
        String globalExposure =
            "\n  window.__snDateScan = function(locale) {\n" +
            "    currentLocale = locale;\n" +
            "    scan();\n" +
            "    return issues.map(function(iss) {\n" +
            "      return {\n" +
            "        found: iss.text,\n" +
            "        reason: iss.message,\n" +
            "        expected: iss.suggestion || '',\n" +
            "        suggestion: iss.suggestion || '',\n" +
            "        type: iss.type\n" +
            "      };\n" +
            "    });\n" +
            "  };\n" +
            "  window.__snDateClear = function() {\n" +
            "    document.querySelectorAll('.sn-date-issue').forEach(function(el) {\n" +
            "      var parent = el.parentNode;\n" +
            "      while (el.firstChild && el.firstChild.className !== 'sn-date-badge') {\n" +
            "        parent.insertBefore(el.firstChild, el);\n" +
            "      }\n" +
            "      parent.removeChild(el);\n" +
            "    });\n" +
            "    var panel = document.getElementById('sn-date-panel');\n" +
            "    if (panel) panel.remove();\n" +
            "    issues = [];\n" +
            "    issueCount = 0;\n" +
            "  };\n";

        // Insert the global exposure before the closing })();
        String patched;
        int closingIdx = raw.lastIndexOf("})();");
        if (closingIdx >= 0) {
            patched = stubs + raw.substring(0, closingIdx) + globalExposure + raw.substring(closingIdx);
        } else {
            // No IIFE wrapper — just append
            patched = stubs + raw + globalExposure;
        }

        return patched;
    }

    /**
     * Inject the extension's CSS and content script into the current page.
     *
     * @param driver the WebDriver instance (page must already be loaded)
     */
    public void inject(WebDriver driver) {
        JavascriptExecutor js = (JavascriptExecutor) driver;

        // Check if already injected
        Boolean alreadyInjected = (Boolean) js.executeScript(
                "return typeof window.__snDateScan === 'function';");
        if (Boolean.TRUE.equals(alreadyInjected)) {
            log.info("Extension content script already injected, skipping");
            return;
        }

        // Inject highlight CSS
        if (!highlightCss.isEmpty()) {
            js.executeScript(
                "var style = document.createElement('style');" +
                "style.id = 'sn-date-checker-css';" +
                "style.textContent = arguments[0];" +
                "document.head.appendChild(style);",
                highlightCss);
            log.info("Extension highlight CSS injected");
        }

        // Inject patched content script
        js.executeScript(patchedContentScript);
        log.info("Extension content script injected");
    }

    /**
     * Inject the extension's content script and run a scan.
     *
     * @param driver the WebDriver instance (page must already be loaded)
     * @param locale the target locale code (e.g. "ja", "de", "fr")
     * @return list of DateIssue objects found on the page
     */
    public List<DateIssue> scan(WebDriver driver, String locale) {
        inject(driver);

        JavascriptExecutor js = (JavascriptExecutor) driver;

        try {
            Object result = js.executeScript(
                    "return JSON.stringify(window.__snDateScan(arguments[0]));", locale);

            if (result == null) {
                log.warn("Extension scan returned null");
                return Collections.emptyList();
            }

            String jsonStr = result.toString();
            log.info("Extension scan result (first 300 chars): {}",
                    jsonStr.substring(0, Math.min(jsonStr.length(), 300)));

            Type listType = new TypeToken<List<DateIssue>>() {}.getType();
            List<DateIssue> issues = gson.fromJson(jsonStr, listType);
            log.info("Extension scan found {} issue(s)", issues != null ? issues.size() : 0);
            return issues != null ? issues : Collections.emptyList();

        } catch (Exception e) {
            log.error("Extension scan failed: {}", e.getMessage());
            return Collections.emptyList();
        }
    }
}
