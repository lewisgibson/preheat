package com.lewisgibson.preheat;

/** Whether an optional library is there, asked without loading or initialising anything from it. */
public final class Classes {
    private Classes() {}

    /** Whether {@code name} (a binary class name) can be found by Preheat's class loader. */
    public static boolean present(String name) {
        try {
            Class.forName(name, false, Classes.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}
