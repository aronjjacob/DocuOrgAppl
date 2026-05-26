package com.example.docuorg;

import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.content.FileProvider;

import android.net.Uri;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.FrameLayout;
import android.view.View;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

import android.content.Intent;
import android.os.Handler;
import android.os.Looper;

public class AiActivity extends AppCompatActivity {

    private ActivityResultLauncher<String> pickImageLauncher;
    private ActivityResultLauncher<Uri> takePictureLauncher;
    private Uri pendingCameraUri;

    private final AiScanClient aiScanClient = new AiScanClient();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private FrameLayout scanningOverlay;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_ai);
        BottomNavHelper.setupBottomNav(this, R.id.nav_ai_item);

        ImageView previewImage = findViewById(R.id.ai_preview_image);
        ImageView uploadIcon = findViewById(R.id.ai_upload_icon);
        TextView uploadTitle = findViewById(R.id.ai_upload_title);
        TextView uploadBody = findViewById(R.id.ai_upload_body);
        scanningOverlay = findViewById(R.id.ai_scanning_overlay);

        pickImageLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                uri -> {
                    if (uri != null) {
                        showPreview(previewImage, uploadIcon, uploadTitle, uploadBody, uri);
                        Toast.makeText(this, R.string.ai_image_loaded, Toast.LENGTH_SHORT).show();
                        runAiScan(uri);
                    }
                });

        takePictureLauncher = registerForActivityResult(
                new ActivityResultContracts.TakePicture(),
                success -> {
                    if (success && pendingCameraUri != null) {
                        showPreview(previewImage, uploadIcon, uploadTitle, uploadBody, pendingCameraUri);
                        Toast.makeText(this, R.string.ai_image_loaded, Toast.LENGTH_SHORT).show();
                        runAiScan(pendingCameraUri);
                    }
                });

        findViewById(R.id.ai_upload_area).setOnClickListener(v -> {
            Toast.makeText(this, R.string.ai_pick_image, Toast.LENGTH_SHORT).show();
            pickImageLauncher.launch("image/*");
        });

        findViewById(R.id.ai_scan_button).setOnClickListener(v -> {
            Uri cameraUri = createTempImageUri();
            if (cameraUri == null) {
                Toast.makeText(this, R.string.ai_camera_unavailable, Toast.LENGTH_SHORT).show();
                pickImageLauncher.launch("image/*");
                return;
            }
            pendingCameraUri = cameraUri;
            takePictureLauncher.launch(cameraUri);
        });

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.ai_root), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
    }

    private Uri createTempImageUri() {
        File cacheDir = new File(getCacheDir(), "images");
        if (!cacheDir.exists() && !cacheDir.mkdirs()) {
            return null;
        }
        File tempFile = new File(cacheDir, "scan_" + UUID.randomUUID() + ".jpg");
        try {
            if (!tempFile.createNewFile()) {
                return null;
            }
        } catch (IOException e) {
            return null;
        }
        return FileProvider.getUriForFile(
                this,
                getPackageName() + ".fileprovider",
                tempFile
        );
    }

    private void showPreview(ImageView previewImage, ImageView uploadIcon,
                             TextView uploadTitle, TextView uploadBody, Uri uri) {
        previewImage.setImageURI(uri);
        previewImage.setVisibility(android.view.View.VISIBLE);
        uploadIcon.setVisibility(android.view.View.GONE);
        uploadTitle.setVisibility(android.view.View.GONE);
        uploadBody.setVisibility(android.view.View.GONE);
    }

    private void runAiScan(Uri uri) {
        mainHandler.post(() -> scanningOverlay.setVisibility(View.VISIBLE));
        aiScanClient.scanImage(this, uri, new AiScanClient.ScanCallback() {
            @Override
            public void onSuccess(AiScanClient.DocumentScanResult result) {
                mainHandler.post(() -> {
                    scanningOverlay.setVisibility(View.GONE);
                    openAddDocumentWithResult(uri, result);
                });
            }

            @Override
            public void onError(String message) {
                mainHandler.post(() -> {
                    scanningOverlay.setVisibility(View.GONE);
                    Toast.makeText(AiActivity.this, message, Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void openAddDocumentWithResult(Uri imageUri, AiScanClient.DocumentScanResult result) {
        Intent intent = new Intent(this, AddDocumentActivity.class);
        intent.putExtra(AddDocumentActivity.EXTRA_IMAGE_URI, imageUri.toString());
        intent.putExtra(AddDocumentActivity.EXTRA_TITLE, result.title);
        intent.putExtra(AddDocumentActivity.EXTRA_CATEGORY, result.category);
        intent.putExtra(AddDocumentActivity.EXTRA_DATE, result.date);
        intent.putExtra(AddDocumentActivity.EXTRA_AMOUNT, result.amount);
        intent.putExtra(AddDocumentActivity.EXTRA_STORE_NAME, result.storeName);
        intent.putExtra(AddDocumentActivity.EXTRA_NOTES, result.notes);
        intent.putExtra(AddDocumentActivity.EXTRA_TAGS, result.tags.toArray(new String[0]));
        startActivity(intent);
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        // Finish this activity so the back stack is clean
        finish();
    }
}
