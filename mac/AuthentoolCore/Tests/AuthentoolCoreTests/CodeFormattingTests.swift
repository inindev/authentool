import Testing
@testable import AuthentoolCore

@Suite("Code formatting")
struct CodeFormattingTests {
    @Test func groupsSixDigitsInHalves() {
        #expect(TOTPCodeFormatter.grouped("123456", separator: " ") == "123 456")
    }

    @Test func usesMediumMathSpaceByDefault() {
        #expect(TOTPCodeFormatter.grouped("005924") == "005\u{205F}924")
    }

    @Test func leavesNonSixDigitCodesUnchanged() {
        #expect(TOTPCodeFormatter.grouped("1234") == "1234")
        #expect(TOTPCodeFormatter.grouped("12345678") == "12345678")
    }
}
