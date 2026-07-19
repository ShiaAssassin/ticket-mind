package com.ticketmind.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

@Component
@Order(1)
@Slf4j
@RequiredArgsConstructor
public class SkillFileWatcher implements ApplicationRunner {

    private final SkillChromaInitializer skillChromaInitializer;

    private final AtomicBoolean running = new AtomicBoolean(false);

    private WatchService watchService;
    private Thread watcherThread;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        if (!skillChromaInitializer.isSkillWatchEnabled()) {
            return;
        }
        if (!skillChromaInitializer.hasWatchableSkillRoot()) {
            log.warn("Skill file watch skipped because local skill root does not exist: {}",
                    skillChromaInitializer.getSkillRootPath());
            return;
        }

        startWatcher(skillChromaInitializer.getSkillRootPath());
    }

    @PreDestroy
    public void stopWatcher() {
        running.set(false);
        if (watcherThread != null) {
            watcherThread.interrupt();
        }
        if (watchService != null) {
            try {
                watchService.close();
            } catch (IOException ex) {
                log.debug("Failed to close skill watch service", ex);
            }
        }
    }

    private void startWatcher(Path skillRoot) throws IOException {
        if (!running.compareAndSet(false, true)) {
            return;
        }

        watchService = FileSystems.getDefault().newWatchService();
        registerRecursively(skillRoot);

        watcherThread = new Thread(this::watchLoop, "skill-file-watcher");
        watcherThread.setDaemon(true);
        watcherThread.start();

        log.info("Watching skill files under {}", skillRoot);
    }

    private void watchLoop() {
        while (running.get()) {
            WatchKey key;
            try {
                key = watchService.take();
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception ex) {
                if (running.get()) {
                    log.error("Skill file watcher stopped unexpectedly", ex);
                }
                return;
            }

            Path watchedDirectory = (Path) key.watchable();
            for (WatchEvent<?> event : key.pollEvents()) {
                WatchEvent.Kind<?> kind = event.kind();
                if (kind == StandardWatchEventKinds.OVERFLOW) {
                    continue;
                }

                Path changedPath = watchedDirectory.resolve((Path) event.context()).toAbsolutePath().normalize();
                handleEvent(kind, changedPath);
            }

            if (!key.reset()) {
                log.warn("Skill watch key became invalid: {}", watchedDirectory);
            }
        }
    }

    private void handleEvent(WatchEvent.Kind<?> kind, Path changedPath) {
        try {
            if (kind == StandardWatchEventKinds.ENTRY_CREATE && Files.isDirectory(changedPath)) {
                registerRecursively(changedPath);
                skillChromaInitializer.syncSkillsUnder(changedPath);
                return;
            }

            if (!isSkillFileCandidate(changedPath)) {
                return;
            }

            if (kind == StandardWatchEventKinds.ENTRY_DELETE) {
                skillChromaInitializer.deleteSkill(changedPath);
                return;
            }

            if (kind == StandardWatchEventKinds.ENTRY_CREATE || kind == StandardWatchEventKinds.ENTRY_MODIFY) {
                skillChromaInitializer.syncSkill(changedPath);
            }
        } catch (Exception ex) {
            log.error("Failed to handle skill file event [{}] for {}", kind.name(), changedPath, ex);
        }
    }

    private boolean isSkillFileCandidate(Path changedPath) {
        return changedPath != null
                && changedPath.getFileName() != null
                && "SKILL.md".equals(changedPath.getFileName().toString());
    }

    private void registerRecursively(Path root) throws IOException {
        try (Stream<Path> stream = Files.walk(root)) {
            stream.filter(Files::isDirectory)
                    .forEach(this::registerDirectory);
        }
    }

    private void registerDirectory(Path directory) {
        try {
            directory.register(
                    watchService,
                    StandardWatchEventKinds.ENTRY_CREATE,
                    StandardWatchEventKinds.ENTRY_MODIFY,
                    StandardWatchEventKinds.ENTRY_DELETE
            );
        } catch (IOException ex) {
            log.error("Failed to register skill watch directory: {}", directory, ex);
        }
    }
}
