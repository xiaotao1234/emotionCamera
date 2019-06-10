package com.zk.android.emotioncamera;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

public class CircleButton extends View {
    private Paint mPaint;
    private boolean isPush;//是否按下按钮
    private float width;
    private float height;
    private float radius;//圆的半径
    public CircleButton(Context context){
        super(context);
        init();
    }

    public CircleButton(Context context, AttributeSet attrs){
        super(context, attrs);
        init();
    }

    public CircleButton(Context context, AttributeSet attrs, int defStyleAttr){
        super(context, attrs, defStyleAttr);
        init();
    }

    public void setIsPush(boolean bool){
        isPush = bool;
    }

    public boolean getIsPush(){
        return isPush;
    }

    private void init(){
        mPaint = new Paint();
        isPush = false;
    }

    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec){
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        width = getMeasuredWidth();
        height = getMeasuredHeight();
        radius = Math.min(width, height) / 2 - 10;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if(!isPush) { //没有按下按钮
            mPaint.setColor(Color.argb(150, 132, 132, 132));
            mPaint.setStyle(Paint.Style.FILL_AND_STROKE);
            canvas.drawCircle(width / 2, height / 2, radius, mPaint);
            mPaint.setColor(Color.argb(150, 239, 239, 239));
            canvas.drawCircle(width / 2, height / 2, radius / 3 * 2, mPaint);
        }else{//按下了按钮
            mPaint.setColor(Color.argb(150, 132, 132, 132));
            //mPaint.setColor(Color.argb(190, 50, 50, 50));
            mPaint.setStyle(Paint.Style.FILL_AND_STROKE);
            canvas.drawCircle(width / 2, height / 2, radius + 10, mPaint);
            //mPaint.setColor(Color.argb(150, 239, 239, 239));
            mPaint.setColor(Color.argb(150, 190, 190, 190));
            canvas.drawCircle(width / 2, height / 2, radius / 3 * 2 + 10, mPaint);
        }

    }
    public void  changeIsPush(){
        isPush = !isPush;
    }
}
