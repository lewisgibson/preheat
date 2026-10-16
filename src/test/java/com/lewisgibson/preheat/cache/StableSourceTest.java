package com.lewisgibson.preheat.cache;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;

class StableSourceTest {
    // The End's deferred1 from two launches of the same game: Iris swapped the numbers of its two specialised
    // functions.
    private static final String LAUNCH_A = """
            float iris_depth_0_GetAmbientOcclusion(sampler2D d) { return 1.0; }
            float iris_depth_1_GetLODShadows(vec3 p) { return 1.0; }
            void main() { float a = iris_depth_0_GetAmbientOcclusion(depthtex0); a *= iris_depth_1_GetLODShadows(v); }
            """;
    private static final String LAUNCH_B = """
            float iris_depth_1_GetAmbientOcclusion(sampler2D d) { return 1.0; }
            float iris_depth_0_GetLODShadows(vec3 p) { return 1.0; }
            void main() { float a = iris_depth_1_GetAmbientOcclusion(depthtex0); a *= iris_depth_0_GetLODShadows(v); }
            """;

    @Test
    void theSameProgramWithSwappedNumbersGetsTheSameKeyText() {
        assertEquals(StableSource.of(LAUNCH_A), StableSource.of(LAUNCH_B));
        assertEquals(LAUNCH_A, StableSource.of(LAUNCH_B), "renumbered by first appearance");
    }

    @Test
    void differentProgramsStayDifferent() {
        // Two specialisations of the same function, called the other way round: a different program.
        String one =
                "float iris_depth_0_F(){return 0.0;} float iris_depth_1_F(){return 1.0;} void main(){x=iris_depth_0_F();}";
        String two =
                "float iris_depth_0_F(){return 0.0;} float iris_depth_1_F(){return 1.0;} void main(){x=iris_depth_1_F();}";
        assertNotEquals(StableSource.of(one), StableSource.of(two));
    }

    @Test
    void eachKindOfNameIsNumberedOnItsOwnAndOtherTextIsLeftAlone() {
        assertEquals(
                "iris_depth_0_A iris_depth_1_B iris_light_0_C iris_depth_0_A my_depth_7_X",
                StableSource.of("iris_depth_5_A iris_depth_2_B iris_light_9_C iris_depth_5_A my_depth_7_X"));
        String plain = "void main() { gl_FragColor = vec4(1.0); }";
        assertSame(plain, StableSource.of(plain));
        assertEquals("$1 iris_depth_0_A", StableSource.of("$1 iris_depth_3_A"), "replacement text is literal");
    }
}
