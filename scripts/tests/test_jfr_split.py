from __future__ import annotations

import contextlib
import io
import unittest

from support import load

split = load("lab/jfr-split")


def event(kind: str, thread: str, frame: str) -> str:
    return f'{kind} {{\n  sampledThread = "{thread}"\n  stackTrace = [\n    {frame}\n  ]\n}}'


RECORDING = "\n".join(
    [
        event("jdk.ExecutionSample", "Render thread", "net.irisshaders.iris.pipeline.transform.TransformPatcher.patch"),
        event("jdk.NativeMethodSample", "Render thread", "org.lwjgl.opengl.GL20C.nglLinkProgram"),
        event("jdk.NativeMethodSample", "Render thread", "org.lwjgl.opengl.GL11C.nglDrawElements"),
        event("jdk.ExecutionSample", "Render thread", "net.minecraft.client.Minecraft.runTick"),
        event("jdk.ExecutionSample", "Worker-1", "net.irisshaders.iris.pipeline.transform.TransformPatcher.patch"),
    ]
)


class SplitTest(unittest.TestCase):
    def test_weights_each_render_thread_sample_by_its_period(self) -> None:
        total, counts = split.split(RECORDING)
        self.assertEqual(total, 60)
        self.assertEqual(
            dict(counts),
            {
                "iris: transform (glsl-transformer)": 10,
                "GL compile, link and binary load": 20,
                "GL draws and dispatches": 20,
                split.REST: 10,
            },
        )

    def test_an_empty_recording_has_nothing(self) -> None:
        self.assertEqual(split.split("")[0], 0)


class MainTest(unittest.TestCase):
    def test_runs_jfr_print_and_prints_the_buckets(self) -> None:
        calls: list[list[str]] = []

        def runner(args: list[str]) -> str:
            calls.append(args)
            return RECORDING

        out = io.StringIO()
        with contextlib.redirect_stdout(out):
            self.assertEqual(split.main(["jfr-split.py", "rec.jfr", "/jdk/bin/jfr"], runner), 0)
        self.assertEqual(calls[0][0], "/jdk/bin/jfr")
        self.assertEqual(calls[0][-1], "rec.jfr")
        self.assertIn("render thread: about 60 ms sampled", out.getvalue())
        self.assertIn("33.3%  GL compile, link and binary load", out.getvalue())

    def test_the_jfr_binary_defaults_to_the_one_on_the_path(self) -> None:
        calls: list[list[str]] = []
        with contextlib.redirect_stdout(io.StringIO()):
            split.main(["jfr-split.py", "rec.jfr"], lambda args: calls.append(args) or "")
        self.assertEqual(calls[0][0], "jfr")

    def test_wrong_arguments_print_the_usage(self) -> None:
        err = io.StringIO()
        with contextlib.redirect_stderr(err):
            self.assertEqual(split.main(["jfr-split.py"]), 2)
        self.assertIn("Usage: jfr-split.py", err.getvalue())

    def test_the_real_runner_returns_standard_output(self) -> None:
        self.assertEqual(split.run(["python3", "-c", "print('ok')"]), "ok\n")


if __name__ == "__main__":
    unittest.main()
