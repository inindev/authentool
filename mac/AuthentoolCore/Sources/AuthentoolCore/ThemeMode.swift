import Foundation

// The five selectable themes. Raw values match the Android app's ThemeMode names so
// a persisted preference reads the same conceptually across both apps. 'system' resolves to a
// light or dark palette at render time based on the OS appearance.
public enum ThemeMode: String, CaseIterable, Codable, Sendable {
    case system = "SYSTEM"
    case frost = "FROST"
    case sunrise = "SUNRISE"
    case midnight = "MIDNIGHT"
    case espresso = "ESPRESSO"

    /// Human-readable label for the theme picker.
    public var displayName: String {
        switch self {
        case .system: return "System"
        case .frost: return "Frost"
        case .sunrise: return "Sunrise"
        case .midnight: return "Midnight"
        case .espresso: return "Espresso"
        }
    }
}

// Persists the selected theme. Non-secret, so UserDefaults is appropriate (the secret seeds
// live in the Keychain). Unknown/missing values fall back to '.system', matching Android.
public final class ThemePreference: @unchecked Sendable {
    private let defaults: UserDefaults
    private let key = "theme_preference"

    public init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
    }

    public var mode: ThemeMode {
        get {
            guard let raw = defaults.string(forKey: key), let mode = ThemeMode(rawValue: raw) else {
                return .system
            }
            return mode
        }
        set { defaults.set(newValue.rawValue, forKey: key) }
    }
}
