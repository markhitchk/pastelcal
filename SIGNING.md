# PastelCal release signing

PastelCal 1.1.0 does not include a signing key or credentials in source control.

For a production Android App Bundle or APK, configure release signing outside the repository (for example with Android Studio's signing configuration, a local `keystore.properties` excluded by `.gitignore`, or CI secrets).

Never commit `.jks`, `.keystore`, private-key, or signing-password files.

## Release identity

- Application ID: `com.pastelcal.app.final`
- Version name: `1.1.0`
- Version code: `1010000`
- Minimum SDK: 26
- Target SDK: 36
- Compile SDK: 36

The debug build uses the `.debug` application-id suffix and `-debug` version-name suffix so it can coexist with a release install.
