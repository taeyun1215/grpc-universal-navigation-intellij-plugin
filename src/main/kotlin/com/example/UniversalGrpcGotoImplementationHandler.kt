package com.example

import com.intellij.codeInsight.hint.HintManager
import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.PsiShortNamesCache
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.annotations.Nullable
import org.jetbrains.kotlin.idea.intentions.callExpression
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression

class UniversalGrpcGotoImplementationHandler : GotoDeclarationHandler {

    private val log = Logger.getInstance(UniversalGrpcGotoImplementationHandler::class.java)

    @Nullable
    override fun getGotoDeclarationTargets(
        sourceElement: PsiElement?,
        offset: Int,
        editor: Editor
    ): Array<PsiElement>? {
        log.info("[GrpcNav] getGotoDeclarationTargets called")

        if (sourceElement == null) {
            log.warn("[GrpcNav] sourceElement is null")
            return null
        }

        val qualified = PsiTreeUtil.getParentOfType(sourceElement, KtDotQualifiedExpression::class.java)
        if (qualified == null) {
            log.warn("[GrpcNav] Not in dot-qualified expression: ${sourceElement.text}")
            return null
        }

        val callExpression = qualified.callExpression as? KtCallExpression
        if (callExpression == null) {
            log.warn("[GrpcNav] Skipping non-call qualified expression: ${qualified.text}")
            return null
        }

        log.info("[GrpcNav] callExpression.text = ${callExpression.text}")

        val receiverExpr = qualified.receiverExpression.text
        val methodName = callExpression.calleeExpression?.text

        log.info("[GrpcNav] receiverExpr=$receiverExpr, methodName=$methodName")

        if (receiverExpr.isNullOrBlank() || methodName.isNullOrBlank()) {
            log.warn("[GrpcNav] Could not extract receiver/method properly")
            return null
        }

        val receiverName = receiverExpr.removeSuffix("Stub").lowercase()
        log.info("[GrpcNav] Extracted receiverName=$receiverName, methodName=$methodName")

        val project = sourceElement.project
        val scope = GlobalSearchScope.allScope(project)

        // 3. Implementation 클래스 찾기
        val implClass = findServiceImplementation(project, scope, methodName, receiverName)
        if (implClass == null) {
            log.warn("[GrpcNav] No implementation found for $receiverName.$methodName")
            ApplicationManager.getApplication().invokeLater {
                HintManager.getInstance()
                    .showErrorHint(editor, "No implementation found for $receiverName.$methodName")
            }
            return null
        }
        log.info("[GrpcNav] Found implementation class=${implClass.qualifiedName}")

        // 4. 구현체에서 메서드 찾기
        val method = implClass.findMethodsByName(methodName, true).firstOrNull()
        if (method == null) {
            log.warn("[GrpcNav] Method $methodName not found in implementation ${implClass.name}")
            ApplicationManager.getApplication().invokeLater {
                HintManager.getInstance()
                    .showErrorHint(editor, "Method $methodName not found in implementation ${implClass.name}")
            }
            return null
        }
        log.info("[GrpcNav] Matched method=${method.name}")

        return arrayOf(method)
    }

    @Nullable
    override fun getActionText(context: DataContext): String? {
        return "Go to gRPC Implementation"
    }

    private fun findServiceImplementation(
        project: Project,
        scope: GlobalSearchScope,
        methodName: String,
        receiverName: String
    ): PsiClass? {
        val cache = PsiShortNamesCache.getInstance(project)
        val allNames = cache.getAllClassNames()
        log.info("[GrpcNav] Total class names in project: ${allNames.size}")

        val candidates = allNames
            .filter { it.contains(receiverName, ignoreCase = true) }
            .flatMap { cache.getClassesByName(it, scope).toList() }

        log.info("[GrpcNav] Candidate simple names (matched receiverName=$receiverName): ${candidates.mapNotNull { it.name }}")
        log.info("[GrpcNav] Resolved candidates count=${candidates.size}")

        val grpcServiceMatch = candidates.firstOrNull { psiClass ->
            val name = psiClass.name ?: return@firstOrNull false
            val hasMethod = psiClass.findMethodsByName(methodName, true).isNotEmpty()
            val isGrpcService = name.endsWith("GrpcService")
            log.info("[GrpcNav] Checking GrpcService class=${psiClass.qualifiedName}, hasMethod=$hasMethod")
            isGrpcService && hasMethod
        }
        if (grpcServiceMatch != null) {
            log.info("[GrpcNav] Matched GrpcService implementation=${grpcServiceMatch.qualifiedName}")
            return grpcServiceMatch
        }

        // fallback: ImplBase / CoroutineImplBase
        val fallbackMatch = candidates.firstOrNull { psiClass ->
            val name = psiClass.name ?: return@firstOrNull false
            val hasMethod = psiClass.findMethodsByName(methodName, true).isNotEmpty()
            val isImplBase = name.endsWith("ImplBase")
            val isCoroutineImplBase = name.endsWith("CoroutineImplBase")
            log.info("[GrpcNav] Checking fallback class=${psiClass.qualifiedName}, hasMethod=$hasMethod")
            (isImplBase || isCoroutineImplBase) && hasMethod
        }

        if (fallbackMatch != null) {
            log.info("[GrpcNav] Matched fallback implementation=${fallbackMatch.qualifiedName}")
        }
        return fallbackMatch
    }
}