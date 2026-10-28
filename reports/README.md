# Test reports

This folder holds anonymous test reports uploaded by the GeoCam app. They help find and fix problems on different phones. This folder is only documentation and data. It is **not** part of the APK.

## How a report is created

In the app: **Settings > Debug > Run all tests**, then tap **Upload report (anonymous)**. Nothing is uploaded unless you tap that button or switch on **Upload report after tests**.

## What is in a report

- Phone make and model, Android version, and app version
- Screen size and display density
- Results of each test: PASS, FAIL, WARN or SKIP, with a short reason
- Camera abilities (zoom range, flash, exposure range) and satellite counts per system
- The last few app error messages

## What is never included

- Location, coordinates or addresses
- Your name, phone number, email or account details
- Photos or any files from your phone
- API keys

Coordinates, email addresses and keys are removed twice: once in the app before sending, and again on the receiving server.

## Folder layout

```
reports/
  2026-10-27/
    Google-Pixel-8-Pro_A15_20261027103015_a1b2c3.txt
```

- One folder per day (UTC date)
- File name: phone model, `A` plus the Android version, upload time (UTC, `YYYYMMDDHHMMSS`) and a short random code

## Reading a report

Each line starts with the result:

| Result | Meaning |
| --- | --- |
| `PASS` | The function works |
| `FAIL` | The function does not work. The reason follows |
| `WARN` | It works but something is not ideal, for example weak GPS accuracy |
| `SKIP` | Not tested on this phone, for example no flash on the front camera |

At the end of each report is a list of things that must be checked by hand, and the stored error messages.

## Opting out and removal

Uploading is always optional. If you uploaded a report and want it removed, open an issue with the file name and it will be deleted.

Reports are public because this repository is public.
