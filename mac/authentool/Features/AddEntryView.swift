//
//  AddEntryView.swift
//  authentool
//
//  Sheet for adding an entry. Validation uses the core SeedValidation so the error
//  messages match the rules the store enforces.
//

import SwiftUI
import AuthentoolCore

struct AddEntryView: View {
    let onAdd: (_ name: String, _ seed: String) throws -> Void
    @Environment(\.dismiss) private var dismiss

    @State private var name: String
    @State private var seed: String
    @State private var errorMessage: String?

    /// Optionally prefill the fields - used by Scan QR so the scanned name/seed can be tweaked
    /// before the entry is created.
    init(
        initialName: String = "",
        initialSeed: String = "",
        onAdd: @escaping (_ name: String, _ seed: String) throws -> Void
    ) {
        self.onAdd = onAdd
        _name = State(initialValue: initialName)
        _seed = State(initialValue: initialSeed)
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            Text("Add Authenticator Entry").font(.headline)

            // Persistent caption labels (always visible, incl. when prefilled by Scan QR); the
            // in-field prompt is a hint/example, not a duplicate of the label. The TextField title
            // stays as the accessibility label.
            VStack(alignment: .leading, spacing: 4) {
                Text("Name").font(.caption).foregroundStyle(.secondary)
                TextField("Name", text: $name, prompt: Text("e.g. GitHub"))
                    .labelsHidden()
                    .textFieldStyle(.roundedBorder)
            }

            VStack(alignment: .leading, spacing: 4) {
                Text("Base32 seed").font(.caption).foregroundStyle(.secondary)
                TextField("Base32 seed", text: $seed, prompt: Text("A-Z, 2-7"))
                    .labelsHidden()
                    .textFieldStyle(.roundedBorder)
                    .autocorrectionDisabled()
                    .font(.system(.body, design: .monospaced))
            }

            if let errorMessage {
                Text(errorMessage)
                    .font(.caption)
                    .foregroundStyle(.red)
            }

            HStack {
                Spacer()
                Button("Cancel", role: .cancel) { dismiss() }
                    .keyboardShortcut(.cancelAction)
                Button("Add") { attemptAdd() }
                    .keyboardShortcut(.defaultAction)
                    .disabled(name.trimmingCharacters(in: .whitespaces).isEmpty || seed.isEmpty)
            }
        }
        .padding(20)
        .frame(width: 380)
    }

    private func attemptAdd() {
        do {
            try onAdd(name, seed)
            dismiss()
        } catch let error as AuthStore.EntryError {
            errorMessage = Self.message(for: error)
        } catch {
            errorMessage = "Could not add entry."
        }
    }

    static func message(for error: AuthStore.EntryError) -> String {
        switch error {
        case .blankName:
            return "Name cannot be empty."
        case .invalidSeed(.empty):
            return "Seed cannot be empty."
        case .invalidSeed(.invalidCharacters):
            return "Seed must be valid Base32 (A-Z, 2-7)."
        case .invalidSeed(.invalidLength):
            return "Seed length is not valid Base32."
        }
    }
}
