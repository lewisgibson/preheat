package com.lewisgibson.preheat.cache;

import static org.junit.jupiter.api.Assumptions.assumeFalse;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;

/** Runs a check while a file or folder cannot be read, and makes it readable again afterwards. */
final class Unreadable {
    private Unreadable() {}

    /** A check that may throw anything a test does. */
    interface Check {
        void run() throws Exception;
    }

    static void during(Path p, Check check) throws Exception {
        Set<PosixFilePermission> before = Files.getPosixFilePermissions(p);
        Files.setPosixFilePermissions(p, Set.of());
        try {
            // A superuser reads anything, so there the case cannot be made.
            assumeFalse(Files.isReadable(p), "running as a user that can read anything");
            check.run();
        } finally {
            Files.setPosixFilePermissions(p, before);
        }
    }
}
