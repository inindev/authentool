import Testing
import Foundation
@testable import AuthentoolCore

@Suite("Theme persistence")
struct ThemeModeTests {
    private func freshPreference() -> (ThemePreference, UserDefaults) {
        let suite = "themetest-\(UUID().uuidString)"
        let defaults = UserDefaults(suiteName: suite)!
        return (ThemePreference(defaults: defaults), defaults)
    }

    @Test func defaultsToSystem() {
        let (pref, _) = freshPreference()
        #expect(pref.mode == .system)
    }

    @Test func persistsAndReadsBack() {
        let (pref, defaults) = freshPreference()
        pref.mode = .espresso
        #expect(pref.mode == .espresso)
        // A new instance over the same defaults sees the saved value.
        #expect(ThemePreference(defaults: defaults).mode == .espresso)
    }

    @Test func unknownValueFallsBackToSystem() {
        let (pref, defaults) = freshPreference()
        defaults.set("NEON", forKey: "theme_preference")
        #expect(pref.mode == .system)
    }

    @Test func rawValuesMatchAndroidNames() {
        #expect(ThemeMode.allCases.map(\.rawValue) == ["SYSTEM", "FROST", "SUNRISE", "MIDNIGHT", "ESPRESSO"])
    }
}
