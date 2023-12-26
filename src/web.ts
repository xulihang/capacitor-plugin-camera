import { WebPlugin } from '@capacitor/core';
import { CameraEnhancer, DCEFrame, PlayCallbackInfo } from 'dynamsoft-camera-enhancer';
import { CameraPreviewPlugin, EnumResolution, ScanRegion } from './definitions';

CameraEnhancer.defaultUIElementURL = "https://cdn.jsdelivr.net/npm/dynamsoft-camera-enhancer@3.1.0/dist/dce.ui.html";

export class CameraPreviewWeb extends WebPlugin implements CameraPreviewPlugin {
  private camera:CameraEnhancer | undefined;
  async setDefaultUIElementURL(url: string): Promise<void> {
    CameraEnhancer.defaultUIElementURL = url;
  }

  async initialize(): Promise<void> {
    this.camera = await CameraEnhancer.createInstance();
    this.camera.on("played", (playCallBackInfo:PlayCallbackInfo) => {
      this.notifyListeners("onPlayed", {resolution:playCallBackInfo.width+"x"+playCallBackInfo.height});
    });
    await this.camera.setUIElement(CameraEnhancer.defaultUIElementURL);

    this.camera.getUIElement().getElementsByClassName("dce-btn-close")[0].remove();
    this.camera.getUIElement().getElementsByClassName("dce-sel-camera")[0].remove();
    this.camera.getUIElement().getElementsByClassName("dce-sel-resolution")[0].remove();
    this.camera.getUIElement().getElementsByClassName("dce-msg-poweredby")[0].remove();
  }

  async getResolution(): Promise<{ resolution: string; }> {
    if (this.camera) {
      let rsl = this.camera.getResolution();
      let resolution:string = rsl[0] + "x" + rsl[1];
      return {resolution: resolution};
    }else{
      throw new Error('DCE not initialized');
    }
  }

  async setResolution(options: { resolution: number; }): Promise<void> {
    if (this.camera) {
      let res = options.resolution;
      let width = 1280;
      let height = 720;
      if (res == EnumResolution.RESOLUTION_480P){
         width = 640;
         height = 480;
      } else if (res == EnumResolution.RESOLUTION_720P){
        width = 1280;
        height = 720;
      } else if (res == EnumResolution.RESOLUTION_1080P){
        width = 1920;
        height = 1080;
      } else if (res == EnumResolution.RESOLUTION_2K){
        width = 2560;
        height = 1440;
      } else if (res == EnumResolution.RESOLUTION_4K){
        width = 3840;
        height = 2160;
      }
      await this.camera.setResolution(width,height);
      return;
    } else {
      throw new Error('DCE not initialized');
    }
  }

  async getAllCameras(): Promise<{ cameras: string[]; }> {
    if (this.camera) {
      let cameras = await this.camera.getAllCameras();
      let labels:string[] = [];
      cameras.forEach(camera => {
        labels.push(camera.label);
      });
      return {cameras:labels};
    }else {
      throw new Error('DCE not initialized');
    }
  }

  async getSelectedCamera(): Promise<{ selectedCamera: string; }> {
    if (this.camera) {
      let cameraInfo = this.camera.getSelectedCamera();
      return {selectedCamera:cameraInfo.label};
    }else {
      throw new Error('DCE not initialized');
    }
  }

  async selectCamera(options: { cameraID: string; }): Promise<void> {
    if (this.camera) {
      let cameras = await this.camera.getAllCameras()
      for (let index = 0; index < cameras.length; index++) {
        const camera = cameras[index];
        if (camera.label === options.cameraID) {
          await this.camera.selectCamera(camera);
          return;
        }
      }
    }else {
      throw new Error('DCE not initialized');
    }
  }

  async setScanRegion(options: { region: ScanRegion; }): Promise<void> {
    if (this.camera){
      this.camera.setScanRegion({
        regionLeft:options.region.left,
        regionTop:options.region.top,
        regionRight:options.region.right,
        regionBottom:options.region.bottom,
        regionMeasuredByPercentage: options.region.measuredByPercentage
      })
    }else {
      throw new Error('DCE not initialized');
    }
  }

  async setZoom(options: { factor: number; }): Promise<void> {
    if (this.camera) {
      await this.camera.setZoom(options.factor);
      return;
    }else {
      throw new Error('DCE not initialized');
    }
  }

  async setFocus(): Promise<void> {
    throw new Error('Method not implemented.');
  }

  async toggleTorch(options: { on: boolean; }): Promise<void> {
    if (this.camera) {
      try{
        if (options["on"]){
          await this.camera.turnOnTorch();
        }else{
          await this.camera.turnOffTorch();
        }
      } catch (e){
        throw new Error("Torch unsupported");
      }
    }
  }

  async startCamera(): Promise<void> {
    if (this.camera) {
      await this.camera.open(true);
    }else {
      throw new Error('DCE not initialized');
    }
  }

  async stopCamera(): Promise<void> {
    if (this.camera) {
      this.camera.close(true);
    }else {
      throw new Error('DCE not initialized');
    }
  }

  async pauseCamera(): Promise<void> {
    if (this.camera) {
      this.camera.pause();
    }else {
      throw new Error('DCE not initialized');
    }
  }

  async resumeCamera(): Promise<void> {
    if (this.camera) {
      this.camera.resume();
    }else {
      throw new Error('DCE not initialized');
    }
  }

  async isOpen(): Promise<{isOpen:boolean}> {
    if (this.camera) {
      return {isOpen:this.camera.isOpen()};
    }else{
      throw new Error('DCE not initialized');
    }
  }

  async takeSnapshot(options:{quality?:number}): Promise<{ base64: string;}> {
    if (this.camera) {
      let desiredQuality = 85;
      if (options.quality) {
        desiredQuality = options.quality;
      }
      let dataURL = this.camera.getFrame().toCanvas().toDataURL('image/jpeg',desiredQuality);
      let base64 = dataURL.replace("data:image/jpeg;base64,","");
      return {base64:base64};
    }else{
      throw new Error('DCE not initialized');
    }
  }

  async takeSnapshot2(): Promise<{ frame: DCEFrame; }> {
    if (this.camera) {
      let frame = this.camera.getFrame();
      return {frame:frame};
    }else {
      throw new Error('DCE not initialized');
    }
  }

  async takePhoto(): Promise<{ base64: string; }> {
    if (this.camera) {
      let dataURL = this.camera.getFrame().toCanvas().toDataURL();
      return {base64:dataURL};
    }else {
      throw new Error('DCE not initialized');
    }
  }

  async requestCameraPermission(): Promise<void> {
    const constraints = {video: true, audio: false};
    const stream = await navigator.mediaDevices.getUserMedia(constraints);
    const tracks = stream.getTracks();
    for (let i=0;i<tracks.length;i++) {
      const track = tracks[i];
      track.stop();  // stop the opened camera
    }
  }
}
