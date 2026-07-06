import Foundation

// Pure helpers describing the shared TOTP time window, so the countdown indicator and code
// refresh are driven by testable math rather than ad-hoc date arithmetic in the view.
public enum TOTPWindow {
    public static let timeStep = 30

    /// The window counter (which 30s bucket) for a given moment. Codes change when this changes.
    public static func counter(at date: Date, timeStep: Int = timeStep) -> Int64 {
        Int64(floor(date.timeIntervalSince1970)) / Int64(timeStep)
    }

    /// Fraction of the current window remaining, in (0, 1]. 1.0 at the start of a window,
    /// approaching 0 as it ends.
    public static func remainingFraction(at date: Date, timeStep: Int = timeStep) -> Double {
        let step = Double(timeStep)
        let position = date.timeIntervalSince1970.truncatingRemainder(dividingBy: step)
        let normalized = position < 0 ? position + step : position // handle pre-epoch
        return 1.0 - (normalized / step)
    }
}
