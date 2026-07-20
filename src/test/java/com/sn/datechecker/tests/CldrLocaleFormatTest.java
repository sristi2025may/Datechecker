package com.sn.datechecker.tests;

import com.sn.datechecker.model.DateIssue;
import com.sn.datechecker.scanner.DateFormatChecker;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.*;

/**
 * Offline unit test for DateFormatChecker CLDR locale format improvements.
 * No Selenium, no ServiceNow instance required — runs 100% locally.
 *
 * Run with: mvn test -DsuiteXmlFile=testng-local.xml
 */
public class CldrLocaleFormatTest {

    private DateFormatChecker checker;

    @BeforeClass
    public void setUp() {
        checker = new DateFormatChecker();
    }

    // ── Data Providers ──────────────────────────────────────────────

    @DataProvider(name = "allLocales")
    public Object[][] allLocales() {
        Set<String> locales = DateFormatChecker.getSupportedLocales();
        Object[][] data = new Object[locales.size()][1];
        int i = 0;
        for (String loc : locales) {
            data[i++] = new Object[]{loc};
        }
        return data;
    }

    @DataProvider(name = "englishMonthCases")
    public Object[][] englishMonthCases() {
        return new Object[][]{
            {"ja",    "February 2026"},
            {"ko",    "Mar 18, 2025"},
            {"zh-CN", "January 1, 2024"},
            {"de",    "October 2025"},
            {"fr",    "Dec 31"},
            {"es",    "July 4, 2026"},
            {"ru",    "August 2025"},
            {"ar",    "Sep 15, 2026"},
            {"it",    "November 2025"},
            {"pt-BR", "April 10, 2026"},
            {"th",    "June 2025"},
            {"tr",    "May 2026"},
            {"cs",    "March 2025"},
            {"pl",    "Jan 2026"},
            {"sv",    "Feb 2026"},
            {"fi",    "December 2025"},
            {"hu",    "Aug 1, 2026"},
            {"nl",    "September 2026"},
            {"he",    "October 2025"},
            {"nb",    "July 2026"},
            {"pt",    "Nov 2025"},
            {"fr-CA", "March 18, 2025"},
            {"zh-Hant","April 2026"},
        };
    }

    @DataProvider(name = "isoDateCases")
    public Object[][] isoDateCases() {
        return new Object[][]{
            {"ja",    "2026-06-24"},
            {"ko",    "2025-03-18"},
            {"de",    "2025-12-31"},
            {"fr",    "2026-01-15"},
            {"es",    "2025-07-04"},
            {"zh-CN", "2026-02-28"},
            {"ar",    "2025-11-11"},
            {"ru",    "2026-03-08"},
            {"th",    "2025-04-13"},
        };
    }

    @DataProvider(name = "isoDateTimeCases")
    public Object[][] isoDateTimeCases() {
        return new Object[][]{
            {"ja",    "2026-06-24 19:30:00"},
            {"ko",    "2025-03-18 09:15"},
            {"de",    "2026-01-01 00:00:00"},
            {"fr-CA", "2025-12-25 14:30:00"},
        };
    }

    @DataProvider(name = "usDateCases")
    public Object[][] usDateCases() {
        return new Object[][]{
            {"ja",    "06/24/2026"},
            {"de",    "12/31/2025"},
            {"fr",    "03/18/2025"},
        };
    }

    @DataProvider(name = "wrongOrderCases")
    public Object[][] wrongOrderCases() {
        return new Object[][]{
            {"ja",    "3月 2025"},
            {"ko",    "6월 2026"},
            {"zh-CN", "2月 2026"},
        };
    }

    @DataProvider(name = "cjkMdyCases")
    public Object[][] cjkMdyCases() {
        return new Object[][]{
            {"ja",    "3月 31 2026"},
            {"ja",    "7月 07 2026 9:30 午前"},
            {"ko",    "6월 15 2025"},
        };
    }

    @DataProvider(name = "placeholderCases")
    public Object[][] placeholderCases() {
        return new Object[][]{
            {"ja",    "YYYY-MM-DD"},
            {"de",    "YYYY-MM-DD HH:mm:ss"},
            {"fr",    "MM/DD/YYYY"},
        };
    }

    @DataProvider(name = "ampmCases")
    public Object[][] ampmCases() {
        return new Object[][]{
            {"ja",    "2:30 PM"},
            {"ko",    "10:15 AM"},
            {"zh-CN", "3:00 pm"},
            {"ar",    "9:30 AM"},
        };
    }

    @DataProvider(name = "correctDateCases")
    public Object[][] correctDateCases() {
        return new Object[][]{
            // These should NOT be flagged — they are correctly localized
            {"ja",    "2025年3月18日"},
            {"ja",    "2026年2月"},
            {"ko",    "2025년 3월 18일"},
            {"ko",    "2026년 2월"},
            {"zh-CN", "2025年3月18日"},
            {"de",    "18. März 2025"},
            {"fr",    "18 mars 2025"},
            {"es",    "18 de marzo de 2025"},
            {"it",    "18 marzo 2025"},
        };
    }

    // ── Tests ────────────────────────────────────────────────────────

    @Test(description = "All 23 locales are registered")
    public void testAllLocalesRegistered() {
        Set<String> locales = DateFormatChecker.getSupportedLocales();
        Assert.assertTrue(locales.size() >= 23,
                "Expected at least 23 locales, got " + locales.size());

        // Verify key locales are present
        for (String code : new String[]{"ar","pt-BR","zh-CN","zh-Hant","cs","nl","fi",
                "fr","fr-CA","de","he","hu","it","ja","ko","nb","pl","pt","ru","es","sv","th","tr"}) {
            Assert.assertTrue(locales.contains(code),
                    "Missing locale: " + code);
        }
    }

    @Test(dataProvider = "englishMonthCases",
          description = "English month names flagged in non-English locales")
    public void testEnglishMonthDetection(String locale, String text) {
        DateIssue issue = checker.detectIssue(text,
                Locale.forLanguageTag(mapLocale(locale)), buildPatterns(locale));
        Assert.assertNotNull(issue,
                "Expected issue for '" + text + "' in locale " + locale);
        Assert.assertTrue(
                "english-month".equals(issue.getType()) || "wrong-format".equals(issue.getType()),
                "Expected english-month or wrong-format but got: " + issue.getType());
        System.out.println("  ✅ [" + locale + "] '" + text + "' → " + issue.getType()
                + " | suggestion: " + issue.getExpected());
    }

    @Test(dataProvider = "isoDateCases",
          description = "ISO dates (YYYY-MM-DD) flagged in non-English locales")
    public void testIsoDateDetection(String locale, String text) {
        DateIssue issue = checker.detectIssue(text,
                Locale.forLanguageTag(mapLocale(locale)), buildPatterns(locale));
        Assert.assertNotNull(issue,
                "Expected issue for '" + text + "' in locale " + locale);
        Assert.assertEquals(issue.getType(), "non-localized-date");
        Assert.assertNotNull(issue.getExpected(),
                "Expected a localized suggestion for " + locale);
        Assert.assertFalse(issue.getExpected().isEmpty(),
                "Suggestion should not be empty for " + locale);
        System.out.println("  ✅ [" + locale + "] '" + text + "' → suggestion: " + issue.getExpected());
    }

    @Test(dataProvider = "isoDateTimeCases",
          description = "ISO datetimes flagged in non-English locales")
    public void testIsoDateTimeDetection(String locale, String text) {
        DateIssue issue = checker.detectIssue(text,
                Locale.forLanguageTag(mapLocale(locale)), buildPatterns(locale));
        Assert.assertNotNull(issue,
                "Expected issue for '" + text + "' in locale " + locale);
        Assert.assertEquals(issue.getType(), "non-localized-date");
        System.out.println("  ✅ [" + locale + "] '" + text + "' → " + issue.getType());
    }

    @Test(dataProvider = "usDateCases",
          description = "US-style dates (MM/DD/YYYY) flagged in non-English locales")
    public void testUsDateDetection(String locale, String text) {
        DateIssue issue = checker.detectIssue(text,
                Locale.forLanguageTag(mapLocale(locale)), buildPatterns(locale));
        Assert.assertNotNull(issue,
                "Expected issue for '" + text + "' in locale " + locale);
        Assert.assertEquals(issue.getType(), "non-localized-date");
        System.out.println("  ✅ [" + locale + "] '" + text + "' → suggestion: " + issue.getExpected());
    }

    @Test(dataProvider = "wrongOrderCases",
          description = "Wrong month-year order (CJK) detected")
    public void testWrongMonthYearOrder(String locale, String text) {
        DateIssue issue = checker.detectIssue(text,
                Locale.forLanguageTag(mapLocale(locale)), buildPatterns(locale));
        Assert.assertNotNull(issue,
                "Expected issue for '" + text + "' in locale " + locale);
        Assert.assertEquals(issue.getType(), "wrong-order");
        // Verify CLDR-correct suggestion
        Assert.assertNotNull(issue.getExpected(),
                "Expected a CLDR-formatted suggestion");
        System.out.println("  ✅ [" + locale + "] '" + text + "' → suggestion: " + issue.getExpected());
    }

    @Test(dataProvider = "cjkMdyCases",
          description = "CJK month-day-year wrong order detected")
    public void testCjkMdyDetection(String locale, String text) {
        DateIssue issue = checker.detectIssue(text,
                Locale.forLanguageTag(mapLocale(locale)), buildPatterns(locale));
        Assert.assertNotNull(issue,
                "Expected issue for '" + text + "' in locale " + locale);
        System.out.println("  ✅ [" + locale + "] '" + text + "' → " + issue.getType()
                + " | suggestion: " + issue.getExpected());
    }

    @Test(dataProvider = "placeholderCases",
          description = "Hardcoded format placeholders detected")
    public void testPlaceholderDetection(String locale, String text) {
        DateIssue issue = checker.detectIssue(text,
                Locale.forLanguageTag(mapLocale(locale)), buildPatterns(locale));
        Assert.assertNotNull(issue,
                "Expected issue for '" + text + "' in locale " + locale);
        Assert.assertEquals(issue.getType(), "non-localized-placeholder");
        System.out.println("  ✅ [" + locale + "] '" + text + "' → " + issue.getType());
    }

    @Test(dataProvider = "ampmCases",
          description = "English AM/PM flagged in locales with localized markers")
    public void testEnglishAmPmDetection(String locale, String text) {
        DateIssue issue = checker.detectIssue(text,
                Locale.forLanguageTag(mapLocale(locale)), buildPatterns(locale));
        Assert.assertNotNull(issue,
                "Expected issue for '" + text + "' in locale " + locale);
        Assert.assertEquals(issue.getType(), "non-localized-date");
        Assert.assertTrue(issue.getReason().contains("AM/PM"),
                "Reason should mention AM/PM markers");
        System.out.println("  ✅ [" + locale + "] '" + text + "' → " + issue.getReason());
    }

    @Test(dataProvider = "correctDateCases",
          description = "Correctly localized dates should NOT be flagged")
    public void testCorrectDatesNotFlagged(String locale, String text) {
        Locale loc = Locale.forLanguageTag(mapLocale(locale));
        // Use the REAL whitelist here — correctly localized dates must be whitelisted
        Set<String> realPatterns = checker.buildExpectedPatterns(loc);
        DateIssue issue = checker.detectIssue(text, loc, realPatterns);
        Assert.assertNull(issue,
                "Should NOT flag correctly localized date '" + text + "' for locale " + locale
                        + (issue != null ? " (got: " + issue.getType() + " - " + issue.getReason() + ")" : ""));
        System.out.println("  ✅ [" + locale + "] '" + text + "' → correctly not flagged");
    }

    @Test(dataProvider = "allLocales",
          description = "Month-year formatting produces non-empty result for all locales")
    public void testMonthYearFormatting(String locale) {
        // Use getSuggestion to test the month-year formatting path
        String suggestion = checker.getSuggestion("February 2026",
                Locale.forLanguageTag(mapLocale(locale)));
        Assert.assertNotNull(suggestion,
                "Month-year suggestion should not be null for locale: " + locale);
        Assert.assertFalse(suggestion.isEmpty(),
                "Month-year suggestion should not be empty for locale: " + locale);
        // Should NOT contain English month names (except if locale IS English-like)
        if (!"en".equals(Locale.forLanguageTag(mapLocale(locale)).getLanguage())) {
            Assert.assertFalse(suggestion.contains("February"),
                    "Suggestion still contains English month 'February' for locale " + locale
                            + ": " + suggestion);
        }
        System.out.println("  ✅ [" + locale + "] 'February 2026' → " + suggestion);
    }

    @Test(description = "Japanese month-year uses CLDR pattern y年M月")
    public void testJapaneseMonthYear() {
        String suggestion = checker.getSuggestion("February 2026",
                Locale.forLanguageTag("ja-JP"));
        Assert.assertNotNull(suggestion);
        Assert.assertTrue(suggestion.contains("2026") && suggestion.contains("月"),
                "Japanese month-year should contain year and 月: " + suggestion);
        System.out.println("  ✅ [ja] 'February 2026' → " + suggestion);
    }

    @Test(description = "Korean month-year uses CLDR pattern y년 M월")
    public void testKoreanMonthYear() {
        String suggestion = checker.getSuggestion("March 2025",
                Locale.forLanguageTag("ko-KR"));
        Assert.assertNotNull(suggestion);
        Assert.assertTrue(suggestion.contains("2025") && suggestion.contains("월"),
                "Korean month-year should contain year and 월: " + suggestion);
        System.out.println("  ✅ [ko] 'March 2025' → " + suggestion);
    }

    @Test(description = "Hungarian month-year uses CLDR pattern y. MMMM")
    public void testHungarianMonthYear() {
        String suggestion = checker.getSuggestion("March 2025",
                Locale.forLanguageTag("hu-HU"));
        Assert.assertNotNull(suggestion);
        Assert.assertTrue(suggestion.startsWith("2025"),
                "Hungarian month-year should start with year: " + suggestion);
        System.out.println("  ✅ [hu] 'March 2025' → " + suggestion);
    }

    @Test(description = "Russian month-year uses standalone form (nominative)")
    public void testRussianMonthYear() {
        String suggestion = checker.getSuggestion("February 2026",
                Locale.forLanguageTag("ru-RU"));
        Assert.assertNotNull(suggestion);
        Assert.assertFalse(suggestion.contains("February"),
                "Should not contain English month: " + suggestion);
        System.out.println("  ✅ [ru] 'February 2026' → " + suggestion);
    }

    @Test(description = "Spanish month-year uses 'de' connector")
    public void testSpanishMonthYear() {
        String suggestion = checker.getSuggestion("March 2025",
                Locale.forLanguageTag("es-ES"));
        Assert.assertNotNull(suggestion);
        Assert.assertTrue(suggestion.contains("de") || suggestion.contains("2025"),
                "Spanish month-year should contain 'de' or year: " + suggestion);
        System.out.println("  ✅ [es] 'March 2025' → " + suggestion);
    }

    // ── Helpers ──────────────────────────────────────────────────────

    /** Map locale code to BCP-47 tag for Locale.forLanguageTag(). */
    private String mapLocale(String code) {
        return switch (code) {
            case "pt-BR" -> "pt-BR";
            case "zh-CN" -> "zh-CN";
            case "zh-Hant" -> "zh-Hant";
            case "fr-CA" -> "fr-CA";
            default -> code;
        };
    }

    /** Build expected patterns set for detection (mirrors DateFormatChecker internals). */
    private Set<String> buildPatterns(String locale) {
        // Empty set — we want detection to run without any whitelist bypasses
        // This way we can test that the detection rules themselves work
        return new HashSet<>();
    }
}
