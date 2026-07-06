//
//  ReorderDismissMonitor.swift
//  authentool
//
//  While reorder mode is active, watches for left-mouse-downs that land outside the window's
//  content area (e.g. the title bar) or in another window, and calls 'onDismiss' so the tiles'
//  drag handles collapse. Clicks inside the content area are passed through untouched so tile
//  dragging still works. App/other-app deactivation is handled separately by ContentView via
//  the controlActiveState environment value.
//

import SwiftUI
import AppKit

struct ReorderDismissMonitor: NSViewRepresentable {
    var isActive: Bool
    var onDismiss: () -> Void

    func makeNSView(context: Context) -> NSView { NSView() }

    func updateNSView(_ nsView: NSView, context: Context) {
        context.coordinator.onDismiss = onDismiss
        context.coordinator.update(isActive: isActive, hostWindow: nsView.window)
    }

    func makeCoordinator() -> Coordinator { Coordinator() }

    final class Coordinator {
        var onDismiss: () -> Void = {}
        private var monitor: Any?
        private weak var hostWindow: NSWindow?

        func update(isActive: Bool, hostWindow: NSWindow?) {
            self.hostWindow = hostWindow
            if isActive, monitor == nil {
                monitor = NSEvent.addLocalMonitorForEvents(matching: [.leftMouseDown]) { [weak self] event in
                    self?.handle(event)
                    return event
                }
            } else if !isActive, let monitor {
                NSEvent.removeMonitor(monitor)
                self.monitor = nil
            }
        }

        private func handle(_ event: NSEvent) {
            guard let hostWindow else { return }
            // A click in a different window collapses reorder mode.
            guard let clickWindow = event.window, clickWindow == hostWindow else {
                onDismiss()
                return
            }
            // A click outside the content layout rect - i.e. the title bar - collapses it too.
            // contentLayoutRect excludes the title bar and is in the window's coordinate space,
            // matching event.locationInWindow.
            if !hostWindow.contentLayoutRect.contains(event.locationInWindow) {
                onDismiss()
            }
        }

        deinit {
            if let monitor { NSEvent.removeMonitor(monitor) }
        }
    }
}
