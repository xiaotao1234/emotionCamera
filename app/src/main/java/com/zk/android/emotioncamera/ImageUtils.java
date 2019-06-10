package com.zk.android.emotioncamera;

import org.json.JSONObject;
import org.opencv.core.Mat;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.io.InputStream;

public class ImageUtils {
    /**
     * 处理图片，使其size*size的float数组
     * @param source 原图片
     * @param size 目标图片大小
     * @return float数组
     */
    public static float[] normalizeBitmap(Mat source, int size){
        Mat dst = new Mat();
        Imgproc.resize(source, dst, new Size(size, size));
        float[] output = new float[size * size * 1];

        for(int i = 0; i < size; i++ )
            for(int j = 0; j < size; j++)
                output[i * size + j] = (float)(dst.get(i, j)[0]/ 255.0);
        return output;

        }

    /**
     *  找出预测值最大的下标及值
     * @param array 图片数组
     * @return
     */
    public static Object[] argmax(float[] array){


            int best = -1;
            float best_confidence = 0.0f;

            for(int i = 0;i < array.length;i++){

                float value = array[i];

                if (value > best_confidence){

                    best_confidence = value;
                    best = i;
                    }
            }

            return new Object[]{best,best_confidence};
            }

    /**
     * 读取json文件，初始化标签
     * @param jsonStream
     * @return
     */
    public static JSONObject labelInit(InputStream jsonStream){
                JSONObject object = new JSONObject();
                try {

                    byte[] jsonData = new byte[jsonStream.available()];
                    jsonStream.read(jsonData);
                    jsonStream.close();
                    String jsonString = new String(jsonData,"utf-8");
                    object = new JSONObject(jsonString);

                }
                catch (Exception e){
                }
                return object;
            }

    /**
     *  根据下表找到标签
     * @param object
     * @param index
     * @return
     */
    public static String getLabel( JSONObject object, int index){
                String label = "";
                try {
                    label = object.getString(String.valueOf(index));
                }
                catch (Exception e){
                }
                return label;
            }
        }
