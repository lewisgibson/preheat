package com.lewisgibson.preheat.config;

import java.nio.file.Path;
import org.jspecify.annotations.Nullable;

/** Lets tests in other packages point the settings at a file of their own (and back). */
public final class ConfigFiles {
    private ConfigFiles() {}

    public static void use(@Nullable Path file) {
        PreheatConfig.useFile(file);
    }
}
