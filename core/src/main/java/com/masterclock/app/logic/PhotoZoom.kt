package com.masterclock.app.logic

/** The most a photo note's photo can be enlarged: enough for a scoresheet's handwriting. */
const val MAX_PHOTO_ZOOM = 5f

/** How far a double tap enlarges a photo shown whole. */
const val PHOTO_DOUBLE_TAP_ZOOM = 2.5f

/**
 * How a photo is shown in its place: enlarged by [scale], and moved by [x] and [y] from the centre,
 * in the place's own pixels. A scoresheet photographed was shown whole and nothing more, too small to
 * read on a phone; this is what a pinch and a drag make of it.
 */
data class PhotoZoom(val scale: Float = 1f, val x: Float = 0f, val y: Float = 0f) {

    val isZoomed: Boolean get() = scale > 1f

    /**
     * After a pinch of [zoom] about [centroidX], [centroidY] -- where the fingers are, from the
     * place's top left corner -- and a drag of [panX], [panY], in a place [width] by [height]. The
     * point between the fingers stays under them; the photo is never smaller than its place, nor
     * moved off it.
     */
    fun transformed(zoom: Float, centroidX: Float, centroidY: Float, panX: Float, panY: Float, width: Float, height: Float): PhotoZoom {
        val newScale = (scale * zoom).coerceIn(1f, MAX_PHOTO_ZOOM)
        val k = newScale / scale
        // The fingers' point from the centre: it maps to the same point of the photo after as before.
        val cx = centroidX - width / 2
        val cy = centroidY - height / 2
        return PhotoZoom(newScale, cx - (cx - x) * k + panX, cy - (cy - y) * k + panY).heldIn(width, height)
    }

    /** A double tap at [tapX], [tapY]: enlarged there when shown whole, shown whole again when not. */
    fun doubleTapped(tapX: Float, tapY: Float, width: Float, height: Float): PhotoZoom =
        if (isZoomed) PhotoZoom()
        else PhotoZoom().transformed(PHOTO_DOUBLE_TAP_ZOOM, tapX, tapY, 0f, 0f, width, height)

    /** Moved no further than keeps the place covered: half of what the enlargement adds, each way. */
    fun heldIn(width: Float, height: Float): PhotoZoom {
        val maxX = width * (scale - 1) / 2
        val maxY = height * (scale - 1) / 2
        // + 0f makes a -0.0 a 0.0: the same place, but not equal to it.
        return copy(x = x.coerceIn(-maxX, maxX) + 0f, y = y.coerceIn(-maxY, maxY) + 0f)
    }
}
