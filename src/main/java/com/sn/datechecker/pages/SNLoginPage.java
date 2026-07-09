package com.sn.datechecker.pages;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.Select;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;

/**
 * Page Object for ServiceNow login page.
 */
public class SNLoginPage {

    private static final Logger log = LoggerFactory.getLogger(SNLoginPage.class);

    private final WebDriver driver;
    private final WebDriverWait wait;

    // Login form selectors (ServiceNow standard login)
    private static final By USERNAME_FIELD = By.id("user_name");
    private static final By PASSWORD_FIELD = By.id("user_password");
    private static final By LOGIN_BUTTON = By.id("sysverb_login");
    private static final By LANGUAGE_SELECT = By.id("language_select");

    public SNLoginPage(WebDriver driver) {
        this.driver = driver;
        this.wait = new WebDriverWait(driver, Duration.ofSeconds(45));
    }

    /**
     * Navigate to the ServiceNow instance login page.
     */
    public SNLoginPage navigateTo(String instanceUrl) {
        String loginUrl = instanceUrl.endsWith("/") ? instanceUrl + "login.do" : instanceUrl + "/login.do";
        log.info("Navigating to: {}", loginUrl);
        driver.get(loginUrl);
        return this;
    }

    /**
     * Login with the given credentials and select the target language.
     * ServiceNow reloads the login page when the language dropdown changes,
     * so we select language first, wait for reload, then fill in credentials.
     */
    public void login(String username, String password, String locale) {
        log.info("Logging in as: {} with locale: {}", username, locale);
        try {
            // Step 1: Wait for the login page to load
            wait.until(ExpectedConditions.visibilityOfElementLocated(USERNAME_FIELD));

            // Step 2: Select the target language (this triggers a page reload)
            selectLanguage(locale);

            // Step 3: After reload, re-locate and fill in credentials
            WebElement userField = wait.until(ExpectedConditions.visibilityOfElementLocated(USERNAME_FIELD));
            userField.clear();
            userField.sendKeys(username);

            WebElement passField = driver.findElement(PASSWORD_FIELD);
            passField.clear();
            passField.sendKeys(password);

            // Step 4: Click login
            driver.findElement(LOGIN_BUTTON).click();

            // Wait for login to complete (URL should change away from login.do)
            wait.until(ExpectedConditions.not(
                    ExpectedConditions.urlContains("login.do")));
            log.info("Login successful. Current URL: {}", driver.getCurrentUrl());

        } catch (Exception e) {
            log.error("Login failed: {}", e.getMessage());
            throw new RuntimeException("ServiceNow login failed", e);
        }
    }

    /**
     * Select the language from the login page dropdown.
     * The dropdown value uses ServiceNow language codes (e.g. "ar", "ja", "de", "fr").
     * SN reloads the page on language change, so we wait for the page to reload
     * and the login form to reappear.
     */
    private void selectLanguage(String locale) {
        try {
            WebElement langDropdown = wait.until(
                    ExpectedConditions.visibilityOfElementLocated(LANGUAGE_SELECT));
            Select langSelect = new Select(langDropdown);

            // Check if already the correct language
            String currentValue = langSelect.getFirstSelectedOption().getAttribute("value");
            if (locale.equals(currentValue)) {
                log.info("Language already set to: {}", locale);
                return;
            }

            log.info("Changing language from '{}' to '{}'", currentValue, locale);

            // Remember current page state to detect reload
            WebElement loginBtn = driver.findElement(LOGIN_BUTTON);

            // Select the language — this triggers page reload via onchange
            langSelect.selectByValue(locale);

            // Wait for the page to reload (old login button becomes stale)
            wait.until(ExpectedConditions.stalenessOf(loginBtn));

            // Wait for the reloaded login form to appear
            wait.until(ExpectedConditions.visibilityOfElementLocated(USERNAME_FIELD));
            log.info("Page reloaded with language: {}", locale);

        } catch (Exception e) {
            log.warn("Could not select language '{}' from dropdown: {}", locale, e.getMessage());
            log.warn("Proceeding with current language.");
        }
    }

    /**
     * Check if we're already logged in (not on a login page).
     */
    public boolean isLoggedIn() {
        return !driver.getCurrentUrl().contains("login.do");
    }
}
