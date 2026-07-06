import Testing
import Foundation
@testable import AuthentoolCore

@Suite("TOTP generation")
struct TOTPGeneratorTests {

    private func date(_ unix: TimeInterval) -> Date { Date(timeIntervalSince1970: unix) }

    // RFC 6238 Appendix B, SHA1, secret = ASCII "12345678901234567890".
    @Test(arguments: [
        (59.0, "287082"),
        (1111111109.0, "081804"),
        (1234567890.0, "005924"),
        (2000000000.0, "279037"),
    ])
    func matchesRFC6238Vectors(unix: TimeInterval, expected: String) {
        let gen = TOTPGenerator(secret: Array("12345678901234567890".utf8))
        #expect(gen.code(at: date(unix)) == expected)
    }

    // Android sample seed MZXW6YTBOJXXEABC.
    @Test(arguments: [
        (0.0, "391061"),
        (1234567890.0, "994452"),
        (1700000000.0, "263178"),
        (2000000000.0, "132209"),
    ])
    func matchesAndroidSampleSeedVectors(unix: TimeInterval, expected: String) throws {
        let gen = try TOTPGenerator(base32Secret: "MZXW6YTBOJXXEABC")
        #expect(gen.code(at: date(unix)) == expected)
    }

    @Test func padsLeadingZeros() {
        // RFC vector at t=1234567890 truncates to 5924 -> must render as "005924".
        let gen = TOTPGenerator(secret: Array("12345678901234567890".utf8))
        let code = gen.code(at: date(1234567890))
        #expect(code == "005924")
        #expect(code.count == 6)
    }

    @Test func codeChangesExactlyAtWindowBoundary() throws {
        let gen = try TOTPGenerator(base32Secret: "MZXW6YTBOJXXEABC")
        // Window 0 = [0,30), window 1 = [30,60).
        #expect(gen.code(at: date(29.999)) == gen.code(counter: 0))
        #expect(gen.code(at: date(30.0)) == gen.code(counter: 1))
        #expect(gen.code(counter: 0) != gen.code(counter: 1))
    }

    @Test func counterPathMatchesTimePath() throws {
        // t=1234567890 -> counter = 1234567890 / 30 = 41152263.
        let gen = try TOTPGenerator(base32Secret: "MZXW6YTBOJXXEABC")
        #expect(gen.code(at: date(1234567890)) == gen.code(counter: 41152263))
    }
}
