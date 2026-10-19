# Legato

A local music player for Android, built for one person. Black and ivory, like Before and Tsundoku.

## Build and install

```sh
export JAVA_HOME=/home/phuuun/Projects/Tools/android-studio/jbr
./gradlew :app:testDebugUnitTest     # JVM tests (Robolectric)
./gradlew :app:installDebug          # build and install on the connected phone
```

adb lives at `/home/phuuun/Projects/Tools/Android/Sdk/platform-tools/adb`; the phone is `943f15`.
Never run `connectedDebugAndroidTest`: it uninstalls the app (and its playlists). Use `installDebug` / `adb install -r`.

Before a release, walk [docs/device-checklist.md](docs/device-checklist.md).
