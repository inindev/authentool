//
//  QRPrintView.swift
//  authentool
//
//  The printable page: a titled grid of per-entry QR codes (each the entry's otpauth:// URI),
//  plus a security caution. Fixed black-on-white US-Letter pages so it prints legibly regardless
//  of the app's theme. 'QRPrintService' renders the pages to a PDF (via ImageRenderer) and prints
//  it through PDFKit - printing a detached NSHostingView directly is rejected by AppKit
//  ("application does not support printing").
//

import SwiftUI
import AppKit
import PDFKit
import AuthentoolCore

/// One US-Letter page of QR tiles. 'showHeader' puts the title/caution on the first page only.
struct QRPrintPageView: View {
    let entries: [AuthEntry]
    let showHeader: Bool

    static let pageSize = CGSize(width: 612, height: 792) // US Letter at 72 dpi
    static let perPage = 9                                // 3 columns x 3 rows

    private let columns = [GridItem(.adaptive(minimum: 150), spacing: 24, alignment: .top)]

    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            if showHeader {
                Text("Authentool — Authenticator Backup")
                    .font(.system(size: 18, weight: .bold))
                Text("Keep this page secure. Each QR code contains a secret key that can recreate "
                     + "your 2FA codes — anyone who scans it gains access.")
                    .font(.system(size: 11))
                    .foregroundStyle(.black.opacity(0.6))
                Divider().overlay(Color.black.opacity(0.2))
            }

            LazyVGrid(columns: columns, alignment: .leading, spacing: 24) {
                ForEach(entries) { entry in
                    VStack(spacing: 8) {
                        if let image = QRCodeGenerator.image(for: entry) {
                            Image(nsImage: image)
                                .interpolation(.none)
                                .resizable()
                                .frame(width: 130, height: 130)
                        }
                        Text(entry.name)
                            .font(.system(size: 12, weight: .medium))
                            .multilineTextAlignment(.center)
                            .lineLimit(2)
                            .foregroundStyle(.black)
                    }
                    .frame(maxWidth: .infinity)
                }
            }

            Spacer(minLength: 0)
        }
        .padding(36)
        .frame(width: Self.pageSize.width, height: Self.pageSize.height, alignment: .topLeading)
        .background(Color.white)
        .foregroundStyle(.black)
    }
}

enum QRPrintService {
    /// Renders the entries to a multi-page PDF and runs the standard print panel (which also offers
    /// "Save as PDF" for export).
    @MainActor
    static func print(entries: [AuthEntry]) {
        guard let data = makePDF(entries: entries) else { return }
        guard let document = PDFDocument(data: data) else { return }
        guard let operation = document.printOperation(
            for: NSPrintInfo.shared,
            scalingMode: .pageScaleNone,
            autoRotate: false
        ) else { return }
        if let window = NSApp.keyWindow ?? NSApp.mainWindow ?? NSApp.windows.first {
            operation.runModal(for: window, delegate: nil, didRun: nil, contextInfo: nil)
        } else {
            operation.run()
        }
    }

    @MainActor
    private static func makePDF(entries: [AuthEntry]) -> Data? {
        // Chunk into pages of 'perPage'.
        let perPage = QRPrintPageView.perPage
        let pages = stride(from: 0, to: entries.count, by: perPage).map {
            Array(entries[$0 ..< min($0 + perPage, entries.count)])
        }

        let data = NSMutableData()
        var mediaBox = CGRect(origin: .zero, size: QRPrintPageView.pageSize)
        guard let consumer = CGDataConsumer(data: data as CFMutableData),
              let context = CGContext(consumer: consumer, mediaBox: &mediaBox, nil) else { return nil }

        for (index, pageEntries) in pages.enumerated() {
            let page = QRPrintPageView(entries: pageEntries, showHeader: index == 0)
            let renderer = ImageRenderer(content: page)
            renderer.render { _, draw in
                context.beginPDFPage(nil)
                draw(context)
                context.endPDFPage()
            }
        }
        context.closePDF()
        return data as Data
    }
}
