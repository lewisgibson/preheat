#!/usr/bin/env python3
"""Splits the render thread's samples in a lab JFR recording into where a dimension change spends its time.

Java samples (jdk.ExecutionSample) and native samples (jdk.NativeMethodSample, the thread inside a GL call) are both
counted; each sample goes to the first bucket whose marker appears anywhere in its stack, in the order below. Under
the "profile" settings JFR samples a thread in Java every 10 ms and in native code every 20 ms, so each sample is
weighted by its period and the result is milliseconds of render thread.

Usage: jfr-split.py <recording.jfr> [jfr binary]
"""

from __future__ import annotations

import collections
import re
import subprocess
import sys
from collections.abc import Callable

BUCKETS = [
    ("iris: preprocess the pack (ProgramSet)", ("shaderpack.programs.ProgramSet.<init>", "shaderpack.ShaderPack.getProgramSet", "org.anarres.cpp")),
    ("iris: transform (glsl-transformer)", ("pipeline.transform.TransformPatcher", "io.github.douira.glsl_transformer")),
    ("GL compile, link and binary load", ("glCompileShader", "glLinkProgram", "nglProgramBinary", "gl.shader.GlShader", "gl.shader.ProgramCreator")),
    ("GL draws and dispatches", ("nglDrawElements", "nglDrawArrays", "nglMultiDrawElements", "glDispatchCompute", "nglDrawElementsInstanced")),
    ("iris: other pipeline construction", ("pipeline.IrisRenderingPipeline.<init>", "pipeline.PipelineManager.preparePipeline")),
    ("iris: destroy old pipeline", ("PipelineManager.destroyPipeline",)),
    ("distant horizons", ("com.seibel.distanthorizons",)),
    ("sodium", ("net.caffeinemc.mods.sodium",)),
]
REST = "rest of the frame (minecraft, other mods)"

Run = Callable[[list[str]], str]


def run(args: list[str]) -> str:
    return subprocess.run(args, capture_output=True, text=True, check=True).stdout


def split(text: str) -> tuple[int, collections.Counter[str]]:
    """The render thread's sampled milliseconds in total and per bucket, from `jfr print` output."""
    counts: collections.Counter[str] = collections.Counter()
    total = 0
    for event in re.split(r"\n(?=jdk\.(?:ExecutionSample|NativeMethodSample) )", text):
        # JDK 25 names the sampled thread in a sampledThread field.
        if 'sampledThread = "Render thread"' not in event:
            continue
        ms = 20 if event.startswith("jdk.NativeMethodSample") else 10
        total += ms
        bucket = next((name for name, markers in BUCKETS if any(m in event for m in markers)), REST)
        counts[bucket] += ms
    return total, counts


def main(argv: list[str], runner: Run = run) -> int:
    if len(argv) not in (2, 3):
        print(__doc__.strip().splitlines()[-1], file=sys.stderr)
        return 2
    jfr = argv[2] if len(argv) == 3 else "jfr"
    total, counts = split(
        runner([jfr, "print", "--stack-depth", "256", "--events", "jdk.ExecutionSample,jdk.NativeMethodSample", argv[1]])
    )
    print(f"render thread: about {total} ms sampled")
    for name, ms in counts.most_common():
        print(f"  ~{ms:6d} ms  {100 * ms / max(total, 1):5.1f}%  {name}")
    return 0


if __name__ == "__main__":  # pragma: no cover
    sys.exit(main(sys.argv))
