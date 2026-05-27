package com.example.docuorg;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;

import java.util.ArrayList;
import java.util.List;

public class DocumentsActivity extends AppCompatActivity {

    public static final String EXTRA_INITIAL_FILTER = "extra_initial_filter";

    private static final String FIRESTORE_USERS_COLLECTION = "users";
    private static final String FIRESTORE_DOCUMENTS_COLLECTION = "documents";

    private FirebaseAuth auth;
    private FirebaseFirestore firestore;
    private FirebaseAuth.AuthStateListener authStateListener;
    private ListenerRegistration documentsRegistration;

    private RecyclerView recyclerView;
    private TextView emptyView;
    private DocumentsAdapter adapter;

    private ChipGroup filtersGroup;
    private Chip filterAll;
    private Chip filterReceipts;
    private Chip filterMedical;
    private Chip filterTax;
    private Chip filterPersonal;

    private String activeCategoryFilter = null; // null = All
    private final List<DocumentItem> allDocuments = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_documents);
        BottomNavHelper.setupBottomNav(this, R.id.nav_documents_item);

        View profileButton = findViewById(R.id.documents_profile);
        if (profileButton != null) {
            profileButton.setOnClickListener(v -> {
                Intent intent = new Intent(this, ProfileInfoActivity.class);
                startActivity(intent);
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
            });
        }

        auth = FirebaseAuth.getInstance();
        firestore = FirebaseFirestore.getInstance();

        recyclerView = findViewById(R.id.documents_recycler);
        emptyView = findViewById(R.id.documents_empty);

        adapter = new DocumentsAdapter(item -> {
            Intent intent = new Intent(this, ViewDocumentActivity.class);
            intent.putExtra(ViewDocumentActivity.EXTRA_DOCUMENT_ID, item.id);
            // Preload some fields so the View screen appears instantly and
            // is robust if local JSON is temporarily missing or corrupted.
            if (item.uri != null) intent.putExtra("extra_preload_uri", item.uri);
            if (item.title != null) intent.putExtra("extra_preload_title", item.title);
            if (item.category != null) intent.putExtra("extra_preload_category", item.category);
            if (item.dateText != null) intent.putExtra("extra_preload_date", item.dateText);
            if (item.dateMillis != null) intent.putExtra("extra_preload_date_millis", item.dateMillis);
            startActivity(intent);
        });

        if (recyclerView != null) {
            recyclerView.setLayoutManager(new LinearLayoutManager(this));
            recyclerView.setAdapter(adapter);
        }

        setupFilters();
        applyInitialFilterFromIntent();

        findViewById(R.id.documents_fab).setOnClickListener(view -> {
            Intent intent = new Intent(this, AddDocumentActivity.class);
            startActivity(intent);
        });

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.documents_root), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        authStateListener = firebaseAuth -> refreshDataSource();
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (auth != null && authStateListener != null) {
            auth.addAuthStateListener(authStateListener);
        }
        refreshDataSource();
    }

    @Override
    protected void onStop() {
        super.onStop();
        stopFirestoreListener();
        if (auth != null && authStateListener != null) {
            auth.removeAuthStateListener(authStateListener);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshDataSource();
    }

    private void setupFilters() {
        filtersGroup = findViewById(R.id.documents_filters);
        filterAll = findViewById(R.id.filter_all);
        filterReceipts = findViewById(R.id.filter_receipts);
        filterMedical = findViewById(R.id.filter_medical);
        filterTax = findViewById(R.id.filter_tax);
        filterPersonal = findViewById(R.id.filter_personal);

        if (filtersGroup != null) {
            filtersGroup.setOnCheckedChangeListener((group, checkedId) -> {
                activeCategoryFilter = mapCheckedIdToCategory(checkedId);
                updateFilterChipStyles(checkedId);
                applyFilterAndRender();
            });
        }

        // Ensure initial styles are correct.
        int initialChecked = filtersGroup != null ? filtersGroup.getCheckedChipId() : View.NO_ID;
        if (initialChecked == View.NO_ID) {
            initialChecked = R.id.filter_all;
        }
        activeCategoryFilter = mapCheckedIdToCategory(initialChecked);
        updateFilterChipStyles(initialChecked);
    }

    private String mapCheckedIdToCategory(int checkedId) {
        if (checkedId == R.id.filter_receipts) {
            return getString(R.string.doc_type_receipt);
        }
        if (checkedId == R.id.filter_medical) {
            return getString(R.string.doc_type_medical);
        }
        if (checkedId == R.id.filter_tax) {
            return getString(R.string.doc_type_tax);
        }
        if (checkedId == R.id.filter_personal) {
            return getString(R.string.doc_type_personal);
        }
        return null; // All
    }

    private void applyInitialFilterFromIntent() {
        if (filtersGroup == null) {
            return;
        }
        String initialFilter = getIntent() != null ? getIntent().getStringExtra(EXTRA_INITIAL_FILTER) : null;
        if (initialFilter == null || initialFilter.trim().isEmpty()) {
            return;
        }
        int targetChipId = mapCategoryToChipId(initialFilter);
        if (targetChipId == View.NO_ID) {
            return;
        }
        if (filtersGroup.getCheckedChipId() != targetChipId) {
            filtersGroup.check(targetChipId);
        }
    }

    private int mapCategoryToChipId(String category) {
        String value = category.trim().toLowerCase();
        if (value.contains("receipt")) {
            return R.id.filter_receipts;
        }
        if (value.contains("medical")) {
            return R.id.filter_medical;
        }
        if (value.contains("tax")) {
            return R.id.filter_tax;
        }
        if (value.contains("personal") || value.contains("id")) {
            return R.id.filter_personal;
        }
        if (value.contains("all") || value.contains("other")) {
            return R.id.filter_all;
        }
        return View.NO_ID;
    }

    private void updateFilterChipStyles(int checkedId) {
        styleChip(filterAll, checkedId == R.id.filter_all);
        styleChip(filterReceipts, checkedId == R.id.filter_receipts);
        styleChip(filterMedical, checkedId == R.id.filter_medical);
        styleChip(filterTax, checkedId == R.id.filter_tax);
        styleChip(filterPersonal, checkedId == R.id.filter_personal);
    }

    private void styleChip(Chip chip, boolean selected) {
        if (chip == null) {
            return;
        }
        if (selected) {
            chip.setChipBackgroundColorResource(R.color.primary_teal);
            chip.setTextColor(ContextCompat.getColor(this, R.color.white));
        } else {
            chip.setChipBackgroundColorResource(R.color.chip_selected_bg);
            chip.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
        }
    }

    private void refreshDataSource() {
        FirebaseUser user = auth != null ? auth.getCurrentUser() : null;
        if (user == null) {
            stopFirestoreListener();
            allDocuments.clear();
            applyFilterAndRender();
            showFirebaseLoginRequired();
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
                android.util.Log.e("DocumentsActivity", "✗ Firestore listener FAILED", e);
                if (allDocuments.isEmpty()) {
                    Toast.makeText(this, R.string.error_unable_to_load, Toast.LENGTH_SHORT).show();
                }
                return;
            }
            allDocuments.clear();
            if (snapshot != null) {
                for (com.google.firebase.firestore.DocumentSnapshot doc : snapshot.getDocuments()) {
                    DocumentItem item = DocumentItem.fromSnapshot(doc);
                    if (item != null) {
                        allDocuments.add(item);
                    }
                }
            }
            android.util.Log.i("DocumentsActivity", "✓ Loaded " + allDocuments.size() + " documents from Firestore");
            applyFilterAndRender();
        });
    }

    private void showFirebaseLoginRequired() {
        if (emptyView != null) {
            emptyView.setText(R.string.firebase_login_required);
            emptyView.setVisibility(View.VISIBLE);
        }
        if (recyclerView != null) {
            recyclerView.setVisibility(View.GONE);
        }
        Toast.makeText(this, R.string.firebase_login_required, Toast.LENGTH_SHORT).show();
    }

    private void stopFirestoreListener() {
        if (documentsRegistration != null) {
            documentsRegistration.remove();
            documentsRegistration = null;
        }
    }


    private void applyFilterAndRender() {
        List<DocumentItem> filtered = new ArrayList<>();
        for (DocumentItem item : allDocuments) {
            if (activeCategoryFilter != null) {
                if (item.category == null) {
                    continue;
                }
                if (!item.category.equalsIgnoreCase(activeCategoryFilter)) {
                    continue;
                }
            }
            filtered.add(item);
        }

        if (adapter != null) {
            adapter.submitList(filtered);
        }

        boolean empty = filtered.isEmpty();
        if (emptyView != null) {
            emptyView.setVisibility(empty ? View.VISIBLE : View.GONE);
        }
        if (recyclerView != null) {
            recyclerView.setVisibility(empty ? View.GONE : View.VISIBLE);
        }
    }
}
