import Testing
import Foundation
@testable import AuthentoolCore

// Exercises the real Keychain adapter against the file-based login keychain (useDataProtection
// = false), which a non-entitled test process can reach. The app itself uses the data-protection
// keychain; the SecItem code path is identical, only the backend differs. Each test uses a unique
// service name and cleans up. If the keychain is unavailable (restricted CI), the test skips.
@Suite("KeychainEntryStorage (login keychain)")
struct KeychainEntryStorageTests {

    /// Makes an isolated storage and confirms the keychain is usable here; returns nil to skip.
    private func makeUsableStorage() -> KeychainEntryStorage? {
        let storage = KeychainEntryStorage(
            service: "authentool-test-\(UUID().uuidString)",
            account: "entries",
            useDataProtection: false
        )
        do {
            try storage.save([]) // probe write
            return storage
        } catch {
            return nil // keychain not accessible in this environment
        }
    }

    @Test func loadReturnsEmptyWhenNothingStored() throws {
        guard let storage = makeUsableStorage() else { return }
        defer { try? storage.deleteAll() }
        // We probed with save([]); an empty list must read back as empty.
        #expect(try storage.load().isEmpty)
    }

    @Test func savePersistsAcrossInstances() throws {
        let service = "authentool-test-\(UUID().uuidString)"
        let writer = KeychainEntryStorage(service: service, account: "entries", useDataProtection: false)
        do { try writer.save([]) } catch { return } // skip if keychain unavailable
        defer { try? writer.deleteAll() }

        let entries = [
            AuthEntry(name: "GitHub", seed: "MZXW6YTBOJXXEABC"),
            AuthEntry(name: "Example", seed: "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ"),
        ]
        try writer.save(entries)

        // A brand-new instance (no shared memory) must read the same data back from the keychain.
        let reader = KeychainEntryStorage(service: service, account: "entries", useDataProtection: false)
        let loaded = try reader.load()
        #expect(loaded.map(\.name) == ["GitHub", "Example"])
        #expect(loaded.map(\.seed) == entries.map(\.seed))
    }

    @Test func saveOverwritesExisting() throws {
        guard let storage = makeUsableStorage() else { return }
        defer { try? storage.deleteAll() }

        try storage.save([AuthEntry(name: "First", seed: "MZXW6YTBOJXXEABC")])
        try storage.save([AuthEntry(name: "Second", seed: "JBSWY3DPEHPK3PXP")])
        let loaded = try storage.load()
        #expect(loaded.map(\.name) == ["Second"])
    }

    @Test func deleteAllRemovesItem() throws {
        guard let storage = makeUsableStorage() else { return }
        try storage.save([AuthEntry(name: "Temp", seed: "MY")])
        try storage.deleteAll()
        #expect(try storage.load().isEmpty)
    }

    @Test func roundTripsThroughAuthStore() async throws {
        let service = "authentool-test-\(UUID().uuidString)"
        let probe = KeychainEntryStorage(service: service, account: "entries", useDataProtection: false)
        do { try probe.save([]) } catch { return }
        defer { try? probe.deleteAll() }

        // Full stack: AuthStore -> KeychainEntryStorage -> reload into a fresh AuthStore.
        try await MainActor.run {
            let store = AuthStore(storage: KeychainEntryStorage(service: service, account: "entries", useDataProtection: false))
            try store.add(name: "Work", seed: "JBSWY3DPEHPK3PXP")
            let reloaded = AuthStore(storage: KeychainEntryStorage(service: service, account: "entries", useDataProtection: false))
            #expect(reloaded.entries.map(\.name) == ["Work"])
        }
    }
}
