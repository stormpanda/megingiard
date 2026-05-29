# Megingiard Security Concept

This document is the entry point for Megingiard's security model. It summarizes the current hardening layers and links to the detailed feature, architecture, and build documentation.

## Scope and Threat Model

Megingiard is a local, device-specific companion app for the AYN Thor. The strongest trust boundary is the Android app sandbox: the normal app process runs as an untrusted app, while Privileged Mode starts a helper daemon under the shell UID through ADB Wireless Debugging.

The current security concept focuses on these threats:

- Repackaged APKs signed by an attacker.
- Native helper binaries or mirror DEX assets swapped inside the APK or during deployment.
- A rogue process claiming the `@megingiard.privd` abstract socket before the real daemon.
- A rogue app connecting to the real daemon socket and sending privileged commands.
- A public APK being decompiled to extract authentication secrets.

It does not claim DRM, copy protection, network hardening, or resistance against a fully compromised device / root attacker.

## Security Layers

| Layer                             | Purpose                                                                                       | Implementation                                                                                                                                                                                                                    | Detailed docs                                                                                                                         |
| --------------------------------- | --------------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------- |
| APK signature pinning             | Detect repackaged APKs signed with an unexpected certificate.                                 | `SignatureGuard.verify()` compares runtime signing cert SHA-256 values with `BuildConfig.EXPECTED_SIGNING_SHA256`. Release builds fail closed when the pin is missing or malformed.                                               | [Architecture](docs/ARCHITECTURE.md#security-architecture), [Requirements](docs/REQUIREMENTS.md#security)                             |
| Release obfuscation and shrinking | Raise the reverse-engineering cost and reduce shipped surface.                                | Release builds enable R8 minification and resource shrinking; keep rules preserve manifest components and serialization.                                                                                                          | [Architecture](docs/ARCHITECTURE.md#security-architecture), [app/proguard-rules.pro](app/proguard-rules.pro)                          |
| Native asset SHA-256 pins         | Detect modified helper binaries and mirror DEX assets before use.                             | `:domain:generateNativeBinaryHashes` generates `NativeBinaryHashes.EXPECTED`; `BinaryIntegrity.verify()` checks assets fail-closed.                                                                                               | [Build Native](docs/BUILD_NATIVE.md#native-asset-integrity), [Requirements](docs/REQUIREMENTS.md#security)                            |
| Pre-exec TOCTOU mitigation        | Ensure the bytes verified in memory are the bytes made executable.                            | `NativeBinaryInjector` verifies assets before writing, re-reads and re-verifies after writing, then sets executable and non-writable bits.                                                                                        | [Architecture](docs/ARCHITECTURE.md#security-architecture), [Build Native](docs/BUILD_NATIVE.md#runtime-verification)                 |
| ADB bootstrap integrity           | Avoid pushing tampered daemon / mirror assets during Privileged Mode setup.                   | `PrivdBootstrapper` verifies `megingiard_privd_arm64` and `megingiard_mirror.dex` before ADB sync push.                                                                                                                           | [Privileged Mode](docs/features/privileged-mode/FEATURE.md#security-model), [Build Native](docs/BUILD_NATIVE.md#runtime-verification) |
| Privd mutual HMAC authentication  | Authenticate both app and daemon before privileged commands are accepted.                     | `CHAL/AUTH/OK/VERIFY/PROOF` over `LocalSocket`, using HMAC-SHA256 with a per-install 32-byte key encrypted at rest in Android Keystore (AES-256-GCM). The key is never embedded in the APK. See **Per-install key scheme** below. | [Privileged Mode](docs/features/privileged-mode/FEATURE.md#security-model)                                                            |
| OS-level peer credential checks   | Enforce that only the legitimate peer can initiate a connection before any protocol exchange. | App side: `LocalSocket.peerCredentials.uid` must equal 2000 (shell). Daemon side: `getsockopt(SO_PEERCRED)` must match the provisioned app UID. If either check fails, the connection is closed before the HMAC handshake.        | [Privileged Mode](docs/features/privileged-mode/FEATURE.md#security-model)                                                            |
| Security logging                  | Make integrity and authentication failures visible in normal diagnostic logs.                 | Security failures use `AppLog.w()` / `AppLog.e()` in `SignatureGuard`, `BinaryIntegrity`, and `PrivdClient`.                                                                                                                      | [AGENTS.md](AGENTS.md#84-logging)                                                                                                     |
| Config export checksums           | Detect accidental or unintended modification of exported user configuration files.            | Export/import embeds and verifies a SHA-256 checksum over canonical serialized content.                                                                                                                                           | [Config Feature](docs/features/config/FEATURE.md)                                                                                     |

## Configuration Keys

Security-sensitive build values are read from `local.properties`:

| Key                         | Meaning                                                                                                           | Required for distribution                                                                         |
| --------------------------- | ----------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------- |
| `megingiard.signing.sha256` | SHA-256 fingerprint of the expected APK signing certificate. Injected into `BuildConfig.EXPECTED_SIGNING_SHA256`. | Yes. Release builds abort if missing or malformed unless explicitly overridden for local testing. |

## Per-install Key Scheme

The previous scheme embedded the Privd HMAC key in `BuildConfig` at compile time. Anyone who downloads the public APK can extract that key via decompilation — without any device access. The current scheme eliminates this weakness.

**How it works:**

1. **Key generation** — During Privileged Mode bootstrap the app generates 32 random bytes with `SecureRandom`. This key is unique per install and never appears in the APK.
2. **Keystore encryption** — The key is encrypted under an AES-256-GCM key that lives in Android Keystore (`megingiard_privd_pair_key_v1`, hardware-backed when the device has a TEE or StrongBox). The ciphertext is stored in `noBackupFilesDir/privd_pair_key.enc`.
3. **Provisioning** — The app transmits the plaintext key to the daemon over the already-authenticated ADB TLS channel: `megingiard_privd --provision <key_hex> <app_uid>`. The daemon writes it to `/data/local/tmp/megingiard_privd.key` (mode 0600, shell-owned). The app UID is stored alongside the key so the daemon can enforce peer identity.
4. **Usage** — On subsequent app starts the app decrypts the key from Keystore storage (`PrivdPairKey.load()`) and loads it into `PrivdClient`. The daemon reads it from the state file at startup. The existing mutual `CHAL/AUTH/OK/VERIFY/PROOF` handshake then uses this per-install key.
5. **Uninstall invalidation** — Android destroys the Keystore AES key when the app is uninstalled. The ciphertext in `noBackupFilesDir` therefore becomes permanently unreadable after reinstall, forcing the user to re-run the setup wizard. A reinstalled app (potentially from a different source) cannot silently inherit the old daemon's trust relationship.

**Attack vectors covered by this scheme:**

| Scenario                                                   | Outcome                                                                      |
| ---------------------------------------------------------- | ---------------------------------------------------------------------------- |
| Public APK decompiled by an attacker                       | No key in APK — decompilation yields nothing useful                          |
| Rogue app tries to connect to the daemon                   | Daemon `SO_PEERCRED` check rejects wrong UID before handshake                |
| Rogue process binds the socket first, impersonating daemon | App `LocalSocket.peerCredentials.uid` check rejects non-shell server         |
| App reinstalled (possibly from a different source)         | Keystore entry destroyed — cannot decrypt stored key — re-bootstrap required |
| Daemon binary from old install serves connections          | Old daemon has no state file → refuses to start                              |

**Security property:** Compromising only the app (without the device's Keystore) or only the daemon binary (without the state file) does not grant access to the other party's secrets.

## Verification and Tests

The current automated coverage focuses on pure cryptographic primitives:

- `BinaryIntegrityTest` covers SHA-256 known-answer vectors and sensitivity to byte changes.
- `HmacUtilTest` covers HMAC-SHA256 RFC 4231 vectors and the constant-time hex MAC comparison used for daemon `PROOF` verification.
- Agents must run `./gradlew :core:test :domain:test` after implementation changes that affect pure `:core` or `:domain` logic.
- Native C source changes must be followed by the matching build script; see [Build Native](docs/BUILD_NATIVE.md#native-rebuild-policy).

Runtime Android branches such as `SignatureGuard.verify()` and the full `LocalSocket` handshake currently require manual device validation or future Robolectric / instrumentation coverage.

## Residual Risks and Future Hardening

- HMAC key rotation and signing-certificate rotation are manual re-bootstrap / redeploy operations today.
- A fully compromised / rooted device can patch code, binaries, memory, or filesystem contents outside the assumptions of this model.
- Socket parser fuzzing and end-to-end tamper tests are not yet part of automated CI.

## Documentation Map

- [Requirements](docs/REQUIREMENTS.md#security) defines the non-functional security requirements.
- [Architecture](docs/ARCHITECTURE.md#security-architecture) explains the system-level security layering.
- [Privileged Mode](docs/features/privileged-mode/FEATURE.md#security-model) documents the daemon trust boundary and mutual HMAC protocol.
- [Build Native](docs/BUILD_NATIVE.md#native-asset-integrity) documents native rebuilds, hash generation, and runtime verification.
- [Config Feature](docs/features/config/FEATURE.md) documents export/import data-integrity checks.
- [Agent Guidelines](AGENTS.md#3-checklist-for-every-change) define required rebuild and test workflow for agents.
