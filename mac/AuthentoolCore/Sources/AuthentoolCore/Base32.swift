// Base32 (RFC 4648) decoding for TOTP seeds.
//
// Implements the streaming algorithm used by the reference CLI tools
// (cli/totp_gen/totp_gen.py, kotlin/TotpGen.kt) so decoded bytes match exactly.
// Input is uppercased and '=' padding is stripped; any other
// out-of-alphabet character is rejected. User-facing normalization (e.g. stripping spaces
// from a pasted seed) belongs in the validation layer, not here.

public enum Base32 {
    /// RFC 4648 Base32 alphabet.
    public static let alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"

    public enum DecodingError: Error, Equatable {
        /// A character outside the Base32 alphabet (other than '=' padding) was found.
        case invalidCharacter(Character)
        /// The input contained no Base32 symbols.
        case empty
    }

    // Reverse lookup: ASCII value -> 5-bit symbol value, or nil if not in the alphabet.
    private static let valueForByte: [Int8] = {
        var table = [Int8](repeating: -1, count: 128)
        for (i, ch) in alphabet.utf8.enumerated() {
            table[Int(ch)] = Int8(i)
        }
        return table
    }()

    /// Decodes a Base32 string into bytes per RFC 4648.
    /// - Throws: 'DecodingError.invalidCharacter' for any non-alphabet, non-'=' character;
    ///   'DecodingError.empty' if no symbols remain after stripping padding.
    public static func decode(_ input: String) throws -> [UInt8] {
        var accumulator = 0
        var bitCount = 0
        var output: [UInt8] = []
        var sawSymbol = false

        for scalar in input.uppercased().unicodeScalars {
            if scalar == "=" { continue }
            let value: Int8 = scalar.value < 128 ? valueForByte[Int(scalar.value)] : -1
            guard value >= 0 else {
                throw DecodingError.invalidCharacter(Character(scalar))
            }
            sawSymbol = true
            accumulator = (accumulator << 5) | Int(value)
            bitCount += 5
            if bitCount >= 8 {
                bitCount -= 8
                output.append(UInt8((accumulator >> bitCount) & 0xFF))
            }
        }

        guard sawSymbol else { throw DecodingError.empty }
        return output
    }
}
