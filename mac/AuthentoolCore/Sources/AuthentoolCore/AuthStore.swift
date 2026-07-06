import Foundation
import Observation

// The single source of truth for the entry list. An @Observable, @MainActor class that owns
// the entries and exposes the mutations the UI drives (add / rename / delete / move / import),
// mirroring the Android command set but as plain, directly-testable methods. Every mutation
// that changes the list persists it through the injected EntryStorage.
//
// All seeds are normalized + validated before they enter the list, so AuthEntry.code() is
// reliable downstream.

@MainActor
@Observable
public final class AuthStore {
    public private(set) var entries: [AuthEntry] = []
    /// Last non-fatal problem (e.g. a persistence failure), for surfacing in the UI.
    public var lastError: String?

    public let columns: Int
    private let storage: EntryStorage

    public enum ImportMode: Sendable {
        case merge   // add entries not already present (by name+seed)
        case replace // discard existing, use imported
    }

    public enum EntryError: Error, Equatable {
        case blankName
        case invalidSeed(SeedValidation.Failure)
    }

    public init(storage: EntryStorage, columns: Int = 2) {
        self.storage = storage
        self.columns = columns
        do {
            entries = try storage.load()
        } catch {
            entries = []
            lastError = "Failed to load saved codes."
        }
    }

    // MARK: - Mutations

    /// Adds a new entry. Name is trimmed (must be non-blank); seed is normalized + validated.
    @discardableResult
    public func add(name: String, seed: String) throws -> AuthEntry {
        let trimmedName = name.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmedName.isEmpty else { throw EntryError.blankName }
        let normalizedSeed: String
        do {
            normalizedSeed = try SeedValidation.validated(seed)
        } catch let failure as SeedValidation.Failure {
            throw EntryError.invalidSeed(failure)
        }
        let entry = AuthEntry(name: trimmedName, seed: normalizedSeed)
        entries.append(entry)
        persist()
        return entry
    }

    /// Renames an entry. Blank names are ignored (no-op), matching the Android behavior.
    @discardableResult
    public func rename(_ id: AuthEntry.ID, to newName: String) -> Bool {
        let trimmed = newName.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty, let index = entries.firstIndex(where: { $0.id == id }) else {
            return false
        }
        guard entries[index].name != trimmed else { return false }
        entries[index].name = trimmed
        persist()
        return true
    }

    @discardableResult
    public func delete(_ id: AuthEntry.ID) -> Bool {
        guard let index = entries.firstIndex(where: { $0.id == id }) else { return false }
        entries.remove(at: index)
        persist()
        return true
    }

    /// Moves an entry one step in the grid, swapping with its neighbour. No-op at edges.
    @discardableResult
    public func move(_ id: AuthEntry.ID, _ direction: GridDirection) -> Bool {
        guard let index = entries.firstIndex(where: { $0.id == id }),
              let target = GridLayout.targetIndex(from: index, direction: direction,
                                                  count: entries.count, columns: columns)
        else { return false }
        entries.swapAt(index, target)
        persist()
        return true
    }

    /// Reorders entries using SwiftUI 'onMove' semantics (drag-to-reorder). No-op (no persist)
    /// if the order is unchanged.
    @discardableResult
    public func moveEntries(fromOffsets source: IndexSet, toOffset destination: Int) -> Bool {
        let before = entries
        let moving = source.map { entries[$0] }
        var result = entries
        for index in source.sorted(by: >) { result.remove(at: index) }
        let adjustedDestination = destination - source.filter { $0 < destination }.count
        result.insert(contentsOf: moving, at: adjustedDestination)
        entries = result
        guard entries != before else { return false }
        persist()
        return true
    }

    /// Imports entries. '.replace' overwrites the list; '.merge' appends entries not already
    /// present (compared by name+seed). Returns the number of entries in the imported set.
    @discardableResult
    public func importEntries(_ imported: [AuthEntry], mode: ImportMode) -> Int {
        switch mode {
        case .replace:
            entries = imported
        case .merge:
            let additions = imported.filter { candidate in
                !entries.contains { $0.name == candidate.name && $0.seed == candidate.seed }
            }
            entries.append(contentsOf: additions)
        }
        persist()
        return imported.count
    }

    // MARK: - Persistence

    private func persist() {
        do {
            try storage.save(entries)
        } catch {
            lastError = "Failed to save changes."
        }
    }
}
