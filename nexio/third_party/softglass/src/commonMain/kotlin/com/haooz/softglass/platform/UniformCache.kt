package com.haooz.softglass.platform

/**
 * uniform 写入缓存：同一 shader 实例上重复写相同值直接跳过。
 *
 * 实时路径每个控件每帧都会重灌一整套样式（约 35 个 uniform），真正逐帧变化的
 * 只有尺寸 / 原点 / 展开进度少数几个。缓存把重复的 JNI 调用（Android）与
 * builder 写入（Skiko）省掉，视觉完全不变。
 *
 * 缓存记录的是「该实例最后一次实际写入的值」，共享同一个 shader 的多个控件
 * 交替写入也不会漏写：值不同就会走真实写入并更新缓存。
 */
internal class UniformCache {

    private val floats = HashMap<String, FloatArray>(64)
    private val ints = HashMap<String, IntArray>(16)
    private val colors = HashMap<String, Int>(8)

    fun setFloat1(name: String, value: Float): Boolean {
        var a = floats.getOrPut(name) { FloatArray(1) }
        if (a.size != 1) {
            a = FloatArray(1).also { floats[name] = it }
        }
        if (a[0] == value) return false
        a[0] = value
        return true
    }

    fun setFloat2(name: String, value1: Float, value2: Float): Boolean {
        var a = floats.getOrPut(name) { FloatArray(2) }
        if (a.size != 2) {
            a = FloatArray(2).also { floats[name] = it }
        }
        if (a[0] == value1 && a[1] == value2) return false
        a[0] = value1
        a[1] = value2
        return true
    }

    fun setFloat3(name: String, value1: Float, value2: Float, value3: Float): Boolean {
        var a = floats.getOrPut(name) { FloatArray(3) }
        if (a.size != 3) {
            a = FloatArray(3).also { floats[name] = it }
        }
        if (a[0] == value1 && a[1] == value2 && a[2] == value3) return false
        a[0] = value1
        a[1] = value2
        a[2] = value3
        return true
    }

    fun setFloat4(name: String, value1: Float, value2: Float, value3: Float, value4: Float): Boolean {
        var a = floats.getOrPut(name) { FloatArray(4) }
        if (a.size != 4) {
            a = FloatArray(4).also { floats[name] = it }
        }
        if (a[0] == value1 && a[1] == value2 && a[2] == value3 && a[3] == value4) return false
        a[0] = value1
        a[1] = value2
        a[2] = value3
        a[3] = value4
        return true
    }

    fun setFloatArray(name: String, values: FloatArray): Boolean {
        val prev = floats[name]
        if (prev != null && prev.contentEquals(values)) return false
        floats[name] = values.copyOf()
        return true
    }

    fun setInt1(name: String, value: Int): Boolean {
        var a = ints.getOrPut(name) { IntArray(1) }
        if (a.size != 1) {
            a = IntArray(1).also { ints[name] = it }
        }
        if (a[0] == value) return false
        a[0] = value
        return true
    }

    fun setInt2(name: String, value1: Int, value2: Int): Boolean {
        var a = ints.getOrPut(name) { IntArray(2) }
        if (a.size != 2) {
            a = IntArray(2).also { ints[name] = it }
        }
        if (a[0] == value1 && a[1] == value2) return false
        a[0] = value1
        a[1] = value2
        return true
    }

    fun setInt3(name: String, value1: Int, value2: Int, value3: Int): Boolean {
        var a = ints.getOrPut(name) { IntArray(3) }
        if (a.size != 3) {
            a = IntArray(3).also { ints[name] = it }
        }
        if (a[0] == value1 && a[1] == value2 && a[2] == value3) return false
        a[0] = value1
        a[1] = value2
        a[2] = value3
        return true
    }

    fun setInt4(name: String, value1: Int, value2: Int, value3: Int, value4: Int): Boolean {
        var a = ints.getOrPut(name) { IntArray(4) }
        if (a.size != 4) {
            a = IntArray(4).also { ints[name] = it }
        }
        if (a[0] == value1 && a[1] == value2 && a[2] == value3 && a[3] == value4) return false
        a[0] = value1
        a[1] = value2
        a[2] = value3
        a[3] = value4
        return true
    }

    fun setIntArray(name: String, values: IntArray): Boolean {
        val prev = ints[name]
        if (prev != null && prev.contentEquals(values)) return false
        ints[name] = values.copyOf()
        return true
    }

    fun setColor(name: String, argb: Int): Boolean {
        val prev = colors[name]
        if (prev == argb) return false
        colors[name] = argb
        return true
    }
}
