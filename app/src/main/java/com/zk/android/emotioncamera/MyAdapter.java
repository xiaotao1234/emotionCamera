package com.zk.android.emotioncamera;

import android.content.Context;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import java.io.File;
import java.util.List;

public class MyAdapter extends RecyclerView.Adapter<MyAdapter.MyViewHolder>{
    private Context context;
    private List<MyBitmap> list;
    private OnItemClickListener mOnItemClickListener;//点击声明接口
    private OnLongClickListener mOnLongClickListener;//长按声明接口
    private OnDelClickListener mOnDelClickListener;//点击删除
    private boolean islongPush;

    public MyAdapter(Context context, List<MyBitmap> list) {
        this.context=context;
        this.list=list;

    }

    public void changeIsLongPush(){
        islongPush = !islongPush;
    }

    public boolean getIsLongPush(){
        return islongPush;
    }


    //承载每个子项的布局
    @Override
    public MyViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        MyViewHolder holder = new MyViewHolder(LayoutInflater.from(
                context).inflate(R.layout.item_home, parent,
                false));
        return holder;
    }


    //将每个子项holder绑定数据
    @Override
    public void onBindViewHolder(final MyViewHolder holder, int position) {
        holder.imageView.setImageBitmap(list.get(position).getImg());
        holder.textView.setText(list.get(position).getTitle());
        if(islongPush)
            holder.delBtn.setVisibility(View.VISIBLE);
        else
            holder.delBtn.setVisibility(View.INVISIBLE);
        View itemView = ((RelativeLayout) holder.itemView).getChildAt(0);

        if (mOnItemClickListener != null) {
            itemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    int position = holder.getLayoutPosition();
                    mOnItemClickListener.onItemClick(holder.itemView, position);
                }
            });
        }
        if(mOnLongClickListener != null){
            itemView.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    int position = holder.getLayoutPosition();
                    Log.d("test", Long.toString(holder.getItemId()));
                    mOnLongClickListener.onLongClick(holder.itemView, position);
                    return false;
                }
            });
        }

        if(mOnDelClickListener != null){
            holder.delBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    int position = holder.getLayoutPosition();
                    mOnDelClickListener.onDelClickListener(holder.delBtn, position);
                }
            });
        }


    }



    public void setOnItemClickListener(OnItemClickListener onItemClickListener) {
        mOnItemClickListener = onItemClickListener;
    }

    public void setOnItemLongClickListener(OnLongClickListener onLongClickListener){
        mOnLongClickListener = onLongClickListener;
    }

    public void setOnDelClickListener(OnDelClickListener onDelClickListener){
        mOnDelClickListener = onDelClickListener;
    }

    /**
     * 删除第position个数据
     * @param position
     */
    public void removeDataAt(int position) {
        MyBitmap p = list.remove(position);
        File f = new File(p.getPath());
        try {
            f.delete();
        }catch (Exception e){
            Log.d("delet", "del not success");
        }
        notifyItemRemoved(position);
    }

    @Override
    public int getItemCount() {
        return list.size();
    }

    /**
     * ViewHolder的类，用于缓存控件
     */
    class MyViewHolder extends RecyclerView.ViewHolder {

        private ImageView imageView;
        private TextView textView;
        private CircleImageView delBtn;


        public MyViewHolder(View itemView){
            super(itemView);
            imageView= (ImageView) itemView.findViewById(R.id.masonry_item_img );
            textView= (TextView) itemView.findViewById(R.id.masonry_item_title);
            delBtn = (CircleImageView) itemView.findViewById(R.id.masonry_item_delBtn);
        }


    }


}
