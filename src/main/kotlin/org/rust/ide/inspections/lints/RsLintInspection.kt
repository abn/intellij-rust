/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections.lints

import com.intellij.codeInspection.*
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiNamedElement
import org.rust.ide.inspections.RsLocalInspectionTool
import org.rust.ide.inspections.RsProblemsHolder
import org.rust.ide.inspections.lints.RsLintLevel.ALLOW
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*

abstract class RsLintInspection : RsLocalInspectionTool() {

    protected abstract fun getLint(element: PsiElement): RsLint?

    protected fun RsProblemsHolder.registerLintProblem(
        element: PsiElement,
        descriptionTemplate: String,
        vararg fixes: LocalQuickFix
    ) {
        registerProblem(element, descriptionTemplate, getProblemHighlightType(element), *fixes)
    }

    protected fun RsProblemsHolder.registerLintProblem(
        element: PsiElement,
        descriptionTemplate: String,
        rangeInElement: TextRange,
        vararg fixes: LocalQuickFix
    ) {
        registerProblem(element, descriptionTemplate, getProblemHighlightType(element), rangeInElement, *fixes)
    }

    private fun getProblemHighlightType(element: PsiElement): ProblemHighlightType =
        getLint(element)?.getProblemHighlightType(element) ?: ProblemHighlightType.WARNING

    override fun isSuppressedFor(element: PsiElement): Boolean {
        if (super.isSuppressedFor(element)) return true
        return getLint(element)?.levelFor(element) == ALLOW
    }

    // TODO: fix quick fix order in UI
    override fun getBatchSuppressActions(element: PsiElement?): Array<SuppressQuickFix> {
        val fixes = super.getBatchSuppressActions(element).toMutableList()
        if (element == null) return fixes.toTypedArray()
        val lint = getLint(element) ?: return fixes.toTypedArray()

        for (ancestor in element.ancestors) {
            val action = when (ancestor) {
                is RsLetDecl,
                is RsFieldDecl,
                is RsEnumVariant,
                is RsItemElement,
                is RsFile -> {
                    var target = when (ancestor) {
                        is RsLetDecl -> "statement"
                        is RsFieldDecl -> "field"
                        is RsEnumVariant -> "enum variant"
                        is RsStructItem -> "struct"
                        is RsEnumItem -> "enum"
                        is RsFunction -> "fn"
                        is RsTypeAlias -> "type"
                        is RsConstant -> "const"
                        is RsModItem -> "mod"
                        is RsImplItem -> "impl"
                        is RsTraitItem -> "trait"
                        is RsUseItem -> "use"
                        is RsFile -> "file"
                        else -> null
                    }
                    if (target != null) {
                        val name = (ancestor as? PsiNamedElement)?.name
                        if (name != null) {
                            target += " $name"
                        }
                        RsSuppressQuickFix(ancestor as RsDocAndAttributeOwner, lint, target)
                    } else {
                        null
                    }
                }
                is RsExprStmt -> {
                    val expr = ancestor.expr
                    if (expr is RsOuterAttributeOwner) RsSuppressQuickFix(expr, lint, "statement") else null
                }
                else -> null
            }
            if (action != null) {
                fixes += action
            }
        }
        return fixes.toTypedArray()
    }
}

private class RsSuppressQuickFix(
    suppressAt: RsDocAndAttributeOwner,
    private val lint: RsLint,
    private val target: String
) : LocalQuickFixOnPsiElement(suppressAt), ContainerBasedSuppressQuickFix {

    override fun getFamilyName(): String = "Suppress warnings"

    override fun getText(): String = "Suppress `${lint.id}` for $target"

    override fun isAvailable(project: Project, context: PsiElement): Boolean = context.isValid

    override fun isSuppressAll(): Boolean = false

    override fun getContainer(context: PsiElement?): PsiElement? = startElement?.takeIf { it !is RsFile }

    override fun invoke(project: Project, file: PsiFile, suppressAt: PsiElement, endElement: PsiElement) {
        val attr = Attribute("allow", lint.id)
        when (suppressAt) {
            is RsOuterAttributeOwner -> {
                val anchor = suppressAt.outerAttrList.firstOrNull() ?: suppressAt.firstChild
                suppressAt.addOuterAttribute(attr, anchor)
            }
            is RsInnerAttributeOwner -> {
                val anchor = suppressAt.children.first { it !is RsInnerAttr && it !is PsiComment } ?: return
                suppressAt.addInnerAttribute(attr, anchor)
            }
        }
    }
}
