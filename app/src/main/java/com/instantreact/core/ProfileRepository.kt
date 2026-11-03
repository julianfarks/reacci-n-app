package com.instantreact.core

/** Almacén clave/valor mínimo (SharedPreferences en Android, un mapa en las pruebas). */
interface KeyValueStore {
    fun get(key: String): String?
    fun put(key: String, value: String?)
}

class MapStore : KeyValueStore {
    val map = HashMap<String, String>()
    override fun get(key: String): String? = map[key]
    override fun put(key: String, value: String?) {
        if (value == null) map.remove(key) else map[key] = value
    }
}

/**
 * Perfiles de calibración guardados + ajustes globales (retraso y duración del toque).
 * [portraitW] x [portraitH] es el tamaño de la pantalla en vertical, usado para los perfiles por defecto.
 */
class ProfileRepository(
    private val store: KeyValueStore,
    private val portraitW: Int,
    private val portraitH: Int,
) {
    private fun ids(): MutableList<String> =
        (store.get("ids") ?: "").split(',').filter { it.isNotBlank() }.toMutableList()

    private fun nextId(): String {
        val n = (store.get("next")?.toIntOrNull() ?: 1)
        store.put("next", (n + 1).toString())
        return "p$n"
    }

    fun hasProfiles(): Boolean = list().isNotEmpty()

    /** Primer arranque: crea los perfiles de ejemplo; el de 8 puntos queda activo y ya calibrado. */
    fun ensureDefaults() {
        if (hasProfiles()) {
            if (store.get("active") == null || get(store.get("active")!!) == null) setActive(list().first().id)
            return
        }
        val p8 = create("Perfil 8 puntos", 8)
        create("Perfil 6 puntos", 6)
        create("Perfil 10 puntos", 10)
        setActive(p8.id)
    }

    fun list(): List<Profile> = ids().mapNotNull { id -> store.get("pf.$id")?.let { ProfileCodec.decode(id, it) } }

    fun get(id: String): Profile? = list().firstOrNull { it.id == id }

    fun activeId(): String = store.get("active") ?: list().firstOrNull()?.id ?: ""

    fun active(): Profile {
        ensureDefaults()
        return get(activeId()) ?: list().first()
    }

    fun setActive(id: String) {
        if (get(id) != null) store.put("active", id)
    }

    /** Inserta o actualiza. */
    fun save(p: Profile) {
        val l = ids()
        if (p.id !in l) {
            l.add(p.id)
            store.put("ids", l.joinToString(","))
        }
        store.put("pf.${p.id}", ProfileCodec.encode(p))
    }

    fun create(name: String, count: Int, blue: Int = 60): Profile {
        val n = count.coerceIn(1, DetectorConfig.MAX_POINTS)
        val p = DefaultLayout.profile(nextId(), ProfileCodec.cleanName(name), n, portraitW, portraitH, blue)
        save(p)
        return p
    }

    fun duplicate(id: String, name: String): Profile? {
        val src = get(id) ?: return null
        val p = src.copy(id = nextId(), name = ProfileCodec.cleanName(name))
        save(p)
        return p
    }

    fun rename(id: String, name: String) {
        val p = get(id) ?: return
        save(p.copy(name = ProfileCodec.cleanName(name)))
    }

    /** No se puede borrar el último perfil. */
    fun delete(id: String): Boolean {
        val l = ids()
        if (l.size <= 1 || id !in l) return false
        l.remove(id)
        store.put("ids", l.joinToString(","))
        store.put("pf.$id", null)
        if (store.get("active") == id) store.put("active", l.first())
        return true
    }

    var delayMs: Long
        get() = store.get("delay")?.toLongOrNull() ?: 0L
        set(v) = store.put("delay", v.toString())

    var tapDurationMs: Long
        get() = store.get("tap")?.toLongOrNull() ?: 10L
        set(v) = store.put("tap", v.toString())

    fun buildConfig(): DetectorConfig = Profiles.toConfig(active(), delayMs, tapDurationMs)
}
