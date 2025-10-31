# Melon Shader (`.msha`) Files

 
Custom video filters in melonDS for Android are described with simple text files that use the `.msha` ("melon shader") extension. Each file packages the information that the emulator needs to compile a pair of OpenGL ES shaders, specify how to feed geometry into them, and choose the texture filtering mode. The examples that ship with the app live in this folder; you can copy and tweak them to build your own effects.
 
## File structure overview
 
An `.msha` file is plain UTF-8 text with three kinds of content:
 
1. **Header properties** – `key: value` or `key=value` pairs that appear before any shader blocks.
2. **Shader sections** – labeled blocks that provide vertex and fragment GLSL source code.
3. **Comments / empty lines** – ignored by the loader. Start a line with `#` to leave notes.
 
The loader parses the file top to bottom and recognises the following layout:
 
```text
name: Friendly Name
textureFiltering: NEAREST
# Optional binding overrides…
 
[vertex]
// vertex shader source
[/vertex]
 
[fragment]
// fragment shader source
[/fragment]
```
 
* Header keys are case-insensitive and may use either camel-case (`textureFiltering`) or snake-case (`texture_filtering`).
* Lines that are blank or begin with `#` are skipped.
* The `[vertex]` and `[fragment]` markers are mandatory and must be closed by their matching `[/vertex]` / `[/fragment]` tags. Everything between the markers is copied verbatim into the compiled shader.
 
## Supported header keys
 
| Key (aliases) | Description | Default |
| --- | --- | --- |
| `name` | Human-readable label shown in the UI. | Filename when absent. |
| `textureFiltering`, `texture_filtering` | Selects the OpenGL texture filter. Accepted values: `NEAREST`, `LINEAR`. | `NEAREST` |
| `bindings.attribUv`, `attribUv` | Attribute name that carries texture coordinates. | `vUV` |
| `bindings.attribPos`, `attribPos` | Attribute name that carries clip-space positions. | `vPos` |
| `bindings.attribAlpha`, `attribAlpha` | Attribute name for per-vertex alpha. | `vAlpha` |
| `bindings.uniformTex`, `uniformTex` | Texture sampler for the current frame. | `tex` |
| `bindings.uniformPrevTex`, `uniformPrevTex` | Texture sampler for the previous frame (used by response filters). | `prevTex` |
| `bindings.uniformPrevWeight`, `uniformPrevWeight` | Blend weight for the previous frame. | `responseWeight` |
| `bindings.uniformTexSize`, `uniformTexSize` | Size of the source texture in pixels. | `texSize` |
| `bindings.uniformViewportSize`, `uniformViewportSize` | Size of the viewport in pixels. | `viewportSize` |
| `bindings.uniformScreenUvBounds`, `uniformScreenUvBounds`, `bindings.uniformUvBounds`, `uniformUvBounds` | UV bounds used when a layout renders only part of the screen texture. | `screenUvBounds` |
#### You only need to override bindings if your shader source declares different attribute or uniform names. The defaults match all built-in shaders and are what the renderers expect.
 
## Writing shader sections
 
The GLSL you place inside `[vertex]…[/vertex]` and `[fragment]…[/fragment]` is compiled as-is for OpenGL ES 2.0. You can freely add `precision` qualifiers, helper functions, or `#ifdef` blocks. The only requirements are:
 
* Declare the attributes and uniforms that you referenced (either via the defaults listed above or your own bindings).
* Produce `gl_Position` in the vertex shader and `gl_FragColor` in the fragment shader.
 
### Available uniforms & varyings
 
Depending on the effect you implement, you can rely on the following data streams:
 
* **Attributes** (`vPos`, `vUV`, `vAlpha` by default) – supplied per-vertex. You can pass them through to varyings for use in the fragment shader.
* **Samplers** (`tex`, `prevTex`) – the current and previous frame textures. `prevTex` is handy for motion blur / LCD persistence effects.
* **Scalar uniforms** (`responseWeight`) – how strongly the previous frame should influence the current one.
* **Vector uniforms** (`texSize`, `viewportSize`, `screenUvBounds`) – texture and viewport dimensions, plus the UV sub-rectangle the screen occupies when using custom layouts.
 
If your shader does not need a specific uniform, you can simply omit it from the GLSL. Likewise, if you declare custom names, remember to provide matching binding overrides in the header.
 
## Minimal example: Linear filter
 
The built-in linear filter simply requests linear sampling and outputs the fetched texel with the correct alpha. Its `.msha` file looks like this:
 
```msha
name: Linear Filter
textureFiltering: LINEAR
 
[vertex]
attribute vec2 vUV;
attribute vec2 vPos;
attribute float vAlpha;
varying vec2 uv;
varying float alpha;
void main()
{
gl_Position = vec4(vPos, 0.0, 1.0);
uv = vUV;
alpha = vAlpha;
}
[/vertex]
 
[fragment]
precision mediump float;
uniform sampler2D tex;
varying vec2 uv;
varying float alpha;
void main()
{
vec4 color = texture2D(tex, uv);
gl_FragColor = vec4(color.bgr, alpha);
}
[/fragment]
```
 
You can use this as a starting point: change the `textureFiltering` mode, add uniforms to the fragment shader, or include your own logic before writing to `gl_FragColor`.
 
## Advanced tips
 
* **Response-based effects** – Filters such as `lcd_grid_dslite.msha` use both `tex` and `prevTex` plus `responseWeight` to blend the current and previous frames. This lets you simulate LCD persistence or glow.
* **Layout-aware shaders** – `screenUvBounds` helps identify the actual portion of the texture that a screen uses when custom layouts draw multiple screens into a single surface.
* **Precision qualifiers** – Guard your fragment shader with the `#ifdef GL_FRAGMENT_PRECISION_HIGH` pattern (see `lcd_grid_dslite.msha`) if you rely on high precision on devices that support it.
 
For more complex examples, explore the other preset files in this folder. Every `.msha` file that comes with the project is fully functional and demonstrates different techniques—from simple scanlines to advanced subpixel LCD emulation.

