//
//  Theme.swift
//  authentool
//
//  The five color schemes, with values ported from the Android app's Color.kt so the
//  two apps look the same. 'system' resolves to Sunrise (light) or Espresso (dark) to match
//  Android's SYSTEM behavior.
//

import SwiftUI
import AuthentoolCore

struct Palette {
    let appText: Color
    let appBackground: Color
    let cardName: Color
    let cardTotp: Color
    let cardBackground: Color
    let cardHiName: Color
    let cardHiTotp: Color
    let cardHiBackground: Color
    let progressTrack: Color
    let progressFill: Color
    let topBarBackground: Color
    let topBarText: Color
    let isDark: Bool
}

extension Palette {
    static let frost = Palette(
        appText: Color(rgb: 0x000000),
        appBackground: Color(rgb: 0xFFFFFF),
        cardName: Color(rgb: 0x49454F),
        cardTotp: Color(rgb: 0x1976D2),
        cardBackground: Color(rgb: 0xF5F5F5),
        cardHiName: Color(rgb: 0x49454F),
        cardHiTotp: Color(rgb: 0x1976D2),
        cardHiBackground: Color(rgb: 0x40C4FF),
        progressTrack: Color(rgb: 0xE0E0E0),
        progressFill: Color(rgb: 0x1976D2),
        topBarBackground: Color(rgb: 0xE0E0E0),
        topBarText: Color(rgb: 0x000000),
        isDark: false
    )

    static let sunrise = Palette(
        appText: Color(rgb: 0x1A1A2E),
        appBackground: Color(rgb: 0xFFFFFF),
        cardName: Color(rgb: 0x49454F),
        cardTotp: Color(rgb: 0x3D45AA),
        cardBackground: Color(rgb: 0xF0EDE6),
        cardHiName: Color(rgb: 0x2A2F78),
        cardHiTotp: Color(rgb: 0xDA3D20),
        cardHiBackground: Color(rgb: 0xFFE89A),
        progressTrack: Color(rgb: 0xE0DDD6),
        progressFill: Color(rgb: 0xF8843F),
        topBarBackground: Color(rgb: 0xDDDFE8),
        topBarText: Color(rgb: 0x1A1A2E),
        isDark: false
    )

    static let midnight = Palette(
        appText: Color(rgb: 0xE0E0E0),
        appBackground: Color(rgb: 0x121212),
        cardName: Color(rgb: 0xCAC4D0),
        cardTotp: Color(rgb: 0x40C4FF),
        cardBackground: Color(rgb: 0x2C2C2C),
        cardHiName: Color(rgb: 0x49454F),
        cardHiTotp: Color(rgb: 0x2C2C2C),
        cardHiBackground: Color(rgb: 0x5B8655),
        progressTrack: Color(rgb: 0x616161),
        progressFill: Color(rgb: 0x40C4FF),
        topBarBackground: Color(rgb: 0x1E1E1E),
        topBarText: Color(rgb: 0xE0E0E0),
        isDark: true
    )

    static let espresso = Palette(
        appText: Color(rgb: 0xEED9B9),
        appBackground: Color(rgb: 0x120D08),
        cardName: Color(rgb: 0xCBB99A),
        cardTotp: Color(rgb: 0xEED9B9),
        cardBackground: Color(rgb: 0x412D15),
        cardHiName: Color(rgb: 0x412D15),
        cardHiTotp: Color(rgb: 0x332310),
        cardHiBackground: Color(rgb: 0xB5A07E),
        progressTrack: Color(rgb: 0x1E1812),
        progressFill: Color(rgb: 0xD2773F),
        topBarBackground: Color(rgb: 0x1E1610),
        topBarText: Color(rgb: 0xEED9B9),
        isDark: true
    )

    static func resolved(for mode: ThemeMode, systemDark: Bool) -> Palette {
        switch mode {
        case .system: return systemDark ? .espresso : .sunrise
        case .frost: return .frost
        case .sunrise: return .sunrise
        case .midnight: return .midnight
        case .espresso: return .espresso
        }
    }
}

extension Color {
    /// Builds a Color from a 0xRRGGBB integer (sRGB), matching the Android hex literals.
    init(rgb: UInt) {
        self.init(
            .sRGB,
            red: Double((rgb >> 16) & 0xFF) / 255.0,
            green: Double((rgb >> 8) & 0xFF) / 255.0,
            blue: Double(rgb & 0xFF) / 255.0
        )
    }
}

private struct PaletteKey: EnvironmentKey {
    static let defaultValue = Palette.sunrise
}

extension EnvironmentValues {
    var palette: Palette {
        get { self[PaletteKey.self] }
        set { self[PaletteKey.self] = newValue }
    }
}
