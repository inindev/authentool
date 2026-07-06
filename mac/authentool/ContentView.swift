//
//  ContentView.swift
//  authentool
//
//  Root view: a themed, drag-reorderable list of entries with a single shared countdown
//  driving every code, plus add / rename / delete. Adding is via File -> New Entry (Cmd-N); theme
//  is in the View -> Theme menu. Binds to the proven AuthStore.
//

import SwiftUI
import AppKit
import UniformTypeIdentifiers
import AuthentoolCore

struct ContentView: View {
    let store: AuthStore
    let lock: LockModel

    @Environment(\.colorScheme) private var colorScheme
    // Tracks window/app focus so reorder mode collapses when the user clicks away to another app.
    @Environment(\.controlActiveState) private var controlActiveState

    // Bound to the same UserDefaults key as the core ThemePreference; the Settings window
    // writes it and this view reacts live.
    @AppStorage("theme_preference") private var themeMode: ThemeMode = .system
    // Display ordering, set from the View -> Sort By menu. Persisted; defaults to the user's
    // manual arrangement.
    @AppStorage("sort_mode") private var sortMode: SortMode = .manual

    // The grid is always 2 columns (matching Android), flexing to fill the window. The card
    // contents (fonts, padding) scale with the resulting column width, so resizing the window
    // sizes the tiles - 'scale = 1.0' at the reference column width below.
    private let gridColumns = 2
    private let gridSpacing: CGFloat = 12
    private let gridPadding: CGFloat = 16
    private let referenceCardWidth: CGFloat = 200
    private let scaleRange = 0.6...1.8

    /// The card scale for a given available grid width: derived from the per-column width relative
    /// to the reference, then clamped so extreme window sizes don't produce tiny/giant tiles.
    private func cardScale(forAvailableWidth width: CGFloat) -> Double {
        let columnWidth = (width - 2 * gridPadding - CGFloat(gridColumns - 1) * gridSpacing)
            / CGFloat(gridColumns)
        let raw = Double(columnWidth / referenceCardWidth)
        return min(max(raw, scaleRange.lowerBound), scaleRange.upperBound)
    }

    @State private var showAdd = false
    @State private var renameTarget: AuthEntry?
    @State private var deleteTarget: AuthEntry?
    @State private var copiedID: AuthEntry.ID?
    @State private var highlightTask: Task<Void, Never>?
    // Transient (not persisted): true while drag handles are shown for hand-arranging tiles.
    @State private var isReordering = false

    // Prefill for the Add dialog (used by Scan QR). 'addFormID' is refreshed each time the dialog
    // opens so AddEntryView re-initializes its @State from the current prefill values.
    @State private var prefillName = ""
    @State private var prefillSeed = ""
    @State private var addFormID = UUID()

    // Backup / restore.
    @State private var showExportPassword = false
    @State private var exportDocument: BackupDocument?
    @State private var showExporter = false
    @State private var showImporter = false
    @State private var pendingImportBlob: String?
    @State private var showImportPassword = false
    @State private var statusMessage: String?

    private var palette: Palette {
        Palette.resolved(for: themeMode, systemDark: colorScheme == .dark)
    }

    /// Entries arranged for display. Reordering always operates on the manual order, so handles
    /// are only meaningful here when 'sortMode == .manual'.
    private var displayedEntries: [AuthEntry] {
        EntryOrdering.sorted(store.entries, by: sortMode)
    }

    var body: some View {
        TimelineView(.periodic(from: .now, by: 0.1)) { context in
            let now = context.date
            VStack(spacing: 0) {
                CountdownBar(
                    fraction: TOTPWindow.remainingFraction(at: now),
                    fill: palette.progressFill,
                    track: palette.progressTrack
                )
                if let error = store.lastError {
                    Text(error)
                        .font(.callout)
                        .foregroundStyle(.white)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(.horizontal, 16)
                        .padding(.vertical, 8)
                        .background(Color.red)
                }
                grid(now: now)
            }
            // A click anywhere that isn't a tile (margins, countdown bar, empty space) ends
            // reorder mode. Tiles handle their own clicks, so this only fires off-tile.
            .contentShape(Rectangle())
            .onTapGesture { if isReordering { isReordering = false } }
        }
        .background(palette.appBackground)
        .background(WindowConfigurator())
        // Collapse reorder mode when the user clicks the title bar / another window...
        .background(ReorderDismissMonitor(isActive: isReordering) { isReordering = false })
        .environment(\.palette, palette)
        .frame(minWidth: 320, minHeight: 360)
        // ...or clicks away to another app / loses key focus, or presses Escape.
        .onChange(of: controlActiveState) { _, state in
            if state != .key { isReordering = false }
        }
        .onChange(of: sortMode) { _, mode in
            if mode != .manual { isReordering = false }
        }
        .onExitCommand { isReordering = false }
        .focusedSceneValue(\.addEntry) {
            prefillName = ""
            prefillSeed = ""
            addFormID = UUID()
            showAdd = true
        }
        .focusedSceneValue(\.scanQR) { Task { await scanQRFromScreen() } }
        .focusedSceneValue(\.printQR) { printQRCodes() }
        .focusedSceneValue(\.exportBackup) { startExport() }
        .focusedSceneValue(\.importBackup) { showImporter = true }
        .sheet(isPresented: $showAdd) {
            AddEntryView(initialName: prefillName, initialSeed: prefillSeed) { name, seed in
                try store.add(name: name, seed: seed)
            }
            .id(addFormID)
        }
        .sheet(isPresented: $showExportPassword) {
            ExportBackupView { password in try makeExportDocument(password: password) }
        }
        .sheet(isPresented: $showImportPassword, onDismiss: { pendingImportBlob = nil }) {
            ImportBackupView { password, mode in
                try performImport(password: password, mode: mode)
            }
        }
        .fileExporter(
            isPresented: $showExporter,
            document: exportDocument,
            contentType: .authentoolBackup,
            defaultFilename: Self.suggestedBackupName()
        ) { result in
            switch result {
            case .success: statusMessage = "Backup exported."
            case .failure(let error): statusMessage = "Export failed: \(error.localizedDescription)"
            }
            exportDocument = nil
        }
        .fileImporter(
            isPresented: $showImporter,
            allowedContentTypes: [.authentoolBackup, .plainText, .data]
        ) { result in
            handleImportFile(result)
        }
        .alert("Authentool", isPresented: statusAlertBinding, presenting: statusMessage) { _ in
            Button("OK", role: .cancel) {}
        } message: { message in
            Text(message)
        }
        .sheet(item: $renameTarget) { entry in
            RenameEntryView(entry: entry) { newName in store.rename(entry.id, to: newName) }
        }
        .confirmationDialog(
            "Delete \(deleteTarget?.name ?? "entry")?",
            isPresented: deleteDialogBinding,
            titleVisibility: .visible
        ) {
            Button("Delete", role: .destructive) {
                if let target = deleteTarget { store.delete(target.id) }
            }
            Button("Cancel", role: .cancel) {}
        } message: {
            Text("This removes the entry and its seed from this device.")
        }
    }

    // MARK: - Grid

    @ViewBuilder
    private func grid(now: Date) -> some View {
        if store.entries.isEmpty {
            emptyState
        } else {
            GeometryReader { geo in
                let scale = cardScale(forAvailableWidth: geo.size.width)
                ScrollView {
                    LazyVGrid(
                        columns: Array(
                            repeating: GridItem(.flexible(), spacing: gridSpacing),
                            count: gridColumns
                        ),
                        spacing: gridSpacing
                    ) {
                        ForEach(displayedEntries) { entry in
                            AuthCardView(
                                entry: entry,
                                date: now,
                                isHighlighted: copiedID == entry.id,
                                isReordering: isReordering,
                                scale: scale,
                                onCopy: { copy(entry) },
                                onRename: { renameTarget = entry },
                                onDelete: { deleteTarget = entry },
                                onBeginReorder: beginReorder,
                                onReorder: reorder
                            )
                        }
                    }
                    .padding(gridPadding)
                }
            }
        }
    }

    /// Moves the dragged entry to the dropped-on entry's position, reusing the store's
    /// tested onMove semantics.
    private func reorder(_ draggedID: AuthEntry.ID, onto targetID: AuthEntry.ID) {
        guard let source = store.entries.firstIndex(where: { $0.id == draggedID }),
              let target = store.entries.firstIndex(where: { $0.id == targetID }),
              source != target
        else { return }
        let destination = source < target ? target + 1 : target
        store.moveEntries(fromOffsets: IndexSet(integer: source), toOffset: destination)
    }

    private var emptyState: some View {
        VStack(spacing: 8) {
            Image(systemName: "lock.shield")
                .font(.system(size: 40))
                .foregroundStyle(palette.cardTotp)
            Text("No entries yet")
                .font(.headline)
                .foregroundStyle(palette.appText)
            Text("Add an authenticator with File ▸ New Entry (⌘N).")
                .font(.subheadline)
                .foregroundStyle(palette.cardName)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }

    // MARK: - Actions

    /// Enters reorder mode (revealing drag handles). Reordering edits the manual arrangement, so
    /// an alphabetical view first switches to manual - you can't hand-arrange a sorted list.
    private func beginReorder() {
        if sortMode != .manual { sortMode = .manual }
        isReordering = true
    }

    // Clipboard managers and Universal Clipboard honor this type to skip archiving/syncing values
    // marked sensitive (the de-facto org.nspasteboard.ConcealedType convention).
    private static let concealedType = NSPasteboard.PasteboardType("org.nspasteboard.ConcealedType")

    private func copy(_ entry: AuthEntry) {
        guard let code = entry.code() else { return }
        let pasteboard = NSPasteboard.general
        pasteboard.clearContents()
        pasteboard.setString(code, forType: .string)
        // Flag the code as transient/sensitive so clipboard history tools don't retain it.
        pasteboard.setString(code, forType: Self.concealedType)

        copiedID = entry.id
        highlightTask?.cancel()
        highlightTask = Task {
            try? await Task.sleep(for: .seconds(1.5))
            if !Task.isCancelled { copiedID = nil }
        }
    }

    private var deleteDialogBinding: Binding<Bool> {
        Binding(
            get: { deleteTarget != nil },
            set: { if !$0 { deleteTarget = nil } }
        )
    }

    // MARK: - Scan QR from screen

    /// Captures an on-screen region, decodes an otpauth QR, and adds the entry. Suppresses the
    /// auto-lock while the system capture overlay has focus so scanning doesn't re-lock the app.
    private func scanQRFromScreen() async {
        lock.suppressAutoLock = true
        defer { lock.suppressAutoLock = false }

        guard let image = await ScreenCaptureService.captureRegion() else { return } // canceled
        let payloads = ScreenCaptureService.decodeQRCodes(in: image)
        guard let uri = payloads.first(where: { $0.lowercased().hasPrefix("otpauth://") }) else {
            statusMessage = "No 2FA QR code was found in the selected area."
            return
        }
        do {
            // Prefill the normal Add dialog so the scanned values can be tweaked before saving.
            let parsed = try OTPAuthURL.parse(uri)
            prefillName = parsed.name
            prefillSeed = parsed.secret
            addFormID = UUID()
            showAdd = true
        } catch {
            statusMessage = "That QR code isn't a TOTP authenticator code."
        }
    }

    /// Prints a titled grid of per-entry QR codes (the standard print panel also offers Save as
    /// PDF). Suppresses auto-lock while the print panel is up.
    private func printQRCodes() {
        guard !store.entries.isEmpty else {
            statusMessage = "There are no entries to print."
            return
        }
        lock.suppressAutoLock = true
        QRPrintService.print(entries: store.entries)
        lock.suppressAutoLock = false
    }

    // MARK: - Backup / Restore

    /// Opens the export password sheet, or reports if there's nothing to export.
    private func startExport() {
        guard !store.entries.isEmpty else {
            statusMessage = "There are no entries to export."
            return
        }
        showExportPassword = true
    }

    /// Encrypts the current entries into the export document and arms the save panel.
    /// Throws (surfaced inline by ExportBackupView) if encoding/encryption fails.
    private func makeExportDocument(password: String) throws {
        let json = try EntryCodec.encode(store.entries)
        let blob = try CryptoBox.encrypt(json, password: password)
        exportDocument = BackupDocument(text: blob)
        // Present the save panel after the password sheet finishes dismissing.
        DispatchQueue.main.async { showExporter = true }
    }

    /// Reads the chosen backup file and, on success, opens the password sheet.
    private func handleImportFile(_ result: Result<URL, Error>) {
        guard case .success(let url) = result else { return } // .failure includes user cancel
        do {
            let scoped = url.startAccessingSecurityScopedResource()
            defer { if scoped { url.stopAccessingSecurityScopedResource() } }
            let data = try Data(contentsOf: url)
            guard let text = String(data: data, encoding: .utf8) else {
                statusMessage = "That file isn't a readable backup."
                return
            }
            pendingImportBlob = text
            showImportPassword = true
        } catch {
            statusMessage = "Could not read the selected file."
        }
    }

    /// Decrypts the pending blob and applies it to the store. Throws (surfaced inline by
    /// ImportBackupView) on wrong password / corrupt file, leaving the store untouched.
    private func performImport(password: String, mode: AuthStore.ImportMode) throws {
        guard let blob = pendingImportBlob else { return }
        let json = try CryptoBox.decrypt(blob, password: password)
        let entries = try EntryCodec.decode(json)
        let count = store.importEntries(entries, mode: mode)
        pendingImportBlob = nil
        statusMessage = "Imported \(count) \(count == 1 ? "entry" : "entries")."
    }

    private var statusAlertBinding: Binding<Bool> {
        Binding(
            get: { statusMessage != nil },
            set: { if !$0 { statusMessage = nil } }
        )
    }

    private static func suggestedBackupName() -> String {
        let formatter = DateFormatter()
        formatter.dateFormat = "yyyyMMdd"
        return "authentool_\(formatter.string(from: Date()))"
    }
}
