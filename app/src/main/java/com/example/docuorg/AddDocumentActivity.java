package com.example.docuorg;

import android.content.Intent;
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
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;

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
    /** If set, the activity edits an existing document instead of creating a new one. */
    public static final String EXTRA_DOCUMENT_ID = "extra_document_id";
    public static final String EXTRA_TITLE = "extra_title";
    public static final String EXTRA_CATEGORY = "extra_category";
    public static final String EXTRA_DATE = "extra_date";
    /** Optional companion to {@link #EXTRA_DATE} to preserve the original millis value. */
    public static final String EXTRA_DATE_MILLIS = "extra_date_millis";
    public static final String EXTRA_AMOUNT = "extra_amount";
    public static final String EXTRA_STORE_NAME = "extra_store_name";
    public static final String EXTRA_NOTES = "extra_notes";
    public static final String EXTRA_TAGS = "extra_tags";

    private static final String STATE_SELECTED_URI = "selected_uri";
    private static final String STATE_CAMERA_PENDING_URI = "camera_pending_uri";
    private static final String STATE_CAMERA_PENDING_NAME = "camera_pending_name";
    private static final String STATE_SELECTED_DATE_MILLIS = "selected_date_millis";
    private static final String STATE_EDITING_DOCUMENT_ID = "editing_document_id";

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
    private Long editingDocumentId;
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

        // Restore state if available
        if (savedInstanceState != null) {
            if (savedInstanceState.containsKey(STATE_EDITING_DOCUMENT_ID)) {
                long restoredEditId = savedInstanceState.getLong(STATE_EDITING_DOCUMENT_ID, -1);
                if (restoredEditId > 0) {
                    editingDocumentId = restoredEditId;
                }
            }
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

        // Wire up auxiliary UI behaviors
        setupCategoryDropdown();
        setupDatePicker();
        setupTagsUi();

        // If we're editing an existing document but the Intent didn't include
        // full preload extras, try to populate fields from Firestore.
        populateFromFirestoreIfNeeded();
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
        if (editingDocumentId != null) {
            outState.putLong(STATE_EDITING_DOCUMENT_ID, editingDocumentId);
        }
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

            long documentId = editingDocumentId != null ? editingDocumentId : System.currentTimeMillis();
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
            // Always save dateMillis (original creation date). Use user's selection or default to now.
            long dateMillisToSave = selectedDateMillis != null ? selectedDateMillis : System.currentTimeMillis();
            docData.put("dateMillis", dateMillisToSave);
            // Always save modifiedDateMillis (updated every time document is saved, for analytics)
            docData.put("modifiedDateMillis", System.currentTimeMillis());
            docData.put("notes", notes);
            docData.put("tags", tags);

            if (categoryInput.getText().toString().equalsIgnoreCase(getString(R.string.doc_type_receipt))) {
                docData.put("amount", amount);
                docData.put("storeName", storeName);
            }

            FirebaseUser currentUser = auth.getCurrentUser();
            if (currentUser == null) {
                Toast.makeText(this, R.string.firebase_login_required, Toast.LENGTH_SHORT).show();
                if (saveButton != null) {
                    saveButton.setEnabled(true);
                }
                return;
            }

            String userId = currentUser.getUid();
            firestore.collection(FIRESTORE_USERS_COLLECTION)
                    .document(userId)
                    .collection(FIRESTORE_DOCUMENTS_COLLECTION)
                    .document(String.valueOf(documentId))
                    .set(docData, SetOptions.merge())
                    .addOnSuccessListener(this, unused -> {
                        android.util.Log.i("AddDocumentActivity", "✓ Document saved to Firestore: ID=" + documentId + ", User=" + userId);
                        Toast.makeText(this, R.string.document_saved, Toast.LENGTH_SHORT).show();
                        setResult(RESULT_OK);
                        finish();
                    })
                    .addOnFailureListener(this, e -> {
                        android.util.Log.e("AddDocumentActivity", "✗ Firestore save FAILED: ID=" + documentId, e);
                        Toast.makeText(this, R.string.error_unable_to_save, Toast.LENGTH_SHORT).show();
                        if (saveButton != null) {
                            saveButton.setEnabled(true);
                        }
                    });
        } catch (Exception e) {
            android.util.Log.e("AddDocumentActivity", "Save failed", e);
            Toast.makeText(this, getString(R.string.error_unable_to_save), Toast.LENGTH_SHORT).show();
            if (saveButton != null) {
                saveButton.setEnabled(true);
            }
        }
    }

    private String getInputText(TextInputEditText input) {
        if (input == null || input.getText() == null) {
            return "";
        }
        return input.getText().toString().trim();
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
            // Edit mode extras
            long editId = intent.getLongExtra(EXTRA_DOCUMENT_ID, -1);
            if (editId > 0) {
                editingDocumentId = editId;
            }
            long dateMillis = intent.getLongExtra(EXTRA_DATE_MILLIS, -1);
            if (dateMillis > 0) {
                selectedDateMillis = dateMillis;
                if (dateInput != null && (dateInput.getText() == null || dateInput.getText().toString().trim().isEmpty())) {
                    dateInput.setText(formatDate(dateMillis));
                }
            }

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

    /**
     * If we're editing an existing document but didn't receive full preload extras,
     * try to load the document from Firestore and populate the UI fields so the
     * user can edit.
     */
    private void populateFromFirestoreIfNeeded() {
        try {
            if (editingDocumentId == null || editingDocumentId <= 0) {
                return;
            }

            // If key fields are already set (via extras), don't overwrite them.
            boolean hasTitle = titleInput != null && titleInput.getText() != null && titleInput.getText().toString().trim().length() > 0;
            boolean hasCategory = categoryInput != null && categoryInput.getText() != null && categoryInput.getText().toString().trim().length() > 0;
            boolean hasDate = dateInput != null && dateInput.getText() != null && dateInput.getText().toString().trim().length() > 0;
            boolean hasUri = selectedDocumentUri != null;
            if (hasTitle && hasCategory && hasDate && hasUri) {
                return; // nothing to populate
            }

            FirebaseUser currentUser = auth != null ? auth.getCurrentUser() : null;
            if (currentUser == null) {
                return;
            }

            firestore.collection(FIRESTORE_USERS_COLLECTION)
                    .document(currentUser.getUid())
                    .collection(FIRESTORE_DOCUMENTS_COLLECTION)
                    .document(String.valueOf(editingDocumentId))
                    .get()
                    .addOnSuccessListener(this, snapshot -> applyFirestoreSnapshotToEditor(snapshot, hasUri, hasTitle, hasCategory, hasDate))
                    .addOnFailureListener(this, e -> android.util.Log.w("AddDocumentActivity", "populateFromFirestoreIfNeeded failed", e));
        } catch (Exception e) {
            android.util.Log.w("AddDocumentActivity", "populateFromFirestoreIfNeeded failed", e);
        }
    }

    private void applyFirestoreSnapshotToEditor(DocumentSnapshot snapshot,
                                               boolean hasUri,
                                               boolean hasTitle,
                                               boolean hasCategory,
                                               boolean hasDate) {
        if (snapshot == null || !snapshot.exists()) {
            return;
        }

        if (!hasUri) {
            String uri = snapshot.getString("uri");
            if (uri != null && !uri.trim().isEmpty()) {
                try {
                    selectedDocumentUri = Uri.parse(uri);
                    updateSelectedFileUi(selectedDocumentUri, snapshot.getString("displayName"));
                } catch (Exception ignored) {
                }
            }
        }

        if (!hasTitle) {
            String title = snapshot.getString("title");
            if (title != null && !title.isEmpty()) {
                setTextIfPresent(R.id.title_input, title);
            }
        }
        if (!hasCategory) {
            String category = snapshot.getString("category");
            if (category != null && !category.isEmpty()) {
                setTextIfPresent(R.id.category_input, category);
                updateReceiptDetailsVisibility(category);
            }
        }
        if (!hasDate) {
            String date = snapshot.getString("dateText");
            if (date != null && !date.isEmpty()) {
                setTextIfPresent(R.id.date_input, date);
            }
            Object rawDateMillis = snapshot.get("dateMillis");
            if (rawDateMillis instanceof Number) {
                long millis = ((Number) rawDateMillis).longValue();
                if (millis > 0) {
                    selectedDateMillis = millis;
                }
            }
        }

        if (notesInput != null && (notesInput.getText() == null || notesInput.getText().toString().trim().isEmpty())) {
            String notes = snapshot.getString("notes");
            if (notes != null) {
                setTextIfPresent(R.id.notes_input, notes);
            }
        }

        if (amountInput != null && (amountInput.getText() == null || amountInput.getText().toString().trim().isEmpty())) {
            String amount = snapshot.getString("amount");
            if (amount != null) {
                amountInput.setText(amount);
            }
        }

        if (storeNameInput != null && (storeNameInput.getText() == null || storeNameInput.getText().toString().trim().isEmpty())) {
            String storeName = snapshot.getString("storeName");
            if (storeName != null) {
                storeNameInput.setText(storeName);
            }
        }

        if (tagsGroup != null) {
            Object rawTags = snapshot.get("tags");
            if (rawTags instanceof List<?> && tagsGroup.getChildCount() <= 1) {
                tagsGroup.removeAllViews();
                for (Object tag : (List<?>) rawTags) {
                    if (tag != null) {
                        String text = String.valueOf(tag).trim();
                        if (!text.isEmpty()) {
                            addTagChip(text);
                        }
                    }
                }
                if (addTagChip != null) {
                    tagsGroup.addView(addTagChip);
                }
            }
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
