package com.example.docuorg;

import android.os.Bundle;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class AddDocumentActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_add_document);

        findViewById(R.id.add_document_back).setOnClickListener(view -> finish());
        findViewById(R.id.cancel_button).setOnClickListener(view -> finish());
        findViewById(R.id.save_document_button).setOnClickListener(
                view -> Toast.makeText(this, R.string.save_document, Toast.LENGTH_SHORT).show());
        findViewById(R.id.gallery_button).setOnClickListener(
                view -> Toast.makeText(this, R.string.gallery, Toast.LENGTH_SHORT).show());
        findViewById(R.id.camera_button).setOnClickListener(
                view -> Toast.makeText(this, R.string.camera, Toast.LENGTH_SHORT).show());

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.add_document_root), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
    }
}

