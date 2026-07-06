//
//  RenameEntryView.swift
//  authentool
//
//  Sheet for renaming an entry. Blank names are disabled at the button level; the store
//  also treats a blank rename as a no-op.
//

import SwiftUI
import AuthentoolCore

struct RenameEntryView: View {
    let entry: AuthEntry
    let onRename: (_ newName: String) -> Void
    @Environment(\.dismiss) private var dismiss

    @State private var name: String

    init(entry: AuthEntry, onRename: @escaping (_ newName: String) -> Void) {
        self.entry = entry
        self.onRename = onRename
        _name = State(initialValue: entry.name)
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            Text("Rename Entry").font(.headline)
            TextField("Name", text: $name)
                .textFieldStyle(.roundedBorder)
            HStack {
                Spacer()
                Button("Cancel", role: .cancel) { dismiss() }
                    .keyboardShortcut(.cancelAction)
                Button("Save") {
                    onRename(name)
                    dismiss()
                }
                .keyboardShortcut(.defaultAction)
                .disabled(name.trimmingCharacters(in: .whitespaces).isEmpty)
            }
        }
        .padding(20)
        .frame(width: 360)
    }
}
