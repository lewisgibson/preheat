package com.lewisgibson.preheat;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;

/** Collects what Preheat writes to its log while open ("INFO PREHEAT ..."), for tests to read. */
public final class LogLines implements AutoCloseable {
    private final List<String> lines = new ArrayList<>();
    private final Logger logger = (Logger) LogManager.getLogger(Preheat.NAME);
    private final Level before = logger.getLevel();
    private final AbstractAppender appender =
            new AbstractAppender("preheat-test-" + UUID.randomUUID(), null, null, true, Property.EMPTY_ARRAY) {
                @Override
                public void append(LogEvent event) {
                    synchronized (lines) {
                        lines.add(event.getLevel() + " " + event.getMessage().getFormattedMessage());
                    }
                }
            };

    private LogLines() {
        appender.start();
        logger.addAppender(appender);
        logger.setLevel(Level.ALL);
    }

    public static LogLines capture() {
        return new LogLines();
    }

    /** Every line so far, as "LEVEL message". */
    public List<String> lines() {
        synchronized (lines) {
            return List.copyOf(lines);
        }
    }

    /** Whether some line contains {@code fragment}. */
    public boolean has(String fragment) {
        return lines().stream().anyMatch(l -> l.contains(fragment));
    }

    @Override
    public void close() {
        logger.removeAppender(appender);
        logger.setLevel(before);
        appender.stop();
    }
}
