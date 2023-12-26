package com.tonyxlh.capacitor.camera;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Build;
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
import androidx.camera.view.PreviewView;
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
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@CapacitorPlugin(
        name = "CameraPreview",
        permissions = {
                @Permission(strings = { Manifest.permission.CAMERA }, alias = CameraPreviewPlugin.CAMERA),
        }
)
public class CameraPreviewPlugin extends Plugin {
    // Permission alias constants
    static final String CAMERA = "camera";
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
                ((ViewGroup) bridge.getWebView().getParent()).addView(previewView,cameraPreviewParams);
                bridge.getWebView().bringToFront();

                exec = Executors.newSingleThreadExecutor();
                cameraProviderFuture = ProcessCameraProvider.getInstance(getContext());
                cameraProviderFuture.addListener(() -> {
                    try {
                        cameraProvider = cameraProviderFuture.get();
                        cameraSelector = new CameraSelector.Builder()
                                .requireLensFacing(CameraSelector.LENS_FACING_BACK).build();
                        setupUseCases();
                        call.resolve();
                    } catch (ExecutionException | InterruptedException e) {
                        e.printStackTrace();
                        call.reject(e.getMessage());
                    }
                }, getContext().getMainExecutor());


            }
        });
    }

    private void setupUseCases(){
        //set up the resolution for the preview and image analysis.
        int orientation = getContext().getResources().getConfiguration().orientation;
        Size resolution;
        if (orientation == Configuration.ORIENTATION_PORTRAIT) {
            resolution = new Size(desiredHeight, desiredWidth);
        }else{
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
                        int left,top,width,height;
                        if (scanRegion.measuredByPercentage == 0) {
                            left = scanRegion.left;
                            top = scanRegion.top;
                            width = scanRegion.right - scanRegion.left;
                            height = scanRegion.bottom - scanRegion.top;
                        }else{
                            left = (int) ((double) scanRegion.left / 100 * bitmap.getWidth());
                            top = (int) ((double) scanRegion.top / 100 * bitmap.getHeight());
                            width = (int) ((double) scanRegion.right / 100 * bitmap.getWidth() - left);
                            height = (int) ((double) scanRegion.bottom / 100 * bitmap.getHeight() - top);
                        }
                        bitmap = Bitmap.createBitmap(bitmap, left, top, width, height,null,false);
                    }
                    if (takeSnapshotCall != null) {
                        int desiredQuality = 85;
                        if (takeSnapshotCall.hasOption("quality")) {
                            desiredQuality = takeSnapshotCall.getInt("quality");
                        }
                        String base64 = bitmap2Base64(bitmap, desiredQuality);
                        JSObject result = new JSObject();
                        result.put("base64",base64);
                        takeSnapshotCall.resolve(result);
                        takeSnapshotCall = null;
                    }
                    if (saveFrameCall != null) {
                        frameTaken = bitmap;
                        JSObject result = new JSObject();
                        result.put("success",true);
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
        useCaseGroup = new UseCaseGroup.Builder()
                .addUseCase(preview)
                .addUseCase(imageAnalysis)
                .addUseCase(imageCapture)
                .build();
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
                try{
                    restoreWebViewBackground();
                    previewView.setVisibility(View.INVISIBLE);
                    cameraProvider.unbindAll();
                    call.resolve();
                }catch (Exception e){
                    call.reject(e.getMessage());
                }
            }
        });
    }

    private void makeWebViewTransparent(){
        bridge.getWebView().setTag(bridge.getWebView().getBackground());
        bridge.getWebView().setBackgroundColor(Color.TRANSPARENT);
    }

    private void restoreWebViewBackground(){
        bridge.getWebView().setBackground((Drawable) bridge.getWebView().getTag());
    }

    @PluginMethod
    public void toggleTorch(PluginCall call) {
        try{
            if (call.getBoolean("on",true)){
                camera.getCameraControl().enableTorch(true);
            }else {
                camera.getCameraControl().enableTorch(false);
            }
            call.resolve();
        }catch (Exception e){
            call.reject(e.getMessage());
        }
    }

    @PluginMethod
    public void setScanRegion(PluginCall call){
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
    public void setZoom(PluginCall call){
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
    public void setFocus(PluginCall call){
        if (call.hasOption("x") && call.hasOption("y")) {
            Float x = call.getFloat("x");
            Float y = call.getFloat("y");
            try {
                MeteringPointFactory factory = new SurfaceOrientedMeteringPointFactory(previewView.getWidth(), previewView.getHeight());
                MeteringPoint point = factory.createPoint(x, y);
                FocusMeteringAction.Builder builder = new FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AF);
                // auto calling cancelFocusAndMetering in 5 seconds
                builder.setAutoCancelDuration(5, TimeUnit.SECONDS);
                FocusMeteringAction action =builder.build();
                camera.getCameraControl().startFocusAndMetering(action);
            } catch (Exception e) {
                e.printStackTrace();
                call.reject(e.getMessage());
            }
        }
        call.resolve();
    }

    @PluginMethod
    public void selectCamera(PluginCall call){
        getActivity().runOnUiThread(new Runnable() {
            @RequiresApi(api = Build.VERSION_CODES.P)
            public void run() {
                if (call.hasOption("cameraID")){
                    try {
                        String cameraID = call.getString("cameraID");
                        if (cameraID.equals("Front-Facing Camera")) {
                            cameraSelector = new CameraSelector.Builder()
                                    .requireLensFacing(CameraSelector.LENS_FACING_FRONT).build();
                        }else{
                            cameraSelector = new CameraSelector.Builder()
                                    .requireLensFacing(CameraSelector.LENS_FACING_BACK).build();
                        }
                        cameraProvider.unbindAll();
                        setupUseCases();
                        camera = cameraProvider.bindToLifecycle((LifecycleOwner) getContext(), cameraSelector, useCaseGroup);
                        triggerOnPlayed();
                    } catch (Exception e) {
                        e.printStackTrace();
                        call.reject(e.getMessage());
                        return;
                    }
                }
                JSObject result = new JSObject();
                result.put("success",true);
                call.resolve(result);
            }
        });
    }

    private void triggerOnPlayed(){
        JSObject onPlayedResult = new JSObject();
        @SuppressLint("RestrictedApi")
        String res = imageAnalysis.getAttachedSurfaceResolution().getWidth()+"x"+imageAnalysis.getAttachedSurfaceResolution().getHeight();
        onPlayedResult.put("resolution",res);
        notifyListeners("onPlayed",onPlayedResult);
    }

    @SuppressLint("RestrictedApi")
    @PluginMethod
    public void getAllCameras(PluginCall call){
        JSObject result = new JSObject();
        JSArray cameras = new JSArray();
        cameras.put("Back-Facing Camera");
        cameras.put("Front-Facing Camera");
        result.put("cameras",cameras);
        call.resolve(result);
    }

    @SuppressLint("RestrictedApi")
    @PluginMethod
    public void getSelectedCamera(PluginCall call){
        if (cameraSelector == null) {
            call.reject("not initialized");
        }else{
            JSObject result = new JSObject();
            String cameraID = "Back-Facing Camera";
            if (cameraSelector.getLensFacing() == CameraSelector.LENS_FACING_FRONT) {
                cameraID = "Front-Facing Camera";
            }
            result.put("selectedCamera",cameraID);
            call.resolve(result);
        }
    }

    @PluginMethod
    public void setResolution(PluginCall call){
        getActivity().runOnUiThread(new Runnable() {
            @RequiresApi(api = Build.VERSION_CODES.P)
            public void run() {
                if (call.hasOption("resolution")){
                    try {
                        int res = call.getInt("resolution");
                        int width = 1280;
                        int height = 720;
                        if (res == 1){
                            width = 640;
                            height = 480;
                        } else if (res == 2){
                            width = 1280;
                            height = 720;
                        } else if (res == 3){
                            width = 1920;
                            height = 1080;
                        } else if (res == 4){
                            width = 2560;
                            height = 1440;
                        } else if (res == 5){
                            width = 3840;
                            height = 2160;
                        }
                        desiredHeight = height;
                        desiredWidth = width;
                        cameraProvider.unbindAll();
                        setupUseCases();
                        camera = cameraProvider.bindToLifecycle((LifecycleOwner) getContext(), cameraSelector, useCaseGroup);
                        triggerOnPlayed();
                    } catch (Exception e) {
                        e.printStackTrace();
                        call.reject(e.getMessage());
                        return;
                    }
                }
                JSObject result = new JSObject();
                result.put("success",true);
                call.resolve(result);
            }
        });
    }

    @SuppressLint("RestrictedApi")
    @PluginMethod
    public void getResolution(PluginCall call){
        if (camera == null) {
            call.reject("Camera not initialized");
        }else{
            JSObject result = new JSObject();
            result.put("resolution",imageAnalysis.getAttachedSurfaceResolution().getWidth()+"x"+imageAnalysis.getAttachedSurfaceResolution().getHeight());
            call.resolve(result);
        }
    }

    static public Bitmap getBitmap(){
        try {
            return frameTaken;
        } catch (Exception e) {
            return null;
        }
    }

    @PluginMethod
    public void takeSnapshot(PluginCall call){
        call.setKeepAlive(true);
        takeSnapshotCall = call;
    }

    @PluginMethod
    public void saveFrame(PluginCall call){
        call.setKeepAlive(true);
        saveFrameCall = call;
    }

    @PluginMethod
    public void takePhoto(PluginCall call){
        File dir = getContext().getExternalCacheDir();
        File file = new File(dir, "photo.jpg");
        ImageCapture.OutputFileOptions outputFileOptions =
                new ImageCapture.OutputFileOptions.Builder(file).build();
        imageCapture.takePicture(outputFileOptions, exec,
                new ImageCapture.OnImageSavedCallback() {
                    @Override
                    public void onImageSaved(ImageCapture.OutputFileResults outputFileResults) {
                        JSObject result = new JSObject();
                        if (call.getBoolean("includeBase64",false)){
                            String base64 = Base64.encodeToString(convertFileToByteArray(file), Base64.DEFAULT);
                            result.put("base64",base64);
                        }
                        result.put("path",file.getAbsolutePath());
                        call.resolve(result);
                    }

                    @Override
                    public void onError(@NonNull ImageCaptureException exception) {
                        call.reject(exception.getMessage());
                    }
                }
        );

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
