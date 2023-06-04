package com.atharvakale.facerecognition;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.util.Log;

import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.MatOfRect;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import org.opencv.objdetect.CascadeClassifier;
import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.gpu.GpuDelegate;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Array;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;

public class Face_Recognition {
    // First add implementation of TensorFlow
    // import interpreter
    private Interpreter interpreter;
    // define input size of model
    private  int INPUT_SIZE;
    // define height and width of frame
    private  int height = 0;
    private  int width = 0;
    // define Gpu delegate
    //this is used to run model using GPU
    private GpuDelegate gpuDelegate = null;
    // define Classifier
    private CascadeClassifier cascadeClassifier;

    Face_Recognition(AssetManager assetManager, Context context, String modelPath, int input_size) throws IOException {
        INPUT_SIZE = input_size;

        // Set GPU for the Interpreter
        Interpreter.Options options = new Interpreter.Options();
        gpuDelegate = new GpuDelegate();

        // Optional
        // use below code if using MobileNet.tflite
        // you can delete below code if using EfficientNet.tflite
        //options.addDelegate(gpuDelegate);

        // before load model add number of threads
        options.setNumThreads(4);

        // load model
        interpreter = new Interpreter(loadModel(assetManager, modelPath), options);
        Log.d("face_Recognition", "Model is loaded");

        // now we will load haar cascade model
        try
        {
            // define input stream to read haar cascade file
            InputStream inputStream = context.getResources().openRawResource(R.raw.haarcascade_frontalface_alt);
            // create a new folder to save classifier
            File cascadeDir = context.getDir("cascade", Context.MODE_PRIVATE);
            // new create a new cascade file in that folder
            File mCascadeFile = new File(cascadeDir, "haarcascade_frontalface_alt");
            // now de define output stream to save haarcascade_frontalface_alt in mCascadeFile
            FileOutputStream outputStream = new FileOutputStream(mCascadeFile);
            // create empty byte buffer to store byte
            byte[] buffer = new byte[4096];
            int byteRead;
            // read byte in loop
            // -1 of no data to read
            while ((byteRead = inputStream.read(buffer)) != -1){
                outputStream.write(buffer, 0, byteRead);
            }
            // when reading file is completed
            inputStream.close();
            outputStream.close();

            // load cascade classifier
            //                                          path of save file
            cascadeClassifier = new CascadeClassifier(mCascadeFile.getAbsolutePath());
            Log.d("face_recognition", "Classifier is loaded");
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
    }

    public Mat recognizeImage(Mat mat_image) {
        // call this function on onCameraFrame of CameraActivity
        Mat a = mat_image.t();
        Core.flip(a,mat_image, 1);
        a.release();

        // convert mat_image to grayscale
        Mat grayscaleImage = new Mat();
        //               input      output          type
        Imgproc.cvtColor(mat_image, grayscaleImage, Imgproc.COLOR_RGBA2GRAY);
        // define height and width
        height = grayscaleImage.height();
        width = grayscaleImage.width();
        // new define minimum height and width of face in frame
        // below this height and width will be neglected
        int absoluteFaceSize = (int) (height*0.1);
        MatOfRect faces = new MatOfRect();
        // this will store all faces

        if(cascadeClassifier != null)
        {
            // detech face in frame
            //                                  input           output      scale of face
            cascadeClassifier.detectMultiScale(grayscaleImage, faces, 1.1, 2, 2,
                    new Size(absoluteFaceSize, absoluteFaceSize), new Size());
            // minimum size of face
        }
        // now convert faces to arary
        Rect[] faceArray = faces.toArray();
        // loop through each faces
        for (int i = 0; i < faceArray.length; i++)
        {
            // draw rectangle around faces
            //              input->output // start point     // end point        color
            Imgproc.rectangle(mat_image, faceArray[i].tl(), faceArray[i].br(), new Scalar(0,255,0,255), 2);

            //                  starting x coordinate     starting y coordinate
            Rect roi = new Rect((int)faceArray[i].tl().x, (int)faceArray[i].tl().y,
                    ((int)faceArray[i].br().x) - ((int)faceArray[i].tl().x),
                    ((int)faceArray[i].br().y) - ((int)faceArray[i].tl().y));
            // roi is used to crop faces from image
            Mat cropped_rgb = new Mat(mat_image, roi);

            // convert cropped_rgb to bitmap
            Bitmap bitmap = null;
            bitmap = Bitmap.createBitmap(cropped_rgb.cols(), cropped_rgb.rows(), Bitmap.Config.ARGB_8888);
            Utils.matToBitmap(cropped_rgb, bitmap);

            // new scale bitmap to model input size 96 or 64
            Bitmap scaledBitmap = Bitmap.createScaledBitmap(bitmap, INPUT_SIZE, INPUT_SIZE, false);
            // now convert scaledBitmap to byteBuffer
            ByteBuffer byteBuffer = convertBitmapToByteBuffer(scaledBitmap);

            // create output
            float[][] face_value = new float[1][1];
            interpreter.run(byteBuffer, face_value);

            Log.d("face_Recognition", "Out: " + Array.get(Array.get(face_value, 0), 0));

            // now we will read face_value
            float read_face = (float) Array.get(Array.get(face_value, 0), 0);
            // now create a new function input as read_face and output as name
            String face_name = get_face_name(read_face);

            // now we will put text on frame
            Imgproc.putText(mat_image, "" + face_name,
                    new Point((int)faceArray[i].tl().x + 10, (int)faceArray[i].tl().y + 20),
                    1, 1.5, new Scalar(255,255,255,150), 2);
            // size
            // It is starting point of face
        }

        Mat b = mat_image.t();
        Core.flip(b, mat_image, 0);
        b.release();

        // before returning rotate it back by -90 degree
        return mat_image;
    }

    private String get_face_name(float read_face) {
        String val = "";

        if(read_face >= 0 && read_face < 0.5){
            val = "Courteney Cox";
        }
        else if(read_face >= 0.5 & read_face < 1.5) {
            val = "Arnold Schwarzenegger";
        }
        else if(read_face >= 1.5 & read_face < 2.5) {
            val = "Bhuvan Bam";
        }
        else if(read_face >= 2.5 & read_face < 3.5) {
            val = "Hardik Pandya";
        }
        else if(read_face >= 3.5 & read_face < 4.5) {
            val = "David Schwimmer";
        }
        else if(read_face >= 4.5 & read_face < 5.5) {
            val = "Matt LeBlanc";
        }
        else if(read_face >= 5.5 & read_face < 6.5) {
            val = "Simon Helberg";
        }
        else if(read_face >= 6.5 & read_face < 7.5) {
            val = "Scarlett Johansson";
        }
        else if(read_face >= 7.5 & read_face < 8.5) {
            val = "Pankaj Tripathi";
        }
        else if(read_face >= 8.5 & read_face < 9.5) {
            val = "Matthew Perry";
        }
        else if(read_face >= 9.5 & read_face < 10.5) {
            val = "Sylvester Stallone";
        }
        else if(read_face >= 10.5 & read_face < 11.5) {
            val = "Messi";
        }
        else if(read_face >= 11.5 & read_face < 12.5) {
            val = "Jim Parsons";
        }
        else if(read_face >= 12.5 & read_face < 13.5) {
            val = "Not in dataset";
        }
        else if(read_face >= 13.5 & read_face < 14.5) {
            val = "Lisa Kudrow";
        }
        else if(read_face >= 14.5 & read_face < 15.5) {
            val = "Mohamed Ali";
        }
        else if(read_face >= 15.5 & read_face < 16.5) {
            val = "Brad_Pitt";
        }
        else if(read_face >= 16.5 & read_face < 17.5) {
            val = "Ronaldo";
        }
        else if(read_face >= 17.5 & read_face < 18.5) {
            val = "Virat Kohli";
        }
        else if(read_face >= 18.5 & read_face < 19.5) {
            val = "Angelina Jolie";
        }
        else if(read_face >= 19.5 & read_face < 20.5) {
            val = "KulnalNayya";
        }
        else if(read_face >= 20.5 & read_face < 21.5) {
            val = "Manoj Bajpayee";
        }
        else if(read_face >= 21.5 & read_face < 22.5) {
            val = "Sachin Tendulka";
        }
        else if(read_face >= 22.5 & read_face < 23.5) {
            val = "Jennifer Aniston";
        }
        else if(read_face >= 23.5 & read_face < 24.5) {
            val = "Dhoni";
        }
        else if(read_face >= 24.5 & read_face < 25.5) {
            val = "Aishwarya Rai";
        }
        else if(read_face >= 25.5 & read_face < 26.5) {
            val = "Johnny Galeck";
        }
        else if(read_face >= 26.5 & read_face < 27.5) {
            val = "Rohit Galech";
        }
        else if(read_face >= 27.5 & read_face < 28.5) {
            val = "Suresh Raina";
        }
        else {
            val = "Suresh Raina";
        }
        return val;
    }

    private ByteBuffer convertBitmapToByteBuffer(Bitmap scaledBitmap) {
        // define ByteBuffer
        ByteBuffer byteBuffer;
        // define input size
        int input_size = INPUT_SIZE;

        // multiply by 4 if input of model is float
        // multiply by 3 if input is RGB
        // if input is GRAY 3->1
        byteBuffer = ByteBuffer.allocateDirect(4*1*input_size*input_size*3);
        byteBuffer.order(ByteOrder.nativeOrder());
        int[] intValues = new int[input_size*input_size];
        scaledBitmap.getPixels(intValues, 0, scaledBitmap.getWidth(), 0, 0,
                scaledBitmap.getWidth(),
                scaledBitmap.getHeight());
        int pixels = 0;
        // loop through each pixels
        for (int i = 0; i < input_size; ++i){
            for (int j = 0; j < input_size; ++j){
                // each pixels value
                final int val = intValues[pixels++];
                // now put this pixel value in byte buffer
                byteBuffer.putFloat((( (val>>16) & 0xFF) )/255.0f);
                byteBuffer.putFloat((( (val>>8) & 0xFF) )/255.0f);
                byteBuffer.putFloat(( (val & 0xFF) )/255.0f);
                // this thing is important
                // it isi placing RGB to MSB to LSB

                // scaling pixels from 0-255 to 0-1
            }
        }
        return byteBuffer;
    }

    private MappedByteBuffer loadModel(AssetManager assetManager, String modelPath) {
        try
        {
            // This will give description of modelPath
            AssetFileDescriptor assetFileDescriptor = assetManager.openFd(modelPath);
            // Create a input stream to model path
            FileInputStream inputStream = new FileInputStream(assetFileDescriptor.getFileDescriptor());
            FileChannel fileChannel = inputStream.getChannel();
            long startOffset = assetFileDescriptor.getStartOffset();
            long declaredLength = assetFileDescriptor.getDeclaredLength();
            return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
        return null;
    }
}
