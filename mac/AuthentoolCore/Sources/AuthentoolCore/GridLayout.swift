// Grid reordering math for the entry grid. Mirrors the Android app's move logic
// (MainViewModel.calculateNewIndex) so reordering behaves identically: a move swaps the
// entry with its neighbour in the given direction, with guards at the grid edges and the
// (possibly partial) last row. Pure and fully unit-tested.

public enum GridDirection: Sendable {
    case up, down, left, right
}

public enum GridLayout {
    /// The index an entry would swap with when moved in 'direction', or 'nil' if the move is
    /// blocked by a grid edge or the end of the list.
    public static func targetIndex(from index: Int, direction: GridDirection, count: Int, columns: Int) -> Int? {
        guard index >= 0, index < count, columns > 0 else { return nil }
        let row = index / columns
        let column = index % columns
        let totalRows = (count + columns - 1) / columns

        switch direction {
        case .up:
            return row > 0 ? index - columns : nil
        case .down:
            return (row < totalRows - 1 && index + columns < count) ? index + columns : nil
        case .left:
            return column > 0 ? index - 1 : nil
        case .right:
            return (column < columns - 1 && index + 1 < count) ? index + 1 : nil
        }
    }
}
