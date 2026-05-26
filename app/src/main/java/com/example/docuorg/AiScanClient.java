package com.example.docuorg;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.util.Base64;

import androidx.annotation.NonNull;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import android.util.Log;

public final class AiScanClient {
    private static final String TAG = "AiScanClient";
    private static final String MODEL_ENDPOINT =
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-3-flash-preview:generateContent?key=";

    private static final String SYSTEM_PROMPT = "You are a document OCR assistant. "
            + "Extract fields from the provided document image and return ONLY valid JSON. "
            + "Required JSON keys: title, category, date, amount, storeName, tags, notes, confidence. "
            + "Rules: date must be in YYYY-MM-DD, amount must be a number as string (e.g., \"12.34\"), "
            + "category should be one of [Receipt, Medical, Tax, Personal, Other]. "
            + "tags must be an array of short keywords. "
            + "notes should be a short summary if present, else empty string. "
            + "confidence should be a number between 0 and 1. "
            + "If a field is not found, use an empty string or empty array. "
            + "Return only JSON, no markdown, no explanation.";

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public interface ScanCallback {
        void onSuccess(@NonNull DocumentScanResult result);

        void onError(@NonNull String message);
    }

    public static final class DocumentScanResult {
        public String title = "";
        public String category = "";
        public String date = "";
        public String amount = "";
        public String storeName = "";
        public String notes = "";
        public List<String> tags = new ArrayList<>();
        public String rawJson = "";
    }

    public void scanImage(@NonNull Context context, @NonNull Uri uri, @NonNull ScanCallback callback) {
        String apiKey = BuildConfig.GEMINI_API_KEY;
        Log.d(TAG, "scanImage: Starting scan for URI: " + uri);

        if (apiKey == null || apiKey.trim().isEmpty()) {
            Log.e(TAG, "scanImage: Missing GEMINI_API_KEY");
            callback.onError("Missing GEMINI_API_KEY. Add it to local.properties.");
            return;
        }
        Log.d(TAG, "scanImage: API key found, length: " + apiKey.length());

        executor.execute(() -> {
            try {
                ContentResolver resolver = context.getContentResolver();
                String mimeType = resolver.getType(uri);
                if (mimeType == null || mimeType.isEmpty()) {
                    mimeType = "image/jpeg";
                }
                Log.d(TAG, "scanImage: MIME type detected: " + mimeType);

                byte[] bytes = readBytes(resolver, uri, 6 * 1024 * 1024);
                if (bytes == null || bytes.length == 0) {
                    Log.e(TAG, "scanImage: Failed to read image bytes");
                    callback.onError("Unable to read image data.");
                    return;
                }
                Log.d(TAG, "scanImage: Image bytes read successfully, size: " + bytes.length + " bytes");

                String base64 = Base64.encodeToString(bytes, Base64.NO_WRAP);
                Log.d(TAG, "scanImage: Base64 encoding complete, encoded size: " + base64.length());

                String payload = buildRequestPayload(base64, mimeType);
                if (payload.isEmpty()) {
                    Log.e(TAG, "scanImage: Failed to build request payload");
                    callback.onError("Unable to build AI request payload.");
                    return;
                }
                Log.d(TAG, "scanImage: Request payload built, size: " + payload.length() + " bytes");

                HttpURLConnection connection = null;
                try {
                    URL url = new URL(MODEL_ENDPOINT + apiKey);
                    Log.d(TAG, "scanImage: Connecting to AI service endpoint");

                    connection = (HttpURLConnection) url.openConnection();
                    connection.setRequestMethod("POST");
                    connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                    connection.setDoOutput(true);

                    byte[] bodyBytes = payload.getBytes(StandardCharsets.UTF_8);
                    connection.getOutputStream().write(bodyBytes);
                    Log.d(TAG, "scanImage: Request sent to AI service");

                    int code = connection.getResponseCode();
                    Log.d(TAG, "scanImage: Response code received: " + code);

                    InputStream stream = code >= 200 && code < 300
                            ? connection.getInputStream()
                            : connection.getErrorStream();
                    String responseBody = readString(stream);

                    if (code < 200 || code >= 300) {
                        String errorMsg = "AI request failed: " + code + " - " + responseBody;
                        Log.e(TAG, "scanImage: Error response - " + errorMsg);
                        callback.onError(errorMsg);
                        return;
                    }

                    Log.d(TAG, "scanImage: Response body received, length: " + responseBody.length());

                    DocumentScanResult result = parseResponse(responseBody);
                    if (result == null) {
                        Log.e(TAG, "scanImage: Failed to parse AI response");
                        callback.onError("Unable to parse AI response.");
                        return;
                    }
                    Log.d(TAG, "scanImage: Response parsed successfully. Title: " + result.title +
                            ", Category: " + result.category + ", Confidence: " + result.rawJson);

                    callback.onSuccess(result);
                } finally {
                    if (connection != null) {
                        connection.disconnect();
                        Log.d(TAG, "scanImage: Connection closed");
                    }
                }
            } catch (Exception e) {
                String errorMsg = e.getMessage() != null ? e.getMessage() : "Unknown error";
                Log.e(TAG, "scanImage: Exception occurred - " + errorMsg, e);
                callback.onError("AI scan failed: " + errorMsg);
            }
        });
    }

    private String buildRequestPayload(String base64, String mimeType) {
        try {
            Log.d(TAG, "buildRequestPayload: Building JSON payload with MIME type: " + mimeType);

            JSONObject root = new JSONObject();

            JSONObject systemInstruction = new JSONObject();
            JSONArray systemParts = new JSONArray();
            systemParts.put(new JSONObject().put("text", SYSTEM_PROMPT));
            systemInstruction.put("parts", systemParts);
            root.put("systemInstruction", systemInstruction);

            JSONArray userParts = new JSONArray();
            userParts.put(new JSONObject().put("text", "Extract the document data from this image."));
            JSONObject inlineData = new JSONObject()
                    .put("mime_type", mimeType)
                    .put("data", base64);
            userParts.put(new JSONObject().put("inline_data", inlineData));

            JSONObject userContent = new JSONObject().put("parts", userParts);
            root.put("contents", new JSONArray().put(userContent));

            JSONObject generationConfig = new JSONObject()
                    .put("temperature", 0.2)
                    .put("response_mime_type", "application/json");
            root.put("generationConfig", generationConfig);

            String payload = root.toString();
            Log.d(TAG, "buildRequestPayload: Payload constructed successfully");
            return payload;
        } catch (Exception e) {
            Log.e(TAG, "buildRequestPayload: Error building payload - " + e.getMessage(), e);
            return "";
        }
    }

    private DocumentScanResult parseResponse(String responseBody) {
        try {
            Log.d(TAG, "parseResponse: Parsing AI response");

            JSONObject root = new JSONObject(responseBody);
            JSONArray candidates = root.optJSONArray("candidates");
            if (candidates == null || candidates.length() == 0) {
                Log.w(TAG, "parseResponse: No candidates found in response");
                return null;
            }
            Log.d(TAG, "parseResponse: Found " + candidates.length() + " candidate(s)");

            JSONObject content = candidates.optJSONObject(0).optJSONObject("content");
            if (content == null) {
                Log.w(TAG, "parseResponse: No content found in first candidate");
                return null;
            }

            JSONArray parts = content.optJSONArray("parts");
            if (parts == null || parts.length() == 0) {
                Log.w(TAG, "parseResponse: No parts found in content");
                return null;
            }
            Log.d(TAG, "parseResponse: Found " + parts.length() + " part(s)");

            String jsonText = parts.optJSONObject(0).optString("text", "");
            if (jsonText.isEmpty()) {
                Log.w(TAG, "parseResponse: Text content is empty");
                return null;
            }
            Log.d(TAG, "parseResponse: JSON text extracted, length: " + jsonText.length());
            Log.d(TAG, "parseResponse: Raw JSON content: " + jsonText);

            JSONObject payload = new JSONObject(jsonText);

            DocumentScanResult result = new DocumentScanResult();
            result.rawJson = jsonText;
            result.title = JsonUtils.getString(payload, "title");
            result.category = normalizeCategory(JsonUtils.getString(payload, "category"));
            result.date = JsonUtils.getString(payload, "date");
            result.amount = JsonUtils.getString(payload, "amount");
            result.storeName = JsonUtils.getString(payload, "storeName");
            result.notes = JsonUtils.getString(payload, "notes");
            result.tags = JsonUtils.getStringList(payload, "tags");

            Log.d(TAG, "parseResponse: Successfully parsed result - Title: " + result.title +
                    ", Category: " + result.category + ", Date: " + result.date +
                    ", Amount: " + result.amount + ", Store: " + result.storeName +
                    ", Tags: " + result.tags.size());

            return result;
        } catch (Exception e) {
            Log.e(TAG, "parseResponse: Error parsing response - " + e.getMessage(), e);
            return null;
        }
    }

    private String normalizeCategory(String category) {
        if (category == null) {
            return "";
        }
        String normalized = category.trim().toLowerCase(Locale.US);
        switch (normalized) {
            case "receipt":
                return "Receipt";
            case "medical":
                return "Medical";
            case "tax":
                return "Tax";
            case "personal":
                return "Personal";
            case "other":
                return "Other";
            default:
                return category;
        }
    }

    private byte[] readBytes(ContentResolver resolver, Uri uri, int maxBytes) throws IOException {
        Log.d(TAG, "readBytes: Reading image from URI, max size: " + (maxBytes / 1024 / 1024) + "MB");

        try (InputStream inputStream = resolver.openInputStream(uri)) {
            if (inputStream == null) {
                Log.e(TAG, "readBytes: Unable to open input stream");
                return null;
            }
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int read;
            int total = 0;
            while ((read = inputStream.read(buffer)) != -1) {
                total += read;
                if (total > maxBytes) {
                    Log.e(TAG, "readBytes: Image exceeds maximum size limit");
                    throw new IOException("Image too large");
                }
                outputStream.write(buffer, 0, read);
            }
            Log.d(TAG, "readBytes: Successfully read " + total + " bytes from image");
            return outputStream.toByteArray();
        }
    }

    private String readString(InputStream inputStream) throws IOException {
        if (inputStream == null) {
            return "";
        }
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = inputStream.read(buffer)) != -1) {
            outputStream.write(buffer, 0, read);
        }
        return new String(outputStream.toByteArray(), StandardCharsets.UTF_8);
    }

    private static final class JsonUtils {
        private static String getString(JSONObject object, String key) {
            if (object == null) {
                return "";
            }
            return object.optString(key, "");
        }

        private static List<String> getStringList(JSONObject object, String key) {
            List<String> list = new ArrayList<>();
            if (object == null) {
                return list;
            }
            JSONArray array = object.optJSONArray(key);
            if (array == null) {
                return list;
            }
            for (int i = 0; i < array.length(); i++) {
                String value = array.optString(i, "");
                if (!value.isEmpty()) {
                    list.add(value);
                }
            }
            return list;
        }
    }
}
