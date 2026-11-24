/*
 * PixelBoard - Gboard Enhancement Mod
 * Licensed under the GNU General Public License v3.0 (GPLv3)
 */
package com.akshaykadam.pixelboard.patches.gboard.features.keyboard

import com.akshaykadam.pixelboard.patches.gboard.shared.gboardPatchesExtensionCarrierPatch
import com.akshaykadam.pixelboard.patches.gboard.shared.invokeRegisters
import com.akshaykadam.pixelboard.patches.gboard.shared.isMethodReference
import com.akshaykadam.pixelboard.patches.gboard.shared.runtimeabi.RuntimeAbiCatalog
import com.akshaykadam.pixelboard.patches.gboard.shared.runtimeabi.RuntimeCallEmitter
import com.akshaykadam.pixelboard.patches.gboard.shared.runtimeabi.RuntimeCallId
import com.akshaykadam.pixelboard.patches.shared.Constants.COMPATIBILITY_GBOARD
import com.akshaykadam.pixelboard.patches.shared.MutableMethod
import com.akshaykadam.pixelboard.patches.shared.addInstructions
import com.akshaykadam.pixelboard.patches.shared.bytecodePatch
import com.akshaykadam.pixelboard.patches.shared.replaceInstruction
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import com.android.tools.smali.dexlib2.iface.instruction.RegisterRangeInstruction
import java.lang.reflect.Modifier

private const val COMMIT_TEXT_TARGET =
    "Landroid/view/inputmethod/InputConnection;->commitText(Ljava/lang/CharSequence;I)Z"

private const val COMMIT_TEXT_WITH_ATTR_TARGET =
    "Landroid/view/inputmethod/InputConnection;->commitText(Ljava/lang/CharSequence;ILandroid/view/inputmethod/TextAttribute;)Z"

private const val EXTENSION_PACKAGE_PREFIX = "Lcom/akshaykadam/pixelboard/extension/"

private val INPUT_METHOD_START_INPUT_PARAMS = listOf(
    "Landroid/view/inputmethod/EditorInfo;",
    "Z",
)

internal val gboardCommaSpacingPatch = bytecodePatch(
    description = "Fix spaces around commas automatically when typing via InputConnection.commitText.",
) {
    compatibleWith(COMPATIBILITY_GBOARD)
    dependsOn(gboardPatchesExtensionCarrierPatch)

    execute {
        val candidateClassTypes = linkedSetOf<String>()

        classDefForEach { classDef ->
            if (classDef.type.startsWith(EXTENSION_PACKAGE_PREFIX)) {
                return@classDefForEach
            }
            val matches = classDef.methods.any { method ->
                val impl = method.implementation ?: return@any false
                val isStartInput = (method.name == "onStartInput" || method.name == "onStartInputView") &&
                    !AccessFlags.ABSTRACT.isSet(method.accessFlags) &&
                    !AccessFlags.STATIC.isSet(method.accessFlags) &&
                    method.parameterTypes == INPUT_METHOD_START_INPUT_PARAMS &&
                    method.returnType == "V"
                if (isStartInput) {
                    return@any true
                }
                impl.instructions.any { instruction ->
                    instruction.isMethodReference(COMMIT_TEXT_TARGET) ||
                        instruction.isMethodReference(COMMIT_TEXT_WITH_ATTR_TARGET)
                }
            }
            if (matches) {
                candidateClassTypes.add(classDef.type)
            }
        }

        val onStartInputCall = RuntimeCallId.COMMA_SPACING_RUNTIME_ON_START_INPUT
        val onStartInputRef = RuntimeAbiCatalog.abi(onStartInputCall).reference
        val commitTextCall = RuntimeCallId.COMMA_SPACING_RUNTIME_COMMIT_TEXT
        val commitTextRef = RuntimeAbiCatalog.abi(commitTextCall).reference
        val commitTextAttrCall = RuntimeCallId.COMMA_SPACING_RUNTIME_COMMIT_TEXT_WITH_ATTR
        val commitTextAttrRef = RuntimeAbiCatalog.abi(commitTextAttrCall).reference

        for (classType in candidateClassTypes) {
            val mutableClass = mutableClassDefByOrNull(classType) ?: continue
            for (method in mutableClass.methods) {
                val impl = method.implementation ?: continue
                val instructions = impl.instructions

                val isStartInput = (method.name == "onStartInput" || method.name == "onStartInputView") &&
                    !AccessFlags.ABSTRACT.isSet(method.accessFlags) &&
                    !AccessFlags.STATIC.isSet(method.accessFlags) &&
                    method.parameterTypes == INPUT_METHOD_START_INPUT_PARAMS &&
                    method.returnType == "V"

                if (isStartInput && instructions.none { it.isMethodReference(onStartInputRef) }) {
                    method.addInstructions(
                        0,
                        RuntimeCallEmitter.invoke(onStartInputCall, "p0 .. p1"),
                    )
                }

                val currentInstructions = method.implementation?.instructions ?: continue
                val replacements = mutableListOf<Pair<Int, String>>()

                for ((index, instruction) in currentInstructions.withIndex()) {
                    if (instruction.isMethodReference(commitTextRef) ||
                        instruction.isMethodReference(commitTextAttrRef)
                    ) {
                        continue
                    }

                    val regs2 = instruction.invokeRegisters(COMMIT_TEXT_TARGET)
                    if (regs2 != null && regs2.size == 3) {
                        val args = formatRegistersForRuntimeCall(instruction, regs2, method)
                        if (args != null) {
                            replacements.add(index to RuntimeCallEmitter.invoke(commitTextCall, args))
                        }
                        continue
                    }

                    val regs3 = instruction.invokeRegisters(COMMIT_TEXT_WITH_ATTR_TARGET)
                    if (regs3 != null && regs3.size == 4) {
                        val args = formatRegistersForRuntimeCall(instruction, regs3, method)
                        if (args != null) {
                            replacements.add(index to RuntimeCallEmitter.invoke(commitTextAttrCall, args))
                        }
                    }
                }

                for ((index, replacement) in replacements.reversed()) {
                    method.replaceInstruction(index, replacement)
                }
            }
        }
    }
}

private fun formatRegistersForRuntimeCall(
    instruction: Instruction,
    registerIndices: List<Int>,
    method: MutableMethod,
): String? {
    if (registerIndices.isEmpty()) return null
    val mapped = registerIndices.map { index -> registerName(index, method) }
    if (registerIndices.all { it <= 15 }) {
        return mapped.joinToString(", ")
    }
    if (instruction is RegisterRangeInstruction) {
        val areContiguousRaw = registerIndices.zipWithNext().all { (a, b) -> b == a + 1 }
        val firstPrefix = mapped.first().first()
        val sameFamily = mapped.all { it.first() == firstPrefix }
        if (areContiguousRaw && sameFamily) {
            return "${mapped.first()} .. ${mapped.last()}"
        }
    }
    return null
}

private fun registerName(registerIndex: Int, method: MutableMethod): String {
    val impl = method.implementation ?: return "v$registerIndex"
    val totalRegisters = impl.registerCount
    val isStatic = Modifier.isStatic(method.accessFlags)
    val paramWords = (if (isStatic) 0 else 1) +
        method.parameterTypes.sumOf { if (it == "J" || it == "D") 2 else 1 }
    val locals = totalRegisters - paramWords
    return if (locals >= 0 && registerIndex >= locals) {
        "p${registerIndex - locals}"
    } else {
        "v$registerIndex"
    }
}
