import Foundation

// Pure ordering for the entry grid. Display-only: it returns a reordered copy and never mutates
// the source, so the store's 'manual' order is preserved while an alphabetical view is shown.
public enum EntryOrdering {
    /// Returns 'entries' arranged for the given mode. 'manual' preserves stored order;
    /// 'alphabetical' sorts by name using 'localizedStandardCompare' (case-insensitive, natural
    /// numeric ordering), with the original index as a stable tie-breaker for equal names.
    public static func sorted(_ entries: [AuthEntry], by mode: SortMode) -> [AuthEntry] {
        switch mode {
        case .manual:
            return entries
        case .alphabetical:
            return entries.enumerated()
                .sorted { lhs, rhs in
                    switch lhs.element.name.localizedStandardCompare(rhs.element.name) {
                    case .orderedAscending: return true
                    case .orderedDescending: return false
                    case .orderedSame: return lhs.offset < rhs.offset
                    }
                }
                .map(\.element)
        }
    }
}
