package com.sn.datechecker.scanner;

import com.sn.datechecker.model.DateIssue;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.Month;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.time.format.TextStyle;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Selenium-integrated date format checker that detects incorrect/unlocalised
 * date strings on any web page.
 *
 * <p>Replicates the detection logic of the {@code sn-date-checker-v2} Chrome
 * extension entirely in Java. Pass a {@link WebDriver} pointing at a loaded
 * page and a target locale; the checker extracts every visible text node,
 * runs the same five detection rules the extension uses, and returns a list
 * of {@link DateIssue} objects.</p>
 *
 * <h3>Detection rules</h3>
 * <ol>
 *   <li><b>English month names</b> — full or abbreviated (Jan–Dec) in a
 *       non-English locale.</li>
 *   <li><b>English weekday names</b> — full or abbreviated (Mon–Sun).</li>
 *   <li><b>Wrong month–year order</b> — CJK/Korean month character before
 *       year, e.g. {@code 3月 2023}.</li>
 *   <li><b>Wrong date format</b> — English-style dates such as
 *       {@code "March 15, 2023"} or {@code "15 March 2023"}.</li>
 *   <li><b>Expected-pattern whitelist</b> — text that matches one of the
 *       four {@link FormatStyle} patterns for the target locale is
 *       considered correct and skipped.</li>
 * </ol>
 *
 * <h3>Usage</h3>
 * <pre>{@code
 * DateFormatChecker checker = new DateFormatChecker();
 * List<DateIssue> issues = checker.scan(driver, "ja");
 * issues.forEach(System.out::println);
 * }</pre>
 */
public class DateFormatChecker {

    private static final Logger log = LoggerFactory.getLogger(DateFormatChecker.class);

    // ───────────────────── Locale Configuration ─────────────────────

    /** Maps short locale codes to Java {@link Locale} objects. */
    private static final Map<String, Locale> LOCALE_MAP = new LinkedHashMap<>();

    /** ServiceNow-specific locale aliases. */
    private static final Map<String, String> SN_ALIAS_MAP = Map.of(
            "fq", "fr-CA",
            "zt", "zh-Hant",
            "pb", "pt-BR"
    );

    static {
        // ServiceNow Language Pack — 24 supported languages
        LOCALE_MAP.put("ar",      Locale.forLanguageTag("ar-SA"));    // Arabic
        LOCALE_MAP.put("pt-BR",   Locale.forLanguageTag("pt-BR"));   // Brazilian Portuguese
        LOCALE_MAP.put("zh-CN",   Locale.forLanguageTag("zh-CN"));   // Chinese (Simplified)
        LOCALE_MAP.put("zh-Hant", Locale.forLanguageTag("zh-Hant")); // Chinese (Traditional)
        LOCALE_MAP.put("cs",      Locale.forLanguageTag("cs-CZ"));   // Czech
        LOCALE_MAP.put("nl",      Locale.forLanguageTag("nl-NL"));   // Dutch
        LOCALE_MAP.put("fi",      Locale.forLanguageTag("fi-FI"));   // Finnish
        LOCALE_MAP.put("fr",      Locale.forLanguageTag("fr-FR"));   // French
        LOCALE_MAP.put("fr-CA",   Locale.forLanguageTag("fr-CA"));   // French Canadian
        LOCALE_MAP.put("de",      Locale.forLanguageTag("de-DE"));   // German
        LOCALE_MAP.put("he",      Locale.forLanguageTag("he-IL"));   // Hebrew
        LOCALE_MAP.put("hu",      Locale.forLanguageTag("hu-HU"));   // Hungarian
        LOCALE_MAP.put("it",      Locale.forLanguageTag("it-IT"));   // Italian
        LOCALE_MAP.put("ja",      Locale.forLanguageTag("ja-JP"));   // Japanese
        LOCALE_MAP.put("ko",      Locale.forLanguageTag("ko-KR"));   // Korean
        LOCALE_MAP.put("nb",      Locale.forLanguageTag("nb-NO"));   // Norwegian
        LOCALE_MAP.put("pl",      Locale.forLanguageTag("pl-PL"));   // Polish
        LOCALE_MAP.put("pt",      Locale.forLanguageTag("pt-PT"));   // Portuguese
        LOCALE_MAP.put("ru",      Locale.forLanguageTag("ru-RU"));   // Russian
        LOCALE_MAP.put("es",      Locale.forLanguageTag("es-ES"));   // Spanish
        LOCALE_MAP.put("sv",      Locale.forLanguageTag("sv-SE"));   // Swedish
        LOCALE_MAP.put("th",      Locale.forLanguageTag("th-TH"));   // Thai
        LOCALE_MAP.put("tr",      Locale.forLanguageTag("tr-TR"));   // Turkish
    }

    // ───────────────────── Detection Patterns ─────────────────────

    private static final Pattern ENGLISH_MONTH_RE = Pattern.compile(
            "\\b(January|February|March|April|May|June|July|August|September|October|November|December"
          + "|Jan|Feb|Mar|Apr|Jun|Jul|Aug|Sep|Oct|Nov|Dec)\\b");

    private static final Pattern ENGLISH_WEEKDAY_RE = Pattern.compile(
            "\\b(Monday|Tuesday|Wednesday|Thursday|Friday|Saturday|Sunday"
          + "|Mon|Tue|Wed|Thu|Fri|Sat|Sun)\\b");

    /** CJK/Korean month character followed by a year — wrong order. */
    private static final Pattern BARE_MONTH_YEAR_RE = Pattern.compile(
            "^(\\d{1,2})[月월]\\s+(\\d{4})$");

    /**
     * CJK month-day-year: "3月 31 2026" or "7月 07 2026 9:30 午前"
     * This is wrong order — should be "2026年3月31日" in ja/zh/ko.
     * Captures: group(1)=month, group(2)=day, group(3)=year, group(4)=optional time portion
     */
    private static final Pattern CJK_MDY_RE = Pattern.compile(
            "(\\d{1,2})[月월]\\s+(\\d{1,2}),?\\s+(\\d{4})(\\s+\\d{1,2}:\\d{2}(:\\d{2})?\\s*([午前後aApP][午mM]?)?)?");

    /**
     * Text-month DMY: "16 juin 2026", "16. Juni 2026 14:12", "30 jun 2026 11:59am"
     * Captures: group(1)=day, group(2)=month_word, group(3)=year, rest=optional time
     */
    private static final Pattern TEXT_MONTH_DMY_RE = Pattern.compile(
            "(\\d{1,2})\\.?\\s+([\\p{L}]{3,12})\\.?\\s+(\\d{4})"
          + "(\\s+\\d{1,2}\\s*[:hH]\\s*\\d{2}(\\s*[:hH]\\s*\\d{2})?"
          + "(\\s*[apAP]\\.?[mM]\\.?|\\s*午[前後])?)?");

    /**
     * Reverse lookup: month name (lowercase, any supported locale) → month number (1–12).
     * Built once from all 34+ supported locales so we can parse dates like "16 juin 2026".
     */
    private static final Map<String, Integer> ALL_MONTH_NAME_TO_NUM = new HashMap<>();
    static {
        Locale[] allLocs = new Locale[LOCALE_MAP.size() + 1];
        int li = 0;
        for (Locale l : LOCALE_MAP.values()) allLocs[li++] = l;
        allLocs[li] = Locale.ENGLISH;
        for (Locale loc : allLocs) {
            for (Month m : Month.values()) {
                for (TextStyle ts : new TextStyle[]{TextStyle.FULL, TextStyle.SHORT,
                        TextStyle.FULL_STANDALONE, TextStyle.SHORT_STANDALONE}) {
                    try {
                        String name = m.getDisplayName(ts, loc);
                        if (name.length() >= 3) {
                            ALL_MONTH_NAME_TO_NUM.putIfAbsent(name.toLowerCase(), m.getValue());
                        }
                    } catch (Exception ignored) {}
                }
            }
        }
    }

    /** Text that looks like it contains a date. */
    private static final Pattern LOOKS_LIKE_DATE_RE = Pattern.compile(
            "\\d{4}|\\d{1,2}[/\\.-]\\d{1,2}|\\d{1,2}\\s*[月年일년월日]");

    /** English-style date: "Month DD, YYYY" */
    private static final Pattern EN_DATE_MDY_RE = Pattern.compile(
            "^[A-Za-z]{3,9}\\s+\\d{1,2},?\\s+\\d{4}$");

    /** English-style date: "DD Month YYYY" */
    private static final Pattern EN_DATE_DMY_RE = Pattern.compile(
            "^\\d{1,2}\\s+[A-Za-z]{3,9}\\s+\\d{4}$");

    /** For extracting components from "Month DD, YYYY" */
    private static final Pattern EN_EXTRACT_MDY = Pattern.compile(
            "\\b([A-Za-z]{3,9})\\s+(\\d{1,2}),?\\s+(\\d{4})\\b");

    /** For extracting components from "DD Month YYYY" */
    private static final Pattern EN_EXTRACT_DMY = Pattern.compile(
            "\\b(\\d{1,2})\\s+([A-Za-z]{3,9})\\s+(\\d{4})\\b");

    /** For extracting "Month YYYY" */
    private static final Pattern EN_MONTH_YEAR = Pattern.compile(
            "^([A-Za-z]{3,9})\\s+(\\d{4})$");

    /** ISO datetime: "2026-06-24 19:30:00" or "2026-06-24 19:30" — matches within text */
    private static final Pattern ISO_DATETIME_RE = Pattern.compile(
            "(\\d{4}-\\d{2}-\\d{2}[\\s T]\\d{2}:\\d{2}(:\\d{2})?)");

    /** ISO date: "2026-06-24" — matches within text */
    private static final Pattern ISO_DATE_RE = Pattern.compile(
            "(?<![\\d-])(\\d{4}-\\d{2}-\\d{2})(?!\\s*\\d{2}:)");

    /** Truncated date+time: "06-24 19:30" */
    private static final Pattern TRUNC_DATETIME_RE = Pattern.compile(
            "^\\d{2}-\\d{2}\\s+\\d{2}:\\d{2}$");

    /** US-style: "06/24/2026" or "6/24/2026" — matches within text */
    private static final Pattern US_DATE_RE = Pattern.compile(
            "(?<![\\d/])(\\d{1,2}/\\d{1,2}/\\d{4})(?![\\d/])");

    /** US-style datetime: "06/24/2026 19:30:00" — matches within text */
    private static final Pattern US_DATETIME_RE = Pattern.compile(
            "(?<![\\d/])(\\d{1,2}/\\d{1,2}/\\d{4}\\s+\\d{2}:\\d{2}(:\\d{2})?)(?![\\d/])");

    /** European-style with dots: "24.06.2026" — matches within text */
    private static final Pattern EU_DOT_DATE_RE = Pattern.compile(
            "(?<![\\d.])(\\d{1,2}\\.\\d{1,2}\\.\\d{4})(?![\\d.])");

    /** Date format pattern strings used as placeholders (e.g. "YYYY-MM-DD HH:mm:ss") */
    private static final Pattern DATE_FORMAT_PATTERN_RE = Pattern.compile(
            "^(YYYY[/\\-.]MM[/\\-.]DD(\\s+HH:mm(:ss)?)?|MM[/\\-.]DD[/\\-.]YYYY|DD[/\\-.]MM[/\\-.]YYYY)$",
            Pattern.CASE_INSENSITIVE);

    /** English month name → 1-indexed month number. */
    private static final Map<String, Integer> ENGLISH_MONTHS = new HashMap<>();
    static {
        String[] full = {"January","February","March","April","May","June",
                         "July","August","September","October","November","December"};
        String[] abbr = {"Jan","Feb","Mar","Apr","May","Jun",
                         "Jul","Aug","Sep","Oct","Nov","Dec"};
        for (int i = 0; i < 12; i++) {
            ENGLISH_MONTHS.put(full[i], i + 1);
            ENGLISH_MONTHS.put(abbr[i], i + 1);
        }
    }

    // ───────────────────── JavaScript for text extraction ─────────────────────

    /**
     * JavaScript that recursively walks the DOM tree including shadow DOM
     * roots, collects all visible text nodes (skipping script/style/input
     * elements), and returns them as a JSON array of strings.
     * ServiceNow's Polaris UI uses web components with shadow DOM extensively,
     * so a simple TreeWalker on document.body finds nothing.
     */
    private static final String EXTRACT_TEXT_NODES_JS =
        "var results = [];" +
        "var seen = new Set();" +
        "function addText(t) { t = t.trim(); if (t.length >= 4 && !seen.has(t)) { seen.add(t); results.push(t); } }" +
        "var SKIP_TAGS = {script:1,style:1,noscript:1,template:1};" +
        // Visibility check: skip elements not visible to the user
        "function isVisible(el) {" +
        "  if (el.hidden || el.getAttribute('aria-hidden') === 'true') return false;" +
        "  if (el.offsetParent === null && el.tagName !== 'BODY' && el.tagName !== 'HTML'" +
        "      && !el.closest('[data-shadow-host]')) {" +
        "    var st = window.getComputedStyle(el);" +
        "    if (st.display === 'none' || st.visibility === 'hidden') return false;" +
        "  }" +
        "  return true;" +
        "}" +
        "function walkNode(root) {" +
        "  if (!root) return;" +
        "  var children = root.childNodes;" +
        "  for (var i = 0; i < children.length; i++) {" +
        "    var node = children[i];" +
        "    if (node.nodeType === 3) {" +  // TEXT_NODE
        "      addText(node.textContent);" +
        "    } else if (node.nodeType === 1) {" +  // ELEMENT_NODE
        "      var tag = node.tagName.toLowerCase();" +
        "      if (SKIP_TAGS[tag]) continue;" +
        "      if (node.classList && node.classList.contains('sn-date-issue')) continue;" +
        "      if (node.id === 'sn-date-panel') continue;" +
        // Skip hidden elements
        "      try { if (!isVisible(node)) continue; } catch(e) {}" +
        // Descend into same-origin iframes
        "      if (tag === 'iframe') {" +
        "        try { if (node.contentDocument && node.contentDocument.body) walkNode(node.contentDocument.body); } catch(e) {}" +
        "        continue;" +
        "      }" +
        // Capture input/textarea values and placeholders
        "      if (tag === 'input' || tag === 'textarea') {" +
        "        if (node.value) addText(node.value);" +
        "        if (node.placeholder) addText(node.placeholder);" +
        "        continue;" +
        "      }" +
        // Skip <time> datetime attribute — it's a machine-readable ISO timestamp, not user-visible text
        // Capture title and aria-valuetext only — these are shown on hover/interaction
        "      var title = node.getAttribute('title');" +
        "      if (title) addText(title);" +
        "      var ariaVal = node.getAttribute('aria-valuetext');" +
        "      if (ariaVal) addText(ariaVal);" +
        "      if (tag === 'svg') {" +
        "        var svgTexts = node.querySelectorAll('text');" +
        "        for (var s = 0; s < svgTexts.length; s++) { addText(svgTexts[s].textContent); }" +
        "        continue;" +
        "      }" +
        "      if (node.shadowRoot) walkNode(node.shadowRoot);" +
        "      walkNode(node);" +
        "    }" +
        "  }" +
        "}" +
        "walkNode(document.body);" +
        // Deep SVG scan: recursively find all SVGs across shadow DOM boundaries
        // (chart components often bury SVGs several shadow DOM levels deep)
        "function deepSvgScan(root) {" +
        "  if (!root) return;" +
        "  try {" +
        "    var svgs = root.querySelectorAll('svg');" +
        "    for (var i = 0; i < svgs.length; i++) {" +
        "      var texts = svgs[i].querySelectorAll('text');" +
        "      for (var j = 0; j < texts.length; j++) { addText(texts[j].textContent); }" +
        "    }" +
        "    var allEls = root.querySelectorAll('*');" +
        "    for (var i = 0; i < allEls.length; i++) {" +
        "      if (allEls[i].shadowRoot) deepSvgScan(allEls[i].shadowRoot);" +
        "    }" +
        "  } catch(e) {}" +
        "}" +
        "deepSvgScan(document);" +
        "return JSON.stringify(results);";

    // ───────────────────── Public API ─────────────────────

    /**
     * Scan the current page for date format issues.
     *
     * @param driver a WebDriver whose current page is already loaded
     * @param localeCode the target locale (e.g. "ja", "ar", "de", "fr")
     * @return list of detected date format issues (empty if none found)
     */
    public List<DateIssue> scan(WebDriver driver, String localeCode) {
        String pageUrl = driver.getCurrentUrl();
        Locale targetLocale = resolveLocale(localeCode);
        if (targetLocale == null) {
            log.warn("Unsupported locale '{}'. Supported: {}", localeCode, LOCALE_MAP.keySet());
            return Collections.emptyList();
        }

        log.info("DateFormatChecker scanning page: {} with locale: {} ({})",
                pageUrl, localeCode, targetLocale.toLanguageTag());

        // Scroll the page to load lazy content below the fold
        scrollPage(driver);

        // Build expected date patterns for this locale (whitelist)
        Set<String> expectedPatterns = buildExpectedPatterns(targetLocale);

        List<DateIssue> issues = new ArrayList<>();

        // Scan main frame (includes shadow DOM traversal)
        List<String> textNodes = extractTextNodes(driver);
        log.info("Extracted {} text nodes from main frame", textNodes.size());
        // Debug: log all extracted text so we can see what's on the page
        for (int idx = 0; idx < textNodes.size(); idx++) {
            log.debug("  text[{}]: \"{}\"", idx, textNodes.get(idx));
        }
        if (log.isInfoEnabled() && !textNodes.isEmpty()) {
            log.info("Sample text nodes (first 20):");
            for (int idx = 0; idx < Math.min(20, textNodes.size()); idx++) {
                String snippet = textNodes.get(idx);
                if (snippet.length() > 100) snippet = snippet.substring(0, 100) + "...";
                log.info("  [{}] \"{}\"", idx, snippet);
            }
            // Log all text nodes that look like they might contain dates
            log.info("Date-like text nodes:");
            for (int idx = 0; idx < textNodes.size(); idx++) {
                String t = textNodes.get(idx);
                if (t.matches(".*\\d{4}.*") || t.matches(".*\\d{1,2}[/\\.-]\\d{1,2}.*")
                    || ENGLISH_MONTH_RE.matcher(t).find() || ENGLISH_WEEKDAY_RE.matcher(t).find()
                    || t.matches(".*\\d{1,2}\\s*[月年일년월日].*")) {
                    log.info("  DATE[{}] \"{}\"", idx, t.length() > 120 ? t.substring(0, 120) + "..." : t);
                }
            }
        }
        for (String text : textNodes) {
            DateIssue issue = detectIssue(text, targetLocale, expectedPatterns);
            if (issue != null) {
                issue.setPageUrl(pageUrl);
                issues.add(issue);
            }
        }

        // Scan each iframe
        try {
            JavascriptExecutor js = (JavascriptExecutor) driver;
            Long iframeCount = (Long) js.executeScript(
                    "return document.querySelectorAll('iframe').length;");
            int count = iframeCount != null ? iframeCount.intValue() : 0;
            for (int i = 0; i < count; i++) {
                try {
                    driver.switchTo().frame(i);
                    List<String> frameTexts = extractTextNodes(driver);
                    log.info("  iframe[{}]: {} text nodes", i, frameTexts.size());
                    for (String text : frameTexts) {
                        DateIssue issue = detectIssue(text, targetLocale, expectedPatterns);
                        if (issue != null) {
                            issue.setPageUrl(pageUrl);
                            issue.setFrameUrl("iframe[" + i + "]");
                            issues.add(issue);
                        }
                    }
                    driver.switchTo().defaultContent();
                } catch (Exception e) {
                    log.warn("  Could not scan iframe[{}]: {}", i, e.getMessage());
                    driver.switchTo().defaultContent();
                }
            }
        } catch (Exception e) {
            log.warn("Error scanning iframes: {}", e.getMessage());
        }

        log.info("DateFormatChecker found {} issue(s) on {}", issues.size(), pageUrl);
        return issues;
    }

    /**
     * Highlight detected issues on the page with a red border box.
     * Call this after scan() and before taking a screenshot.
     *
     * @param driver the WebDriver with the page loaded
     * @param issues the list of issues returned by scan()
     */
    public void highlightIssues(WebDriver driver, List<DateIssue> issues) {
        if (issues.isEmpty()) return;

        // Build a JS array of search keys for highlighting.
        // For each issue, extract a short, unique search key:
        //   - english-month/weekday: extract just the English date part (e.g. "Jun 27")
        //   - non-localized-date: the ISO/US date string itself (e.g. "2026-06-29 02:20:52")
        //   - non-localized-placeholder: the placeholder pattern (e.g. "YYYY-MM-DD")
        // All use contains-matching against element textContent, then pick the smallest element.
        java.util.Set<String> uniqueKeys = new java.util.LinkedHashSet<>();
        for (DateIssue issue : issues) {
            String key = extractHighlightKey(issue);
            if (key != null && !key.isEmpty()) uniqueKeys.add(key);
        }
        StringBuilder issueDataJs = new StringBuilder("[");
        int idx = 0;
        for (String key : uniqueKeys) {
            if (idx++ > 0) issueDataJs.append(",");
            String escaped = key.replace("\\", "\\\\").replace("\"", "\\\"");
            issueDataJs.append("\"").append(escaped).append("\"");
        }
        issueDataJs.append("]");

        String highlightJs =
            "var keys = " + issueDataJs + ";" +
            "var highlighted = 0;" +
            "var markedEls = new Set();" +
            "function applyHighlight(el) {" +
            "  if (markedEls.has(el)) return;" +
            "  var isSvg = el.ownerSVGElement || (el.tagName && el.tagName.toLowerCase() === 'svg');" +
            "  if (isSvg) {" +
            "    var r = el.getBoundingClientRect();" +
            "    var ov = document.createElement('div');" +
            "    ov.style.cssText = 'position:absolute;pointer-events:none;z-index:99999;" +
            "      border:3px solid red;box-shadow:0 0 8px rgba(255,0,0,0.5);" +
            "      left:'+(r.left+window.scrollX)+'px;top:'+(r.top+window.scrollY)+'px;" +
            "      width:'+r.width+'px;height:'+r.height+'px';" +
            "    document.body.appendChild(ov);" +
            "  } else {" +
            "    el.style.border = '3px solid red';" +
            "    el.style.boxShadow = '0 0 8px rgba(255,0,0,0.5)';" +
            "  }" +
            "  markedEls.add(el);" +
            "  highlighted++;" +
            "}" +
            // For each key, find the smallest element whose textContent contains it
            "function findSmallest(root, text) {" +
            "  if (!root) return null;" +
            "  var best = null;" +
            "  var bestSize = Infinity;" +
            "  function walkChildren(parent) {" +
            "    if (!parent) return;" +
            "    var kids = parent.childNodes || parent.children || [];" +
            "    for (var i = 0; i < kids.length; i++) { walk(kids[i]); }" +
            "  }" +
            "  function walk(node) {" +
            "    if (!node) return;" +
            // Handle shadow roots and document fragments (nodeType 11)
            "    if (node.nodeType === 11) { walkChildren(node); return; }" +
            "    if (node.nodeType !== 1) return;" +
            "    var tag = (node.tagName || '').toLowerCase();" +
            "    if (tag === 'script' || tag === 'style' || tag === 'noscript') return;" +
            // Input/textarea: check placeholder and value
            "    if (tag === 'input' || tag === 'textarea') {" +
            "      var ph = (node.placeholder || '').trim();" +
            "      var val = (node.value || '').trim();" +
            "      if (ph.indexOf(text) >= 0 || val.indexOf(text) >= 0) {" +
            "        best = node; bestSize = 0;" +
            "      }" +
            "      return;" +
            "    }" +
            "    if (tag === 'iframe') {" +
            "      try { if (node.contentDocument) walkChildren(node.contentDocument.body); } catch(e) {}" +
            "      return;" +
            "    }" +
            // SVG: search <text> elements inside and highlight individual text elements
            "    if (tag === 'svg') {" +
            "      var svgTexts = node.querySelectorAll('text');" +
            "      for (var s = 0; s < svgTexts.length; s++) {" +
            "        if ((svgTexts[s].textContent || '').indexOf(text) >= 0) {" +
            "          var rect = svgTexts[s].getBoundingClientRect();" +
            "          var sz = rect.width * rect.height;" +
            "          if (sz > 0 && sz < bestSize) { best = svgTexts[s]; bestSize = sz; }" +
            "        }" +
            "      }" +
            "      return;" +
            "    }" +
            // Check if this element's visible text contains the search key
            "    var tc = '';" +
            "    try { tc = node.textContent || ''; } catch(e) {}" +
            "    if (tc.indexOf(text) >= 0) {" +
            "      var rect = node.getBoundingClientRect();" +
            "      var sz = rect.width * rect.height;" +
            // Only consider visible, reasonably sized elements
            "      if (sz > 0 && sz < bestSize && rect.height < 400 && rect.width < 700) {" +
            "        best = node; bestSize = sz;" +
            "      }" +
            "    }" +
            // Descend into shadow DOM first, then light DOM children
            "    if (node.shadowRoot) walkChildren(node.shadowRoot);" +
            "    walkChildren(node);" +
            "  }" +
            "  walk(root);" +
            "  return best;" +
            "}" +
            // Deep shadow DOM search: find elements across all shadow boundaries
            "function deepFind(root, text) {" +
            "  if (!root) return null;" +
            "  var best = null; var bestSize = Infinity;" +
            "  try {" +
            // Search SVGs in this root — target individual <text> elements
            "    var svgs = root.querySelectorAll('svg');" +
            "    for (var i = 0; i < svgs.length; i++) {" +
            "      var txts = svgs[i].querySelectorAll('text');" +
            "      for (var j = 0; j < txts.length; j++) {" +
            "        if ((txts[j].textContent || '').indexOf(text) >= 0) {" +
            "          var r = txts[j].getBoundingClientRect();" +
            "          var s = r.width * r.height;" +
            "          if (s > 0 && s < bestSize) { best = txts[j]; bestSize = s; }" +
            "        }" +
            "      }" +
            "    }" +
            // Search all elements in this root
            "    var all = root.querySelectorAll('*');" +
            "    for (var i = 0; i < all.length; i++) {" +
            "      var el = all[i];" +
            "      var tc = (el.textContent || '').trim();" +
            "      if (tc.indexOf(text) >= 0) {" +
            "        var r = el.getBoundingClientRect();" +
            "        var s = r.width * r.height;" +
            "        if (s > 0 && s < bestSize && r.height < 400 && r.width < 700) {" +
            "          best = el; bestSize = s;" +
            "        }" +
            "      }" +
            // Recurse into shadow roots
            "      if (el.shadowRoot) {" +
            "        var sub = deepFind(el.shadowRoot, text);" +
            "        if (sub) {" +
            "          var sr = sub.getBoundingClientRect();" +
            "          var ss = sr.width * sr.height;" +
            "          if (ss > 0 && ss < bestSize) { best = sub; bestSize = ss; }" +
            "        }" +
            "      }" +
            "    }" +
            "  } catch(e) {}" +
            "  return best;" +
            "}" +
            // Process each key: try fast walk first, then deep search as fallback
            "for (var i = 0; i < keys.length; i++) {" +
            "  var el = findSmallest(document.body, keys[i]);" +
            "  if (!el) el = deepFind(document, keys[i]);" +
            "  if (el) applyHighlight(el);" +
            "}" +
            "return highlighted;";

        log.info("Highlight search keys: {}", uniqueKeys);
        try {
            JavascriptExecutor js = (JavascriptExecutor) driver;
            Object result = js.executeScript(highlightJs);
            long count = result != null ? ((Number) result).longValue() : 0;
            log.info("Highlighted {} element(s) with red border on page", count);
            // Scroll back to top so screenshot captures the page from the beginning
            js.executeScript("window.scrollTo(0, 0);");
            Thread.sleep(500);
        } catch (Exception e) {
            log.warn("Failed to highlight issues: {}", e.getMessage());
        }
    }

    /**
     * Extract a short, specific search key from an issue for highlighting.
     * This avoids matching large containers by using just the date-related
     * portion of the found text.
     */
    private String extractHighlightKey(DateIssue issue) {
        String found = issue.getFound();
        String type = issue.getType();

        if ("non-localized-date".equals(type) || "non-localized-placeholder".equals(type)) {
            // The found text is already just the date string (e.g., "2026-06-29 02:20:52")
            return found;
        }

        if ("english-month".equals(type)) {
            // Extract the English month + surrounding context (e.g., "Jun 27", "Jun 28")
            // Use a broader pattern to capture month + optional day/year
            java.util.regex.Matcher m = Pattern.compile(
                "\\b(January|February|March|April|May|June|July|August|September|October|November|December"
                + "|Jan|Feb|Mar|Apr|Jun|Jul|Aug|Sep|Oct|Nov|Dec)"
                + "(\\s+\\d{1,2})?(,?\\s+\\d{4})?\\b").matcher(found);
            if (m.find()) {
                return m.group(0).trim(); // e.g., "Jun 27" or "January 18, 2023"
            }
        }

        if ("english-weekday".equals(type)) {
            java.util.regex.Matcher m = ENGLISH_WEEKDAY_RE.matcher(found);
            if (m.find()) {
                return m.group(0);
            }
        }

        // Fallback: use the full found text (for wrong-order, wrong-format, etc.)
        return found;
    }

    /**
     * Returns the set of supported locale codes.
     */
    public static Set<String> getSupportedLocales() {
        return Collections.unmodifiableSet(LOCALE_MAP.keySet());
    }

    // ───────────────────── Core Detection Logic ─────────────────────

    /**
     * Check a single text string for date format issues.
     * Mirrors the {@code detectIssue()} function in the extension's content.js.
     *
     * @param text           the text to check
     * @param targetLocale   the expected locale
     * @param expectedPatterns pre-computed correct date patterns for the locale
     * @return a DateIssue if a problem is found, or null if the text is fine
     */
    DateIssue detectIssue(String text, Locale targetLocale, Set<String> expectedPatterns) {
        String t = text.trim();
        if (t.isEmpty() || t.length() < 4) return null;

        // If this text matches a correct pattern for the locale, skip it
        if (expectedPatterns.contains(t)) return null;

        // Rule 1: English month names in non-English locale
        if (ENGLISH_MONTH_RE.matcher(t).find()) {
            String localizedExample = getSuggestion(t, targetLocale);
            return buildIssue(t, "english-month",
                    "English month name found — locale is " + targetLocale.toLanguageTag(),
                    localizedExample, targetLocale);
        }

        // Rule 2: English weekday names in non-English locale
        if (ENGLISH_WEEKDAY_RE.matcher(t).find()) {
            String localizedExample = getSuggestion(t, targetLocale);
            return buildIssue(t, "english-weekday",
                    "English weekday name found — locale is " + targetLocale.toLanguageTag(),
                    localizedExample, targetLocale);
        }

        // Rule 3: Wrong month-year order (CJK/Korean)
        if (BARE_MONTH_YEAR_RE.matcher(t).find()) {
            return buildIssue(t, "wrong-order",
                    "Month before year — wrong order for this locale",
                    getSuggestion(t, targetLocale), targetLocale);
        }

        // Rule 3b: CJK month-day-year (wrong order): "3月 31 2026", "7月 07 2026 9:30 午前"
        java.util.regex.Matcher cjkMdyMatcher = CJK_MDY_RE.matcher(t);
        if (cjkMdyMatcher.find()) {
            String matched = cjkMdyMatcher.group(0);
            try {
                int month = Integer.parseInt(cjkMdyMatcher.group(1));
                int day = Integer.parseInt(cjkMdyMatcher.group(2));
                int year = Integer.parseInt(cjkMdyMatcher.group(3));
                if (month >= 1 && month <= 12 && day >= 1 && day <= 31) {
                    int safeDay = Math.min(day, java.time.YearMonth.of(year, month).lengthOfMonth());
                    LocalDate date = LocalDate.of(year, month, safeDay);
                    String expected = formatLong(date, targetLocale);
                    // Append time portion if present
                    String timePart = cjkMdyMatcher.group(4);
                    if (timePart != null && !timePart.trim().isEmpty()) {
                        expected += " " + timePart.trim();
                    }
                    return buildIssue(matched, "non-localized-date",
                            "month-day-year order — should be year-month-day for " + targetLocale.getDisplayLanguage(),
                            expected, targetLocale);
                }
            } catch (Exception ignored) {}
        }

        // Rule 3c: Text-month date (any locale): "16 juin 2026 14 h 12", "16. Juni 2026 14:12"
        java.util.regex.Matcher textDmyMatcher = TEXT_MONTH_DMY_RE.matcher(t);
        if (textDmyMatcher.find()) {
            String monthWord = textDmyMatcher.group(2).toLowerCase().replaceAll("\\.$", "");
            Integer monthNum = ALL_MONTH_NAME_TO_NUM.get(monthWord);
            if (monthNum != null) {
                String matched = textDmyMatcher.group(0).trim();
                try {
                    int day = Integer.parseInt(textDmyMatcher.group(1));
                    int year = Integer.parseInt(textDmyMatcher.group(3));
                    if (day >= 1 && day <= 31) {
                        int safeDay = Math.min(day, java.time.YearMonth.of(year, monthNum).lengthOfMonth());
                        LocalDate date = LocalDate.of(year, monthNum, safeDay);
                        String expected = formatLong(date, targetLocale);
                        return buildIssue(matched, "non-localized-date",
                                "Date not localized for " + targetLocale.getDisplayLanguage(),
                                expected, targetLocale);
                    }
                } catch (Exception ignored) {}
            }
        }

        // Rule 4: Text looks like a date but is in English/wrong format
        if (LOOKS_LIKE_DATE_RE.matcher(t).find()) {
            boolean obviouslyWrong = EN_DATE_MDY_RE.matcher(t).matches()
                                  || EN_DATE_DMY_RE.matcher(t).matches();
            if (obviouslyWrong) {
                return buildIssue(t, "wrong-format",
                        "Date not localized for " + targetLocale.getDisplayLanguage(),
                        getSuggestion(t, targetLocale), targetLocale);
            }
        }

        // Rule 5: Date format pattern strings used as placeholders
        // e.g. "YYYY-MM-DD HH:mm:ss" or "YYYY-MM-DD" in input placeholders
        if (DATE_FORMAT_PATTERN_RE.matcher(t).matches()) {
            return buildIssue(t, "non-localized-placeholder",
                    "Date format pattern not localized for " + targetLocale.getDisplayLanguage(),
                    getLocalizedFormatPattern(targetLocale), targetLocale);
        }

        // Rule 6: Non-localized numeric date formats (search within text, not exact match)
        java.util.regex.Matcher isoDateTimeMatcher = ISO_DATETIME_RE.matcher(t);
        if (isoDateTimeMatcher.find()) {
            String matched = isoDateTimeMatcher.group(1);
            return buildIssue(matched, "non-localized-date",
                    "Date/time not localized for " + targetLocale.getDisplayLanguage(),
                    getSuggestionFromIso(matched, targetLocale), targetLocale);
        }
        // ISO date: "2026-06-24" (negative lookahead prevents matching datetime)
        java.util.regex.Matcher isoDateMatcher = ISO_DATE_RE.matcher(t);
        if (isoDateMatcher.find()) {
            String matched = isoDateMatcher.group(1);
            return buildIssue(matched, "non-localized-date",
                    "Date not localized for " + targetLocale.getDisplayLanguage(),
                    getSuggestionFromIso(matched, targetLocale), targetLocale);
        }
        // Truncated datetime: "06-24 19:30"
        if (TRUNC_DATETIME_RE.matcher(t).matches()) {
            return buildIssue(t, "non-localized-date",
                    "Date not localized for " + targetLocale.getDisplayLanguage(),
                    null, targetLocale);
        }
        // US-style datetime: "06/24/2026 19:30:00"
        java.util.regex.Matcher usDateTimeMatcher = US_DATETIME_RE.matcher(t);
        if (usDateTimeMatcher.find()) {
            String matched = usDateTimeMatcher.group(1);
            return buildIssue(matched, "non-localized-date",
                    "Date not localized for " + targetLocale.getDisplayLanguage(),
                    getSuggestionFromUsDate(matched, targetLocale), targetLocale);
        }
        // US-style: "06/24/2026"
        java.util.regex.Matcher usDateMatcher = US_DATE_RE.matcher(t);
        if (usDateMatcher.find()) {
            String matched = usDateMatcher.group(1);
            return buildIssue(matched, "non-localized-date",
                    "Date not localized for " + targetLocale.getDisplayLanguage(),
                    getSuggestionFromUsDate(matched, targetLocale), targetLocale);
        }
        // European dot-style: "24.06.2026" (wrong for non-European locales)
        java.util.regex.Matcher euDotMatcher = EU_DOT_DATE_RE.matcher(t);
        if (euDotMatcher.find()) {
            String lang = targetLocale.getLanguage();
            // Only flag if this is NOT a European locale that uses dot format
            if (!"de".equals(lang) && !"cs".equals(lang) && !"sk".equals(lang)
                && !"hr".equals(lang) && !"sl".equals(lang) && !"hu".equals(lang)
                && !"ro".equals(lang) && !"bg".equals(lang)) {
                String matched = euDotMatcher.group(1);
                return buildIssue(matched, "non-localized-date",
                        "Date not localized for " + targetLocale.getDisplayLanguage(),
                        null, targetLocale);
            }
        }

        return null;
    }

    // ───────────────────── Suggestion Generation ─────────────────────

    /**
     * Attempt to parse the incorrect date and reformat it in the target locale.
     * Mirrors {@code getSuggestion()} in content.js.
     */
    String getSuggestion(String text, Locale targetLocale) {
        // Pattern: "3月 2023" (bare CJK month + year)
        Matcher bareMatch = BARE_MONTH_YEAR_RE.matcher(text);
        if (bareMatch.matches()) {
            int month = Integer.parseInt(bareMatch.group(1).replaceAll("[月월]", ""));
            int year = Integer.parseInt(bareMatch.group(2));
            if (month >= 1 && month <= 12) {
                LocalDate date = LocalDate.of(year, month, 1);
                return formatYearMonth(date, targetLocale);
            }
        }

        // Pattern: "Month DD, YYYY"
        Matcher mdyMatch = EN_EXTRACT_MDY.matcher(text);
        if (mdyMatch.find()) {
            Integer monthIdx = ENGLISH_MONTHS.get(mdyMatch.group(1));
            if (monthIdx != null) {
                int day = Integer.parseInt(mdyMatch.group(2));
                int year = Integer.parseInt(mdyMatch.group(3));
                LocalDate date = LocalDate.of(year, monthIdx, Math.min(day, 28));
                return formatLong(date, targetLocale);
            }
        }

        // Pattern: "DD Month YYYY"
        Matcher dmyMatch = EN_EXTRACT_DMY.matcher(text);
        if (dmyMatch.find()) {
            Integer monthIdx = ENGLISH_MONTHS.get(dmyMatch.group(2));
            if (monthIdx != null) {
                int day = Integer.parseInt(dmyMatch.group(1));
                int year = Integer.parseInt(dmyMatch.group(3));
                LocalDate date = LocalDate.of(year, monthIdx, Math.min(day, 28));
                return formatLong(date, targetLocale);
            }
        }

        // Pattern: "Month YYYY"
        Matcher myMatch = EN_MONTH_YEAR.matcher(text);
        if (myMatch.matches()) {
            Integer monthIdx = ENGLISH_MONTHS.get(myMatch.group(1));
            if (monthIdx != null) {
                LocalDate date = LocalDate.of(Integer.parseInt(myMatch.group(2)), monthIdx, 1);
                return formatYearMonth(date, targetLocale);
            }
        }

        return null;
    }

    // ───────────────────── Helpers ─────────────────────

    private Locale resolveLocale(String code) {
        if (code == null) return null;
        // Check SN aliases first
        String resolved = SN_ALIAS_MAP.getOrDefault(code, code);
        Locale locale = LOCALE_MAP.get(resolved);
        if (locale != null) return locale;
        // Try BCP47 tag directly
        try {
            Locale parsed = Locale.forLanguageTag(resolved);
            if (!parsed.getLanguage().isEmpty()) return parsed;
        } catch (Exception ignored) {}
        return null;
    }

    /**
     * Scroll the page (and any scrollable shadow DOM containers) to ensure
     * lazy-loaded content below the fold is rendered before extraction.
     */
    private void scrollPage(WebDriver driver) {
        try {
            JavascriptExecutor js = (JavascriptExecutor) driver;
            // Scroll the main document
            js.executeScript(
                "function scrollAll(root) {" +
                "  if (!root) return;" +
                "  var scrollable = root === document ? document.documentElement : root;" +
                "  if (scrollable.scrollHeight > scrollable.clientHeight) {" +
                "    scrollable.scrollTop = scrollable.scrollHeight;" +
                "  }" +
                "  var els = (root.querySelectorAll ? root.querySelectorAll('*') : []);" +
                "  for (var i = 0; i < els.length; i++) {" +
                "    if (els[i].scrollHeight > els[i].clientHeight + 50) {" +
                "      els[i].scrollTop = els[i].scrollHeight;" +
                "    }" +
                "    if (els[i].shadowRoot) scrollAll(els[i].shadowRoot);" +
                "    if (els[i].tagName === 'IFRAME') {" +
                "      try { scrollAll(els[i].contentDocument); } catch(e) {}" +
                "    }" +
                "  }" +
                "}" +
                "scrollAll(document);"
            );
            // Brief pause to let lazy content render
            Thread.sleep(1500);
            // Scroll back to top for screenshot
            js.executeScript("window.scrollTo(0, 0);");
            log.info("Page scrolled to load all content");
        } catch (Exception e) {
            log.warn("Error during page scroll: {}", e.getMessage());
        }
    }

    /**
     * Generate a localized date format pattern string for the target locale.
     * Used as a suggestion when "YYYY-MM-DD" placeholder is detected.
     */
    private String getLocalizedFormatPattern(Locale locale) {
        try {
            LocalDate sample = LocalDate.of(2024, 12, 31);
            String formatted = DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT)
                    .withLocale(locale).format(sample);
            return formatted + " (localized format example)";
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Build the set of "correct" date strings for multiple sample dates in all four
     * format styles (FULL, LONG, MEDIUM, SHORT). Text matching any of these
     * is considered properly localised and won't be flagged.
     *
     * Per SN documentation, valid Asian date formats include:
     *   ja: Full=2023年10月16日, Long=2023/10/16, Medium=2023/10/16, Short=23/10/16
     *   ko: Full=2023년 10월 16일 월요일, Long=2023년 10월 16일, Medium=2023. 10. 16
     *   zh-CN: Full=2023年10月16日 星期一, Long=2023年10月16日, Medium=2023-10-16
     */
    private Set<String> buildExpectedPatterns(Locale locale) {
        Set<String> patterns = new HashSet<>();
        // Use multiple sample dates to build a broader whitelist
        LocalDate[] samples = {
            LocalDate.of(2023, 1, 18),
            LocalDate.of(2024, 6, 15),
            LocalDate.of(2025, 12, 31),
            LocalDate.now()
        };
        for (LocalDate sample : samples) {
            for (FormatStyle style : FormatStyle.values()) {
                try {
                    String formatted = DateTimeFormatter.ofLocalizedDate(style)
                            .withLocale(locale).format(sample);
                    patterns.add(formatted);
                } catch (Exception ignored) {}
            }
        }
        // Add CJK slash-format dates as valid patterns (per SN Intl.DateTimeFormat output)
        String lang = locale.getLanguage();
        if ("ja".equals(lang)) {
            // Japanese long/medium uses slash format: YYYY/MM/DD
            for (LocalDate s : samples) {
                patterns.add(String.format("%d/%d/%d", s.getYear(), s.getMonthValue(), s.getDayOfMonth()));
                patterns.add(String.format("%d/%02d/%02d", s.getYear(), s.getMonthValue(), s.getDayOfMonth()));
                // Short: YY/MM/DD
                patterns.add(String.format("%02d/%d/%d", s.getYear() % 100, s.getMonthValue(), s.getDayOfMonth()));
                patterns.add(String.format("%02d/%02d/%02d", s.getYear() % 100, s.getMonthValue(), s.getDayOfMonth()));
            }
        } else if ("ko".equals(lang)) {
            // Korean medium/short: YYYY. MM. DD or YYYY. M. D
            for (LocalDate s : samples) {
                patterns.add(String.format("%d. %d. %d", s.getYear(), s.getMonthValue(), s.getDayOfMonth()));
                patterns.add(String.format("%d. %02d. %02d", s.getYear(), s.getMonthValue(), s.getDayOfMonth()));
            }
        } else if ("zh".equals(lang)) {
            // Chinese simplified medium: YYYY-MM-DD, short: YY-MM-DD
            for (LocalDate s : samples) {
                patterns.add(String.format("%d-%d-%d", s.getYear(), s.getMonthValue(), s.getDayOfMonth()));
                patterns.add(String.format("%02d-%d-%d", s.getYear() % 100, s.getMonthValue(), s.getDayOfMonth()));
                patterns.add(String.format("%02d-%02d-%02d", s.getYear() % 100, s.getMonthValue(), s.getDayOfMonth()));
            }
        }
        return patterns;
    }

    private List<String> extractTextNodes(WebDriver driver) {
        try {
            JavascriptExecutor js = (JavascriptExecutor) driver;
            Object result = js.executeScript(EXTRACT_TEXT_NODES_JS);
            if (result == null) return Collections.emptyList();

            String json = result.toString();
            // Simple JSON array parser — each element is a quoted string
            List<String> texts = new ArrayList<>();
            json = json.substring(1, json.length() - 1); // strip [ ]
            if (json.isEmpty()) return texts;

            // Use a proper split that respects escaped quotes
            StringBuilder current = new StringBuilder();
            boolean inString = false;
            boolean escaped = false;
            for (int i = 0; i < json.length(); i++) {
                char c = json.charAt(i);
                if (escaped) {
                    current.append(c);
                    escaped = false;
                } else if (c == '\\') {
                    escaped = true;
                } else if (c == '"') {
                    inString = !inString;
                } else if (c == ',' && !inString) {
                    String val = current.toString().trim();
                    if (!val.isEmpty()) texts.add(val);
                    current.setLength(0);
                } else {
                    current.append(c);
                }
            }
            String last = current.toString().trim();
            if (!last.isEmpty()) texts.add(last);

            return texts;
        } catch (Exception e) {
            log.error("Failed to extract text nodes: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    private DateIssue buildIssue(String found, String type, String reason, String suggestion, Locale locale) {
        DateIssue issue = new DateIssue();
        issue.setFound(found);
        issue.setType(type);
        issue.setReason(reason);
        issue.setExpected(suggestion != null ? suggestion : "");
        // Build how-to-fix with SN Utah+ API references (KB0562050)
        String howToFix = buildHowToFix(type, found, locale);
        issue.setSuggestion(howToFix);
        return issue;
    }

    /**
     * Build a "How to fix" suggestion referencing ServiceNow Utah+ APIs
     * per KB0562050 (Locale Support - Date and Time).
     *
     * Includes:
     * - Server-side fix: GlideDate/GlideDateTime.getDisplayValueLang(style)
     * - Client-side fix: Intl.DateTimeFormat with dateStyle/timeStyle
     * - Locale-specific expected output examples for all 4 styles
     * - Special SN code note: fq→fr-CA, zt→zh-Hant, pb→pt-BR
     */
    private String buildHowToFix(String type, String found, Locale locale) {
        return "https://buildtools1.service-now.com/kb_view.do?sysparm_article=KB0562050";
    }

    private String formatLong(LocalDate date, Locale locale) {
        try {
            return DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG)
                    .withLocale(locale).format(date);
        } catch (Exception e) {
            return null;
        }
    }

    private String formatYearMonth(LocalDate date, Locale locale) {
        try {
            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("MMMM yyyy", locale);
            return fmt.format(date);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Parse an ISO date/datetime string and reformat it for the target locale.
     * Handles "YYYY-MM-DD", "YYYY-MM-DD HH:mm", "YYYY-MM-DD HH:mm:ss".
     */
    private String getSuggestionFromIso(String text, Locale locale) {
        try {
            String datePart = text.contains(" ") ? text.split("\\s+")[0] : text;
            String[] parts = datePart.split("-");
            int year = Integer.parseInt(parts[0]);
            int month = Integer.parseInt(parts[1]);
            int day = Integer.parseInt(parts[2]);
            LocalDate date = LocalDate.of(year, month, day);
            return DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG)
                    .withLocale(locale).format(date);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Parse a US-style date (MM/DD/YYYY) and reformat it for the target locale.
     */
    private String getSuggestionFromUsDate(String text, Locale locale) {
        try {
            String datePart = text.contains(" ") ? text.split("\\s+")[0] : text;
            String[] parts = datePart.split("/");
            int month = Integer.parseInt(parts[0]);
            int day = Integer.parseInt(parts[1]);
            int year = Integer.parseInt(parts[2]);
            LocalDate date = LocalDate.of(year, month, Math.min(day, 28));
            return DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG)
                    .withLocale(locale).format(date);
        } catch (Exception e) {
            return null;
        }
    }
}
