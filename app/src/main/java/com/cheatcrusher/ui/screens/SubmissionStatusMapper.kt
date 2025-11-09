package com.cheatcrusher.ui.screens

import com.cheatcrusher.data.local.CachedQuiz

object SubmissionStatusMapper {
    fun resolveTitleForPending(pendingQuizId: String, cachedList: List<CachedQuiz>): String {
        return cachedList.firstOrNull { it.quizId == pendingQuizId }?.title ?: pendingQuizId
    }
}