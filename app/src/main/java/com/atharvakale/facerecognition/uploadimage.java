package com.atharvakale.facerecognition;

import static com.atharvakale.facerecognition.MainActivity.registered;

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.util.Pair;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.tasks.OnSuccessListener;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceDetection;
import com.google.mlkit.vision.face.FaceDetector;
import com.google.mlkit.vision.face.FaceDetectorOptions;

import org.tensorflow.lite.Interpreter;

import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
//import com.atharvakale.facerecognition.MainActivity.reg

public class uploadimage extends AppCompatActivity {

    int inputSize = 112;
    int[] intValues;
    boolean isModelQuantized=false;

    float IMAGE_MEAN = 128.0f;
    float IMAGE_STD = 128.0f;
    float[][] embeedings;
    boolean developerMode=false;
    float distance= 1.0f;
    private Button choosen,upload;
    private ImageView imageView;
    private TextView resutl;
    private Uri profileUri;
    private Context context=uploadimage.this;
    boolean start=true, flipX=false;

    private static final int MY_CAMERA_REQUEST_CODE = 100;

    private static int SELECT_PICTURE = 1;
    private Interpreter tfLite;
    private FaceDetector detector;
    private final String modelFile="mobile_face_net.tflite"; //model name
    int OUTPUT_SIZE = 192;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_uploadimge);
        AnhXa();
        clicks();

        //Load model
        try {
            tfLite = new Interpreter(loadModelFile(uploadimage.this, modelFile));
        } catch (IOException e) {
            e.printStackTrace();
        }
        //Initialize Face Detector
        FaceDetectorOptions highAccuracyOpts =
                new FaceDetectorOptions.Builder()
                        .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                        .build();
        detector = FaceDetection.getClient(highAccuracyOpts);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == SELECT_PICTURE && resultCode == RESULT_OK && data !=null) {
            profileUri = data.getData();
            imageView.setImageURI(profileUri);
            imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);

            try {

                Bitmap takenImage = getResizedBitmap(getBitmapFromUri(profileUri), 112, 112);
                takenImage = rotateBitmap(takenImage, 0, flipX, false);
                Bitmap scale = getResizedBitmap(takenImage, 112, 112);
                recognizeImage(scale);

            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }
    private static Bitmap rotateBitmap(
            Bitmap bitmap, int rotationDegrees, boolean flipX, boolean flipY) {
        Matrix matrix = new Matrix();

        // Rotate the image back to straight.
        matrix.postRotate(rotationDegrees);

        // Mirror the image along the X or Y axis.
        matrix.postScale(flipX ? -1.0f : 1.0f, flipY ? -1.0f : 1.0f);
        Bitmap rotatedBitmap =
                Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);

        // Recycle the old bitmap if it has changed.
        if (rotatedBitmap != bitmap) {
            bitmap.recycle();
        }
        return rotatedBitmap;
    }
    public void recognizeImage(final Bitmap bitmap) {

        //Create ByteBuffer to store normalized image
        ByteBuffer imgData = ByteBuffer.allocateDirect(1 * inputSize * inputSize * 3 * 4);

        imgData.order(ByteOrder.nativeOrder());

        intValues = new int[inputSize * inputSize];

        //get pixel values from Bitmap to normalize
        bitmap.getPixels(intValues, 0, bitmap.getWidth(), 0, 0, bitmap.getWidth(), bitmap.getHeight());

        imgData.rewind();

        for (int i = 0; i < inputSize; ++i) {
            for (int j = 0; j < inputSize; ++j) {
                int pixelValue = intValues[i * inputSize + j];
                if (isModelQuantized) {
                    // Quantized model
                    imgData.put((byte) ((pixelValue >> 16) & 0xFF));
                    imgData.put((byte) ((pixelValue >> 8) & 0xFF));
                    imgData.put((byte) (pixelValue & 0xFF));
                } else { // Float model
                    imgData.putFloat((((pixelValue >> 16) & 0xFF) - IMAGE_MEAN) / IMAGE_STD);
                    imgData.putFloat((((pixelValue >> 8) & 0xFF) - IMAGE_MEAN) / IMAGE_STD);
                    imgData.putFloat(((pixelValue & 0xFF) - IMAGE_MEAN) / IMAGE_STD);

                }
            }
        }

        //imgData is input to our model
        Object[] inputArray = {imgData};

        Map<Integer, Object> outputMap = new HashMap<>();


        embeedings = new float[1][OUTPUT_SIZE]; //output of model will be stored in this variable

        outputMap.put(0, embeedings);

        tfLite.runForMultipleInputsOutputs(inputArray, outputMap); //Run model

        float distance_local = Float.MAX_VALUE;
        String id = "0";
        String label = "?";
        //Compare new face with saved Faces.
        if (registered.size() > 0) {

            final List<Pair<String, Float>> nearest = findNearest(embeedings[0]);//Find 2 closest matching face

            if (nearest.get(0) != null) {

                final String name = nearest.get(0).first; //get name and distance of closest matching face
                distance_local = nearest.get(0).second;
                System.out.println("nearest.get(0).second: " + nearest.get(0).second);

                if (distance_local < distance) {//If distance between Closest found face is more than 1.000 ,then output UNKNOWN face.
                    resutl.setText(name + " full");
                    System.out.println("name: " + name);
                }
                else
                    resutl.setText("Unknown");
            }
        }
    }
    private List<Pair<String, Float>> findNearest(float[] emb) {
        List<Pair<String, Float>> neighbour_list = new ArrayList<Pair<String, Float>>();
        Pair<String, Float> ret = null; //to get closest match
        Pair<String, Float> prev_ret = null; //to get second closest match
        for (Map.Entry<String, SimilarityClassifier.Recognition> entry : registered.entrySet())
        {

            final String name = entry.getKey();
            final float[] knownEmb = ((float[][]) entry.getValue().getExtra())[0];

            float distance = 0;
            for (int i = 0; i < emb.length; i++) {
                float diff = emb[i] - knownEmb[i];
                distance += diff*diff;
            }
            distance = (float) Math.sqrt(distance);
            if (ret == null || distance < ret.second) {
                prev_ret=ret;
                ret = new Pair<>(name, distance);
            }
        }
        if(prev_ret==null) prev_ret=ret;
        neighbour_list.add(ret);
        neighbour_list.add(prev_ret);

        return neighbour_list;

    }
    public Bitmap getResizedBitmap(Bitmap bm, int newWidth, int newHeight) {
        int width = bm.getWidth();
        int height = bm.getHeight();
        float scaleWidth = ((float) newWidth) / width;
        float scaleHeight = ((float) newHeight) / height;
        // CREATE A MATRIX FOR THE MANIPULATION
        Matrix matrix = new Matrix();
        // RESIZE THE BIT MAP
        matrix.postScale(scaleWidth, scaleHeight);

        // "RECREATE" THE NEW BITMAP
        Bitmap resizedBitmap = Bitmap.createBitmap(
                bm, 0, 0, width, height, matrix, false);
        bm.recycle();
        return resizedBitmap;
    }

    private void AnhXa() {
        choosen = findViewById(R.id.button3);
        upload = findViewById(R.id.button2);
        imageView = findViewById(R.id.imageView);
        resutl = findViewById(R.id.textView);

    }
    private void clicks(){

        choosen.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                Intent intent=new Intent(Intent.ACTION_PICK);
                intent.setType("image/*");
                Toast.makeText(uploadimage.this, "Choosen Image ", Toast.LENGTH_SHORT).show();

                startActivityForResult(intent,1);
            }
        });
        upload.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                AlertDialog.Builder builder = new AlertDialog.Builder(context);
                builder.setTitle("Select Action:");

                // add a checkbox list
                String[] names= {"View Recognition List","Update Recognition List","Save Recognitions","Load Recognitions","Clear All Recognitions","Import Photo (Beta)"};

                builder.setItems(names, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {

                        switch (which)
                        {
                            case 0:
                                displaynameListview();
                                break;
                            case 1:
                                updatenameListview();
                                break;
                            case 2:
                                insertToSP(registered,0); //mode: 0:save all, 1:clear all, 2:update all
                                break;
                            case 3:
                                registered.putAll(readFromSP());
                                break;
                            case 4:
                                clearnameList();
                                break;
                            case 5:
                                loadphoto();
                                break;
                        }

                    }
                });

                builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {

                    }
                });
                builder.setNegativeButton("Cancel", null);

                // create and show the alert dialog
                AlertDialog dialog = builder.create();
                dialog.show();
            }
        });
    }

    private void displaynameListview()
    {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        // System.out.println("Registered"+registered);
        if(registered.isEmpty())
            builder.setTitle("No Faces Added!!");
        else
            builder.setTitle("Recognitions:");

        // add a checkbox list
        String[] names= new String[registered.size()];
        boolean[] checkedItems = new boolean[registered.size()];
        int i=0;
        for (Map.Entry<String, SimilarityClassifier.Recognition> entry : registered.entrySet())
        {
            //System.out.println("NAME"+entry.getKey());
            names[i]=entry.getKey();
            checkedItems[i]=false;
            i=i+1;

        }
        builder.setItems(names,null);

        builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {

            }
        });

        // create and show the alert dialog
        AlertDialog dialog = builder.create();
        dialog.show();
    }
    private void updatenameListview()
    {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        if(registered.isEmpty()) {
            builder.setTitle("No Faces Added!!");
            builder.setPositiveButton("OK",null);
        }
        else{
            builder.setTitle("Select Recognition to delete:");

            // add a checkbox list
            String[] names= new String[registered.size()];
            boolean[] checkedItems = new boolean[registered.size()];
            int i=0;
            for (Map.Entry<String, SimilarityClassifier.Recognition> entry : registered.entrySet())
            {
                //System.out.println("NAME"+entry.getKey());
                names[i]=entry.getKey();
                checkedItems[i]=false;
                i=i+1;

            }

            builder.setMultiChoiceItems(names, checkedItems, new DialogInterface.OnMultiChoiceClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which, boolean isChecked) {
                    // user checked or unchecked a box
                    //Toast.makeText(MainActivity.this, names[which], Toast.LENGTH_SHORT).show();
                    checkedItems[which]=isChecked;

                }
            });

            builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {

                    // System.out.println("status:"+ Arrays.toString(checkedItems));
                    for(int i=0;i<checkedItems.length;i++)
                    {
                        //System.out.println("status:"+checkedItems[i]);
                        if(checkedItems[i])
                        {
//                                Toast.makeText(MainActivity.this, names[i], Toast.LENGTH_SHORT).show();
                            registered.remove(names[i]);
                        }

                    }
                    insertToSP(registered,2); //mode: 0:save all, 1:clear all, 2:update all
                    Toast.makeText(context, "Recognitions Updated", Toast.LENGTH_SHORT).show();
                }
            });
            builder.setNegativeButton("Cancel", null);

            // create and show the alert dialog
            AlertDialog dialog = builder.create();
            dialog.show();
        }
    }
    private HashMap<String, SimilarityClassifier.Recognition> readFromSP(){
        SharedPreferences sharedPreferences = getSharedPreferences("HashMap", MODE_PRIVATE);
        String defValue = new Gson().toJson(new HashMap<String, SimilarityClassifier.Recognition>());
        String json=sharedPreferences.getString("map",defValue);
        TypeToken<HashMap<String,SimilarityClassifier.Recognition>> token = new TypeToken<HashMap<String,SimilarityClassifier.Recognition>>() {};
        HashMap<String,SimilarityClassifier.Recognition> retrievedMap=new Gson().fromJson(json,token.getType());
        for (Map.Entry<String, SimilarityClassifier.Recognition> entry : retrievedMap.entrySet())
        {
            float[][] output=new float[1][OUTPUT_SIZE];
            ArrayList arrayList= (ArrayList) entry.getValue().getExtra();
            arrayList = (ArrayList) arrayList.get(0);
            for (int counter = 0; counter < arrayList.size(); counter++) {
                output[0][counter] = ((Double) arrayList.get(counter)).floatValue();
            }
            entry.getValue().setExtra(output);

            //System.out.println("Entry output "+entry.getKey()+" "+entry.getValue().getExtra() );

        }
//        System.out.println("OUTPUT"+ Arrays.deepToString(outut));
        Toast.makeText(context, "Recognitions Loaded", Toast.LENGTH_SHORT).show();
        return retrievedMap;
    }

    private void insertToSP(HashMap<String, SimilarityClassifier.Recognition> jsonMap,int mode) {
        if(mode==1)  //mode: 0:save all, 1:clear all, 2:update all
            jsonMap.clear();
        else if (mode==0)
            jsonMap.putAll(readFromSP());
        String jsonString = new Gson().toJson(jsonMap);
        SharedPreferences sharedPreferences = getSharedPreferences("HashMap", MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString("map", jsonString);
        //System.out.println("Input josn"+jsonString.toString());
        editor.apply();
        Toast.makeText(context, "Recognitions Saved", Toast.LENGTH_SHORT).show();
    }
    private  void clearnameList()
    {
        AlertDialog.Builder builder =new AlertDialog.Builder(context);
        builder.setTitle("Do you want to delete all Recognitions?");
        builder.setPositiveButton("Delete All", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                registered.clear();
                Toast.makeText(context, "Recognitions Cleared", Toast.LENGTH_SHORT).show();
            }
        });
        insertToSP(registered,1);
        builder.setNegativeButton("Cancel",null);
        AlertDialog dialog = builder.create();
        dialog.show();
    }
    private void loadphoto()
    {
        start=false;
        Intent intent = new Intent();
        intent.setType("image/*");
        intent.setAction(Intent.ACTION_GET_CONTENT);
        startActivityForResult(Intent.createChooser(intent, "Select Picture"), SELECT_PICTURE);
    }
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == MY_CAMERA_REQUEST_CODE) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "camera permission granted", Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(this, "camera permission denied", Toast.LENGTH_LONG).show();
            }
        }
    }

    private MappedByteBuffer loadModelFile(Activity activity, String MODEL_FILE) throws IOException {
        AssetFileDescriptor fileDescriptor = activity.getAssets().openFd(MODEL_FILE);
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();
        long startOffset = fileDescriptor.getStartOffset();
        long declaredLength = fileDescriptor.getDeclaredLength();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }
    private Bitmap getBitmapFromUri(Uri uri) throws IOException {
        ParcelFileDescriptor parcelFileDescriptor =
                getContentResolver().openFileDescriptor(uri, "r");
        FileDescriptor fileDescriptor = parcelFileDescriptor.getFileDescriptor();
        Bitmap image = BitmapFactory.decodeFileDescriptor(fileDescriptor);
        parcelFileDescriptor.close();
        return image;
    }

}
