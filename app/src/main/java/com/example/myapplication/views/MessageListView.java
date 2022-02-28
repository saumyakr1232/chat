package com.example.myapplication.views;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.widget.ListView;

public class MessageListView extends ListView
{
    public MessageListView(Context context) {
        super(context);
    }
    public MessageListView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }
    public MessageListView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }
    @Override
    public boolean onInterceptTouchEvent (MotionEvent event)
    {
        onTouchEvent(event);
        return false;
    }
    public int computeVerticalScrollOffset()
    {
        return super.computeVerticalScrollOffset();
    }
}