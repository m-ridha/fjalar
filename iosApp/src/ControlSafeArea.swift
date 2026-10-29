import CoreGraphics

/// Keeps custom controls in the largest unobstructed pane. The map remains full size.
/// Reserved-region frames already include Apple's recommended interaction margins.
func controlSafeRect(in bounds: CGRect, safeFrame: CGRect, avoiding regions: [CGRect]) -> CGRect {
    var candidates = [bounds.intersection(safeFrame)]
    for region in regions {
        candidates = candidates.flatMap { rect -> [CGRect] in
            let overlap = rect.intersection(region)
            guard !overlap.isNull && !overlap.isEmpty else { return [rect] }
            return [
                CGRect(x: rect.minX, y: rect.minY, width: overlap.minX - rect.minX, height: rect.height),
                CGRect(x: overlap.maxX, y: rect.minY, width: rect.maxX - overlap.maxX, height: rect.height),
                CGRect(x: rect.minX, y: rect.minY, width: rect.width, height: overlap.minY - rect.minY),
                CGRect(x: rect.minX, y: overlap.maxY, width: rect.width, height: rect.maxY - overlap.maxY),
            ].filter { !$0.isEmpty && !$0.isNull }
        }
    }
    // Prefer the leading pane when equally sized panes are available.
    return candidates.sorted {
        let firstArea = $0.width * $0.height
        let secondArea = $1.width * $1.height
        if firstArea == secondArea { return $0.minX < $1.minX }
        return firstArea > secondArea
    }.first ?? .zero
}
