// AuthentoolCore - pure, deterministic core of the Authentool macOS app.
//
// The module's umbrella namespace. The real types live alongside this file
// (Base32, TOTPGenerator, CryptoBox, AuthEntry, AuthStore).

public enum AuthentoolCore {
    /// Format version of the encrypted backup blob this build reads/writes.
    /// Must match the Android app and the CLI tools.
    public static let backupFormatVersion: UInt8 = 0x08
}
