package tv.chushou.record.openglesdemo;

import android.opengl.GLES20;
import android.text.TextUtils;
import android.util.Log;

/**
 * Created by zhusong on 18/3/26.
 */

public class ShaderHelper {
    private static final String TAG = "ShaderHelper";

    /**
     * Load Vertex&Fragment Shader
     * @param type
     * @param shaderCode
     * @return
     */
    public static int loadShader(int type, String shaderCode) {
        if (TextUtils.isEmpty(shaderCode)) {
            return 0;
        }
        int shader = GLES20.glCreateShader(type);
        if (shader == 0) {
            Log.w(TAG, "Create Shader Failure");
            return 0;
        }
        GLES20.glShaderSource(shader, shaderCode);
        GLES20.glCompileShader(shader);
        final int[] compileStatus = new int[1];
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compileStatus, 0);
        if (compileStatus[0] == 0) {
            GLES20.glDeleteShader(shader);
            Log.w(TAG, "Compile Shader Failure:\n" + GLES20.glGetShaderInfoLog(shader));
            return 0;
        }
        return shader;
    }

    public static int loadVertexShader(String shaderCode) {
        return loadShader(GLES20.GL_VERTEX_SHADER, shaderCode);
    }

    public static int loadFragmentShader(String shaderCode) {
        return loadShader(GLES20.GL_FRAGMENT_SHADER, shaderCode);
    }

    /**
     * Link Vertex&Fragment Shader To Program
     * @param vertexShader
     * @param fragmentShader
     * @return
     */
    public static int linkProgram(int vertexShader, int fragmentShader) {
        int program = GLES20.glCreateProgram();
        if (program == 0) {
            Log.w(TAG, "Create Program Failure");
            return 0;
        }

        GLES20.glAttachShader(program, vertexShader);
        GLES20.glAttachShader(program, fragmentShader);
        GLES20.glLinkProgram(program);
        final int[] linkStatus = new int[1];
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linkStatus, 0);
        if (linkStatus[0] == 0) {
            GLES20.glDeleteProgram(program);
            Log.w(TAG, "Link Program Failure");
            return 0;
        }

        return program;
    }

    public static int linkProgram(String vertexShader, String fragmentShader) {
        int vertex = loadVertexShader(vertexShader);
        if (vertex == 0) {
            return 0;
        }
        int fragment = loadFragmentShader(fragmentShader);
        if (fragment == 0) {
            return 0;
        }
        return linkProgram(vertex, fragment);
    }


    public static boolean validateProgram(int program) {
        GLES20.glValidateProgram(program);
        final int[] validateStatus = new int[1];
        GLES20.glGetProgramiv(program, GLES20.GL_VALIDATE_STATUS, validateStatus, 0);

        return validateStatus[0] != 0;
    }
}
