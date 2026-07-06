import Foundation

// How the entry grid is ordered for display (separate from the act of reordering). 'manual' is
// the user's saved arrangement - the canonical stored order; 'alphabetical' is a display-only
// sort by name that never mutates the stored order, so switching back to 'manual' restores the
// user's arrangement exactly. Modeled like ThemeMode and persisted the same way.
public enum SortMode: String, CaseIterable, Codable, Sendable {
    case manual = "MANUAL"
    case alphabetical = "ALPHABETICAL"

    /// Human-readable label for the Sort By picker.
    public var displayName: String {
        switch self {
        case .manual: return "Manual"
        case .alphabetical: return "Alphabetical"
        }
    }
}
