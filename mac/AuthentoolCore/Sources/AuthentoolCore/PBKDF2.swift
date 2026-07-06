import Foundation
import CommonCrypto

// PBKDF2-HMAC-SHA256 key derivation. CryptoKit has no PBKDF2, so we use CommonCrypto
// Parameters (SHA256, iteration count, key length) are supplied by
// the caller and must match the Android app / CLI tools for backup compatibility.

enum PBKDF2 {
    /// Derives a key from a UTF-8 password and salt. Returns 'keyLength' bytes.
    /// The caller is responsible for zeroing the returned key after use.
    static func deriveKey(password: String, salt: [UInt8], iterations: Int, keyLength: Int) -> [UInt8] {
        let passwordBytes = Array(password.utf8)
        var derived = [UInt8](repeating: 0, count: keyLength)

        let status: Int32 = passwordBytes.withUnsafeBytes { pwRaw in
            salt.withUnsafeBytes { saltRaw in
                derived.withUnsafeMutableBytes { outRaw in
                    CCKeyDerivationPBKDF(
                        CCPBKDFAlgorithm(kCCPBKDF2),
                        pwRaw.baseAddress?.assumingMemoryBound(to: CChar.self),
                        passwordBytes.count,
                        saltRaw.baseAddress?.assumingMemoryBound(to: UInt8.self),
                        salt.count,
                        CCPseudoRandomAlgorithm(kCCPRFHmacAlgSHA256),
                        UInt32(iterations),
                        outRaw.baseAddress?.assumingMemoryBound(to: UInt8.self),
                        keyLength
                    )
                }
            }
        }

        precondition(status == kCCSuccess, "PBKDF2 derivation failed with status \(status)")
        return derived
    }
}
