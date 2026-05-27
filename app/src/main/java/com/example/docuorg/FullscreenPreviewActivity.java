package com.example.docuorg;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;

import androidx.appcompat.app.AppCompatActivity;

/**
 * Fullscreen preview for image documents. Expects EXTRA_URI string extra.
 */
public class FullscreenPreviewActivity extends AppCompatActivity {

    public static final String EXTRA_URI = "extra_preview_uri";

    private ZoomImageView zoomImage;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_fullscreen_preview);

        zoomImage = findViewById(R.id.fullscreen_zoom_image);
        ImageButton close = findViewById(R.id.fullscreen_close);
        close.setOnClickListener(v -> finish());

        Intent intent = getIntent();
        if (intent == null) return;

        String uri = intent.getStringExtra(EXTRA_URI);
        if (uri == null) return;

        try {
            Uri u = Uri.parse(uri);
            zoomImage.setImageURI(u);
        } catch (Exception e) {
            finish();
        }
    }
}

