import Testing
@testable import AuthentoolCore

// 2-column grid, indices laid out row-major:
//   0 1
//   2 3
//   4        (partial last row, count = 5)
@Suite("Grid reordering math")
struct GridLayoutTests {
    let columns = 2
    let count = 5

    @Test func upFromTopRowIsBlocked() {
        #expect(GridLayout.targetIndex(from: 0, direction: .up, count: count, columns: columns) == nil)
        #expect(GridLayout.targetIndex(from: 1, direction: .up, count: count, columns: columns) == nil)
    }

    @Test func upFromLowerRowMovesUpOneRow() {
        #expect(GridLayout.targetIndex(from: 2, direction: .up, count: count, columns: columns) == 0)
        #expect(GridLayout.targetIndex(from: 3, direction: .up, count: count, columns: columns) == 1)
        #expect(GridLayout.targetIndex(from: 4, direction: .up, count: count, columns: columns) == 2)
    }

    @Test func downMovesDownOneRowWhenTargetExists() {
        #expect(GridLayout.targetIndex(from: 0, direction: .down, count: count, columns: columns) == 2)
        #expect(GridLayout.targetIndex(from: 2, direction: .down, count: count, columns: columns) == 4)
    }

    @Test func downIsBlockedWhenNoEntryBelow() {
        // index 3 -> 5 would be out of range (count 5), and index 4 is the last row.
        #expect(GridLayout.targetIndex(from: 3, direction: .down, count: count, columns: columns) == nil)
        #expect(GridLayout.targetIndex(from: 4, direction: .down, count: count, columns: columns) == nil)
    }

    @Test func leftIsBlockedInFirstColumn() {
        #expect(GridLayout.targetIndex(from: 0, direction: .left, count: count, columns: columns) == nil)
        #expect(GridLayout.targetIndex(from: 2, direction: .left, count: count, columns: columns) == nil)
        #expect(GridLayout.targetIndex(from: 4, direction: .left, count: count, columns: columns) == nil)
    }

    @Test func leftMovesOneInSecondColumn() {
        #expect(GridLayout.targetIndex(from: 1, direction: .left, count: count, columns: columns) == 0)
        #expect(GridLayout.targetIndex(from: 3, direction: .left, count: count, columns: columns) == 2)
    }

    @Test func rightMovesOneWhenNeighbourExists() {
        #expect(GridLayout.targetIndex(from: 0, direction: .right, count: count, columns: columns) == 1)
        #expect(GridLayout.targetIndex(from: 2, direction: .right, count: count, columns: columns) == 3)
    }

    @Test func rightIsBlockedInLastColumnOrAtEnd() {
        #expect(GridLayout.targetIndex(from: 1, direction: .right, count: count, columns: columns) == nil)
        #expect(GridLayout.targetIndex(from: 3, direction: .right, count: count, columns: columns) == nil)
        // index 4 is alone in the last row: no right neighbour.
        #expect(GridLayout.targetIndex(from: 4, direction: .right, count: count, columns: columns) == nil)
    }

    @Test func outOfRangeIndexReturnsNil() {
        #expect(GridLayout.targetIndex(from: -1, direction: .up, count: count, columns: columns) == nil)
        #expect(GridLayout.targetIndex(from: 5, direction: .up, count: count, columns: columns) == nil)
    }
}
