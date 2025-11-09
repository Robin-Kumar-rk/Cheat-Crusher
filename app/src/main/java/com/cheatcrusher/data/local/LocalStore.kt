package com.cheatcrusher.data.local

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.io.File

object LocalStore {
    private fun dir(context: Context): File = File(context.filesDir, "quizzes").apply { mkdirs() }

    fun saveQuiz(context: Context, quizId: String, code: String, title: String, rawJson: String) {
        try {
            val root = dir(context)
            File(root, "$quizId.json").writeText(rawJson)
            val meta = JSONObject().apply {
                put("quizId", quizId)
                put("code", code)
                put("title", title)
            }
            File(root, "$quizId.meta.json").writeText(meta.toString())
            Log.d("LocalStore", "Saved quiz $quizId to local storage")
        } catch (e: Exception) {
            Log.e("LocalStore", "Failed to save quiz", e)
        }
    }

    fun readQuiz(context: Context, quizId: String): String? {
        return try {
            val root = dir(context)
            val file = File(root, "$quizId.json")
            if (file.exists()) file.readText() else null
        } catch (e: Exception) { null }
    }

    fun listSaved(context: Context): List<Triple<String, String, String>> {
        return try {
            val root = dir(context)
            root.listFiles { _, name -> name.endsWith(".meta.json") }?.mapNotNull { f ->
                try {
                    val meta = JSONObject(f.readText())
                    Triple(meta.getString("quizId"), meta.getString("code"), meta.getString("title"))
                } catch (_: Exception) { null }
            } ?: emptyList()
        } catch (e: Exception) { emptyList() }
    }

    fun saveAnswerCode(context: Context, quizId: String, ansCode: String) {
        try {
            val root = dir(context)
            val metaFile = File(root, "$quizId.meta.json")
            val meta = if (metaFile.exists()) {
                try { JSONObject(metaFile.readText()) } catch (_: Exception) { JSONObject() }
            } else {
                JSONObject().apply { put("quizId", quizId) }
            }
            meta.put("ansCode", ansCode)
            metaFile.writeText(meta.toString())
            Log.d("LocalStore", "Saved ans code for $quizId")
        } catch (e: Exception) {
            Log.e("LocalStore", "Failed to save ans code", e)
        }
    }

    fun readAnswerCode(context: Context, quizId: String): String? {
        return try {
            val root = dir(context)
            val metaFile = File(root, "$quizId.meta.json")
            if (!metaFile.exists()) return null
            val meta = JSONObject(metaFile.readText())
            meta.optString("ansCode").takeIf { it.isNotBlank() }
        } catch (e: Exception) { null }
    }

    fun deleteQuiz(context: Context, quizId: String) {
        try {
            val root = dir(context)
            val raw = File(root, "$quizId.json")
            val meta = File(root, "$quizId.meta.json")
            if (raw.exists()) raw.delete()
            if (meta.exists()) meta.delete()
            Log.d("LocalStore", "Deleted quiz $quizId from local storage")
        } catch (e: Exception) {
            Log.e("LocalStore", "Failed to delete quiz $quizId", e)
        }
    }
}
