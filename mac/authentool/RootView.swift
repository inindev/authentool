//
//  RootView.swift
//  authentool
//
//  The window's root. Gates the code grid behind the Touch ID lock: ContentView is only
//  built while unlocked, so codes can't render otherwise. Authenticates on launch and
//  re-locks when the app resigns active, re-prompting on return.
//

import SwiftUI
import AuthentoolCore

struct RootView: View {
    let store: AuthStore
    let lock: LockModel

    @Environment(\.colorScheme) private var colorScheme
    @Environment(\.scenePhase) private var scenePhase
    @AppStorage("theme_preference") private var themeMode: ThemeMode = .system

    // Guards against re-prompting after a cancel within the same lock cycle (avoids a prompt
    // loop), while still auto-prompting once per launch / return-to-foreground.
    @State private var didAutoPrompt = false

    private var palette: Palette {
        Palette.resolved(for: themeMode, systemDark: colorScheme == .dark)
    }

    var body: some View {
        Group {
            if lock.isUnlocked {
                ContentView(store: store, lock: lock)
            } else {
                LockView(state: lock.state) { Task { await lock.authenticate() } }
                    .environment(\.palette, palette)
                    .frame(minWidth: 320, minHeight: 360)
                    .background(WindowConfigurator())
            }
        }
        .task { await autoPrompt() }
        .onChange(of: scenePhase) { _, phase in
            if phase == .active {
                Task { await autoPrompt() }
            } else if !lock.suppressAutoLock {
                // Resigned active / occluded: re-lock so codes are hidden, and allow a fresh
                // auto-prompt on the next activation. Skipped while we drive the capture overlay.
                lock.lock()
                didAutoPrompt = false
            }
        }
    }

    /// Prompts once per lock cycle. The LockView button can still retry manually.
    private func autoPrompt() async {
        guard lock.state == .locked, !didAutoPrompt else { return }
        didAutoPrompt = true
        await lock.authenticate()
    }
}
