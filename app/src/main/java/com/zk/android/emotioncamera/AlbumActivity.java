package com.zk.android.emotioncamera;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Environment;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.StaggeredGridLayoutManager;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

//相册界面
public class AlbumActivity extends AppCompatActivity {

    private String path;//根目录路径
    private RecyclerView mRecyclerView;//显示列表
    List<String> files;//所有照片路径
    private MyAdapter honmeAdapter;//适配器


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_album);
        path =Environment.getExternalStorageDirectory()+"/Pictures/emotionClassifies/";//储存目录
        files = getFilesAllName(path);

        mRecyclerView = (RecyclerView)findViewById(R.id.recyclerview);
        Log.d("test", "1");
        final StaggeredGridLayoutManager layoutManager = new StaggeredGridLayoutManager(2,StaggeredGridLayoutManager.VERTICAL);//定义瀑布流管理器，第一个参数是列数，第二个是方向。
        layoutManager.setGapStrategy(StaggeredGridLayoutManager.GAP_HANDLING_NONE);//不设置的话，图片闪烁错位，有可能有整列错位的情况。
        mRecyclerView.setLayoutManager(layoutManager);//设置瀑布流管理器
        //      获取数据，向适配器传数据，绑定适配器
        Log.d("test", "2");
        List<MyBitmap> datas = initData();

        honmeAdapter = new MyAdapter(AlbumActivity.this,datas);
        mRecyclerView.setAdapter(honmeAdapter);
        mRecyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
                super.onScrollStateChanged(recyclerView, newState);
                layoutManager.invalidateSpanAssignments();//这行主要解决了当加载更多数据时，底部需要重绘，否则布局可能衔接不上。
            }
        });
        honmeAdapter.setOnItemClickListener(new OnItemClickListener() {
            @Override
            public void onItemClick(View view, int position) {
                Log.d("review", Integer.toString(position));
                Intent intent = new Intent(AlbumActivity.this, ImageDetailsActivity.class);
                intent.putExtra("image_path", files.get(position));
                AlbumActivity.this.startActivity(intent);

            }
        });

        honmeAdapter.setOnItemLongClickListener(new OnLongClickListener() {
            @Override
            public void onLongClick(View view, final int position) {
                honmeAdapter.changeIsLongPush();
                honmeAdapter.notifyDataSetChanged();

            }



        });

        honmeAdapter.setOnDelClickListener(new OnDelClickListener() {
            @Override
            public void onDelClickListener(View view, int position) {
                files.remove(position);
                honmeAdapter.removeDataAt(position);
                Log.d("del", Integer.toString(position));
                honmeAdapter.notifyDataSetChanged();
            }
        });


    }


    private List<MyBitmap> initData() {
        List<MyBitmap> myBitmapList =new ArrayList<MyBitmap>();
        for(int i = 0; i < files.size(); i++){
            String path = files.get(i);
            MyBitmap p = new MyBitmap(readImg(path), path.split("/")[6], path);
            myBitmapList.add(p);}

        return myBitmapList;
    };


    //将图片转为bitmap格式
    public Bitmap readImg(String path){
        File mFile=new File(path);
        //若该文件存在
        if (mFile.exists()) {
            Bitmap bitmap=BitmapFactory.decodeFile(path);
            return bitmap;
        }
        return null;
    }


    //所有图片路径
    public static List<String> getFilesAllName(String path) {
        File file=new File(path);
        File[] files=file.listFiles();
        if (files == null){Log.e("error","空目录");return null;}
        List<String> s = new ArrayList<>();
        for(int i =0;i<files.length;i++){
            s.add(files[i].getAbsolutePath());
        }
        MyComparator comparator = new MyComparator();
        Collections.sort(s, comparator);

        return s;
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event){
        if(keyCode == KeyEvent.KEYCODE_BACK){//当返回按键被按下时
            if(honmeAdapter.getIsLongPush()){
                honmeAdapter.changeIsLongPush();
                honmeAdapter.notifyDataSetChanged();
            }
            else{
                finish();//结束当前Activity
            }
        }
        return false;
    }


}
