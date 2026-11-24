package com.akshaykadam.pixelboard.patches.gboard.features.clipboard

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import org.junit.Assert.assertTrue
import org.junit.Test

class GboardQuickPastePatchSourceTest {
    @Test
    fun `quick paste patch hooks onCreateInputView and onStartInputView and is wired into settings`() {
        val source = read(
            "patches/src/main/kotlin/com/akshaykadam/pixelboard/patches/gboard/" +
                "features/clipboard/GboardQuickPastePatch.kt",
        )
        assertTrue(source.contains("onCreateInputView"))
        assertTrue(source.contains("onStartInputView"))
        assertTrue(source.contains("Lcom/akshaykadam/pixelboard/extension/"))
        assertTrue(source.contains("QUICK_PASTE_RUNTIME_ON_INPUT_VIEW_CREATED"))
        assertTrue(source.contains("QUICK_PASTE_RUNTIME_ON_START_INPUT_VIEW"))

        val abi = read(
            "patches/src/main/kotlin/com/akshaykadam/pixelboard/patches/gboard/" +
                "shared/runtimeabi/RuntimeAbi.kt",
        )
        assertTrue(abi.contains("QUICK_PASTE_RUNTIME_ON_INPUT_VIEW_CREATED("))
        assertTrue(abi.contains("QUICK_PASTE_RUNTIME_ON_START_INPUT_VIEW("))

        val settings = read(
            "patches/src/main/kotlin/com/akshaykadam/pixelboard/patches/gboard/" +
                "shared/GboardPatchesSettingsPatch.kt",
        )
        assertTrue(settings.contains("gboardQuickPastePatch"))
    }

    private fun read(relativePath: String): String =
        Files.readString(repositoryRoot().resolve(relativePath), StandardCharsets.UTF_8)
            .replace("\r\n", "\n")

    private fun repositoryRoot(): Path {
        val workingDirectory = Path.of("").toAbsolutePath().normalize()
        return generateSequence(workingDirectory) { it.parent }
            .firstOrNull { Files.isRegularFile(it.resolve("settings.gradle.kts")) }
            ?: error("Could not locate repository root from $workingDirectory")
    }
}
