# AGENTS.md

This file is a loader, not the instructions. The instructions for this repository are written for Claude Code, and they apply in full to OpenAI Codex or any other agent that reads this file.

## Read these files before doing any work

At the start of every session, before any command, edit or answer, read these from top to bottom:

1. `CLAUDE.md`: what Preheat is, how to build and test it, the layout, the rules broken most often, and releases
2. `.claude/rules/java-architecture.md`: fail soft, thin mixins, threads and coverage
3. `.claude/rules/java-consistency.md`: the house style every file is held to
4. `.claude/rules/java-review-checklist.md`: what every change is reviewed against
5. `.claude/rules/operational-awareness.md`: releases, the jars, and running the game

Claude Code loads all of them by itself and Codex loads none, so reading them is your job. Where they say "Claude" or "the agent", they mean you. If `AGENTS.local.md` exists, read it too.

## Translating Claude Code terms

- **Skills.** "The `release` skill" means `.claude/skills/release/SKILL.md`. Codex finds the same folder through `.agents/skills`, a committed link to `.claude/skills`, so use those skills as your own.
- **Not available, ignore.** Subagents, assistant memory, Claude Code hooks and settings, and anything under `~/.claude/`.
