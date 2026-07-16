package com.mattoid.scheduled.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class PlaywrightBrowserLocatorTest {

    private String originalUserHome;

    @BeforeEach
    void saveUserHome() {
        originalUserHome = System.getProperty("user.home");
    }

    @AfterEach
    void restoreUserHome() {
        if (originalUserHome != null) {
            System.setProperty("user.home", originalUserHome);
        }
    }

    @Test
    @EnabledOnOs(OS.LINUX)
    void linux_findChromiumExecutable_returnsLatestVersion(@TempDir Path tempDir) throws Exception {
        System.setProperty("user.home", tempDir.toString());
        Path cacheDir = tempDir.resolve(".cache/ms-playwright");
        Files.createDirectories(cacheDir.resolve("chromium-1234/chrome-linux"));
        Files.createFile(cacheDir.resolve("chromium-1234/chrome-linux/chrome")).toFile().setExecutable(true);
        Files.createDirectories(cacheDir.resolve("chromium-567/chrome-linux"));
        Files.createFile(cacheDir.resolve("chromium-567/chrome-linux/chrome")).toFile().setExecutable(true);

        PlaywrightBrowserLocator locator = new PlaywrightBrowserLocator();
        Path executable = locator.findChromiumExecutable();

        assertNotNull(executable);
        assertTrue(executable.toString().contains("chromium-1234"));
        assertTrue(executable.endsWith("chrome-linux/chrome"));
    }

    @Test
    @EnabledOnOs(OS.LINUX)
    void linux_noChromium_returnsNull(@TempDir Path tempDir) {
        System.setProperty("user.home", tempDir.toString());
        PlaywrightBrowserLocator locator = new PlaywrightBrowserLocator();
        assertNull(locator.findChromiumExecutable());
    }

    @Test
    @EnabledOnOs(OS.LINUX)
    void linux_nonExecutableChrome_returnsNull(@TempDir Path tempDir) throws Exception {
        System.setProperty("user.home", tempDir.toString());
        Path cacheDir = tempDir.resolve(".cache/ms-playwright");
        Files.createDirectories(cacheDir.resolve("chromium-1234/chrome-linux"));
        Files.createFile(cacheDir.resolve("chromium-1234/chrome-linux/chrome"));

        PlaywrightBrowserLocator locator = new PlaywrightBrowserLocator();
        assertNull(locator.findChromiumExecutable());
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    void windows_findChromiumExecutable_returnsExe(@TempDir Path tempDir) throws Exception {
        Path cacheDir = tempDir.resolve("ms-playwright");
        PlaywrightBrowserLocator locator = new TestableLocator(cacheDir);
        Files.createDirectories(cacheDir.resolve("chromium-1234/chrome-win"));
        Files.createFile(cacheDir.resolve("chromium-1234/chrome-win/chrome.exe"));

        Path executable = locator.findChromiumExecutable();

        assertNotNull(executable);
        assertTrue(executable.endsWith("chrome-win/chrome.exe"));
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    void windows_nonExeFile_returnsNull(@TempDir Path tempDir) throws Exception {
        Path cacheDir = tempDir.resolve("ms-playwright");
        PlaywrightBrowserLocator locator = new TestableLocator(cacheDir);
        Files.createDirectories(cacheDir.resolve("chromium-1234/chrome-win"));
        Files.createFile(cacheDir.resolve("chromium-1234/chrome-win/chrome"));

        assertNull(locator.findChromiumExecutable());
    }

    @Test
    void isChromiumPresent_whenFound_returnsTrue(@TempDir Path tempDir) throws Exception {
        Path cacheDir = tempDir.resolve("ms-playwright");
        Files.createDirectories(cacheDir.resolve("chromium-1/chrome-win"));
        Files.createFile(cacheDir.resolve("chromium-1/chrome-win/chrome.exe"));

        PlaywrightBrowserLocator locator = new TestableLocator(cacheDir);
        assertTrue(locator.isChromiumPresent());
    }

    @Test
    void isChromiumPresent_whenNotFound_returnsFalse(@TempDir Path tempDir) {
        PlaywrightBrowserLocator locator = new TestableLocator(tempDir.resolve("ms-playwright"));
        assertFalse(locator.isChromiumPresent());
    }

    private static class TestableLocator extends PlaywrightBrowserLocator {
        private final Path cacheDir;

        TestableLocator(Path cacheDir) {
            this.cacheDir = cacheDir;
        }

        @Override
        Path getPlaywrightCacheDir() {
            return cacheDir;
        }
    }
}
