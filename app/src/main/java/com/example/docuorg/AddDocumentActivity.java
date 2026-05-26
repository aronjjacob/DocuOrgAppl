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

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

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

    private MaterialCardView receiptDetailsCard;
    private TextInputEditText amountInput;
    private TextInputEditText storeNameInput;

    private ChipGroup tagsGroup;
    private Chip addTagChip;

    private Long selectedDateMillis;
    private TextView amountValue;
    private TextView storeNameValue;
    private com.google.android.material.chip.ChipGroup tagsGroup;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_add_document);

        selectedFileLabel = findViewById(R.id.selected_file_label);
        selectedFilePreview = findViewById(R.id.selected_file_preview);

        titleInput = findViewById(R.id.title_input);
        categoryInput = findViewById(R.id.category_input);
        dateInputLayout = findViewById(R.id.date_input_layout);
        dateInput = findViewById(R.id.date_input);
        notesInput = findViewById(R.id.notes_input);

        receiptDetailsCard = findViewById(R.id.receipt_details_card);
        amountInput = findViewById(R.id.amount_input);
        storeNameInput = findViewById(R.id.store_name_input);

        tagsGroup = findViewById(R.id.tags_group);
        addTagChip = findViewById(R.id.tag_add);

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
                        // Capture cancelled/failed; clear pending URI.
                        pendingCameraUri = null;
                        pendingCameraDisplayName = null;
                    }
                }
        );

        findViewById(R.id.add_document_back).setOnClickListener(view -> finish());
        findViewById(R.id.cancel_button).setOnClickListener(view -> finish());
        findViewById(R.id.save_document_button).setOnClickListener(view -> saveDocument());
        try {
            selectedFileLabel = findViewById(R.id.selected_file_label);
            selectedFilePreview = findViewById(R.id.selected_file_preview);
            amountValue = findViewById(R.id.amount_value);
            storeNameValue = findViewById(R.id.store_name_value);
            tagsGroup = findViewById(R.id.tags_group);

            applyScanExtras(getIntent());

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
                            // Capture cancelled/failed; clear pending URI.
                            pendingCameraUri = null;
                            pendingCameraDisplayName = null;
                        }
                    }
            );

            findViewById(R.id.add_document_back).setOnClickListener(view -> finish());
            findViewById(R.id.cancel_button).setOnClickListener(view -> finish());
            findViewById(R.id.save_document_button).setOnClickListener(
                    view -> Toast.makeText(this, R.string.save_document, Toast.LENGTH_SHORT).show());

        setupCategoryDropdown();
        setupDatePicker();
        setupTagsUi();

        if (savedInstanceState != null) {
            String restoredUri = savedInstanceState.getString(STATE_SELECTED_URI);
            if (restoredUri != null) {
                onDocumentPicked(Uri.parse(restoredUri));
            View uploadArea = findViewById(R.id.upload_area);
            if (uploadArea != null) {
                uploadArea.setOnClickListener(v -> launchGalleryPicker());
            }

            findViewById(R.id.gallery_button).setOnClickListener(view -> launchGalleryPicker());
            findViewById(R.id.camera_button).setOnClickListener(view -> launchCameraCapture());

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
            }
            pendingCameraDisplayName = savedInstanceState.getString(STATE_CAMERA_PENDING_NAME);

            if (savedInstanceState.containsKey(STATE_SELECTED_DATE_MILLIS)) {
                long restoredDate = savedInstanceState.getLong(STATE_SELECTED_DATE_MILLIS, -1);
                if (restoredDate > 0) {
                    selectedDateMillis = restoredDate;
                    dateInput.setText(formatDate(restoredDate));
                }
            }
        }

        // Receipt details are optional, and generally only relevant for receipt-like docs.
        if (receiptDetailsCard != null) {
            receiptDetailsCard.setVisibility(View.GONE);
        }

            ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.add_document_root), (v, insets) -> {
                Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
                return insets;
            });
        } catch (Exception e) {
            // Log error and finish activity to prevent crash
            android.util.Log.e("AddDocumentActivity", "Error initializing activity", e);
            Toast.makeText(this, "Error loading document form", Toast.LENGTH_SHORT).show();
            finish();
        }
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

    private JSONArray collectTags() {
        JSONArray tags = new JSONArray();
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
                    tags.put(text.toString());
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
        String title = titleInput != null && titleInput.getText() != null
                ? titleInput.getText().toString().trim()
                : "";
        String category = categoryInput != null ? categoryInput.getText().toString().trim() : "";
        String dateText = dateInput != null && dateInput.getText() != null
                ? dateInput.getText().toString().trim()
                : "";

        if (selectedDocumentUri == null || TextUtils.isEmpty(title) || TextUtils.isEmpty(category) || TextUtils.isEmpty(dateText)) {
            Toast.makeText(this, R.string.error_missing_required, Toast.LENGTH_SHORT).show();
            return;
        }

        updateReceiptDetailsVisibility(category);

        String notes = notesInput != null && notesInput.getText() != null
                ? notesInput.getText().toString().trim()
                : "";
        String amount = amountInput != null && amountInput.getText() != null
                ? amountInput.getText().toString().trim()
                : "";
        String storeName = storeNameInput != null && storeNameInput.getText() != null
                ? storeNameInput.getText().toString().trim()
                : "";

        try {
            JSONObject doc = new JSONObject();
            doc.put("id", System.currentTimeMillis());
            doc.put("uri", selectedDocumentUri.toString());
            doc.put("displayName", getDisplayName(selectedDocumentUri));
            doc.put("mime", getContentResolver().getType(selectedDocumentUri));
            doc.put("title", title);
            doc.put("category", category);
            doc.put("dateText", dateText);
            if (selectedDateMillis != null) {
                doc.put("dateMillis", selectedDateMillis);
            }
            doc.put("notes", notes);
            doc.put("tags", collectTags());

            // Optional receipt metadata.
            if (category.equalsIgnoreCase(getString(R.string.doc_type_receipt))) {
                doc.put("amount", amount);
                doc.put("storeName", storeName);
            }

            SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            String raw = prefs.getString(PREFS_DOCUMENTS_JSON, "[]");
            JSONArray docs = new JSONArray(raw);
            docs.put(doc);
            prefs.edit().putString(PREFS_DOCUMENTS_JSON, docs.toString()).apply();

            Toast.makeText(this, R.string.document_saved, Toast.LENGTH_SHORT).show();
            finish();
        } catch (JSONException e) {
            Toast.makeText(this, R.string.error_unable_to_save, Toast.LENGTH_SHORT).show();
        }
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
            if (amount != null && amountValue != null) {
                amountValue.setText(amount.isEmpty() ? getString(R.string.amount_value) : "$ " + amount);
            }

            String storeName = intent.getStringExtra(EXTRA_STORE_NAME);
            if (storeName != null && storeNameValue != null) {
                storeNameValue.setText(storeName.isEmpty() ? getString(R.string.store_name_hint) : storeName);
            }

            String notes = intent.getStringExtra(EXTRA_NOTES);
            if (notes != null && !notes.isEmpty()) {
                setTextIfPresent(R.id.notes_input, notes);
            }

            String[] tags = intent.getStringArrayExtra(EXTRA_TAGS);
            if (tags != null && tagsGroup != null) {
                tagsGroup.removeAllViews();
                for (String tag : tags) {
                    if (tag == null || tag.trim().isEmpty()) {
                        continue;
                    }
                    com.google.android.material.chip.Chip chip = new com.google.android.material.chip.Chip(this);
                    chip.setText(tag.trim());
                    chip.setChipBackgroundColorResource(R.color.chip_bg);
                    chip.setTextColor(getColor(R.color.primary_teal));
                    chip.setCloseIconVisible(true);
                    chip.setCloseIconTintResource(R.color.primary_teal);
                    tagsGroup.addView(chip);
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
}
