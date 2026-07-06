import Foundation
import Security

// The production EntryStorage: persists the entry list in the macOS Keychain as a single
// generic-password item holding the shared JSON payload. The Keychain
// provides encryption at rest; accessibility is WhenUnlockedThisDeviceOnly so secrets never
// sync off-device and are unreadable while the Mac is locked.
//
// This is the one I/O adapter in the package - the deterministic core (TOTP/crypto/store)
// stays pure. The store depends only on the EntryStorage protocol, so this class is
// substitutable by InMemoryEntryStorage in unit tests.
public final class KeychainEntryStorage: EntryStorage, @unchecked Sendable {
    public enum KeychainError: Error, Equatable {
        case unexpectedStatus(OSStatus)
        case dataCorrupted
    }

    private let service: String
    private let account: String
    // The app uses the data-protection keychain (required under App Sandbox). Tests target
    // the file-based login keychain, which a non-entitled test process can access.
    private let useDataProtection: Bool

    public init(
        service: String = "com.github.inindev.authentool",
        account: String = "auth_codes_list",
        useDataProtection: Bool = true
    ) {
        self.service = service
        self.account = account
        self.useDataProtection = useDataProtection
    }

    private func baseQuery() -> [String: Any] {
        var query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
        ]
        if useDataProtection {
            query[kSecUseDataProtectionKeychain as String] = true
        }
        return query
    }

    public func load() throws -> [AuthEntry] {
        var query = baseQuery()
        query[kSecReturnData as String] = true
        query[kSecMatchLimit as String] = kSecMatchLimitOne

        var item: CFTypeRef?
        let status = SecItemCopyMatching(query as CFDictionary, &item)
        if status == errSecItemNotFound { return [] }
        guard status == errSecSuccess else { throw KeychainError.unexpectedStatus(status) }
        guard let data = item as? Data, let json = String(data: data, encoding: .utf8) else {
            throw KeychainError.dataCorrupted
        }
        return try EntryCodec.decode(json)
    }

    public func save(_ entries: [AuthEntry]) throws {
        let data = Data(try EntryCodec.encode(entries).utf8)

        // Update the existing item if present, otherwise add a new one.
        let updateStatus = SecItemUpdate(
            baseQuery() as CFDictionary,
            [kSecValueData as String: data] as CFDictionary
        )
        if updateStatus == errSecSuccess { return }
        guard updateStatus == errSecItemNotFound else {
            throw KeychainError.unexpectedStatus(updateStatus)
        }

        var addQuery = baseQuery()
        addQuery[kSecValueData as String] = data
        addQuery[kSecAttrAccessible as String] = kSecAttrAccessibleWhenUnlockedThisDeviceOnly
        let addStatus = SecItemAdd(addQuery as CFDictionary, nil)
        guard addStatus == errSecSuccess else { throw KeychainError.unexpectedStatus(addStatus) }
    }

    /// Removes the stored item. Used for test cleanup and a future "reset" affordance.
    public func deleteAll() throws {
        let status = SecItemDelete(baseQuery() as CFDictionary)
        guard status == errSecSuccess || status == errSecItemNotFound else {
            throw KeychainError.unexpectedStatus(status)
        }
    }
}
