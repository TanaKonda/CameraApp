package test.cameraapp;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
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
import android.net.Uri;
import android.os.Environment;
import android.os.Bundle;
import android.os.Looper;
import android.provider.MediaStore;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.text.SimpleDateFormat;
import java.util.Date;

public class MyCameraActivity extends AppCompatActivity {
    private ImageView imageView;
    private static final String TAG = "API";
    private TextureView textureView;
    private Uri imageToUploadUri;
    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();

    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 90);
        ORIENTATIONS.append(Surface.ROTATION_90, 0);
        ORIENTATIONS.append(Surface.ROTATION_180, 270);
        ORIENTATIONS.append(Surface.ROTATION_270, 180);
    }

    private String cameraId;
    protected CameraDevice cameraDevice;
    protected CameraCaptureSession cameraCaptureSessions;
    protected CaptureRequest captureRequest;
    protected CaptureRequest.Builder captureRequestBuilder;
    private Size imageDimension;
    private ImageReader imageReader;
    private File file;
    private static final int REQUEST_CAMERA_PERMISSION = 200;
    private boolean mFlashSupported;
    private Handler mBackgroundHandler;
    private HandlerThread mBackgroundThread = new HandlerThread("Camera Background");

    // Storage Permissions
    // private static final int REQUEST_EXTERNAL_STORAGE = 1;
    private static String[] PERMISSIONS_STORAGE = {
            Manifest.permission.CAMERA,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
    };

    public static void verifyStoragePermissions(Activity activity) {

        // Check if we have write permission
        int permission = ActivityCompat.checkSelfPermission(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE);
        int permission2 = ActivityCompat.checkSelfPermission(activity, Manifest.permission.CAMERA);
        if (permission != PackageManager.PERMISSION_GRANTED || permission2 != PackageManager.PERMISSION_GRANTED) {
            // We don't have permission so prompt the user
            ActivityCompat.requestPermissions(
                    activity,
                    PERMISSIONS_STORAGE,
                    REQUEST_CAMERA_PERMISSION
            );
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults[0] == PackageManager.PERMISSION_DENIED) {
                // close the app
                Toast.makeText(MyCameraActivity.this, "Sorry!!!, you can't use this app without granting permission", Toast.LENGTH_LONG).show();
                finish();
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_my_camera);
//        verifyStoragePermissions(MyCameraActivity.this);
//        Button photoButton = (Button) this.findViewById(R.id.button1);
//        photoButton.setOnClickListener(new View.OnClickListener() {
//
//            @Override
//            public void onClick(View v) {
//
//            captureCameraImage();
//                takeapicture();
//            }
//        });
//    }
        textureView = (TextureView) findViewById(R.id.texture);
        assert textureView != null;
        textureView.setSurfaceTextureListener(textureListener);
        Button takePictureButton = (Button) findViewById(R.id.button1);
        assert takePictureButton != null;
        takePictureButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                takePicture();
            }
        });

    }

    TextureView.SurfaceTextureListener textureListener = new TextureView.SurfaceTextureListener() {

        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
            //open your camera here
            openCamera();
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
            // Transform you image captured size according to the surface width and height
            // MAYBE MOVE CROPPING HERE
        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
            // closeCamera();  // PUT IN TO SEE IF IT BREAKS
            return false;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surface) {
            // IRRELEVANT
        }
    };

    private final CameraDevice.StateCallback stateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(CameraDevice camera) {
            //This is called when the camera is open
            Log.d(TAG, "onOpened");
            cameraDevice = camera;
            createCameraPreview();
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

    final CameraCaptureSession.CaptureCallback captureCallbackListener = new CameraCaptureSession.CaptureCallback() {
        @Override
        public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request, TotalCaptureResult result) {
            super.onCaptureCompleted(session, request, result);
            Toast.makeText(MyCameraActivity.this, "Saved:" + file, Toast.LENGTH_SHORT).show();
            createCameraPreview();
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

    protected void takePicture() {
        if (null == cameraDevice) {
            Log.d(TAG, "cameraDevice is null");
            return;
        }
        CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);   // create camera manager
        try {
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraDevice.getId()); // get characteristics of our devices camera
            Size[] jpegSizes = null;
            if (characteristics != null) {
                jpegSizes = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP).getOutputSizes(ImageFormat.JPEG);
            }
            int width = 640;
            int height = 480;
            if (jpegSizes != null && 0 < jpegSizes.length) {
                width = jpegSizes[0].getWidth();
                height = jpegSizes[0].getHeight();
            }
            ImageReader reader = ImageReader.newInstance(width, height, ImageFormat.JPEG, 1);
            List<Surface> outputSurfaces = new ArrayList<Surface>(2);
            outputSurfaces.add(reader.getSurface());    // image reader
            outputSurfaces.add(new Surface(textureView.getSurfaceTexture()));   // texture surface
            final CaptureRequest.Builder captureBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);   // capture request
            captureBuilder.addTarget(reader.getSurface());  // target??
            captureBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);  // ???
            // Orientation
            int rotation = getWindowManager().getDefaultDisplay().getRotation();
            captureBuilder.set(CaptureRequest.JPEG_ORIENTATION, ORIENTATIONS.get(rotation));
            final File file = new File(Environment.getExternalStorageDirectory() + "/pic.jpg"); // CHANGE NAME
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
                    Toast.makeText(MyCameraActivity.this, "Saved:" + file, Toast.LENGTH_SHORT).show();
                    createCameraPreview();
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

            // Put image splitting code here
            imageToUploadUri = Uri.fromFile(file);

            if(imageToUploadUri != null){
                Uri selectedImage = imageToUploadUri;
                getContentResolver().notifyChange(selectedImage, null);
                Bitmap reducedSizeBitmap = getBitmap(imageToUploadUri.getPath());
                Bitmap[] arr = createBitmaps(reducedSizeBitmap);

                if(arr[0]!=null)
                {
                    this.imageView = (ImageView)this.findViewById(R.id.imageView1);
                    imageView.setImageBitmap(arr[0]);
                }
                if(arr[1]!=null)
                {
                    this.imageView = (ImageView)this.findViewById(R.id.imageView2);
                    imageView.setImageBitmap(arr[1]);
                }
                else
                {
                    Log.d("oh no", "arr 1 was null");
                }
                if(arr[2]!=null)
                {
                    this.imageView = (ImageView)this.findViewById(R.id.imageView3);
                    imageView.setImageBitmap(arr[2]);
                }
                if(arr[3]!=null)
                {
                    this.imageView = (ImageView)this.findViewById(R.id.imageView4);
                    imageView.setImageBitmap(arr[3]);
                }
                if(arr[4]!=null)
                {
                    this.imageView = (ImageView)this.findViewById(R.id.imageView5);
                    imageView.setImageBitmap(arr[4]);
                }
                if(arr[5]!=null)
                {
                    this.imageView = (ImageView)this.findViewById(R.id.imageView6);
                    imageView.setImageBitmap(arr[5]);
                }
                if(arr[6]!=null)
                {
                    this.imageView = (ImageView)this.findViewById(R.id.imageView7);
                    imageView.setImageBitmap(arr[6]);
                }
                if(arr[7]!=null)
                {
                    this.imageView = (ImageView)this.findViewById(R.id.imageView8);
                    imageView.setImageBitmap(arr[7]);
                }
                if(arr[8]!=null)
                {
                    this.imageView = (ImageView)this.findViewById(R.id.imageView9);
                    imageView.setImageBitmap(arr[8]);
                }

                if(reducedSizeBitmap != null){
                    Log.d("new", "nvm");
                    // imageView.setImageBitmap(reducedSizeBitmap);
                }else{
                    Toast.makeText(this,"Error while capturing Image1",Toast.LENGTH_LONG).show();
                }
            }else{
                Toast.makeText(this,"Error while capturing Image2",Toast.LENGTH_LONG).show();
            }

        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    /* READABLE NON CALL BACK CODE */

    protected void createCameraPreview() {
        try {
            SurfaceTexture texture = textureView.getSurfaceTexture();
            assert texture != null;
            texture.setDefaultBufferSize(imageDimension.getWidth(), imageDimension.getHeight());
            Surface surface = new Surface(texture);
            captureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            captureRequestBuilder.addTarget(surface);
            cameraDevice.createCaptureSession(Arrays.asList(surface), new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
                    //The camera is already closed
                    if (null == cameraDevice) {
                        return;
                    }
                    // When the session is ready, we start displaying the preview.
                    cameraCaptureSessions = cameraCaptureSession;
                    updatePreview();
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {
                    Toast.makeText(MyCameraActivity.this, "Configuration change", Toast.LENGTH_SHORT).show();
                }
            }, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void openCamera() {
        CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        Log.d(TAG, "is camera open");
        try {
            cameraId = manager.getCameraIdList()[0];    // BACK CAMERA
            Log.d(TAG, "id is " + cameraId);
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
            StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            assert map != null;
            imageDimension = map.getOutputSizes(SurfaceTexture.class)[0];
            // Add permission for camera and let user grant the permission
            verifyStoragePermissions(this);
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
                manager.openCamera(cameraId, stateCallback, null);
            } else {
                Log.d(TAG, "permissions are not being granted");
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
        Log.d(TAG, "openCamera X");
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

    private void closeCamera() {
        if (null != cameraDevice) {
            cameraDevice.close();
            cameraDevice = null;
        }
        if (null != imageReader) {
            imageReader.close();
            imageReader = null;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "onResume");
        startBackgroundThread();
        if (textureView.isAvailable()) {
            openCamera();
        } else {
            textureView.setSurfaceTextureListener(textureListener);
        }
    }

    @Override
    protected void onPause() {
        Log.d(TAG, "onPause");
        closeCamera();
        stopBackgroundThread();
        super.onPause();
    }

//    private void captureCameraImage() {
//        Intent chooserIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
//        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
//        String imageFileName = "JPEG_" + timeStamp + "_";
//        File f = new File(Environment.getExternalStorageDirectory(), "/" + imageFileName);  // to avoid name collision
//        chooserIntent.putExtra(MediaStore.EXTRA_OUTPUT, Uri.fromFile(f));
//        imageToUploadUri = Uri.fromFile(f);
//        startActivityForResult(chooserIntent, CAMERA_PHOTO);
//
//    }

//    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
//        super.onActivityResult(requestCode, resultCode, data);
//        if (requestCode == CAMERA_PHOTO && resultCode == Activity.RESULT_OK) {
//            if(imageToUploadUri != null){
//                Uri selectedImage = imageToUploadUri;
//                getContentResolver().notifyChange(selectedImage, null);
//                Bitmap reducedSizeBitmap = getBitmap(imageToUploadUri.getPath());
//                Bitmap[] arr = createBitmaps(reducedSizeBitmap);
//
//                if(arr[0]!=null)
//                {
//                    this.imageView = (ImageView)this.findViewById(R.id.imageView1);
//                    imageView.setImageBitmap(arr[0]);
//                }
//                if(arr[1]!=null)
//                {
//                    this.imageView = (ImageView)this.findViewById(R.id.imageView2);
//                    imageView.setImageBitmap(arr[1]);
//                }
//                else
//                {
//                    Log.d("oh no", "arr 1 was null");
//                }
//                if(arr[2]!=null)
//                {
//                    this.imageView = (ImageView)this.findViewById(R.id.imageView3);
//                    imageView.setImageBitmap(arr[2]);
//                }
//                if(arr[3]!=null)
//                {
//                    this.imageView = (ImageView)this.findViewById(R.id.imageView4);
//                    imageView.setImageBitmap(arr[3]);
//                }
//                if(arr[4]!=null)
//                {
//                    this.imageView = (ImageView)this.findViewById(R.id.imageView5);
//                    imageView.setImageBitmap(arr[4]);
//                }
//                if(arr[5]!=null)
//                {
//                    this.imageView = (ImageView)this.findViewById(R.id.imageView6);
//                    imageView.setImageBitmap(arr[5]);
//                }
//                if(arr[6]!=null)
//                {
//                    this.imageView = (ImageView)this.findViewById(R.id.imageView7);
//                    imageView.setImageBitmap(arr[6]);
//                }
//                if(arr[7]!=null)
//                {
//                    this.imageView = (ImageView)this.findViewById(R.id.imageView8);
//                    imageView.setImageBitmap(arr[7]);
//                }
//                if(arr[8]!=null)
//                {
//                    this.imageView = (ImageView)this.findViewById(R.id.imageView9);
//                    imageView.setImageBitmap(arr[8]);
//                }
//
//                if(reducedSizeBitmap != null){
//                    Log.d("new", "nvm");
//                    // imageView.setImageBitmap(reducedSizeBitmap);
//                }else{
//                    Toast.makeText(this,"Error while capturing Image1",Toast.LENGTH_LONG).show();
//                }
//            }else{
//                Toast.makeText(this,"Error while capturing Image2",Toast.LENGTH_LONG).show();
//            }
//        }
//    }

    public Bitmap[] createBitmaps(Bitmap source) {
        Bitmap[] bmp = new Bitmap[9];
        int k = 0;
        int width = source.getWidth();
        int height = source.getHeight();
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++, k++) {
                bmp[k] = Bitmap.createBitmap(source, (width * j) / 3, (i * height) / 3, width / 3, height / 3);
            }
        }
        return bmp;
    }


    private Bitmap getBitmap(String path) {

        Uri uri = Uri.fromFile(new File(path));
        InputStream in = null;
        try {
            Log.d("hi", "entered");
            final int IMAGE_MAX_SIZE = 1200000; // 1.2MP
            in = getContentResolver().openInputStream(uri);

            // Decode image size
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeStream(in, null, o);
            in.close();


            int scale = 1;
            while ((o.outWidth * o.outHeight) * (1 / Math.pow(scale, 2)) >
                    IMAGE_MAX_SIZE) {
                scale++;
            }
            Log.d("hi", "scale = " + scale + ", orig-width: " + o.outWidth + ", orig-height: " + o.outHeight);

            Bitmap b = null;
            in = getContentResolver().openInputStream(uri);
            if (scale > 1) {
                scale--;
                // scale to max possible inSampleSize that still yields an image
                // larger than target
                o = new BitmapFactory.Options();
                o.inSampleSize = scale;
                b = BitmapFactory.decodeStream(in, null, o);
//
//                // resize to desired dimensions
//                int height = b.getHeight();
//                int width = b.getWidth();
//                Log.d("hi", "1th scale operation dimenions - width: " + width + ", height: " + height);
//
//                double y = Math.sqrt(IMAGE_MAX_SIZE
//                        / (((double) width) / height));
//                double x = (y / height) * width;
//
//                Bitmap scaledBitmap = Bitmap.createScaledBitmap(b, (int) x,
//                        (int) y, true);
//                b.recycle();
//                b = scaledBitmap;
//
//                System.gc();
            } else {
                b = BitmapFactory.decodeStream(in);
            }
            in.close();

            Log.d("hi", "bitmap size - width: " + b.getWidth() + ", height: " +
                    b.getHeight());
            return b;
        } catch (IOException e) {
            Log.e("hi", e.getMessage(), e);
            return null;
        }
    }
}