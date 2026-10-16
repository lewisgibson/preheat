package com.lewisgibson.preheat.config;

/** Why a folder chosen for the shader cache cannot be used. Each has plain wording on screen; the log keeps the detail. */
public enum FolderProblem {
    /** Not something the file system accepts as a folder name. */
    INVALID("preheat.cache.folder.problem.invalid"),
    /** A relative path, which would depend on where the game happened to start. */
    NOT_FULL_PATH("preheat.cache.folder.problem.relative"),
    /** A file is already there. */
    IS_A_FILE("preheat.cache.folder.problem.file"),
    /** It cannot be created or written to (permissions, a read-only or missing drive). */
    NOT_WRITABLE("preheat.cache.folder.problem.unwritable"),
    /** Something that is not Preheat's already has the name of the folder Preheat makes inside the chosen one. */
    NOT_OURS("preheat.cache.folder.problem.notOurs"),
    /** Moving the saved shaders there failed part way; nothing was changed. */
    MOVE_FAILED("preheat.cache.folder.problem.moveFailed");

    /** The language key of its wording. */
    public final String key;

    FolderProblem(String key) {
        this.key = key;
    }
}
