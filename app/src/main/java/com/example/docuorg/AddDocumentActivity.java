package com.example.docuorg;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.OpenableColumns;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class AddDocumentActivity extends AppCompatActivity {

    public static final String EXTRA_IMAGE_URI = "extra_image_uri";
    public static final String EXTRA_TITLE = "extra_title";
    public static final String EXTRA_CATEGORY = "extra_category";
    public static final String EXTRA_DATE = "extra_date";
    public static final String EXTRA_AMOUNT = "extra_amount";
    public static final String EXTRA_STORE_NAME = "extra_store_name";
    public static final String EXTRA_NOTES = "extra_notes";
    public static final String EXTRA_TAGS = "extra_tags";

    private static final String STATE_SELECTED_URI = "selected_uri";
    private static final String STATE_CAMERA_PENDING_URI = "camera_pending_uri";
    private static final String STATE_CAMERA_PENDING_NAME = "camera_pending_name";
    private static final String STATE_SELECTED_DATE_MILLIS = "selected_date_millis";

    private static final String PREFS_NAME = "docuorg_prefs";
    private static final String PREFS_DOCUMENTS_JSON = "documents_json";
    private static final long MAX_FILE_BYTES = 10L * 1024L * 1024L;
    private static final String FIRESTORE_USERS_COLLECTION = "users";
    private static final String FIRESTORE_DOCUMENTS_COLLECTION = "documents";

    private ActivityResultLauncher<String[]> openDocumentLauncher;
    private ActivityResultLauncher<Uri> takePictureLauncher;

    private Uri selectedDocumentUri;
    private TextView selectedFileLabel;
    private ImageView selectedFilePreview;

    private Uri pendingCameraUri;
    private String pendingCameraDisplayName;

    private TextInputEditText titleInput;
    private AutoCompleteTextView categoryInput;
    private TextInputLayout dateInputLayout;
    private TextInputEditText dateInput;
    private TextInputEditText notesInput;
    private TextInputLayout titleInputLayout;
    private TextInputLayout categoryInputLayout;

    private MaterialCardView receiptDetailsCard;
    private TextInputEditText amountInput;
    private TextInputEditText storeNameInput;

    private ChipGroup tagsGroup;
    private Chip addTagChip;

    private View saveButton;

    private Long selectedDateMillis;
    private FirebaseFirestore firestore;
    private FirebaseAuth auth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_add_document);
        firestore = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();

        // Initialize views
        selectedFileLabel = findViewById(R.id.selected_file_label);
        selectedFilePreview = findViewById(R.id.selected_file_preview);

        titleInputLayout = findViewById(R.id.title_input_layout);
        titleInput = findViewById(R.id.title_input);
        categoryInputLayout = findViewById(R.id.category_input_layout);
        categoryInput = findViewById(R.id.category_input);
        dateInputLayout = findViewById(R.id.date_input_layout);
        dateInput = findViewById(R.id.date_input);
        notesInput = findViewById(R.id.notes_input);

        receiptDetailsCard = findViewById(R.id.receipt_details_card);
        amountInput = findViewById(R.id.amount_input);
        storeNameInput = findViewById(R.id.store_name_input);

        tagsGroup = findViewById(R.id.tags_group);
        addTagChip = findViewById(R.id.tag_add);

        // Register activity result launchers
        openDocumentLauncher = registerForActivityResult(
                new ActivityResultContracts.OpenDocument(),
                this::onDocumentPicked
        );

        takePictureLauncher = registerForActivityResult(
                new ActivityResultContracts.TakePicture(),
                success -> {
                    if (success != null && success) {
                        onCameraImageCaptured();
                    } else {
                        pendingCameraUri = null;
                        pendingCameraDisplayName = null;
                    }
                }
        );

        // Set up click listeners
        findViewById(R.id.add_document_back).setOnClickListener(view -> finish());
        findViewById(R.id.cancel_button).setOnClickListener(view -> finish());
        saveButton = findViewById(R.id.save_document_button);
        if (saveButton != null) {
            saveButton.setOnClickListener(view -> saveDocument());
        }

        View uploadArea = findViewById(R.id.upload_area);
        if (uploadArea != null) {
            uploadArea.setOnClickListener(v -> launchGalleryPicker());
        }

        findViewById(R.id.gallery_button).setOnClickListener(view -> launchGalleryPicker());
        findViewById(R.id.camera_button).setOnClickListener(view -> launchCameraCapture());

        // Apply extras from AI scan if available
        applyScanExtras(getIntent());

        // Set up UI components
        setupCategoryDropdown();
        setupDatePicker();
        setupTagsUi();

        // Restore state if available
        if (savedInstanceState != null) {
            String restoredUri = savedInstanceState.getString(STATE_SELECTED_URI);
            if (restoredUri != null) {
                onDocumentPicked(Uri.parse(restoredUri));
            }

            String cameraPendingUri = savedInstanceState.getString(STATE_CAMERA_PENDING_URI);
            if (cameraPendingUri != null) {
                pendingCameraUri = Uri.parse(cameraPendingUri);
            }
            pendingCameraDisplayName = savedInstanceState.getString(STATE_CAMERA_PENDING_NAME);

            if (savedInstanceState.containsKey(STATE_SELECTED_DATE_MILLIS)) {
                long restoredDate = savedInstanceState.getLong(STATE_SELECTED_DATE_MILLIS, -1);
                if (restoredDate > 0) {
                    selectedDateMillis = restoredDate;
                    if (dateInput != null) {
                        dateInput.setText(formatDate(restoredDate));
                    }
                }
            }
        }

        // Receipt details are optional
        if (receiptDetailsCard != null) {
            receiptDetailsCard.setVisibility(View.GONE);
        }

        // Apply window insets
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.add_document_root), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (selectedDocumentUri != null) {
            outState.putString(STATE_SELECTED_URI, selectedDocumentUri.toString());
        }
        if (pendingCameraUri != null) {
            outState.putString(STATE_CAMERA_PENDING_URI, pendingCameraUri.toString());
        }
        if (pendingCameraDisplayName != null) {
            outState.putString(STATE_CAMERA_PENDING_NAME, pendingCameraDisplayName);
        }
        if (selectedDateMillis != null) {
            outState.putLong(STATE_SELECTED_DATE_MILLIS, selectedDateMillis);
        }
    }

    private void setupCategoryDropdown() {
        if (categoryInput == null) {
            return;
        }

        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(
                this,
                R.array.document_categories,
                android.R.layout.simple_list_item_1
        );
        categoryInput.setAdapter(adapter);

        categoryInput.setOnItemClickListener((parent, view, position, id) -> {
            String selected = String.valueOf(parent.getItemAtPosition(position));
            updateReceiptDetailsVisibility(selected);
        });

        categoryInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                updateReceiptDetailsVisibility(s != null ? s.toString() : null);
            }
        });

        categoryInput.setOnClickListener(v -> categoryInput.showDropDown());
    }

    private void updateReceiptDetailsVisibility(String category) {
        if (receiptDetailsCard == null) {
            return;
        }

        boolean show = !TextUtils.isEmpty(category)
                && category.equalsIgnoreCase(getString(R.string.doc_type_receipt));
        receiptDetailsCard.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    private void setupDatePicker() {
        if (dateInput == null) {
            return;
        }

        // Prevent keyboard from showing; date is selected via picker.
        dateInput.setKeyListener(null);

        MaterialDatePicker<Long> picker = MaterialDatePicker.Builder.datePicker()
                .setTitleText(R.string.date_label)
                .build();

        picker.addOnPositiveButtonClickListener(selection -> {
            selectedDateMillis = selection;
            dateInput.setText(formatDate(selection));
        });

        View.OnClickListener openPicker = v -> {
            if (!picker.isAdded()) {
                picker.show(getSupportFragmentManager(), "date_picker");
            }
        };

        dateInput.setOnClickListener(openPicker);
        if (dateInputLayout != null) {
            dateInputLayout.setEndIconOnClickListener(openPicker);
        }
    }

    private String formatDate(long utcMillis) {
        // MaterialDatePicker returns UTC millis; format in the user's locale/timezone.
        SimpleDateFormat formatter = new SimpleDateFormat("MM/dd/yyyy", Locale.getDefault());
        return formatter.format(new Date(utcMillis));
    }

    private void setupTagsUi() {
        if (tagsGroup == null) {
            return;
        }

        // Existing chips with close icons should remove themselves.
        for (int i = 0; i < tagsGroup.getChildCount(); i++) {
            View child = tagsGroup.getChildAt(i);
            if (child instanceof Chip) {
                Chip chip = (Chip) child;
                if (chip.getId() != R.id.tag_add && chip.isCloseIconVisible()) {
                    chip.setOnCloseIconClickListener(v -> tagsGroup.removeView(chip));
                }
            }
        }

        if (addTagChip != null) {
            addTagChip.setOnClickListener(v -> showAddTagDialog());
        }
    }

    private void showAddTagDialog() {
        EditText input = new EditText(this);
        input.setHint(R.string.add_tag_dialog_hint);

        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.add_tag_dialog_title)
                .setView(input)
                .setNegativeButton(R.string.cancel, (dialog, which) -> dialog.dismiss())
                .setPositiveButton(R.string.action_add, (dialog, which) -> {
                    String tag = input.getText() != null ? input.getText().toString().trim() : "";
                    if (TextUtils.isEmpty(tag)) {
                        Toast.makeText(this, R.string.error_tag_empty, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    addTagChip(tag);
                })
                .show();
    }

    private void addTagChip(String tagText) {
        if (tagsGroup == null) {
            return;
        }

        Chip chip = new Chip(this);
        chip.setText(tagText);
        chip.setCloseIconVisible(true);
        chip.setOnCloseIconClickListener(v -> tagsGroup.removeView(chip));
        // Mirror the styling of existing chips.
        chip.setTextColor(ContextCompat.getColor(this, R.color.primary_teal));
        chip.setChipBackgroundColorResource(R.color.chip_bg);
        chip.setCloseIconTintResource(R.color.primary_teal);

        int insertIndex = addTagChip != null ? tagsGroup.indexOfChild(addTagChip) : -1;
        if (insertIndex >= 0) {
            tagsGroup.addView(chip, insertIndex);
        } else {
            tagsGroup.addView(chip);
        }
    }

    private List<String> collectTags() {
        List<String> tags = new ArrayList<>();
        if (tagsGroup == null) {
            return tags;
        }
        for (int i = 0; i < tagsGroup.getChildCount(); i++) {
            View child = tagsGroup.getChildAt(i);
            if (child instanceof Chip) {
                Chip chip = (Chip) child;
                if (chip.getId() == R.id.tag_add) {
                    continue;
                }
                CharSequence text = chip.getText();
                if (!TextUtils.isEmpty(text)) {
                    tags.add(text.toString());
                }
            }
        }
        return tags;
    }

    private void launchGalleryPicker() {
        // Storage Access Framework picker; no runtime storage permission needed.
        openDocumentLauncher.launch(new String[]{"image/*", "application/pdf"});
    }

    private void launchCameraCapture() {
        try {
            File photoFile = createCameraImageFile();
            pendingCameraDisplayName = photoFile.getName();
            pendingCameraUri = FileProvider.getUriForFile(
                    this,
                    getPackageName() + ".fileprovider",
                    photoFile
            );
            takePictureLauncher.launch(pendingCameraUri);
        } catch (IOException e) {
            Toast.makeText(this, "Unable to create image file", Toast.LENGTH_SHORT).show();
        } catch (IllegalArgumentException e) {
            Toast.makeText(this, "Camera provider not configured", Toast.LENGTH_SHORT).show();
        }
    }

    private File createCameraImageFile() throws IOException {
        File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        if (storageDir == null) {
            storageDir = getCacheDir();
        }
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        return File.createTempFile("DOCUORG_" + timestamp + "_", ".jpg", storageDir);
    }

    private void onCameraImageCaptured() {
        if (pendingCameraUri == null) {
            return;
        }

        selectedDocumentUri = pendingCameraUri;
        updateSelectedFileUi(selectedDocumentUri, pendingCameraDisplayName);

        pendingCameraUri = null;
        pendingCameraDisplayName = null;
    }

    private void onDocumentPicked(Uri uri) {
        if (uri == null) {
            return;
        }

        Long size = getFileSizeBytes(uri);
        if (size != null && size > MAX_FILE_BYTES) {
            Toast.makeText(this, R.string.error_file_too_large, Toast.LENGTH_SHORT).show();
            return;
        }

        selectedDocumentUri = uri;

        try {
            getContentResolver().takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
            );
        } catch (SecurityException ignored) {
            // Some providers might not allow persistable permissions; still usable for this session.
        }

        updateSelectedFileUi(uri, null);
    }

    private Long getFileSizeBytes(Uri uri) {
        try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE);
                if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) {
                    return cursor.getLong(sizeIndex);
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private void saveDocument() {
        try {
            clearValidationErrors();

            if (saveButton != null) {
                saveButton.setEnabled(false);
            }

            boolean hasError = false;
            if (selectedDocumentUri == null) {
                Toast.makeText(this, R.string.error_missing_required, Toast.LENGTH_SHORT).show();
                android.util.Log.w("AddDocumentActivity", "saveDocument blocked: selectedDocumentUri is null");
                hasError = true;
            }
            if (TextUtils.isEmpty(titleInput.getText().toString())) {
                setInputError(titleInputLayout, getString(R.string.error_missing_required));
                hasError = true;
            }
            if (TextUtils.isEmpty(categoryInput.getText().toString())) {
                setInputError(categoryInputLayout, getString(R.string.error_missing_required));
                hasError = true;
            }
            if (TextUtils.isEmpty(dateInput.getText().toString())) {
                setInputError(dateInputLayout, getString(R.string.error_missing_required));
                hasError = true;
            }
            if (hasError) {
                Toast.makeText(this, R.string.error_missing_required, Toast.LENGTH_SHORT).show();
                if (saveButton != null) {
                    saveButton.setEnabled(true);
                }
                return;
            }

            updateReceiptDetailsVisibility(categoryInput.getText().toString());

            String notes = getInputText(notesInput);
            String amount = getInputText(amountInput);
            String storeName = getInputText(storeNameInput);

            long documentId = System.currentTimeMillis();
            List<String> tags = collectTags();

            Map<String, Object> docData = new HashMap<>();
            docData.put("id", documentId);
            docData.put("uri", selectedDocumentUri.toString());
            docData.put("displayName", getDisplayName(selectedDocumentUri));
            try {
                docData.put("mime", getContentResolver().getType(selectedDocumentUri));
            } catch (SecurityException ignored) {
                docData.put("mime", "");
            }
            docData.put("title", titleInput.getText().toString());
            docData.put("category", categoryInput.getText().toString());
            docData.put("dateText", dateInput.getText().toString());
            if (selectedDateMillis != null) {
                docData.put("dateMillis", selectedDateMillis);
            }
            docData.put("notes", notes);
            docData.put("tags", tags);

            if (categoryInput.getText().toString().equalsIgnoreCase(getString(R.string.doc_type_receipt))) {
                docData.put("amount", amount);
                docData.put("storeName", storeName);
            }

            // Always save locally first so the UI can return immediately.
            // Cloud sync is best-effort and should not block navigation.
            boolean localSaved = saveDocumentLocally(documentId, docData);
            if (!localSaved) {
                Toast.makeText(this, getString(R.string.error_unable_to_save), Toast.LENGTH_SHORT).show();
                if (saveButton != null) {
                    saveButton.setEnabled(true);
                }
                return;
            }

            FirebaseUser currentUser = auth.getCurrentUser();
            if (currentUser == null) {
                Toast.makeText(
                        this,
                        "Saved locally. Sign in to sync to Firebase.",
                        Toast.LENGTH_SHORT
                ).show();
                setResult(RESULT_OK);
                finish();
                return;
            }

            String userId = currentUser.getUid();
            // Don't block navigation on cloud sync.
            firestore.collection(FIRESTORE_USERS_COLLECTION)
                    .document(userId)
                    .collection(FIRESTORE_DOCUMENTS_COLLECTION)
                    .document(String.valueOf(documentId))
                    .set(docData, SetOptions.merge())
                    .addOnFailureListener(this, e -> android.util.Log.e("AddDocumentActivity", "Cloud save failed", e));

            Toast.makeText(this, R.string.document_saved, Toast.LENGTH_SHORT).show();
            setResult(RESULT_OK);
            finish();
        } catch (Exception e) {
            android.util.Log.e("AddDocumentActivity", "Save failed", e);
            Toast.makeText(this, getString(R.string.error_unable_to_save), Toast.LENGTH_SHORT).show();
            if (saveButton != null) {
                saveButton.setEnabled(true);
            }
        }
    }

    private boolean saveDocumentLocally(long documentId, Map<String, Object> docData) {
        try {
            JSONObject doc = new JSONObject();
            doc.put("id", documentId);
            doc.put("uri", valueAsString(docData.get("uri")));
            doc.put("displayName", valueAsString(docData.get("displayName")));
            doc.put("mime", valueAsString(docData.get("mime")));
            doc.put("title", valueAsString(docData.get("title")));
            doc.put("category", valueAsString(docData.get("category")));
            doc.put("dateText", valueAsString(docData.get("dateText")));

            Object dateMillis = docData.get("dateMillis");
            if (dateMillis instanceof Long) {
                doc.put("dateMillis", (Long) dateMillis);
            }

            doc.put("notes", valueAsString(docData.get("notes")));

            JSONArray tagsArray = new JSONArray();
            Object tagsValue = docData.get("tags");
            if (tagsValue instanceof List<?>) {
                for (Object tag : (List<?>) tagsValue) {
                    if (tag != null) {
                        tagsArray.put(String.valueOf(tag));
                    }
                }
            }
            doc.put("tags", tagsArray);

            Object amount = docData.get("amount");
            if (amount != null) {
                doc.put("amount", String.valueOf(amount));
            }
            Object storeName = docData.get("storeName");
            if (storeName != null) {
                doc.put("storeName", String.valueOf(storeName));
            }

            SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            String raw = prefs.getString(PREFS_DOCUMENTS_JSON, "[]");
            JSONArray docs;
            try {
                docs = new JSONArray(raw);
            } catch (JSONException ignored) {
                // If the stored value was corrupted, reset to a new list.
                docs = new JSONArray();
            }
            docs.put(doc);
            // Prefer apply() (async) to avoid rare commit() failures blocking navigation.
            prefs.edit().putString(PREFS_DOCUMENTS_JSON, docs.toString()).apply();
            return true;
        } catch (JSONException e) {
            return false;
        }
    }

    private String getInputText(TextInputEditText input) {
        if (input == null || input.getText() == null) {
            return "";
        }
        return input.getText().toString().trim();
    }

    private String valueAsString(Object value) {
        return value != null ? String.valueOf(value) : "";
    }

    private void updateSelectedFileUi(Uri uri, String displayNameOverride) {
        if (selectedFileLabel != null) {
            String name = displayNameOverride != null ? displayNameOverride : getDisplayName(uri);
            selectedFileLabel.setText(name != null ? name : uri.toString());
        }

        if (selectedFilePreview != null) {
            String mime = getContentResolver().getType(uri);
            boolean isImage = mime != null && mime.startsWith("image/");
            if (isImage) {
                selectedFilePreview.setVisibility(View.VISIBLE);
                selectedFilePreview.setImageURI(uri);
            } else {
                selectedFilePreview.setImageURI(null);
                selectedFilePreview.setVisibility(View.GONE);
            }
        }
    }

    private String getDisplayName(Uri uri) {
        try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (nameIndex >= 0) {
                    return cursor.getString(nameIndex);
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private void applyScanExtras(Intent intent) {
        if (intent == null) {
            return;
        }
        try {
            String imageUri = intent.getStringExtra(EXTRA_IMAGE_URI);
            if (imageUri != null) {
                onDocumentPicked(Uri.parse(imageUri));
            }

            String title = intent.getStringExtra(EXTRA_TITLE);
            if (title != null && !title.isEmpty()) {
                setTextIfPresent(R.id.title_input, title);
            }

            String category = intent.getStringExtra(EXTRA_CATEGORY);
            if (category != null && !category.isEmpty()) {
                setTextIfPresent(R.id.category_input, category);
            }

            String date = intent.getStringExtra(EXTRA_DATE);
            if (date != null && !date.isEmpty()) {
                setTextIfPresent(R.id.date_input, date);
            }

            String amount = intent.getStringExtra(EXTRA_AMOUNT);
            if (amount != null && amountInput != null) {
                amountInput.setText(amount.isEmpty() ? "" : amount);
            }

            String storeName = intent.getStringExtra(EXTRA_STORE_NAME);
            if (storeName != null && storeNameInput != null) {
                storeNameInput.setText(storeName.isEmpty() ? "" : storeName);
            }

            String notes = intent.getStringExtra(EXTRA_NOTES);
            if (notes != null && !notes.isEmpty()) {
                setTextIfPresent(R.id.notes_input, notes);
            }

            String[] tags = intent.getStringArrayExtra(EXTRA_TAGS);
            if (tags != null && tagsGroup != null) {
                // Remove all views except the add tag chip
                tagsGroup.removeAllViews();

                for (String tag : tags) {
                    if (tag == null || tag.trim().isEmpty()) {
                        continue;
                    }
                    addTagChip(tag.trim());
                }

                // Re-add the add tag chip at the end
                if (addTagChip != null) {
                    tagsGroup.addView(addTagChip);
                }
            }
        } catch (Exception e) {
            android.util.Log.e("AddDocumentActivity", "Error applying scan extras", e);
        }
    }

    private void setTextIfPresent(int viewId, String value) {
        try {
            com.google.android.material.textfield.TextInputEditText input = findViewById(viewId);
            if (input != null && value != null && !value.isEmpty()) {
                input.setText(value);
            }
        } catch (Exception e) {
            android.util.Log.w("AddDocumentActivity", "Could not set text for view " + viewId, e);
        }
    }

    private void clearValidationErrors() {
        setInputError(titleInputLayout, null);
        setInputError(categoryInputLayout, null);
        setInputError(dateInputLayout, null);
    }

    private void setInputError(TextInputLayout layout, String error) {
        if (layout == null) {
            return;
        }
        layout.setError(error);
    }
}
