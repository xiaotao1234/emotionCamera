package com.zk.android.emotioncamera;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Environment;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.View;
import android.view.WindowManager;

import org.json.JSONObject;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfRect;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import org.opencv.objdetect.CascadeClassifier;
import org.tensorflow.contrib.android.TensorFlowInferenceInterface;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import static com.zk.android.emotioncamera.AlbumActivity.getFilesAllName;

public class MainActivity extends AppCompatActivity implements
        CameraBridgeViewBase.CvCameraViewListener2, View.OnClickListener{
    private CameraBridgeViewBase cameraView;//avaCameraView的抽象基类，用于画面获取与处理
    private CircleImageView mimageView;//相册预览图
    private CascadeClassifier classifier;//人脸级联分类器
    public CircleImageView maiImageView;//贴图开关按键
    private CircleImageView switchCamera;//前后摄像头切换按键
    private boolean misAiPush;//判断贴图开关按键是否被按下，true：按下，false：没按下
    private Mat mGray;//摄像头灰度帧矩阵
    private Mat mRgba;//摄像头rgba帧矩阵
    private Mat mRgbaRotate;//rgba帧旋转后矩阵
    private Mat mGrayRotate;//gray帧旋转后矩阵
    private Mat mpostMat;//处理后帧矩阵
    private Mat mpreMat;//处理前帧矩阵
    private int mAbsoluteFaceSize = 0;//脸部绝对尺寸
    private boolean isFrontCamera;//判断是否按下切换摄像头按键，true：按下，false：没按下
    private JSONObject labels;//分类的标签
    private static String TAG = "zkdebug";//调试信息
    private static final String KEY_INDEX = "switch";//onPaus时保存isFrontCamera
    private static final String KEY_AIPUSH = "aipush";//onPaus时保存misAiPush
    private float downX;//按下拍照时X坐标
    private float downY;//按下拍照时Y坐标
    private float moveX;//移动时X坐标
    private float moveY;//移动时Y坐标

    private static final String MODEL_PATH = "file:///android_asset/EmotionNetEmotionClassify.pb";//表情分类模型路径
    private static final String INPUT_NAME = "conv2d_1_input";//模型输入节点
    private static final String OUTPUT_NAME = "output_1";//模型输出节点
    private TensorFlowInferenceInterface tf;//tensorFlow接口


    private  Mat []labelsMat = new Mat[8];//7种表情贴图，最后一个是白色贴图
    private float[] PREDICTIONS = new float[7];//7种表情预测结果
    private float[] floatValues;//Mat转化为float数组
    private int[] INPUT_SIZE = {48,48,1};//模型输入形式

    private Timer timer;//贴图按键显示时间计时器
    private MyTimerTask timerTask;
    //隐藏贴图按键
    private class MyTimerTask extends TimerTask {
        @Override
        public void run() {
            maiImageView.setVisibility(View.INVISIBLE);
            Log.d("mythread", "invisible");
        }
    };



    // 手动装载openCV库文件，以保证手机无需安装OpenCV Manager
    //加载tensorflow库
    static {
        System.loadLibrary("opencv_java3");
        System.loadLibrary("tensorflow_inference");
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        initWindowSettings();//初始化activaity设置
        checkPermission();//检查权限
        checkSaveFloderPath();//检查储存路径

        setContentView(R.layout.activity_main);

        initLabelsMat();//初始化贴图
        tf = new TensorFlowInferenceInterface(getAssets(),MODEL_PATH);//实例化tensorflow
        try {
            labels = ImageUtils.labelInit(getAssets().open("Labels.json"));//初始化labels
        }catch (Exception e){}

        cameraView = (CameraBridgeViewBase) findViewById(R.id.camera_view);
        cameraView.setCvCameraViewListener(this); // 设置相机监听
        cameraView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                {
                    //贴图键5s显示后隐藏
                    maiImageView.setVisibility(View.VISIBLE);
                    if(timer == null) {
                        timer = new Timer(true);
                        timerTask = new MyTimerTask();
                        timer.schedule(timerTask, 5000);
                    }
                    //用户在5秒内再次点击，重新计时
                    else{
                        timerTask.cancel();
                        timer.cancel();
                        timer.purge();
                        timer = new Timer(true);
                        timerTask = new MyTimerTask();
                        timer.schedule(timerTask, 5000);
                    }
                }
            }
        });
        initClassifier();//初始化级联分类器
        if(savedInstanceState != null) {
            isFrontCamera = savedInstanceState.getBoolean(KEY_INDEX, false);
            misAiPush = savedInstanceState.getBoolean(KEY_AIPUSH, false);
            ensureCamera(isFrontCamera);//切换前后摄像头
        }
        cameraView.enableView();


        CircleImageView switchCamera = (CircleImageView) findViewById(R.id.switch_camera);
        switchCamera.setOnClickListener(this); //默认后置

        final CircleButton takePhotoBtn = (CircleButton) findViewById(R.id.take_btn);
        takePhotoBtn.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                //按下拍照按钮，记录x坐标和y坐标
                if(event.getAction() == MotionEvent.ACTION_DOWN) {
                    Log.d("take", "down");
                    downX = event.getX();
                    downY = event.getY();
                    moveX = downX;
                    moveY = downY;
                    takePhotoBtn.changeIsPush();//改变按下状态
                }
                //记录用户移动x、y坐标
                else if(event.getAction() == MotionEvent.ACTION_MOVE) {
                    moveX = event.getX();
                    moveY = event.getY();
                    Log.d("move", "moveX:" + Float.toString(moveX)+ " moveY" + Float.toString(moveY));
                    return true;
                }
                //用户抬起时
                else if(event.getAction() == MotionEvent.ACTION_UP) {
                    //判断移动距离
                    if(sqlDistance(downX, downY, moveX, moveY) <= 20000) {
                        if (misAiPush)//判断贴图按键状态
                            takePhoto(mpostMat);
                        else
                            takePhoto(mpreMat);
                    }

                    Log.d("take", "up");
                    takePhotoBtn.changeIsPush();
                }
                takePhotoBtn.invalidate();//刷新按钮
                return true;
            }
        });

        mimageView = (CircleImageView)findViewById(R.id.albumView);
        mimageView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //跳转到AlbumActivity界面
                Intent intent = new Intent(MainActivity.this, AlbumActivity.class);
                MainActivity.this.startActivity(intent);
            }
        });

        maiImageView = (CircleImageView)findViewById(R.id.ai);
        maiImageView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //根据按下状态改变按键
                misAiPush = !misAiPush;
                if(misAiPush)
                    maiImageView.setBackgroundResource(R.drawable.aiblue);
                else
                    maiImageView.setBackgroundResource(R.drawable.aigray);
                maiImageView.setVisibility(View.VISIBLE);
                if(timer != null){//5s结束前用户点击，重新计时
                    timerTask.cancel();
                    timer.purge();
                    timer.cancel();
                }
                //显示5秒贴图按键
                timer = new Timer(true);
                timerTask = new MyTimerTask();
                timer.schedule(timerTask, 5000);
            }
        });

        //刚进app时显示5s贴图按键
        if(misAiPush)
            maiImageView.setBackgroundResource(R.drawable.aiblue);
        else
            maiImageView.setBackgroundResource(R.drawable.aigray);
        maiImageView.setVisibility(View.VISIBLE);
        timer = new Timer(true);
        timerTask = new MyTimerTask();
        timer.schedule(timerTask, 5000);
    }



    /**
     * 判断用户滑动的距离，Point1(x1, y1)和Point2(x2,y2)距离的平方
     * @param x1 Point1X坐标
     * @param y1 Point1Y坐标
     * @param x2 Point2X坐标
     * @param y2 Point2Y坐标
     * @return 距离的平方
     */
    private float sqlDistance(float x1, float y1, float x2, float y2){
        return (x1 - x2) * (x1 - x2) + (y1 - y2) * (y1 - y2);
    }


    /**
     * 在贴图相应位置加黑色边框
     * @param res 原贴图
     * @param rect 原贴图在目标图片的矩形区域
     * @param deswidth 目标图片的宽度
     * @param desheight 目标图片的高度
     * @return Mat 目标图片
     */
    private Mat addBorder(Mat res, Rect rect, int deswidth, int desheight){
        Mat temp = new Mat();
        Imgproc.resize(res, temp, new Size(rect.width, rect.height));//原贴图与矩形区域一样大
        int leftwidth = (int)rect.tl().x;//左边框的宽度
        int topheight = (int)rect.tl().y;//上边框的宽度
        int rightwidth = deswidth - (int)rect.br().x; //有边框的宽度
        int bottomheight = desheight - (int)rect.br().y; //下边框的宽度
        Mat des = new Mat();
        Core.copyMakeBorder(temp, des, topheight, bottomheight, leftwidth, rightwidth, Core.BORDER_CONSTANT, new Scalar(0, 0, 0, 255)); //添加黑色边框
        return des;
    }


    /**
     * 检查调用摄像头、内存读写的权限
     */
    private void checkPermission(){
        if (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED )
            ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.CAMERA, Manifest.permission.READ_EXTERNAL_STORAGE}, 1);
    }

    /**
     * 检查储存路径，若没有则创建
     */
    private void checkSaveFloderPath(){
        String path = Environment.getExternalStorageDirectory()+"/Pictures/emotionClassifies/";//储存目录
        File baseFloder = new File(path);
        if(!baseFloder.exists()){
            baseFloder.mkdirs();
        }
    }

    /**
     * 存储照片，保存在根目录/Pictures/emotionClassifies/中
     * @param processMat 要存储的照片
     */
    private void takePhoto(Mat processMat){
        if(!processMat.empty()){//判断图片是否为空
            int rotation = cameraView.getDisplay().getRotation();
            Mat inter = new Mat();
            Log.e("Mat", "................1..............");
            //将四通道的RGBA转为三通道的BGR，重要！！
            Imgproc.cvtColor(processMat, inter, Imgproc.COLOR_RGB2BGR);
            Log.e("Mat", "................2...............");
            File sdDir = null;
            //判断是否存在机身内存
            boolean sdCardExist = Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED);
            if(sdCardExist){
                //获得机身存储根目录
                sdDir = Environment.getExternalStorageDirectory();
                Log.e("Mat", "..............3.............");
            }
            //设置文件名
            String filename = String.format("%05d", getImgNums() + 1);
            //存储路径
            String savepath = sdDir + "/Pictures/emotionClassifies/";
            File f = new File(savepath);
            if(!f.exists())//如果不存在此文件夹，创建
                f.mkdirs();
            String filePath = sdDir + "/Pictures/emotionClassifies/" + filename + ".jpg";
            Log.e("Mat", "..................."+filePath+".............");
            //根据手机的横竖屏状态旋转照片
            if(rotation == Surface.ROTATION_0)
                Core.rotate(inter, inter, Core.ROTATE_90_CLOCKWISE);
            if(rotation == Surface.ROTATION_270)
                Core.rotate(inter, inter, Core.ROTATE_180);
            //将转化后的BGR矩阵内容写入到文件中
            Imgcodecs.imwrite(filePath, inter);
            showCircleImage(filePath);

        }
    }

    /**
     * 显示在相册预览图上
     * @param filePath 图片的路径
     */
    private void showCircleImage(String filePath){
        Bitmap btmap = BitmapFactory.decodeFile(filePath);
        mimageView.setImageBitmap(btmap);
    }

    /**
     * 显示最近拍摄照片在相册预览图上
     * 如果没有图片则显示灰图
     */
    private void checkCircleImage(){
        String path =Environment.getExternalStorageDirectory()+"/Pictures/emotionClassifies/";
        List<String> files = getFilesAllName(path);
        if(files.size() == 0)
            mimageView.setImageResource(R.drawable.gray);
        else
            showCircleImage(files.get(files.size() - 1));
    }


    /**
     * 获得照片总数
     * @return 照片总数
     */
    private int getImgNums(){
        String path = Environment.getExternalStorageDirectory() + "/Pictures/emotionClassifies/";
        return getFilesAllName(path).size();
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.switch_camera:
                cameraView.disableView();
                if (isFrontCamera) {
                    cameraView.setCameraIndex(CameraBridgeViewBase.CAMERA_ID_BACK);
                    isFrontCamera = false;
                } else {
                    cameraView.setCameraIndex(CameraBridgeViewBase.CAMERA_ID_FRONT);
                    isFrontCamera = true;
                }
                cameraView.enableView();
                break;
            default:
        }
    }

    /**
     * 切换前、后摄像头
     * @param isFrontCamera true：前置摄像头 false：后置摄像头
     */
    public void ensureCamera(boolean isFrontCamera){
        if(isFrontCamera)
            cameraView.setCameraIndex(CameraBridgeViewBase.CAMERA_ID_FRONT);
        else
            cameraView.setCameraIndex(CameraBridgeViewBase.CAMERA_ID_BACK);
        cameraView.enableView();
    }

    @Override
    public void onSaveInstanceState(Bundle savedInstanceState){
        super.onSaveInstanceState(savedInstanceState);
        savedInstanceState.putBoolean(KEY_INDEX, isFrontCamera);
        savedInstanceState.putBoolean(KEY_AIPUSH, misAiPush);
    }

    // 初始化窗口设置, 包括全屏、常亮
    private void initWindowSettings() {
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    // 初始化人脸级联分类器，必须先初始化
    private void initClassifier() {
        try {
            InputStream is = getResources().openRawResource(R.raw.lbpcascade_frontalface);
            File cascadeDir = getDir("cascade", Context.MODE_PRIVATE);
            File cascadeFile = new File(cascadeDir, "lbpcascade_frontalface.xml");
            FileOutputStream os = new FileOutputStream(cascadeFile);
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = is.read(buffer)) != -1) {
                os.write(buffer, 0, bytesRead);
            }
            is.close();
            os.close();
            classifier = new CascadeClassifier(cascadeFile.getAbsolutePath());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 帧处理前初始化
     * @param width -  the width of the frames that will be delivered
     * @param height - the height of the frames that will be delivered
     */
    @Override
    public void onCameraViewStarted(int width, int height) {
        mGray = new Mat(height, width, CvType.CV_8UC1);
        mRgba = new Mat(height, width, CvType.CV_8UC4);
        mGrayRotate = new Mat(width, height, CvType.CV_8UC1);
        mRgbaRotate = new Mat(width, height, CvType.CV_8UC4);
        mpreMat = new Mat();
        mpostMat = new Mat();
    }

    /**
     * 帧处理后释放空间
     */
    @Override
    public void onCameraViewStopped() {
        mGray.release();
        mRgba.release();
        mGrayRotate.release();
        mRgbaRotate.release();
        mpreMat.release();
        mpostMat.release();
    }

    /**
     * 初始化贴图矩阵
     */
    private void initLabelsMat(){
        try {
            labelsMat[0] = new Mat();
            Bitmap bmp = BitmapFactory.decodeStream(getAssets().open("angry.jpg"));//读取图片
            Utils.bitmapToMat(bmp, labelsMat[0]);

            labelsMat[1] = new Mat();
            bmp = BitmapFactory.decodeStream(getAssets().open("disgust.jpg"));//读取图片
            Utils.bitmapToMat(bmp, labelsMat[1]);

            labelsMat[2] = new Mat();
            bmp = BitmapFactory.decodeStream(getAssets().open("fear.jpg"));//读取图片
            Utils.bitmapToMat(bmp, labelsMat[2]);

            labelsMat[3] = new Mat();
            bmp = BitmapFactory.decodeStream(getAssets().open("happy.jpg"));//读取图片
            Utils.bitmapToMat(bmp, labelsMat[3]);

            labelsMat[4] = new Mat();
            bmp = BitmapFactory.decodeStream(getAssets().open("sad.jpg"));//读取图片
            Utils.bitmapToMat(bmp, labelsMat[4]);

            labelsMat[5] = new Mat();
            bmp = BitmapFactory.decodeStream(getAssets().open("surprise.jpg"));//读取图片
            Utils.bitmapToMat(bmp, labelsMat[5]);

            labelsMat[6] = new Mat();
            bmp = BitmapFactory.decodeStream(getAssets().open("neutral.jpg"));//读取图片
            Utils.bitmapToMat(bmp, labelsMat[6]);

            labelsMat[7] = new Mat();
            bmp = BitmapFactory.decodeStream(getAssets().open("whitehead.jpg"));//读取图片
            Utils.bitmapToMat(bmp, labelsMat[7]);

        }catch(Exception e){
            Log.d("read","read error");
        }
    }

    /**
     * 进行贴图
     * @param Rgba 视频帧
     * @param map 贴图
     * @param faceRect 贴图矩形位置
     */
    private void chartlet(Mat Rgba, Mat map, Rect faceRect){
        Mat white = new Mat();
        labelsMat[7].copyTo(white);
        Mat expandWhite = addBorder(white, faceRect, Rgba.width(), Rgba.height()); //添加边框
        Mat expandMat = addBorder(map, faceRect, Rgba.width(), Rgba.height());
        Core.subtract(Rgba, expandWhite, Rgba); //将要贴图位置置为黑色
        Core.add(Rgba, expandMat, Rgba); //添加贴图
    }

    @Override
    // 这里执行人脸检测的逻辑, 根据OpenCV提供的例子实现(face-detection)
    public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame) {
        mRgba = inputFrame.rgba();//摄像头rgba帧
        mGray = inputFrame.gray();//摄像头灰度值帧

        int rotation = cameraView.getDisplay().getRotation();//获取手机横竖屏状态
        // 翻转矩阵以适配前后置摄像头
        if (isFrontCamera) {
            Core.flip(mRgba, mRgba, 1);
            Core.flip(mGray, mGray, 1);
        }
        mRgba.copyTo(mpreMat);
        float mRelativeFaceSize = 0.2f; //人脸相对大小
        //手机竖屏状态
        if(rotation == Surface.ROTATION_0){
            Core.rotate(mGray, mGrayRotate, Core.ROTATE_90_CLOCKWISE);
            Core.rotate(mRgba, mRgbaRotate, Core.ROTATE_90_CLOCKWISE);
            if (mAbsoluteFaceSize == 0) {
                int height = mGrayRotate.rows();
                if (Math.round(height * mRelativeFaceSize) > 0) {
                    mAbsoluteFaceSize = Math.round(height * mRelativeFaceSize);
                }
            }
            MatOfRect faces = new MatOfRect();
            if (classifier != null)
                classifier.detectMultiScale(mGrayRotate, faces, 1.1, 3, 2,
                        new Size(mAbsoluteFaceSize, mAbsoluteFaceSize), new Size());
            Rect[] facesArray = faces.toArray();
            Scalar faceRectColor = new Scalar(0, 255, 0, 255);
            for (Rect faceRect : facesArray) {

                predict(faceRect,mGrayRotate);

                //得到最高的结果
                Object[] results = ImageUtils.argmax(PREDICTIONS);

                int class_index = (Integer) results[0];
                float confidence = (Float) results[1];

                //转换成label
                final String label = ImageUtils.getLabel(labels, class_index);
                String text = label + ":" + String.format("%.2f", confidence);


                if(misAiPush){
                    chartlet(mRgbaRotate, labelsMat[class_index], faceRect);
                }

                else {

                    Imgproc.putText(mRgbaRotate, text, faceRect.tl(), 2, 2, faceRectColor);
                    Imgproc.rectangle(mRgbaRotate, faceRect.tl(), faceRect.br(), faceRectColor, 3);
                }

                Core.rotate(mRgbaRotate, mRgba, Core.ROTATE_90_COUNTERCLOCKWISE);;


            }}
        //手机逆时针旋转了90度
        else if(rotation == Surface.ROTATION_90){
            if (mAbsoluteFaceSize == 0) {
                int height = mGray.rows();
                if (Math.round(height * mRelativeFaceSize) > 0) {
                    mAbsoluteFaceSize = Math.round(height * mRelativeFaceSize);
                }
            }
            MatOfRect faces = new MatOfRect();
            if (classifier != null)
                classifier.detectMultiScale(mGray, faces, 1.1, 3, 2,
                        new Size(mAbsoluteFaceSize, mAbsoluteFaceSize), new Size());
            Rect[] facesArray = faces.toArray();
            Scalar faceRectColor = new Scalar(0, 255, 0, 255);
            for (Rect faceRect : facesArray) {

                predict(faceRect, mGray);

                Object[] results = ImageUtils.argmax(PREDICTIONS);

                int class_index = (Integer) results[0];
                float confidence = (Float) results[1];

                //Convert predicted class index into actual label name
                final String label = ImageUtils.getLabel(labels, class_index);
                String text = label + ":" + String.format("%.2f", confidence);

                if(misAiPush){
                    chartlet(mRgba, labelsMat[class_index], faceRect);
                }

                else {

                    Imgproc.putText(mRgba, text, faceRect.tl(), 2, 2, faceRectColor);

                    Imgproc.rectangle(mRgba, faceRect.tl(), faceRect.br(), faceRectColor, 3);
                }
            }
        }
        //手机旋转了180度
        else if(rotation == Surface.ROTATION_180){
            Core.rotate(mGray, mGrayRotate, Core.ROTATE_90_COUNTERCLOCKWISE);
            Core.rotate(mRgba, mRgbaRotate, Core.ROTATE_90_COUNTERCLOCKWISE);
            if (mAbsoluteFaceSize == 0) {
                int height = mGrayRotate.rows();
                if (Math.round(height * mRelativeFaceSize) > 0) {
                    mAbsoluteFaceSize = Math.round(height * mRelativeFaceSize);
                }
            }
            MatOfRect faces = new MatOfRect();
            if (classifier != null)
                classifier.detectMultiScale(mGrayRotate, faces, 1.1, 3, 2,
                        new Size(mAbsoluteFaceSize, mAbsoluteFaceSize), new Size());
            Rect[] facesArray = faces.toArray();
            Scalar faceRectColor = new Scalar(0, 255, 0, 255);
            for (Rect faceRect : facesArray) {

                predict(faceRect, mGrayRotate);

                Object[] results = ImageUtils.argmax(PREDICTIONS);

                int class_index = (Integer) results[0];
                float confidence = (Float) results[1];

                //Convert predicted class index into actual label name
                final String label = ImageUtils.getLabel(labels, class_index);
                String text = label + ":" + String.format("%.2f", confidence);

                if(misAiPush){
                    chartlet(mRgbaRotate, labelsMat[class_index], faceRect);
                }

                else {

                    Imgproc.putText(mRgbaRotate, text, faceRect.tl(), 2, 2, faceRectColor);

                    Imgproc.rectangle(mRgbaRotate, faceRect.tl(), faceRect.br(), faceRectColor, 3);
                }
                Core.rotate(mRgbaRotate, mRgba, Core.ROTATE_90_CLOCKWISE);;
            }
        }
        //手机旋转了270度
        else if(rotation == Surface.ROTATION_270){
            Core.rotate(mGray, mGrayRotate, Core.ROTATE_180);
            Core.rotate(mRgba, mRgbaRotate, Core.ROTATE_180);
            if (mAbsoluteFaceSize == 0) {
                int height = mGrayRotate.rows();
                if (Math.round(height * mRelativeFaceSize) > 0) {
                    mAbsoluteFaceSize = Math.round(height * mRelativeFaceSize);
                }
            }
            MatOfRect faces = new MatOfRect();
            if (classifier != null)
                classifier.detectMultiScale(mGrayRotate, faces, 1.1, 3, 2,
                        new Size(mAbsoluteFaceSize, mAbsoluteFaceSize), new Size());
            Rect[] facesArray = faces.toArray();
            Scalar faceRectColor = new Scalar(0, 255, 0, 255);
            for (Rect faceRect : facesArray) {

                predict(faceRect, mGrayRotate);

                Object[] results = ImageUtils.argmax(PREDICTIONS);

                int class_index = (Integer) results[0];
                float confidence = (Float) results[1];

                //Convert predicted class index into actual label name
                final String label = ImageUtils.getLabel(labels, class_index);
                String text = label + ":" + String.format("%.2f", confidence);

                if(misAiPush){
                    chartlet(mRgbaRotate, labelsMat[class_index], faceRect);
                }

                else {

                    Imgproc.putText(mRgbaRotate, text, faceRect.tl(), 2, 2, faceRectColor);

                    Imgproc.rectangle(mRgbaRotate, faceRect.tl(), faceRect.br(), faceRectColor, 3);
                }

                Core.rotate(mRgbaRotate, mRgba, Core.ROTATE_180);;
            }
        }
        mRgba.copyTo(mpostMat);
        return mRgba;
    }

    @Override
    protected void onResume(){
        super.onResume();
        initLabelsMat();//初始化贴图
        cameraView.enableView();
        checkCircleImage();

        if(misAiPush)
            maiImageView.setBackgroundResource(R.drawable.aiblue);
        else
            maiImageView.setBackgroundResource(R.drawable.aigray);
        maiImageView.setVisibility(View.VISIBLE);
        if(timer != null)
        {
            timerTask.cancel();
            timer.purge();
            timer.cancel();
        }
        timer = new Timer(true);
        timerTask = new MyTimerTask();
        timer.schedule(timerTask, 5000);
    }

    @Override
    protected void onPause(){
        super.onPause();
        cameraView.disableView();

        if(timer != null)
        {
            timerTask.cancel();
            timer.purge();
            timer.cancel();
        }

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cameraView.disableView();
    }


    /**
     *  预测人脸表情
     * @param faceRect 人脸区域
     * @param Gray 视频帧
     */
    public void predict(Rect faceRect, Mat Gray) {

        //转换为48*48的灰度图
        Log.d("predict", "enter");
        floatValues = ImageUtils.normalizeBitmap(new Mat(Gray, faceRect),48);
        Log.d("predict", Float.toString(floatValues[0]));

        //放入tensorflow
        tf.feed(INPUT_NAME,floatValues,1, 48,48,1);
        Log.d("predict", "feed");

//        Session s = new Session(tf.graph());
//        tf.graph().
//        Tensor learning_phase = Tensor.create(false);
//        Tensor res = s.runner().f;
//        Tensor result = s.runner().feed("input", image).feed("batch_normalization_1/keras_learning_phase", learning_phase).fetch("output").run().get(0))



        //计算结果
        tf.run(new String[]{OUTPUT_NAME});
        Log.d("predict", "run");


        //复制输出到PREDICTIONS数组
        tf.fetch(OUTPUT_NAME,PREDICTIONS);
        Log.d("predict", "fetch");
    }
}
