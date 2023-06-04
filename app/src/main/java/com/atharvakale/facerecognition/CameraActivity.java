package com.atharvakale.facerecognition;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.view.SurfaceView;
import android.view.Window;
import android.view.WindowManager;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.CvType;
import org.opencv.core.Mat;

import java.io.IOException;

public class CameraActivity extends Activity implements CameraBridgeViewBase.CvCameraViewListener2 {
    private  static final String TAG="MainActivity";

    // define our class
    private Face_Recognition face_recognition;

    private Mat mRgba;
    private Mat mGray;
    private CameraBridgeViewBase mOpenCvCameraView;
    private BaseLoaderCallback mLoaderCallback = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            switch (status){
                case LoaderCallbackInterface
                        .SUCCESS:{
                    Log.i(TAG, "OpenCv is loaded");
                    mOpenCvCameraView.enableView();
                }
                default:
                {
                    super.onManagerConnected(status);
                }
                break;
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        // if camera permission is not given it will ask for it on device
        int MY_PERMISSTIONS_REQUEST_CAMERA = 0;
        if(ContextCompat.checkSelfPermission(CameraActivity.this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_DENIED)
        {
            ActivityCompat.requestPermissions(CameraActivity.this, new String[] {Manifest.permission.CAMERA}, MY_PERMISSTIONS_REQUEST_CAMERA);
        }

        setContentView(R.layout.activity_camera);

        mOpenCvCameraView = (CameraBridgeViewBase) findViewById(R.id.surface);
        mOpenCvCameraView.setVisibility(SurfaceView.VISIBLE);
        mOpenCvCameraView.setCvCameraViewListener(this);

        // call our class face_Recognition
        try {
            // input size model is 96 if using EfficientNet, MobileNet | 64 if using model.tflite
            int inputSize = 96;
            face_recognition = new Face_Recognition(getAssets(),
                    CameraActivity.this,
                    "MobileNet.tflite",
                    inputSize);
        }
        catch (IOException e){
            e.printStackTrace();
            Log.d("CameraActivity", "Model is not loaded");
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if(OpenCVLoader.initDebug()){
            // if load success
            Log.d(TAG, "OpenCV initialization is done");
            mLoaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS);
        }
        else {
            //
            Log.d(TAG, "OpenCV is not loaded. Try again");
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_4_0, this, mLoaderCallback);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if(mOpenCvCameraView != null){
            mOpenCvCameraView.disableView();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if(mOpenCvCameraView != null){
            mOpenCvCameraView.disableView();
        }
    }

    @Override
    public void onCameraViewStarted(int width, int height) {
        mRgba = new Mat(height, width, CvType.CV_8UC4);
        mGray = new Mat(height, width, CvType.CV_8UC1);
    }

    @Override
    public void onCameraViewStopped() {
        mRgba.release();
    }

    @Override
    public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame) {
        mRgba = inputFrame.rgba();
        mGray = inputFrame.gray();

        // pass input as mRgba
        mRgba = face_recognition.recognizeImage(mRgba);
        // output is saved in m Rgba
        // we will show returned mRgba to screen

        return mRgba;
    }
}