package com.lewisgibson.preheat.cache;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * The key a patched program is saved under. Iris keeps its own in-memory cache of patches for the whole session, keyed
 * by the patch parameters ({@code Parameters.equals}), the sources, whether the device's depth range is zero to one and
 * whether it is a shadow pass ({@code TransformPatcher.CacheKey}): Iris itself treats a patch as a function of those.
 * Preheat's key holds all of that and more: every field of the parameters, read by reflection (so a field a later Iris
 * adds is in the key without anyone remembering to add it), the program's name, the sources, the depth range, whether
 * Iris prints patched code for debugging, and the context the caller adds (the Iris and Preheat versions, and every
 * installed mod with its version, so a mod that changes Iris's patching can never be served an old result).
 *
 * <p>A parameter value of a type it cannot write down in a stable way makes the program uncacheable ({@link #describe}
 * returns null), never a key that might match something else. No game classes here: it sees Iris's objects only through
 * reflection.
 */
public final class PatchKey {
    /**
     * Fields of Iris's {@code Parameters} that {@code TransformPatcher.transformInternal} assigns before reading, per
     * program and per stage, so their value on entry is whatever an earlier patch left and says nothing about this one.
     */
    static final Set<String> ASSIGNED_BY_PATCHING = Set.of("type", "name");

    /** Classes whose fields are written down field by field: Iris's own. */
    private static final String IRIS = "net.irisshaders.";

    private static final int MAX_DEPTH = 8;

    private PatchKey() {}

    /**
     * A stable description of a patch parameters object: its class and every instance field, through its superclasses,
     * except {@link #ASSIGNED_BY_PATCHING} on the {@code Parameters} base class. Null when some value cannot be written
     * down stably.
     */
    public static @Nullable String describe(Object parameters) {
        StringBuilder sb = new StringBuilder();
        try {
            return write(parameters, sb, 0, new IdentityHashMap<>()) ? sb.toString() : null;
        } catch (RuntimeException | IllegalAccessException e) {
            return null;
        }
    }

    private static boolean write(@Nullable Object v, StringBuilder sb, int depth, IdentityHashMap<Object, Boolean> seen)
            throws IllegalAccessException {
        if (depth > MAX_DEPTH) {
            return false;
        }
        if (v == null) {
            sb.append("null");
            return true;
        }
        Class<?> c = v.getClass();
        if (v instanceof String s) {
            sb.append('"').append(s.length()).append(':').append(s).append('"');
            return true;
        }
        if (v instanceof Boolean || v instanceof Character || v instanceof Number) {
            if (!c.getName().startsWith("java.lang.")) {
                return false;
            }
            sb.append(c.getSimpleName()).append('(').append(v).append(')');
            return true;
        }
        if (v instanceof Enum<?> e) {
            sb.append(e.getDeclaringClass().getName()).append('.').append(e.name());
            return true;
        }
        if (seen.containsKey(v)) {
            return false;
        }
        seen.put(v, Boolean.TRUE);
        try {
            if (v instanceof Map<?, ?> m) {
                List<String> entries = new ArrayList<>();
                for (Map.Entry<?, ?> e : m.entrySet()) {
                    StringBuilder one = new StringBuilder();
                    if (!write(e.getKey(), one, depth + 1, seen)) {
                        return false;
                    }
                    one.append('=');
                    if (!write(e.getValue(), one, depth + 1, seen)) {
                        return false;
                    }
                    entries.add(one.toString());
                }
                entries.sort(null);
                sb.append("map").append(entries);
                return true;
            }
            if (v instanceof Collection<?> col) {
                List<String> items = new ArrayList<>();
                for (Object o : col) {
                    StringBuilder one = new StringBuilder();
                    if (!write(o, one, depth + 1, seen)) {
                        return false;
                    }
                    items.add(one.toString());
                }
                // A list's order is part of it; a set's is not.
                if (v instanceof Set<?>) {
                    items.sort(null);
                    sb.append("set");
                } else {
                    sb.append("list");
                }
                sb.append(items);
                return true;
            }
            if (c.isArray()) {
                sb.append("array[");
                for (int i = 0; i < Array.getLength(v); i++) {
                    if (!write(Array.get(v, i), sb, depth + 1, seen)) {
                        return false;
                    }
                    sb.append(',');
                }
                sb.append(']');
                return true;
            }
            if (!c.getName().startsWith(IRIS) || c.isSynthetic() || c.isAnonymousClass() || c.isHidden()) {
                return false;
            }
            sb.append(c.getName()).append('{');
            // An object's class is never an interface, so its superclasses always end at Object.
            for (Class<?> k = c; k != Object.class; k = k.getSuperclass()) {
                boolean base = k.getSuperclass() == Object.class;
                // Sorted: the order reflection lists fields in is not specified.
                Field[] fields = k.getDeclaredFields();
                Arrays.sort(fields, Comparator.comparing(Field::getName));
                for (Field f : fields) {
                    if (Modifier.isStatic(f.getModifiers())
                            || (base && depth == 0 && ASSIGNED_BY_PATCHING.contains(f.getName()))) {
                        continue;
                    }
                    f.setAccessible(true);
                    sb.append(k.getSimpleName()).append('.').append(f.getName()).append('=');
                    if (!write(f.get(v), sb, depth + 1, seen)) {
                        return false;
                    }
                    sb.append(';');
                }
            }
            sb.append('}');
            return true;
        } finally {
            seen.remove(v);
        }
    }
}
