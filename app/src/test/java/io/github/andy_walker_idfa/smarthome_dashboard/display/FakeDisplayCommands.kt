package io.github.andy_walker_idfa.smarthome_dashboard.display

/** Records display commands. */
class FakeDisplayCommands(var nightEnabled: Boolean = true) : DisplayCommands {
    val calls = mutableListOf<String>()

    override fun setBrightnessPercent(percent: Int) {
        calls += "brightness=$percent"
    }

    override fun setNight(night: Boolean): Boolean {
        if (!nightEnabled) return false
        calls += "night=$night"
        return true
    }
}
