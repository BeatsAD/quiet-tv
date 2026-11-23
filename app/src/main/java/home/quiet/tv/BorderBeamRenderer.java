package home.quiet.tv;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;

import java.util.Arrays;

/**
 * Android port of the orbit mode in ismnoiet's BorderBeam.metal (MIT).
 * Source: https://github.com/ismnoiet/border-beam-ios/blob/a11d1b938b1cc8efad02e942f7590a0156bbd7da/Sources/BorderBeam/Shaders/BorderBeam.metal
 * Copyright (c) 2026 ismnoiet. See assets/licenses/border-beam-ios-MIT.txt.
 */
final class BorderBeamRenderer {
    private static final float ARC = .27f;
    private static final float GLOW_REACH = 10f;
    private static final int BLOB_COUNT = 24;
    private static final float QUARTER_TURN = (float) Math.PI * .5f;
    private static final float MIN_WEIGHT = .0001f;
    private static final int PROFILE_STEPS = 1024;
    private static final int[] PALETTE = {0xffc92cff, 0xffa13cff, 0xff526dff, 0xff1fdbff, 0xffd0ffff};

    private final int width, height;
    private final float pointsPerPixel, halfWidth, halfHeight, radius, innerX, innerY;
    private final float cornerArc, perimeter, sigma, blurScale;
    private final Bitmap bitmap;
    private final int[] pixels;
    private final float[] perimeterAtPixel, edgeWeight, weights, reds, greens, blues;
    private final float[] blurX, blurY;
    private final float[] crispProfile = new float[PROFILE_STEPS + 1];
    private final float[][] colors = new float[PROFILE_STEPS + 1][];
    private final float[][] blobColor = new float[BLOB_COUNT][];
    private final float[] blobWeight = new float[BLOB_COUNT];
    private float centerX, centerY;

    BorderBeamRenderer(Rect bounds, float unit, float cornerRadius) {
        // Render at display resolution up to two pixels per design point.
        float rasterScale = Math.min(1f, 2f / unit);
        width = Math.max(1, Math.round(bounds.width() * rasterScale));
        height = Math.max(1, Math.round(bounds.height() * rasterScale));
        pointsPerPixel = 1f / (unit * rasterScale);
        halfWidth = width * pointsPerPixel * .5f;
        halfHeight = height * pointsPerPixel * .5f;
        radius = Math.min(cornerRadius, Math.min(halfWidth, halfHeight));
        innerX = halfWidth - radius;
        innerY = halfHeight - radius;
        cornerArc = QUARTER_TURN * radius;
        perimeter = 4f * (innerX + innerY + cornerArc);
        float spacing = ARC * perimeter / BLOB_COUNT;
        sigma = Math.max(GLOW_REACH * .7f, .8f * spacing);
        blurScale = .55f * spacing / (sigma * 2.50663f);

        bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        int count = width * height;
        pixels = new int[count];
        perimeterAtPixel = new float[count];
        edgeWeight = new float[count];
        weights = new float[count];
        reds = new float[count];
        greens = new float[count];
        blues = new float[count];
        blurX = new float[width];
        blurY = new float[height];

        for (int y = 0; y < height; y++) {
            float py = (y + .5f) * pointsPerPixel - halfHeight;
            for (int x = 0; x < width; x++) {
                float px = (x + .5f) * pointsPerPixel - halfWidth;
                float dx = Math.abs(px) - innerX;
                float dy = Math.abs(py) - innerY;
                float distance = radius - (float) Math.hypot(Math.max(dx, 0f), Math.max(dy, 0f))
                        - Math.min(Math.max(dx, dy), 0f);
                int i = y * width + x;
                if (distance < 0f || distance > 3f * sigma) {
                    perimeterAtPixel[i] = -1f;
                    continue;
                }
                perimeterAtPixel[i] = perimeterParam(px, py, dx, dy);
                float crisp = 1f - smoothStep((distance - .5f) / 1.75f);
                float halo = .6f * (float) Math.exp(-distance / Math.max(GLOW_REACH * .1f, .6f));
                edgeWeight[i] = crisp + halo;
            }
        }

        for (int i = 0; i <= PROFILE_STEPS; i++) {
            float t = (float) i / PROFILE_STEPS;
            crispProfile[i] = bell(t, 1.2f);
            colors[i] = samplePalette(t);
        }
        for (int i = 0; i < BLOB_COUNT; i++) {
            float t = (i + .5f) / BLOB_COUNT;
            blobColor[i] = samplePalette(t);
            blobWeight[i] = blurScale * bell(t, 1.6f);
        }
    }

    void draw(Canvas canvas, Rect bounds, Paint paint, float phase) {
        Arrays.fill(weights, 0f);
        Arrays.fill(reds, 0f);
        Arrays.fill(greens, 0f);
        Arrays.fill(blues, 0f);
        for (int i = 0; i < BLOB_COUNT; i++) {
            float t = (i + .5f) / BLOB_COUNT;
            boundaryPoint(wrap(phase + 1f - ARC + t * ARC) * perimeter);
            addBlob(blobColor[i], blobWeight[i]);
        }
        for (int i = 0; i < pixels.length; i++) {
            if (perimeterAtPixel[i] < 0f) {
                pixels[i] = 0;
                continue;
            }
            float t = (wrap(perimeterAtPixel[i] - phase) - (1f - ARC)) / ARC;
            if (t >= 0f) {
                int index = Math.min(PROFILE_STEPS, Math.round(t * PROFILE_STEPS));
                add(i, colors[index], edgeWeight[i] * crispProfile[index]);
            }
            float weight = weights[i];
            if (weight <= MIN_WEIGHT) {
                pixels[i] = 0;
                continue;
            }
            // Bitmap.setPixels accepts straight RGB and premultiplies it by the stored alpha.
            int alpha = Math.round(255f * Math.min(1f, weight));
            int red = Math.round(reds[i] / weight);
            int green = Math.round(greens[i] / weight);
            int blue = Math.round(blues[i] / weight);
            pixels[i] = (alpha << 24) | (red << 16) | (green << 8) | blue;
        }
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height);
        canvas.drawBitmap(bitmap, null, bounds, paint);
    }

    private void addBlob(float[] color, float weight) {
        // The upstream shader ignores Gaussian values below .003, beyond 3.41 sigma.
        float reach = 3.5f * sigma / pointsPerPixel;
        float x = (centerX + halfWidth) / pointsPerPixel - .5f;
        float y = (centerY + halfHeight) / pointsPerPixel - .5f;
        int left = Math.max(0, (int) Math.ceil(x - reach));
        int right = Math.min(width - 1, (int) Math.floor(x + reach));
        int top = Math.max(0, (int) Math.ceil(y - reach));
        int bottom = Math.min(height - 1, (int) Math.floor(y + reach));
        float scale = pointsPerPixel / sigma;
        for (int col = left; col <= right; col++) {
            float delta = (col - x) * scale;
            blurX[col] = (float) Math.exp(-.5f * delta * delta);
        }
        for (int row = top; row <= bottom; row++) {
            float delta = (row - y) * scale;
            blurY[row] = (float) Math.exp(-.5f * delta * delta);
        }
        for (int row = top; row <= bottom; row++) {
            int i = row * width + left;
            for (int col = left; col <= right; col++, i++) {
                float gaussian = blurX[col] * blurY[row];
                if (gaussian >= .003f && perimeterAtPixel[i] >= 0f) add(i, color, gaussian * weight);
            }
        }
    }

    private void add(int i, float[] color, float weight) {
        weights[i] += weight;
        reds[i] += color[0] * weight;
        greens[i] += color[1] * weight;
        blues[i] += color[2] * weight;
    }

    // The perimeter starts at the midpoint of the right edge and runs clockwise.
    private float perimeterParam(float x, float y, float dx, float dy) {
        float bottomLeft = innerY + cornerArc + 2f * innerX;
        float left = bottomLeft + cornerArc;
        float topLeft = left + 2f * innerY;
        float topRight = topLeft + cornerArc + 2f * innerX;
        float distance;
        if (dx > 0f && dy > 0f) {
            float angle = (float) Math.atan2(dy, dx);
            if (x >= 0f && y >= 0f) distance = innerY + radius * angle;
            else if (x < 0f && y >= 0f) distance = bottomLeft + radius * (QUARTER_TURN - angle);
            else if (x < 0f) distance = topLeft + radius * angle;
            else distance = topRight + radius * (QUARTER_TURN - angle);
        } else if (dy < dx) {
            distance = x >= 0f ? y : left + innerY - y;
        } else {
            distance = y >= 0f ? innerY + cornerArc + innerX - x : topLeft + cornerArc + x + innerX;
        }
        return wrap(distance / perimeter);
    }

    private void boundaryPoint(float distance) {
        if (distance < innerY) { centerX = halfWidth; centerY = distance; return; }
        distance -= innerY;
        if (distance < cornerArc) { arcPoint(innerX, innerY, distance / radius); return; }
        distance -= cornerArc;
        if (distance < 2f * innerX) { centerX = innerX - distance; centerY = halfHeight; return; }
        distance -= 2f * innerX;
        if (distance < cornerArc) { arcPoint(-innerX, innerY, QUARTER_TURN + distance / radius); return; }
        distance -= cornerArc;
        if (distance < 2f * innerY) { centerX = -halfWidth; centerY = innerY - distance; return; }
        distance -= 2f * innerY;
        if (distance < cornerArc) { arcPoint(-innerX, -innerY, 2f * QUARTER_TURN + distance / radius); return; }
        distance -= cornerArc;
        if (distance < 2f * innerX) { centerX = -innerX + distance; centerY = -halfHeight; return; }
        distance -= 2f * innerX;
        if (distance < cornerArc) { arcPoint(innerX, -innerY, 3f * QUARTER_TURN + distance / radius); return; }
        centerX = halfWidth;
        centerY = -innerY + distance - cornerArc;
    }

    private void arcPoint(float x, float y, float angle) {
        centerX = x + radius * (float) Math.cos(angle);
        centerY = y + radius * (float) Math.sin(angle);
    }

    private static float[] samplePalette(float t) {
        float position = t * (PALETTE.length - 1);
        int index = Math.min((int) position, PALETTE.length - 2);
        float fraction = position - index;
        int first = PALETTE[index], second = PALETTE[index + 1];
        return new float[]{
                ((first >> 16) & 255) * (1f - fraction) + ((second >> 16) & 255) * fraction,
                ((first >> 8) & 255) * (1f - fraction) + ((second >> 8) & 255) * fraction,
                (first & 255) * (1f - fraction) + (second & 255) * fraction
        };
    }

    private static float bell(float t, float power) {
        return (float) Math.pow(Math.sin(Math.PI * t), power);
    }

    private static float wrap(float t) {
        return t - (float) Math.floor(t);
    }

    private static float smoothStep(float t) {
        t = Math.max(0f, Math.min(1f, t));
        return t * t * (3f - 2f * t);
    }
}
