import Foundation

// JSON serialization for the entry list, matching the shared schema:
// a JSON array of {"name","seed"} objects, order preserved. This is the exact payload that
// is stored at rest and encrypted into backups, so it must stay compatible
// with the Android app and CLI tools.

public enum EntryCodec {
    public static func encode(_ entries: [AuthEntry]) throws -> String {
        let encoder = JSONEncoder()
        // Sorted keys give deterministic output and, since "name" < "seed", emit the same
        // name-then-seed key order as the Android app - easy to diff against the reference.
        encoder.outputFormatting = [.withoutEscapingSlashes, .sortedKeys]
        let data = try encoder.encode(entries)
        return String(decoding: data, as: UTF8.self)
    }

    public static func decode(_ json: String) throws -> [AuthEntry] {
        try JSONDecoder().decode([AuthEntry].self, from: Data(json.utf8))
    }
}
