package com.example.docuorg;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Small helper around the local SharedPreferences JSON store used for offline-first documents.
 */
public final class LocalDocumentsStore {

    private LocalDocumentsStore() {
    }

    private static final String PREFS_NAME = "docuorg_prefs";
    private static final String PREFS_DOCUMENTS_JSON = "documents_json";

    @NonNull
    public static JSONArray readAll(@NonNull Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String raw = prefs.getString(PREFS_DOCUMENTS_JSON, "[]");
        try {
            return new JSONArray(raw);
        } catch (JSONException ignored) {
            return new JSONArray();
        }
    }

    public static void writeAll(@NonNull Context context, @NonNull JSONArray array) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString(PREFS_DOCUMENTS_JSON, array.toString()).apply();
    }

    @Nullable
    public static JSONObject findById(@NonNull Context context, long documentId) {
        JSONArray docs = readAll(context);
        for (int i = 0; i < docs.length(); i++) {
            JSONObject obj = docs.optJSONObject(i);
            if (obj == null) {
                continue;
            }
            long id = obj.optLong("id", -1);
            if (id == documentId) {
                return obj;
            }
        }
        return null;
    }

    public static boolean upsert(@NonNull Context context, @NonNull JSONObject doc) {
        try {
            long id = doc.optLong("id", -1);
            if (id <= 0) {
                return false;
            }

            JSONArray docs = readAll(context);
            boolean replaced = false;
            for (int i = 0; i < docs.length(); i++) {
                JSONObject existing = docs.optJSONObject(i);
                if (existing == null) {
                    continue;
                }
                if (existing.optLong("id", -1) == id) {
                    docs.put(i, doc);
                    replaced = true;
                    break;
                }
            }
            if (!replaced) {
                docs.put(doc);
            }
            writeAll(context, docs);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    public static boolean delete(@NonNull Context context, long documentId) {
        try {
            JSONArray docs = readAll(context);
            JSONArray out = new JSONArray();
            boolean deleted = false;
            for (int i = 0; i < docs.length(); i++) {
                JSONObject obj = docs.optJSONObject(i);
                if (obj == null) {
                    continue;
                }
                long id = obj.optLong("id", -1);
                if (id == documentId) {
                    deleted = true;
                    continue;
                }
                out.put(obj);
            }
            if (deleted) {
                writeAll(context, out);
            }
            return deleted;
        } catch (Exception ignored) {
            return false;
        }
    }
}

