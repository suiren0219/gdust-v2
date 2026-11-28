package example.power

import com.menglimi.power.PowerApi
import com.menglimi.power.PowerBinding
import com.menglimi.power.PowerDetail
import com.menglimi.power.PowerDetailSource
import com.menglimi.power.PowerHistory
import com.menglimi.power.PowerMonitor
import com.menglimi.power.PowerMonitorConfig
import com.menglimi.power.PowerMonitorState
import com.menglimi.power.PowerMonitorStore
import kotlinx.coroutines.runBlocking

/** 示例用内存存储；Android App 可按同一接口换成数据库或 SharedPreferences。 */
class MemoryPowerStore(initial: PowerMonitorConfig) : PowerMonitorStore {
    private var configuration = initial
    private var current = PowerMonitorState()

    @Synchronized override fun config(): PowerMonitorConfig = configuration
    @Synchronized override fun state(): PowerMonitorState = current

    @Synchronized override fun update(
        binding: PowerBinding,
        transform: (PowerMonitorState) -> PowerMonitorState,
    ): Boolean {
        if (configuration.binding != binding) return false
        current = transform(current)
        return true
    }

    @Synchronized fun configure(next: PowerMonitorConfig) {
        if (configuration.binding != next.binding) current = PowerMonitorState()
        configuration = next
    }
}

fun main(args: Array<String>) = runBlocking {
    val live = args.contains("--live")
    val binding = if (live) PowerBinding(
        System.getenv("POWER_ACCOUNT") ?: error("请设置 POWER_ACCOUNT"),
        System.getenv("POWER_CUSTOMER_CODE")?.toIntOrNull() ?: error("请设置 POWER_CUSTOMER_CODE"),
        System.getenv("POWER_ROOM_VERIFY") ?: error("请设置 POWER_ROOM_VERIFY"),
    ) else PowerBinding("synthetic-account", 1, "synthetic-room")
    require(binding.isValid) { "电费绑定不完整" }
    val store = MemoryPowerStore(PowerMonitorConfig(binding = binding))
    val source = if (live) PowerApi() else PowerDetailSource {
        PowerDetail(12.5, 1.0, listOf("一" to 1.0), 30.0, 100.0)
    }
    val monitor = PowerMonitor(store, source, onWarning = {
        println("预警：${it.kind}，剩余 ${it.current.odd} 度")
        true
    })
    val result = monitor.query(force = true)
    check(result.sampled && result.reading != null)
    check(monitor.cachedReading() == result.reading)
    println("${if (live) "实时查询" else "离线合成演示"}：剩余 ${result.reading!!.detail.odd} 度")
    println("本机历史 ${PowerHistory.group(store.state().samples).size} 天；只依赖 power-core。")
}
