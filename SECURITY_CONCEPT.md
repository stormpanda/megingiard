# Megingiard Security Concept

This document is the entry point for Megingiard's security model. It summarizes the current hardening layers and links to the detailed feature, architecture, and build documentation.

## Scope and Threat Model

Megingiard is a local, device-specific companion app for the AYN Thor. The strongest trust boundary is the Android app sandbox: the normal app process runs as an untrusted app, while Privileged Mode starts a helper daemon under the shell UID through ADB Wireless Debugging.

The current security concept focuses on these threats:

- Repackaged APKs signed by an attacker.
- Native helper binaries or mirror DEX assets swapped inside the APK or during deployment.
- A rogue process claiming the `@megingiard.privd` abstract socket before the real daemon.
- A rogue app connecting to the real daemon socket and sending privileged commands.
- Accidental release builds without production signing or Privd HMAC configuration.

It does not claim DRM, copy protection, network hardening, or resistance against a fully compromised device / root attacker.

## Security Layers

| Layer                             | Purpose                                                                            | Implementation                                                                                                                                                           | Detailed docs                                                                                                                         |
| --------------------------------- | ---------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------ | ------------------------------------------------------------------------------------------------------------------------------------- |
| APK signature pinning             | Detect repackaged APKs signed with an unexpected certificate.                      | `SignatureGuard.verify()` compares runtime signing cert SHA-256 values with `BuildConfig.EXPECTED_SIGNING_SHA256`. Release builds fail closed when no pin is configured. | [Architecture](docs/ARCHITECTURE.md#security-architecture), [Requirements](docs/REQUIREMENTS.md#security)                             |
| Release obfuscation and shrinking | Raise the reverse-engineering cost and reduce shipped surface.                     | Release builds enable R8 minification and resource shrinking; keep rules preserve manifest components and serialization.                                                 | [Architecture](docs/ARCHITECTURE.md#security-architecture), [app/proguard-rules.pro](app/proguard-rules.pro)                          |
| Native asset SHA-256 pins         | Detect modified helper binaries and mirror DEX assets before use.                  | `:domain:generateNativeBinaryHashes` generates `NativeBinaryHashes.EXPECTED`; `BinaryIntegrity.verify()` checks assets fail-closed.                                      | [Build Native](docs/BUILD_NATIVE.md#native-asset-integrity), [Requirements](docs/REQUIREMENTS.md#security)                            |
| Pre-exec TOCTOU mitigation        | Ensure the bytes verified in memory are the bytes made executable.                 | `NativeBinaryInjector` verifies assets before writing, re-reads and re-verifies after writing, then sets executable and non-writable bits.                               | [Architecture](docs/ARCHITECTURE.md#security-architecture), [Build Native](docs/BUILD_NATIVE.md#runtime-verification)                 |
| ADB bootstrap integrity           | Avoid pushing tampered daemon / mirror assets during Privileged Mode setup.        | `PrivdBootstrapper` verifies `megingiard_privd_arm64` and `megingiard_mirror.dex` before ADB sync push.                                                                  | [Privileged Mode](docs/features/privileged-mode/FEATURE.md#security-model), [Build Native](docs/BUILD_NATIVE.md#runtime-verification) |
| Privd mutual HMAC authentication  | Authenticate both app and daemon before privileged commands are accepted.          | `CHAL/AUTH/OK/VERIFY/PROOF` over `LocalSocket`, using HMAC-SHA256 with a shared 32-byte key.                                                                             | [Privileged Mode](docs/features/privileged-mode/FEATURE.md#security-model)                                                            |
| Security logging                  | Make integrity and authentication failures visible in normal diagnostic logs.      | Security failures use `AppLog.w()` / `AppLog.e()` in `SignatureGuard`, `BinaryIntegrity`, and `PrivdClient`.                                                             | [AGENTS.md](AGENTS.md#54-logging)                                                                                                     |
| Config export checksums           | Detect accidental or unintended modification of exported user configuration files. | Export/import embeds and verifies a SHA-256 checksum over canonical serialized content.                                                                                  | [Config Feature](docs/features/config/FEATURE.md)                                                                                     |

## Configuration Keys

Security-sensitive build values are read from `local.properties`:

| Key                         | Meaning                                                                                                                                                                      | Required for distribution                                                            |
| --------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------ |
| `megingiard.signing.sha256` | SHA-256 fingerprint of the expected APK signing certificate. Injected into `BuildConfig.EXPECTED_SIGNING_SHA256`.                                                            | Yes. Release builds abort if missing unless explicitly overridden for local testing. |
| `megingiard.privd.hmac.key` | 64-character hex string (32 bytes) shared by the app and `megingiard_privd`. Injected into `BuildConfig.PRIVD_HMAC_KEY` and `PRIVD_HMAC_KEY_HEX` when rebuilding the daemon. | Yes. Release builds and daemon rebuilds reject the public default unless explicitly overridden for local testing. |

Generate a Privd HMAC key with:

```bash
openssl rand -hex 32 | tr '[:lower:]' '[:upper:]'
```

Whenever `megingiard.privd.hmac.key` changes, rebuild the daemon with:

```bash
./build_megingiard_privd.sh
```

For local non-distribution testing only, release builds can be forced with `-Pmegingiard.allowDefaultPrivdHmacKey=true`, and the daemon script can be forced with `MEGINGIARD_ALLOW_DEFAULT_PRIVD_HMAC_KEY=true`. Do not use either override for distributed APKs.

## Verification and Tests

The current automated coverage focuses on pure cryptographic primitives:

- `BinaryIntegrityTest` covers SHA-256 known-answer vectors and sensitivity to byte changes.
- `HmacUtilTest` covers HMAC-SHA256 RFC 4231 vectors and the constant-time hex MAC comparison used for daemon `PROOF` verification.
- Agents must run `./gradlew :core:test :domain:test` after implementation changes that affect pure `:core` or `:domain` logic.
- Native C source changes must be followed by the matching build script; see [Build Native](docs/BUILD_NATIVE.md#native-rebuild-policy).

Runtime Android branches such as `SignatureGuard.verify()` and the full `LocalSocket` handshake currently require manual device validation or future Robolectric / instrumentation coverage.

## Residual Risks and Future Hardening

- The default Privd HMAC key is public. Release builds and daemon rebuilds now reject it by default, but explicit local override switches still exist for non-distribution testing.
- HMAC key rotation and signing-certificate rotation are manual rebuild / redeploy operations today.
- A fully compromised / rooted device can patch code, binaries, memory, or filesystem contents outside the assumptions of this model.
- Socket parser fuzzing and end-to-end tamper tests are not yet part of automated CI.

## Documentation Map

- [Requirements](docs/REQUIREMENTS.md#security) defines the non-functional security requirements.
- [Architecture](docs/ARCHITECTURE.md#security-architecture) explains the system-level security layering.
- [Privileged Mode](docs/features/privileged-mode/FEATURE.md#security-model) documents the daemon trust boundary and mutual HMAC protocol.
- [Build Native](docs/BUILD_NATIVE.md#native-asset-integrity) documents native rebuilds, hash generation, and runtime verification.
- [Config Feature](docs/features/config/FEATURE.md) documents export/import data-integrity checks.
- [Agent Guidelines](AGENTS.md#15-checklist-for-every-change) define required rebuild and test workflow for agents.
