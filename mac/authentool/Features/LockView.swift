//
//  LockView.swift
//  authentool
//
//  The cover shown whenever the app isn't unlocked. It fully replaces the code grid -
//  the grid isn't built at all while locked - so codes never render on an unattended or
//  screenshotted screen. Offers a retry affordance.
//

import SwiftUI
import AuthentoolCore

struct LockView: View {
    let state: LockState
    let onUnlock: () -> Void

    @Environment(\.palette) private var palette

    var body: some View {
        VStack(spacing: 16) {
            Image(systemName: "lock.fill")
                .font(.system(size: 48))
                .foregroundStyle(palette.cardTotp)
            Text("Authentool is locked")
                .font(.headline)
                .foregroundStyle(palette.appText)

            if state == .authenticating {
                ProgressView()
                    .controlSize(.small)
            } else {
                Button("Unlock", action: onUnlock)
                    .keyboardShortcut(.defaultAction)
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(palette.appBackground)
    }
}
