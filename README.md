# Cache Cleaner

Force stop apps and clear their cache, with cache sizes shown per app.

- **Shizuku mode (recommended):** start Shizuku, tap Grant. Runs `am force-stop` and `pm clear --cache-only` directly.
- **Accessibility mode:** no Shizuku needed; the app taps the buttons in system Settings (English labels).
- **Cache sizes:** grant "Usage access" once.
- Theme follows the system; use the menu to switch System / Light / Dark.

Build: push to `main`; GitHub Actions produces `app-debug.apk` under Actions → the run → Artifacts.
