package com.myapp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.view.View;

/** Splash screen drawn on a 360 x 800 design grid and scaled to fit any phone. */
public class SplashView extends View {
    private static final float W = 360f, H = 800f;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final long start = SystemClock.uptimeMillis();

    public SplashView(Context context) {
        super(context);
    }

    @Override protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight();
        paint.setStyle(Paint.Style.FILL);
        paint.setShader(new LinearGradient(0, 0, 0, h,
                new int[]{0xFF070620, 0xFF130A4A, 0xFF1B0B5C, 0xFF08051F},
                new float[]{0f, 0.35f, 0.7f, 1f}, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, paint);
        paint.setShader(null);

        float s = Math.min(w / W, h / H);
        c.save();
        c.translate((w - W * s) / 2f, (h - H * s) / 2f);
        c.scale(s, s);

        glow(c, 180, 225, 170, 0x665B2BFF);
        glow(c, 180, 580, 190, 0x773A3BFF);
        waves(c);
        logo(c);
        text(c, "PrivateCall", 348, 46, 0xFFFFFFFF, true);
        text(c, "Chat  •  Call  •  Video Call", 396, 20, 0xFFEDEBFF, false);
        text(c, "100% Private & Secure", 426, 20, 0xFFEDEBFF, false);
        shield(c);
        text(c, "Your Privacy", 698, 19, 0xFFFFFFFF, false);
        text(c, "Our Priority", 724, 19, 0xFFFFFFFF, false);
        progress(c);
        c.restore();
    }

    private void glow(Canvas c, float x, float y, float r, int color) {
        paint.setShader(new RadialGradient(x, y, r, color, color & 0x00FFFFFF, Shader.TileMode.CLAMP));
        c.drawCircle(x, y, r, paint);
        paint.setShader(null);
    }

    private void waves(Canvas c) {
        paint.setStyle(Paint.Style.STROKE);
        for (int i = 0; i < 14; i++) {
            float d = i * 4f;
            paint.setStrokeWidth(i % 5 == 0 ? 1.8f : 0.8f);
            paint.setColor(i % 2 == 0 ? 0x77A52BFF : 0x554A6BFF);
            Path p = new Path();
            p.moveTo(-40, 560 + d);
            p.cubicTo(70, 520 + d * 2, 120, 690 - d, 200, 625 + d * 0.5f);
            p.cubicTo(270, 570 - d, 320, 640 - d * 2, 400, 545 + d);
            c.drawPath(p, paint);
        }
        paint.setStyle(Paint.Style.FILL);
    }

    private void logo(Canvas c) {
        // White rounded badge with a purple padlock and two signal arcs.
        paint.setColor(0xFFFFFFFF);
        c.drawRoundRect(new RectF(105, 140, 255, 290), 46, 46, paint);
        paint.setShader(new LinearGradient(118, 153, 242, 277, 0xFF7B3CFF, 0xFF4318D8, Shader.TileMode.CLAMP));
        c.drawRoundRect(new RectF(117, 152, 243, 278), 36, 36, paint);
        paint.setShader(null);
        padlock(c, 170, 224, 1.1f, 0xFFFFFFFF, 0xFF5A24E6);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeWidth(6);
        paint.setColor(0xFFFFFFFF);
        c.drawArc(new RectF(180, 180, 214, 214), -80, 70, false, paint);
        c.drawArc(new RectF(167, 167, 227, 227), -80, 70, false, paint);
        paint.setStyle(Paint.Style.FILL);
    }

    private void padlock(Canvas c, float x, float y, float k, int body, int hole) {
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(7 * k);
        paint.setColor(body);
        c.drawArc(new RectF(x - 14 * k, y - 32 * k, x + 14 * k, y - 4 * k), 180, 180, false, paint);
        c.drawLine(x - 14 * k, y - 18 * k, x - 14 * k, y - 6 * k, paint);
        c.drawLine(x + 14 * k, y - 18 * k, x + 14 * k, y - 6 * k, paint);
        paint.setStyle(Paint.Style.FILL);
        c.drawRoundRect(new RectF(x - 24 * k, y - 8 * k, x + 24 * k, y + 30 * k), 8 * k, 8 * k, paint);
        paint.setColor(hole);
        c.drawCircle(x, y + 7 * k, 6 * k, paint);
        c.drawRect(x - 2.5f * k, y + 8 * k, x + 2.5f * k, y + 20 * k, paint);
    }

    private Path shieldPath(float top, float half, float bottom) {
        Path p = new Path();
        p.moveTo(180, top);
        p.cubicTo(180 + half * 0.35f, top + 16, 180 + half * 0.7f, top + 22, 180 + half, top + 24);
        p.cubicTo(180 + half, top + 82, 180 + half * 0.65f, bottom - 28, 180, bottom);
        p.cubicTo(180 - half * 0.65f, bottom - 28, 180 - half, top + 82, 180 - half, top + 24);
        p.cubicTo(180 - half * 0.7f, top + 22, 180 - half * 0.35f, top + 16, 180, top);
        p.close();
        return p;
    }

    private void shield(Canvas c) {
        Path outer = shieldPath(495, 64, 645);
        paint.setShader(new LinearGradient(116, 495, 244, 645, 0xFF2A57E8, 0xFF3A1590, Shader.TileMode.CLAMP));
        c.drawPath(outer, paint);
        paint.setShader(null);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(3);
        paint.setColor(0xFF7FA8FF);
        c.drawPath(outer, paint);
        paint.setStrokeWidth(1.5f);
        paint.setColor(0x889A7BFF);
        c.drawPath(shieldPath(507, 52, 630), paint);
        paint.setStyle(Paint.Style.FILL);
        padlock(c, 180, 570, 1.05f, 0xFFE4E6FF, 0xFF3224A8);
    }

    private void text(Canvas c, String value, float y, float size, int color, boolean bold) {
        paint.setColor(color);
        paint.setTextSize(size);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTypeface(Typeface.create(Typeface.SANS_SERIF, bold ? Typeface.BOLD : Typeface.NORMAL));
        c.drawText(value, 180, y, paint);
    }

    private void progress(Canvas c) {
        float done = Math.min(1f, (SystemClock.uptimeMillis() - start) / 2200f);
        paint.setColor(0xFF2D2470);
        c.drawRoundRect(new RectF(90, 758, 270, 766), 4, 4, paint);
        paint.setColor(0xFFA62BFF);
        c.drawRoundRect(new RectF(90, 758, 90 + 180 * done, 766), 4, 4, paint);
        if (done < 1f) postInvalidateOnAnimation();
    }
}
