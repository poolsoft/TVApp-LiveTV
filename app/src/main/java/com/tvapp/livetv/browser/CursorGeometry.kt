package com.tvapp.livetv.browser

/**
 * Sanal fare imleci (virtual cursor) matematik hesaplamalari.
 * Imlec koordinat sinirlandirma (clamp), ivmelenme ve sayfa kenari kaydirma (wheel) mantigini icerir.
 */
object CursorGeometry {

    const val BASE_SPEED_PX = 6f
    const val MAX_SPEED_PX = 22f
    const val ACCEL_MS = 800L

    data class Point(val x: Float, val y: Float)

    /**
     * Imleci ekran sinirlari icinde tutar.
     */
    fun clamp(x: Float, y: Float, width: Int, height: Int): Point = Point(
        x.coerceIn(0f, (width - 1).coerceAtLeast(0).toFloat()),
        y.coerceIn(0f, (height - 1).coerceAtLeast(0).toFloat())
    )

    /**
     * D-pad yonune gore yeni imlec koordinatini hesaplar.
     */
    fun step(current: Point, dirX: Int, dirY: Int, speedPx: Float, width: Int, height: Int): Point =
        clamp(current.x + dirX * speedPx, current.y + dirY * speedPx, width, height)

    /**
     * Tus basili tutuldugunda ivmelenme (acceleration) uygular.
     */
    fun speedForHoldMs(heldMs: Long, multiplier: Float = 1f): Float {
        val base = when {
            heldMs <= 0L -> BASE_SPEED_PX
            heldMs >= ACCEL_MS -> MAX_SPEED_PX
            else -> {
                val t = heldMs.toFloat() / ACCEL_MS
                BASE_SPEED_PX + (MAX_SPEED_PX - BASE_SPEED_PX) * t
            }
        }
        return base * multiplier
    }

    fun isAtTopEdge(y: Float, zonePx: Float): Boolean = y <= zonePx

    fun isAtBottomEdge(y: Float, height: Int, zonePx: Float): Boolean = y >= height - zonePx

    /**
     * Ekran kenarina ulasildiginda fare tekerlegi (wheel) kaydirma miktarini hesaplar.
     */
    fun wheelStep(dirY: Int, y: Float, height: Int, zonePx: Float, ticksPerFrame: Float): Float = when {
        dirY < 0 && isAtTopEdge(y, zonePx) -> ticksPerFrame
        dirY > 0 && isAtBottomEdge(y, height, zonePx) -> -ticksPerFrame
        else -> 0f
    }
}
