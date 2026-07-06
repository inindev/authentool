// User-facing seed validation and normalization for the add/edit entry flow.
//
// The Base32 decoder (Base32.swift) is intentionally strict. This layer is the friendly
// front door: it normalizes a pasted seed (uppercase, strip spaces/padding) and reports a
// typed reason when it can't be used, so the UI can show a helpful message.
//
// A seed is accepted iff, after normalization, every character is in the Base32 alphabet and
// the length is one the decoder yields whole bytes for. This matches the Android validator
// (isValidBase32): valid lengths are 2, 4, 5, 7, or any multiple of 8.

public enum SeedValidation {
    public enum Failure: Error, Equatable {
        case empty
        case invalidCharacters
        case invalidLength
    }

    /// Uppercases and removes whitespace and '=' padding. Does not validate.
    public static func normalize(_ seed: String) -> String {
        seed.uppercased().filter { !$0.isWhitespace && $0 != "=" }
    }

    /// Returns the normalized seed if valid, otherwise throws a typed 'Failure'.
    public static func validated(_ seed: String) throws -> String {
        let normalized = normalize(seed)
        guard !normalized.isEmpty else { throw Failure.empty }
        guard normalized.allSatisfy({ Base32.alphabet.contains($0) }) else {
            throw Failure.invalidCharacters
        }
        guard isValidLength(normalized.count) else { throw Failure.invalidLength }
        return normalized
    }

    public static func isValid(_ seed: String) -> Bool {
        (try? validated(seed)) != nil
    }

    // Lengths that Base32 decodes into a whole number of bytes without leftover bits.
    private static func isValidLength(_ length: Int) -> Bool {
        if length == 2 || length == 4 || length == 5 || length == 7 { return true }
        return length >= 8 && length % 8 == 0
    }
}
