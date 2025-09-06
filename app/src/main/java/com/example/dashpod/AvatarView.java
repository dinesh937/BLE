package com.example.dashpod;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

/**
 * Custom Avatar View that visualizes BNO055 sensor data
 * Shows a 2D stick figure that moves based on sensor orientation
 */
public class AvatarView extends View {

    private Paint bodyPaint;
    private Paint headPaint;
    private Paint jointPaint;
    private Paint backgroundPaint;

    // Avatar proportions and positioning
    private float centerX, centerY;
    private float headRadius = 30f;
    private float torsoLength = 80f;
    private float armLength = 50f;
    private float legLength = 60f;

    // Current sensor data
    private float yaw = 0f;        // Rotation around Y-axis
    private float pitch = 0f;      // Rotation around X-axis
    private float roll = 0f;       // Rotation around Z-axis
    private float qw = 1f, qx = 0f, qy = 0f, qz = 0f;

    // Animation smoothing
    private float targetYaw = 0f, targetPitch = 0f, targetRoll = 0f;
    private static final float SMOOTHING_FACTOR = 0.1f;

    public AvatarView(Context context) {
        super(context);
        init();
    }

    public AvatarView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public AvatarView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        // Initialize paint objects
        bodyPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        bodyPaint.setColor(Color.WHITE);
        bodyPaint.setStrokeWidth(6f);
        bodyPaint.setStyle(Paint.Style.STROKE);
        bodyPaint.setStrokeCap(Paint.Cap.ROUND);

        headPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        headPaint.setColor(Color.CYAN);
        headPaint.setStyle(Paint.Style.FILL);

        jointPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        jointPaint.setColor(Color.YELLOW);
        jointPaint.setStyle(Paint.Style.FILL);

        backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        backgroundPaint.setColor(Color.DKGRAY);
        backgroundPaint.setStyle(Paint.Style.FILL);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        centerX = w / 2f;
        centerY = h / 2f;

        // Scale avatar size based on view dimensions
        float scale = Math.min(w, h) / 300f;
        headRadius *= scale;
        torsoLength *= scale;
        armLength *= scale;
        legLength *= scale;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        // Draw background
        canvas.drawRect(0, 0, getWidth(), getHeight(), backgroundPaint);

        // Smooth the animation
        smoothSensorValues();

        // Draw avatar with current sensor orientation
        drawAvatar(canvas);

        // Draw sensor value display
        drawSensorInfo(canvas);

        // Continue animation
        invalidate();
    }

    private void smoothSensorValues() {
        targetYaw = lerp(targetYaw, yaw, SMOOTHING_FACTOR);
        targetPitch = lerp(targetPitch, pitch, SMOOTHING_FACTOR);
        targetRoll = lerp(targetRoll, roll, SMOOTHING_FACTOR);
    }

    private float lerp(float start, float end, float factor) {
        return start + factor * (end - start);
    }

    private void drawAvatar(Canvas canvas) {
        canvas.save();

        // Apply overall body rotation based on sensor data
        canvas.translate(centerX, centerY);
        canvas.rotate(targetRoll * 0.5f); // Apply roll to whole body

        // Calculate body part positions with sensor influence
        float headX = targetPitch * 0.3f;
        float headY = -torsoLength/2 - headRadius - 10;

        // Draw head with yaw rotation effect
        canvas.save();
        canvas.translate(headX, headY);
        canvas.rotate(targetYaw * 0.8f);

        // Head shape changes based on yaw (perspective effect)
        float headScaleX = 1.0f - Math.abs(targetYaw) / 360f * 0.3f;
        canvas.scale(headScaleX, 1.0f);
        canvas.drawCircle(0, 0, headRadius, headPaint);

        // Draw face direction indicator
        canvas.drawCircle(headRadius * 0.3f, -headRadius * 0.2f, 3, jointPaint); // Right eye
        canvas.drawCircle(headRadius * 0.3f, headRadius * 0.2f, 3, jointPaint);  // Left eye
        canvas.restore();

        // Draw neck
        canvas.drawLine(headX, headY + headRadius, 0, -torsoLength/2, bodyPaint);

        // Draw torso with pitch influence
        float torsoEndY = torsoLength/2 + targetPitch * 0.2f;
        canvas.drawLine(0, -torsoLength/2, 0, torsoEndY, bodyPaint);

        // Draw arms with sensor-based movement
        drawArm(canvas, true, targetPitch, targetRoll, targetYaw);  // Left arm
        drawArm(canvas, false, targetPitch, targetRoll, targetYaw); // Right arm

        // Draw legs with balance compensation
        drawLeg(canvas, true, targetPitch, targetRoll);   // Left leg
        drawLeg(canvas, false, targetPitch, targetRoll);  // Right leg

        canvas.restore();
    }

    private void drawArm(Canvas canvas, boolean isLeft, float pitch, float roll, float yaw) {
        float side = isLeft ? -1f : 1f;
        float shoulderX = side * 20f;
        float shoulderY = -torsoLength/4;

        // Arm movement based on sensor data
        float armAngle = side * (pitch * 0.4f + roll * 0.6f + yaw * 0.2f);
        float upperArmEndX = shoulderX + (float)Math.sin(Math.toRadians(armAngle)) * armLength * 0.6f;
        float upperArmEndY = shoulderY + (float)Math.cos(Math.toRadians(armAngle)) * armLength * 0.6f;

        // Upper arm
        canvas.drawLine(shoulderX, shoulderY, upperArmEndX, upperArmEndY, bodyPaint);

        // Elbow joint
        canvas.drawCircle(upperArmEndX, upperArmEndY, 4, jointPaint);

        // Lower arm (elbow movement)
        float elbowAngle = armAngle + (pitch * 0.3f);
        float lowerArmEndX = upperArmEndX + (float)Math.sin(Math.toRadians(elbowAngle)) * armLength * 0.4f;
        float lowerArmEndY = upperArmEndY + (float)Math.cos(Math.toRadians(elbowAngle)) * armLength * 0.4f;

        canvas.drawLine(upperArmEndX, upperArmEndY, lowerArmEndX, lowerArmEndY, bodyPaint);

        // Hand
        canvas.drawCircle(lowerArmEndX, lowerArmEndY, 6, headPaint);
    }

    private void drawLeg(Canvas canvas, boolean isLeft, float pitch, float roll) {
        float side = isLeft ? -1f : 1f;
        float hipX = side * 10f;
        float hipY = torsoLength/2;

        // Leg movement for balance (opposite to roll)
        float legAngle = -side * roll * 0.4f + pitch * 0.2f;
        float upperLegEndX = hipX + (float)Math.sin(Math.toRadians(legAngle)) * legLength * 0.6f;
        float upperLegEndY = hipY + (float)Math.cos(Math.toRadians(legAngle)) * legLength * 0.6f;

        // Upper leg (thigh)
        canvas.drawLine(hipX, hipY, upperLegEndX, upperLegEndY, bodyPaint);

        // Knee joint
        canvas.drawCircle(upperLegEndX, upperLegEndY, 4, jointPaint);

        // Lower leg (knee bends for balance)
        float kneeAngle = legAngle + Math.abs(roll) * 0.3f;
        float lowerLegEndX = upperLegEndX + (float)Math.sin(Math.toRadians(kneeAngle)) * legLength * 0.4f;
        float lowerLegEndY = upperLegEndY + (float)Math.cos(Math.toRadians(kneeAngle)) * legLength * 0.4f;

        canvas.drawLine(upperLegEndX, upperLegEndY, lowerLegEndX, lowerLegEndY, bodyPaint);

        // Foot
        RectF footRect = new RectF(lowerLegEndX - 8, lowerLegEndY - 3, lowerLegEndX + 8, lowerLegEndY + 3);
        canvas.drawRoundRect(footRect, 3, 3, headPaint);
    }

    private void drawSensorInfo(Canvas canvas) {
        Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(12f);

        // Display sensor values
        String yawText = String.format("Yaw: %.1f°", yaw);
        String pitchText = String.format("Pitch: %.1f°", pitch);
        String rollText = String.format("Roll: %.1f°", roll);
        String quatText = String.format("Q: W=%.2f X=%.2f Y=%.2f Z=%.2f", qw, qx, qy, qz);

        canvas.drawText(yawText, 10, 20, textPaint);
        canvas.drawText(pitchText, 10, 35, textPaint);
        canvas.drawText(rollText, 10, 50, textPaint);
        canvas.drawText(quatText, 10, 65, textPaint);

        // Status indicator
        Paint statusPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        statusPaint.setColor(Color.GREEN);
        canvas.drawCircle(getWidth() - 20, 20, 8, statusPaint);
        textPaint.setTextSize(10f);
        canvas.drawText("LIVE", getWidth() - 50, 25, textPaint);
    }

    /**
     * Update avatar with new sensor data
     */
    public void updateSensorData(float yaw, float pitch, float roll, float qw, float qx, float qy, float qz) {
        this.yaw = yaw;
        this.pitch = pitch;
        this.roll = roll;
        this.qw = qw;
        this.qx = qx;
        this.qy = qy;
        this.qz = qz;

        // Trigger redraw
        invalidate();
    }

    /**
     * Reset avatar to neutral position
     */
    public void resetAvatar() {
        this.yaw = 0f;
        this.pitch = 0f;
        this.roll = 0f;
        this.qw = 1f;
        this.qx = 0f;
        this.qy = 0f;
        this.qz = 0f;
        this.targetYaw = 0f;
        this.targetPitch = 0f;
        this.targetRoll = 0f;

        invalidate();
    }
}