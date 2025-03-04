/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections

import com.intellij.codeInspection.LocalQuickFix
import org.rust.ide.inspections.fixes.AddTypeArguments
import org.rust.ide.inspections.fixes.RemoveTypeArguments
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.RsElement
import org.rust.lang.core.psi.ext.RsGenericDeclaration
import org.rust.lang.core.psi.ext.constParameters
import org.rust.lang.core.psi.ext.typeParameters
import org.rust.lang.utils.RsDiagnostic
import org.rust.lang.utils.addToHolder

/**
 * Inspection that detects the E0107 error.
 */
class RsWrongGenericArgumentsNumberInspection : RsLocalInspectionTool() {
    override fun buildVisitor(holder: RsProblemsHolder, isOnTheFly: Boolean) =
        object : RsVisitor() {
            override fun visitBaseType(type: RsBaseType) {
                if (!isPathValid(type.path)) return
                checkTypeArguments(holder, type)
            }

            override fun visitTraitRef(trait: RsTraitRef) {
                if (!isPathValid(trait.path)) return
                checkTypeArguments(holder, trait)
            }

            override fun visitCallExpr(o: RsCallExpr) = checkTypeArguments(holder, o)
            override fun visitMethodCall(o: RsMethodCall) = checkTypeArguments(holder, o)
        }

    // Don't apply generic declaration checks to Fn-traits and `Self`
    private fun isPathValid(path: RsPath?): Boolean = path?.valueParameterList == null && path?.cself == null

    private fun checkTypeArguments(holder: RsProblemsHolder, o: RsElement) {
        val (actualArguments, declaration) = getTypeArgumentsAndDeclaration(o) ?: return

        val actualTypeArgs = actualArguments?.typeReferenceList?.size ?: 0
        val expectedTotalTypeParams = declaration.typeParameters.size
        val expectedRequiredTypeParams = declaration.typeParameters.count { it.typeReference == null }

        val actualConstArgs = actualArguments?.exprList?.size ?: 0
        val expectedTotalConstParams = declaration.constParameters.size

        val actualArgs = actualTypeArgs + actualConstArgs
        val expectedTotalParams = expectedTotalTypeParams + expectedTotalConstParams
        val expectedRequiredParams = expectedRequiredTypeParams + expectedTotalConstParams

        if (actualArgs == expectedTotalParams) return

        val errorText = when (o) {
            is RsBaseType, is RsTraitRef -> checkTypeReference(actualArgs, expectedRequiredParams, expectedTotalParams)
            is RsMethodCall, is RsCallExpr -> checkFunctionCall(actualArgs, expectedRequiredParams, expectedTotalParams)
            else -> null
        } ?: return

        val haveTypeParams = expectedTotalTypeParams > 0 || actualTypeArgs > 0
        val haveConstParams = expectedTotalConstParams > 0 || actualConstArgs > 0
        val argumentName = when {
            haveTypeParams && !haveConstParams -> "type"
            !haveTypeParams && haveConstParams -> "const"
            else -> "generic"
        }

        val problemText = "Wrong number of $argumentName arguments: expected $errorText, found $actualArgs"
        val fixes = getFixes(o, actualTypeArgs, expectedTotalTypeParams)

        RsDiagnostic.WrongNumberOfTypeArguments(o, problemText, fixes).addToHolder(holder)
    }
}

private fun checkTypeReference(actualArgs: Int, expectedRequiredParams: Int, expectedTotalParams: Int): String? {
    return when {
        actualArgs > expectedTotalParams ->
            if (expectedRequiredParams != expectedTotalParams) "at most $expectedTotalParams" else "$expectedTotalParams"
        actualArgs < expectedRequiredParams ->
            if (expectedRequiredParams != expectedTotalParams) "at least $expectedRequiredParams" else "$expectedTotalParams"
        else -> null
    }
}

private fun checkFunctionCall(actualArgs: Int, expectedRequiredParams: Int, expectedTotalParams: Int): String? {
    return when {
        actualArgs > expectedTotalParams ->
            if (expectedRequiredParams != expectedTotalParams) "at most $expectedTotalParams" else "$expectedTotalParams"
        actualArgs in 1 until expectedTotalParams ->
            if (expectedRequiredParams != expectedTotalParams) "at least $expectedRequiredParams" else "$expectedTotalParams"
        else -> null
    }
}

private fun getFixes(element: RsElement, actualArgs: Int, expectedTotalParams: Int): List<LocalQuickFix> =
    when {
        actualArgs > expectedTotalParams -> listOf(RemoveTypeArguments(expectedTotalParams, actualArgs))
        actualArgs < expectedTotalParams -> listOf(AddTypeArguments(element))
        else -> emptyList()
    }

fun getTypeArgumentsAndDeclaration(element: RsElement): Pair<RsTypeArgumentList?, RsGenericDeclaration>? {
    val (arguments, resolved) = when (element) {
        is RsMethodCall -> element.typeArgumentList to element.reference.resolve()
        is RsCallExpr -> (element.expr as? RsPathExpr)?.path?.typeArgumentList to (element.expr as? RsPathExpr)?.path?.reference?.resolve()
        is RsBaseType -> element.path?.typeArgumentList to element.path?.reference?.resolve()
        is RsTraitRef -> element.path.typeArgumentList to element.path.reference?.resolve()
        else -> return null
    }
    if (resolved !is RsGenericDeclaration) return null
    return Pair(arguments, resolved)
}
