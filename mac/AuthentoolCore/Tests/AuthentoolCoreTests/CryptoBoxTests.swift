import Testing
import Foundation
@testable import AuthentoolCore

@Suite("CryptoBox (backup blob v0x08)")
struct CryptoBoxTests {

    // Fixture produced by the reference tool cli/crypt.py (version 0x08, PBKDF2-SHA256
    // @250k, AES-256-GCM). Decrypting it here proves Python -> Swift interop. The random
    // salt/nonce mean the blob bytes differ run to run; this is one captured instance.
    static let pythonFixtureBlob =
        "CBgVGKTopyjhaLhsfoy/scnh9fWuCWiwxGsojnXFbmAv5eDFKswY0gv3knDLN+XsJP9VPh+fexKKHMzWrONBiSm1iYBrFFG4iFrco0LbwB1MpIybjqwFB6PO8ZpbTOCNAOLM5jF9iGAEwejDmrLPIaR+QjWhmsLS1TceVEA440Cm5s06MkpVi7yFyleUgC//Xk6xBylGcQ=="
    static let pythonFixturePlaintext =
        #"[{"name":"GitHub","seed":"MZXW6YTBOJXXEABC"},{"name":"Example","seed":"GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ"}]"#
    static let pythonFixturePassword = "correct horse battery"

    @Test func decryptsPythonReferenceFixture() throws {
        let result = try CryptoBox.decrypt(Self.pythonFixtureBlob, password: Self.pythonFixturePassword)
        #expect(result == Self.pythonFixturePlaintext)
    }

    @Test func roundTripsArbitraryPayload() throws {
        let plaintext = #"[{"name":"Work VPN","seed":"JBSWY3DPEHPK3PXP"}]"#
        let blob = try CryptoBox.encrypt(plaintext, password: "hunter2pass")
        #expect(try CryptoBox.decrypt(blob, password: "hunter2pass") == plaintext)
    }

    @Test func producesV8VersionByte() throws {
        let blob = try CryptoBox.encrypt("hello", password: "password1")
        let bytes = [UInt8](Data(base64Encoded: blob)!)
        #expect(bytes[0] == 0x08)
        // version(1) + salt(16) + nonce(12) + tag(16) minimum before ciphertext.
        #expect(bytes.count >= 1 + 16 + 12 + 16)
    }

    @Test func wrongPasswordFailsCleanly() {
        #expect(throws: CryptoBox.CryptoError.wrongPasswordOrCorrupt) {
            _ = try CryptoBox.decrypt(Self.pythonFixtureBlob, password: "not the password")
        }
    }

    @Test func tamperedCiphertextFailsAuthentication() throws {
        let blob = try CryptoBox.encrypt("sensitive", password: "password1")
        var bytes = [UInt8](Data(base64Encoded: blob)!)
        bytes[bytes.count - 1] ^= 0xFF // flip a tag byte
        let tampered = Data(bytes).base64EncodedString()
        #expect(throws: CryptoBox.CryptoError.wrongPasswordOrCorrupt) {
            _ = try CryptoBox.decrypt(tampered, password: "password1")
        }
    }

    @Test func unsupportedVersionByteIsReported() throws {
        let blob = try CryptoBox.encrypt("data", password: "password1")
        var bytes = [UInt8](Data(base64Encoded: blob)!)
        bytes[0] = 0x07
        let modified = Data(bytes).base64EncodedString()
        #expect(throws: CryptoBox.CryptoError.unsupportedVersion(0x07)) {
            _ = try CryptoBox.decrypt(modified, password: "password1")
        }
    }

    @Test func tooShortBlobIsReported() {
        let shortBlob = Data([0x08, 0x00, 0x01, 0x02]).base64EncodedString()
        #expect(throws: CryptoBox.CryptoError.tooShort) {
            _ = try CryptoBox.decrypt(shortBlob, password: "password1")
        }
    }

    @Test func toleratesWhitespaceAndMissingPadding() throws {
        let blob = try CryptoBox.encrypt("padded payload", password: "password1")
        let mangled = "  \n" + blob.replacingOccurrences(of: "=", with: "") + "\n "
        #expect(try CryptoBox.decrypt(mangled, password: "password1") == "padded payload")
    }

    @Test func emptyInputsAreRejected() {
        #expect(throws: CryptoBox.CryptoError.emptyInput) {
            _ = try CryptoBox.encrypt("", password: "password1")
        }
        #expect(throws: CryptoBox.CryptoError.emptyPassword) {
            _ = try CryptoBox.encrypt("data", password: "")
        }
        #expect(throws: CryptoBox.CryptoError.emptyPassword) {
            _ = try CryptoBox.decrypt(Self.pythonFixtureBlob, password: "")
        }
    }
}
