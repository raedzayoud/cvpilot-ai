package com.backend.backend.service.export;

import com.backend.backend.config.AppProperties;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Compiles LaTeX to PDF with Tectonic (or any compatible engine configured in LATEX_COMPILER).
 * Runs in a throwaway directory with --untrusted (no shell escape), a timeout, and at most two
 * compilations at once so export bursts cannot exhaust the CPU.
 */
@Slf4j
@Component
public class LatexCompiler {

    public static class CompileException extends Exception {
        public CompileException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    private static final Semaphore SLOTS = new Semaphore(2);

    private final AppProperties.Latex config;

    public LatexCompiler(AppProperties properties) {
        this.config = properties.latex();
    }

    public boolean isEnabled() {
        return config.enabled() && config.compiler() != null && !config.compiler().isBlank();
    }

    public byte[] compile(String source) throws CompileException {
        if (!isEnabled()) throw new CompileException("LaTeX compilation is disabled", null);
        Path dir = null;
        boolean acquired = false;
        try {
            acquired = SLOTS.tryAcquire(config.timeout().toSeconds(), TimeUnit.SECONDS);
            if (!acquired) throw new CompileException("Too many PDF exports in progress", null);

            dir = Files.createTempDirectory("cvpilot-latex-");
            Path tex = dir.resolve("cv.tex");
            Files.writeString(tex, source, StandardCharsets.UTF_8);

            Process process = new ProcessBuilder(List.of(
                    config.compiler(), "--untrusted", "--chatter", "minimal", "--outdir", dir.toString(), tex.toString()))
                    .directory(dir.toFile())
                    .redirectErrorStream(true)
                    .redirectOutput(dir.resolve("compiler.log").toFile())
                    .start();
            long started = System.nanoTime();
            if (!process.waitFor(config.timeout().toMillis(), TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
                throw new CompileException("LaTeX compilation timed out after " + config.timeout().toSeconds() + "s", null);
            }
            Path pdf = dir.resolve("cv.pdf");
            if (process.exitValue() != 0 || !Files.exists(pdf)) {
                String logTail = tail(dir.resolve("compiler.log"));
                log.warn("LaTeX compilation failed (exit {}): {}", process.exitValue(), logTail);
                throw new CompileException("LaTeX compilation failed", null);
            }
            log.info("Compiled CV PDF in {} ms", (System.nanoTime() - started) / 1_000_000);
            return Files.readAllBytes(pdf);
        } catch (IOException e) {
            throw new CompileException("Could not run the LaTeX compiler (" + config.compiler() + ")", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CompileException("Interrupted while compiling", e);
        } finally {
            if (acquired) SLOTS.release();
            deleteQuietly(dir);
        }
    }

    private static String tail(Path logFile) {
        try {
            String text = Files.readString(logFile, StandardCharsets.UTF_8);
            return text.length() <= 1500 ? text : text.substring(text.length() - 1500);
        } catch (IOException e) {
            return "(no log)";
        }
    }

    private static void deleteQuietly(Path dir) {
        if (dir == null) return;
        try (Stream<Path> paths = Files.walk(dir)) {
            paths.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
        } catch (IOException ignored) {
            // temp dir cleanup is best effort
        }
    }
}
