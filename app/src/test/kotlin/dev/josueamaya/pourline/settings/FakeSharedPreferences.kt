package dev.josueamaya.pourline.settings

import android.content.SharedPreferences

/** Un `SharedPreferences` en memoria para los tests JVM; cuenta las escrituras ([writes]) y las claves borradas. */
class FakeSharedPreferences : SharedPreferences {
  val values = mutableMapOf<String, Any?>()
  var writes = 0
    private set

  override fun getAll(): MutableMap<String, *> = values.toMutableMap()

  override fun getString(key: String, defValue: String?): String? = typed(key, defValue)

  @Suppress("UNCHECKED_CAST")
  override fun getStringSet(key: String, defValues: MutableSet<String>?): MutableSet<String>? = typed(key, defValues)

  override fun getInt(key: String, defValue: Int): Int = typed(key, defValue)

  override fun getLong(key: String, defValue: Long): Long = typed(key, defValue)

  override fun getFloat(key: String, defValue: Float): Float = typed(key, defValue)

  override fun getBoolean(key: String, defValue: Boolean): Boolean = typed(key, defValue)

  override fun contains(key: String): Boolean = key in values

  /** Como `SharedPreferences` real: un valor de otro tipo lanza `ClassCastException`. */
  @Suppress("UNCHECKED_CAST")
  private fun <T> typed(key: String, default: T): T = if (key in values) values[key] as T else default

  override fun edit(): SharedPreferences.Editor = FakeEditor()

  override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit

  override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit

  private inner class FakeEditor : SharedPreferences.Editor {
    private val pending = mutableMapOf<String, Any?>()
    private val removed = mutableSetOf<String>()
    private var clear = false

    override fun putString(key: String, value: String?) = apply { pending[key] = value }

    override fun putStringSet(key: String, values: MutableSet<String>?) = apply { pending[key] = values?.toSet() }

    override fun putInt(key: String, value: Int) = apply { pending[key] = value }

    override fun putLong(key: String, value: Long) = apply { pending[key] = value }

    override fun putFloat(key: String, value: Float) = apply { pending[key] = value }

    override fun putBoolean(key: String, value: Boolean) = apply { pending[key] = value }

    override fun remove(key: String) = apply { removed += key }

    override fun clear() = apply { clear = true }

    override fun commit(): Boolean {
      if (clear) values.clear()
      removed.forEach { values.remove(it) }
      pending.forEach { (k, v) -> if (v == null) values.remove(k) else values[k] = v }
      writes++
      return true
    }

    override fun apply() {
      commit()
    }
  }
}
