package com.tonyxlh.capacitor.camera;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.ContentValues;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import android.util.Base64;
import android.util.Log;
import android.util.Size;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.camera.core.AspectRatio;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.CameraState;
import androidx.camera.core.FocusMeteringAction;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.MeteringPoint;
import androidx.camera.core.MeteringPointFactory;
import androidx.camera.core.Preview;
import androidx.camera.core.SurfaceOrientedMeteringPointFactory;
import androidx.camera.core.UseCaseGroup;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.video.PendingRecording;
import androidx.camera.video.Quality;
import androidx.camera.video.QualitySelector;
import androidx.camera.video.Recorder;
import androidx.camera.video.Recording;
import androidx.camera.video.VideoCapture;
import androidx.camera.video.MediaStoreOutputOptions;
import androidx.camera.video.VideoRecordEvent;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.util.Consumer;
import androidx.lifecycle.LifecycleOwner;

import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.PermissionState;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.annotation.Permission;
import com.getcapacitor.annotation.PermissionCallback;
import com.google.common.util.concurrent.ListenableFuture;

import org.json.JSONException;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Date;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@CapacitorPlugin(
        name = "CameraPreview",
        permissions = {
                @Permission(strings = {Manifest.permission.CAMERA}, alias = CameraPreviewPlugin.CAMERA),
                @Permission(strings = {Manifest.permission.RECORD_AUDIO}, alias = CameraPreviewPlugin.MICROPHONE),
        }
)
public class CameraPreviewPlugin extends Plugin {
    // Permission alias constants
    static final String CAMERA = "camera";
    static final String MICROPHONE = "microphone";
    private String callbackID;
    private PreviewView previewView;
    private ListenableFuture<ProcessCameraProvider> cameraProviderFuture;
    private ExecutorService exec;
    private Camera camera;
    private CameraSelector cameraSelector;
    private ProcessCameraProvider cameraProvider;
    private Preview preview;
    private ImageCapture imageCapture;
    private UseCaseGroup useCaseGroup;
    private ImageAnalysis imageAnalysis;
    private Recorder recorder;
    private Recording currentRecording;
    private PluginCall stopRecordingCall;
    private PluginCall takeSnapshotCall;
    private PluginCall saveFrameCall;
    private int desiredWidth = 1280;
    private int desiredHeight = 720;
    private CameraState previousCameraStatus;
    private ScanRegion scanRegion;

    static public Bitmap frameTaken;

    @PluginMethod
    public void initialize(PluginCall call) {
        getActivity().runOnUiThread(new Runnable() {
            @RequiresApi(api = Build.VERSION_CODES.P)
            public void run() {
                previewView = new PreviewView(getContext());
                FrameLayout.LayoutParams cameraPreviewParams = new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                );
                ((ViewGroup) bridge.getWebView().getParent()).addView(previewView, cameraPreviewParams);
                bridge.getWebView().bringToFront();

                exec = Executors.newSingleThreadExecutor();
                cameraProviderFuture = ProcessCameraProvider.getInstance(getContext());
                cameraProviderFuture.addListener(() -> {
                    try {
                        cameraProvider = cameraProviderFuture.get();
                        cameraSelector = new CameraSelector.Builder()
                                .requireLensFacing(CameraSelector.LENS_FACING_BACK).build();
                        setupUseCases(false);
                        call.resolve();
                    } catch (ExecutionException | InterruptedException e) {
                        e.printStackTrace();
                        call.reject(e.getMessage());
                    }
                }, getContext().getMainExecutor());


            }
        });
    }

    private void setupUseCases(boolean enableVideo) {
        //set up the resolution for the preview and image analysis.
        int orientation = getContext().getResources().getConfiguration().orientation;
        Size resolution;
        if (orientation == Configuration.ORIENTATION_PORTRAIT) {
            resolution = new Size(desiredHeight, desiredWidth);
        } else {
            resolution = new Size(desiredWidth, desiredHeight);
        }

        Preview.Builder previewBuilder = new Preview.Builder();
        previewBuilder.setTargetResolution(resolution);
        preview = previewBuilder.build();
        preview.setSurfaceProvider(previewView.getSurfaceProvider());

        ImageAnalysis.Builder imageAnalysisBuilder = new ImageAnalysis.Builder();

        imageAnalysisBuilder.setTargetResolution(resolution)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST);

        imageAnalysis = imageAnalysisBuilder.build();

        imageAnalysis.setAnalyzer(exec, new ImageAnalysis.Analyzer() {
            @Override
            public void analyze(@NonNull ImageProxy image) {
                if (takeSnapshotCall != null || saveFrameCall != null) {
                    @SuppressLint("UnsafeOptInUsageError")
                    Bitmap bitmap = BitmapUtils.getBitmap(image);
                    if (scanRegion != null) {
                        int left, top, width, height;
                        if (scanRegion.measuredByPercentage == 0) {
                            left = scanRegion.left;
                            top = scanRegion.top;
                            width = scanRegion.right - scanRegion.left;
                            height = scanRegion.bottom - scanRegion.top;
                        } else {
                            left = (int) ((double) scanRegion.left / 100 * bitmap.getWidth());
                            top = (int) ((double) scanRegion.top / 100 * bitmap.getHeight());
                            width = (int) ((double) scanRegion.right / 100 * bitmap.getWidth() - left);
                            height = (int) ((double) scanRegion.bottom / 100 * bitmap.getHeight() - top);
                        }
                        bitmap = Bitmap.createBitmap(bitmap, left, top, width, height, null, false);
                    }
                    if (takeSnapshotCall != null) {
                        int desiredQuality = 85;
                        if (takeSnapshotCall.hasOption("quality")) {
                            desiredQuality = takeSnapshotCall.getInt("quality");
                        }
                        String base64 = bitmap2Base64(bitmap, desiredQuality);
                        JSObject result = new JSObject();
                        result.put("base64", base64);
                        takeSnapshotCall.resolve(result);
                        takeSnapshotCall = null;
                    }
                    if (saveFrameCall != null) {
                        frameTaken = bitmap;
                        JSObject result = new JSObject();
                        result.put("success", true);
                        saveFrameCall.resolve(result);
                        saveFrameCall = null;
                    }
                }
                image.close();
            }
        });

        imageCapture =
                new ImageCapture.Builder()
                        .setTargetAspectRatio(AspectRatio.RATIO_16_9)
                        .build();

        if (enableVideo) {
            Quality quality = Quality.HD;
            QualitySelector qualitySelector = QualitySelector.from(quality);
            Recorder.Builder recorderBuilder = new Recorder.Builder();
            recorderBuilder.setQualitySelector(qualitySelector);
            recorder = recorderBuilder.build();
            VideoCapture videoCapture = VideoCapture.withOutput(recorder);
            useCaseGroup = new UseCaseGroup.Builder()
                    .addUseCase(preview)
                    .addUseCase(imageAnalysis)
                    .addUseCase(videoCapture)
                    .build();
        }else{
            useCaseGroup = new UseCaseGroup.Builder()
                    .addUseCase(preview)
                    .addUseCase(imageAnalysis)
                    .addUseCase(imageCapture)
                    .build();
        }
    }

    @PluginMethod
    public void startCamera(PluginCall call) {
        getActivity().runOnUiThread(new Runnable() {
            public void run() {
                try {
                    camera = cameraProvider.bindToLifecycle((LifecycleOwner) getContext(), cameraSelector, useCaseGroup);
                    previewView.setVisibility(View.VISIBLE);
                    makeWebViewTransparent();
                    triggerOnPlayed();
                    call.resolve();
                } catch (Exception e) {
                    e.printStackTrace();
                    call.reject(e.getMessage());
                }
            }
        });
    }

    @PluginMethod
    public void stopCamera(PluginCall call) {
        getActivity().runOnUiThread(new Runnable() {
            public void run() {
                try {
                    restoreWebViewBackground();
                    previewView.setVisibility(View.INVISIBLE);
                    cameraProvider.unbindAll();
                    call.resolve();
                } catch (Exception e) {
                    call.reject(e.getMessage());
                }
            }
        });
    }

    private void makeWebViewTransparent() {
        bridge.getWebView().setTag(bridge.getWebView().getBackground());
        bridge.getWebView().setBackgroundColor(Color.TRANSPARENT);
    }

    private void restoreWebViewBackground() {
        bridge.getWebView().setBackground((Drawable) bridge.getWebView().getTag());
    }

    @PluginMethod
    public void toggleTorch(PluginCall call) {
        try {
            if (call.getBoolean("on", true)) {
                camera.getCameraControl().enableTorch(true);
            } else {
                camera.getCameraControl().enableTorch(false);
            }
            call.resolve();
        } catch (Exception e) {
            call.reject(e.getMessage());
        }
    }

    @PluginMethod
    public void setScanRegion(PluginCall call) {
        JSObject region = call.getObject("region");
        try {
            scanRegion = new ScanRegion(region.getInt("top"),
                    region.getInt("bottom"),
                    region.getInt("left"),
                    region.getInt("right"),
                    region.getInt("measuredByPercentage"));
        } catch (JSONException e) {
            call.reject(e.getMessage());
        }
        call.resolve();
    }

    @PluginMethod
    public void setZoom(PluginCall call) {
        if (call.hasOption("factor")) {
            Float factor = call.getFloat("factor");
            try {
                camera.getCameraControl().setZoomRatio(factor);
            } catch (Exception e) {
                e.printStackTrace();
                call.reject(e.getMessage());
            }
        }
        call.resolve();
    }

    @PluginMethod
    public void setFocus(PluginCall call) {
        if (call.hasOption("x") && call.hasOption("y")) {
            Float x = call.getFloat("x");
            Float y = call.getFloat("y");
            try {
                MeteringPointFactory factory = new SurfaceOrientedMeteringPointFactory(previewView.getWidth(), previewView.getHeight());
                MeteringPoint point = factory.createPoint(x, y);
                FocusMeteringAction.Builder builder = new FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AF);
                // auto calling cancelFocusAndMetering in 5 seconds
                builder.setAutoCancelDuration(5, TimeUnit.SECONDS);
                FocusMeteringAction action = builder.build();
                camera.getCameraControl().startFocusAndMetering(action);
            } catch (Exception e) {
                e.printStackTrace();
                call.reject(e.getMessage());
            }
        }
        call.resolve();
    }

    @PluginMethod
    public void selectCamera(PluginCall call) {
        getActivity().runOnUiThread(new Runnable() {
            @RequiresApi(api = Build.VERSION_CODES.P)
            public void run() {
                if (call.hasOption("cameraID")) {
                    try {
                        String cameraID = call.getString("cameraID");
                        if (cameraID.equals("Front-Facing Camera")) {
                            cameraSelector = new CameraSelector.Builder()
                                    .requireLensFacing(CameraSelector.LENS_FACING_FRONT).build();
                        } else {
                            cameraSelector = new CameraSelector.Builder()
                                    .requireLensFacing(CameraSelector.LENS_FACING_BACK).build();
                        }
                        if (camera != null) {
                            if (camera.getCameraInfo().getCameraState().getValue().getType() == CameraState.Type.OPEN) {
                                cameraProvider.unbindAll();
                                setupUseCases(false);
                                camera = cameraProvider.bindToLifecycle((LifecycleOwner) getContext(), cameraSelector, useCaseGroup);
                                triggerOnPlayed();
                            }
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                        call.reject(e.getMessage());
                        return;
                    }
                }
                JSObject result = new JSObject();
                result.put("success", true);
                call.resolve(result);
            }
        });
    }

    @PluginMethod
    public void setLayout(PluginCall call){
        if (previewView != null) {
            getActivity().runOnUiThread(new Runnable() {
                public void run() {
                    if (call.hasOption("width") && call.hasOption("height") && call.hasOption("left") && call.hasOption("top")) {
                        try{
                            double width = getLayoutValue(call.getString("width"),true);
                            double height = getLayoutValue(call.getString("height"),false);
                            double left = getLayoutValue(call.getString("left"),true);
                            double top = getLayoutValue(call.getString("top"),false);
                            previewView.setX((int) left);
                            previewView.setY((int) top);
                            ViewGroup.LayoutParams cameraPreviewParams = previewView.getLayoutParams();
                            cameraPreviewParams.width = (int) width;
                            cameraPreviewParams.height = (int) height;
                            previewView.setLayoutParams(cameraPreviewParams);
                        }catch(Exception e) {
                            Log.d("Camera",e.getMessage());
                        }
                    }
                    call.resolve();
                }
            });
        }else{
            call.reject("Camera not initialized");
        }
    }
    private double getLayoutValue(String value,boolean isWidth) {
        if (value.indexOf("%") != -1) {
            double percent = Double.parseDouble(value.substring(0,value.length()-1))/100;
            if (isWidth) {
                return percent * Resources.getSystem().getDisplayMetrics().widthPixels;
            }else{
                return percent * Resources.getSystem().getDisplayMetrics().heightPixels;
            }
        }
        if (value.indexOf("px") != -1) {
            return Double.parseDouble(value.substring(0,value.length()-2));
        }
        try {
            return Double.parseDouble(value);
        }catch(Exception e) {
            if (isWidth) {
                return Resources.getSystem().getDisplayMetrics().widthPixels;
            }else{
                return Resources.getSystem().getDisplayMetrics().heightPixels;
            }
        }
    }
    private void triggerOnPlayed() {
        try {
            JSObject onPlayedResult = new JSObject();
            @SuppressLint("RestrictedApi")
            String res = imageAnalysis.getAttachedSurfaceResolution().getWidth() + "x" + imageAnalysis.getAttachedSurfaceResolution().getHeight();
            onPlayedResult.put("resolution", res);
            notifyListeners("onPlayed", onPlayedResult);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @SuppressLint("RestrictedApi")
    @PluginMethod
    public void getAllCameras(PluginCall call) {
        JSObject result = new JSObject();
        JSArray cameras = new JSArray();
        cameras.put("Back-Facing Camera");
        cameras.put("Front-Facing Camera");
        result.put("cameras", cameras);
        call.resolve(result);
    }

    @SuppressLint("RestrictedApi")
    @PluginMethod
    public void getSelectedCamera(PluginCall call) {
        if (cameraSelector == null) {
            call.reject("not initialized");
        } else {
            JSObject result = new JSObject();
            String cameraID = "Back-Facing Camera";
            if (cameraSelector.getLensFacing() == CameraSelector.LENS_FACING_FRONT) {
                cameraID = "Front-Facing Camera";
            }
            result.put("selectedCamera", cameraID);
            call.resolve(result);
        }
    }

    @PluginMethod
    public void setResolution(PluginCall call) {
        getActivity().runOnUiThread(new Runnable() {
            @RequiresApi(api = Build.VERSION_CODES.P)
            public void run() {
                if (call.hasOption("resolution")) {
                    try {
                        int res = call.getInt("resolution");
                        int width = 1280;
                        int height = 720;
                        if (res == 1) {
                            width = 640;
                            height = 480;
                        } else if (res == 2) {
                            width = 1280;
                            height = 720;
                        } else if (res == 3) {
                            width = 1920;
                            height = 1080;
                        } else if (res == 4) {
                            width = 2560;
                            height = 1440;
                        } else if (res == 5) {
                            width = 3840;
                            height = 2160;
                        }
                        desiredHeight = height;
                        desiredWidth = width;
                        CameraState.Type status = null;
                        if (camera != null) {
                            status = camera.getCameraInfo().getCameraState().getValue().getType();
                            if (status == CameraState.Type.OPEN) {
                                cameraProvider.unbindAll();
                            }
                        }
                        setupUseCases(false);
                        if (camera != null) {
                            if (status == CameraState.Type.OPEN) {
                                camera = cameraProvider.bindToLifecycle((LifecycleOwner) getContext(), cameraSelector, useCaseGroup);
                                triggerOnPlayed();
                            }
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                        call.reject(e.getMessage());
                        return;
                    }
                }
                JSObject result = new JSObject();
                result.put("success", true);
                call.resolve(result);
            }
        });
    }

    @SuppressLint("RestrictedApi")
    @PluginMethod
    public void getResolution(PluginCall call) {
        if (camera == null) {
            call.reject("Camera not initialized");
        } else {
            try {
                JSObject result = new JSObject();
                result.put("resolution", imageAnalysis.getAttachedSurfaceResolution().getWidth() + "x" + imageAnalysis.getAttachedSurfaceResolution().getHeight());
                call.resolve(result);
            } catch (Exception e) {
                call.reject(e.getMessage());
            }

        }
    }

    static public Bitmap getBitmap() {
        try {
            return frameTaken;
        } catch (Exception e) {
            return null;
        }
    }

    @PluginMethod
    public void takeSnapshot(PluginCall call) {
        call.setKeepAlive(true);
        takeSnapshotCall = call;
    }

    @PluginMethod
    public void saveFrame(PluginCall call) {
        call.setKeepAlive(true);
        saveFrameCall = call;
    }

    @PluginMethod
    public void takePhoto(PluginCall call) {
        if (camera == null) {
            call.reject("Camera not initialized.");
            return;
        }
        getActivity().runOnUiThread(new Runnable() {
            @RequiresApi(api = Build.VERSION_CODES.P)
            public void run() {
                if (useCaseGroup.getUseCases().contains(imageCapture) == false) {
                    cameraProvider.unbindAll();
                    setupUseCases(false);
                    camera = cameraProvider.bindToLifecycle((LifecycleOwner) getContext(), cameraSelector, useCaseGroup);
                }
                File file;
                if (call.hasOption("pathToSave")) {
                    file = new File(call.getString("pathToSave"));
                } else {
                    File dir = getContext().getExternalCacheDir();
                    file = new File(dir, new Date().getTime() + ".jpg");
                }
                ImageCapture.OutputFileOptions outputFileOptions =
                        new ImageCapture.OutputFileOptions.Builder(file).build();
                imageCapture.takePicture(outputFileOptions, exec,
                        new ImageCapture.OnImageSavedCallback() {
                            @Override
                            public void onImageSaved(ImageCapture.OutputFileResults outputFileResults) {
                                JSObject result = new JSObject();
                                if (call.getBoolean("includeBase64", false)) {
                                    String base64 = Base64.encodeToString(convertFileToByteArray(file), Base64.DEFAULT);
                                    result.put("base64", base64);
                                }
                                result.put("path", file.getAbsolutePath());
                                call.resolve(result);
                            }

                            @Override
                            public void onError(@NonNull ImageCaptureException exception) {
                                call.reject(exception.getMessage());
                            }
                        }
                );
            }
        });
    }

    @RequiresApi(api = Build.VERSION_CODES.P)
    @PluginMethod
    public void startRecording(PluginCall call) {
        if (camera == null) {
            call.reject("Camera not initialized.");
            return;
        }
        getActivity().runOnUiThread(new Runnable() {
            @RequiresApi(api = Build.VERSION_CODES.P)
            public void run() {
                cameraProvider.unbindAll();
                setupUseCases(true);
                camera = cameraProvider.bindToLifecycle((LifecycleOwner) getContext(), cameraSelector, useCaseGroup);
                if (recorder != null) {
                    // create MediaStoreOutputOptions for our recorder: resulting our recording!
                    String name = "CameraX-recording-" + System.currentTimeMillis() + ".mp4";
                    ContentValues contentValues = new ContentValues();
                    contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, name);

                    MediaStoreOutputOptions mediaStoreOutput = new MediaStoreOutputOptions.Builder(
                            getContext().getContentResolver(),
                            MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
                            .setContentValues(contentValues)
                            .build();

                    // configure Recorder and Start recording to the mediaStoreOutput.

                    PendingRecording pendingRecording = recorder.prepareRecording(getContext(), mediaStoreOutput);

                    if (ActivityCompat.checkSelfPermission(getContext(), Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                        // TODO: Consider calling
                        //    ActivityCompat#requestPermissions
                        // here to request the missing permissions, and then overriding
                        //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                        //                                          int[] grantResults)
                        // to handle the case where the user grants the permission. See the documentation
                        // for ActivityCompat#requestPermissions for more details.
                    }else{
                        pendingRecording.withAudioEnabled();
                    }
                    Consumer<VideoRecordEvent> captureListener = new Consumer<VideoRecordEvent>() {
                        @Override
                        public void accept(VideoRecordEvent videoRecordEvent) {
                            Log.d("Camera",videoRecordEvent.toString());
                            if (videoRecordEvent instanceof VideoRecordEvent.Finalize) {
                                Log.d("Camera","finalize");
                                Uri uri = ((VideoRecordEvent.Finalize) videoRecordEvent).getOutputResults().getOutputUri();
                                String path = uri.getPath();

                                if (stopRecordingCall != null) {
                                    JSObject result = new JSObject();
                                    if (stopRecordingCall.getBoolean("includeBase64",false)) {
                                        try {
                                            InputStream iStream = getContext().getContentResolver().openInputStream(uri);
                                            byte[] inputData = getBytes(iStream);
                                            String base64 = Base64.encodeToString(inputData, Base64.DEFAULT);
                                            result.put("base64",base64);
                                        } catch (IOException e) {
                                            throw new RuntimeException(e);
                                        }
                                    }
                                    result.put("path",path);
                                    recorder = null;
                                    stopRecordingCall.resolve(result);
                                    stopRecordingCall = null;
                                }
                            }
                        }
                    };
                    currentRecording = pendingRecording.start(getContext().getMainExecutor(),captureListener);
                    call.resolve();
                }else{
                    call.reject("Recording is not ready");
                }
            }
        });
    }

    private byte[] getBytes(InputStream inputStream) throws IOException {
        ByteArrayOutputStream byteBuffer = new ByteArrayOutputStream();
        int bufferSize = 1024;
        byte[] buffer = new byte[bufferSize];

        int len = 0;
        while ((len = inputStream.read(buffer)) != -1) {
            byteBuffer.write(buffer, 0, len);
        }
        return byteBuffer.toByteArray();
    }

    @PluginMethod
    public void stopRecording(PluginCall call){
        getActivity().runOnUiThread(new Runnable() {

            @Override
            public void run() {
                call.setKeepAlive(true);
                stopRecordingCall = call;
                currentRecording.stop();
            }
        });
    }

    public static byte[] convertFileToByteArray(File f) {
        byte[] byteArray = null;
        try {
            InputStream inputStream = new FileInputStream(f);
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] b = new byte[1024 * 8];
            int bytesRead = 0;
            while ((bytesRead = inputStream.read(b)) != -1) {
                bos.write(b, 0, bytesRead);
            }

            byteArray = bos.toByteArray();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return byteArray;
    }

    public static String bitmap2Base64(Bitmap bitmap,int quality) {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream);
        return Base64.encodeToString(outputStream.toByteArray(), Base64.DEFAULT);
    }

    @PluginMethod
    public void isOpen(PluginCall call){
        if (camera != null) {
            JSObject result = new JSObject();
            if (camera.getCameraInfo().getCameraState().getValue().getType() == CameraState.Type.OPEN) {
                result.put("isOpen",true);
            }else{
                result.put("isOpen",false);
            }
            call.resolve(result);
        }else {
            call.reject("Camera not initialized.");
        }
    }

    @Override
    protected void handleOnPause() {
        if (camera != null) {
            CameraState cameraStatus = camera.getCameraInfo().getCameraState().getValue();
            previousCameraStatus = cameraStatus;
            if (cameraStatus.getType() == CameraState.Type.OPEN) {
                cameraProvider.unbindAll();
            }
        }
        super.handleOnPause();
    }

    @Override
    protected void handleOnResume() {
        if (camera != null) {
            if (previousCameraStatus.getType() == CameraState.Type.OPEN) {
                camera = cameraProvider.bindToLifecycle((LifecycleOwner) getContext(), cameraSelector, useCaseGroup);
            }
        }
        super.handleOnResume();
    }

    @Override
    protected void handleOnConfigurationChanged(Configuration newConfig) {
        notifyListeners("onOrientationChanged",null);
        super.handleOnConfigurationChanged(newConfig);
    }



    @PluginMethod
    public void requestCameraPermission(PluginCall call) {
        boolean hasCameraPerms = getPermissionState(CAMERA) == PermissionState.GRANTED;
        if (hasCameraPerms == false) {
            Log.d("Camera","no camera permission. request permission.");
            String[] aliases = new String[] { CAMERA };
            requestPermissionForAliases(aliases, call, "cameraPermissionsCallback");
        }else{
            call.resolve();
        }
    }

    @PermissionCallback
    private void cameraPermissionsCallback(PluginCall call) {
        boolean hasCameraPerms = getPermissionState(CAMERA) == PermissionState.GRANTED;
        if (hasCameraPerms) {
            call.resolve();
        }else {
            call.reject("Permission not granted.");
        }
    }

    @PluginMethod
    public void requestMicroPhonePermission(PluginCall call) {
        boolean hasCameraPerms = getPermissionState(MICROPHONE) == PermissionState.GRANTED;
        if (hasCameraPerms == false) {
            Log.d("Camera","no microphone permission. request permission.");
            String[] aliases = new String[] { MICROPHONE };
            requestPermissionForAliases(aliases, call, "microphonePermissionsCallback");
        }else{
            call.resolve();
        }
    }

    @PermissionCallback
    private void microphonePermissionsCallback(PluginCall call) {
        boolean hasPerms = getPermissionState(MICROPHONE) == PermissionState.GRANTED;
        if (hasPerms) {
            call.resolve();
        }else {
            call.reject("Permission not granted.");
        }
    }

    @PluginMethod
    public void getOrientation(PluginCall call) {
        int orientation = getContext().getResources().getConfiguration().orientation;
        JSObject result = new JSObject();
        if (orientation == Configuration.ORIENTATION_PORTRAIT) {
            result.put("orientation","PORTRAIT");
        }else{
            result.put("orientation","LANDSCAPE");
        }
        call.resolve(result);
    }

}
