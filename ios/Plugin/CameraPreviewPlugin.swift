import Foundation
import UIKit
import Capacitor
import AVFoundation

/**
 * Please read the Capacitor iOS Plugin Development Guide
 * here: https://capacitorjs.com/docs/plugins/ios
 */
@objc(CameraPreviewPlugin)
public class CameraPreviewPlugin: CAPPlugin, AVCaptureVideoDataOutputSampleBufferDelegate, AVCapturePhotoCaptureDelegate {
    var previewView: PreviewView!
    var captureSession: AVCaptureSession!
    var photoOutput: AVCapturePhotoOutput!
    var videoOutput: AVCaptureVideoDataOutput!
    var takeSnapshotCall: CAPPluginCall! = nil
    var takePhotoCall: CAPPluginCall! = nil
    var getResolutionCall: CAPPluginCall! = nil
    var saveFrameCall: CAPPluginCall! = nil
    static public var frameTaken:UIImage!
    var triggerPlayRequired = false
    var facingBack = true
    var videoInput:AVCaptureDeviceInput!
    var scanRegion:ScanRegion! = nil
    @objc func initialize(_ call: CAPPluginCall) {
        // Initialize a camera view for previewing video.
        NotificationCenter.default.addObserver(self, selector: #selector(rotated), name: UIDevice.orientationDidChangeNotification, object: nil)
        DispatchQueue.main.sync {
            self.previewView = PreviewView.init(frame: (bridge?.viewController?.view.bounds)!)
            self.webView!.superview!.insertSubview(self.previewView, belowSubview: self.webView!)
            initializeCaptureSession()
        }
        call.resolve()
    }
    
    @objc func rotated() {
        let bounds = self.webView?.bounds
        if bounds != nil {
            self.previewView.frame = bounds!
            if UIDevice.current.orientation == UIDeviceOrientation.portrait {
                self.previewView.videoPreviewLayer.connection?.videoOrientation = .portrait
            }else if UIDevice.current.orientation == UIDeviceOrientation.landscapeLeft {
                self.previewView.videoPreviewLayer.connection?.videoOrientation = .landscapeRight
            }else if UIDevice.current.orientation == UIDeviceOrientation.landscapeRight {
                self.previewView.videoPreviewLayer.connection?.videoOrientation = .landscapeLeft
            }
        }
        notifyListeners("onOrientationChanged",data: nil)
    }
    
    @objc func startCamera(_ call: CAPPluginCall) {
        makeWebViewTransparent()
        if self.captureSession != nil {
            DispatchQueue.main.sync {
                self.captureSession.startRunning()
                triggerOnPlayed()
            }
        }else{
            call.reject("Camera not initialized")
            return
        }
        call.resolve()
    }
    
    func initializeCaptureSession(){
        // Create the capture session.
        self.captureSession = AVCaptureSession()

        // Find the default audio device.
        guard let videoDevice = AVCaptureDevice.default(for: .video) else { return }
        
        do {
            // Wrap the video device in a capture device input.
            self.videoInput = try AVCaptureDeviceInput(device: videoDevice)
            // If the input can be added, add it to the session.
            if self.captureSession.canAddInput(videoInput) {
                self.captureSession.addInput(videoInput)
                self.previewView.videoPreviewLayer.session = self.captureSession
                self.previewView.videoPreviewLayer.videoGravity = AVLayerVideoGravity.resizeAspectFill
                
                self.videoOutput = AVCaptureVideoDataOutput.init()
                if self.captureSession.canAddOutput(self.videoOutput) {
                    self.captureSession.addOutput(videoOutput)
                }
                
                self.photoOutput = AVCapturePhotoOutput()
                self.photoOutput.isHighResolutionCaptureEnabled = true
                //self.photoOutput.
                if self.captureSession.canAddOutput(self.photoOutput) {
                    self.captureSession.addOutput(photoOutput)
                }
                
                self.captureSession.sessionPreset = AVCaptureSession.Preset.hd1280x720
                
                var queue:DispatchQueue
                queue = DispatchQueue(label: "queue")
                self.videoOutput.setSampleBufferDelegate(self as AVCaptureVideoDataOutputSampleBufferDelegate, queue: queue)
                self.videoOutput.videoSettings = [kCVPixelBufferPixelFormatTypeKey : kCVPixelFormatType_32BGRA] as [String : Any]
            }
            
        } catch {
            // Configuration failed. Handle error.
        }
    }
    func takePhotoWithAVFoundation(){
        //self.captureSession.sessionPreset = AVCaptureSession.Preset.hd4K3840x2160
        let photoSettings: AVCapturePhotoSettings
        photoSettings = AVCapturePhotoSettings()
        photoSettings.isHighResolutionPhotoEnabled = true
        
        self.photoOutput.capturePhoto(with: photoSettings, delegate: self)
    }
    
    public func photoOutput(_ output: AVCapturePhotoOutput, didFinishProcessingPhoto photo: AVCapturePhoto, error: Error?) {
        if let error = error {
            print("Error:", error)
        } else {
            if let imageData = photo.fileDataRepresentation() {
                var ret = PluginCallResultData()
                var url:URL
                let pathToSave = takePhotoCall.getString("pathToSave", "")
                if pathToSave == "" {
                    url = FileManager.default.temporaryDirectory
                        .appendingPathComponent(UUID().uuidString)
                        .appendingPathExtension("jpeg")
                }else{
                    url = URL(string: "file://\(pathToSave)")!
                }
                if takePhotoCall.getBool("includeBase64", false) {
                    let image = UIImage(data: imageData)
                    let base64 = getBase64FromImage(image: image!, quality: 100.0)
                    ret["base64"] = base64
                }
                do {
                    
                    try imageData.write(to: url)
                    ret["path"] = url.path
                } catch {
                    print(error)
                }
                takePhotoCall.resolve(ret)
                takePhotoCall = nil
            }
        }
    }
    
    public func captureOutput(_ output: AVCaptureOutput, didOutput sampleBuffer: CMSampleBuffer, from connection: AVCaptureConnection)
    {
        if triggerPlayRequired || getResolutionCall != nil {
            var ret = PluginCallResultData()
            let imageBuffer:CVImageBuffer = CMSampleBufferGetImageBuffer(sampleBuffer)!
                    CVPixelBufferLockBaseAddress(imageBuffer, .readOnly)
            let width = CVPixelBufferGetWidth(imageBuffer)
            let height = CVPixelBufferGetHeight(imageBuffer)
            let res = String(width)+"x"+String(height)
            ret["resolution"] = res
            if triggerPlayRequired {
                notifyListeners("onPlayed", data: ret)
                triggerPlayRequired = false
            }
            if getResolutionCall != nil {
                getResolutionCall.resolve(ret)
                getResolutionCall = nil
            }
            
        }
        if takeSnapshotCall != nil || saveFrameCall != nil {
            guard let imageBuffer = CMSampleBufferGetImageBuffer(sampleBuffer) else {
                print("Failed to get image buffer from sample buffer.")
                return
            }
            let ciImage = CIImage(cvPixelBuffer: imageBuffer)
            guard let cgImage = CIContext().createCGImage(ciImage, from: ciImage.extent) else {
                print("Failed to create bitmap from image.")
                return
            }
            var degree = 0;
            if UIDevice.current.orientation == UIDeviceOrientation.portrait {
                if connection.videoOrientation == AVCaptureVideoOrientation.landscapeRight || connection.videoOrientation == AVCaptureVideoOrientation.landscapeLeft  {
                    degree = 90
                }
            }else if UIDevice.current.orientation == UIDeviceOrientation.landscapeRight {
                degree = 180
            }
            
            let image = UIImage(cgImage: cgImage)
            let rotated = rotatedUIImage(image: image, degree: degree)
            var normalized = normalizedImage(rotated)
            if self.scanRegion != nil {
                normalized = croppedUIImage(image: normalized, scanRegion: self.scanRegion)
            }
            if takeSnapshotCall != nil {
                let base64 = getBase64FromImage(image: normalized, quality: 100.0);
                var ret = PluginCallResultData()
                ret["base64"] = base64
                takeSnapshotCall.resolve(ret)
                takeSnapshotCall = nil
            }
            if saveFrameCall != nil {
                CameraPreviewPlugin.frameTaken = normalized
                var ret = PluginCallResultData()
                ret["success"] = true
                saveFrameCall.resolve(ret)
                saveFrameCall = nil
            }
        }
    }
    
    func makeWebViewTransparent(){
        DispatchQueue.main.async {
           self.bridge?.webView!.isOpaque = false
           self.bridge?.webView!.backgroundColor = UIColor.clear
           self.bridge?.webView!.scrollView.backgroundColor = UIColor.clear
       }
    }
    func restoreWebViewBackground(){
        DispatchQueue.main.async {
           self.bridge?.webView!.isOpaque = true
           self.bridge?.webView!.backgroundColor = UIColor.white
           self.bridge?.webView!.scrollView.backgroundColor = UIColor.white
       }
    }
    
    @objc func toggleTorch(_ call: CAPPluginCall) {
        let device = videoInput.device
        if device.hasTorch {
            do {
                try device.lockForConfiguration()
                if call.getBool("on", true) == true {
                    device.torchMode = .on
                } else {
                    device.torchMode = .off
                }
                device.unlockForConfiguration()
            } catch {
                print("Torch could not be used")
            }
        }
        call.resolve()
    }
    
    @objc func stopCamera(_ call: CAPPluginCall) {
        restoreWebViewBackground()
        DispatchQueue.main.sync {
            self.captureSession.stopRunning()
        }
        call.resolve()
    }
    
    
    @objc func setResolution(_ call: CAPPluginCall) {
        let res = call.getInt("resolution", 5)
        let running = self.captureSession.isRunning
        if running {
            self.captureSession.stopRunning()
        }
        if (res == 1){
            self.captureSession.sessionPreset = AVCaptureSession.Preset.vga640x480
        } else if (res == 2){
            self.captureSession.sessionPreset = AVCaptureSession.Preset.hd1280x720
        } else if (res == 3 && facingBack == true){
            self.captureSession.sessionPreset = AVCaptureSession.Preset.hd1920x1080
        } else if (res == 5 && facingBack == true){
            self.captureSession.sessionPreset = AVCaptureSession.Preset.hd4K3840x2160
        }
        if running {
            self.captureSession.startRunning()
            triggerOnPlayed()
        }
        call.resolve()
    }
    
    @objc func getResolution(_ call: CAPPluginCall) {
        call.keepAlive = true
        getResolutionCall = call
    }
    
    @objc func triggerOnPlayed() {
        triggerPlayRequired = true
    }
    
    @objc func getAllCameras(_ call: CAPPluginCall) {
        var ret = PluginCallResultData()
        let array = NSMutableArray();
        array.add("Front-Facing Camera")
        array.add("Back-Facing Camera")
        ret["cameras"] = array
        call.resolve(ret)
    }
    
    @objc func getSelectedCamera(_ call: CAPPluginCall) {
        var ret = PluginCallResultData()
        if facingBack {
            ret["selectedCamera"] = "Back-Facing Camera"
        }else{
            ret["selectedCamera"] = "Front-Facing Camera"
        }
        call.resolve(ret)
    }
    
    @objc func getOrientation(_ call: CAPPluginCall) {
        var ret = PluginCallResultData()
        if UIDevice.current.orientation.isPortrait {
            ret["orientation"] = "PORTRAIT"
        }else{
            ret["orientation"] = "LANDSCAPE"
        }
        call.resolve(ret)
    }
    
    @objc func selectCamera(_ call: CAPPluginCall) {
        let isRunning = self.captureSession.isRunning
        if isRunning {
            self.captureSession.stopRunning()
        }
        let cameraID = call.getString("cameraID", "Back-Facing Camera")
        if cameraID == "Back-Facing Camera" && facingBack == false {
            self.captureSession.removeInput(self.videoInput)
            let videoDevice = captureDevice(with: AVCaptureDevice.Position.back)
            self.videoInput = try? AVCaptureDeviceInput(device: videoDevice!)
            self.captureSession.addInput(self.videoInput)
            facingBack = true
        }
        if cameraID == "Front-Facing Camera" && facingBack == true {
            self.captureSession.removeInput(self.videoInput)
            self.captureSession.sessionPreset = AVCaptureSession.Preset.hd1280x720
            let videoDevice = captureDevice(with: AVCaptureDevice.Position.front)
            self.videoInput = try? AVCaptureDeviceInput(device: videoDevice!)
            self.captureSession.addInput(self.videoInput)
            facingBack = false
        }
        if isRunning {
            self.captureSession.startRunning()
        }
        triggerOnPlayed()
        call.resolve()
    }
    
    @objc func setLayout(_ call: CAPPluginCall) {
        if (self.previewView == nil){
            call.reject("not initialized")
        }else{
            DispatchQueue.main.async {
                let left = self.getLayoutValue(call.getString("left")!,true)
                let top = self.getLayoutValue(call.getString("top")!,false)
                let width = self.getLayoutValue(call.getString("width")!,true)
                let height = self.getLayoutValue(call.getString("height")!,false)
                self.previewView.frame = CGRect.init(x: left, y: top, width: width, height: height)
            }
            call.resolve()
        }
    }
    
    func getLayoutValue(_ value: String,_ isWidth: Bool) -> CGFloat {
       if value.contains("%") {
           let percent = CGFloat(Float(String(value[..<value.lastIndex(of: "%")!]))!/100)
           if isWidth {
               return percent * (self.bridge?.webView!.frame.width)!
           }else{
               return percent * (self.bridge?.webView!.frame.height)!
           }
       }
       if value.contains("px") {
           let num = CGFloat(Float(String(value[..<value.lastIndex(of: "p")!]))!)
           return num
       }
       return CGFloat(Float(value)!)
   }

    func captureDevice(with position: AVCaptureDevice.Position) -> AVCaptureDevice? {

        let devices = AVCaptureDevice.DiscoverySession(deviceTypes: [ .builtInWideAngleCamera, .builtInMicrophone, .builtInDualCamera, .builtInTelephotoCamera ], mediaType: AVMediaType.video, position: .unspecified).devices

        for device in devices {
            if device.position == position {
                return device
            }
        }

        return nil
    }
    
    @objc func setScanRegion(_ call: CAPPluginCall) {
        let region = call.getObject("region")
        self.scanRegion = ScanRegion()
        self.scanRegion.top = region?["top"] as! Int
        self.scanRegion.right = region?["right"] as! Int
        self.scanRegion.left = region?["left"] as! Int
        self.scanRegion.bottom = region?["bottom"] as! Int
        self.scanRegion.measuredByPercentage = region?["measuredByPercentage"] as! Int
        call.resolve()
    }
    
    @objc func setZoom(_ call: CAPPluginCall) {
        let device = videoInput.device
        do {
            try device.lockForConfiguration()
            var factor:CGFloat = CGFloat(call.getFloat("factor") ?? 1.0)
            factor = max(factor, device.minAvailableVideoZoomFactor)
            factor = min(factor, device.maxAvailableVideoZoomFactor)
            device.videoZoomFactor = factor
            device.unlockForConfiguration()
        } catch {
            print("Zoom could not be used")
        }
        call.resolve()
    }
    
    @objc func setFocus(_ call: CAPPluginCall) {
        let x = call.getFloat("x", -1.0);
        let y = call.getFloat("y", -1.0);
        if x != -1.0 && y != -1.0 {
            let device = videoInput.device
            do {
                try device.lockForConfiguration()
                device.focusPointOfInterest = CGPoint(x: CGFloat(x), y: CGFloat(y))
                device.unlockForConfiguration()
            } catch {
                print("Focues could not be used")
            }
        }
        call.resolve()
    }
    
    @objc func requestCameraPermission(_ call: CAPPluginCall) {
        call.resolve()
    }

    @objc func requestMicroPhonePermission(_ call: CAPPluginCall) {
        call.resolve()
    }
    
    @objc func isOpen(_ call: CAPPluginCall) {
        var ret = PluginCallResultData()
        ret["isOpen"] = self.captureSession.isRunning
        call.resolve(ret)
    }
    
    @objc static func getBitmap() -> UIImage? {
        return frameTaken
    }
    
    @objc func saveFrame(_ call: CAPPluginCall) {
        call.keepAlive = true
        saveFrameCall = call
    }
    
    @objc func takeSnapshot(_ call: CAPPluginCall) {
        call.keepAlive = true
        takeSnapshotCall = call
    }
    
    func croppedUIImage(image:UIImage,scanRegion:ScanRegion) -> UIImage {
        let cgImage = image.cgImage
        let imgWidth = Double(cgImage!.width)
        let imgHeight = Double(cgImage!.height)
        var regionLeft = Double(scanRegion.left)
        var regionTop = Double(scanRegion.top)
        var regionWidth = Double(scanRegion.right - scanRegion.left)
        var regionHeight = Double(scanRegion.bottom - scanRegion.top)
        if scanRegion.measuredByPercentage == 1 {
            regionLeft = regionLeft / 100  * imgWidth
            regionTop = regionTop / 100  * imgHeight
            regionWidth = regionWidth / 100  * imgWidth
            regionHeight = regionHeight / 100  * imgHeight
        }
        
        // The cropRect is the rect of the image to keep,
        // in this case centered
        let cropRect = CGRect(
            x: regionLeft,
            y: regionTop,
            width: regionWidth,
            height: regionHeight
        ).integral

        let cropped = cgImage?.cropping(
            to: cropRect
        )!
        let image = UIImage(cgImage: cropped!)
        return image
    }
    
    func rotatedUIImage(image:UIImage, degree: Int) -> UIImage {
        var rotatedImage = UIImage()
        switch degree
        {
            case 90:
                rotatedImage = UIImage(cgImage: image.cgImage!, scale: 1.0, orientation: .right)
            case 180:
                rotatedImage = UIImage(cgImage: image.cgImage!, scale: 1.0, orientation: .down)
            default:
                return image
        }
        return rotatedImage
    }
    
    
    
    func normalizedImage(_ image:UIImage) -> UIImage {
        if image.imageOrientation == UIImage.Orientation.up {
            return image
        }
        UIGraphicsBeginImageContextWithOptions(image.size, false, image.scale)
        image.draw(in: CGRect(x:0,y:0,width:image.size.width,height:image.size.height))
        let normalized = UIGraphicsGetImageFromCurrentImageContext()!
        UIGraphicsEndImageContext();
        return normalized
    }
    
    func getBase64FromImage(image:UIImage, quality: CGFloat) -> String{
       let dataTmp = image.jpegData(compressionQuality: quality)
       if let data = dataTmp {
           return data.base64EncodedString()
       }
       return ""
    }
    
    @objc func takePhoto(_ call: CAPPluginCall) {
        call.keepAlive = true
        takePhotoCall = call
        takePhotoWithAVFoundation()
    }
    
}
