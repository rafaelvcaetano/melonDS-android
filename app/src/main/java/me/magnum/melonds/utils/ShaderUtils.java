package me.magnum.melonds.utils;

import android.opengl.GLES20;

public class ShaderUtils {
	public static final String DEFAULT_VERT_SHADER =
			"uniform mat4 MVP;\n" +
            "attribute vec2 vUV;\n" +
            "attribute vec2 vPos;\n" +
            "varying vec2 uv;\n" +
            "void main()\n" +
            "{\n" +
            "    gl_Position = MVP * vec4(vPos, 0.0, 1.0);\n" +
            "    uv = vUV;\n" +
            "}\n";

	public static final String DEFAULT_FRAG_SHADER =
			"uniform sampler2D tex;\n" +
            "varying vec2 uv;\n" +
            "void main()\n" +
            "{\n" +
            "    vec4 color = texture2D(tex, uv);\n" +
			"    //float color = texture2D(tex, uv).a;\n" +
            "    gl_FragColor = vec4(color.bgr, 1);\n" +
            "}\n";

	private enum ShaderType {
		VERTEX(GLES20.GL_VERTEX_SHADER),
		FRAGMENT(GLES20.GL_FRAGMENT_SHADER);

		private int glType;

		ShaderType(int glType) {
			this.glType = glType;
		}

		public int getGLType() {
			return this.glType;
		}
	}

	public static int createDefaultShaderProgram() {
		return createShaderProgram(DEFAULT_VERT_SHADER, DEFAULT_FRAG_SHADER);
	}

	public static int createShaderProgram(String vertexShader, String fragmentShader) {
		int program = GLES20.glCreateProgram();
		GLES20.glAttachShader(program, createShader(ShaderType.VERTEX, vertexShader));
		GLES20.glAttachShader(program, createShader(ShaderType.FRAGMENT, fragmentShader));
		GLES20.glLinkProgram(program);

		int[] result = new int[1];
		GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, result, 0);
		if (result[0] == GLES20.GL_FALSE)
			System.err.println(GLES20.glGetProgramInfoLog(program));

		return program;
	}

	private static int createShader(ShaderType shaderType, String code) {
		int shader = GLES20.glCreateShader(shaderType.getGLType());
		GLES20.glShaderSource(shader, code);
		GLES20.glCompileShader(shader);

		int[] result = new int[1];
		GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, result, 0);
		if (result[0] == GLES20.GL_FALSE)
			System.err.println(GLES20.glGetShaderInfoLog(shader));

		return shader;
	}
}
