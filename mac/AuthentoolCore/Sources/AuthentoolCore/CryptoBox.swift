import Foundation
import CryptoKit

// Password-based encryption for backup blobs, byte-compatible with the Android app and the
// CLI tools (cli/crypt.py, CryptUtil.kt). The exact format is:
//
//   base64( [0x08] ++ salt(16) ++ nonce(12) ++ ciphertext ++ tag(16) )
//
// where the key = PBKDF2-HMAC-SHA256(password, salt, 250000) -> 32 bytes, and the cipher is
// AES-256-GCM with a 128-bit tag and no additional authenticated data.
//
// CryptoKit's SealedBox.combined is exactly 'nonce ++ ciphertext ++ tag', so the blob body
// after the version byte and salt is the SealedBox combined representation.

public enum CryptoBox {
    public static let version: UInt8 = 0x08
    static let saltSize = 16
    static let nonceSize = 12
    static let tagSize = 16
    static let keySize = 32
    static let iterations = 250_000

    public enum CryptoError: Error, Equatable {
        case emptyInput
        case emptyPassword
        case invalidBase64
        case tooShort
        case unsupportedVersion(UInt8)
        case wrongPasswordOrCorrupt
    }

    /// Encrypts 'plaintext' with 'password', returning the Base64 blob.
    public static func encrypt(_ plaintext: String, password: String) throws -> String {
        guard !plaintext.isEmpty else { throw CryptoError.emptyInput }
        guard !password.isEmpty else { throw CryptoError.emptyPassword }

        let salt = randomBytes(saltSize)
        var key = PBKDF2.deriveKey(password: password, salt: salt, iterations: iterations, keyLength: keySize)
        defer { zero(&key) }

        let symmetricKey = SymmetricKey(data: key)
        // Let CryptoKit generate a random 12-byte nonce; '.combined' includes it.
        let sealed = try AES.GCM.seal(Data(plaintext.utf8), using: symmetricKey)
        guard let combined = sealed.combined else {
            // Only nil for non-standard nonce sizes, which we never use.
            throw CryptoError.wrongPasswordOrCorrupt
        }

        var output = Data([version])
        output.append(contentsOf: salt)
        output.append(combined)
        return output.base64EncodedString()
    }

    /// Decrypts a Base64 blob produced by 'encrypt' (or the Android app / CLI tools).
    public static func decrypt(_ base64: String, password: String) throws -> String {
        guard !password.isEmpty else { throw CryptoError.emptyPassword }

        // Tolerate surrounding/internal whitespace and missing padding.
        let cleaned = base64.filter { !$0.isWhitespace }
        let remainder = cleaned.count % 4
        let padded = remainder == 0 ? cleaned : cleaned + String(repeating: "=", count: 4 - remainder)
        guard let data = Data(base64Encoded: padded) else { throw CryptoError.invalidBase64 }

        let bytes = [UInt8](data)
        // version(1) + salt(16) + nonce(12) + tag(16) + at least 1 byte of ciphertext.
        guard bytes.count >= 1 + saltSize + nonceSize + tagSize + 1 else { throw CryptoError.tooShort }
        guard bytes[0] == version else { throw CryptoError.unsupportedVersion(bytes[0]) }

        let salt = Array(bytes[1 ..< (1 + saltSize)])
        let combined = Data(bytes[(1 + saltSize)...]) // nonce ++ ciphertext ++ tag

        var key = PBKDF2.deriveKey(password: password, salt: salt, iterations: iterations, keyLength: keySize)
        defer { zero(&key) }
        let symmetricKey = SymmetricKey(data: key)

        do {
            let sealedBox = try AES.GCM.SealedBox(combined: combined)
            let plaintext = try AES.GCM.open(sealedBox, using: symmetricKey)
            guard let string = String(data: plaintext, encoding: .utf8) else {
                throw CryptoError.wrongPasswordOrCorrupt
            }
            return string
        } catch let error as CryptoError {
            throw error
        } catch {
            // Authentication failure (wrong password / tampered data) or malformed box.
            throw CryptoError.wrongPasswordOrCorrupt
        }
    }

    // MARK: - Helpers

    private static func randomBytes(_ count: Int) -> [UInt8] {
        var rng = SystemRandomNumberGenerator() // CSPRNG on Apple platforms.
        return (0 ..< count).map { _ in UInt8.random(in: 0 ... 255, using: &rng) }
    }

    private static func zero(_ bytes: inout [UInt8]) {
        for i in bytes.indices { bytes[i] = 0 }
    }
}
