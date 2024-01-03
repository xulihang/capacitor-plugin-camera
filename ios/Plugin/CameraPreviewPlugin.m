#import <Foundation/Foundation.h>
#import <Capacitor/Capacitor.h>

// Define the plugin using the CAP_PLUGIN Macro, and
// each method the plugin supports using the CAP_PLUGIN_METHOD macro.
CAP_PLUGIN(CameraPreviewPlugin, "CameraPreview",
           CAP_PLUGIN_METHOD(initialize, CAPPluginReturnPromise);
           CAP_PLUGIN_METHOD(getResolution, CAPPluginReturnPromise);
           CAP_PLUGIN_METHOD(setResolution, CAPPluginReturnPromise);
           CAP_PLUGIN_METHOD(getAllCameras, CAPPluginReturnPromise);
           CAP_PLUGIN_METHOD(getSelectedCamera, CAPPluginReturnPromise);
           CAP_PLUGIN_METHOD(getOrientation, CAPPluginReturnPromise);
           CAP_PLUGIN_METHOD(selectCamera, CAPPluginReturnPromise);
           CAP_PLUGIN_METHOD(setScanRegion, CAPPluginReturnPromise);
           CAP_PLUGIN_METHOD(setZoom, CAPPluginReturnPromise);
           CAP_PLUGIN_METHOD(setFocus, CAPPluginReturnPromise);
           CAP_PLUGIN_METHOD(setLayout, CAPPluginReturnPromise);
           CAP_PLUGIN_METHOD(saveFrame, CAPPluginReturnPromise);
           CAP_PLUGIN_METHOD(startCamera, CAPPluginReturnPromise);
           CAP_PLUGIN_METHOD(stopCamera, CAPPluginReturnPromise);
           CAP_PLUGIN_METHOD(startRecording, CAPPluginReturnPromise);
           CAP_PLUGIN_METHOD(stopRecording, CAPPluginReturnPromise);
           CAP_PLUGIN_METHOD(takeSnapshot, CAPPluginReturnPromise);
           CAP_PLUGIN_METHOD(takePhoto, CAPPluginReturnPromise);
           CAP_PLUGIN_METHOD(toggleTorch, CAPPluginReturnPromise);
           CAP_PLUGIN_METHOD(requestCameraPermission, CAPPluginReturnPromise);
           CAP_PLUGIN_METHOD(requestMicroPhonePermission, CAPPluginReturnPromise);
           CAP_PLUGIN_METHOD(isOpen, CAPPluginReturnPromise);
)
