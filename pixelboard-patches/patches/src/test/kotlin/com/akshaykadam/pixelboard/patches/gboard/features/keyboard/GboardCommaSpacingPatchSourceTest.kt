package com.akshaykadam.pixelboard.patches.gboard.features.keyboard

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import org.junit.Assert.assertTrue
import org.junit.Test

class GboardCommaSpacingPatchSourceTest {
    @Test
    fun `comma spacing patch hooks commitText and startInput and is wired into settings`() {
        val source = read(
            "patches/src/main/kotlin/com/akshaykadam/pixelboard/patches/gboard/" +
                "features/keyboard/GboardCommaSpacingPatch.kt",
        )
        assertTrue(source.contains("Landroid/view/inputmethod/InputConnection;->commitText(Ljava/lang/CharSequence;I)Z"))
        assertTrue(source.contains("Landroid/view/inputmethod/InputConnection;->commitText(Ljava/lang/CharSequence;ILandroid/view/inputmethod/TextAttribute;)Z"))
        assertTrue(source.contains("Lcom/akshaykadam/pixelboard/extension/"))
        assertTrue(source.contains("COMMA_SPACING_RUNTIME_ON_START_INPUT"))
        assertTrue(source.contains("COMMA_SPACING_RUNTIME_COMMIT_TEXT"))
        assertTrue(source.contains("COMMA_SPACING_RUNTIME_COMMIT_TEXT_WITH_ATTR"))

        val abi = read(
            "patches/src/main/kotlin/com/akshaykadam/pixelboard/patches/gboard/" +
                "shared/runtimeabi/RuntimeAbi.kt",
        )
        assertTrue(abi.contains("COMMA_SPACING_RUNTIME_ON_START_INPUT("))
        assertTrue(abi.contains("COMMA_SPACING_RUNTIME_COMMIT_TEXT("))
        assertTrue(abi.contains("COMMA_SPACING_RUNTIME_COMMIT_TEXT_WITH_ATTR("))

        val settings = read(
            "patches/src/main/kotlin/com/akshaykadam/pixelboard/patches/gboard/" +
                "shared/GboardPatchesSettingsPatch.kt",
        )
        assertTrue(settings.contains("gboardCommaSpacingPatch"))
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
