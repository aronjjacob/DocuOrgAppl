package com.example.docuorg;

import android.content.ClipData;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import android.graphics.Bitmap;
import android.graphics.pdf.PdfRenderer;
import android.os.ParcelFileDescriptor;
import android.widget.ImageView;
import android.graphics.Canvas;

public class ViewDocumentActivity extends AppCompatActivity {

    public static final String EXTRA_DOCUMENT_ID = "extra_document_id";
    // Optional lightweight preload fields passed from the documents list so the
    // detail screen can render immediately while the local/cloud loads complete.
    public static final String EXTRA_PRELOAD_URI = "extra_preload_uri";
    public static final String EXTRA_PRELOAD_TITLE = "extra_preload_title";
    public static final String EXTRA_PRELOAD_CATEGORY = "extra_preload_category";
    public static final String EXTRA_PRELOAD_DATE = "extra_preload_date";
    public static final String EXTRA_PRELOAD_DATE_MILLIS = "extra_preload_date_millis";

    private static final String FIRESTORE_USERS_COLLECTION = "users";
    private static final String FIRESTORE_DOCUMENTS_COLLECTION = "documents";

    private FirebaseAuth auth;
    private FirebaseFirestore firestore;

    private long documentId = -1;
    private JSONObject currentDoc;

    private TextView tagView;
    private TextView titleView;
    private TextView categoryValue;
    private TextView dateValue;
    private LinearLayout tagsContainer;
    private ImageView previewImage;

    private ActivityResultLauncher<Intent> editLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_view_document);
        BottomNavHelper.setupBottomNav(this, R.id.nav_documents_item);

        View profileButton = findViewById(R.id.view_document_profile);
        if (profileButton != null) {
            profileButton.setOnClickListener(v -> {
                Intent intent = new Intent(this, ProfileInfoActivity.class);
                startActivity(intent);
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
            });
        }

        auth = FirebaseAuth.getInstance();
        firestore = FirebaseFirestore.getInstance();

        tagView = findViewById(R.id.document_tag);
        titleView = findViewById(R.id.document_title);
        categoryValue = findViewById(R.id.document_category_value);
        dateValue = findViewById(R.id.document_date_value);
        tagsContainer = findViewById(R.id.document_tags_container);
        previewImage = findViewById(R.id.document_preview_image);
        if (previewImage != null) {
            previewImage.setOnClickListener(v -> {
                Uri uri = getDocumentUri();
                String mime = getDocumentMime();
                if (uri == null) {
                    return;
                }
                if (mime != null && mime.startsWith("image/")) {
                    Intent full = new Intent(this, FullscreenPreviewActivity.class);
                    full.putExtra(FullscreenPreviewActivity.EXTRA_URI, uri.toString());
                    startActivity(full);
                } else {
                    // For PDFs and others open external viewer
                    Intent view = new Intent(Intent.ACTION_VIEW);
                    if (mime != null) view.setDataAndType(uri, mime);
                    else view.setData(uri);
                    view.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    try {
                        startActivity(view);
                    } catch (Exception ignored) {
                    }
                }
            });
        }

        ImageView back = findViewById(R.id.view_document_back);
        if (back != null) {
            back.setOnClickListener(v -> finish());
        }

        MaterialButton editButton = findViewById(R.id.edit_document_button);
        if (editButton != null) {
            editButton.setOnClickListener(v -> onEdit());
        }

        MaterialButton shareButton = findViewById(R.id.share_button);
        if (shareButton != null) {
            shareButton.setOnClickListener(v -> onShare());
        }

        MaterialButton deleteButton = findViewById(R.id.delete_button);
        if (deleteButton != null) {
            deleteButton.setOnClickListener(v -> confirmDelete());
        }

        View expand = findViewById(R.id.document_expand);
        if (expand != null) {
            expand.setOnClickListener(v -> onOpen());
        }

        editLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> loadDocument()
        );

        documentId = getIntent().getLongExtra(EXTRA_DOCUMENT_ID, -1);
        if (documentId <= 0) {
            Toast.makeText(this, R.string.error_unable_to_load, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // If the caller passed lightweight preload data, bind it immediately so the UI
        // doesn't appear empty while we load the full document from local/cache/cloud.
        try {
            String preloadUri = getIntent().getStringExtra(EXTRA_PRELOAD_URI);
            String preloadTitle = getIntent().getStringExtra(EXTRA_PRELOAD_TITLE);
            String preloadCategory = getIntent().getStringExtra(EXTRA_PRELOAD_CATEGORY);
            String preloadDate = getIntent().getStringExtra(EXTRA_PRELOAD_DATE);
            long preloadDateMillis = getIntent().getLongExtra(EXTRA_PRELOAD_DATE_MILLIS, -1);
            if ((preloadUri != null && !preloadUri.isEmpty()) ||
                    (preloadTitle != null && !preloadTitle.isEmpty()) ||
                    (preloadCategory != null && !preloadCategory.isEmpty()) ||
                    (preloadDate != null && !preloadDate.isEmpty()) || preloadDateMillis > 0) {
                org.json.JSONObject preload = new org.json.JSONObject();
                preload.put("id", documentId);
                if (preloadUri != null) preload.put("uri", preloadUri);
                if (preloadTitle != null) preload.put("title", preloadTitle);
                if (preloadCategory != null) preload.put("category", preloadCategory);
                if (preloadDate != null) preload.put("dateText", preloadDate);
                if (preloadDateMillis > 0) preload.put("dateMillis", preloadDateMillis);
                bind(preload);
            }
        } catch (Exception ignored) {
        }

        loadDocument();

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.view_document_root), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
    }

    private void loadDocument() {
        FirebaseUser user = auth != null ? auth.getCurrentUser() : null;
        if (user == null) {
            if (currentDoc == null) {
                Toast.makeText(this, R.string.error_unable_to_load, Toast.LENGTH_SHORT).show();
                finish();
            }
            return;
        }

        firestore.collection(FIRESTORE_USERS_COLLECTION)
                .document(user.getUid())
                .collection(FIRESTORE_DOCUMENTS_COLLECTION)
                .document(String.valueOf(documentId))
                .get()
                .addOnSuccessListener(this, snapshot -> {
                    if (snapshot != null && snapshot.exists()) {
                        android.util.Log.i("ViewDocumentActivity", "✓ Document loaded from Firestore: ID=" + documentId);
                        JSONObject cloud = snapshotToJson(snapshot);
                        if (cloud != null) {
                            bind(cloud);
                        }
                    } else if (currentDoc == null) {
                        Toast.makeText(this, R.string.error_unable_to_load, Toast.LENGTH_SHORT).show();
                        finish();
                    }
                })
                .addOnFailureListener(this, e -> {
                    android.util.Log.e("ViewDocumentActivity", "✗ Firestore load FAILED: ID=" + documentId, e);
                    if (currentDoc == null) {
                        Toast.makeText(this, R.string.error_unable_to_load, Toast.LENGTH_SHORT).show();
                        finish();
                    }
                });
    }

    private JSONObject snapshotToJson(DocumentSnapshot snapshot) {
        try {
            JSONObject obj = new JSONObject();

            long id = documentId;
            Object rawId = snapshot.get("id");
            if (rawId instanceof Number) {
                id = ((Number) rawId).longValue();
            }
            obj.put("id", id);
            obj.put("uri", safeString(snapshot.getString("uri")));
            obj.put("displayName", safeString(snapshot.getString("displayName")));
            obj.put("mime", safeString(snapshot.getString("mime")));
            obj.put("title", safeString(snapshot.getString("title")));
            obj.put("category", safeString(snapshot.getString("category")));
            obj.put("dateText", safeString(snapshot.getString("dateText")));

            Object rawMillis = snapshot.get("dateMillis");
            if (rawMillis instanceof Number) {
                obj.put("dateMillis", ((Number) rawMillis).longValue());
            }

            Object rawModifiedMillis = snapshot.get("modifiedDateMillis");
            if (rawModifiedMillis instanceof Number) {
                obj.put("modifiedDateMillis", ((Number) rawModifiedMillis).longValue());
            }

            obj.put("notes", safeString(snapshot.getString("notes")));

            JSONArray tags = new JSONArray();
            Object rawTags = snapshot.get("tags");
            if (rawTags instanceof List<?>) {
                for (Object t : (List<?>) rawTags) {
                    if (t != null) {
                        tags.put(String.valueOf(t));
                    }
                }
            }
            obj.put("tags", tags);

            String amount = snapshot.getString("amount");
            if (amount != null) {
                obj.put("amount", amount);
            }
            String storeName = snapshot.getString("storeName");
            if (storeName != null) {
                obj.put("storeName", storeName);
            }

            return obj;
        } catch (Exception ignored) {
            return null;
        }
    }

    private String safeString(String value) {
        return value != null ? value : "";
    }

    private void bind(JSONObject doc) {
        currentDoc = doc;

        String title = doc.optString("title", "");
        String displayName = doc.optString("displayName", "");
        if (title == null || title.trim().isEmpty()) {
            title = (displayName != null && !displayName.trim().isEmpty()) ? displayName : "Untitled";
        }
        String category = doc.optString("category", "");

        String dateText = doc.optString("dateText", "");
        if ((dateText == null || dateText.trim().isEmpty()) && doc.has("dateMillis")) {
            long millis = doc.optLong("dateMillis", -1);
            if (millis > 0) {
                dateText = new SimpleDateFormat("MM/dd/yyyy", Locale.getDefault())
                        .format(new Date(millis));
            }
        }

        if (tagView != null) {
            tagView.setText(category != null && !category.trim().isEmpty() ? category : getString(R.string.document_tag));
        }
        if (titleView != null) {
            titleView.setText(title);
        }
        if (categoryValue != null) {
            categoryValue.setText(category != null ? category : "");
        }
        if (dateValue != null) {
            dateValue.setText(dateText != null ? dateText : "");
        }

        renderTags(doc.optJSONArray("tags"));

        // Update the visual preview (image thumbnail, pdf first page, or icon)
        updatePreview();
    }

    private void updatePreview() {
        if (previewImage == null) return;
        previewImage.setImageDrawable(null);

        Uri uri = getDocumentUri();
        if (uri == null) {
            // fallback: show category-based icon
            previewImage.setImageResource(pickIconRes(currentDoc != null ? currentDoc.optString("category", null) : null));
            return;
        }

        try {
            String mime = getDocumentMime();
            if (mime != null && mime.startsWith("image/")) {
                // Let the ImageView load the image via URI (content/file provider URIs supported)
                previewImage.setImageURI(uri);
                return;
            }

            // Attempt PDF thumbnail for PDFs (first page)
            boolean isPdf = (mime != null && mime.equals("application/pdf")) || uri.toString().toLowerCase(Locale.ROOT).endsWith(".pdf");
            if (isPdf) {
                ParcelFileDescriptor pfd = null;
                PdfRenderer renderer = null;
                try {
                    pfd = getContentResolver().openFileDescriptor(uri, "r");
                    if (pfd != null) {
                        renderer = new PdfRenderer(pfd);
                        if (renderer.getPageCount() > 0) {
                            PdfRenderer.Page page = renderer.openPage(0);
                            int width = previewImage.getWidth();
                            int height = previewImage.getHeight();
                            if (width <= 0) width = 400;
                            if (height <= 0) height = 300;
                            Bitmap bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
                            Canvas c = new Canvas(bmp);
                            c.drawColor(0xFFFFFFFF);
                            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
                            page.close();
                            previewImage.setImageBitmap(bmp);
                            return;
                        }
                    }
                } catch (Exception e) {
                    android.util.Log.w("ViewDocumentActivity", "PDF render failed", e);
                } finally {
                    if (renderer != null) renderer.close();
                    if (pfd != null) try { pfd.close(); } catch (Exception ignored) {}
                }
            }

            // Fallback for other types: show category icon
            previewImage.setImageResource(pickIconRes(currentDoc != null ? currentDoc.optString("category", null) : null));
        } catch (Exception e) {
            android.util.Log.w("ViewDocumentActivity", "updatePreview failed", e);
            previewImage.setImageResource(pickIconRes(currentDoc != null ? currentDoc.optString("category", null) : null));
        }
    }

    private int pickIconRes(String category) {
        if (category == null) {
            return R.drawable.ic_doc_receipt;
        }
        String c = category.trim().toLowerCase(Locale.US);
        if (c.contains("receipt")) return R.drawable.ic_doc_receipt;
        if (c.contains("medical")) return R.drawable.ic_doc_medical;
        if (c.contains("tax")) return R.drawable.ic_doc_tax;
        if (c.contains("personal")) return R.drawable.ic_doc_personal;
        return R.drawable.ic_doc_receipt;
    }
    private void renderTags(JSONArray tags) {
        if (tagsContainer == null) {
            return;
        }
        tagsContainer.removeAllViews();
        if (tags == null || tags.length() == 0) {
            return;
        }
        for (int i = 0; i < tags.length(); i++) {
            String tag = tags.optString(i, "");
            if (tag == null || tag.trim().isEmpty()) {
                continue;
            }
            tagsContainer.addView(createTagPill(tag.trim(), i > 0));
        }
    }

    private View createTagPill(String text, boolean addStartMargin) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(10);
        tv.setTextColor(getResources().getColor(R.color.primary_teal, getTheme()));
        tv.setBackgroundResource(R.drawable.bg_tag_pill);
        int padH = dp(8);
        int padV = dp(2);
        tv.setPadding(padH, padV, padH, padV);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        if (addStartMargin) {
            lp.setMarginStart(dp(8));
        }
        tv.setLayoutParams(lp);
        return tv;
    }

    private int dp(int dp) {
        float density = getResources().getDisplayMetrics().density;
        return (int) (dp * density);
    }

    private Uri getDocumentUri() {
        if (currentDoc == null) {
            return null;
        }
        String raw = currentDoc.optString("uri", null);
        if (raw == null || raw.trim().isEmpty()) {
            return null;
        }
        try {
            return Uri.parse(raw);
        } catch (Exception ignored) {
            return null;
        }
    }

    private String getDocumentMime() {
        if (currentDoc == null) {
            return null;
        }
        String mime = currentDoc.optString("mime", null);
        return mime != null && !mime.trim().isEmpty() ? mime : null;
    }

    private void onOpen() {
        Uri uri = getDocumentUri();
        if (uri == null) {
            Toast.makeText(this, R.string.error_unable_to_load, Toast.LENGTH_SHORT).show();
            return;
        }

        String mime = getDocumentMime();
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(uri, mime != null ? mime : "*/*");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

        try {
            startActivity(intent);
        } catch (Exception e) {
            // Fallback: try without specifying type.
            try {
                Intent fallback = new Intent(Intent.ACTION_VIEW);
                fallback.setData(uri);
                fallback.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                startActivity(fallback);
            } catch (Exception ignored) {
                Toast.makeText(this, R.string.error_unable_to_load, Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void onShare() {
        Uri uri = getDocumentUri();
        if (uri == null) {
            Toast.makeText(this, R.string.error_unable_to_load, Toast.LENGTH_SHORT).show();
            return;
        }

        String mime = getDocumentMime();
        Intent share = new Intent(Intent.ACTION_SEND);
        share.setType(mime != null ? mime : "*/*");
        share.putExtra(Intent.EXTRA_STREAM, uri);
        share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        share.setClipData(ClipData.newUri(getContentResolver(), "document", uri));

        try {
            startActivity(Intent.createChooser(share, getString(R.string.share)));
        } catch (Exception ignored) {
            Toast.makeText(this, R.string.error_unable_to_load, Toast.LENGTH_SHORT).show();
        }
    }

    private void onEdit() {
        if (currentDoc == null) {
            Toast.makeText(this, R.string.error_unable_to_load, Toast.LENGTH_SHORT).show();
            return;
        }

        Intent intent = new Intent(this, AddDocumentActivity.class);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.putExtra(AddDocumentActivity.EXTRA_DOCUMENT_ID, documentId);

        String uri = currentDoc.optString("uri", null);
        if (uri != null && !uri.trim().isEmpty()) {
            intent.putExtra(AddDocumentActivity.EXTRA_IMAGE_URI, uri);
        }

        intent.putExtra(AddDocumentActivity.EXTRA_TITLE, currentDoc.optString("title", ""));
        intent.putExtra(AddDocumentActivity.EXTRA_CATEGORY, currentDoc.optString("category", ""));
        intent.putExtra(AddDocumentActivity.EXTRA_DATE, currentDoc.optString("dateText", ""));
        if (currentDoc.has("dateMillis")) {
            long millis = currentDoc.optLong("dateMillis", -1);
            if (millis > 0) {
                intent.putExtra(AddDocumentActivity.EXTRA_DATE_MILLIS, millis);
            }
        }
        intent.putExtra(AddDocumentActivity.EXTRA_NOTES, currentDoc.optString("notes", ""));

        JSONArray tags = currentDoc.optJSONArray("tags");
        if (tags != null && tags.length() > 0) {
            String[] tagArray = new String[tags.length()];
            for (int i = 0; i < tags.length(); i++) {
                tagArray[i] = tags.optString(i, "");
            }
            intent.putExtra(AddDocumentActivity.EXTRA_TAGS, tagArray);
        }

        if (currentDoc.has("amount")) {
            intent.putExtra(AddDocumentActivity.EXTRA_AMOUNT, currentDoc.optString("amount", ""));
        }
        if (currentDoc.has("storeName")) {
            intent.putExtra(AddDocumentActivity.EXTRA_STORE_NAME, currentDoc.optString("storeName", ""));
        }

        editLauncher.launch(intent);
    }

    private void confirmDelete() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.delete)
                .setMessage(R.string.confirm_delete_document)
                .setNegativeButton(R.string.cancel, (d, w) -> d.dismiss())
                .setPositiveButton(R.string.delete, (d, w) -> deleteNow())
                .show();
    }

    private void deleteNow() {
        FirebaseUser user = auth != null ? auth.getCurrentUser() : null;
        if (user == null) {
            Toast.makeText(this, R.string.error_unable_to_delete, Toast.LENGTH_SHORT).show();
            return;
        }

        firestore.collection(FIRESTORE_USERS_COLLECTION)
                .document(user.getUid())
                .collection(FIRESTORE_DOCUMENTS_COLLECTION)
                .document(String.valueOf(documentId))
                .delete()
                .addOnSuccessListener(this, unused -> {
                    android.util.Log.i("ViewDocumentActivity", "✓ Document deleted from Firestore: ID=" + documentId);
                    Toast.makeText(this, R.string.document_deleted, Toast.LENGTH_SHORT).show();
                    setResult(RESULT_OK);
                    finish();
                })
                .addOnFailureListener(this, e -> {
                    android.util.Log.e("ViewDocumentActivity", "✗ Firestore delete FAILED: ID=" + documentId, e);
                    Toast.makeText(this, R.string.error_unable_to_delete, Toast.LENGTH_SHORT).show();
                });
    }
}
