//
//  WindowConfigurator.swift
//  authentool
//
//  Disables the window's full-screen capability - this is a small utility, not a full-screen
//  app. Removing .fullScreenPrimary makes the green title-bar button zoom (not enter full
//  screen) and removes the "Enter Full Screen" menu item.
//

import SwiftUI
import AppKit

struct WindowConfigurator: NSViewRepresentable {
    func makeNSView(context: Context) -> NSView {
        let view = NSView()
        DispatchQueue.main.async {
            guard let window = view.window else { return }
            window.collectionBehavior.remove(.fullScreenPrimary)
            window.collectionBehavior.remove(.fullScreenAuxiliary)
            window.collectionBehavior.insert(.fullScreenNone)
        }
        return view
    }

    func updateNSView(_ nsView: NSView, context: Context) {}
}
