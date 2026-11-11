# Toridame for Android TV

The Android TV app for **Toridame**, a personal fork of [Jellyfin](https://github.com/jellyfin/jellyfin) 12.4. It is a fork of the official [Jellyfin for Android TV](https://github.com/jellyfin/jellyfin-androidtv) app. It is not affiliated with or endorsed by the Jellyfin project.

It works with any Jellyfin server, and it is meant to be used with the [Toridame server](https://github.com/jt-ito/Toridame) and [Toridame Web](https://github.com/jt-ito/Toridame-web). Features that need the Toridame server are marked below, and they simply stay out of the way on other servers.

So far it has been tried in the Android TV emulator. Expect rough edges on real hardware.

## What's different from the official app

**A side menu instead of a toolbar**
- A menu at the left edge of every screen except the player. It folds to icons and opens when you go left from the first item of a row or grid, and it closes when you go back right.
- It holds your account at the top, then Home, Search, Random title, Catch up, Now playing (while something plays) and Preferences, with the clock in the top right corner. It opens on the item of the page you are on.
- Everything is drawn from the theme colors, so a different theme restyles it.

**Accounts and start-up screens**
- An **account switcher**: change to another saved account without going through the sign-in screen. Hold an account to forget it.
- "Who's watching?", server selection, server address and sign-in screens redrawn: centered, large round tiles, a dark background.

**Home**
- A header above the rows with the title, details and a short overview of what is focused (and a greeting and the latest additions for libraries). It keeps the same height, so the page does not jump as you move.
- **Continue watching and Next up** in one row, and a **Favorites** row.
- **Sort options** in libraries, including year and play count, with the direction remembered.
- **Random title** and **Catch up** (go through titles you have not watched and open, skip, hide, add to the watchlist or mark each one).

**In the player**
- **Chapters on the seek bar**, with the name of the chapter above it, and bookmarks as marks on the bar.
- Buttons for the **next chapter** and for **bookmarks**, and a **settings button** that holds audio, chapters, bookmarks, speed, quality and zoom.
- The episode list keeps the focus (left and right never leave it, and one press of up or down closes it), and also lists the folder, the queue, collections and versions of what is playing.
- Faster seeking and chapter jumping (quick presses are combined into one jump), and seeking while paused.
- **Hold for 2x speed**, **remappable remote shortcuts** and a **pause screen** with what you are watching.
- **Spoiler guard** for episode details you have not reached.

**From the Toridame server**
- External ratings (IMDb, Rotten Tomatoes and others), where to watch, and TMDb reviews on title pages.
- **Hidden titles** (with an optional PIN) and **private libraries**.
- Badges on cards for the picture size, the age rating and the score.
- The **maintenance banner and lock-out**, and the household **activity feed**.
- Intro and credits segments, once the server has detected them (it is off until turned on per library), show the usual skip prompts.

**Preferences**
- Regrouped under Account, App, Content and privacy, and Help, with the pages the app adds (remote shortcuts, badges, hidden titles, private libraries) reachable from the first page.

**Speed**
- Less logging in release builds, lighter images (WebP), a device profile that is worked out once, and a baseline profile for faster start-up.

The home header and the merged home rows are inspired by [Wholphin](https://github.com/damontecres/Wholphin), another Android TV client for Jellyfin.

## Building

You need JDK 21 and the Android SDK (point `sdk.dir` in `local.properties` at it).

```bash
./gradlew :app:assembleDebug
```

For a release build, which is minified and needs to be signed:

```bash
./gradlew :app:assembleRelease
```

Signing reads `KEYSTORE_FILE`, `KEYSTORE_PASSWORD`, `SIGNING_KEY_ALIAS` and `SIGNING_KEY_PASSWORD` from the environment (or the same names as Gradle properties, such as `keystore.file`). Without them the release build is left unsigned. The APK ends up in `app/build/outputs/apk/`.

To install it on a TV or an emulator:

```bash
adb install -r app/build/outputs/apk/release/jellyfin-androidtv-v0.0.0-dev.1-release.apk
```

The emulator needs an Android TV system image. The release build includes libraries for arm, arm64, x86 and x86_64.

## Staying close to upstream

The history is the official app's, with this fork's changes on top, so `git diff` against the `upstream` remote shows what changed. The official repository is `https://github.com/jellyfin/jellyfin-androidtv`.

## License

GPL-2.0, as for Jellyfin. See [LICENSE](LICENSE). Jellyfin is a trademark of its respective owners.
