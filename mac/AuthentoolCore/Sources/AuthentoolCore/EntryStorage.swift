// Persistence boundary for the entry list. The store depends only on this protocol, which
// keeps the store unit-testable with an in-memory fake and lets the Keychain-backed
// implementation be swapped in without touching store logic.

public protocol EntryStorage: AnyObject, Sendable {
    func load() throws -> [AuthEntry]
    func save(_ entries: [AuthEntry]) throws
}

/// In-memory storage for tests and previews. Not for production use (no encryption).
public final class InMemoryEntryStorage: EntryStorage, @unchecked Sendable {
    private var entries: [AuthEntry]
    public private(set) var saveCount = 0

    public init(_ entries: [AuthEntry] = []) {
        self.entries = entries
    }

    public func load() throws -> [AuthEntry] { entries }

    public func save(_ entries: [AuthEntry]) throws {
        self.entries = entries
        saveCount += 1
    }
}
