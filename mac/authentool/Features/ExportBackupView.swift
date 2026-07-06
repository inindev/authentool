//
//  ExportBackupView.swift
//  authentool
//
//  Sheet that collects a backup password (with confirmation, min 8 chars) before
//  exporting. On confirm it hands the password to 'onExport', which encrypts the entries and
//  triggers the save panel. Crypto/encode errors surface inline.
//

import SwiftUI

struct ExportBackupView: View {
    /// Encrypts and arms the save panel with the given password. Throws on failure.
    let onExport: (_ password: String) throws -> Void
    @Environment(\.dismiss) private var dismiss

    @State private var password = ""
    @State private var confirm = ""
    @State private var errorMessage: String?

    private static let minLength = 8

    private var isValid: Bool {
        password.count >= Self.minLength && password == confirm
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            Text("Export Backup").font(.headline)
            Text("Choose a password to encrypt the backup. You'll need it to restore.")
                .font(.caption)
                .foregroundStyle(.secondary)

            SecureField("Password", text: $password)
                .textFieldStyle(.roundedBorder)
            SecureField("Confirm password", text: $confirm)
                .textFieldStyle(.roundedBorder)

            if let errorMessage {
                Text(errorMessage).font(.caption).foregroundStyle(.red)
            } else if !password.isEmpty && password.count < Self.minLength {
                Text("Password must be at least \(Self.minLength) characters.")
                    .font(.caption).foregroundStyle(.secondary)
            } else if !confirm.isEmpty && password != confirm {
                Text("Passwords don't match.")
                    .font(.caption).foregroundStyle(.secondary)
            }

            HStack {
                Spacer()
                Button("Cancel", role: .cancel) { dismiss() }
                    .keyboardShortcut(.cancelAction)
                Button("Export") { attemptExport() }
                    .keyboardShortcut(.defaultAction)
                    .disabled(!isValid)
            }
        }
        .padding(20)
        .frame(width: 380)
    }

    private func attemptExport() {
        do {
            try onExport(password)
            // Drop the password from view state once it's been used.
            password = ""
            confirm = ""
            dismiss()
        } catch {
            errorMessage = "Could not create the backup."
        }
    }
}
