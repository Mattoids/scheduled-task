package com.mattoid.scheduled.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DependencyInstallServiceTest {

    @Test
    void unsupportedKey_throwsIllegalArgumentException() {
        DependencyCheckService checkService = mock(DependencyCheckService.class);
        DependencyInstallService service = new DependencyInstallService(checkService);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.install("firefox"));
        assertTrue(ex.getMessage().contains("暂不支持的依赖项"));
    }

    @Test
    void duplicateInstall_attachesToRunningTask() throws Exception {
        DependencyCheckService checkService = mock(DependencyCheckService.class);
        DependencyInstallService service = spy(new DependencyInstallService(checkService));
        CountDownLatch blocker = new CountDownLatch(1);
        doAnswer(inv -> {
            blocker.await();
            return null;
        }).when(service).doInstall(any());

        SseEmitter first = service.install("chromium");
        assertTrue(service.isInstalling("chromium"));

        SseEmitter second = service.install("chromium");
        assertNotSame(first, second);
        assertTrue(service.isInstalling("chromium"));

        blocker.countDown();
        waitUntilFinished(service, "chromium");
        assertFalse(service.isInstalling("chromium"));
    }

    @Test
    void isInstalling_libKey_normalizesToChromium() throws Exception {
        DependencyCheckService checkService = mock(DependencyCheckService.class);
        DependencyInstallService service = spy(new DependencyInstallService(checkService));
        CountDownLatch blocker = new CountDownLatch(1);
        doAnswer(inv -> {
            blocker.await();
            return null;
        }).when(service).doInstall(any());

        service.install("lib:libssl.so.3");
        assertTrue(service.isInstalling("lib:libssl.so.3"));
        assertTrue(service.isInstalling("chromium"));
        assertFalse(service.isInstalling("firefox"));

        blocker.countDown();
    }

    @Test
    void install_success_completesEmitter() throws Exception {
        DependencyCheckService checkService = mock(DependencyCheckService.class);
        DependencyInstallService service = spy(new DependencyInstallService(checkService));
        doNothing().when(service).doInstall(any());
        when(checkService.refreshDependencies()).thenReturn(List.of());

        SseEmitter emitter = service.install("chromium");

        waitUntilFinished(service, "chromium");
        assertFalse(service.isInstalling("chromium"));
    }

    @Test
    void install_failure_triggersErrorCallback() throws Exception {
        DependencyCheckService checkService = mock(DependencyCheckService.class);
        DependencyInstallService service = spy(new DependencyInstallService(checkService));
        doThrow(new RuntimeException("安装失败")).when(service).doInstall(any());

        SseEmitter emitter = service.install("chromium");

        waitUntilFinished(service, "chromium");
        assertFalse(service.isInstalling("chromium"));
    }

    @Test
    void getSnapshot_returnsDefaultForUnsupportedKey() {
        DependencyCheckService checkService = mock(DependencyCheckService.class);
        DependencyInstallService service = new DependencyInstallService(checkService);

        var snapshot = service.getSnapshot("firefox");

        assertEquals("firefox", snapshot.key());
        assertFalse(snapshot.running());
        assertEquals("idle", snapshot.status());
    }

    @Test
    void detectOs_returnsCurrentFamily() {
        DependencyCheckService checkService = mock(DependencyCheckService.class);
        DependencyInstallService service = new DependencyInstallService(checkService);

        DependencyInstallService.OsInfo os = service.detectOs();
        assertNotNull(os.family());
        assertNotEquals("unknown", os.family());
    }

    @Test
    void buildPackageManagerCommand_apt_usesShellScript() {
        DependencyCheckService checkService = mock(DependencyCheckService.class);
        DependencyInstallService service = new DependencyInstallService(checkService);

        List<String> cmd = service.buildPackageManagerCommand("apt", List.of("libssl"));

        assertEquals("sh", cmd.get(0));
        assertEquals("-c", cmd.get(1));
        assertTrue(cmd.get(2).contains("apt-get update"));
        assertTrue(cmd.get(2).contains("libssl"));
    }

    @Test
    void buildPackageManagerCommand_dnf() {
        DependencyCheckService checkService = mock(DependencyCheckService.class);
        DependencyInstallService service = new DependencyInstallService(checkService);

        List<String> cmd = service.buildPackageManagerCommand("dnf", List.of("glib2"));

        assertEquals(List.of("dnf", "install", "-y", "glib2"), cmd);
    }

    @Test
    void buildPackageManagerCommand_apk() {
        DependencyCheckService checkService = mock(DependencyCheckService.class);
        DependencyInstallService service = new DependencyInstallService(checkService);

        List<String> cmd = service.buildPackageManagerCommand("apk", List.of("chromium"));

        assertEquals(List.of("apk", "add", "--no-cache", "chromium"), cmd);
    }

    @Test
    void buildPackageManagerCommand_unknown_throws() {
        DependencyCheckService checkService = mock(DependencyCheckService.class);
        DependencyInstallService service = new DependencyInstallService(checkService);

        assertThrows(IllegalStateException.class,
                () -> service.buildPackageManagerCommand("unknown", List.of("x")));
    }

    @Test
    void buildAptPackageList_legacyUbuntu_usesOriginalNames() {
        DependencyCheckService checkService = mock(DependencyCheckService.class);
        DependencyInstallService service = new DependencyInstallService(checkService);

        DependencyInstallService.OsInfo os = new DependencyInstallService.OsInfo(
                "linux", "ubuntu", "apt", "22.04", "jammy");
        List<String> packages = service.buildAptPackageList(os);

        assertTrue(packages.contains("libglib2.0-0"));
        assertFalse(packages.contains("libglib2.0-0t64"));
        assertTrue(packages.contains("libasound2"));
        assertFalse(packages.contains("libasound2t64"));
    }

    @Test
    void buildAptPackageList_ubuntu2404_usesT64Names() {
        DependencyCheckService checkService = mock(DependencyCheckService.class);
        DependencyInstallService service = new DependencyInstallService(checkService);

        DependencyInstallService.OsInfo os = new DependencyInstallService.OsInfo(
                "linux", "ubuntu", "apt", "24.04", "noble");
        List<String> packages = service.buildAptPackageList(os);

        assertTrue(packages.contains("libglib2.0-0t64"));
        assertFalse(packages.contains("libglib2.0-0"));
        assertTrue(packages.contains("libasound2t64"));
        assertTrue(packages.contains("libcups2t64"));
    }

    @Test
    void buildAptPackageList_debianTrixie_usesT64Names() {
        DependencyCheckService checkService = mock(DependencyCheckService.class);
        DependencyInstallService service = new DependencyInstallService(checkService);

        DependencyInstallService.OsInfo os = new DependencyInstallService.OsInfo(
                "linux", "debian", "apt", "13", "trixie");
        List<String> packages = service.buildAptPackageList(os);

        assertTrue(packages.contains("libatk1.0-0t64"));
        assertFalse(packages.contains("libatk1.0-0"));
    }

    @Test
    void execute_commandSucceeds_returnsExitCode() throws Exception {
        DependencyCheckService checkService = mock(DependencyCheckService.class);
        DependencyInstallService service = new DependencyInstallService(checkService);
        DependencyInstallService.InstallTask task = new DependencyInstallService.InstallTask("chromium", new java.util.concurrent.ConcurrentHashMap<>());
        task.setEmitter(new SseEmitter(0L));

        List<String> command = List.of(
                System.getProperty("java.home") + "/bin/java",
                "-version"
        );
        int exit = service.execute(command, task, "test");
        assertEquals(0, exit);
    }

    @Test
    void execute_commandNotFound_throwsException() {
        DependencyCheckService checkService = mock(DependencyCheckService.class);
        DependencyInstallService service = new DependencyInstallService(checkService);
        DependencyInstallService.InstallTask task = new DependencyInstallService.InstallTask("chromium", new java.util.concurrent.ConcurrentHashMap<>());
        task.setEmitter(new SseEmitter(0L));

        List<String> command = List.of("this-command-definitely-does-not-exist-xyz");
        Exception ex = assertThrows(Exception.class,
                () -> service.execute(command, task, "test"));
        assertNotNull(ex.getMessage());
    }

    @Test
    void execute_timeout_throwsRuntimeException() {
        DependencyCheckService checkService = mock(DependencyCheckService.class);
        DependencyInstallService service = new DependencyInstallService(checkService);
        service.setProcessTimeoutMinutesForTesting(0);

        DependencyInstallService.InstallTask task = new DependencyInstallService.InstallTask("chromium", new java.util.concurrent.ConcurrentHashMap<>());
        task.setEmitter(new SseEmitter(0L));

        List<String> command = List.of(
                System.getProperty("java.home") + "/bin/java",
                "-cp", System.getProperty("java.class.path"),
                SleepHelper.class.getName(),
                "2"
        );
        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> service.execute(command, task, "test"));
        assertTrue(ex.getMessage().contains("超时"));
    }

    private void waitUntilFinished(DependencyInstallService service, String key) throws InterruptedException {
        long deadline = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(5);
        while (service.isInstalling(key) && System.currentTimeMillis() < deadline) {
            Thread.sleep(50);
        }
    }
}
