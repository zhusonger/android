package tv.chushou.record.openglesdemo.airhockey;

import android.app.Activity;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.support.annotation.Nullable;

import tv.chushou.record.openglesdemo.R;

/**
 * Created by zhusong on 18/3/26.
 */

public class AirHockeyActivity extends Activity {

    private GLSurfaceView mGlView;
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_airhockey);
        mGlView = (GLSurfaceView) findViewById(R.id.gl_view);
        mGlView.setEGLContextClientVersion(2);
        mGlView.setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);
        mGlView.setRenderer(new AirHockeyRender());
    }
}
