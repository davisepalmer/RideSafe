package com.davisepalmer.ridesafe;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
//import android.support.v4.content.ContextCompat;

import android.Manifest;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.PersistableBundle;
import android.provider.Settings;
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;
import android.app.Dialog;


import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.io.OutputStream;
import android.net.Uri;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.MultipartBody;
import androidx.core.content.ContextCompat;

import android.content.res.ColorStateList;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.karumi.dexter.Dexter;

import com.karumi.dexter.PermissionToken;
import com.karumi.dexter.listener.PermissionDeniedResponse;
import com.karumi.dexter.listener.PermissionGrantedResponse;
import com.karumi.dexter.listener.PermissionRequest;
import com.karumi.dexter.listener.single.PermissionListener;
import com.google.android.gms.maps.MapView;


import java.io.FileInputStream;

public class MainActivity extends AppCompatActivity implements OnMapReadyCallback {
    private static String URL_TO_SERVER = "https://mintaio.com/driving";
//    public static String URL_TO_SERVER = "https://www.toptal.com/developers/postbin/1708836087562-3955015260726";
    private static final String TAG = "AndroidCameraApi";
    private Button driveBtn;
    private TextureView textureView;
    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();
    private boolean isDriving = false;
    private Handler mCaptureHandler;
    private HandlerThread mCaptureThread;
    private static final long CAPTURE_INTERVAL = 10 * 1000;

    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 90);
        ORIENTATIONS.append(Surface.ROTATION_90, 0);
        ORIENTATIONS.append(Surface.ROTATION_180, 270);
        ORIENTATIONS.append(Surface.ROTATION_270, 180);
    }

    private String cameraId;
    protected CameraDevice cameraDevice;
    protected CameraCaptureSession cameraCaptureSessions;
    protected CaptureRequest.Builder captureRequestBuilder;
    private Size imageDimension;
//    private ImageReader imageReader;
    private File file;
    private File folder;
    private String folderName = "MyPhotoDir";
    private static final int REQUEST_CAMERA_PERMISSION = 200;
    private Handler mBackgroundHandler;
    private HandlerThread mBackgroundThread;
    boolean isPermissionGranted;
    MapView mapView;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        textureView = findViewById(R.id.texture);
        mapView=findViewById(R.id.mapView);
        if(textureView != null)
            textureView.setSurfaceTextureListener(textureListener);
    checkPermission();
    if (isPermissionGranted) {
        if(checkGooglePlayServices()) {
            Toast.makeText(this, "Google Play Services Available.", Toast.LENGTH_SHORT).show();
            mapView.getMapAsync(this);
            mapView.onCreate(savedInstanceState);
        }else {
            Toast.makeText(this, "Google Play Services NOT Avaliable", Toast.LENGTH_SHORT).show();
        }
    }
        driveBtn = findViewById(R.id.driveBtn);
        if(driveBtn != null)
            driveBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    if (!isDriving) {
                        startDrive();
                    } else {
                        endDrive();
                    }
                }
            });

    }

    private boolean checkGooglePlayServices() {
        GoogleApiAvailability googleApiAvailability = GoogleApiAvailability.getInstance();
        int result = googleApiAvailability.isGooglePlayServicesAvailable(this);
        if(result == ConnectionResult.SUCCESS) {
            return true;
        }
        else if (googleApiAvailability.isUserResolvableError(result)) {
            Dialog dialog=googleApiAvailability.getErrorDialog(this, result, 201, new DialogInterface.OnCancelListener() {
                @Override
                public void onCancel(DialogInterface dialogInterface) {
                    Toast.makeText(MainActivity.this, "User canceled dialog", Toast.LENGTH_SHORT).show();
                }
            });
            dialog.show();
        }
        return false;
    }

    private void checkPermission() {
        Dexter.withContext(this).withPermission(Manifest.permission.ACCESS_FINE_LOCATION).withListener(new PermissionListener() {
            @Override
            public void onPermissionGranted(PermissionGrantedResponse permissionGrantedResponse) {
                isPermissionGranted = true;
                //Toast?
            }

            @Override
            public void onPermissionDenied(PermissionDeniedResponse permissionDeniedResponse) {
                Intent intent = new Intent();
                intent.setAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                Uri uri = Uri.fromParts("package",getPackageResourcePath(), "");
                intent.setData(uri);
                startActivity(intent);
            }

            @Override
            public void onPermissionRationaleShouldBeShown(PermissionRequest permissionRequest, PermissionToken permissionToken) {
                permissionToken.continuePermissionRequest();
            }
        }).check();
    }

    private void startDrive() {
        isDriving = true;
        driveBtn.setText("End Drive");
        // Set the background tint to red
        int redColor = ContextCompat.getColor(this, R.color.red_color);
        driveBtn.setBackgroundTintList(ColorStateList.valueOf(redColor));
        startCaptureThread();
    }

    private void endDrive() {
        isDriving = false;
        driveBtn.setText("Start Drive");
        // Set the background tint to green
        int greenColor = ContextCompat.getColor(this, R.color.green_color);
        driveBtn.setBackgroundTintList(ColorStateList.valueOf(greenColor));
        stopCaptureThread();
    }



    private void startCaptureThread() {

        mCaptureThread = new HandlerThread("CaptureThread");
        mCaptureThread.start();
        mCaptureHandler = new Handler(mCaptureThread.getLooper());

        mCaptureHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                takePicture(); // Capture the image
                mCaptureHandler.postDelayed(this, CAPTURE_INTERVAL); // Schedule the next capture
            }
        }, CAPTURE_INTERVAL);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopCaptureThread();
        mapView.onDestroy();

    }

    private void stopCaptureThread() {
        if (mCaptureThread != null) {
            mCaptureThread.quitSafely();
            try {
                mCaptureThread.join();
                mCaptureThread = null;
                mCaptureHandler = null;
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    TextureView.SurfaceTextureListener textureListener = new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
            // Open your camera here
            openCamera();
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
            // Transform you image captured size according to the surface width and height
        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
            return false;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surface) {
        }
    };

    private final CameraDevice.StateCallback stateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(CameraDevice camera) {
            // This is called when the camera is open
            Log.e(TAG, "onOpened");
            cameraDevice = camera;
        }

        @Override
        public void onDisconnected(CameraDevice camera) {
            cameraDevice.close();
        }

        @Override
        public void onError(CameraDevice camera, int error) {
            cameraDevice.close();
            cameraDevice = null;
        }
    };

    protected void startBackgroundThread() {
        mBackgroundThread = new HandlerThread("Camera Background");
        mBackgroundThread.start();
        mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
    }

    protected void stopBackgroundThread() {
        mBackgroundThread.quitSafely();
        try {
            mBackgroundThread.join();
            mBackgroundThread = null;
            mBackgroundHandler = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private String currentPhotoPath;
protected void takePicture() {
    if (cameraDevice == null) {
        Log.e(TAG, "cameraDevice is null");
        return;
    }
    if (!isExternalStorageAvailableForRW() || isExternalStorageReadOnly()) {
        driveBtn.setEnabled(false);
    }
    if (isStoragePermissionGranted()) {
        CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraDevice.getId());
            Size[] jpegSizes = null;
            if (characteristics != null) {
                jpegSizes = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP).getOutputSizes(ImageFormat.JPEG);
            }
            int width = 640;
            int height = 480;
            if (jpegSizes != null && jpegSizes.length > 0) {
                width = jpegSizes[0].getWidth();
                height = jpegSizes[0].getHeight();
            }
            ImageReader reader = ImageReader.newInstance(width, height, ImageFormat.JPEG, 1);
            List<Surface> outputSurfaces = new ArrayList<Surface>(2);
            outputSurfaces.add(reader.getSurface());
            outputSurfaces.add(new Surface(textureView.getSurfaceTexture()));
            final CaptureRequest.Builder captureBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            captureBuilder.addTarget(reader.getSurface());
            captureBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
            // Orientation
            int rotation = getWindowManager().getDefaultDisplay().getRotation();
            captureBuilder.set(CaptureRequest.JPEG_ORIENTATION, ORIENTATIONS.get(rotation));
            file = null;
            folder = new File(folderName);
            String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
            String imageFileName = "IMG_" + timeStamp + ".jpg";
            file = new File(getExternalFilesDir(folderName), "/" + imageFileName);
            currentPhotoPath = file.getAbsolutePath(); // Set currentPhotoPath here
            if (!folder.exists()) {
                folder.mkdirs();
            }
            ImageReader.OnImageAvailableListener readerListener = new ImageReader.OnImageAvailableListener() {
                @Override
                public void onImageAvailable(ImageReader reader) {
                    Image image = null;
                    try {
                        image = reader.acquireLatestImage();
                        ByteBuffer buffer = image.getPlanes()[0].getBuffer();
                        byte[] bytes = new byte[buffer.capacity()];
                        buffer.get(bytes);
                        save(bytes);
                    } catch (FileNotFoundException e) {
                        e.printStackTrace();
                    } catch (IOException e) {
                        e.printStackTrace();
                    } finally {
                        if (image != null) {
                            image.close();
                        }
                    }
                }

                private void save(byte[] bytes) throws IOException {
                    OutputStream output = null;
                    try {
                        output = new FileOutputStream(file);
                        output.write(bytes);
                        // After saving the image, send it to the server
                        sendImageToServer(file);
                    } finally {
                        if (null != output) {
                            output.close();
                        }
                    }
                }
            };
            reader.setOnImageAvailableListener(readerListener, mBackgroundHandler);
            final CameraCaptureSession.CaptureCallback captureListener = new CameraCaptureSession.CaptureCallback() {
                @Override
                public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request, TotalCaptureResult result) {
                    super.onCaptureCompleted(session, request, result);
                    Toast.makeText(MainActivity.this, "Saved:" + file, Toast.LENGTH_SHORT).show();
                    Log.d(TAG, "" + file);
                }
            };
            cameraDevice.createCaptureSession(outputSurfaces, new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(CameraCaptureSession session) {
                    try {
                        session.capture(captureBuilder.build(), captureListener, mBackgroundHandler);
                    } catch (CameraAccessException e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void onConfigureFailed(CameraCaptureSession session) {
                }
            }, mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }
}


    private String convertImageToBase64(File imageFile) {
        try {
            FileInputStream fis = new FileInputStream(imageFile);
            byte[] imageBytes = new byte[(int) imageFile.length()];
            fis.read(imageBytes);
            fis.close();
            String base64Image = android.util.Base64.encodeToString(imageBytes, android.util.Base64.DEFAULT);
            return "data:image/jpeg;base64," + base64Image;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    private void sendImageToServer(File imageFile) {
        OkHttpClient client = new OkHttpClient();
        RequestBody fileBody = RequestBody.create(imageFile, MediaType.parse("image/jpeg"));
        MultipartBody requestBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("myFile", imageFile.getName(), fileBody)
                .build();

        // Create the request
        Request request = new Request.Builder()
                .url(URL_TO_SERVER)
                .addHeader("coord", "30.616491, -96.320812")
                .post(requestBody)
                .build();

        try {
            // Execute the request
            Response response = client.newCall(request).execute();
            if (response.isSuccessful()) {
                String responseData = response.body().string();
                // Do something with the response data
                Log.d(TAG, "Response: " + responseData);
            } else {
                // Handle error
                Log.e(TAG, "Request failed: " + response.code() + " - " + response.message());
            }
            // Close the response body
            if (response.body() != null) {
                response.body().close();
            }
        } catch (IOException e) {
            e.printStackTrace();
            // Handle failure
            Log.e(TAG, "Error sending image to server: " + e.getMessage());
        }
    }



//    @Override
//    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
//        super.onActivityResult(requestCode, resultCode, data);
//        if (requestCode == CAMERA_REQUEST_CODE) {
//            if (resultCode == Activity.RESULT_OK) {
//                // Your code to handle the result
//            }
//        }
//    }



    private static boolean isExternalStorageReadOnly() {
        String extStorageState = Environment.getExternalStorageState();
        if (Environment.MEDIA_MOUNTED_READ_ONLY.equals(extStorageState)) {
            return true;
        }
        return false;
    }

    public static void sendDummyPostRequest() {
        OkHttpClient client = new OkHttpClient();

        // Create a JSON request body with an empty object
        MediaType mediaType = MediaType.parse("application/json");
        RequestBody body = RequestBody.create(mediaType, "{}");

        // Create the request
        Request request = new Request.Builder()
                .url(URL_TO_SERVER)
                .post(body)
                .build();

        // Execute the request asynchronously
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                e.printStackTrace();
                // Handle failure
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                // Handle success
                if (response.isSuccessful()) {
                    String responseData = response.body().string();
                    System.out.println("Response: " + responseData);
                } else {
                    // Handle error
                    System.err.println("Request failed: " + response.code() + " - " + response.message());
                }
                // Close the response body
                response.close();
            }
        });
    }

    public static void main(String[] args) {
        sendDummyPostRequest();
    }
    private boolean isExternalStorageAvailableForRW() {
        String extStorageState = Environment.getExternalStorageState();
        if (extStorageState.equals(Environment.MEDIA_MOUNTED)) {
            return true;
        }
        return false;
    }

    private boolean isStoragePermissionGranted() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    == PackageManager.PERMISSION_GRANTED) {
                // Permission is granted
                return true;
            } else {
                //Permission is revoked
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);
                return false;
            }
        } else {
            // Permission is automatically granted on sdk<23 upon installation
            return true;
        }
    }

    private void openCamera() {
        CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        Log.e(TAG, "is camera open");
        try {
            cameraId = manager.getCameraIdList()[0];
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
            StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            assert map != null;
            imageDimension = map.getOutputSizes(SurfaceTexture.class)[0];
            // Add permission for camera and let user grant the permission
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_CAMERA_PERMISSION);
                return;
            }
            manager.openCamera(cameraId, stateCallback, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
        Log.e(TAG, "openCamera X");
    }

    protected void updatePreview() {
        if (null == cameraDevice) {
            Log.e(TAG, "updatePreview error, return");
        }
        captureRequestBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
        try {
            cameraCaptureSessions.setRepeatingRequest(captureRequestBuilder.build(), null, mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults[0] == PackageManager.PERMISSION_DENIED) {
                // close the app
                Toast.makeText(MainActivity.this, "Sorry!!!, you can't use this app without granting permission", Toast.LENGTH_LONG).show();
                finish();
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.e(TAG, "onResume");
        startBackgroundThread();
        if (textureView.isAvailable()) {
            openCamera();
        } else {
            textureView.setSurfaceTextureListener(textureListener);
        }
        mapView.onResume(); //SKETCHY

    }

    @Override
    protected void onPause() {
        Log.e(TAG, "onPause");
        //closeCamera();
        stopBackgroundThread();
        super.onPause();
        mapView.onPause();

    }

    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
    }

    @Override
    public void onStart() {
        super.onStart();
        mapView.onStart();
    }




    @Override
    public void onStop() {
        super.onStop();
        mapView.onStop();

    }



    @Override
    public void onSaveInstanceState(@NonNull Bundle outState, @NonNull PersistableBundle outPersistentState) {
        super.onSaveInstanceState(outState, outPersistentState);
        mapView.onSaveInstanceState(outState);

    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        mapView.onLowMemory();

    }


}