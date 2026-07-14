# Release Setup — GitHub Secrets

These secrets are required for the CI release workflow (`.github/workflows/release.yml`).
Add them at **Settings → Secrets and variables → Actions** in your GitHub repository.

---

## 1. Android Keystore (`KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`)

### Generate a keystore (if you don't have one)

```bash
keytool -genkey -v -keystore release.jks -keyalg RSA -keysize 2048 \
  -validity 10000 -alias release -storepass YourStorePass -keypass YourKeyPass
```

This creates a `release.jks` file.  
Fill in your details when prompted (name, org, city, etc — anything works).

### If you already have a keystore

Base64-encode it:

```bash
base64 -w0 /path/to/your/release.jks
```

List aliases to find yours (and verify password):

```bash
keytool -list -keystore /path/to/your/release.jks -storepass YourStorePass
```

Expected output:
```
release, Jan 15, 2025, PrivateKeyEntry, Certificate fingerprint (SHA-256): ...
```
The alias is `release` in this example.

### Generate a keystore (if you don't have one)

```bash
keytool -genkey -v -keystore release.jks -keyalg RSA -keysize 2048 \
  -validity 10000 -alias release -storepass YourStorePass -keypass YourKeyPass
```

Fill in your details when prompted (name, org, city, etc — anything works).

### Base64-encode the keystore

```bash
base64 -w0 release.jks
```

Copy the output — this is your `KEYSTORE_BASE64`.

### Set the secrets

| Secret | Value |
|--------|-------|
| `KEYSTORE_BASE64` | Base64 of `release.jks` (from above) — binary file encoded as text for GitHub Secrets |
| `KEYSTORE_PASSWORD` | The `-storepass` you used — unlocks the keystore file |
| `KEY_ALIAS` | The alias name (e.g. `release`) — selects which key inside the keystore to use |
| `KEY_PASSWORD` | The `-keypass` you used — unlocks the specific key (often same as store password) |

**Why this works:** The CI workflow decodes the base64 back to a `.jks` file, then passes these values to Android's signing config (`storeFile`, `storePassword`, `keyAlias`, `keyPassword`) so Gradle can sign the APK. Without them, the APK would be signed with the debug key, which is not suitable for distribution.

---

## 2. GPG Signing Key (`GPG_PRIVATE_KEY`, `GPG_PASSPHRASE`)

Used to sign the APK checksums so users can verify authenticity.

### Generate a GPG key

```bash
gpg --full-generate-key
```

- Kind: **RSA and RSA**
- Size: **4096 bits**
- Expiry: **0** (no expiry)
- Real name / Email: anything (e.g. `yourname <your@email.com>`)
- Passphrase: **remember this — you'll need it**

### If you already have a GPG key

List your keys to find the key ID or email:

```bash
gpg --list-secret-keys
```

Export the private key (ASCII-armored):

```bash
gpg --armor --export-secret-key your@email.com
```

If you forgot the passphrase, there is no recovery — generate a new key.

### Generate a GPG key (if you don't have one)

```bash
gpg --full-generate-key
```

- Kind: **RSA and RSA**
- Size: **4096 bits**
- Expiry: **0** (no expiry)
- Real name / Email: anything (e.g. `yourname <your@email.com>`)
- Passphrase: **remember this — you'll need it**

### Export the private key (ASCII-armored)

```bash
gpg --armor --export-secret-key your@email.com
```

Copy the output (including `-----BEGIN PGP PRIVATE KEY BLOCK-----` and `-----END PGP PRIVATE KEY BLOCK-----`).

### Set the secrets

| Secret | Value |
|--------|-------|
| `GPG_PRIVATE_KEY` | Full armored private key output (multi-line) — imported by CI to sign checksum files |
| `GPG_PASSPHRASE` | The passphrase you set during key generation — unlocks the private key for signing |

**Why this works:** The CI imports the GPG key, generates SHA-256 checksums for each APK, then creates `.sig` files by signing those checksums with your private key. Users can verify the APK integrity with `gpg --verify`. Optional — the build succeeds without it, but checksums won't be signed.

---

## Auto-generated secret

| Secret | Notes |
|--------|-------|
| `GITHUB_TOKEN` | Automatically provided by GitHub Actions — no setup needed. |

---

## Verifying it works

Once all secrets are set, push a `v*` tag:

```bash
git tag v1.0.0 && git push origin v1.0.0
```

The release workflow will trigger automatically. Check its progress under your repo's **Actions** tab.
