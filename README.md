# CipherChat

A secure realtime Android messaging application built using modern Android development practices, Firebase services, Clean Architecture, and Jetpack Compose.

## Features

### Authentication

* Email & Password Authentication
* Firebase Authentication
* JWT Token Retrieval
* Secure Token Storage
* Auto Login
* Session Restoration
* Logout Flow

### Security

* EncryptedSharedPreferences
* Device Tracking
* Single Device Session Validation
* Secure Session Management

### Realtime Features

* Firestore Realtime User Sync
* Online/Offline Presence
* Last Seen Tracking
* Realtime User List Updates

### Architecture

* Clean Architecture
* MVVM
* Repository Pattern
* Use Cases
* Dependency Injection with Hilt
* StateFlow & Coroutines

## Tech Stack

* Kotlin
* Jetpack Compose
* Material 3
* Firebase Authentication
* Cloud Firestore
* Hilt
* Coroutines
* Flow
* StateFlow
* EncryptedSharedPreferences

## Project Structure

app/
├── data/
├── domain/
├── presentation/
├── security/
├── di/
└── navigation/

## Authentication Flow

Login
→ Firebase Authentication
→ JWT Token Generation
→ Encrypted Token Storage
→ Session Restoration
→ Auto Login

## Security Features

* JWT-based session handling
* Encrypted local storage
* Device validation
* Secure logout
* Presence management

## Upcoming Features

* One-to-One Realtime Messaging
* Message Delivery Status
* Typing Indicators
* Push Notifications
* End-to-End Encryption
* Offline Message Caching
* Media Sharing
