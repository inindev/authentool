// Presentation helper: groups a TOTP code as "123 456" for readability.
//
// The Android app splits a 6-digit code in half with a U+205F MEDIUM MATHEMATICAL SPACE
// We mirror that default so the two apps look identical, but
// the separator is configurable. Codes whose length is not 6 are returned unchanged, which
// matches the Android behavior (it only groups 6-digit codes).

public enum TOTPCodeFormatter {
    /// U+205F MEDIUM MATHEMATICAL SPACE - the separator the Android app uses.
    public static let defaultSeparator = "\u{205F}"

    public static func grouped(_ code: String, separator: String = defaultSeparator) -> String {
        guard code.count == 6 else { return code }
        let mid = code.index(code.startIndex, offsetBy: 3)
        return code[..<mid] + separator + code[mid...]
    }
}
