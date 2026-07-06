import Foundation

// A single authenticator entry: a display name plus its Base32 TOTP seed.
//
// The on-disk / backup representation is the JSON object {"name","seed"}.
// 'id' is a runtime-only identity for SwiftUI/diffing and is intentionally NOT serialized -
// it is regenerated on decode, matching the Android app (which derives transient ids on load).
public struct AuthEntry: Identifiable, Equatable, Codable, Sendable {
    public let id: UUID
    public var name: String
    public var seed: String

    public init(id: UUID = UUID(), name: String, seed: String) {
        self.id = id
        self.name = name
        self.seed = seed
    }

    private enum CodingKeys: String, CodingKey {
        case name, seed
    }

    public init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        self.id = UUID()
        self.name = try container.decode(String.self, forKey: .name)
        self.seed = try container.decode(String.self, forKey: .seed)
    }

    public func encode(to encoder: Encoder) throws {
        var container = encoder.container(keyedBy: CodingKeys.self)
        try container.encode(name, forKey: .name)
        try container.encode(seed, forKey: .seed)
    }

    /// The current TOTP code, or 'nil' if the stored seed is not decodable.
    /// Seeds added/imported through 'AuthStore' are normalized, so this is non-nil in practice;
    /// it guards the UI against a hand-corrupted backup.
    public func code(at date: Date = Date()) -> String? {
        guard let generator = try? TOTPGenerator(base32Secret: seed) else { return nil }
        return generator.code(at: date)
    }
}
