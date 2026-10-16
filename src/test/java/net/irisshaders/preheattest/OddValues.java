package net.irisshaders.preheattest;

import java.lang.invoke.MethodHandles;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;

/**
 * Values under Iris's package prefix that PatchKey must refuse, because their class says nothing stable about them (an
 * anonymous class, a lambda, a hidden class and a synthetic one), and a holder that nests a parameters object.
 */
public final class OddValues {
    private OddValues() {}

    public static Object anonymous() {
        return new Object() {};
    }

    public static Object lambda() {
        Runnable r = () -> {};
        return r;
    }

    /** A hidden class that is not also synthetic, as a lambda's is. */
    public static Object hidden() throws ReflectiveOperationException {
        byte[] bytes = emptyClass("net/irisshaders/preheattest/HiddenValue", 0);
        Class<?> c = MethodHandles.lookup().defineHiddenClass(bytes, true).lookupClass();
        return c.getDeclaredConstructor().newInstance();
    }

    /** A synthetic class that is not hidden. */
    public static Object synthetic() throws ReflectiveOperationException {
        byte[] bytes = emptyClass("net/irisshaders/preheattest/SyntheticValue", Opcodes.ACC_SYNTHETIC);
        Class<?> c = MethodHandles.lookup().defineClass(bytes);
        return c.getDeclaredConstructor().newInstance();
    }

    private static byte[] emptyClass(String name, int flags) {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER | flags, name, null, "java/lang/Object", null);
        Method init = Method.getMethod("void <init> ()");
        GeneratorAdapter g = new GeneratorAdapter(Opcodes.ACC_PUBLIC, init, null, null, cw);
        g.loadThis();
        g.invokeConstructor(org.objectweb.asm.Type.getType(Object.class), init);
        g.returnValue();
        g.endMethod();
        cw.visitEnd();
        return cw.toByteArray();
    }

    /** Holds another value, as a parameters object holds Iris's own objects; its static field is not part of it. */
    // Read by PatchKey through reflection.
    @SuppressWarnings({"UnusedVariable", "NonFinalStaticField"})
    public static final class Holder {
        public static int count = 1;
        private final Object inner;

        public Holder(Object inner) {
            this.inner = inner;
        }
    }
}
