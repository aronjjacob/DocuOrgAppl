package com.example.docuorg;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class DashboardActivity extends AppCompatActivity {

    private static final String FIRESTORE_USERS_COLLECTION = "users";
    private static final String FIRESTORE_DOCUMENTS_COLLECTION = "documents";

    private FirebaseAuth auth;
    private FirebaseFirestore firestore;
    private FirebaseAuth.AuthStateListener authStateListener;
    private ListenerRegistration documentsRegistration;

    private TextView greetingTitle;
    private TextView totalDocumentsValue;
    private TextView totalDocumentsDelta;

    private View recentCardPrimary;
    private View recentCardSecondary;
    private TextView recentTagPrimary;
    private TextView recentTitlePrimary;
    private TextView recentTimePrimary;
    private TextView recentTagSecondary;
    private TextView recentTitleSecondary;
    private TextView recentTimeSecondary;

    private final List<DocumentItem> recentDocuments = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_dashboard);
        BottomNavHelper.setupBottomNav(this, R.id.nav_home_item);

        auth = FirebaseAuth.getInstance();
        firestore = FirebaseFirestore.getInstance();

        greetingTitle = findViewById(R.id.greeting_title);
        totalDocumentsValue = findViewById(R.id.total_documents_value);
        totalDocumentsDelta = findViewById(R.id.total_documents_delta_value);

        recentCardPrimary = findViewById(R.id.recent_card_tax);
        recentCardSecondary = findViewById(R.id.recent_card_secondary);
        recentTagPrimary = findViewById(R.id.recent_card_tax_tag);
        recentTitlePrimary = findViewById(R.id.recent_card_tax_title);
        recentTimePrimary = findViewById(R.id.recent_card_tax_time);
        recentTagSecondary = findViewById(R.id.recent_card_secondary_tag);
        recentTitleSecondary = findViewById(R.id.recent_card_secondary_title);
        recentTimeSecondary = findViewById(R.id.recent_card_secondary_time);

        bindStaticActions();
        updateGreeting();
        bindRecentCard(null, recentTagPrimary, recentTitlePrimary, recentTimePrimary, recentCardPrimary);
        bindRecentCard(null, recentTagSecondary, recentTitleSecondary, recentTimeSecondary, recentCardSecondary);

        authStateListener = firebaseAuth -> {
            updateGreeting();
            refreshDashboardData();
        };

        findViewById(R.id.add_fab).setOnClickListener(view -> {
            Intent intent = new Intent(this, AddDocumentActivity.class);
            startActivity(intent);
        });

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.dashboard_root), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (auth != null && authStateListener != null) {
            auth.addAuthStateListener(authStateListener);
        }
        refreshDashboardData();
    }

    @Override
    protected void onStop() {
        super.onStop();
        stopFirestoreListener();
        if (auth != null && authStateListener != null) {
            auth.removeAuthStateListener(authStateListener);
        }
    }

    private void bindStaticActions() {
        View viewAll = findViewById(R.id.view_all);
        if (viewAll != null) {
            viewAll.setOnClickListener(v -> openDocumentsWithFilter(null));
        }

        wireCategoryShortcut(R.id.category_tax_item, getString(R.string.doc_type_tax));
        wireCategoryShortcut(R.id.category_medical_item, getString(R.string.doc_type_medical));
        wireCategoryShortcut(R.id.category_receipts_item, getString(R.string.doc_type_receipt));
        wireCategoryShortcut(R.id.category_personal_item, getString(R.string.doc_type_personal));
        wireCategoryShortcut(R.id.category_more_item, null);
    }

    private void wireCategoryShortcut(int viewId, String categoryFilter) {
        View item = findViewById(viewId);
        if (item == null) {
            return;
        }
        item.setOnClickListener(v -> openDocumentsWithFilter(categoryFilter));
    }

    private void openDocumentsWithFilter(String categoryFilter) {
        Intent intent = new Intent(this, DocumentsActivity.class);
        if (categoryFilter != null && !categoryFilter.trim().isEmpty()) {
            intent.putExtra(DocumentsActivity.EXTRA_INITIAL_FILTER, categoryFilter);
        }
        startActivity(intent);
    }

    private void updateGreeting() {
        if (greetingTitle == null) {
            return;
        }
        FirebaseUser user = auth != null ? auth.getCurrentUser() : null;
        if (user == null) {
            greetingTitle.setText(R.string.dashboard_greeting_default);
            return;
        }

        String display = user.getDisplayName();
        if (display == null || display.trim().isEmpty()) {
            String email = user.getEmail();
            if (email != null && email.contains("@")) {
                display = email.substring(0, email.indexOf('@'));
            } else {
                display = getString(R.string.nav_home);
            }
        }
        greetingTitle.setText(getString(R.string.dashboard_greeting_template, display));
    }

    private void refreshDashboardData() {
        FirebaseUser user = auth != null ? auth.getCurrentUser() : null;
        if (user == null) {
            stopFirestoreListener();
            recentDocuments.clear();
            renderDocuments();
            return;
        }
        startFirestoreListener(user.getUid());
    }

    private void startFirestoreListener(String uid) {
        stopFirestoreListener();
        Query query = firestore.collection(FIRESTORE_USERS_COLLECTION)
                .document(uid)
                .collection(FIRESTORE_DOCUMENTS_COLLECTION)
                .orderBy("id", Query.Direction.DESCENDING);

        documentsRegistration = query.addSnapshotListener(this, (snapshot, e) -> {
            if (e != null) {
                android.util.Log.e("DashboardActivity", "✗ Firestore listener FAILED", e);
                Toast.makeText(this, R.string.error_unable_to_load, Toast.LENGTH_SHORT).show();
                return;
            }

            recentDocuments.clear();
            if (snapshot != null) {
                for (com.google.firebase.firestore.DocumentSnapshot doc : snapshot.getDocuments()) {
                    DocumentItem item = DocumentItem.fromSnapshot(doc);
                    if (item != null) {
                        recentDocuments.add(item);
                    }
                }
            }
            renderDocuments();
        });
    }

    private void stopFirestoreListener() {
        if (documentsRegistration != null) {
            documentsRegistration.remove();
            documentsRegistration = null;
        }
    }

    private void renderDocuments() {
        int count = recentDocuments.size();
        if (totalDocumentsValue != null) {
            totalDocumentsValue.setText(String.valueOf(count));
        }
        if (totalDocumentsDelta != null) {
            totalDocumentsDelta.setText(getWeekDeltaText());
        }

        DocumentItem primary = count > 0 ? recentDocuments.get(0) : null;
        DocumentItem secondary = count > 1 ? recentDocuments.get(1) : null;

        bindRecentCard(primary, recentTagPrimary, recentTitlePrimary, recentTimePrimary, recentCardPrimary);
        bindRecentCard(secondary, recentTagSecondary, recentTitleSecondary, recentTimeSecondary, recentCardSecondary);
    }

    private String getWeekDeltaText() {
        long now = System.currentTimeMillis();

        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(now);
        cal.set(Calendar.DAY_OF_WEEK, cal.getFirstDayOfWeek());
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);

        long startThisWeek = cal.getTimeInMillis();
        long startLastWeek = startThisWeek - (7L * 24L * 60L * 60L * 1000L);

        int thisWeekCount = 0;
        int lastWeekCount = 0;
        for (DocumentItem item : recentDocuments) {
            Long dateMillis = item.dateMillis;
            if (dateMillis == null || dateMillis <= 0) {
                continue;
            }
            if (dateMillis >= startThisWeek) {
                thisWeekCount++;
            } else if (dateMillis >= startLastWeek) {
                lastWeekCount++;
            }
        }

        int delta = thisWeekCount - lastWeekCount;
        return getString(R.string.dashboard_week_delta_template, delta);
    }

    private void bindRecentCard(
            DocumentItem item,
            TextView tagView,
            TextView titleView,
            TextView timeView,
            View cardView
    ) {
        if (tagView != null) {
            tagView.setText(item != null ? deriveTag(item) : getString(R.string.dashboard_recent_empty_tag));
        }
        if (titleView != null) {
            titleView.setText(item != null ? item.bestTitle() : getString(R.string.dashboard_recent_empty_title));
        }
        if (timeView != null) {
            timeView.setText(item != null ? deriveDateText(item) : getString(R.string.dashboard_recent_empty_time));
        }
        if (cardView != null) {
            cardView.setOnClickListener(v -> {
                if (item == null) {
                    Toast.makeText(this, R.string.no_documents_yet, Toast.LENGTH_SHORT).show();
                    return;
                }
                Intent intent = new Intent(this, ViewDocumentActivity.class);
                intent.putExtra(ViewDocumentActivity.EXTRA_DOCUMENT_ID, item.id);
                if (item.uri != null) intent.putExtra(ViewDocumentActivity.EXTRA_PRELOAD_URI, item.uri);
                if (item.title != null) intent.putExtra(ViewDocumentActivity.EXTRA_PRELOAD_TITLE, item.title);
                if (item.category != null) intent.putExtra(ViewDocumentActivity.EXTRA_PRELOAD_CATEGORY, item.category);
                if (item.dateText != null) intent.putExtra(ViewDocumentActivity.EXTRA_PRELOAD_DATE, item.dateText);
                if (item.dateMillis != null) {
                    intent.putExtra(ViewDocumentActivity.EXTRA_PRELOAD_DATE_MILLIS, item.dateMillis);
                }
                startActivity(intent);
            });
        }
    }

    private String deriveDateText(DocumentItem item) {
        String date = item.dateText;
        if (date == null || date.trim().isEmpty()) {
            return getString(R.string.dashboard_recent_date_unknown);
        }
        return getString(R.string.dashboard_recent_modified_template, date);
    }

    private String deriveTag(DocumentItem item) {
        String mime = item.mime != null ? item.mime.toLowerCase(Locale.US) : "";
        if (mime.contains("pdf")) {
            return "PDF";
        }
        if (mime.contains("word") || mime.contains("doc")) {
            return "DOC";
        }
        if (mime.contains("image")) {
            return "IMG";
        }
        if (item.category != null && !item.category.trim().isEmpty()) {
            return item.category.trim().toUpperCase(Locale.US);
        }
        return getString(R.string.dashboard_recent_empty_tag);
    }
}
