<img width="1280" height="640" alt="social-preview" src="https://github.com/user-attachments/assets/300a028a-fba9-449e-b312-97baf70c8042" />


# GeoCam – Open-Source GPS Camera for Android

**GeoCam is a free, open-source geotag camera app for Android.** Every photo is stamped with your live GPS location, full address, coordinates, date and time, and a small map, so you can prove where and when a photo was taken. It also shows how many satellites your phone is using (GPS, GLONASS, Galileo, BeiDou, QZSS, SBAS and NavIC).

Useful for site surveys, inspections, field work, solar and electrical installations, construction progress photos, deliveries and attendance proof.

[**Download the latest APK**](https://github.com/uzairtanveer12/geocam/releases/latest) · Android 14 and newer

## Features

- **Location stamp on every photo:** place name, full address, latitude and longitude, date, time and timezone, an optional note, and a map thumbnail
- **Live preview card:** the address, coordinates, accuracy and satellite count are shown on the camera screen while you frame the photo
- **Satellite information:** satellites in use, per-system counts, signal strength and dual-frequency (L5) status
- **GPS first, with mobile network location as a backup**
- **Real lens zoom:** 0.5x, 1x, 2x, 5x and more on phones that expose their lenses, plus pinch to zoom (built on CameraX)
- **Camera controls:** flash (off, auto, on, torch), tap to focus, exposure slider, grid lines, timer (3 s / 10 s) and photo quality
- **Saves to your Gallery** in `Pictures/GeoCam`, with share and open-in-maps buttons
- **Photos taken sideways are saved in landscape**; icons rotate with the phone
- **Battery friendly:** GPS and camera stop when the app is in the background
- Optional Google Maps API key for Google address and map data (OpenStreetMap is used by default)

## Install

1. Open the [Releases](https://github.com/uzairtanveer12/geocam/releases) page.
2. Download the latest `GeoCam-*.apk` file to your phone.
3. Open it and allow "install unknown apps" if Android asks.
4. Allow **Camera** and **Location** when the app asks.

## Privacy

GeoCam has no account, no ads and no analytics. Photos and settings stay on your phone.

To find the address and draw the map, the app sends your current coordinates to a map service:

- **OpenStreetMap** (Nominatim and map tiles) by default
- **Google Maps** (Geocoding and Static Maps) only if you enter your own API key in Settings

Your Google API key is stored only on your phone.

## Build it yourself

The project is a web app (`www/index.html`) wrapped with [Capacitor](https://capacitorjs.com/), plus small native Android plugins in `native/` for the camera (CameraX), satellite data (GnssStatus), and saving or sharing photos.

The APK is built automatically by GitHub Actions:

1. Fork this repository.
2. Open the **Actions** tab and run **Build APK**, or publish a release with a tag that starts with `v`.
3. Download `GeoCam-*.apk` from the run or the release page.

To build locally you need Node.js 20, Java 17 and the Android SDK: run `npm install`, `npx cap add android`, copy the files from `native/` into the generated Android project, then build with Gradle. The steps are in `.github/workflows/build-apk.yml`.

## Project structure

| Path | What it is |
| --- | --- |
| `www/index.html` | The app interface and logic |
| `native/` | Android plugins: camera, satellites, gallery and share |
| `assets/` | App icon images |
| `.github/workflows/build-apk.yml` | Automatic APK build |

## Contributing

Bug reports, ideas and pull requests are welcome. If you report a problem, please include your phone model, Android version and the text shown at the bottom of the app's Settings screen.

## Credits

Map data © [OpenStreetMap](https://www.openstreetmap.org/copyright) contributors. Built with [Capacitor](https://capacitorjs.com/) and [AndroidX CameraX](https://developer.android.com/media/camera/camerax).

## License

[MIT](LICENSE) © 2026 Muhammad Uzair Tanveer
