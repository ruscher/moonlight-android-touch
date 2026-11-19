package com.limelight.brp.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

import com.limelight.brp.OscLayouts;

import java.util.List;

/**
 * A small picture of a controller layout, drawn from the same data that builds
 * the real controller ({@link OscLayouts}), so the preview cannot drift from it.
 * Sticks and the D-pad are circles, triggers and bumpers rounded rectangles,
 * face buttons small circles. Decorative: the card around it carries the name
 * for TalkBack.
 */
@android.annotation.SuppressLint("ViewConstructor") // built in code only, from a layout
public class OscLayoutPreview extends View {
    private final List<OscLayouts.Spec> specs;
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint frame = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();

    public OscLayoutPreview(Context c, OscLayouts.Layout layout) {
        super(c);
        specs = OscLayouts.specs(layout, false, true);
        fill.setColor(0x33A48BFF);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(BrpUi.dp(c, 1.2f));
        stroke.setColor(BrpUi.ACCENT);
        frame.setColor(0xFF10142A);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int w = MeasureSpec.getSize(widthMeasureSpec);
        // A 20:9 phone screen, the shape people hold while streaming.
        setMeasuredDimension(w, w * 9 / 20);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth(), h = getHeight();
        float radius = BrpUi.dp(getContext(), 8);
        r.set(0, 0, w, h);
        canvas.drawRoundRect(r, radius, radius, frame);
        for (OscLayouts.Spec s : specs) {
            float left = s.leftPx(w, h), top = s.topPx(h);
            r.set(left, top, left + s.widthPx(h), top + s.heightPx(h));
            r.inset(1, 1);
            switch (s.kind) {
                case LEFT_STICK:
                case RIGHT_STICK:
                case DPAD:
                    canvas.drawOval(r, fill);
                    canvas.drawOval(r, stroke);
                    break;
                case BUTTON:
                    if (s.w == s.h) {
                        canvas.drawOval(r, fill);
                        canvas.drawOval(r, stroke);
                        break;
                    }
                    // fall through: wide buttons look like bumpers
                default:
                    float rr = Math.min(r.width(), r.height()) / 3;
                    canvas.drawRoundRect(r, rr, rr, fill);
                    canvas.drawRoundRect(r, rr, rr, stroke);
                    break;
            }
        }
    }
}
