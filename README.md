# SmashSMS

Background SMS/notification spam filter for Android using **Shizuku**, allowing you to keep **Google Messages** as your default RCS app while automatically deleting matching spam threads from the system Telephony database.

## Overview

- **Contact Safety:** Skips saved contacts via `ContactsContract.PhoneLookup`.
- **Spam Matching:** Filters non-contact messages against case-insensitive political/cold-text regex rules (`SpamDetector`).
- **Background Deletion:** Waits briefly for Telephony persistence, then deletes matching SMS rows via Shizuku (`content delete`).
- **Notification Gatekeeper:** Dismisses matching Google Messages notifications (`NotificationListenerService`) silently, and plays the default notification sound/vibration (respecting ringer mode and DND) only for clean messages.
- **Quarantine Log:** Saves filtered messages in a local Room database viewable in the app.

## Requirements

- Android 14+ (`minSdk = 34`)
- [Shizuku](https://shizuku.rikka.app/) running on the device

## Setup

1. Clone and install:
   ```bash
   git clone https://github.com/kylegmaxwell/SmashSMS.git
   cd SmashSMS
   ./gradlew app:installDebug
   ```
2. Start **Shizuku** (Wireless Debugging, ADB, or root).
3. Open **SmashSMS** and complete the setup cards on the main screen:
   - **SMS & Contacts Access** (`RECEIVE_SMS`, `READ_SMS`, `READ_CONTACTS`)
   - **Notification Listener** (enable **SmashSMS Spam Filter** in system settings; on sideloaded Android 14+ builds, enable *Allow restricted settings* in App Info first if grayed out)
   - **Shizuku Access** (tap **Authorize**)
   - **Silence Google Messages** (recommended: set Google Messages' *Incoming messages* channel to **Silent** so SmashSMS controls sound/vibration and spam never chimes)

## Tests

```bash
./gradlew app:testDebugUnitTest
```
