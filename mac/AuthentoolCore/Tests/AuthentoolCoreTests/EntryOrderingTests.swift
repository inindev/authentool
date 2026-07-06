import Testing
import Foundation
@testable import AuthentoolCore

@Suite("Entry ordering")
struct EntryOrderingTests {
    // A valid Base32 seed; the actual value is irrelevant to ordering, only the name matters.
    private let seed = "JBSWY3DPEHPK3PXP"

    private func entries(_ names: [String]) -> [AuthEntry] {
        names.map { AuthEntry(name: $0, seed: seed) }
    }

    @Test func manualPreservesStoredOrder() {
        let input = entries(["Zebra", "alpha", "Mango"])
        let result = EntryOrdering.sorted(input, by: .manual)
        #expect(result.map(\.name) == ["Zebra", "alpha", "Mango"])
    }

    @Test func alphabeticalSortsByName() {
        let input = entries(["Zebra", "alpha", "Mango"])
        let result = EntryOrdering.sorted(input, by: .alphabetical)
        #expect(result.map(\.name) == ["alpha", "Mango", "Zebra"])
    }

    @Test func alphabeticalIsCaseInsensitive() {
        let input = entries(["banana", "Apple", "cherry", "Berry"])
        let result = EntryOrdering.sorted(input, by: .alphabetical)
        #expect(result.map(\.name) == ["Apple", "banana", "Berry", "cherry"])
    }

    @Test func alphabeticalUsesNaturalNumericOrdering() {
        // localizedStandardCompare orders embedded numbers numerically, not lexically.
        let input = entries(["Item 10", "Item 2", "Item 1"])
        let result = EntryOrdering.sorted(input, by: .alphabetical)
        #expect(result.map(\.name) == ["Item 1", "Item 2", "Item 10"])
    }

    @Test func alphabeticalIsStableForEqualNames() {
        // Equal names keep their original relative order; ids distinguish them.
        let a = AuthEntry(name: "Same", seed: seed)
        let b = AuthEntry(name: "Same", seed: seed)
        let c = AuthEntry(name: "Same", seed: seed)
        let result = EntryOrdering.sorted([a, b, c], by: .alphabetical)
        #expect(result.map(\.id) == [a.id, b.id, c.id])
    }

    @Test func alphabeticalDoesNotMutateInput() {
        let input = entries(["Zebra", "alpha", "Mango"])
        _ = EntryOrdering.sorted(input, by: .alphabetical)
        #expect(input.map(\.name) == ["Zebra", "alpha", "Mango"])
    }

    @Test func emptyAndSingleAreHandled() {
        #expect(EntryOrdering.sorted([], by: .alphabetical).isEmpty)
        let one = entries(["Solo"])
        #expect(EntryOrdering.sorted(one, by: .alphabetical).map(\.name) == ["Solo"])
    }

    @Test func rawValuesAreStable() {
        #expect(SortMode.allCases.map(\.rawValue) == ["MANUAL", "ALPHABETICAL"])
    }
}
