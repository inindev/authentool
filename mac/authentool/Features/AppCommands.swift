//
//  AppCommands.swift
//  authentool
//
//  Menu-bar commands. Adding an entry is File -> New Entry (Cmd-N), wired to the focused window
//  via FocusedValue. Theme selection is a View -> Theme submenu bound to the shared
//  @AppStorage key, so the main window recolors live.
//

import SwiftUI
import AuthentoolCore

/// Actions published by the active window so menu commands can drive it.
struct AddEntryKey: FocusedValueKey {
    typealias Value = () -> Void
}

struct ExportBackupKey: FocusedValueKey {
    typealias Value = () -> Void
}

struct ImportBackupKey: FocusedValueKey {
    typealias Value = () -> Void
}

struct ScanQRKey: FocusedValueKey {
    typealias Value = () -> Void
}

struct PrintQRKey: FocusedValueKey {
    typealias Value = () -> Void
}

extension FocusedValues {
    var addEntry: (() -> Void)? {
        get { self[AddEntryKey.self] }
        set { self[AddEntryKey.self] = newValue }
    }
    var scanQR: (() -> Void)? {
        get { self[ScanQRKey.self] }
        set { self[ScanQRKey.self] = newValue }
    }
    var printQR: (() -> Void)? {
        get { self[PrintQRKey.self] }
        set { self[PrintQRKey.self] = newValue }
    }
    var exportBackup: (() -> Void)? {
        get { self[ExportBackupKey.self] }
        set { self[ExportBackupKey.self] = newValue }
    }
    var importBackup: (() -> Void)? {
        get { self[ImportBackupKey.self] }
        set { self[ImportBackupKey.self] = newValue }
    }
}

struct AuthentoolCommands: Commands {
    @FocusedValue(\.addEntry) private var addEntry
    @FocusedValue(\.scanQR) private var scanQR
    @FocusedValue(\.printQR) private var printQR
    @FocusedValue(\.exportBackup) private var exportBackup
    @FocusedValue(\.importBackup) private var importBackup
    @AppStorage("theme_preference") private var themeMode: ThemeMode = .system
    @AppStorage("sort_mode") private var sortMode: SortMode = .manual

    var body: some Commands {
        // File -> New Entry (replaces the default "New" item).
        CommandGroup(replacing: .newItem) {
            Button("New Entry…") { addEntry?() }
                .keyboardShortcut("n", modifiers: .command)
                .disabled(addEntry == nil)
            Button("Scan QR Code from Screen…") { scanQR?() }
                .keyboardShortcut("s", modifiers: [.command, .shift])
                .disabled(scanQR == nil)
        }
        // File -> Import / Export Backup.
        CommandGroup(replacing: .importExport) {
            Button("Import Backup…") { importBackup?() }
                .disabled(importBackup == nil)
            Button("Export Backup…") { exportBackup?() }
                .disabled(exportBackup == nil)
        }
        // Replace SwiftUI's broken default Print item (which sends print: up the responder chain
        // to a non-document app -> "does not support printing") with our QR print command. The
        // item must stay enabled so it actually captures Cmd-P; it no-ops when no window is focused.
        CommandGroup(replacing: .printItem) {
            Button("Print QR Codes…") { printQR?() }
                .keyboardShortcut("p", modifiers: .command)
        }
        // Replace the default View menu's toolbar/full-screen items with our own Theme / Sort By
        // radio groups, so there's a single View menu (no toolbar or full-screen entries).
        CommandGroup(replacing: .toolbar) {
            Picker("Theme", selection: $themeMode) {
                ForEach(ThemeMode.allCases, id: \.self) { mode in
                    Text(mode.displayName).tag(mode)
                }
            }
            Picker("Sort By", selection: $sortMode) {
                ForEach(SortMode.allCases, id: \.self) { mode in
                    Text(mode.displayName).tag(mode)
                }
            }
        }
    }
}
