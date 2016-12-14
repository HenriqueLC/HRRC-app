package com.mygdx.hrrc.screen.util;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.math.Circle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.ui.Widget;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;

import static java.lang.Math.PI;
import static java.lang.Math.cos;
import static java.lang.Math.sin;
import static java.lang.Math.toRadians;

public class SliderJoystick extends Widget {
    private boolean touched;
    private final Vector2 origin = new Vector2();
    private final Vector2 knobPosition = new Vector2();
    private Drawable knob, background;
    private float knobRadius, radius;
    private float knobPercent;
    private class TouchBound {
        float centerX, centerY;
        Circle external, internal;
        TouchBound() {
            centerX = origin.x + knobRadius + radius;
            centerY = origin.y + knobRadius;
            float externalRadius = radius + knobRadius;
            float internalRadius = radius - knobRadius;
            external = new Circle(centerX, centerY, externalRadius);
            internal = new Circle(centerX, centerY, internalRadius);
            knobPosition.set(this.getDefaultPoint());
        }

        Vector2 getInterceptionPoint(float screenX, float screenY) {
            screenX += origin.x;
            screenY += origin.y;
            double alpha;
            if (screenX - centerX >= 0) {
                alpha = PI / 2;
            }
            else if (screenY - centerY <= 0) {
                alpha = 0;
            }
            else {
                alpha = -Math.atan((screenY - centerY) / (screenX - centerX));
            }
            float x = (float)(centerX - radius * cos(alpha));
            x -= knob.getMinWidth() / 2;
            float y = (float)(centerY + radius * sin(alpha));
            y -= knob.getMinHeight() / 2;
            knobPercent = -1 * (1 - (float)(alpha / (PI / 4)));
            return new Vector2(x, y);
        }

        Vector2 getDefaultPoint() {
            float x = (float)(centerX - radius * cos(toRadians(45)));
            x -= knob.getMinWidth() / 2;
            float y = (float)(centerY + radius * sin(toRadians(45)));
            y -= knob.getMinHeight() / 2;
            knobPercent = 0f;
            return new Vector2(x, y);
        }
    }
    private TouchBound touchBound;

    public SliderJoystick(Drawable knob, float knobRadius, Drawable background, float radius) {
        this.knob = knob;
        this.knobRadius = knobRadius;
        this.background = background;
        this.radius = radius;
        // TouchBound
        touchBound = new TouchBound();
        // Add Listener
        addListener(new InputListener() {
            @Override
            public boolean touchDown (InputEvent event, float x, float y, int pointer, int button) {
                if (touched) return false;
                touched = true;
                calculatePositionAndValue(x, y, false);
                return true;
            }

            @Override
            public void touchDragged (InputEvent event, float x, float y, int pointer) {
                calculatePositionAndValue(x, y, false);
            }

            @Override
            public void touchUp (InputEvent event, float x, float y, int pointer, int button) {
                touched = false;
                calculatePositionAndValue(x, y, true);
            }

        });
    }

    private void calculatePositionAndValue(float x, float y, boolean isTouchUp) {
        if (!isTouchUp) {
            knobPosition.set(touchBound.getInterceptionPoint(x, y));
        }
        else {
            knobPosition.set(touchBound.getDefaultPoint());
        }
    }

    @Override
    public void setX(float x) {
        super.setX(x);
        origin.x = x;
        touchBound = new TouchBound();
    }

    @Override
    public void setY(float y) {
        super.setY(y);
        origin.y = y;
        touchBound = new TouchBound();
    }

    @Override
    public void setPosition (float x, float y) {
        super.setPosition(x, y);
        origin.set(x, y);
        touchBound = new TouchBound();
    }

    @Override
    public void draw(Batch batch, float parentAlpha) {
        validate();

        Color c = getColor();
        batch.setColor(c.r, c.g, c.b, c.a * parentAlpha);

        float x = getX();
        float y = getY();
        float w = getWidth();
        float h = getHeight();

        if (background != null) {
            background.draw(batch, x, y, w, h);
        }

        if (knob != null) {
            knob.draw(batch, knobPosition.x, knobPosition.y, knob.getMinWidth(), knob.getMinHeight());
        }
    }

    public boolean isTouched () {
        return touched;
    }

    public float getKnobPercent() {
        return knobPercent;
    }

}