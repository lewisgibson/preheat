package com.lewisgibson.preheat;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ClassesTest {
    @Test
    void aClassIsPresentWhenItCanBeFound() {
        assertTrue(Classes.present("org.lwjgl.sdl.SDLEvents"), "the test classpath has LWJGL's SDL");
        assertFalse(Classes.present("org.lwjgl.glfw.NoSuchThing"));
    }
}
