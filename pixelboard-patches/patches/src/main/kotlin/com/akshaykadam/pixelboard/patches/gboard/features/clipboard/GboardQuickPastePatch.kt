/*
 * PixelBoard - Gboard Enhancement Mod
 * Licensed under the GNU General Public License v3.0 (GPLv3)
 */
package com.akshaykadam.pixelboard.patches.gboard.features.clipboard

import com.akshaykadam.pixelboard.patches.gboard.shared.gboardPatchesExtensionCarrierPatch
import com.akshaykadam.pixelboard.patches.gboard.shared.isMethodReference
import com.akshaykadam.pixelboard.patches.gboard.shared.isOpcode
import com.akshaykadam.pixelboard.patches.gboard.shared.returnInstructionIndices
import com.akshaykadam.pixelboard.patches.gboard.shared.runtimeabi.RuntimeAbiCatalog
import com.akshaykadam.pixelboard.patches.gboard.shared.runtimeabi.RuntimeCallEmitter
import com.akshaykadam.pixelboard.patches.gboard.shared.runtimeabi.RuntimeCallId
import com.akshaykadam.pixelboard.patches.shared.Constants.COMPATIBILITY_GBOARD
import com.akshaykadam.pixelboard.patches.shared.MutableMethod
import com.akshaykadam.pixelboard.patches.shared.addInstructions
import com.akshaykadam.pixelboard.patches.shared.bytecodePatch
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import java.lang.reflect.Modifier

private const val EXTENSION_PACKAGE_PREFIX = "Lcom/akshaykadam/pixelboard/extension/"

private val START_INPUT_VIEW_PARAMS = listOf(
    "Landroid/view/inputmethod/EditorInfo;",
    "Z",
)

internal val gboardQuickPastePatch = bytecodePatch(
    description = "Attach a quick one-tap Paste button to the keyboard header bar.",
) {
    compatibleWith(COMPATIBILITY_GBOARD)
    dependsOn(gboardPatchesExtensionCarrierPatch)

    execute {
        val candidateClassTypes = linkedSetOf<String>()

        classDefForEach { classDef ->
            if (classDef.type.startsWith(EXTENSION_PACKAGE_PREFIX)) {
                return@classDefForEach
            }
            val hasTarget = classDef.methods.any { method ->
                method.implementation != null &&
                    !AccessFlags.ABSTRACT.isSet(method.accessFlags) &&
                    !AccessFlags.STATIC.isSet(method.accessFlags) &&
                    (isOnCreateInputView(method.name, method.parameterTypes, method.returnType) ||
                        isOnStartInputView(method.name, method.parameterTypes, method.returnType))
            }
            if (hasTarget) {
                candidateClassTypes.add(classDef.type)
            }
        }

        val onCreateViewCall = RuntimeCallId.QUICK_PASTE_RUNTIME_ON_INPUT_VIEW_CREATED
        val onCreateViewRef = RuntimeAbiCatalog.abi(onCreateViewCall).reference
        val onStartViewCall = RuntimeCallId.QUICK_PASTE_RUNTIME_ON_START_INPUT_VIEW
        val onStartViewRef = RuntimeAbiCatalog.abi(onStartViewCall).reference

        for (classType in candidateClassTypes) {
            val mutableClass = mutableClassDefByOrNull(classType) ?: continue
            for (method in mutableClass.methods) {
                val impl = method.implementation ?: continue
                if (AccessFlags.ABSTRACT.isSet(method.accessFlags) || AccessFlags.STATIC.isSet(method.accessFlags)) {
                    continue
                }

                if (isOnCreateInputView(method.name, method.parameterTypes, method.returnType)) {
                    val instructions = impl.instructions
                    if (instructions.none { it.isMethodReference(onCreateViewRef) }) {
                        val thisReg = thisRegisterIndex(method)
                        if (thisReg in 0..15) {
                            val returns = method.returnInstructionIndices()
                                .filter { instructions[it].isOpcode("RETURN_OBJECT") }
                            returns.asReversed().forEach { returnIndex ->
                                val viewRegIndex =
                                    (instructions[returnIndex] as? OneRegisterInstruction)?.registerA
                                        ?: return@forEach
                                if (viewRegIndex in 0..15) {
                                    val viewRegName = registerName(viewRegIndex, method)
                                    method.addInstructions(
                                        returnIndex,
                                        RuntimeCallEmitter.invoke(onCreateViewCall, "p0, $viewRegName"),
                                    )
                                }
                            }
                        }
                    }
                } else if (isOnStartInputView(method.name, method.parameterTypes, method.returnType)) {
                    val instructions = impl.instructions
                    if (instructions.none { it.isMethodReference(onStartViewRef) }) {
                        method.addInstructions(
                            0,
                            RuntimeCallEmitter.invoke(onStartViewCall, "p0 .. p1"),
                        )
                    }
                }
            }
        }
    }
}

private fun isOnCreateInputView(
    name: String,
    parameterTypes: List<CharSequence>,
    returnType: String,
): Boolean = name == "onCreateInputView" &&
    parameterTypes.isEmpty() &&
    returnType == "Landroid/view/View;"

private fun isOnStartInputView(
    name: String,
    parameterTypes: List<CharSequence>,
    returnType: String,
): Boolean = name == "onStartInputView" &&
    parameterTypes == START_INPUT_VIEW_PARAMS &&
    returnType == "V"

private fun thisRegisterIndex(method: MutableMethod): Int {
    val impl = method.implementation ?: return -1
    val totalRegisters = impl.registerCount
    val isStatic = Modifier.isStatic(method.accessFlags)
    val paramWords = (if (isStatic) 0 else 1) +
        method.parameterTypes.sumOf { if (it == "J" || it == "D") 2 else 1 }
    return totalRegisters - paramWords
}

private fun registerName(registerIndex: Int, method: MutableMethod): String {
    val locals = thisRegisterIndex(method)
    return if (locals >= 0 && registerIndex >= locals) {
        "p${registerIndex - locals}"
    } else {
        "v$registerIndex"
    }
}
