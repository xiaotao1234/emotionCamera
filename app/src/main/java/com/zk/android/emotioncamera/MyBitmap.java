package com.zk.android.emotioncamera;

import android.graphics.Bitmap;

public class MyBitmap {
    private Bitmap img;
    private String title;
    private String path;

    public MyBitmap(Bitmap img, String title, String path) {
        this.img = img;
        this.title = title;
        this.path = path;

    }

    public Bitmap getImg() {
        return img;
    }

    public void setImg(Bitmap img) {
        this.img = img;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }
}
