package me.magnum.melonds.renderer;

import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.util.Log;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import me.magnum.melonds.utils.ShaderUtils;

public class DSRenderer implements GLSurfaceView.Renderer {
	private static final String TAG = "DSRenderer";

	private static final int SCREEN_WIDTH = 256;
	private static final int SCREEN_HEIGHT = 384;

	private RendererListener rendererListener;

	private int mainTexture;
	private int program;

	private int l_MVP;
	private int l_uv;
	private int l_pos;
	private int l_tex;

	private float[] mvpMatrix;
	private FloatBuffer posBuffer;
	private FloatBuffer uvBuffer;
	private ByteBuffer texBuffer;

	private float width;
	private float height;
	private float bottom;

	public void setRendererListener(RendererListener listener) {
		this.rendererListener = listener;
	}

	@Override
	public void onSurfaceCreated(GL10 gl, EGLConfig config) {
		if (this.rendererListener == null) {
			Log.w(TAG, "No frame buffer updater specified");
			return;
		}

		GLES20.glClearColor(0f, 0f, 0f, 1f);
		GLES20.glDisable(GLES20.GL_CULL_FACE);

		int[] texture = new int[1];
		GLES20.glGenTextures(1, texture, 0);
		this.mainTexture = texture[0];
		GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, this.mainTexture);
		GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
		GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
		GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
		GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);

		this.program = ShaderUtils.createDefaultShaderProgram();
		GLES20.glUseProgram(this.program);

		this.l_MVP = GLES20.glGetUniformLocation(this.program, "MVP");
		this.l_uv = GLES20.glGetAttribLocation(this.program, "vUV");
		this.l_pos = GLES20.glGetAttribLocation(this.program, "vPos");
		this.l_tex = GLES20.glGetUniformLocation(this.program, "tex");

		GLES20.glEnableVertexAttribArray(this.l_MVP);
		GLES20.glEnableVertexAttribArray(this.l_uv);
		GLES20.glEnableVertexAttribArray(this.l_pos);
		GLES20.glEnableVertexAttribArray(this.l_tex);

		this.mvpMatrix = new float[16];
		float[] projectionMatrix = new float[16];
		float[] viewMatrix = new float[16];

		Matrix.orthoM(projectionMatrix, 0, -1, 1, -1, 1, -1, 10);
		Matrix.setLookAtM(viewMatrix, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0);
		Matrix.multiplyMM(this.mvpMatrix, 0, projectionMatrix, 0, viewMatrix, 0);

		// Invert UVs on the Y axis since the texture data should start at the bottom left corner
		this.uvBuffer = ByteBuffer.allocateDirect(2 * 4 * 4)
				.order(ByteOrder.nativeOrder())
				.asFloatBuffer()
				.put(new float[] {0, 1, 1, 1, 0, 0, 1, 0});
		this.texBuffer = ByteBuffer.allocateDirect(SCREEN_WIDTH * SCREEN_HEIGHT * 4)
				.order(ByteOrder.nativeOrder());
	}

	@Override
	public void onSurfaceChanged(GL10 gl, int width, int height) {
		this.width = width;
		this.height = height;

		GLES20.glViewport(0, 0, width, height);

		float dsAspectRatio = (192 * 2) / 256f;
		float surfaceTargetHeight = width * dsAspectRatio;
		float relativeTargetHeight = surfaceTargetHeight / height;

		float dsBottom = 1 - relativeTargetHeight * 2;

		this.posBuffer = ByteBuffer.allocateDirect(2 * 4 * 4)
				.order(ByteOrder.nativeOrder())
				.asFloatBuffer()
				.put(new float[] {-1, dsBottom, 1, dsBottom, -1, 1, 1, 1});

		this.bottom = height - surfaceTargetHeight;
		if (this.rendererListener != null)
			this.rendererListener.onRendererSizeChanged(width, height);
	}

	@Override
	public void onDrawFrame(GL10 gl) {
		this.rendererListener.updateFrameBuffer(this.texBuffer);

		this.posBuffer.position(0);
		this.uvBuffer.position(0);
		this.texBuffer.position(0);

		GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);

		GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
		GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, this.mainTexture);
		GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, SCREEN_WIDTH, SCREEN_HEIGHT, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, this.texBuffer);

		GLES20.glUniformMatrix4fv(this.l_MVP, 1, false, this.mvpMatrix, 0);
		GLES20.glVertexAttribPointer(this.l_pos, 4, GLES20.GL_FLOAT, false, 2 * 4, this.posBuffer);
		GLES20.glVertexAttribPointer(this.l_uv, 4, GLES20.GL_FLOAT, false, 2 * 4, this.uvBuffer);
		GLES20.glUniform1i(this.l_tex, 0);

		GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
	}

	public float getWidth() {
		return this.width;
	}

	public float getHeight() {
		return this.height;
	}

	public float getBottom() {
		return this.bottom;
	}

	public interface RendererListener {
		void onRendererSizeChanged(int width, int height);
		void updateFrameBuffer(ByteBuffer dst);
	}
}
