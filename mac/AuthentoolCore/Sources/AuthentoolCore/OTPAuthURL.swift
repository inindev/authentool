import Foundation

// Parses an 'otpauth://totp/...' URI (the Key URI Format used by 2FA QR codes) into its fields.
// Pure and testable. Parsing is lenient: it surfaces everything the QR carries (issuer, algorithm,
// digits, period) so the UI can show it and let the user confirm/tweak. The app itself only
// generates SHA1 / 6-digit / 30s codes, so 'usesDefaultParameters' flags a QR whose
// codes wouldn't match - the caller decides what to do with that.
public enum OTPAuthURL {
    public struct Entry: Equatable, Sendable {
        public let name: String
        public let secret: String
        public let issuer: String?
        public let algorithm: String   // as written in the QR; defaults to "SHA1"
        public let digits: Int         // defaults to 6
        public let period: Int         // seconds; defaults to 30

        /// True when the QR's parameters match what this app actually generates.
        public var usesDefaultParameters: Bool {
            algorithm.uppercased() == "SHA1" && digits == 6 && period == 30
        }
    }

    public enum ParseError: Error, Equatable {
        case notOTPAuth
        case notTOTP        // e.g. an otpauth://hotp/... URI
        case missingSecret
    }

    public static func parse(_ string: String) throws -> Entry {
        let trimmed = string.trimmingCharacters(in: .whitespacesAndNewlines)
        guard let comps = URLComponents(string: trimmed),
              comps.scheme?.lowercased() == "otpauth"
        else { throw ParseError.notOTPAuth }
        guard comps.host?.lowercased() == "totp" else { throw ParseError.notTOTP }

        let items = comps.queryItems ?? []
        func value(_ key: String) -> String? {
            items.first { $0.name.lowercased() == key }?.value
        }

        guard let secret = value("secret")?.trimmingCharacters(in: .whitespaces), !secret.isEmpty else {
            throw ParseError.missingSecret
        }

        // Label is the (already percent-decoded) path, possibly "Issuer:Account".
        let label = comps.path.hasPrefix("/") ? String(comps.path.dropFirst()) : comps.path
        var issuer = value("issuer")
        var account = label
        if let colon = label.firstIndex(of: ":") {
            let labelIssuer = String(label[..<colon]).trimmingCharacters(in: .whitespaces)
            account = String(label[label.index(after: colon)...]).trimmingCharacters(in: .whitespaces)
            if issuer?.isEmpty ?? true { issuer = labelIssuer }
        }
        let trimmedIssuer = issuer?.trimmingCharacters(in: .whitespaces)

        return Entry(
            name: displayName(issuer: trimmedIssuer, account: account),
            secret: secret,
            issuer: (trimmedIssuer?.isEmpty ?? true) ? nil : trimmedIssuer,
            algorithm: value("algorithm")?.uppercased() ?? "SHA1",
            digits: value("digits").flatMap(Int.init) ?? 6,
            period: value("period").flatMap(Int.init) ?? 30
        )
    }

    /// Builds an 'otpauth://totp/...' URI for an entry, for QR export. Parameters are omitted since
    /// they're the app's defaults (SHA1 / 6 / 30); the label is the entry name. Round-trips through
    /// 'parse' back to the same name + secret.
    public static func makeURI(name: String, secret: String, issuer: String? = nil) -> String {
        var components = URLComponents()
        components.scheme = "otpauth"
        components.host = "totp"
        components.path = "/" + name // URLComponents percent-encodes the label on output
        var items = [URLQueryItem(name: "secret", value: secret)]
        if let issuer, !issuer.isEmpty {
            items.append(URLQueryItem(name: "issuer", value: issuer))
        }
        components.queryItems = items
        return components.string ?? ""
    }

    /// Builds a friendly entry name from the issuer and account, matching how the QR encoded them.
    private static func displayName(issuer: String?, account: String) -> String {
        let issuer = (issuer ?? "").trimmingCharacters(in: .whitespaces)
        let account = account.trimmingCharacters(in: .whitespaces)
        if !issuer.isEmpty, !account.isEmpty, account.lowercased() != issuer.lowercased() {
            return "\(issuer) (\(account))"
        }
        return issuer.isEmpty ? account : issuer
    }
}
