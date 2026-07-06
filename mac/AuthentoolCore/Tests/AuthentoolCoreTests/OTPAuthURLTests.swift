import Testing
import Foundation
@testable import AuthentoolCore

@Suite("otpauth:// parsing")
struct OTPAuthURLTests {
    @Test func parsesStandardURIWithIssuerAndAccount() throws {
        let entry = try OTPAuthURL.parse(
            "otpauth://totp/Example:alice@example.com?secret=JBSWY3DPEHPK3PXP&issuer=Example"
        )
        #expect(entry.secret == "JBSWY3DPEHPK3PXP")
        #expect(entry.name == "Example (alice@example.com)")
    }

    @Test func derivesIssuerFromLabelPrefix() throws {
        // No issuer query param; issuer comes from the "Issuer:Account" label.
        let entry = try OTPAuthURL.parse("otpauth://totp/GitHub:octocat?secret=JBSWY3DPEHPK3PXP")
        #expect(entry.name == "GitHub (octocat)")
    }

    @Test func usesPlainLabelWhenNoIssuer() throws {
        let entry = try OTPAuthURL.parse("otpauth://totp/My%20Account?secret=JBSWY3DPEHPK3PXP")
        #expect(entry.name == "My Account")
    }

    @Test func defaultsParametersWhenAbsent() throws {
        let entry = try OTPAuthURL.parse("otpauth://totp/Acme?secret=JBSWY3DPEHPK3PXP")
        #expect(entry.algorithm == "SHA1")
        #expect(entry.digits == 6)
        #expect(entry.period == 30)
        #expect(entry.usesDefaultParameters)
    }

    @Test func surfacesNonDefaultParameters() throws {
        // Lenient: parse succeeds and reports the values; the caller warns/decides.
        let entry = try OTPAuthURL.parse(
            "otpauth://totp/Acme?secret=JBSWY3DPEHPK3PXP&algorithm=SHA256&digits=8&period=60&issuer=Acme"
        )
        #expect(entry.algorithm == "SHA256")
        #expect(entry.digits == 8)
        #expect(entry.period == 60)
        #expect(entry.issuer == "Acme")
        #expect(!entry.usesDefaultParameters)
    }

    @Test func rejectsNonOTPAuth() {
        #expect(throws: OTPAuthURL.ParseError.notOTPAuth) {
            try OTPAuthURL.parse("https://example.com/?secret=ABC")
        }
    }

    @Test func rejectsHOTP() {
        #expect(throws: OTPAuthURL.ParseError.notTOTP) {
            try OTPAuthURL.parse("otpauth://hotp/Acme?secret=JBSWY3DPEHPK3PXP&counter=0")
        }
    }

    @Test func rejectsMissingSecret() {
        #expect(throws: OTPAuthURL.ParseError.missingSecret) {
            try OTPAuthURL.parse("otpauth://totp/Acme?issuer=Acme")
        }
    }

    // MARK: - makeURI

    @Test func makeURIProducesParseableURI() throws {
        let uri = OTPAuthURL.makeURI(name: "GitHub (octocat)", secret: "JBSWY3DPEHPK3PXP")
        #expect(uri.hasPrefix("otpauth://totp/"))
        let parsed = try OTPAuthURL.parse(uri)
        #expect(parsed.name == "GitHub (octocat)")
        #expect(parsed.secret == "JBSWY3DPEHPK3PXP")
    }

    @Test func makeURIEncodesSpacesAndIncludesIssuer() throws {
        let uri = OTPAuthURL.makeURI(name: "My Account", secret: "JBSWY3DPEHPK3PXP", issuer: "Acme")
        #expect(uri.contains("My%20Account"))
        let parsed = try OTPAuthURL.parse(uri)
        #expect(parsed.secret == "JBSWY3DPEHPK3PXP")
        #expect(parsed.issuer == "Acme")
    }
}
