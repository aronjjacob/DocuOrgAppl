package com.example.docuorg;

import android.os.Bundle;
import android.widget.EditText;
import android.widget.Toast;
import android.view.View;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class UpdatePasswordActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_update_password);

        findViewById(R.id.update_password_back).setOnClickListener(v -> finish());

        EditText currentInput = findViewById(R.id.current_password_input);
        EditText newInput = findViewById(R.id.new_password_input);
        EditText confirmInput = findViewById(R.id.confirm_password_input);
        View updateButton = findViewById(R.id.update_password_button);

        updateButton.setOnClickListener(v -> {
            String current = currentInput.getText() != null ? currentInput.getText().toString() : "";
            String newPw = newInput.getText() != null ? newInput.getText().toString() : "";
            String confirm = confirmInput.getText() != null ? confirmInput.getText().toString() : "";
            if (current.isEmpty() || newPw.isEmpty() || confirm.isEmpty()) {
                Toast.makeText(this, R.string.password_required_fields, Toast.LENGTH_SHORT).show();
                return;
            }
            if (!newPw.equals(confirm)) {
                Toast.makeText(this, R.string.password_mismatch, Toast.LENGTH_SHORT).show();
                return;
            }
            if (newPw.length() < 6) {
                Toast.makeText(this, R.string.password_too_short, Toast.LENGTH_SHORT).show();
                return;
            }

            com.google.firebase.auth.FirebaseUser user =
                    com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser();
            if (user == null || user.getEmail() == null) {
                Toast.makeText(this, R.string.password_update_failed, Toast.LENGTH_SHORT).show();
                return;
            }

            updateButton.setEnabled(false);
            com.google.firebase.auth.AuthCredential credential =
                    com.google.firebase.auth.EmailAuthProvider.getCredential(user.getEmail(), current);
            user.reauthenticate(credential)
                    .addOnSuccessListener(unused -> user.updatePassword(newPw)
                            .addOnSuccessListener(ignored -> {
                                Toast.makeText(this, R.string.password_updated, Toast.LENGTH_SHORT).show();
                                finish();
                            })
                            .addOnFailureListener(err -> {
                                Toast.makeText(this, R.string.password_update_failed, Toast.LENGTH_SHORT).show();
                                updateButton.setEnabled(true);
                            }))
                    .addOnFailureListener(err -> {
                        Toast.makeText(this, R.string.password_update_requires_recent_login, Toast.LENGTH_SHORT).show();
                        updateButton.setEnabled(true);
                    });
        });

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.update_password_root), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
    }
}
