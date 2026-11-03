package com.instantreact.core

enum class PointState { GRAY_OK, BLUE_OK, BAD }

/**
 * Indicador de calibración correcta: comprueba, sobre una captura reducida, que un marcador está
 * dentro de un disco de color uniforme (gris o azul), de tamaño razonable y forma circular.
 * El fondo (región enorme) o el texto (formas irregulares) se marcan como BAD.
 */
object PointCheck {
    private fun key(p: Int): Int = (((p shr 16) and 0xFF) shr 2 shl 12) or (((p shr 8) and 0xFF) shr 2 shl 6) or ((p and 0xFF) shr 2)

    fun evaluate(pixels: IntArray, w: Int, h: Int, step: Int, cx: Int, cy: Int, radius: Int, blueThreshold: Int = 60): PointState {
        val gx = cx / step
        val gy = cy / step
        if (gx < 0 || gy < 0 || gx >= w || gy >= h) return PointState.BAD
        val gr = maxOf(1, radius / step)

        // Color más frecuente dentro del área (robusto frente a texto que cruce el círculo).
        val counts = HashMap<Int, Int>()
        for (y in maxOf(0, gy - gr)..minOf(h - 1, gy + gr)) {
            for (x in maxOf(0, gx - gr)..minOf(w - 1, gx + gr)) {
                val dx = x - gx; val dy = y - gy
                if (dx * dx + dy * dy > gr * gr) continue
                val k = key(pixels[y * w + x])
                counts[k] = (counts[k] ?: 0) + 1
            }
        }
        val modeKey = counts.maxByOrNull { it.value }?.key ?: return PointState.BAD
        var seed = -1
        var bestDist = Int.MAX_VALUE
        for (y in maxOf(0, gy - gr)..minOf(h - 1, gy + gr)) {
            for (x in maxOf(0, gx - gr)..minOf(w - 1, gx + gr)) {
                val dx = x - gx; val dy = y - gy
                val d = dx * dx + dy * dy
                if (d <= gr * gr && d < bestDist && key(pixels[y * w + x]) == modeKey) {
                    bestDist = d
                    seed = y * w + x
                }
            }
        }
        if (seed < 0) return PointState.BAD
        val seedKey = key(pixels[seed])

        val maxArea = (w.toLong() * h * 12 / 100).toInt()
        val visited = HashSet<Int>()
        val stack = ArrayList<Int>()
        stack.add(seed); visited.add(seed)
        var area = 0
        var minX = w; var maxX = -1; var minY = h; var maxY = -1
        var sr = 0L; var sg = 0L; var sb = 0L
        while (stack.isNotEmpty()) {
            val cur = stack.removeAt(stack.size - 1)
            val x = cur % w; val y = cur / w
            area++
            if (area > maxArea) return PointState.BAD
            val p = pixels[cur]
            sr += (p shr 16) and 0xFF; sg += (p shr 8) and 0xFF; sb += p and 0xFF
            if (x < minX) minX = x; if (x > maxX) maxX = x
            if (y < minY) minY = y; if (y > maxY) maxY = y
            if (x > 0) push(pixels, cur - 1, seedKey, visited, stack)
            if (x < w - 1) push(pixels, cur + 1, seedKey, visited, stack)
            if (y > 0) push(pixels, cur - w, seedKey, visited, stack)
            if (y < h - 1) push(pixels, cur + w, seedKey, visited, stack)
        }
        val bw = maxX - minX + 1
        val bh = maxY - minY + 1
        if (bw > w * 40 / 100 || bh > h * 40 / 100) return PointState.BAD
        val aspect = bw.toDouble() / bh
        if (aspect < 0.7 || aspect > 1.43) return PointState.BAD
        val fill = area.toDouble() / (bw * bh)
        if (fill < 0.5 || fill > 0.95) return PointState.BAD
        if (area < maxOf(4.0, Math.PI * gr * gr * 0.5)) return PointState.BAD
        val r = (sr / area).toInt(); val g = (sg / area).toInt(); val b = (sb / area).toInt()
        return when {
            ColorClassifier.isBlue(r, g, b, blueThreshold) -> PointState.BLUE_OK
            ColorClassifier.isGray(r, g, b) -> PointState.GRAY_OK
            else -> PointState.BAD
        }
    }

    private fun push(pixels: IntArray, idx: Int, seedKey: Int, visited: HashSet<Int>, stack: ArrayList<Int>) {
        if (key(pixels[idx]) == seedKey && visited.add(idx)) stack.add(idx)
    }
}
