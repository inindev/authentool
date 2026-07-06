//
//  BackupDocument.swift
//  authentool
//
//  A trivial text FileDocument wrapping the Base64 '.enc' backup blob,
//  used by the SwiftUI fileExporter when saving a backup. The blob is produced by
//  CryptoBox.encrypt; this type just carries it to/from disk as UTF-8.
//

import SwiftUI
import UniformTypeIdentifiers

extension UTType {
    /// The '.enc' backup file type. Falls back to plain data if the extension can't be resolved.
    static var authentoolBackup: UTType { UTType(filenameExtension: "enc") ?? .data }
}

struct BackupDocument: FileDocument {
    static var readableContentTypes: [UTType] { [.authentoolBackup, .plainText, .data] }
    static var writableContentTypes: [UTType] { [.authentoolBackup] }

    var text: String

    init(text: String) { self.text = text }

    init(configuration: ReadConfiguration) throws {
        guard let data = configuration.file.regularFileContents,
              let string = String(data: data, encoding: .utf8)
        else { throw CocoaError(.fileReadCorruptFile) }
        text = string
    }

    func fileWrapper(configuration: WriteConfiguration) throws -> FileWrapper {
        FileWrapper(regularFileWithContents: Data(text.utf8))
    }
}
