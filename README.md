# App Manager

A simple Android app manager built with Kotlin.

## Features
- Lists installed launcher applications
- Search by app name or package name
- Select multiple apps
- Opens Android App Info for manual Force Stop / Clear Cache actions

## Android limitation
A normal, non-root third-party app cannot silently force-stop another app or clear its private cache. This app opens the system App Info screen so the user can perform the action.

## Build locally
Open this folder in Android Studio and build the `app` module, or run:

```bash
gradle assembleDebug
```

## GitHub Actions
Every push to `main` or `master` builds a debug APK and uploads it as an Actions artifact.

To publish a GitHub Release:
1. Push your changes.
2. Create and push a version tag, for example `v1.0.1`.
3. GitHub Actions builds the APK and attaches it to a GitHub Release.

Example:

```bash
git tag v1.0.1
git push origin v1.0.1
```

The generated debug APK is signed with Android's debug signing key and can be installed for testing. For a production Play Store release, configure a private release signing key separately.
