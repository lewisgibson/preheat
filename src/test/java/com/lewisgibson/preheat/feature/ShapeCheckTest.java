package com.lewisgibson.preheat.feature;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.google.common.base.Splitter;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * The shape check against Preheat's real mixins and the real Iris 1.11.7 and Minecraft 26.3 classes: everything fits
 * as shipped, and each kind of change a new Iris could make (a method gone, a parameter added, a field renamed, a call
 * removed from a method body) is reported against the right member.
 */
class ShapeCheckTest {
    private CompatibilityMatrixTest.JarSource real;
    private final Map<String, ClassNode> overrides = new HashMap<>();
    private ShapeCheck.ClassSource source;

    @BeforeEach
    void open() throws Exception {
        Path row = Path.of(System.getProperty("preheat.matrix", "libs/matrix")).resolve("26.3-fabric-iris1.11.7");
        assumeTrue(Files.isDirectory(row), "run scripts/fetch-deps.py first");
        List<Path> jars = new ArrayList<>();
        try (var files = Files.walk(row)) {
            files.filter(f -> f.toString().endsWith(".jar")).forEach(jars::add);
        }
        List<Path> classes = new ArrayList<>();
        for (String p : Splitter.on(File.pathSeparator)
                .split(System.getProperty("preheat.classes", "build/classes/java/main"))) {
            classes.add(Path.of(p));
        }
        real = new CompatibilityMatrixTest.JarSource(classes, jars);
        overrides.clear();
        source = name -> overrides.containsKey(name) ? overrides.get(name) : real.get(name);
    }

    private ClassNode mixin(String relative) {
        return real.get(FeatureCheck.MIXINS + relative.replace('.', '/'));
    }

    /** A copy of a real class with {@code change} applied, served in its place. */
    private void mutate(String internalName, Consumer<ClassNode> change) {
        ClassNode copy = new ClassNode();
        real.get(internalName).accept(copy);
        change.accept(copy);
        overrides.put(internalName, copy);
    }

    @Test
    void everyShippedMixinFitsIris1117() {
        for (Feature f : Feature.values()) {
            for (String m : f.mixins) {
                if (!Feature.distantHorizonsEntry(m)) {
                    assertEquals(List.of(), ShapeCheck.checkMixin(mixin(m), source), m);
                }
            }
        }
    }

    @Test
    void aMissingInjectionTargetIsReported() {
        mutate(
                "net/irisshaders/iris/pipeline/PipelineManager",
                c -> c.methods.removeIf(m -> m.name.equals("destroyPipeline")));
        List<String> problems = ShapeCheck.checkMixin(mixin("keep.PipelineManagerMixin"), source);
        assertTrue(problems.contains("PipelineManager.destroyPipeline is missing"), problems.toString());
    }

    @Test
    void aChangedParameterListIsReported() {
        mutate(
                "net/irisshaders/iris/pipeline/PipelineManager",
                c -> c.methods.stream()
                        .filter(m -> m.name.equals("preparePipeline"))
                        .forEach(m -> m.desc = m.desc.replace(")", "Z)")));
        List<String> problems = ShapeCheck.checkMixin(mixin("keep.PipelineManagerMixin"), source);
        assertTrue(
                problems.stream()
                        .anyMatch(p -> p.startsWith("PipelineManager.preparePipeline changed: its parameters are now")),
                problems.toString());
    }

    @Test
    void aRenamedShadowedFieldIsReported() {
        mutate(
                "net/irisshaders/iris/pipeline/PipelineManager",
                c -> c.fields.stream()
                        .filter(f -> f.name.equals("pipelinesPerDimension"))
                        .forEach(f -> f.name = "pipelines"));
        assertTrue(ShapeCheck.checkMixin(mixin("keep.PipelineManagerMixin"), source)
                .contains("PipelineManager.pipelinesPerDimension is missing"));
    }

    @Test
    void aCallGoneFromTheTargetBodyIsReported() {
        mutate(
                "net/irisshaders/iris/pipeline/programs/ShaderCreator",
                c -> c.methods.stream().filter(m -> m.name.equals("link")).forEach(m -> {
                    for (AbstractInsnNode i : m.instructions.toArray()) {
                        if (i instanceof MethodInsnNode mi && mi.name.equals("glLinkProgram")) {
                            mi.name = "glLinkProgramNow";
                        }
                    }
                }));
        List<String> problems = ShapeCheck.checkMixin(mixin("cache.ShaderCreatorCacheMixin"), source);
        assertTrue(
                problems.contains("ShaderCreator.link no longer calls GlStateManager.glLinkProgram"),
                problems.toString());
    }

    @Test
    void accessorsAndInvokersAreChecked() {
        mutate(
                "net/irisshaders/iris/pipeline/programs/PartialShader",
                c -> c.fields.removeIf(f -> f.name.equals("program")));
        assertTrue(ShapeCheck.checkMixin(mixin("cache.PartialShaderAccessor"), source).stream()
                .anyMatch(p -> p.startsWith("PartialShader.program")));
        mutate("net/irisshaders/iris/gl/program/ProgramBuilder", c -> c.methods.removeIf(m -> m.name.equals("<init>")));
        assertTrue(ShapeCheck.checkMixin(mixin("cache.ProgramBuilderAccessor"), source).stream()
                .anyMatch(p -> p.startsWith("ProgramBuilder.<init>")));
    }

    @Test
    void aHelperCallThatNoLongerResolvesIsReported() {
        mutate(
                "net/irisshaders/iris/shaderpack/materialmap/WorldRenderingSettings",
                c -> c.methods.removeIf(m -> m.name.equals("setVertexFormat")));
        List<String> problems = ShapeCheck.checkLinks(
                real.get(FeatureCheck.ROOT + "keep/IrisReactivation"), source, FeatureCheck.FOREIGN);
        assertTrue(
                problems.stream().anyMatch(p -> p.startsWith("WorldRenderingSettings.setVertexFormat")),
                problems.toString());
        assertEquals(
                List.of(),
                ShapeCheck.checkLinks(
                        real.get(FeatureCheck.ROOT + "keep/IrisReactivation"), real, FeatureCheck.FOREIGN));
    }

    @Test
    void aWholeFeatureGoesOffAndTakesItsDependentsWithIt() {
        mutate(
                "net/irisshaders/iris/pipeline/PipelineManager",
                c -> c.methods.removeIf(m -> m.name.equals("destroyPipeline")));
        Map<Feature, FeatureCheck.Result> r = FeatureCheck.evaluate(source, false);
        assertEquals(FeatureCheck.State.OFF, r.get(Feature.KEEP_PIPELINES).state());
        assertEquals(
                List.of("needs keeping each dimension's shaders loaded, which is off"),
                r.get(Feature.PREBUILD).problems());
        assertEquals(FeatureCheck.State.ON, r.get(Feature.PROGRAM_CACHE).state(), "the cache does not need keeping");
        assertEquals(
                FeatureCheck.State.NOT_NEEDED,
                r.get(Feature.BACKGROUND_DH_CLOSE).state());
    }

    @Test
    void injectSignatures() {
        MethodNode target = new MethodNode(Opcodes.ACC_PUBLIC, "go", "(ILjava/lang/String;)Z", null, null);
        String cir = "Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfoReturnable;";
        String ci = "Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;";
        assertNull(ShapeCheck.injectSignature(
                new MethodNode(Opcodes.ACC_PRIVATE, "h", "(" + cir + ")V", null, null), target));
        assertNull(ShapeCheck.injectSignature(
                new MethodNode(Opcodes.ACC_PRIVATE, "h", "(ILjava/lang/String;" + cir + ")V", null, null), target));
        assertEquals(
                "it now returns a value",
                ShapeCheck.injectSignature(
                        new MethodNode(Opcodes.ACC_PRIVATE, "h", "(" + ci + ")V", null, null), target));
        assertTrue(ShapeCheck.injectSignature(
                        new MethodNode(Opcodes.ACC_PRIVATE, "h", "(I" + cir + ")V", null, null), target)
                .startsWith("its parameters"));
        assertEquals(
                "it is no longer static",
                ShapeCheck.injectSignature(
                        new MethodNode(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC, "h", "(" + cir + ")V", null, null),
                        target));
        assertEquals(
                "the handler takes no CallbackInfo",
                ShapeCheck.injectSignature(new MethodNode(Opcodes.ACC_PRIVATE, "h", "()V", null, null), target));
        MethodNode ctor =
                new MethodNode(Opcodes.ACC_PUBLIC, "<init>", Type.getMethodDescriptor(Type.VOID_TYPE), null, null);
        assertNull(ShapeCheck.injectSignature(
                new MethodNode(Opcodes.ACC_PRIVATE, "h", "(" + ci + ")V", null, null), ctor));
    }

    @Test
    void jdkMethodsInheritedByGameTypesResolve() {
        assertTrue(ShapeCheck.hasMethod(real, "net/minecraft/client/Minecraft", "toString", "()Ljava/lang/String;"));
        assertTrue(!ShapeCheck.hasMethod(real, "net/minecraft/client/Minecraft", "noSuchMethod", "()V"));
    }
}
