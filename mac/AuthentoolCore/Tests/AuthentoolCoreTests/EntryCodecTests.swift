import Testing
import Foundation
@testable import AuthentoolCore

@Suite("Entry JSON codec")
struct EntryCodecTests {
    @Test func encodesNameSeedSchemaWithoutId() throws {
        let entries = [
            AuthEntry(name: "GitHub", seed: "MZXW6YTBOJXXEABC"),
            AuthEntry(name: "Example", seed: "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ"),
        ]
        let json = try EntryCodec.encode(entries)
        #expect(json == #"[{"name":"GitHub","seed":"MZXW6YTBOJXXEABC"},{"name":"Example","seed":"GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ"}]"#)
    }

    @Test func decodesSharedSchema() throws {
        let json = #"[{"name":"GitHub","seed":"MZXW6YTBOJXXEABC"}]"#
        let entries = try EntryCodec.decode(json)
        #expect(entries.count == 1)
        #expect(entries[0].name == "GitHub")
        #expect(entries[0].seed == "MZXW6YTBOJXXEABC")
    }

    @Test func roundTripsPreservingOrder() throws {
        let entries = [
            AuthEntry(name: "A", seed: "MZXW6YTBOJXXEABC"),
            AuthEntry(name: "B", seed: "JBSWY3DPEHPK3PXP"),
            AuthEntry(name: "C", seed: "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ"),
        ]
        let decoded = try EntryCodec.decode(try EntryCodec.encode(entries))
        #expect(decoded.map(\.name) == ["A", "B", "C"])
        #expect(decoded.map(\.seed) == entries.map(\.seed))
    }

    @Test func decodeRegeneratesDistinctIds() throws {
        let decoded = try EntryCodec.decode(#"[{"name":"A","seed":"MY"},{"name":"A","seed":"MY"}]"#)
        // ids are transient; even identical name/seed get distinct identities.
        #expect(decoded[0].id != decoded[1].id)
    }

    @Test func decodesEmptyArray() throws {
        #expect(try EntryCodec.decode("[]").isEmpty)
    }
}
