//
//  AuthCardView.swift
//  authentool
//
//  One authenticator card in the grid: name + current code. Click copies the code with a brief
//  highlight; right-click exposes rename / delete. A long-press enters reorder mode (handled by
//  the parent), which reveals a grip handle on every card; dragging that handle reorders, with the
//  card itself shown as the drag preview so the tile follows the cursor.
//

import SwiftUI
import AuthentoolCore

struct AuthCardView: View {
    let entry: AuthEntry
    let date: Date
    let isHighlighted: Bool
    /// True while the grid is in reorder mode: show the drag handle and allow dragging.
    let isReordering: Bool
    /// Card size multiplier from the View -> Zoom commands (1.0 = actual size).
    let scale: Double
    let onCopy: () -> Void
    let onRename: () -> Void
    let onDelete: () -> Void
    /// Long-press the card to enter reorder mode.
    let onBeginReorder: () -> Void
    /// Called when another card's handle is dropped onto this card: (draggedID, targetID).
    let onReorder: (_ draggedID: AuthEntry.ID, _ targetID: AuthEntry.ID) -> Void

    @Environment(\.palette) private var palette
    // The live width of this card, so the drag preview can match the tile and shrink from there.
    @State private var cardWidth: CGFloat = 0

    private var code: String { entry.code(at: date) ?? "------" }
    // Lato Bold (bundled), matching the Android app's TOTP digits.
    private var codeFont: Font { .custom("Lato-Bold", size: 40 * scale) }
    // The name keeps a fixed size (only the code scales). Tracking is applied at the Text below.
    private var nameFont: Font { .system(size: 14) }

    var body: some View {
        VStack(alignment: .leading, spacing: 6 * scale) {
            HStack(spacing: 8 * scale) {
                Text(entry.name)
                    .font(nameFont)
                    .tracking(0.5)
                    .foregroundStyle(isHighlighted ? palette.cardHiName : palette.cardName)
                    .lineLimit(1)
                Spacer()
                if isReordering {
                    Image(systemName: "line.3.horizontal")
                        .foregroundStyle(palette.cardName.opacity(0.55))
                        .help("Drag to reorder")
                        .draggable(entry.id.uuidString) { dragPreview }
                }
            }
            Text(TOTPCodeFormatter.grouped(code))
                .font(codeFont)
                .monospacedDigit()
                .foregroundStyle(isHighlighted ? palette.cardHiTotp : palette.cardTotp)
                .lineLimit(1)
                .minimumScaleFactor(0.6)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(16 * scale)
        .background(
            RoundedRectangle(cornerRadius: 10)
                .fill(isHighlighted ? palette.cardHiBackground : palette.cardBackground)
        )
        .background(
            GeometryReader { geo in
                Color.clear
                    .onAppear { cardWidth = geo.size.width }
                    .onChange(of: geo.size.width) { _, width in cardWidth = width }
            }
        )
        .overlay(
            // A subtle outline while reordering signals the cards are now draggable.
            RoundedRectangle(cornerRadius: 10)
                .strokeBorder(palette.cardTotp.opacity(isReordering ? 0.5 : 0), lineWidth: 1.5)
        )
        .shadow(color: .black.opacity(isReordering ? 0.18 : 0), radius: 5, y: 2)
        .contentShape(RoundedRectangle(cornerRadius: 10))
        // Click copies, but only when not arranging - in reorder mode a click is for dragging.
        .onTapGesture { if !isReordering { onCopy() } }
        .onLongPressGesture(minimumDuration: 0.4, perform: onBeginReorder)
        .help(isReordering ? "Drag to reorder" : "Click to copy code")
        .dropDestination(for: String.self) { items, _ in
            guard isReordering, let first = items.first, let draggedID = UUID(uuidString: first)
            else { return false }
            onReorder(draggedID, entry.id)
            return true
        }
        .accessibilityElement(children: .combine)
        .accessibilityLabel("\(entry.name), code \(code)")
        .accessibilityHint(isReordering ? "Drag to reorder" : "Copies the code")
        .contextMenu {
            Button("Copy Code", action: onCopy)
            Button("Rename…", action: onRename)
            Divider()
            Button("Delete", role: .destructive, action: onDelete)
        }
    }

    /// The card shown under the cursor while dragging, so the whole tile travels with the pointer.
    private var dragPreview: some View {
        VStack(alignment: .leading, spacing: 6 * scale) {
            Text(entry.name)
                .font(nameFont)
                .tracking(0.5)
                .foregroundStyle(palette.cardName)
                .lineLimit(1)
            Text(TOTPCodeFormatter.grouped(code))
                .font(codeFont)
                .monospacedDigit()
                .foregroundStyle(palette.cardTotp)
                .lineLimit(1)
        }
        .padding(16 * scale)
        .frame(width: cardWidth > 0 ? cardWidth : 220, alignment: .leading)
        .background(RoundedRectangle(cornerRadius: 10).fill(palette.cardBackground))
        // Render the floating tile at 90% of the real card for a subtle "lifted" effect.
        .scaleEffect(0.9)
    }
}
