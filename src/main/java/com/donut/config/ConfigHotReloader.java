package com.donut.config;

import java.io.IOException;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;

/**
 * Watches the config directory and triggers a debounced reload callback when
 * config.toml changes. Runs on a virtual thread; safe to start once at init.
 */
final class ConfigHotReloader {
    private static volatile boolean started;
    private static volatile Thread thread;

    private ConfigHotReloader() {
    }

    static void start(Path configToml, Runnable onReload) {
        if (started) return;
        started = true;
        Path dir = configToml.toAbsolutePath().getParent();
        String file = configToml.getFileName().toString();
        thread = Thread.ofVirtual().name("donut-config-watch").start(() -> watchLoop(dir, file, onReload));
    }

    private static void watchLoop(Path dir, String file, Runnable onReload) {
        try (WatchService ws = FileSystems.getDefault().newWatchService()) {
            dir.register(ws, StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_MODIFY);
            while (true) {
                WatchKey key = ws.take();
                boolean touched = false;
                for (WatchEvent<?> ev : key.pollEvents()) {
                    Object ctx = ev.context();
                    if (ctx instanceof Path p && p.getFileName().toString().equals(file)) touched = true;
                }
                if (touched) {
                    Thread.sleep(250); // debounce editor write bursts
                    onReload.run();
                }
                if (!key.reset()) break;
            }
        } catch (ClosedWatchServiceException | InterruptedException ignored) {
            // shutting down
        } catch (IOException e) {
            System.err.println("[Donut] Config hot-reload watcher failed: " + e);
        }
    }
}
