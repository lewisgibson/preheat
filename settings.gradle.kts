rootProject.name = "preheat"

// The CPU lab's driver mod (testmod/): test-only, never shipped. Built only when the game's jars are present.
if (file("libs/compile").isDirectory) {
    include(":testmod")
}
