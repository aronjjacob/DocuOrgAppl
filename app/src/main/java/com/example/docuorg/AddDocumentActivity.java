package com.example.docuorg;

import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.OpenableColumns;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

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

    private ActivityResultLauncher<String[]> openDocumentLauncher;
    private ActivityResultLauncher<Uri> takePictureLauncher;

    private Uri selectedDocumentUri;
    private TextView selectedFileLabel;
    private ImageView selectedFilePreview;

    private Uri pendingCameraUri;
    private String pendingCameraDisplayName;

    private TextView amountValue;
    private TextView storeNameValue;
    private com.google.android.material.chip.ChipGroup tagsGroup;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_add_document);

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
