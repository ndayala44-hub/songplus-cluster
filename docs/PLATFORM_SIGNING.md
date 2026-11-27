# Platform-key signing (the legitimate route to BYDAUTO data)

The `BYDAUTO_*_GET` permissions that unlock speed, energy, gear, lights etc. are **signature-level**. Android grants a
signature permission only to an app signed with the *same certificate* that the component which declared the permission
is signed with — here, the certificate the head unit trusts as the "platform" key. There is no runtime dialog and no
`pm grant` that can substitute for this (your probe showed exactly that: the `_GET` entries are `signature` and stay
"permission not granted").

This is **not** a bypass. We are not making `checkSelfPermission` lie. We sign the APK with the platform certificate so
the OS performs its normal signature check and grants the permission for real. That is the designed mechanism for
signature permissions.

## Will it work on the Song Plus?

Maybe not. Two independent sources, plus your own probe, warn that **some DiLink units deny `BYDAUTO_*` at the platform
level regardless of signing**. If your ROM was built with a *private* BYD key, the public AOSP test key below will not
match and the permissions will stay denied. Many cheap DiLink 3.0 ROMs are built with AOSP **test-keys**, in which case
it works. The only way to know is to try. If it doesn't light up, use **OBD2** for real speed.

This step also makes the app a platform-signed app (it would hold *all* signature permissions it declares). Only install
it on your own vehicle.

## Step 1 — get a platform keystore

You need the certificate your ROM trusts. For a test-keys ROM, that is the public AOSP platform key
(`platform.x509.pem` + `platform.pk8`, published in AOSP under `build/target/product/security/`). Convert the PEM/PK8
pair into a Java keystore:

```bash
# produce a PKCS#12, then a JKS the Gradle signingConfig can read
openssl pkcs8 -inform DER -nocrypt -in platform.pk8 -out platform.key.pem
openssl pkcs12 -export -in platform.x509.pem -inkey platform.key.pem \
  -name platform -out platform.p12 -password pass:android
keytool -importkeystore -srckeystore platform.p12 -srcstoretype PKCS12 -srcstorepass android \
  -destkeystore platform.keystore -deststoretype PKCS12 -deststorepass android -destalias platform
```

If instead you extracted a key from your own unit's ROM, use that pair in the same commands.

## Step 2a — build locally

```bash
./gradlew assembleDebug \
  -PPLATFORM_KEYSTORE=/absolute/path/platform.keystore \
  -PPLATFORM_KS_PASSWORD=android -PPLATFORM_KEY_ALIAS=platform -PPLATFORM_KEY_PASSWORD=android
```

When `PLATFORM_KEYSTORE` is absent the build is unchanged (normal debug key; OBD2 + simulation still work). When it is
present, both debug and release are signed with it.

## Step 2b — build in GitHub Actions

Add repository secrets:

- `PLATFORM_KEYSTORE_B64` — `base64 -w0 platform.keystore`
- `PLATFORM_KS_PASSWORD`, `PLATFORM_KEY_ALIAS`, `PLATFORM_KEY_PASSWORD`

Then, in the build job, before the Gradle step, decode it and pass the path:

```yaml
- name: Restore platform keystore
  if: ${{ secrets.PLATFORM_KEYSTORE_B64 != '' }}
  run: echo "${{ secrets.PLATFORM_KEYSTORE_B64 }}" | base64 -d > "$RUNNER_TEMP/platform.keystore"
- name: Build
  run: ./gradlew assembleDebug
  env:
    PLATFORM_KEYSTORE: ${{ secrets.PLATFORM_KEYSTORE_B64 != '' && format('{0}/platform.keystore', runner.temp) || '' }}
    PLATFORM_KS_PASSWORD: ${{ secrets.PLATFORM_KS_PASSWORD }}
    PLATFORM_KEY_ALIAS: ${{ secrets.PLATFORM_KEY_ALIAS }}
    PLATFORM_KEY_PASSWORD: ${{ secrets.PLATFORM_KEY_PASSWORD }}
```

Never commit the keystore itself.

## Step 3 — install and check

1. Uninstall the previous app first (changing the signing key blocks an in-place update).
2. Install the new APK.
3. On a test-keys ROM the BYDAUTO permissions are typically pre-granted at install. Open **Settings → Vehicle
   integration → Probe BYD permissions**: if the `_GET` entries now read *permission granted*, set **Data mode → BYD
   only** and the cluster shows real data.
4. If they still read *permission not granted*, this unit denies them regardless of signing. Stop here and use **OBD2**.

## What this does not include

No `BydPermissionContext` shim and no ADB/`app_process` shell-identity helper. Both defeat the permission from outside
the signature system rather than satisfying it, and neither is in this project.
