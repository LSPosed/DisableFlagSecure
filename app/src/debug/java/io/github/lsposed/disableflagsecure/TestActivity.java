package io.github.lsposed.disableflagsecure;

import android.app.Activity;
import android.graphics.Paint;
import android.os.Bundle;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class TestActivity extends Activity implements SurfaceHolder.Callback {
    private TextView textView;
    private SurfaceView surfaceView;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG) {{
        setColor(0xFF00FFFF);
    }};

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);

        var linearLayout = new LinearLayout(this);
        linearLayout.setOrientation(LinearLayout.VERTICAL);
        linearLayout.setBackgroundColor(0xFFFFFF00);
        setContentView(linearLayout, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT));

        textView = new TextView(this);
        linearLayout.addView(textView, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT, 1.0f));

        surfaceView = new SurfaceView(this);
        surfaceView.setZOrderOnTop(true);
        surfaceView.setSecure(true);
        surfaceView.getHolder().addCallback(this);
        linearLayout.addView(surfaceView, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT, 1.0f));
    }


    @Override
    public void surfaceCreated(@NonNull SurfaceHolder holder) {
        var canvas = holder.lockCanvas();
        canvas.drawRect(0, 0, surfaceView.getMeasuredWidth(), surfaceView.getMeasuredHeight(), paint);
        textView.setText("SurfaceView");
        textView.draw(canvas);
        textView.setText("TextView");
        holder.unlockCanvasAndPost(canvas);
    }

    @Override
    public void surfaceChanged(@NonNull SurfaceHolder holder, int format, int width, int height) {

    }

    @Override
    public void surfaceDestroyed(@NonNull SurfaceHolder holder) {

    }
}
