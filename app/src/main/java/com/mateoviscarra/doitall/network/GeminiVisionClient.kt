package com.mateoviscarra.doitall.network

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.OutputStreamWriter
import java.net.URL
import javax.net.ssl.HttpsURLConnection

private const val TAG = "GeminiVisionClient"

data class FoodScanSubItem(
    val name: String,
    val portion: String,
    val calories: Int,
    val proteinGrams: Float,
    val carbsGrams: Float,
    val fatGrams: Float
)

data class FoodScanResult(
    val mealName: String,
    val calories: Int,
    val proteinGrams: Float,
    val carbsGrams: Float,
    val fatGrams: Float,
    val servingDescription: String,
    val items: List<FoodScanSubItem> = emptyList(),
    val notes: String = ""
)

class GeminiVisionClient(private val context: Context) {

    suspend fun analyzeFoodImage(
        imageUri: Uri,
        apiKey: String,
        systemPrompt: String,
        userNote: String = ""
    ): Result<FoodScanResult> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext Result.failure(
                IllegalStateException("Gemini API key is not configured. Please enter your Google AI Studio API key in Settings -> AI Vision Scanner.")
            )
        }

        try {
            val base64Image = encodeImageToBase64(imageUri)
                ?: return@withContext Result.failure(IllegalArgumentException("Failed to decode the selected image."))

            // List of active models in order of preference
            val models = listOf(
                "gemini-3.6-flash",
                "gemini-flash-lite-latest",
                "gemini-3.1-flash-lite",
                "gemini-flash-latest",
                "gemini-3.8-flash"
            )
            var lastError: Exception? = null

            for (model in models) {
                try {
                    Log.d(TAG, "Attempting food analysis with model: $model")
                    val result = callGeminiGenerate(model, apiKey, systemPrompt, base64Image, userNote)
                    return@withContext Result.success(result)
                } catch (e: Exception) {
                    val msg = e.message.orEmpty()
                    Log.w(TAG, "Model $model attempt failed: $msg")
                    lastError = e
                    // If it's a model-not-found (404), temporarily unavailable (503), or rate-limit (429), try next model
                    val isRecoverable = msg.contains("404") || msg.contains("503") ||
                            msg.contains("429") || msg.contains("UNAVAILABLE") ||
                            msg.contains("not found", ignoreCase = true) ||
                            msg.contains("no longer available", ignoreCase = true)
                    if (!isRecoverable) {
                        // Bad request (e.g. invalid API key or malformed request), don't loop through all models
                        throw e
                    }
                }
            }

            Result.failure(lastError ?: RuntimeException("Could not analyze image with Gemini."))
        } catch (e: Exception) {
            Log.e(TAG, "Error analyzing food image", e)
            Result.failure(e)
        }
    }

    private fun callGeminiGenerate(
        model: String,
        apiKey: String,
        systemPrompt: String,
        base64Jpeg: String,
        userNote: String
    ): FoodScanResult {
        val endpoint = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"
        val url = URL(endpoint)
        val conn = (url.openConnection() as HttpsURLConnection).apply {
            requestMethod = "POST"
            setRequestProperty("Content-Type", "application/json")
            connectTimeout = 30000
            readTimeout = 60000
            doInput = true
            doOutput = true
        }

        val requestJson = JSONObject()

        // System instruction
        if (systemPrompt.isNotBlank()) {
            val sysObj = JSONObject()
            val sysParts = JSONArray().apply {
                put(JSONObject().put("text", systemPrompt))
            }
            sysObj.put("parts", sysParts)
            requestJson.put("system_instruction", sysObj)
        }

        // Contents
        val promptText = buildString {
            append("Analyze the provided image (food dish or nutrition facts label). Estimate total calories, protein (g), carbs (g), and fat (g) for the entire meal as a single whole for quick logging.")
            append("\nIMPORTANT RULES:")
            append("\n1. Do NOT break down the meal into individual ingredients or sub-items. Estimate ONLY the overall total calories and macronutrients for the entire meal combined.")
            append("\n2. Keep 'mealName' short, simple, and clean (1 to 3 words maximum, e.g. 'Ceviche', 'Silpancho', 'Oatmeal', 'Steak & Rice', 'Protein Bar'). Do NOT create long compound or overly descriptive names (e.g. use 'Ceviche' instead of 'Ceviche Boliviano de Trucha con Camote').")
            append("\n3. If user context notes extra sides or ingredients (e.g. 'camote', 'extra cheese'), factor their calories and macros into the overall totals, and mention them in 'servingDescription' (e.g. '1 plato con camote') or 'notes', but keep 'mealName' concise.")
            if (userNote.isNotBlank()) {
                append("\nUser context / notes: ").append(userNote)
            }
            append("\nReturn strictly JSON with this schema:")
            append("""
                {
                  "mealName": "Concise name (1-3 words max, e.g. Ceviche, Silpancho, Oatmeal)",
                  "servingDescription": "Estimated portion description (e.g. 1 plate, 250g, 1 unit)",
                  "calories": 0,
                  "proteinGrams": 0.0,
                  "carbsGrams": 0.0,
                  "fatGrams": 0.0,
                  "notes": "Short explanation or context"
                }
            """.trimIndent())
        }

        val contentParts = JSONArray().apply {
            put(
                JSONObject().put(
                    "inline_data",
                    JSONObject()
                        .put("mime_type", "image/jpeg")
                        .put("data", base64Jpeg)
                )
            )
            put(JSONObject().put("text", promptText))
        }

        val contents = JSONArray().apply {
            put(JSONObject().put("parts", contentParts))
        }
        requestJson.put("contents", contents)

        // Generation config for JSON
        val genConfig = JSONObject().apply {
            put("response_mime_type", "application/json")
        }
        requestJson.put("generationConfig", genConfig)

        // Write request
        OutputStreamWriter(conn.outputStream).use { writer ->
            writer.write(requestJson.toString())
            writer.flush()
        }

        val responseCode = conn.responseCode
        if (responseCode !in 200..299) {
            val errorBody = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "HTTP $responseCode"
            Log.e(TAG, "Gemini API error ($responseCode): $errorBody")
            val cleanMsg = try {
                val errObj = JSONObject(errorBody)
                val msg = errObj.optJSONObject("error")?.optString("message") ?: errorBody
                msg
            } catch (_: Exception) {
                errorBody
            }
            throw RuntimeException("Gemini error ($responseCode): $cleanMsg")
        }

        val responseBody = conn.inputStream.bufferedReader().use { it.readText() }
        return parseGeminiResponse(responseBody)
    }

    private fun parseGeminiResponse(jsonText: String): FoodScanResult {
        val root = JSONObject(jsonText)
        val candidates = root.optJSONArray("candidates")
            ?: throw IllegalStateException("Gemini returned no candidates.")
        if (candidates.length() == 0) throw IllegalStateException("Empty candidates in response.")

        val firstCandidate = candidates.getJSONObject(0)
        val content = firstCandidate.optJSONObject("content")
            ?: throw IllegalStateException("No content in candidate.")
        val parts = content.optJSONArray("parts")
            ?: throw IllegalStateException("No parts in content.")
        if (parts.length() == 0) throw IllegalStateException("Empty parts in content.")

        val rawText = parts.getJSONObject(0).optString("text", "")
        if (rawText.isBlank()) throw IllegalStateException("Empty text in Gemini response.")

        // Clean any accidental markdown wrap
        val cleanedJson = rawText.trim()
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()

        val parsed = JSONObject(cleanedJson)

        var mealName = parsed.optString("mealName", "").ifBlank {
            parsed.optString("foodName", "").ifBlank {
                parsed.optString("dishName", "").ifBlank {
                    parsed.optString("name", "Estimated Meal")
                }
            }
        }.trim()

        if (mealName.isBlank()) {
            mealName = "Estimated Meal"
        }

        var servingDescription = parsed.optString("servingDescription", "").ifBlank {
            parsed.optString("portion", "").ifBlank {
                parsed.optString("servingSize", "").ifBlank {
                    parsed.optString("serving", "1 serving")
                }
            }
        }.trim()
        if (servingDescription.isBlank()) {
            servingDescription = "1 serving"
        }

        val calories = if (parsed.has("calories")) {
            parsed.optInt("calories", 0)
        } else {
            parsed.optInt("totalCalories", 0)
        }

        val protein = if (parsed.has("proteinGrams")) {
            parsed.optDouble("proteinGrams", 0.0).toFloat()
        } else {
            parsed.optDouble("protein", 0.0).toFloat()
        }

        val carbs = if (parsed.has("carbsGrams")) {
            parsed.optDouble("carbsGrams", 0.0).toFloat()
        } else {
            parsed.optDouble("carbs", 0.0).toFloat()
        }

        val fat = if (parsed.has("fatGrams")) {
            parsed.optDouble("fatGrams", 0.0).toFloat()
        } else {
            parsed.optDouble("fat", 0.0).toFloat()
        }

        val notes = parsed.optString("notes", "").ifBlank {
            parsed.optString("note", "")
        }

        return FoodScanResult(
            mealName = mealName,
            calories = calories,
            proteinGrams = protein,
            carbsGrams = carbs,
            fatGrams = fat,
            servingDescription = servingDescription,
            items = emptyList(),
            notes = notes
        )
    }

    private fun encodeImageToBase64(uri: Uri): String? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return null
            // First decode bounds
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeStream(inputStream, null, options)
            inputStream.close()

            // Calculate inSampleSize to fit inside 1024x1024
            val maxDim = 1024
            var sampleSize = 1
            var width = options.outWidth
            var height = options.outHeight

            while (width / 2 >= maxDim || height / 2 >= maxDim) {
                width /= 2
                height /= 2
                sampleSize *= 2
            }

            val decodeStream = context.contentResolver.openInputStream(uri) ?: return null
            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.RGB_565
            }
            val originalBitmap = BitmapFactory.decodeStream(decodeStream, null, decodeOptions)
            decodeStream.close()

            if (originalBitmap == null) return null

            val finalBitmap = if (originalBitmap.width > maxDim || originalBitmap.height > maxDim) {
                val ratio = minOf(
                    maxDim.toFloat() / originalBitmap.width,
                    maxDim.toFloat() / originalBitmap.height
                )
                val targetW = (originalBitmap.width * ratio).toInt()
                val targetH = (originalBitmap.height * ratio).toInt()
                Bitmap.createScaledBitmap(originalBitmap, targetW, targetH, true)
            } else {
                originalBitmap
            }

            val byteArrayOutputStream = ByteArrayOutputStream()
            finalBitmap.compress(Bitmap.CompressFormat.JPEG, 80, byteArrayOutputStream)
            val bytes = byteArrayOutputStream.toByteArray()
            Base64.encodeToString(bytes, Base64.NO_WRAP)
        } catch (e: Exception) {
            Log.e(TAG, "Error encoding image to Base64", e)
            null
        }
    }
}
