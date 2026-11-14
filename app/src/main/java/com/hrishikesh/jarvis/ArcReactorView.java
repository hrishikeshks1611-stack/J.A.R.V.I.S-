package com.hrishikesh.jarvis;

import android.content.Context;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/** OpenGL ES 2.0 animated three-dimensional ARC reactor made from lit torus and core meshes. */
public final class ArcReactorView extends GLSurfaceView {
    public ArcReactorView(Context context) {
        super(context);
        setEGLContextClientVersion(2);
        setRenderer(new ReactorRenderer());
        setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);
        setPreserveEGLContextOnPause(true);
    }

    private static final class ReactorRenderer implements Renderer {
        private static final String VERTEX =
                "uniform mat4 uMvp; uniform mat4 uModel; attribute vec3 aPosition; attribute vec3 aNormal; " +
                "varying float vLight; varying float vRim; void main(){ gl_Position=uMvp*vec4(aPosition,1.0); " +
                "vec3 n=normalize(mat3(uModel)*aNormal); vec3 l=normalize(vec3(-0.35,0.55,0.9)); " +
                "vLight=max(dot(n,l),0.0); vec3 v=normalize(vec3(0.0,0.0,1.0)); " +
                "vRim=pow(1.0-max(dot(n,v),0.0),2.2); }";
        private static final String FRAGMENT =
                "precision mediump float; uniform vec4 uColor; varying float vLight; varying float vRim; " +
                "void main(){ vec3 metal=uColor.rgb*(0.24+0.72*vLight); " +
                "vec3 edge=uColor.rgb*(0.12+0.34*vRim); " +
                "gl_FragColor=vec4(metal+edge,uColor.a); }";
        private int program, mvpLocation, modelLocation, colorLocation, positionLocation, normalLocation;
        private Mesh outer, middle, inner, core;
        private final float[] projection = new float[16];
        private final float[] view = new float[16];
        private final float[] model = new float[16];
        private final float[] pv = new float[16];
        private final float[] mvp = new float[16];
        private long started;

        @Override public void onSurfaceCreated(GL10 gl, EGLConfig config) {
            GLES20.glClearColor(0.008f, 0.025f, 0.06f, 1f);
            GLES20.glEnable(GLES20.GL_DEPTH_TEST);
            GLES20.glEnable(GLES20.GL_CULL_FACE);
            program = buildProgram(VERTEX, FRAGMENT);
            mvpLocation = GLES20.glGetUniformLocation(program, "uMvp");
            modelLocation = GLES20.glGetUniformLocation(program, "uModel");
            colorLocation = GLES20.glGetUniformLocation(program, "uColor");
            positionLocation = GLES20.glGetAttribLocation(program, "aPosition");
            normalLocation = GLES20.glGetAttribLocation(program, "aNormal");
            outer = Mesh.torus(.88f, .045f, 128, 12);
            middle = Mesh.torus(.67f, .035f, 112, 10);
            inner = Mesh.torus(.46f, .026f, 96, 10);
            core = Mesh.sphere(.19f, 40, 32);
            started = System.nanoTime();
        }

        @Override public void onSurfaceChanged(GL10 gl, int width, int height) {
            GLES20.glViewport(0, 0, width, height);
            float aspect = Math.max(1f, (float) width / Math.max(1, height));
            Matrix.perspectiveM(projection, 0, 42f, aspect, .1f, 20f);
            Matrix.setLookAtM(view, 0, 0f, 0f, 3.4f, 0f, 0f, 0f, 0f, 1f, 0f);
            Matrix.multiplyMM(pv, 0, projection, 0, view, 0);
        }

        @Override public void onDrawFrame(GL10 gl) {
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);
            GLES20.glUseProgram(program);
            float seconds = (System.nanoTime() - started) / 1_000_000_000f;
            Matrix.setIdentityM(model, 0);
            Matrix.rotateM(model, 0, seconds * 22f, 0f, 1f, 0f);
            Matrix.rotateM(model, 0, 22f, 1f, 0f, 0f);
            draw(outer, model, .22f, .43f, .53f, 1f);

            Matrix.setIdentityM(model, 0);
            Matrix.rotateM(model, 0, -seconds * 34f, 1f, .3f, .2f);
            Matrix.rotateM(model, 0, 67f, 0f, 1f, 0f);
            draw(middle, model, .29f, .48f, .51f, 1f);

            Matrix.setIdentityM(model, 0);
            Matrix.rotateM(model, 0, seconds * 48f, .2f, 1f, .4f);
            Matrix.rotateM(model, 0, -48f, 1f, 0f, 0f);
            draw(inner, model, .42f, .67f, .72f, 1f);

            Matrix.setIdentityM(model, 0);
            Matrix.rotateM(model, 0, seconds * 12f, 0f, 1f, 0f);
            draw(core, model, .12f, .34f, .43f, 1f);
        }

        private void draw(Mesh mesh, float[] transform, float r, float g, float b, float a) {
            Matrix.multiplyMM(mvp, 0, pv, 0, transform, 0);
            GLES20.glUniformMatrix4fv(mvpLocation, 1, false, mvp, 0);
            GLES20.glUniformMatrix4fv(modelLocation, 1, false, transform, 0);
            GLES20.glUniform4f(colorLocation, r, g, b, a);
            GLES20.glEnableVertexAttribArray(positionLocation);
            GLES20.glEnableVertexAttribArray(normalLocation);
            GLES20.glVertexAttribPointer(positionLocation, 3, GLES20.GL_FLOAT, false, 0, mesh.positions);
            GLES20.glVertexAttribPointer(normalLocation, 3, GLES20.GL_FLOAT, false, 0, mesh.normals);
            GLES20.glDrawElements(GLES20.GL_TRIANGLES, mesh.indexCount, GLES20.GL_UNSIGNED_SHORT, mesh.indices);
            GLES20.glDisableVertexAttribArray(positionLocation);
            GLES20.glDisableVertexAttribArray(normalLocation);
        }

        private int buildProgram(String vertex, String fragment) {
            int vs = compile(GLES20.GL_VERTEX_SHADER, vertex);
            int fs = compile(GLES20.GL_FRAGMENT_SHADER, fragment);
            int p = GLES20.glCreateProgram();
            GLES20.glAttachShader(p, vs); GLES20.glAttachShader(p, fs); GLES20.glLinkProgram(p);
            int[] linked = new int[1]; GLES20.glGetProgramiv(p, GLES20.GL_LINK_STATUS, linked, 0);
            if (linked[0] == 0) throw new IllegalStateException(GLES20.glGetProgramInfoLog(p));
            GLES20.glDeleteShader(vs); GLES20.glDeleteShader(fs);
            return p;
        }

        private int compile(int type, String source) {
            int shader = GLES20.glCreateShader(type);
            GLES20.glShaderSource(shader, source); GLES20.glCompileShader(shader);
            int[] compiled = new int[1]; GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0);
            if (compiled[0] == 0) throw new IllegalStateException(GLES20.glGetShaderInfoLog(shader));
            return shader;
        }
    }

    private static final class Mesh {
        final FloatBuffer positions, normals;
        final ShortBuffer indices;
        final int indexCount;
        Mesh(float[] p, float[] n, short[] ix) {
            positions = floats(p); normals = floats(n); indices = shorts(ix); indexCount = ix.length;
        }
        static FloatBuffer floats(float[] values) {
            FloatBuffer b = ByteBuffer.allocateDirect(values.length * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
            b.put(values).position(0); return b;
        }
        static ShortBuffer shorts(short[] values) {
            ShortBuffer b = ByteBuffer.allocateDirect(values.length * 2).order(ByteOrder.nativeOrder()).asShortBuffer();
            b.put(values).position(0); return b;
        }
        static Mesh torus(float major, float minor, int majorSteps, int minorSteps) {
            int rows = majorSteps + 1, cols = minorSteps + 1;
            float[] p = new float[rows * cols * 3], n = new float[p.length];
            short[] ix = new short[majorSteps * minorSteps * 6];
            int v = 0;
            for (int i=0;i<=majorSteps;i++) {
                double u=2*Math.PI*i/majorSteps;
                for (int j=0;j<=minorSteps;j++) {
                    double w=2*Math.PI*j/minorSteps;
                    float nx=(float)(Math.cos(w)*Math.cos(u));
                    float ny=(float)(Math.cos(w)*Math.sin(u));
                    float nz=(float)Math.sin(w);
                    p[v]=(float)((major+minor*Math.cos(w))*Math.cos(u));
                    n[v++]=nx;
                    p[v]=(float)((major+minor*Math.cos(w))*Math.sin(u));
                    n[v++]=ny;
                    p[v]=(float)(minor*Math.sin(w));
                    n[v++]=nz;
                }
            }
            int k=0;
            for (int i=0;i<majorSteps;i++) for (int j=0;j<minorSteps;j++) {
                int a=i*cols+j, b=a+cols;
                ix[k++]=(short)a; ix[k++]=(short)b; ix[k++]=(short)(a+1);
                ix[k++]=(short)(a+1); ix[k++]=(short)b; ix[k++]=(short)(b+1);
            }
            return new Mesh(p,n,ix);
        }
        static Mesh sphere(float radius, int stacks, int slices) {
            int rows=stacks+1, cols=slices+1;
            float[] p=new float[rows*cols*3], n=new float[p.length];
            short[] ix=new short[stacks*slices*6];
            int v=0;
            for(int i=0;i<=stacks;i++) {
                double phi=Math.PI*i/stacks;
                for(int j=0;j<=slices;j++) {
                    double theta=2*Math.PI*j/slices;
                    float x=(float)(Math.sin(phi)*Math.cos(theta));
                    float y=(float)(Math.sin(phi)*Math.sin(theta));
                    float z=(float)Math.cos(phi);
                    p[v]=radius*x; n[v++]=x;
                    p[v]=radius*y; n[v++]=y;
                    p[v]=radius*z; n[v++]=z;
                }
            }
            int k=0;
            for(int i=0;i<stacks;i++) for(int j=0;j<slices;j++) {
                int a=i*cols+j, b=a+cols;
                ix[k++]=(short)a; ix[k++]=(short)b; ix[k++]=(short)(a+1);
                ix[k++]=(short)(a+1); ix[k++]=(short)b; ix[k++]=(short)(b+1);
            }
            return new Mesh(p,n,ix);
        }
    }
}
