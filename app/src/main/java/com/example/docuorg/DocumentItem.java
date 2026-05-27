package com.example.docuorg;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.firebase.firestore.DocumentSnapshot;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Simple model representing a saved document (either local JSON cache or Firestore).
 */
public final class DocumentItem {

    public final long id;
    @Nullable public final String uri;
    @Nullable public final String displayName;
    @Nullable public final String mime;
    @Nullable public final String title;
    @Nullable public final String category;
    @Nullable public final String dateText;
    @Nullable public final Long dateMillis;

    public DocumentItem(
            long id,
            @Nullable String uri,
            @Nullable String displayName,
            @Nullable String mime,
            @Nullable String title,
            @Nullable String category,
            @Nullable String dateText,
            @Nullable Long dateMillis
    ) {
        this.id = id;
        this.uri = uri;
        this.displayName = displayName;
        this.mime = mime;
        this.title = title;
        this.category = category;
        this.dateText = dateText;
        this.dateMillis = dateMillis;
    }

    @NonNull
    public String bestTitle() {
        if (title != null && !title.trim().isEmpty()) {
            return title;
        }
        if (displayName != null && !displayName.trim().isEmpty()) {
            return displayName;
        }
        return "Untitled";
    }

    @Nullable
    public static DocumentItem fromSnapshot(@NonNull DocumentSnapshot snapshot) {
        Object rawId = snapshot.get("id");
        long id;
        if (rawId instanceof Number) {
            id = ((Number) rawId).longValue();
        } else {
            // Fallback: Firestore docId may be the timestamp string.
            try {
                id = Long.parseLong(snapshot.getId());
            } catch (Exception e) {
                id = System.currentTimeMillis();
            }
        }

        String uri = snapshot.getString("uri");
        String displayName = snapshot.getString("displayName");
        String mime = snapshot.getString("mime");
        String title = snapshot.getString("title");
        String category = snapshot.getString("category");
        String dateText = snapshot.getString("dateText");

        Long dateMillis = null;
        Object rawDateMillis = snapshot.get("dateMillis");
        if (rawDateMillis instanceof Number) {
            dateMillis = ((Number) rawDateMillis).longValue();
        }

        return new DocumentItem(id, uri, displayName, mime, title, category, dateText, dateMillis);
    }

    @Nullable
    public static DocumentItem fromJson(@NonNull JSONObject obj) {
        try {
            long id = obj.optLong("id", System.currentTimeMillis());
            String uri = obj.optString("uri", null);
            String displayName = obj.optString("displayName", null);
            String mime = obj.optString("mime", null);
            String title = obj.optString("title", null);
            String category = obj.optString("category", null);
            String dateText = obj.optString("dateText", null);

            Long dateMillis = null;
            if (obj.has("dateMillis")) {
                long v = obj.optLong("dateMillis", -1);
                if (v > 0) {
                    dateMillis = v;
                }
            }

            return new DocumentItem(id, uri, displayName, mime, title, category, dateText, dateMillis);
        } catch (Exception ignored) {
            return null;
        }
    }

    @NonNull
    public static List<DocumentItem> fromJsonArray(@Nullable JSONArray array) {
        List<DocumentItem> result = new ArrayList<>();
        if (array == null) {
            return result;
        }
        for (int i = 0; i < array.length(); i++) {
            JSONObject obj = array.optJSONObject(i);
            if (obj == null) {
                continue;
            }
            DocumentItem item = fromJson(obj);
            if (item != null) {
                result.add(item);
            }
        }
        return result;
    }
}

