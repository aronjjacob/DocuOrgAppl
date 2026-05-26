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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_add_document);

        selectedFileLabel = findViewById(R.id.selected_file_label);
        selectedFilePreview = findViewById(R.id.selected_file_preview);

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
}

