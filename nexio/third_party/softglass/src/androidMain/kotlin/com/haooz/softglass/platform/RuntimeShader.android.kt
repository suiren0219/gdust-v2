package com.haooz.softglass.platform

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.toArgb

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
actual fun createGlassRuntimeShader(source: String): GlassRuntimeShader =
    AndroidGlassRuntimeShader(android.graphics.RuntimeShader(source))

actual fun GlassRuntimeShader.asComposeShader(): Shader = asAndroidRuntimeShader()

fun GlassRuntimeShader.asAndroidRuntimeShader(): android.graphics.RuntimeShader =
    (this as AndroidGlassRuntimeShader).shader

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
internal class AndroidGlassRuntimeShader(val shader: android.graphics.RuntimeShader) : GlassRuntimeShader {

    private val uniforms = UniformCache()

    override fun setFloatUniform(name: String, value: Float) {
        if (uniforms.setFloat1(name, value)) shader.setFloatUniform(name, value)
    }

    override fun setFloatUniform(name: String, value1: Float, value2: Float) {
        if (uniforms.setFloat2(name, value1, value2)) shader.setFloatUniform(name, value1, value2)
    }

    override fun setFloatUniform(name: String, value1: Float, value2: Float, value3: Float) {
        if (uniforms.setFloat3(name, value1, value2, value3)) {
            shader.setFloatUniform(name, value1, value2, value3)
        }
    }

    override fun setFloatUniform(name: String, value1: Float, value2: Float, value3: Float, value4: Float) {
        if (uniforms.setFloat4(name, value1, value2, value3, value4)) {
            shader.setFloatUniform(name, value1, value2, value3, value4)
        }
    }

    override fun setFloatUniform(name: String, values: FloatArray) {
        if (uniforms.setFloatArray(name, values)) shader.setFloatUniform(name, values)
    }

    override fun setIntUniform(name: String, value: Int) {
        if (uniforms.setInt1(name, value)) shader.setIntUniform(name, value)
    }

    override fun setIntUniform(name: String, value1: Int, value2: Int) {
        if (uniforms.setInt2(name, value1, value2)) shader.setIntUniform(name, value1, value2)
    }

    override fun setIntUniform(name: String, value1: Int, value2: Int, value3: Int) {
        if (uniforms.setInt3(name, value1, value2, value3)) {
            shader.setIntUniform(name, value1, value2, value3)
        }
    }

    override fun setIntUniform(name: String, value1: Int, value2: Int, value3: Int, value4: Int) {
        if (uniforms.setInt4(name, value1, value2, value3, value4)) {
            shader.setIntUniform(name, value1, value2, value3, value4)
        }
    }

    override fun setIntUniform(name: String, values: IntArray) {
        if (uniforms.setIntArray(name, values)) shader.setIntUniform(name, values)
    }

    override fun setColorUniform(name: String, color: Color) {
        val argb = color.toArgb()
        if (uniforms.setColor(name, argb)) shader.setColorUniform(name, argb)
    }
}
