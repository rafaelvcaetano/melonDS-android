package me.magnum.melonds.common.opengl

class ShaderProgramSource private constructor(val textureFiltering: TextureFiltering, val vertexShaderSource: String, val fragmentShaderSource: String) {
    enum class TextureFiltering {
        NEAREST,
        LINEAR
    }

    companion object {
        private const val DEFAULT_VERT_SHADER = "attribute vec2 vUV;\n" +
                "attribute vec2 vPos;\n" +
                "attribute float vAlpha;\n" +
                "varying vec2 uv;\n" +
                "varying float alpha;\n" +
                "void main()\n" +
                "{\n" +
                "    gl_Position = vec4(vPos, 0.0, 1.0);\n" +
                "    uv = vUV;\n" +
                "    alpha = vAlpha;\n" +
                "}"

        private const val DEFAULT_FRAG_SHADER = "precision mediump float;\n" +
                "uniform sampler2D tex;\n" +
                "varying vec2 uv;\n" +
                "varying float alpha;\n" +
                "void main()\n" +
                "{\n" +
                "    vec4 color = texture2D(tex, uv);\n" +
                "    gl_FragColor = vec4(color.bgr, alpha);\n" +
                "}"

        val BackgroundShader = ShaderProgramSource(
            TextureFiltering.LINEAR,
            "attribute vec2 vUV;\n" +
                    "attribute vec2 vPos;\n" +
                    "varying vec2 uv;\n" +
                    "void main()\n" +
                    "{\n" +
                    "    gl_Position = vec4(vPos, 0.0, 1.0);\n" +
                    "    uv = vUV;\n" +
                    "}",
            "precision mediump float;\n" +
                    "uniform sampler2D tex;\n" +
                    "varying vec2 uv;\n" +
                    "void main()\n" +
                    "{\n" +
                    "    vec4 color = texture2D(tex, uv);\n" +
                    "    gl_FragColor = vec4(color.rgb, 1);\n" +
                    "}"
        )

        val NoFilterShader = ShaderProgramSource(
            TextureFiltering.NEAREST,
            DEFAULT_VERT_SHADER,
            DEFAULT_FRAG_SHADER
        )

        val LinearShader = ShaderProgramSource(
            TextureFiltering.LINEAR,
            DEFAULT_VERT_SHADER,
            DEFAULT_FRAG_SHADER
        )

        // Author: Gigaherz
        // License: Public domain
        val LcdShader = ShaderProgramSource(
            TextureFiltering.NEAREST,
                "attribute vec2 vPos;\n" +
                    "attribute vec2 vUV;\n" +
                    "attribute float vAlpha;\n" +
                    "varying vec2 uv;\n" +
                    "varying float alpha;\n" +
                    "varying vec2 omega;\n" +
                    "" +
                    "void main() {\n" +
                    "    gl_Position = vec4(vPos, 0.0, 1.0);\n" +
                    "    uv = vUV;\n" +
                    "    alpha = vAlpha;\n" +
                    "    omega = 3.141592654 * 2.0 * vec2(256, 384);\n" +
                    "}",
            "#ifdef GL_FRAGMENT_PRECISION_HIGH\n" +
                    "precision highp float;\n" +
                    "#else\n" +
                    "precision mediump float;\n" +
                    "#endif\n" +
                    "uniform sampler2D tex;\n" +
                    "varying vec2 uv;\n" +
                    "varying float alpha;\n" +
                    "varying vec2 omega;\n" +
                    "" +
                    "/* configuration (higher values mean brighter image but reduced effect depth) */\n" +
                    "const float brighten_scanlines = 16.0;\n" +
                    "const float brighten_lcd = 4.0;\n" +
                    "" +
                    "const vec3 offsets = 3.141592654 * vec3(1.0/2.0,1.0/2.0 - 2.0/3.0,1.0/2.0-4.0/3.0);\n" +
                    "" +
                    "void main() {\n" +
                    "    vec2 angle = uv * omega;\n" +
                    "" +
                    "    float yfactor = (brighten_scanlines + sin(angle.y)) / (brighten_scanlines + 1.0);\n" +
                    "    vec3 xfactors = (brighten_lcd + sin(angle.x + offsets)) / (brighten_lcd + 1.0);\n" +
                    "" +
                    "    gl_FragColor.rgb = yfactor * xfactors * texture2D(tex, uv).bgr;\n" +
                    "    gl_FragColor.a = alpha;\n" +
                    "}"
        )

        val LcdGridDsLiteShader = ShaderProgramSource(
            TextureFiltering.NEAREST,
            DEFAULT_VERT_SHADER,
            "#ifdef GL_FRAGMENT_PRECISION_HIGH\n" +
                "precision highp float;\n" +
                "#else\n" +
                "precision mediump float;\n" +
                "#endif\n" +
                "uniform sampler2D tex;\n" +
                "uniform sampler2D prevTex;\n" +
                "uniform vec2 texSize;\n" +
                "uniform vec2 viewportSize;\n" +
                "uniform vec4 screenUvBounds;\n" +
                "uniform float responseWeight;\n" +
                "varying vec2 uv;\n" +
                "varying float alpha;\n" +
                "const float brighten_scanlines = 16.0;\n" +
                "const float brighten_lcd = 4.0;\n" +
                "const vec3 offsets = 3.141592654 * vec3(0.5, 0.5 - 0.6666667, 0.5 - 1.3333333);\n" +
                "const float gain = 1.0;\n" +
                "const float gamma = 2.2;\n" +
                "const float blacklevel = 0.0;\n" +
                "const float ambient = 0.0;\n" +
                "const float outgamma = 2.2;\n" +
                "const float maskContrast = 0.8;\n" +
                "const float detailBlend = 0.3;\n" +
                "const vec3 postBias = vec3(0.0);\n" +
                "const vec2 lowerScreenPhase = vec2(0.15, 0.10);\n" +
                "const vec3 channelGain = vec3(1.07, 0.97, 1.05);\n" +
                "const float saturationBoost = 1.12;\n" +
                "const vec3 rSubpixel = vec3(1.0, 0.0, 0.0);\n" +
                "const vec3 gSubpixel = vec3(0.0, 1.0, 0.0);\n" +
                "const vec3 bSubpixel = vec3(0.0, 0.0, 1.0);\n" +
                "const float useBgr = 1.0;\n" +
                "const float targetGamma = 2.2;\n" +
                "const float displayGamma = 2.2;\n" +
                "const float dslLuminance = 0.955;\n" +
                "const mat3 dslMatrix = mat3(\n" +
                "    0.965, 0.11, -0.065,\n" +
                "    0.02, 0.925, 0.055,\n" +
                "    0.01, -0.02, 1.03\n" +
                ");\n" +
                "float intsmear_func_x(float z) {\n" +
                "    float z2 = z * z;\n" +
                "    float zn = z;\n" +
                "    float ret = 0.0;\n" +
                "    ret += zn * 1.0;\n" +
                "    zn *= z2;\n" +
                "    ret += zn * -0.6666667;\n" +
                "    zn *= z2;\n" +
                "    ret += zn * -0.2;\n" +
                "    zn *= z2;\n" +
                "    ret += zn * 0.5714286;\n" +
                "    zn *= z2;\n" +
                "    ret += zn * -0.1111111;\n" +
                "    zn *= z2;\n" +
                "    ret += zn * -0.1818182;\n" +
                "    zn *= z2;\n" +
                "    ret += zn * 0.0769231;\n" +
                "    return ret;\n" +
                "}\n" +
                "float intsmear_func_y(float z) {\n" +
                "    float z2 = z * z;\n" +
                "    float zn = z;\n" +
                "    float ret = 0.0;\n" +
                "    ret += zn * 1.0;\n" +
                "    zn *= z2;\n" +
                "    ret += zn * 0.0;\n" +
                "    zn *= z2;\n" +
                "    ret += zn * -0.8;\n" +
                "    zn *= z2;\n" +
                "    ret += zn * 0.2857143;\n" +
                "    zn *= z2;\n" +
                "    ret += zn * 0.4444444;\n" +
                "    zn *= z2;\n" +
                "    ret += zn * -0.3636364;\n" +
                "    zn *= z2;\n" +
                "    ret += zn * 0.0769231;\n" +
                "    return ret;\n" +
                "}\n" +
                "float intsmear_x(float x, float dx, float d) {\n" +
                "    float zl = clamp((x - dx * 0.5) / d, -1.0, 1.0);\n" +
                "    float zh = clamp((x + dx * 0.5) / d, -1.0, 1.0);\n" +
                "    return d * (intsmear_func_x(zh) - intsmear_func_x(zl)) / dx;\n" +
                "}\n" +
                "float intsmear_y(float x, float dx, float d) {\n" +
                "    float zl = clamp((x - dx * 0.5) / d, -1.0, 1.0);\n" +
                "    float zh = clamp((x + dx * 0.5) / d, -1.0, 1.0);\n" +
                "    return d * (intsmear_func_y(zh) - intsmear_func_y(zl)) / dx;\n" +
                "}\n" +
                "vec3 fetchBlended(ivec2 baseCoord, ivec2 offset, vec2 texelSize) {\n" +
                "    vec2 coord = (vec2(baseCoord + offset) + vec2(0.5)) * texelSize;\n" +
                "    vec3 curr = texture2D(tex, coord).bgr;\n" +
                "    vec3 prev = texture2D(prevTex, coord).bgr;\n" +
                "    vec3 blended = mix(curr, prev, responseWeight);\n" +
                "    return pow(gain * blended + vec3(blacklevel), vec3(gamma)) + vec3(ambient);\n" +
                "}\n" +
                "void main() {\n" +
                "    vec2 safeTexSize = max(texSize, vec2(1.0));\n" +
                "    vec2 texelSize = 1.0 / safeTexSize;\n" +
                "    vec2 uvMin = screenUvBounds.xy;\n" +
                "    vec2 uvMax = screenUvBounds.zw;\n" +
                "    vec2 uvRange = max(uvMax - uvMin, vec2(1e-6));\n" +
                "    float screenTexWidth = max(safeTexSize.x * uvRange.x, 1.0);\n" +
                "    float screenTexHeight = max(safeTexSize.y * uvRange.y, 1.0);\n" +
                "    float texelWidthScreen = 1.0 / screenTexWidth;\n" +
                "    float texelHeightScreen = 1.0 / screenTexHeight;\n" +
                "    vec2 viewport = max(viewportSize, vec2(1.0));\n" +
                "    float localX = clamp((uv.x - uvMin.x) / uvRange.x, 0.0, 1.0);\n" +
                "    float localY = clamp((uv.y - uvMin.y) / uvRange.y, 0.0, 1.0);\n" +
                "    vec2 screenPixelCoord = vec2(localX * screenTexWidth, localY * screenTexHeight);\n" +
                "    vec2 pixelCoord = uv / texelSize - vec2(0.4999);\n" +
                "    ivec2 tli = ivec2(floor(pixelCoord));\n" +
                "    float subpix = (uv.x / texelSize.x - 0.4999 - float(tli.x)) * 3.0;\n" +
                "    float rsubpix = (screenTexWidth / viewport.x) * 3.0;\n" +
                "    vec3 lcol = vec3(\n" +
                "        intsmear_x(subpix + 1.0, rsubpix, 1.5),\n" +
                "        intsmear_x(subpix, rsubpix, 1.5),\n" +
                "        intsmear_x(subpix - 1.0, rsubpix, 1.5)\n" +
                "    );\n" +
                "    vec3 rcol = vec3(\n" +
                "        intsmear_x(subpix - 2.0, rsubpix, 1.5),\n" +
                "        intsmear_x(subpix - 3.0, rsubpix, 1.5),\n" +
                "        intsmear_x(subpix - 4.0, rsubpix, 1.5)\n" +
                "    );\n" +
                "    if (useBgr > 0.5) {\n" +
                "        lcol = lcol.bgr;\n" +
                "        rcol = rcol.bgr;\n" +
                "    }\n" +
                "    float subpixY = uv.y / texelSize.y - 0.4999 - float(tli.y);\n" +
                "    float rsubpixY = screenTexHeight / viewport.y;\n" +
                "    float tcol = intsmear_y(subpixY, rsubpixY, 0.63);\n" +
                "    float bcol = intsmear_y(subpixY - 1.0, rsubpixY, 0.63);\n" +
                "    vec3 topLeftColor = fetchBlended(tli, ivec2(0, 0), texelSize) * lcol * vec3(tcol);\n" +
                "    vec3 bottomRightColor = fetchBlended(tli, ivec2(1, 1), texelSize) * rcol * vec3(bcol);\n" +
                "    vec3 bottomLeftColor = fetchBlended(tli, ivec2(0, 1), texelSize) * lcol * vec3(bcol);\n" +
                "    vec3 topRightColor = fetchBlended(tli, ivec2(1, 0), texelSize) * rcol * vec3(tcol);\n" +
                "    vec3 averageColor = topLeftColor + bottomRightColor + bottomLeftColor + topRightColor;\n" +
                "    vec2 angle = screenPixelCoord * 6.28318530718;\n" +
                "    float lowerScreen = step(0.5, 0.5 * (uvMin.y + uvMax.y));\n" +
                "    angle += lowerScreen * lowerScreenPhase;\n" +
                "    float yfactor = (brighten_scanlines + sin(angle.y)) / (brighten_scanlines + 1.0);\n" +
                "    vec3 xfactors = (brighten_lcd + sin(angle.x + offsets)) / (brighten_lcd + 1.0);\n" +
                "    vec3 rawAverage = averageColor;\n" +
                "    vec3 mask = yfactor * xfactors;\n" +
                "    vec3 softenedMask = mix(vec3(1.0), mask, maskContrast);\n" +
                "    vec3 maskedColor = rawAverage * softenedMask;\n" +
                "    averageColor = mix(maskedColor, rawAverage, detailBlend);\n" +
                "    vec3 cred = pow(rSubpixel, vec3(outgamma));\n" +
                "    vec3 cgreen = pow(gSubpixel, vec3(outgamma));\n" +
                "    vec3 cblue = pow(bSubpixel, vec3(outgamma));\n" +
                "    averageColor = mat3(cred, cgreen, cblue) * averageColor;\n" +
                "    vec3 baseColor = pow(averageColor, vec3(1.0 / outgamma));\n" +
                "    vec3 dslLinear = pow(baseColor, vec3(targetGamma));\n" +
                "    dslLinear = clamp(dslLinear * dslLuminance, 0.0, 1.0);\n" +
                "    vec3 corrected = dslMatrix * dslLinear;\n" +
                "    corrected *= channelGain;\n" +
                "    float gray = dot(corrected, vec3(0.299, 0.587, 0.114));\n" +
                "    corrected = mix(vec3(gray), corrected, saturationBoost);\n" +
                "    vec3 finalColor = pow(corrected, vec3(1.0 / displayGamma));\n" +
                "    finalColor = clamp(finalColor + postBias, 0.0, 1.0);\n" +
                "    gl_FragColor = vec4(finalColor, alpha);\n" +
                "}"
        )

        // Author: Themaister
        // This code is hereby placed in the public domain.
        val ScanlinesShader = ShaderProgramSource(
            TextureFiltering.NEAREST,
                "attribute vec2 vPos;\n" +
                    "attribute vec2 vUV;\n" +
                    "attribute float vAlpha;\n" +
                    "varying vec2 uv;\n" +
                    "varying float alpha;\n" +
                    "varying vec2 omega;\n" +
                    "" +
                    "vec2 inputSize = vec2(256, 384);\n" + // What is this?
                    "vec2 outputSize = vec2(256, 384);\n" + // What is this?
                    "" +
                    "void main()\n" +
                    "{\n" +
                    "    gl_Position = vec4(vPos, 0.0, 1.0);\n" +
                    "    uv = vUV;\n" +
                    "    vec2 textureSize = vec2(256, 384);\n" +
                    "    alpha = vAlpha;\n" +
                    "    omega = vec2(3.1415 * outputSize.x * textureSize.x / inputSize.x, 2.0 * 3.1415 * textureSize.y);\n" +
                    "}",
            "#ifdef GL_FRAGMENT_PRECISION_HIGH\n" +
                    "precision highp float;\n" +
                    "#else\n" +
                    "precision mediump float;\n" +
                    "#endif\n" +
                    "" +
                    "uniform sampler2D tex;\n" +
                    "varying float alpha;\n" +
                    "varying vec2 uv;\n" +
                    "varying vec2 omega;\n" +
                    "" +
                    "const float base_brightness = 0.95;\n" +
                    "const vec2 sine_comp = vec2(0.05, 0.15);\n" +
                    "" +
                    "void main ()\n" +
                    "{\n" +
                    "    vec4 color = vec4(texture2D(tex, uv).bgr, alpha);\n" +
                    "" +
                    "    vec4 scanline = color * (base_brightness + dot(sine_comp * sin(uv * omega), vec2(1.0)));\n" +
                    "    gl_FragColor = clamp(scanline, 0.0, 1.0);\n" +
                    "}"
        )

        // Hyllian's 2xBR Shader
        //
        // Copyright (C) 2011 Hyllian/Jararaca - sergiogdb@gmail.com
        //
        // This program is free software; you can redistribute it and/or
        // modify it under the terms of the GNU General Public License
        // as published by the Free Software Foundation; either version 2
        // of the License, or (at your option) any later version.
        //
        // This program is distributed in the hope that it will be useful,
        // but WITHOUT ANY WARRANTY; without even the implied warranty of
        // MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
        // GNU General Public License for more details.
        //
        // You should have received a copy of the GNU General Public License
        // along with this program; if not, write to the Free Software
        // Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
        val XbrShader = ShaderProgramSource(
            TextureFiltering.NEAREST,
                "attribute vec2 vPos;\n" +
                    "attribute vec2 vUV;\n" +
                    "attribute float vAlpha;\n" +
                    "varying vec2 uv[3];\n" +
                    "varying float alpha;\n" +
                    "" +
                    "void main() {\n" +
                    "    vec2 ps = 1.0 / vec2(256, 384);\n" +
                    "    uv[0] = vUV;\n" +
                    "    uv[1] = vec2(0.0, -ps.y);\n" +
                    "    uv[2] = vec2(-ps.x, 0.0);\n" +
                    "    alpha = vAlpha;\n" +
                    "" +
                    "    gl_Position = vec4(vPos, 0.0, 1.0);\n" +
                    "}",
            "#ifdef GL_FRAGMENT_PRECISION_HIGH\n" +
                    "precision highp float;\n" +
                    "#else\n" +
                    "precision mediump float;\n" +
                    "#endif\n" +
                    "uniform sampler2D tex;\n" +
                    "varying vec2 uv[3];\n" +
                    "varying float alpha;\n" +
                    "" +
                    "const vec3 dtt = vec3(65536.0, 255.0, 1.0);\n" +
                    "" +
                    "float reduce(vec3 color) {\n" +
                    "    return dot(color, dtt);\n" +
                    "}\n" +
                    "" +
                    "void main() {\n" +
                    "    vec2 fp = fract(uv[0] * vec2(256, 384));\n" +
                    "" +
                    "    vec2 g1 = uv[1] * (step(0.5, fp.x) + step(0.5, fp.y) - 1.0) +\n" +
                    "            uv[2] * (step(0.5, fp.x) - step(0.5, fp.y));\n" +
                    "    vec2 g2 = uv[1] * (step(0.5, fp.y) - step(0.5, fp.x)) +\n" +
                    "            uv[2] * (step(0.5, fp.x) + step(0.5, fp.y) - 1.0);\n" +
                    "" +
                    "    vec3 B = texture2D(tex, uv[0] + g1     ).bgr;\n" +
                    "    vec3 C = texture2D(tex, uv[0] + g1 - g2).bgr;\n" +
                    "    vec3 D = texture2D(tex, uv[0]      + g2).bgr;\n" +
                    "    vec3 E = texture2D(tex, uv[0]          ).bgr;\n" +
                    "    vec3 F = texture2D(tex, uv[0]      - g2).bgr;\n" +
                    "    vec3 G = texture2D(tex, uv[0] - g1 + g2).bgr;\n" +
                    "    vec3 H = texture2D(tex, uv[0] - g1     ).bgr;\n" +
                    "    vec3 I = texture2D(tex, uv[0] - g1 - g2).bgr;\n" +
                    "" +
                    "    float b = reduce(B);\n" +
                    "    float c = reduce(C);\n" +
                    "    float d = reduce(D);\n" +
                    "    float e = reduce(E);\n" +
                    "    float f = reduce(F);\n" +
                    "    float g = reduce(G);\n" +
                    "    float h = reduce(H);\n" +
                    "    float i = reduce(I);\n" +
                    "" +
                    "    gl_FragColor.rgb = E;\n" +
                    "" +
                    "    if (h==f && h!=e && ( e==g && (h==i || e==d) || e==c && (h==i || e==b) ))\n" +
                    "    {\n" +
                    "        gl_FragColor.rgb = mix(E, F, 0.5);\n" +
                    "    }\n" +
                    "    gl_fragColor.a = alpha;\n" +
                    "}"
        )

        val Hq2xShader = ShaderProgramSource(
            TextureFiltering.NEAREST,
                "attribute vec2 vPos;\n" +
                    "attribute vec2 vUV;\n" +
                    "attribute float vAlpha;\n" +
                    "varying vec4 uv[5];\n" +
                    "varying float alpha;\n" +
                    "" +
                    "void main() {\n" +
                    "    vec2 dg1 = 0.5 / vec2(256, 384);\n" +
                    "    vec2 dg2 = vec2(-dg1.x, dg1.y);\n" +
                    "    vec2 dx = vec2(dg1.x, 0.0);\n" +
                    "    vec2 dy = vec2(0.0, dg1.y);\n" +
                    "" +
                    "    uv[0].xy = vUV;\n" +
                    "    uv[1].xy = vUV - dg1;\n" +
                    "    uv[1].zw = vUV - dy;\n" +
                    "    uv[2].xy = vUV - dg2;\n" +
                    "    uv[2].zw = vUV + dx;\n" +
                    "    uv[3].xy = vUV + dg1;\n" +
                    "    uv[3].zw = vUV + dy;\n" +
                    "    uv[4].xy = vUV + dg2;\n" +
                    "    uv[4].zw = vUV - dx;\n" +
                    "    alpha = vAlpha;\n" +
                    "" +
                    "    gl_Position = vec4(vPos, 0.0, 1.0);\n" +
                    "}",
            "#ifdef GL_FRAGMENT_PRECISION_HIGH\n" +
                    "precision highp float;\n" +
                    "#else\n" +
                    "precision mediump float;\n" +
                    "#endif\n" +
                    "uniform sampler2D tex;\n" +
                    "varying vec4 uv[5];\n" +
                    "varying float alpha;\n" +
                    "" +
                    "    const float mx = 0.325;      // start smoothing wt.\n" +
                    "    const float k = -0.250;      // wt. decrease factor\n" +
                    "    const float max_w = 0.25;    // max filter weigth\n" +
                    "    const float min_w =-0.05;    // min filter weigth\n" +
                    "    const float lum_add = 0.25;  // effects smoothing\n" +
                    "" +
                    "void main() {\n" +
                    "    vec3 c00 = texture2D(tex, uv[1].xy).xyz; \n" +
                    "    vec3 c10 = texture2D(tex, uv[1].zw).xyz; \n" +
                    "    vec3 c20 = texture2D(tex, uv[2].xy).xyz; \n" +
                    "    vec3 c01 = texture2D(tex, uv[4].zw).xyz; \n" +
                    "    vec3 c11 = texture2D(tex, uv[0].xy).xyz; \n" +
                    "    vec3 c21 = texture2D(tex, uv[2].zw).xyz; \n" +
                    "    vec3 c02 = texture2D(tex, uv[4].xy).xyz; \n" +
                    "    vec3 c12 = texture2D(tex, uv[3].zw).xyz; \n" +
                    "    vec3 c22 = texture2D(tex, uv[3].xy).xyz; \n" +
                    "    vec3 dt = vec3(1.0, 1.0, 1.0);\n" +
                    "" +
                    "    float md1 = dot(abs(c00 - c22), dt);\n" +
                    "    float md2 = dot(abs(c02 - c20), dt);\n" +
                    "" +
                    "    float w1 = dot(abs(c22 - c11), dt) * md2;\n" +
                    "    float w2 = dot(abs(c02 - c11), dt) * md1;\n" +
                    "    float w3 = dot(abs(c00 - c11), dt) * md2;\n" +
                    "    float w4 = dot(abs(c20 - c11), dt) * md1;\n" +
                    "" +
                    "    float t1 = w1 + w3;\n" +
                    "    float t2 = w2 + w4;\n" +
                    "    float ww = max(t1, t2) + 0.001;\n" +
                    "" +
                    "    c11 = (w1 * c00 + w2 * c20 + w3 * c22 + w4 * c02 + ww * c11) / (t1 + t2 + ww);\n" +
                    "" +
                    "    float lc1 = k / (0.12 * dot(c10 + c12 + c11, dt) + lum_add);\n" +
                    "    float lc2 = k / (0.12 * dot(c01 + c21 + c11, dt) + lum_add);\n" +
                    "" +
                    "    w1 = clamp(lc1 * dot(abs(c11 - c10), dt) + mx, min_w, max_w);\n" +
                    "    w2 = clamp(lc2 * dot(abs(c11 - c21), dt) + mx, min_w, max_w);\n" +
                    "    w3 = clamp(lc1 * dot(abs(c11 - c12), dt) + mx, min_w, max_w);\n" +
                    "    w4 = clamp(lc2 * dot(abs(c11 - c01), dt) + mx, min_w, max_w);\n" +
                    "" +
                    "    gl_FragColor.bgr = w1 * c10 + w2 * c21 + w3 * c12 + w4 * c01 + (1.0 - w1 - w2 - w3 - w4) * c11;\n" +
                    "    gl_FragColor.a = alpha;\n" +
                    "}"
        )

        // 4xGLSLHqFilter shader
        //
        // Copyright (C) 2005 guest(r) - guest.r@gmail.com
        //
        // This program is free software; you can redistribute it and/or
        // modify it under the terms of the GNU General Public License
        // as published by the Free Software Foundation; either version 2
        // of the License, or (at your option) any later version.
        //
        // This program is distributed in the hope that it will be useful,
        // but WITHOUT ANY WARRANTY; without even the implied warranty of
        // MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
        // GNU General Public License for more details.
        //
        // You should have received a copy of the GNU General Public License
        // along with this program; if not, write to the Free Software
        // Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
        val Hq4xShader = ShaderProgramSource(
            TextureFiltering.NEAREST,
                "attribute vec2 vPos;\n" +
                    "attribute vec2 vUV;\n" +
                    "attribute float vAlpha;\n" +
                    "varying vec4 uv[7];\n" +
                    "varying float alpha;\n" +
                    "" +
                    "void main()\n" +
                    "{\n" +
                    "    vec2 dg1 = 0.5 / vec2(256, 384);\n" +
                    "    vec2 dg2 = vec2(-dg1.x, dg1.y);\n" +
                    "    vec2 sd1 = dg1 * 0.5;\n" +
                    "    vec2 sd2 = dg2 * 0.5;\n" +
                    "    vec2 ddx = vec2(dg1.x, 0.0);\n" +
                    "    vec2 ddy = vec2(0.0, dg1.y);\n" +
                    "" +
                    "    gl_Position = vec4(vPos, 0.0, 1.0);\n" +
                    "    uv[0].xy = vUV;\n" +
                    "    uv[1].xy = vUV - sd1;\n" +
                    "    uv[2].xy = vUV - sd2;\n" +
                    "    uv[3].xy = vUV + sd1;\n" +
                    "    uv[4].xy = vUV + sd2;\n" +
                    "    uv[5].xy = vUV - dg1;\n" +
                    "    uv[6].xy = vUV + dg1;\n" +
                    "    uv[5].zw = vUV - dg2;\n" +
                    "    uv[6].zw = vUV + dg2;\n" +
                    "    uv[1].zw = vUV - ddy;\n" +
                    "    uv[2].zw = vUV + ddx;\n" +
                    "    uv[3].zw = vUV + ddy;\n" +
                    "    uv[4].zw = vUV - ddx;\n" +
                    "    alpha = vAlpha;\n" +
                    "}",
            "#ifdef GL_FRAGMENT_PRECISION_HIGH\n" +
                    "precision highp float;\n" +
                    "#else\n" +
                    "precision mediump float;\n" +
                    "#endif\n" +
                    "uniform sampler2D tex;\n" +
                    "varying vec4 uv[7];\n" +
                    "varying float alpha;\n" +
                    "" +
                    "const float mx = 1.00;      // start smoothing wt.\n" +
                    "const float k = -1.10;      // wt. decrease factor\n" +
                    "const float max_w = 0.75;   // max filter weigth\n" +
                    "const float min_w = 0.03;   // min filter weigth\n" +
                    "const float lum_add = 0.33; // effects smoothing\n" +
                    "" +
                    "void main()\n" +
                    "{\n" +
                    "    vec3 c  = texture2D(tex, uv[0].xy).bgr;\n" +
                    "    vec3 i1 = texture2D(tex, uv[1].xy).bgr;\n" +
                    "    vec3 i2 = texture2D(tex, uv[2].xy).bgr;\n" +
                    "    vec3 i3 = texture2D(tex, uv[3].xy).bgr;\n" +
                    "    vec3 i4 = texture2D(tex, uv[4].xy).bgr;\n" +
                    "    vec3 o1 = texture2D(tex, uv[5].xy).bgr;\n" +
                    "    vec3 o3 = texture2D(tex, uv[6].xy).bgr;\n" +
                    "    vec3 o2 = texture2D(tex, uv[5].zw).bgr;\n" +
                    "    vec3 o4 = texture2D(tex, uv[6].zw).bgr;\n" +
                    "    vec3 s1 = texture2D(tex, uv[1].zw).bgr;\n" +
                    "    vec3 s2 = texture2D(tex, uv[2].zw).bgr;\n" +
                    "    vec3 s3 = texture2D(tex, uv[3].zw).bgr;\n" +
                    "    vec3 s4 = texture2D(tex, uv[4].zw).bgr;\n" +
                    "    vec3 dt = vec3(1.0,1.0,1.0);\n" +
                    "" +
                    "    float ko1=dot(abs(o1-c),dt);\n" +
                    "    float ko2=dot(abs(o2-c),dt);\n" +
                    "    float ko3=dot(abs(o3-c),dt);\n" +
                    "    float ko4=dot(abs(o4-c),dt);\n" +
                    "" +
                    "    float k1=min(dot(abs(i1-i3),dt),max(ko1,ko3));\n" +
                    "    float k2=min(dot(abs(i2-i4),dt),max(ko2,ko4));\n" +
                    "" +
                    "    float w1 = k2; if(ko3<ko1) w1*=ko3/ko1;\n" +
                    "    float w2 = k1; if(ko4<ko2) w2*=ko4/ko2;\n" +
                    "    float w3 = k2; if(ko1<ko3) w3*=ko1/ko3;\n" +
                    "    float w4 = k1; if(ko2<ko4) w4*=ko2/ko4;\n" +
                    "" +
                    "    c=(w1*o1+w2*o2+w3*o3+w4*o4+0.001*c)/(w1+w2+w3+w4+0.001);\n" +
                    "" +
                    "    w1 = k*dot(abs(i1-c)+abs(i3-c),dt)/(0.125*dot(i1+i3,dt)+lum_add);\n" +
                    "    w2 = k*dot(abs(i2-c)+abs(i4-c),dt)/(0.125*dot(i2+i4,dt)+lum_add);\n" +
                    "    w3 = k*dot(abs(s1-c)+abs(s3-c),dt)/(0.125*dot(s1+s3,dt)+lum_add);\n" +
                    "    w4 = k*dot(abs(s2-c)+abs(s4-c),dt)/(0.125*dot(s2+s4,dt)+lum_add);\n" +
                    "" +
                    "    w1 = clamp(w1+mx,min_w,max_w); \n" +
                    "    w2 = clamp(w2+mx,min_w,max_w);\n" +
                    "    w3 = clamp(w3+mx,min_w,max_w); \n" +
                    "    w4 = clamp(w4+mx,min_w,max_w);\n" +
                    "" +
                    "    gl_FragColor.rgb = (w1*(i1+i3)+w2*(i2+i4)+w3*(s1+s3)+w4*(s2+s4)+c)/(2.0*(w1+w2+w3+w4)+1.0);\n" +
                    "    gl_FragColor.a = alpha;\n" +
                    "}"
        )

        // Fragment shader based on "Improved texture interpolation" by Iñigo Quílez
        // Original description: http://www.iquilezles.org/www/articles/texture/texture.htm
        val QuilezShader = ShaderProgramSource(
            TextureFiltering.LINEAR,
            DEFAULT_VERT_SHADER,
            "#ifdef GL_FRAGMENT_PRECISION_HIGH\n" +
                    "precision highp float;\n" +
                    "#else\n" +
                    "precision mediump float;\n" +
                    "#endif\n" +
                    "uniform sampler2D tex;\n" +
                    "varying float alpha;\n" +
                    "varying vec2 uv;\n" +
                    "" +
                    "vec4 getTexel(vec2 p) {\n" +
                    "    vec2 textureSize = vec2(256, 384);\n" +
                    "    p = p * textureSize + vec2(0.5);\n" +
                    "" +
                    "    vec2 i = floor(p);\n" +
                    "    vec2 f = p - i;\n" +
                    "    f = f * f * f * (f * (f * 6.0 - vec2(15.0)) + vec2(10.0));\n" +
                    "    p = i + f;\n" +
                    "" +
                    "    p = (p - vec2(0.5)) / textureSize;\n" +
                    "    return texture2D(tex, p);\n" +
                    "}\n" +
                    "" +
                    "void main() {\n" +
                    "    gl_FragColor = vec4(getTexel(uv).bgr, alpha);\n" +
                    "}"
        )
    }
}
