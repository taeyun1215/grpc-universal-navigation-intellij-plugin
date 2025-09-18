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
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression

/**
 * Universal gRPC Goto Implementation Handler
 *
 * gRPC Stub 호출부에서 "구현체"로 점프하도록 IntelliJ의 GotoDeclarationHandler 확장
 */
class UniversalGrpcGotoImplementationHandler : GotoDeclarationHandler {

    private val log = Logger.getInstance(UniversalGrpcGotoImplementationHandler::class.java)

    /**
     * Ctrl+B (Goto Declaration) 동작 시 호출되는 메서드
     */
    @Nullable
    override fun getGotoDeclarationTargets(
        sourceElement: PsiElement?,
        offset: Int,
        editor: Editor
    ): Array<PsiElement>? {
        log.info("[GrpcNav] getGotoDeclarationTargets called")

        // 1. 커서가 null이면 종료
        if (sourceElement == null) {
            log.warn("[GrpcNav] sourceElement is null")
            return null
        }

        // 2. dot-qualified expression (ex: userStub.getUserInfo(...)) 추출
        val qualified = PsiTreeUtil.getParentOfType(sourceElement, KtDotQualifiedExpression::class.java)
        if (qualified == null) {
            log.warn("[GrpcNav] Not in dot-qualified expression: ${sourceElement.text}")
            return null
        }

        // 3. call expression (메서드 호출 부분) 추출
        val callExpression = qualified.callExpression
        if (callExpression == null) {
            log.warn("[GrpcNav] Skipping non-call qualified expression: ${qualified.text}")
            return null
        }
        log.info("[GrpcNav] callExpression.text = ${callExpression.text}")

        // 4. 수신 객체(receiver) + 메서드명 추출
        val receiverExpr = qualified.receiverExpression.text
        val methodName = callExpression.calleeExpression?.text
        log.info("[GrpcNav] receiverExpr=$receiverExpr, methodName=$methodName")

        if (receiverExpr.isNullOrBlank() || methodName.isNullOrBlank()) {
            log.warn("[GrpcNav] Could not extract receiver/method properly")
            return null
        }

        // 5. Stub일 때만 처리: Stub로 끝나지 않으면 무시
        if (!receiverExpr.endsWith("Stub")) {
            log.info("[GrpcNav] Skipping non-Stub receiver: $receiverExpr")
            return null
        }

        // 6. receiver 이름에서 "Stub" 접미어 제거 → Service 이름 유추
        val receiverName = receiverExpr.removeSuffix("Stub").lowercase()
        log.info("[GrpcNav] Extracted receiverName=$receiverName, methodName=$methodName")

        val project = sourceElement.project
        val scope = GlobalSearchScope.allScope(project)

        // 7. 구현체 클래스 찾기
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

        // 8. 해당 클래스에서 메서드 검색
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

        return arrayOf(method) // 최종 점프 타깃
    }

    @Nullable
    override fun getActionText(context: DataContext): String? {
        return "Go to gRPC Implementation"
    }

    /**
     * 실제 gRPC 구현체(GrpcService 클래스) 탐색 로직
     */
    private fun findServiceImplementation(
        project: Project,
        scope: GlobalSearchScope,
        methodName: String,
        receiverName: String
    ): PsiClass? {
        val cache = PsiShortNamesCache.getInstance(project)
        val allNames = cache.getAllClassNames()
        log.info("[GrpcNav] Total class names in project: ${allNames.size}")

        // 1. receiverName 과 매칭되는 후보 클래스 목록 필터링
        val candidates = allNames
            .filter { it.contains(receiverName, ignoreCase = true) }
            .flatMap { cache.getClassesByName(it, scope).toList() }
        log.info("[GrpcNav] Candidate simple names: ${candidates.mapNotNull { it.name }}")

        // 2. "GrpcService" 로 끝나는 클래스 우선 매칭
        val grpcServiceMatch = candidates.firstOrNull { psiClass ->
            val name = psiClass.name ?: return@firstOrNull false
            val hasMethod = psiClass.findMethodsByName(methodName, true).isNotEmpty()
            val isGrpcService = name.endsWith("GrpcService")
            isGrpcService && hasMethod
        }
        if (grpcServiceMatch != null) {
            log.info("[GrpcNav] Matched GrpcService implementation=${grpcServiceMatch.qualifiedName}")
            return grpcServiceMatch
        }

        // 3. fallback: ImplBase / CoroutineImplBase 클래스 탐색
        val fallbackMatch = candidates.firstOrNull { psiClass ->
            val name = psiClass.name ?: return@firstOrNull false
            val hasMethod = psiClass.findMethodsByName(methodName, true).isNotEmpty()
            val isImplBase = name.endsWith("ImplBase")
            val isCoroutineImplBase = name.endsWith("CoroutineImplBase")
            (isImplBase || isCoroutineImplBase) && hasMethod
        }
        if (fallbackMatch != null) {
            log.info("[GrpcNav] Matched fallback implementation=${fallbackMatch.qualifiedName}")
        }
        return fallbackMatch
    }
}