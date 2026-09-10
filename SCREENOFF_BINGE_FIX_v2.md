# NovelRegEx v0.1 — screen-off binge playback

The former hidden-WebView chapter preloader has been removed. The app now requests the next chapter only after the current chapter finishes and uses the visible WebView for that navigation.

Current behavior:
- Keep PARTIAL_WAKE_LOCK during playback and chapter transition.
- Keep a Wi-Fi lock during playback/transition to reduce Wi-Fi sleep during screen-off playback.
- Continue narrating the current chapter while the screen is off.
- Defer next-chapter loading until the device becomes interactive when a chapter ends with the screen off.
- Preserve the visible-WebView next-chapter navigation and bounded reload watchdog.
- Do not change the working TTS engine/API path.
