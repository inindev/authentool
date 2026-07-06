import Foundation
import CryptoKit

// RFC 6238 TOTP generator. Fixed to HMAC-SHA1 / 30s / 6 digits to match the Android app
// and CLI tools, but the parameters are configurable for testing
// against the RFC 6238 Appendix B vectors. The clock is injectable (a 'Date') so tests are
// fully deterministic.

public struct TOTPGenerator: Sendable {
    public let secret: [UInt8]
    public let timeStep: Int
    public let digits: Int

    public init(secret: [UInt8], timeStep: Int = 30, digits: Int = 6) {
        precondition(timeStep > 0, "timeStep must be positive")
        precondition(digits > 0 && digits <= 9, "digits must be 1...9")
        self.secret = secret
        self.timeStep = timeStep
        self.digits = digits
    }

    /// Convenience initializer that decodes a Base32 seed.
    public init(base32Secret: String, timeStep: Int = 30, digits: Int = 6) throws {
        self.init(secret: try Base32.decode(base32Secret), timeStep: timeStep, digits: digits)
    }

    /// The TOTP code for the given moment (defaults to now).
    public func code(at date: Date = Date()) -> String {
        let unix = date.timeIntervalSince1970
        // floor toward negative infinity so pre-epoch times still bucket correctly.
        let counter = Int64(floor(unix)) / Int64(timeStep)
        return code(counter: UInt64(bitPattern: counter))
    }

    /// The TOTP code for an explicit counter value (RFC 4226 moving factor).
    public func code(counter: UInt64) -> String {
        var bigEndianCounter = counter.bigEndian
        let counterBytes = withUnsafeBytes(of: &bigEndianCounter) { Array($0) }

        let key = SymmetricKey(data: secret)
        let mac = HMAC<Insecure.SHA1>.authenticationCode(for: counterBytes, using: key)
        let hash = Array(mac) // 20 bytes

        // Dynamic truncation (RFC 4226 section 5.3).
        let offset = Int(hash[hash.count - 1] & 0x0F)
        let binary = (UInt32(hash[offset] & 0x7F) << 24)
            | (UInt32(hash[offset + 1]) << 16)
            | (UInt32(hash[offset + 2]) << 8)
            | UInt32(hash[offset + 3])

        var modulus: UInt32 = 1
        for _ in 0..<digits { modulus *= 10 }
        let otp = binary % modulus

        let digitsString = String(otp)
        let padding = digits - digitsString.count
        return padding > 0 ? String(repeating: "0", count: padding) + digitsString : digitsString
    }
}
