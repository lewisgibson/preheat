# Security policy

Preheat is a client-side Minecraft mod. It reads and writes settings and a shader cache, and passes cached shader binaries to the graphics driver. The mod does not make network requests.

## Reporting privately

Use this repository's [private vulnerability reporting](https://github.com/lewisgibson/preheat/security/advisories/new), available through **Report a vulnerability** on GitHub's **Security** tab. Do not put vulnerability details in a public issue.

Include the affected Preheat version, operating system, graphics card and driver where relevant, and enough detail to reproduce the problem. Explain what an attacker would need to control, such as a cache file or folder path. Reports are handled as time allows. Please coordinate public disclosure with the maintainer while a fix is being prepared.

## What to report

Security reports include cache handling that can read, overwrite or delete files outside its intended location, unsafe handling of links or junctions, and cache validation failures that could expose the graphics driver to attacker-controlled shader binaries.

Checksums detect damaged files; they do not prove who created a file. Preheat's cache checks are described in [the shader cache](docs/shader-cache.md#validation-and-recovery).

Rendering errors, slow portals and ordinary mod compatibility problems belong in [bug reports](https://github.com/lewisgibson/preheat/issues/new/choose), unless they have a security impact.

## Supported versions

Only the latest release receives security fixes.
