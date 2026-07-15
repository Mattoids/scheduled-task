package com.mattoid.scheduled.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DependencyCheckServiceTest {

    @Test
    @DisabledOnOs(OS.LINUX)
    void nonLinux_usesBrowserCapabilityService() {
        PlaywrightBrowserLocator locator = mock(PlaywrightBrowserLocator.class);
        BrowserCapabilityService browserCapability = mock(BrowserCapabilityService.class);
        when(browserCapability.isChromiumAvailable()).thenReturn(true);

        DependencyCheckService service = new DependencyCheckService(browserCapability, locator);
        List<DependencyCheckService.DependencyItem> items = service.checkDependencies();

        assertEquals(1, items.size());
        DependencyCheckService.DependencyItem chromium = items.get(0);
        assertEquals("chromium", chromium.key());
        assertEquals("Chromium 内核", chromium.name());
        assertTrue(chromium.available());
        assertFalse(chromium.installable());
        verify(browserCapability).isChromiumAvailable();
        verifyNoInteractions(locator);
    }

    @Test
    @DisabledOnOs(OS.LINUX)
    void nonLinux_chromiumUnavailable_markedInstallable() {
        PlaywrightBrowserLocator locator = mock(PlaywrightBrowserLocator.class);
        BrowserCapabilityService browserCapability = mock(BrowserCapabilityService.class);
        when(browserCapability.isChromiumAvailable()).thenReturn(false);

        DependencyCheckService service = new DependencyCheckService(browserCapability, locator);
        List<DependencyCheckService.DependencyItem> items = service.checkDependencies();

        assertEquals(1, items.size());
        assertFalse(items.get(0).available());
        assertTrue(items.get(0).installable());
    }

    @Test
    void checkDependencies_usesCacheWithinTtl() {
        PlaywrightBrowserLocator locator = mock(PlaywrightBrowserLocator.class);
        BrowserCapabilityService browserCapability = mock(BrowserCapabilityService.class);
        when(browserCapability.isChromiumAvailable()).thenReturn(false);

        DependencyCheckService service = new DependencyCheckService(browserCapability, locator);
        List<DependencyCheckService.DependencyItem> first = service.checkDependencies();
        List<DependencyCheckService.DependencyItem> second = service.checkDependencies();

        assertEquals(first, second);
        verify(browserCapability, times(1)).isChromiumAvailable();
    }

    @Test
    void refreshDependencies_alwaysReChecks() {
        PlaywrightBrowserLocator locator = mock(PlaywrightBrowserLocator.class);
        BrowserCapabilityService browserCapability = mock(BrowserCapabilityService.class);
        when(browserCapability.isChromiumAvailable()).thenReturn(false, true);

        DependencyCheckService service = new DependencyCheckService(browserCapability, locator);
        List<DependencyCheckService.DependencyItem> first = service.checkDependencies();
        List<DependencyCheckService.DependencyItem> second = service.refreshDependencies();

        assertFalse(first.get(0).available());
        assertTrue(second.get(0).available());
        verify(browserCapability, times(2)).isChromiumAvailable();
    }

    @Test
    void parseMissingLibsFromLddOutput_findsNotFoundLibs() {
        String output = """
                linux-vdso.so.1 =>  (0x00007fff...)
                libssl.so.3 => not found
                libcrypto.so.3 => /lib/x86_64-linux-gnu/libcrypto.so.3
                libmissing.so.1 => not found
                """;
        List<String> missing = DependencyCheckService.parseMissingLibsFromLddOutput(output);
        assertEquals(List.of("libssl.so.3", "libmissing.so.1"), missing);
    }

    @Test
    void parseMissingLibsFromLddOutput_dynamicExecutableHint_returnsEmpty() {
        String output = "\tnot a dynamic executable\n";
        List<String> missing = DependencyCheckService.parseMissingLibsFromLddOutput(output);
        assertTrue(missing.isEmpty());
    }

    @Test
    void parseMissingLibsFromLddOutput_nullInput_returnsEmpty() {
        assertTrue(DependencyCheckService.parseMissingLibsFromLddOutput(null).isEmpty());
    }

    @Test
    @EnabledOnOs(OS.LINUX)
    void linux_executableNotFound_returnsInstallable() {
        PlaywrightBrowserLocator locator = mock(PlaywrightBrowserLocator.class);
        BrowserCapabilityService browserCapability = mock(BrowserCapabilityService.class);
        when(locator.findChromiumExecutable()).thenReturn(null);

        DependencyCheckService service = new DependencyCheckService(browserCapability, locator);
        List<DependencyCheckService.DependencyItem> items = service.checkDependencies();

        assertEquals(1, items.size());
        DependencyCheckService.DependencyItem chromium = items.get(0);
        assertEquals("chromium", chromium.key());
        assertFalse(chromium.available());
        assertTrue(chromium.installable());
        verifyNoInteractions(browserCapability);
    }

    @Test
    @EnabledOnOs(OS.LINUX)
    void linux_scriptExecutable_returnsDynamicExecutableHint(@TempDir Path tempDir) throws Exception {
        PlaywrightBrowserLocator locator = mock(PlaywrightBrowserLocator.class);
        BrowserCapabilityService browserCapability = mock(BrowserCapabilityService.class);

        Path fakeExe = tempDir.resolve("chrome");
        Files.writeString(fakeExe, "#!/bin/sh\necho fake");
        assertTrue(fakeExe.toFile().setExecutable(true));
        when(locator.findChromiumExecutable()).thenReturn(fakeExe);

        DependencyCheckService service = new DependencyCheckService(browserCapability, locator);
        List<DependencyCheckService.DependencyItem> items = service.checkDependencies();

        assertEquals(1, items.size());
        assertFalse(items.get(0).available());
        assertTrue(items.get(0).installable());
        verifyNoInteractions(browserCapability);
    }
}
