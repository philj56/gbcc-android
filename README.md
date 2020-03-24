# [GBCC Android](https://gbcc.github.io)
This is the android front-end to [GBCC](https://gbcc.github.io), a
cross-platform Game Boy and Game Boy Color emulator written in C with a focus
on accuracy. See the [main repository](https://github.com/philj56/gbcc) for
details.

## Install
You can get gbcc on [Google Play](https://play.google.com/store/apps/details?id=com.philj56.gbcc).

### Prebuilt packages ![](https://github.com/philj56/gbcc-android/workflows/Build%20APK/badge.svg)
Debug packages are generated on each commit. To download them, navigate to the
[actions](https://github.com/philj56/gbcc-android/actions) tab (you'll need to
be logged in to GitHub to do this). Select the latest "Build Packages" job that
succeeded, and look for the artifacts dropdown.

### From source
I'll write up instructions on this at some point, but it's pretty much just
install the Android SDK & NDK, clone this repo recursively, and then run

```sh
./gradlew build
```

This will generate the apks in `app/build/outputs/apk/`.
