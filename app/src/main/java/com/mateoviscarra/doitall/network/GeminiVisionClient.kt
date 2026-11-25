package com.mateoviscarra.doitall.network

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.util.Log
import com.mateoviscarra.doitall.data.NutritionGoals
import com.mateoviscarra.doitall.data.UserProfile
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

data class SupplementScanResult(
    val name: String,
    val dosageDescription: String,
    val calories: Int,
    val proteinGrams: Float,
    val carbsGrams: Float,
    val fatGrams: Float,
    val micronutrientsSummary: String,
    val notes: String = ""
)

class GeminiVisionClient(private val context: Context) {

    suspend fun analyzeFoodImage(
        imageUri: Uri,
        apiKey: String,
        systemPrompt: String,
        userNote: String = "",
        handScaleInstruction: String = ""
    ): Result<FoodScanResult> = analyzeFoodImages(
        imageUris = listOf(imageUri),
        apiKey = apiKey,
        systemPrompt = systemPrompt,
        userNote = userNote,
        handScaleInstruction = handScaleInstruction
    )

    suspend fun analyzeFoodImages(
        imageUris: List<Uri>,
        apiKey: String,
        systemPrompt: String,
        userNote: String = "",
        handScaleInstruction: String = ""
    ): Result<FoodScanResult> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext Result.failure(
                IllegalStateException("Gemini API key is not configured. Please enter your Google AI Studio API key in Settings -> AI Vision Scanner.")
            )
        }
        if (imageUris.isEmpty() && userNote.isBlank()) {
            return@withContext Result.failure(
                IllegalArgumentException("Please add at least one photo or describe what you ate.")
            )
        }

        try {
            val base64Images = if (imageUris.isNotEmpty()) {
                imageUris.mapNotNull { encodeImageToBase64(it) }
            } else {
                emptyList()
            }
            if (imageUris.isNotEmpty() && base64Images.isEmpty()) {
                return@withContext Result.failure(IllegalArgumentException("Failed to decode the selected image(s)."))
            }

            // List of active models in order of speed and preference (flash-lite models provide sub-2s responses)
            val models = listOf(
                "gemini-flash-lite-latest",
                "gemini-3.5-flash-lite",
                "gemini-3.1-flash-lite",
                "gemini-3.6-flash",
                "gemini-flash-latest"
            )
            var lastError: Exception? = null

            for (model in models) {
                try {
                    Log.d(TAG, "Attempting food analysis (${base64Images.size} images) with model: $model")
                    val result = callGeminiGenerate(model, apiKey, systemPrompt, base64Images, userNote, handScaleInstruction)
                    return@withContext Result.success(result)
                } catch (e: Exception) {
                    val msg = e.message.orEmpty()
                    Log.w(TAG, "Model $model attempt failed: $msg")
                    lastError = e
                    // If it's a model-not-found (404), temporarily unavailable (503), rate-limit (429), or timeout, try next model
                    val isRecoverable = msg.contains("404") || msg.contains("503") ||
                            msg.contains("429") || msg.contains("UNAVAILABLE") ||
                            msg.contains("not found", ignoreCase = true) ||
                            msg.contains("no longer available", ignoreCase = true) ||
                            msg.contains("timeout", ignoreCase = true) ||
                            msg.contains("timed out", ignoreCase = true) ||
                            e is java.net.SocketTimeoutException ||
                            e is java.net.SocketException ||
                            e is java.io.InterruptedIOException
                    if (!isRecoverable) {
                        // Bad request (e.g. invalid API key or malformed request), don't loop through all models
                        throw e
                    }
                }
            }

            Result.failure(lastError ?: RuntimeException("Could not analyze image(s) with Gemini."))
        } catch (e: Exception) {
            Log.e(TAG, "Error analyzing food image(s)", e)
            Result.failure(e)
        }
    }

    private fun callGeminiGenerate(
        model: String,
        apiKey: String,
        systemPrompt: String,
        base64Jpegs: List<String>,
        userNote: String,
        handScaleInstruction: String = ""
    ): FoodScanResult {
        val endpoint = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"
        val url = URL(endpoint)
        val conn = (url.openConnection() as HttpsURLConnection).apply {
            requestMethod = "POST"
            setRequestProperty("Content-Type", "application/json")
            connectTimeout = 15000
            readTimeout = 25000
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
        val promptText = if (base64Jpegs.isNotEmpty()) {
            buildString {
                append("Analyze the provided image(s) (which may include a food dish, a nutrition facts label, and/or a serving size reference). Combine information across all provided images to estimate total calories, protein (g), carbs (g), and fat (g) for the meal consumed as a single whole for quick logging.")
                append("\nIMPORTANT RULES:")
                append("\n1. Do NOT break down the meal into individual ingredients or sub-items. Estimate ONLY the overall total calories and macronutrients for the entire meal combined.")
                append("\n2. If both a nutrition label and a portion/serving photo (or user note) are provided, use the nutrition label values scaled to the actual serving size shown or described.")
                append("\n3. Keep 'mealName' short, simple, and clean (1 to 3 words maximum, e.g. 'Ceviche', 'Silpancho', 'Oatmeal', 'Steak & Rice', 'Protein Bar'). Do NOT create long compound or overly descriptive names (e.g. use 'Ceviche' instead of 'Ceviche Boliviano de Trucha con Camote').")
                append("\n4. If user context notes extra sides or ingredients (e.g. 'camote', 'extra cheese'), factor their calories and macros into the overall totals, and mention them in 'servingDescription' (e.g. '1 plato con camote') or 'notes', but keep 'mealName' concise.")
                if (handScaleInstruction.isNotBlank()) {
                    append("\n\n").append(handScaleInstruction.trim())
                }
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
        } else {
            buildString {
                append("Estimate total calories, protein (g), carbs (g), and fat (g) for the meal described below as a single whole for quick logging.")
                append("\nMeal description: ").append(userNote)
                append("\nIMPORTANT RULES:")
                append("\n1. Do NOT break down the meal into individual ingredients or sub-items. Estimate ONLY the overall total calories and macronutrients for the entire meal combined.")
                append("\n2. Keep 'mealName' short, simple, and clean (1 to 3 words maximum, e.g. 'Ceviche', 'Silpancho', 'Oatmeal', 'Steak & Rice', 'Protein Bar'). Do NOT create long compound or overly descriptive names.")
                append("\n3. In 'servingDescription', describe the estimated portion (e.g. '1 plate (~400g)', '2 units').")
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
        }

        val contentParts = JSONArray().apply {
            base64Jpegs.forEach { b64 ->
                put(
                    JSONObject().put(
                        "inline_data",
                        JSONObject()
                            .put("mime_type", "image/jpeg")
                            .put("data", b64)
                    )
                )
            }
            put(JSONObject().put("text", promptText))
        }

        val contents = JSONArray().apply {
            put(JSONObject().put("parts", contentParts))
        }
        requestJson.put("contents", contents)

        // Generation config for JSON
        val genConfig = JSONObject().apply {
            put("response_mime_type", "application/json")
            put("maxOutputTokens", 512)
            if (!model.contains("lite")) {
                put("thinkingConfig", JSONObject().put("thinkingBudget", 0))
            }
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

    suspend fun askMenuAdvice(
        imageUris: List<Uri>,
        menuText: String,
        userProfile: UserProfile,
        goals: NutritionGoals,
        remainingCalories: Int?,
        remainingProtein: Float?,
        apiKey: String
    ): Result<String> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext Result.failure(
                IllegalStateException("Gemini API key is not configured. Please enter your Google AI Studio API key in Settings -> AI Vision Scanner.")
            )
        }
        if (imageUris.isEmpty() && menuText.isBlank()) {
            return@withContext Result.failure(
                IllegalArgumentException("Please take a photo of the menu or enter menu items/notes.")
            )
        }

        try {
            val base64Images = if (imageUris.isNotEmpty()) {
                imageUris.mapNotNull { encodeImageToBase64(it) }
            } else {
                emptyList()
            }
            if (imageUris.isNotEmpty() && base64Images.isEmpty()) {
                return@withContext Result.failure(IllegalArgumentException("Failed to decode the menu photo(s)."))
            }

            val models = listOf(
                "gemini-flash-lite-latest",
                "gemini-3.5-flash-lite",
                "gemini-3.1-flash-lite",
                "gemini-3.6-flash",
                "gemini-flash-latest"
            )
            var lastError: Exception? = null

            for (model in models) {
                try {
                    Log.d(TAG, "Attempting menu advice with model: $model (${base64Images.size} images)")
                    val result = callGeminiMenuAdvice(
                        model = model,
                        apiKey = apiKey,
                        userProfile = userProfile,
                        goals = goals,
                        remainingCalories = remainingCalories,
                        remainingProtein = remainingProtein,
                        base64Jpegs = base64Images,
                        menuText = menuText
                    )
                    return@withContext Result.success(result)
                } catch (e: Exception) {
                    val msg = e.message.orEmpty()
                    Log.w(TAG, "Model $model menu advice attempt failed: $msg")
                    lastError = e
                    val isRecoverable = msg.contains("404") || msg.contains("503") ||
                            msg.contains("429") || msg.contains("UNAVAILABLE") ||
                            msg.contains("not found", ignoreCase = true) ||
                            msg.contains("no longer available", ignoreCase = true) ||
                            msg.contains("timeout", ignoreCase = true) ||
                            msg.contains("timed out", ignoreCase = true) ||
                            e is java.net.SocketTimeoutException ||
                            e is java.net.SocketException ||
                            e is java.io.InterruptedIOException
                    if (!isRecoverable) {
                        throw e
                    }
                }
            }

            Result.failure(lastError ?: RuntimeException("Could not get menu recommendation from Gemini."))
        } catch (e: Exception) {
            Log.e(TAG, "Error getting menu recommendation", e)
            Result.failure(e)
        }
    }

    private fun callGeminiMenuAdvice(
        model: String,
        apiKey: String,
        userProfile: UserProfile,
        goals: NutritionGoals,
        remainingCalories: Int?,
        remainingProtein: Float?,
        base64Jpegs: List<String>,
        menuText: String
    ): String {
        val endpoint = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"
        val url = URL(endpoint)
        val conn = (url.openConnection() as HttpsURLConnection).apply {
            requestMethod = "POST"
            setRequestProperty("Content-Type", "application/json")
            connectTimeout = 15000
            readTimeout = 25000
            doInput = true
            doOutput = true
        }

        val requestJson = JSONObject()

        val systemPrompt = buildString {
            append("You are an expert sports nutritionist and fitness coach. ")
            append("The user is at a restaurant or ordering food and needs a clear, concrete recommendation on what to order based on the menu and their personal goals.")
            append("\nLocation context: The user is in Bolivia, so understand Bolivian restaurant preparations, dishes (e.g. carnes a la parrilla, bife de chorizo, lomo, cuadril, pechuga a la plancha, trucha, surubí, silpancho, pique macho, sopas, guarniciones como arroz, papas fritas, puré, ensalada, plátano, etc.) as well as international culinary terms.")
            append("\nATHLETE PROFILE & TARGETS:")
            append("\n- Sex: ").append(userProfile.sex.ifBlank { "Unspecified" })
            if (userProfile.heightCm > 0f) append("\n- Height: ").append(userProfile.heightCm).append(" cm")
            if (userProfile.targetWeightKg > 0f) append("\n- Target Weight: ").append(userProfile.targetWeightKg).append(" kg")
            if (userProfile.fitnessGoalSummary.isNotBlank()) append("\n- Fitness Goal & Context: ").append(userProfile.fitnessGoalSummary)
            append("\n- Daily Targets: ").append(goals.calorieTarget).append(" kcal | ")
                .append(goals.proteinTargetGrams.toInt()).append("g protein | ")
                .append(goals.carbsTargetGrams.toInt()).append("g carbs | ")
                .append(goals.fatTargetGrams.toInt()).append("g fat")
            if (remainingCalories != null && remainingProtein != null) {
                append("\n- Remaining budget today: ~").append(remainingCalories).append(" kcal | ~")
                    .append(remainingProtein.toInt()).append("g protein")
            }
            append("\n\nRESPONSE GUIDELINES:")
            append("\n1. CONCRETE TOP PICK: Always declare a clear, concrete single top recommendation right away (e.g. '**Recommended Choice: [Dish Name]**'). Never be vague.")
            append("\n2. WHY IT WINS: Explain succinctly why it fits their current goal (e.g. high lean protein, optimal satiety, manageable calorie density).")
            append("\n3. MODIFICATIONS & CAVEATS: Give actionable tweaks to ask the waiter/kitchen (e.g. 'Ask for the dressing on the side', 'Swap French fries for a double salad, boiled potatoes, or steamed rice', 'Avoid heavy cream sauces or deep-fried batters').")
            append("\n4. BACKUP / CONTINGENCY: Include 1-2 concrete backup choices (e.g. 'If they are out of X, order Y instead. If craving red meat, choose Z with no cheese').")
            append("\n5. ESTIMATED MACROS: Provide a rough estimated calorie & macro breakdown for the recommended dish.")
            append("\n6. FORMAT: Respond in friendly, concise, clean Markdown with bold headers and bullet points. Do NOT return JSON.")
        }

        val sysObj = JSONObject()
        val sysParts = JSONArray().apply {
            put(JSONObject().put("text", systemPrompt))
        }
        sysObj.put("parts", sysParts)
        requestJson.put("system_instruction", sysObj)

        val promptText = buildString {
            if (base64Jpegs.isNotEmpty()) {
                append("Please review the attached menu photo(s).")
            }
            if (menuText.isNotBlank()) {
                append("\nUser notes / menu items / preferences: ").append(menuText)
            } else if (base64Jpegs.isEmpty()) {
                append("\nRecommend what to order.")
            }
        }

        val contentParts = JSONArray().apply {
            base64Jpegs.forEach { b64 ->
                put(
                    JSONObject().put(
                        "inline_data",
                        JSONObject()
                            .put("mime_type", "image/jpeg")
                            .put("data", b64)
                    )
                )
            }
            put(JSONObject().put("text", promptText))
        }

        val contents = JSONArray().apply {
            put(JSONObject().put("parts", contentParts))
        }
        requestJson.put("contents", contents)

        val genConfig = JSONObject().apply {
            put("maxOutputTokens", 1024)
            if (!model.contains("lite")) {
                put("thinkingConfig", JSONObject().put("thinkingBudget", 0))
            }
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
            Log.e(TAG, "Gemini Menu Advice API error ($responseCode): $errorBody")
            val cleanMsg = try {
                val errObj = JSONObject(errorBody)
                errObj.optJSONObject("error")?.optString("message") ?: errorBody
            } catch (_: Exception) {
                errorBody
            }
            throw RuntimeException("Gemini error ($responseCode): $cleanMsg")
        }

        val responseBody = conn.inputStream.bufferedReader().use { it.readText() }
        val root = JSONObject(responseBody)
        val candidates = root.optJSONArray("candidates")
            ?: throw IllegalStateException("Gemini returned no response.")
        if (candidates.length() == 0) throw IllegalStateException("Empty candidates in response.")
        val firstCandidate = candidates.getJSONObject(0)
        val content = firstCandidate.optJSONObject("content")
            ?: throw IllegalStateException("No content in candidate.")
        val parts = content.optJSONArray("parts")
            ?: throw IllegalStateException("No parts in content.")
        if (parts.length() == 0) throw IllegalStateException("Empty parts in content.")
        return parts.getJSONObject(0).optString("text", "").trim()
    }

    suspend fun analyzeSupplementLabel(
        imageUris: List<Uri>,
        apiKey: String,
        userNote: String = ""
    ): Result<SupplementScanResult> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext Result.failure(
                IllegalStateException("Gemini API key is not configured. Please enter your Google AI Studio API key in Settings -> AI Vision Scanner.")
            )
        }
        if (imageUris.isEmpty() && userNote.isBlank()) {
            return@withContext Result.failure(
                IllegalArgumentException("Please attach a supplement bottle/label photo or enter supplement notes.")
            )
        }

        try {
            val base64Images = if (imageUris.isNotEmpty()) {
                imageUris.mapNotNull { encodeImageToBase64(it) }
            } else {
                emptyList()
            }
            if (imageUris.isNotEmpty() && base64Images.isEmpty()) {
                return@withContext Result.failure(IllegalArgumentException("Failed to decode the selected image(s)."))
            }

            val models = listOf(
                "gemini-flash-lite-latest",
                "gemini-3.5-flash-lite",
                "gemini-3.1-flash-lite",
                "gemini-3.6-flash",
                "gemini-flash-latest"
            )
            var lastError: Exception? = null

            for (model in models) {
                try {
                    val result = callGeminiSupplementLabel(model, apiKey, base64Images, userNote)
                    return@withContext Result.success(result)
                } catch (e: Exception) {
                    val msg = e.message.orEmpty()
                    lastError = e
                    val isRecoverable = msg.contains("404") || msg.contains("503") ||
                            msg.contains("429") || msg.contains("UNAVAILABLE") ||
                            msg.contains("not found", ignoreCase = true) ||
                            msg.contains("no longer available", ignoreCase = true) ||
                            msg.contains("timeout", ignoreCase = true) ||
                            msg.contains("timed out", ignoreCase = true) ||
                            e is java.net.SocketTimeoutException ||
                            e is java.net.SocketException ||
                            e is java.io.InterruptedIOException
                    if (!isRecoverable) throw e
                }
            }
            Result.failure(lastError ?: RuntimeException("Could not scan supplement label."))
        } catch (e: Exception) {
            Log.e(TAG, "Error scanning supplement label", e)
            Result.failure(e)
        }
    }

    private fun callGeminiSupplementLabel(
        model: String,
        apiKey: String,
        base64Jpegs: List<String>,
        userNote: String
    ): SupplementScanResult {
        val endpoint = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"
        val conn = (URL(endpoint).openConnection() as HttpsURLConnection).apply {
            requestMethod = "POST"
            setRequestProperty("Content-Type", "application/json")
            connectTimeout = 15000
            readTimeout = 25000
            doInput = true
            doOutput = true
        }

        val promptText = buildString {
            append("Extract supplement/pill details for a single daily serving from the attached bottle/label photo(s) and/or notes.")
            if (userNote.isNotBlank()) append("\nUser notes: ").append(userNote)
            append(
                """
                
                Return strictly JSON with this schema:
                {
                  "name": "Short supplement name (e.g. Fish Oil, Magnesium Glycinate, Multivitamin)",
                  "dosageDescription": "Serving dose (e.g. 2400mg, 2 softgels, 4g)",
                  "calories": 0,
                  "proteinGrams": 0.0,
                  "carbsGrams": 0.0,
                  "fatGrams": 0.0,
                  "micronutrientsSummary": "Concise comma-separated active ingredients/micronutrients per serving (e.g. EPA 720mg, DHA 480mg, Vitamin E 10mg)",
                  "notes": "Short helpful note (e.g. Take with food)"
                }
                """.trimIndent()
            )
        }

        val contentParts = JSONArray().apply {
            base64Jpegs.forEach { b64 ->
                put(
                    JSONObject().put(
                        "inline_data",
                        JSONObject().put("mime_type", "image/jpeg").put("data", b64)
                    )
                )
            }
            put(JSONObject().put("text", promptText))
        }

        val genConfig = JSONObject().apply {
            put("response_mime_type", "application/json")
            put("maxOutputTokens", 512)
            if (!model.contains("lite")) {
                put("thinkingConfig", JSONObject().put("thinkingBudget", 0))
            }
        }

        val requestJson = JSONObject()
            .put("contents", JSONArray().put(JSONObject().put("parts", contentParts)))
            .put("generationConfig", genConfig)

        OutputStreamWriter(conn.outputStream).use { it.write(requestJson.toString()) }

        val responseCode = conn.responseCode
        if (responseCode !in 200..299) {
            val errorBody = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "HTTP $responseCode"
            throw RuntimeException("Gemini error ($responseCode): $errorBody")
        }

        val responseBody = conn.inputStream.bufferedReader().use { it.readText() }
        val rawText = JSONObject(responseBody)
            .optJSONArray("candidates")
            ?.optJSONObject(0)
            ?.optJSONObject("content")
            ?.optJSONArray("parts")
            ?.optJSONObject(0)
            ?.optString("text", "")
            .orEmpty()

        val cleanedJson = rawText.trim()
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()
        val parsed = JSONObject(cleanedJson)

        return SupplementScanResult(
            name = parsed.optString("name", "Supplement").trim().ifBlank { "Supplement" },
            dosageDescription = parsed.optString("dosageDescription", "").trim(),
            calories = parsed.optInt("calories", 0),
            proteinGrams = parsed.optDouble("proteinGrams", 0.0).toFloat(),
            carbsGrams = parsed.optDouble("carbsGrams", 0.0).toFloat(),
            fatGrams = parsed.optDouble("fatGrams", 0.0).toFloat(),
            micronutrientsSummary = parsed.optString("micronutrientsSummary", "").trim(),
            notes = parsed.optString("notes", "").trim()
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

            // Calculate inSampleSize to fit inside 768x768 (cuts payload by ~65%, speeds up upload significantly)
            val maxDim = 768
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
            finalBitmap.compress(Bitmap.CompressFormat.JPEG, 72, byteArrayOutputStream)
            val bytes = byteArrayOutputStream.toByteArray()
            Base64.encodeToString(bytes, Base64.NO_WRAP)
        } catch (e: Exception) {
            Log.e(TAG, "Error encoding image to Base64", e)
            null
        }
    }
}
