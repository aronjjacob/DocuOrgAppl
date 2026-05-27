# DocuOrg Mobile App Final Requirement Documentation

## 3. Introduction

### Background of the Project
DocuOrg is a mobile document organization application designed to help users digitize, categorize, and manage personal and business documents. The app combines document capture workflows with AI-powered extraction to turn paper or image-based files into structured, searchable records.

### Purpose of the Mobile Application
The mobile app provides a secure digital filing cabinet that enables users to capture documents, organize them by category and tags, and access them from a single dashboard. It also offers an AI scan workflow to prefill document metadata and speed up data entry.

### Target Users
- Individuals managing personal documents (IDs, receipts, tax, medical, and personal files).
- Small business owners tracking expenses, invoices, and compliance documents.
- Users who need mobile-first document capture and lightweight organization.

## 4. Project Objectives

### General Objective
Deliver a mobile application that simplifies document capture, organization, and retrieval with secure access and AI-assisted data extraction.

### Specific Objectives
- Provide secure authentication and account creation using Firebase Email/Password.
- Enable document capture via gallery upload or camera.
- Capture structured metadata (title, category, date, tags, notes, receipt details).
- Offer AI-based scanning to prefill metadata from document images.
- Store documents locally with optional cloud sync when authenticated.
- Provide a dashboard and document library for quick access.

## 5. Scope and Limitations

### Features Included in the App
- Login, sign-up, and password reset flows using Firebase Authentication.
- Dashboard overview with document counts, categories, and recent items.
- Document library with filter chips and list/grid UI.
- Add Document flow with upload area, metadata fields, tags, and receipt details.
- AI scan workflow for extracting metadata from images.
- Document detail view with preview, metadata, and action buttons.
- Settings screen with account, preferences, security, and logout.
- Bottom navigation for Home, Documents, AI, and Settings.

### Features Not Included or Limitations
- iOS version is not implemented; the current app targets Android only (minSdk 24).
- Search, filter, edit, share, and delete actions are presented in the UI but are not fully wired to backend logic.
- Document lists and dashboard counters are static UI samples; live data binding is not implemented.
- AI scanning requires a valid `GEMINI_API_KEY` in `local.properties` and network connectivity.
- No multi-user sharing or collaboration features are included.

## 6. System Overview

### Brief Description of How the App Works
Users authenticate with Firebase, then land on a dashboard showing document statistics and quick links. The document library provides category filters and recent document cards. Users can add new documents by uploading files or capturing photos, entering metadata, and saving the record locally or to Firestore when signed in. The AI screen allows image capture or selection, sends the image to a Gemini-based service, and opens the Add Document screen with extracted metadata for review. Settings provide account and preference options plus logout.

### Technologies Used
- **Platform**: Android (Java 11, AndroidX, Material Components)
- **Authentication**: Firebase Authentication (Email/Password)
- **Data Storage**: Firebase Firestore for cloud sync, SharedPreferences for local cache
- **AI Processing**: Google Gemini API (Generative Language) via HTTPS
- **File Handling**: Storage Access Framework, FileProvider, camera capture

### Platform: Android/iOS
- **Android**: Supported (minSdk 24, targetSdk 36)
- **iOS**: Not available in the current implementation

## 7. App Features and Functionalities

### 7.1 Secure Sign-In & Password Reset
- **Description**: Allows users to log in with an email and password and request password reset emails.
- **Screenshot placeholder**: `[Screenshot: Login Screen]`
- **How it works**: The login form validates email/password inputs and authenticates using Firebase. A "Forgot Password" dialog triggers Firebase password reset emails.

### 7.2 Account Registration
- **Description**: Enables new users to create an account with email/password confirmation.
- **Screenshot placeholder**: `[Screenshot: Sign-Up Screen]`
- **How it works**: The sign-up form validates input, creates a Firebase user, sends a verification email, and redirects to the dashboard.

### 7.3 Dashboard Overview
- **Description**: Provides a summary of document counts, categories, and recent documents.
- **Screenshot placeholder**: `[Screenshot: Dashboard Screen]`
- **How it works**: The dashboard presents static counters, category icons, and recent document cards; a floating action button opens Add Document.

### 7.4 Document Library & Filters
- **Description**: Lists documents with category filters and quick actions.
- **Screenshot placeholder**: `[Screenshot: Documents Screen]`
- **How it works**: Filter chips highlight categories (All, Receipts, Medical, Tax, Personal). Document cards display sample titles, dates, and types.

### 7.5 Add Document (Upload + Metadata)
- **Description**: Captures a new document file and associated metadata.
- **Screenshot placeholder**: `[Screenshot: Add Document Screen]`
- **How it works**: Users upload a PDF or image from gallery/camera, enter title, category, date, tags, notes, and receipt details (amount/store) when category is Receipt. Validation ensures required fields before saving.

### 7.6 AI Scan Assistant
- **Description**: Extracts metadata from a scanned or selected image using AI.
- **Screenshot placeholder**: `[Screenshot: AI Scan Screen]`
- **How it works**: The AI screen captures or selects an image, sends it to the Gemini API, and parses a JSON response. The app then opens Add Document with fields prefilled.

### 7.7 Document Details View
- **Description**: Displays a document preview, metadata, and action buttons.
- **Screenshot placeholder**: `[Screenshot: View Document Screen]`
- **How it works**: The screen shows a preview panel, tags, category, year, and buttons for edit/share/delete (UI placeholders).

### 7.8 Document Storage & Sync
- **Description**: Saves document metadata locally and optionally to the cloud.
- **Screenshot placeholder**: `[Screenshot: Save Confirmation / Toast]`
- **How it works**: When signed in, documents are written to Firestore under the user’s collection and cached locally. Without authentication, data is stored in SharedPreferences.

### 7.9 Settings & Logout
- **Description**: Provides access to account details, preferences, security links, and logout.
- **Screenshot placeholder**: `[Screenshot: Settings Screen]`
- **How it works**: The settings UI groups profile, password, notifications, appearance, privacy, terms, and help options. Logout signs the user out and returns to the login screen.

### 7.10 Bottom Navigation
- **Description**: Persistent navigation across Home, Documents, AI, and Settings.
- **Screenshot placeholder**: `[Screenshot: Bottom Navigation]`
- **How it works**: The navigation bar routes users between major sections and highlights the active tab.
