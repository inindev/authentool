//
//  authentoolApp.swift
//  authentool
//
//  Created by John Clark on 6/27/26.
//

import SwiftUI
import CoreText
import AuthentoolCore

@main
struct authentoolApp: App {
    // Composition root: the store is backed by the Keychain adapter. We use the legacy
    // (file-based) keychain because a sandboxed, locally/ad-hoc-signed app has no team
    // identity, and the data-protection keychain requires a keychain-access-group entitlement
    // that only proper Team signing provides - without it SecItemAdd fails with
    // errSecMissingEntitlement and nothing persists. The legacy keychain works for the app's
    // own items under App Sandbox. A release build signed with a Team can switch this on.
    @State private var store = AuthStore(storage: KeychainEntryStorage(useDataProtection: false))
    // The Touch ID gate: starts locked, owns the lock state for the whole app.
    @State private var lock = LockModel(authenticator: SystemBiometricAuthenticator())

    init() {
        // Suppress AppKit's auto-inserted "Enter Full Screen" item in the View menu. This must be
        // set before the menu is built (hence App.init); collectionBehavior alone only greys it
        // out. See WindowConfigurator for the matching window-level full-screen disable.
        UserDefaults.standard.register(defaults: ["NSFullScreenMenuItemEverywhere": false])

        // Register the bundled Lato faces (shared with the Android app) so the TOTP digits match.
        for name in ["Lato-Regular", "Lato-Bold"] {
            if let url = Bundle.main.url(forResource: name, withExtension: "ttf") {
                CTFontManagerRegisterFontsForURL(url as CFURL, .process, nil)
            }
        }
    }

    var body: some Scene {
        // A single, unique window is the canonical scene for a single-window utility.
        Window("Authentool", id: "main") {
            RootView(store: store, lock: lock)
        }
        .commands {
            AuthentoolCommands()
        }
    }
}
