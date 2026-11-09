# CameraRoll

A native Google Photos clone for Android, written in Kotlin with Jetpack
Compose. No Google Cloud involved: backups and sync go to your own Nextcloud
or OpenCloud server over WebDAV.

## Features

- Timeline of photos and videos grouped by day ("Today", "Yesterday", dates
  in Spanish).
- Memories: strips of photos from this same day in previous years.
- Fullscreen viewer with a pager, looping video, and an info panel (date,
  size, dimensions, duration, cloud backup state).
- Multiple selection (long press), favorites, share, trash.
- Albums: create, rename, delete, add and remove photos.
- Trash with restore and automatic 30-day purge.
- Search by file name with filters (all / photos / videos).
- Import from the gallery (visual multi-select) with EXIF date, dimensions
  and locally generated WebP thumbnails.
- Home screen widgets (Glance): "Memories" and "Featured photo".
- Nextcloud/OpenCloud sync:
  - Settings for URL, user, app password and folder.
  - Accepts a root URL or a full WebDAV endpoint (`.../remote.php/dav/files/user`).
  - Push uploads new files to `/Fotos/YYYY/MM/`. Pull imports files added on
    the server.
  - Periodic sync every 6 hours with WorkManager (foreground service), plus a
    "Sync now" button and "Test connection".
- Light and dark theme following the system (Material 3).

## Build

Requirements: JDK 17, Android SDK 34 (platform and build-tools 34.0.0).

```sh
./gradlew :app:assembleDebug
./gradlew :app:assembleRelease
```

The release build is signed only when the `CAMERAROLL_KEYSTORE` environment
variables are set. Point `CAMERAROLL_KEYSTORE` at a keystore file and set
`CAMERAROLL_KEYSTORE_PASSWORD`, `CAMERAROLL_KEY_ALIAS` and
`CAMERAROLL_KEY_PASSWORD`. No keystore ships in this repository.

minSdk 26 (Android 8.0+) and targetSdk 34.

## Structure

```
app/src/main/java/app/cameraroll/
├── CameraRollApp.kt         # Application: trash purge, periodic sync
├── data/
│   ├── db/Entities.kt       # Room: photos, albums, cross-refs, DAOs
│   ├── CloudSettings.kt     # DataStore: Nextcloud/OpenCloud account
│   └── PhotoRepository.kt   # import, EXIF, thumbnails, trash
├── webdav/DavClient.kt      # minimal WebDAV over OkHttp (PROPFIND/PUT/GET/MKCOL/DELETE)
├── sync/
│   ├── SyncEngine.kt        # bidirectional push/pull
│   └── SyncWorker.kt        # periodic WorkManager + foreground
├── ui/
│   ├── MainActivity.kt      # permissions + entry
│   ├── Navigation.kt        # NavHost + bottom bar
│   ├── PhotosViewModel.kt   # state (StateFlow over Room)
│   ├── photos/              # timeline, memories, viewer, favorites, trash
│   ├── albums/              # album list and detail
│   ├── search/              # search
│   ├── settings/            # cloud, storage, about
│   └── components/          # grid with day headers
└── widget/Widgets.kt        # Glance widgets: Memories and Featured photo
```

## Privacy

Files live in the app's internal storage and, if you configure it, on your
server. There are no calls to Google services.

## License

GNU AGPL-3.0. See [LICENSE](LICENSE).
