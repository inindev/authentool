//
//  CountdownBar.swift
//  authentool
//
//  The shared 30s countdown indicator. A single bar drives all codes; 'fraction' is
//  computed from TOTPWindow so the value is testable and identical across the app.
//

import SwiftUI

struct CountdownBar: View {
    let fraction: Double
    let fill: Color
    let track: Color

    var body: some View {
        GeometryReader { geo in
            ZStack(alignment: .leading) {
                Rectangle().fill(track)
                Rectangle()
                    .fill(fill)
                    .frame(width: max(0, geo.size.width * fraction))
            }
        }
        .frame(height: 6)
        .accessibilityHidden(true)
    }
}
