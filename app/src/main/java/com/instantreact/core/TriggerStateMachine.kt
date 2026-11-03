package com.instantreact.core

/**
 * Máquina de estados por círculo que garantiza UNA sola pulsación por estímulo.
 *
 *  - Armado + azul  -> dispara y pasa a "bloqueado".
 *  - Bloqueado + azul -> no vuelve a disparar (evita pulsaciones duplicadas).
 *  - Bloqueado + [rearmFrames] fotogramas seguidos no azules -> se rearma (el círculo volvió a su estado original).
 *
 * Con rearmFrames = 1 se detectan cambios muy rápidos (azul, gris, azul en fotogramas consecutivos).
 */
class TriggerStateMachine(count: Int, private val rearmFrames: Int = 1) {
    private val armed = BooleanArray(count) { true }
    private val nonBlue = IntArray(count)

    /** @return true únicamente en el flanco gris -> azul con el círculo armado. */
    fun update(index: Int, blue: Boolean): Boolean {
        if (blue) {
            nonBlue[index] = 0
            if (armed[index]) {
                armed[index] = false
                return true
            }
            return false
        }
        if (!armed[index]) {
            nonBlue[index]++
            if (nonBlue[index] >= rearmFrames) {
                armed[index] = true
                nonBlue[index] = 0
            }
        }
        return false
    }

    fun isArmed(index: Int): Boolean = armed[index]

    fun reset() {
        for (i in armed.indices) {
            armed[i] = true
            nonBlue[i] = 0
        }
    }
}
