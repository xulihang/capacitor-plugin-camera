import { PluginListenerHandle } from "@capacitor/core";

export interface CameraPreviewPlugin {
  initialize(): Promise<void>;
  getResolution(): Promise<{resolution: string}>;
  setResolution(options: {resolution: number}): Promise<void>;
  getAllCameras(): Promise<{cameras: string[]}>;
  getSelectedCamera(): Promise<{selectedCamera: string}>;
  selectCamera(options: {cameraID: string; }): Promise<void>;
  setScanRegion(options: {region:ScanRegion}): Promise<void>;
  setZoom(options: {factor: number}): Promise<void>;
  setFocus(options: {x: number, y: number}): Promise<void>;
  /**
  * Web Only
  */
  setDefaultUIElementURL(url:string): Promise<void>;
  /**
  * Web Only
  */
  setElement(ele:HTMLElement): Promise<void>;
  startCamera(): Promise<void>;
  stopCamera(): Promise<void>;
  /**
  * take a snapshot as base64.
  */
  takeSnapshot(options:{quality?:number}): Promise<{base64:string}>;
  /**
  * save a frame internally. Android and iOS only.
  */
  saveFrame(): Promise<{success:boolean}>;
  /**
  * take a snapshot on to a canvas. Web Only
  */
  takeSnapshot2(options:{canvas:HTMLCanvasElement,maxLength?:number}): Promise<{scaleRatio?:number}>;
  takePhoto(options: {pathToSave?:string,includeBase64?: boolean}): Promise<{path?:string,base64?:string,blob?:Blob}>;
  toggleTorch(options: {on: boolean}): Promise<void>;
  /**
  * get the orientation of the device.
  */
  getOrientation(): Promise<{"orientation":"PORTRAIT"|"LANDSCAPE"}>;
  startRecording(): Promise<void>;
  stopRecording(options:{includeBase64?:boolean}): Promise<{path?:string,base64?:string,blob?:Blob}>;
  setLayout(options: {top: string, left:string, width:string, height:string}): Promise<void>;
  requestCameraPermission(): Promise<void>;
  requestMicroPhonePermission(): Promise<void>;
  isOpen():Promise<{isOpen:boolean}>;
  addListener(
    eventName: 'onPlayed',
    listenerFunc: onPlayedListener,
  ): Promise<PluginListenerHandle>;
  addListener(
    eventName: 'onOrientationChanged',
    listenerFunc: onOrientationChangedListener,
  ): Promise<PluginListenerHandle>;
  removeAllListeners(): Promise<void>;
}

export type onPlayedListener = (result:{resolution:string}) => void;
export type onOrientationChangedListener = () => void;

/**
 * measuredByPercentage: 0 in pixel, 1 in percent
 */
export interface ScanRegion{
  left: number;
  top: number;
  right: number;
  bottom: number;
  measuredByPercentage: number;
}

export enum EnumResolution {
  RESOLUTION_AUTO = 0,
  RESOLUTION_480P = 1,
  RESOLUTION_720P = 2,
  RESOLUTION_1080P = 3,
  RESOLUTION_2K = 4,
  RESOLUTION_4K = 5
}
