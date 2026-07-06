import Testing
import Foundation
@testable import AuthentoolCore

@MainActor
@Suite("AuthStore mutations")
struct AuthStoreTests {

    private func makeStore(_ entries: [AuthEntry] = []) -> (AuthStore, InMemoryEntryStorage) {
        let storage = InMemoryEntryStorage(entries)
        return (AuthStore(storage: storage), storage)
    }

    // MARK: load

    @Test func loadsExistingEntriesOnInit() {
        let (store, _) = makeStore([AuthEntry(name: "A", seed: "MZXW6YTBOJXXEABC")])
        #expect(store.entries.count == 1)
        #expect(store.entries[0].name == "A")
    }

    // MARK: add

    @Test func addNormalizesSeedAndPersists() throws {
        let (store, storage) = makeStore()
        try store.add(name: "  GitHub  ", seed: "mzxw 6ytb ojxx eabc")
        #expect(store.entries.count == 1)
        #expect(store.entries[0].name == "GitHub")     // trimmed
        #expect(store.entries[0].seed == "MZXW6YTBOJXXEABC") // normalized
        #expect(storage.saveCount == 1)
    }

    @Test func addRejectsBlankName() {
        let (store, _) = makeStore()
        #expect(throws: AuthStore.EntryError.blankName) {
            try store.add(name: "   ", seed: "MZXW6YTBOJXXEABC")
        }
        #expect(store.entries.isEmpty)
    }

    @Test func addRejectsInvalidSeed() {
        let (store, _) = makeStore()
        #expect(throws: AuthStore.EntryError.invalidSeed(.invalidCharacters)) {
            try store.add(name: "Bad", seed: "MZXW6Y10")
        }
        #expect(store.entries.isEmpty)
    }

    // MARK: rename

    @Test func renameUpdatesNameAndPersists() throws {
        let (store, storage) = makeStore()
        let entry = try store.add(name: "Old", seed: "MZXW6YTBOJXXEABC")
        let before = storage.saveCount
        #expect(store.rename(entry.id, to: "  New  "))
        #expect(store.entries[0].name == "New")
        #expect(storage.saveCount == before + 1)
    }

    @Test func renameToBlankIsNoOp() throws {
        let (store, storage) = makeStore()
        let entry = try store.add(name: "Keep", seed: "MZXW6YTBOJXXEABC")
        let before = storage.saveCount
        #expect(store.rename(entry.id, to: "   ") == false)
        #expect(store.entries[0].name == "Keep")
        #expect(storage.saveCount == before) // no persist on no-op
    }

    @Test func renameUnchangedNameIsNoOp() throws {
        let (store, _) = makeStore()
        let entry = try store.add(name: "Same", seed: "MZXW6YTBOJXXEABC")
        #expect(store.rename(entry.id, to: "Same") == false)
    }

    // MARK: delete

    @Test func deleteRemovesEntry() throws {
        let (store, _) = makeStore()
        let a = try store.add(name: "A", seed: "MZXW6YTBOJXXEABC")
        try store.add(name: "B", seed: "JBSWY3DPEHPK3PXP")
        #expect(store.delete(a.id))
        #expect(store.entries.map(\.name) == ["B"])
    }

    @Test func deleteUnknownIdIsNoOp() {
        let (store, _) = makeStore([AuthEntry(name: "A", seed: "MY")])
        #expect(store.delete(UUID()) == false)
        #expect(store.entries.count == 1)
    }

    // MARK: move

    @Test func moveSwapsWithNeighbour() throws {
        let (store, _) = makeStore()
        let a = try store.add(name: "A", seed: "MY")
        try store.add(name: "B", seed: "MY")
        // 2-column grid: [A B] -> move A right -> [B A]
        #expect(store.move(a.id, .right))
        #expect(store.entries.map(\.name) == ["B", "A"])
    }

    @Test func moveAtEdgeIsNoOp() throws {
        let (store, storage) = makeStore()
        let a = try store.add(name: "A", seed: "MY")
        try store.add(name: "B", seed: "MY")
        let before = storage.saveCount
        #expect(store.move(a.id, .left) == false) // A already in first column
        #expect(store.entries.map(\.name) == ["A", "B"])
        #expect(storage.saveCount == before)
    }

    // MARK: drag reorder

    @Test func moveEntriesReordersForwardLikeOnMove() throws {
        let (store, _) = makeStore()
        let a = try store.add(name: "A", seed: "MY")
        try store.add(name: "B", seed: "MY")
        try store.add(name: "C", seed: "MY")
        // Drag A (index 0) to offset 2 -> [B, A, C], matching SwiftUI onMove.
        #expect(store.moveEntries(fromOffsets: IndexSet(integer: 0), toOffset: 2))
        #expect(store.entries.map(\.name) == ["B", "A", "C"])
        _ = a
    }

    @Test func moveEntriesReordersBackward() throws {
        let (store, _) = makeStore()
        try store.add(name: "A", seed: "MY")
        try store.add(name: "B", seed: "MY")
        try store.add(name: "C", seed: "MY")
        // Drag C (index 2) to offset 0 -> [C, A, B].
        #expect(store.moveEntries(fromOffsets: IndexSet(integer: 2), toOffset: 0))
        #expect(store.entries.map(\.name) == ["C", "A", "B"])
    }

    @Test func moveEntriesNoOpDoesNotPersist() throws {
        let (store, storage) = makeStore()
        try store.add(name: "A", seed: "MY")
        try store.add(name: "B", seed: "MY")
        let before = storage.saveCount
        // Moving index 0 to offset 0 leaves order unchanged.
        #expect(store.moveEntries(fromOffsets: IndexSet(integer: 0), toOffset: 0) == false)
        #expect(store.entries.map(\.name) == ["A", "B"])
        #expect(storage.saveCount == before)
    }

    // MARK: import

    @Test func importReplaceOverwrites() throws {
        let (store, _) = makeStore()
        try store.add(name: "Existing", seed: "MZXW6YTBOJXXEABC")
        let imported = [AuthEntry(name: "New", seed: "JBSWY3DPEHPK3PXP")]
        let count = store.importEntries(imported, mode: .replace)
        #expect(count == 1)
        #expect(store.entries.map(\.name) == ["New"])
    }

    @Test func importMergeAddsOnlyNewByNameAndSeed() throws {
        let (store, _) = makeStore()
        try store.add(name: "GitHub", seed: "MZXW6YTBOJXXEABC")
        let imported = [
            AuthEntry(name: "GitHub", seed: "MZXW6YTBOJXXEABC"), // duplicate -> skipped
            AuthEntry(name: "GitHub", seed: "JBSWY3DPEHPK3PXP"), // same name, diff seed -> added
            AuthEntry(name: "New", seed: "JBSWY3DPEHPK3PXP"),    // new -> added
        ]
        let count = store.importEntries(imported, mode: .merge)
        #expect(count == 3) // returns size of imported set, Android-compatible
        #expect(store.entries.count == 3)
        #expect(store.entries.map(\.name) == ["GitHub", "GitHub", "New"])
    }

    // MARK: persistence failure surfacing

    @Test func persistenceFailureSetsLastError() throws {
        let storage = FailingStorage()
        let store = AuthStore(storage: storage)
        try store.add(name: "A", seed: "MY")
        #expect(store.lastError == "Failed to save changes.")
        #expect(store.entries.count == 1) // in-memory mutation still applied
    }
}

private final class FailingStorage: EntryStorage, @unchecked Sendable {
    struct Boom: Error {}
    func load() throws -> [AuthEntry] { [] }
    func save(_ entries: [AuthEntry]) throws { throw Boom() }
}
