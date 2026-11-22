package com.hemz.distributedcache.modules.loadtestservice.run;

import com.hemz.distributedcache.modules.loadtestservice.config.LoadTestProperties;
import tools.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Keeps each run as one JSON file (runs/run-000001.json).
 *
 * Files instead of a database table on purpose: storing results in the same
 * PostgreSQL we are measuring would add load to the system under test.
 * Files are also easy to copy into a bench/results folder and commit.
 */
@Component
public class RunStore {

    private static final Logger log = LoggerFactory.getLogger(RunStore.class);

    private final Path dir;
    private final ObjectMapper mapper;
    private final AtomicLong sequence = new AtomicLong();

    public RunStore(LoadTestProperties props, ObjectMapper mapper) {
        this.dir = Path.of(props.runsDir()).toAbsolutePath().normalize();
        this.mapper = mapper;
    }

    /** Create the folder, find the highest id, and mark runs interrupted by a crash as FAILED. */
    @PostConstruct
    void init() {
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot create runs folder " + dir, e);
        }
        long max = 0;
        List<Run> runs = readAll();
        for (Run run : runs) {
            max = Math.max(max, run.getId());
            if (run.getStatus() == RunStatus.RUNNING || run.getStatus() == RunStatus.PENDING) {
                run.setStatus(RunStatus.FAILED);
                run.setFailureReason("Product Catalogue stopped while this run was in progress");
                if (run.getFinishedAt() == null) {
                    run.setFinishedAt(Instant.now());
                }
                save(run);
            }
        }
        sequence.set(max);
        log.info("Run store at {} ({} saved runs)", dir, runs.size());
    }

    public long nextId() {
        return sequence.incrementAndGet();
    }

    /** Write to a temp file, then move it into place, so a crash never leaves half a file. */
    public synchronized void save(Run run) {
        Path target = file(run.getId());
        Path tmp = target.resolveSibling(target.getFileName() + ".tmp");
        try {
            mapper.writeValue(tmp.toFile(), run);
            try {
                Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Could not save run " + run.getId(), e);
        }
    }

    public Optional<Run> find(long id) {
        Path f = file(id);
        if (!Files.isRegularFile(f)) {
            return Optional.empty();
        }
        return Optional.ofNullable(read(f));
    }

    public List<Run> findAll() {
        List<Run> runs = readAll();
        runs.sort(Comparator.comparingLong(Run::getId).reversed());
        return runs;
    }

    private List<Run> readAll() {
        List<Run> runs = new ArrayList<>();
        try (DirectoryStream<Path> files = Files.newDirectoryStream(dir, "run-*.json")) {
            for (Path f : files) {
                Run r = read(f);
                if (r != null) {
                    runs.add(r);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return runs;
    }

    private Run read(Path f) {
        try {
            return mapper.readValue(f.toFile(), Run.class);
        } catch (Exception e) {
            log.warn("Skipping unreadable run file {}: {}", f.getFileName(), e.getMessage());
            return null;
        }
    }

    private Path file(long id) {
        return dir.resolve(String.format("run-%06d.json", id));
    }
}
