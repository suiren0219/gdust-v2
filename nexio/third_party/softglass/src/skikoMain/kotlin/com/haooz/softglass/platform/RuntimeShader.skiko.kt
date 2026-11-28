package com.haooz.softglass.platform

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.asComposeShader
import androidx.compose.ui.graphics.colorspace.ColorSpaces
import androidx.compose.ui.graphics.toArgb
import org.jetbrains.skia.RuntimeEffect
import org.jetbrains.skia.RuntimeShaderBuilder

actual fun createGlassRuntimeShader(source: String): GlassRuntimeShader =
    SkikoGlassRuntimeShader(RuntimeShaderBuilder(RuntimeEffect.makeForShader(source)))

actual fun GlassRuntimeShader.asComposeShader(): Shader =
    asSkikoRuntimeShader().makeShader().asComposeShader()

fun GlassRuntimeShader.asSkikoRuntimeShader(): RuntimeShaderBuilder =
    (this as SkikoGlassRuntimeShader).builder

internal class SkikoGlassRuntimeShader(val builder: RuntimeShaderBuilder) : GlassRuntimeShader {

    private val uniforms = UniformCache()

    override fun setFloatUniform(name: String, value: Float) {
        if (uniforms.setFloat1(name, value)) builder.uniform(name, value)
    }

    override fun setFloatUniform(name: String, value1: Float, value2: Float) {
        if (uniforms.setFloat2(name, value1, value2)) builder.uniform(name, value1, value2)
    }

    override fun setFloatUniform(name: String, value1: Float, value2: Float, value3: Float) {
        if (uniforms.setFloat3(name, value1, value2, value3)) {
            builder.uniform(name, value1, value2, value3)
        }
    }

    override fun setFloatUniform(name: String, value1: Float, value2: Float, value3: Float, value4: Float) {
        if (uniforms.setFloat4(name, value1, value2, value3, value4)) {
            builder.uniform(name, value1, value2, value3, value4)
        }
    }

    override fun setFloatUniform(name: String, values: FloatArray) {
        if (uniforms.setFloatArray(name, values)) builder.uniform(name, values)
    }

    override fun setIntUniform(name: String, value: Int) {
        if (uniforms.setInt1(name, value)) builder.uniform(name, value)
    }

    override fun setIntUniform(name: String, value1: Int, value2: Int) {
        if (uniforms.setInt2(name, value1, value2)) builder.uniform(name, value1, value2)
    }

    override fun setIntUniform(name: String, value1: Int, value2: Int, value3: Int) {
        if (uniforms.setInt3(name, value1, value2, value3)) {
            builder.uniform(name, value1, value2, value3)
        }
    }

    override fun setIntUniform(name: String, value1: Int, value2: Int, value3: Int, value4: Int) {
        if (uniforms.setInt4(name, value1, value2, value3, value4)) {
            builder.uniform(name, value1, value2, value3, value4)
        }
    }

    override fun setIntUniform(name: String, values: IntArray) {
        throw UnsupportedOperationException("Skia runtime shader does not support int array uniforms")
    }

    override fun setColorUniform(name: String, color: Color) {
        val argb = color.toArgb()
        if (!uniforms.setColor(name, argb)) return
        val srgb = color.convert(ColorSpaces.Srgb)
        val a = srgb.alpha
        builder.uniform(name, srgb.red * a, srgb.green * a, srgb.blue * a, a)
    }
}
