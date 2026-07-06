import Testing
@testable import AuthentoolCore

@Suite("Base32 decoding")
struct Base32Tests {
    // Android sample seed -> known bytes.
    @Test func decodesAndroidSampleSeed() throws {
        let bytes = try Base32.decode("MZXW6YTBOJXXEABC")
        #expect(bytes == [0x66, 0x6f, 0x6f, 0x62, 0x61, 0x72, 0x6f, 0x72, 0x00, 0x22])
    }

    @Test func decodesRFCSecretAscii() throws {
        // "12345678901234567890" Base32-encoded is GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ
        let bytes = try Base32.decode("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ")
        #expect(bytes == Array("12345678901234567890".utf8))
    }

    @Test func isCaseInsensitive() throws {
        #expect(try Base32.decode("mzxw6ytbojxxeabc") == (try Base32.decode("MZXW6YTBOJXXEABC")))
    }

    @Test func ignoresPaddingEquals() throws {
        // "MY======" is RFC 4648 padded encoding of a single byte 0x66 ('f').
        #expect(try Base32.decode("MY======") == [0x66])
    }

    @Test func rejectsInvalidCharacter() {
        #expect(throws: Base32.DecodingError.invalidCharacter("1")) {
            _ = try Base32.decode("MZXW1")
        }
    }

    @Test func rejectsEmptyInput() {
        #expect(throws: Base32.DecodingError.empty) {
            _ = try Base32.decode("======")
        }
    }
}
