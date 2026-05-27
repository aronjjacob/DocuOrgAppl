package com.example.docuorg;

public class ProcessedItem {
    public final String title;
    public final String category;
    public final String imageUri;
    public final long timestamp;

    public ProcessedItem(String title, String category, String imageUri, long timestamp) {
        this.title = title;
        this.category = category;
        this.imageUri = imageUri;
        this.timestamp = timestamp;
    }
}

