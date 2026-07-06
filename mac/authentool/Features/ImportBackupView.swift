//
//  ImportBackupView.swift
//  authentool
//
//  Sheet shown after a backup file is chosen: collects the password and the merge/replace mode,
//  then asks 'onImport' to decrypt and apply it. Wrong-password / corrupt-file
//  failures are reported inline so the user can retry without losing the picked file.
//

import SwiftUI
import AuthentoolCore

struct ImportBackupView: View {
    /// Decrypts and applies the backup; throws on wrong password / corrupt file.
    let onImport: (_ password: String, _ mode: AuthStore.ImportMode) throws -> Void
    @Environment(\.dismiss) private var dismiss

    @State private var password = ""
    @State private var mode: AuthStore.ImportMode = .replace
    @State private var errorMessage: String?

    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            Text("Import Backup").font(.headline)
            Text("Enter the password used to create this backup.")
                .font(.caption)
                .foregroundStyle(.secondary)

            SecureField("Password", text: $password)
                .textFieldStyle(.roundedBorder)

            Picker("Mode", selection: $mode) {
                Text("Replace existing entries").tag(AuthStore.ImportMode.replace)
                Text("Merge with existing").tag(AuthStore.ImportMode.merge)
            }
            .pickerStyle(.radioGroup)

            if let errorMessage {
                Text(errorMessage).font(.caption).foregroundStyle(.red)
            }

            HStack {
                Spacer()
                Button("Cancel", role: .cancel) { dismiss() }
                    .keyboardShortcut(.cancelAction)
                Button("Import") { attemptImport() }
                    .keyboardShortcut(.defaultAction)
                    .disabled(password.isEmpty)
            }
        }
        .padding(20)
        .frame(width: 380)
    }

    private func attemptImport() {
        do {
            try onImport(password, mode)
            // Drop the password from view state once it's been used.
            password = ""
            dismiss()
        } catch {
            errorMessage = "Wrong password, or the file isn't a valid backup."
        }
    }
}
