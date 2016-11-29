package com.mygdx.hrrc.screen.util;

import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.BitmapFontCache;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.scenes.scene2d.Actor;

public class Text extends Actor {

    private BitmapFontCache bitmapFontCache;
    private GlyphLayout glyphLayout;

    public Text(BitmapFontCache bitmapFontCache, String message) {
        this.bitmapFontCache = bitmapFontCache;
        glyphLayout = this.bitmapFontCache.addText(message, 0, 0);
    }

    public void setMessage(String message) {
        bitmapFontCache.clear();
        glyphLayout = this.bitmapFontCache.addText(message, 0, 0);
    }

    @Override
    public float getWidth() {
        return glyphLayout.width;
    }

    @Override
    public float getHeight() {
        return glyphLayout.height;
    }

    public void setAlphas(float alpha) {
        bitmapFontCache.setAlphas(alpha);
    }

    @Override
    public void draw(Batch batch, float parentAlpha) {
        float x = getX();
        float y = getY();
        float scaleX = getScaleX();
        float scaleY = getScaleY();

        bitmapFontCache.setPosition(x, y);
        bitmapFontCache.getFont().getData().setScale(scaleX, scaleY);
        bitmapFontCache.draw(batch);
    }
}
