package com.instantreact.core

data class FoundCircle(val x: Int, val y: Int, val radius: Int, val area: Int)

/**
 * Localiza círculos grises (también muy oscuros) en una captura reducida (rejilla ARGB).
 * Segmenta por color cuantizado -> componentes conexas -> filtra por forma (casi cuadrado, relleno ~ pi/4,
 * tamaño razonable, color gris) y elige los `count` de tamaño más parecido. Devuelve coordenadas de la
 * rejilla; quien llama debe multiplicar por el factor de reducción.
 */
object CircleFinder {
    private fun key(p: Int): Int = (((p shr 16) and 0xFF) shr 2 shl 12) or (((p shr 8) and 0xFF) shr 2 shl 6) or ((p and 0xFF) shr 2)

    fun find(pixels: IntArray, w: Int, h: Int, count: Int): List<FoundCircle> {
        val n = w * h
        val keys = IntArray(n) { key(pixels[it]) }
        val visited = BooleanArray(n)
        val stack = IntArray(n)
        val candidates = ArrayList<FoundCircle>()
        for (start in 0 until n) {
            if (visited[start]) continue
            val k = keys[start]
            var sp = 0
            stack[sp++] = start
            visited[start] = true
            var minX = w; var maxX = -1; var minY = h; var maxY = -1
            var area = 0
            var sumX = 0L; var sumY = 0L; var sr = 0L; var sg = 0L; var sb = 0L
            while (sp > 0) {
                val cur = stack[--sp]
                val cx = cur % w
                val cy = cur / w
                area++
                sumX += cx; sumY += cy
                val p = pixels[cur]
                sr += (p shr 16) and 0xFF; sg += (p shr 8) and 0xFF; sb += p and 0xFF
                if (cx < minX) minX = cx
                if (cx > maxX) maxX = cx
                if (cy < minY) minY = cy
                if (cy > maxY) maxY = cy
                if (cx > 0 && !visited[cur - 1] && keys[cur - 1] == k) { visited[cur - 1] = true; stack[sp++] = cur - 1 }
                if (cx < w - 1 && !visited[cur + 1] && keys[cur + 1] == k) { visited[cur + 1] = true; stack[sp++] = cur + 1 }
                if (cy > 0 && !visited[cur - w] && keys[cur - w] == k) { visited[cur - w] = true; stack[sp++] = cur - w }
                if (cy < h - 1 && !visited[cur + w] && keys[cur + w] == k) { visited[cur + w] = true; stack[sp++] = cur + w }
            }
            val bw = maxX - minX + 1
            val bh = maxY - minY + 1
            if (bw < 4 || bh < 4 || area < 12) continue
            if (bw > w * 35 / 100 || bh > h * 35 / 100) continue
            val aspect = bw.toDouble() / bh
            if (aspect < 0.75 || aspect > 1.33) continue
            val fill = area.toDouble() / (bw * bh)
            if (fill < 0.55 || fill > 0.92) continue
            val r = (sr / area).toInt(); val g = (sg / area).toInt(); val b = (sb / area).toInt()
            if (!ColorClassifier.isGray(r, g, b)) continue
            candidates.add(
                FoundCircle(
                    x = (sumX / area).toInt(),
                    y = (sumY / area).toInt(),
                    radius = (bw + bh) / 4,
                    area = area,
                ),
            )
        }
        if (candidates.size <= count) return sortReading(candidates)
        // Elegir el grupo de `count` círculos con áreas más parecidas.
        candidates.sortByDescending { it.area }
        var bestStart = 0
        var bestSpread = Double.MAX_VALUE
        for (s in 0..candidates.size - count) {
            val spread = (candidates[s].area - candidates[s + count - 1].area).toDouble() / candidates[s].area
            if (spread < bestSpread) {
                bestSpread = spread
                bestStart = s
            }
        }
        return sortReading(candidates.subList(bestStart, bestStart + count))
    }

    /** Orden de lectura (numeración de los marcadores): por filas y luego de izquierda a derecha. */
    private fun sortReading(list: List<FoundCircle>): List<FoundCircle> {
        if (list.isEmpty()) return list
        val tol = list.map { it.radius }.average().toInt().coerceAtLeast(1)
        val byY = list.sortedBy { it.y }
        val result = ArrayList<FoundCircle>(list.size)
        var row = ArrayList<FoundCircle>()
        var rowY = byY[0].y
        for (c in byY) {
            if (c.y - rowY > tol) {
                result.addAll(row.sortedBy { it.x })
                row = ArrayList()
                rowY = c.y
            }
            row.add(c)
        }
        result.addAll(row.sortedBy { it.x })
        return result
    }
}
