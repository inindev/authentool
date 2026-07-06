import Testing
@testable import AuthentoolCore

@Suite("Seed validation")
struct SeedValidationTests {
    @Test func normalizesUppercaseStripsSpacesAndPadding() {
        #expect(SeedValidation.normalize("mzxw 6ytb ojxx eabc") == "MZXW6YTBOJXXEABC")
        #expect(SeedValidation.normalize("MY======") == "MY")
    }

    @Test func acceptsValidSeeds() throws {
        #expect(try SeedValidation.validated("mzxw6ytbojxxeabc") == "MZXW6YTBOJXXEABC")
        #expect(try SeedValidation.validated("JBSWY3DPEHPK3PXP") == "JBSWY3DPEHPK3PXP")
        // Spaced input from a QR/manual paste normalizes and validates.
        #expect(try SeedValidation.validated("JBSW Y3DP EHPK 3PXP") == "JBSWY3DPEHPK3PXP")
    }

    @Test func acceptsAndroidValidLengths() {
        // 2, 4, 5, 7, and multiples of 8 are accepted.
        #expect(SeedValidation.isValid("MY"))        // 2
        #expect(SeedValidation.isValid("MZXW"))      // 4
        #expect(SeedValidation.isValid("MZXW6"))     // 5
        #expect(SeedValidation.isValid("MZXW6YT"))   // 7
        #expect(SeedValidation.isValid("MZXW6YTB"))  // 8
    }

    @Test func rejectsInvalidLength() {
        // length 3 and 6 are not valid Base32 byte boundaries.
        #expect(throws: SeedValidation.Failure.invalidLength) { try SeedValidation.validated("MZX") }
        #expect(throws: SeedValidation.Failure.invalidLength) { try SeedValidation.validated("MZXW6Y") }
    }

    @Test func rejectsInvalidCharacters() {
        // 0, 1, 8, 9 are not in the Base32 alphabet.
        #expect(throws: SeedValidation.Failure.invalidCharacters) { try SeedValidation.validated("MZXW6Y10") }
    }

    @Test func rejectsEmpty() {
        #expect(throws: SeedValidation.Failure.empty) { try SeedValidation.validated("   ") }
        #expect(throws: SeedValidation.Failure.empty) { try SeedValidation.validated("======") }
    }

    @Test func validatedSeedsAlwaysDecode() throws {
        // Anything the validator accepts, the strict decoder must accept too.
        for seed in ["MY", "MZXW", "MZXW6", "MZXW6YT", "MZXW6YTBOJXXEABC", "JBSWY3DPEHPK3PXP"] {
            let normalized = try SeedValidation.validated(seed)
            #expect(throws: Never.self) { try Base32.decode(normalized) }
        }
    }
}
