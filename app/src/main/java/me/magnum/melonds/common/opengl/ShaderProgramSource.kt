package me.magnum.melonds.common.opengl

class ShaderProgramSource private constructor(val textureFiltering: TextureFiltering, val vertexShaderSource: String, val fragmentShaderSource: String) {
    enum class TextureFiltering {
        NEAREST,
        LINEAR
    }

    companion object {
        private const val TEXTURE_WIDTH = 256
        private const val TEXTURE_HEIGHT = 192 * 2 + 2

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
                    "    omega = 3.141592654 * 2.0 * vec2($TEXTURE_WIDTH, $TEXTURE_HEIGHT);\n" +
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
                    "vec2 inputSize = vec2($TEXTURE_WIDTH, $TEXTURE_HEIGHT);\n" + // What is this?
                    "vec2 outputSize = vec2($TEXTURE_WIDTH, $TEXTURE_HEIGHT);\n" + // What is this?
                    "" +
                    "void main()\n" +
                    "{\n" +
                    "    gl_Position = vec4(vPos, 0.0, 1.0);\n" +
                    "    uv = vUV;\n" +
                    "    vec2 textureSize = vec2($TEXTURE_WIDTH, $TEXTURE_HEIGHT);\n" +
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
                    "    vec2 ps = 1.0 / vec2($TEXTURE_WIDTH, $TEXTURE_HEIGHT);\n" +
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
                    "    vec2 fp = fract(uv[0] * vec2($TEXTURE_WIDTH, $TEXTURE_HEIGHT));\n" +
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
                    "    gl_FragColor.a = alpha;\n" +
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
                    "    vec2 dg1 = 0.5 / vec2($TEXTURE_WIDTH, $TEXTURE_HEIGHT);\n" +
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
                    "    vec2 dg1 = 0.5 / vec2($TEXTURE_WIDTH, $TEXTURE_HEIGHT);\n" +
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
                    "    vec2 textureSize = vec2($TEXTURE_WIDTH, $TEXTURE_HEIGHT);\n" +
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