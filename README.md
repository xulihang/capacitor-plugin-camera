# capacitor-plugin-camera

A capacitor camera plugin.

[Online demo](https://dazzling-cactus-692f14.netlify.app/)

## Supported Platforms

* Android (based on CameraX)
* iOS (based on AVCaptureSession)
* Web (based on getUserMedia with Dynamsoft Camera Enhancer)

## Versions

For Capacitor 5, use versions 1.x.

For Capacitor 6, use versions 2.x.

## Install

```bash
npm install capacitor-plugin-camera
npx cap sync
```
## Get Bitmap/UIImage via Reflection

If you are developing a plugin, you can use reflection to get the camera frames as Bitmap or UIImage on the native side.

Java:

```java
Class cls = Class.forName("com.tonyxlh.capacitor.camera.CameraPreviewPlugin");
Method m = cls.getMethod("getBitmap",null);
Bitmap bitmap = (Bitmap) m.invoke(null, null);
```

Objective-C:

```obj-c
- (UIImage*)getUIImage{
    UIImage *image = ((UIImage* (*)(id, SEL))objc_msgSend)(objc_getClass("CameraPreviewPlugin"), sel_registerName("getBitmap"));
    return image;
}
```

You have to call `saveFrame` beforehand.

## Declare Permissions

To use camera and microphone, we need to declare permissions.

Add the following to Android's `AndroidManifest.xml`:

```xml
<uses-permission android:name="android.permission.CAMERA" />
<uses-permission android:name="android.permission.RECORD_AUDIO" />
```

Add the following to iOS's `Info.plist`:

```xml
<key>NSCameraUsageDescription</key>
<string>For camera usage</string>
<key>NSMicrophoneUsageDescription</key>
<string>For video recording</string>
```

## FAQ

Why I cannot see the camera?

For native platforms, the plugin puts the native camera view behind the webview and sets the webview as transparent so that we can display HTML elements above the camera.

You may need to add the style below on your app's HTML or body element to avoid blocking the camera view:

```css
ion-content {
  --background: transparent;
}
```

In dark mode, it is neccessary to set the `--ion-blackground-color` property. You can do this with the following code:

```js
document.documentElement.style.setProperty('--ion-background-color', 'transparent');
```

## API

<docgen-index>

* [`initialize()`](#initialize)
* [`getResolution()`](#getresolution)
* [`setResolution(...)`](#setresolution)
* [`getAllCameras()`](#getallcameras)
* [`getSelectedCamera()`](#getselectedcamera)
* [`selectCamera(...)`](#selectcamera)
* [`setScanRegion(...)`](#setscanregion)
* [`setZoom(...)`](#setzoom)
* [`setFocus(...)`](#setfocus)
* [`setDefaultUIElementURL(...)`](#setdefaultuielementurl)
* [`setElement(...)`](#setelement)
* [`startCamera()`](#startcamera)
* [`stopCamera()`](#stopcamera)
* [`takeSnapshot(...)`](#takesnapshot)
* [`saveFrame()`](#saveframe)
* [`takeSnapshot2(...)`](#takesnapshot2)
* [`takePhoto(...)`](#takephoto)
* [`toggleTorch(...)`](#toggletorch)
* [`getOrientation()`](#getorientation)
* [`startRecording()`](#startrecording)
* [`stopRecording(...)`](#stoprecording)
* [`setLayout(...)`](#setlayout)
* [`requestCameraPermission()`](#requestcamerapermission)
* [`requestMicroPhonePermission()`](#requestmicrophonepermission)
* [`isOpen()`](#isopen)
* [`addListener('onPlayed', ...)`](#addlisteneronplayed)
* [`addListener('onOrientationChanged', ...)`](#addlisteneronorientationchanged)
* [`removeAllListeners()`](#removealllisteners)
* [Interfaces](#interfaces)
* [Type Aliases](#type-aliases)

</docgen-index>

<docgen-api>
<!--Update the source file JSDoc comments and rerun docgen to update the docs below-->

### initialize()

```typescript
initialize() => Promise<void>
```

--------------------


### getResolution()

```typescript
getResolution() => Promise<{ resolution: string; }>
```

**Returns:** <code>Promise&lt;{ resolution: string; }&gt;</code>

--------------------


### setResolution(...)

```typescript
setResolution(options: { resolution: number; }) => Promise<void>
```

| Param         | Type                                 |
| ------------- | ------------------------------------ |
| **`options`** | <code>{ resolution: number; }</code> |

--------------------


### getAllCameras()

```typescript
getAllCameras() => Promise<{ cameras: string[]; }>
```

**Returns:** <code>Promise&lt;{ cameras: string[]; }&gt;</code>

--------------------


### getSelectedCamera()

```typescript
getSelectedCamera() => Promise<{ selectedCamera: string; }>
```

**Returns:** <code>Promise&lt;{ selectedCamera: string; }&gt;</code>

--------------------


### selectCamera(...)

```typescript
selectCamera(options: { cameraID: string; }) => Promise<void>
```

| Param         | Type                               |
| ------------- | ---------------------------------- |
| **`options`** | <code>{ cameraID: string; }</code> |

--------------------


### setScanRegion(...)

```typescript
setScanRegion(options: { region: ScanRegion; }) => Promise<void>
```

| Param         | Type                                                           |
| ------------- | -------------------------------------------------------------- |
| **`options`** | <code>{ region: <a href="#scanregion">ScanRegion</a>; }</code> |

--------------------


### setZoom(...)

```typescript
setZoom(options: { factor: number; }) => Promise<void>
```

| Param         | Type                             |
| ------------- | -------------------------------- |
| **`options`** | <code>{ factor: number; }</code> |

--------------------


### setFocus(...)

```typescript
setFocus(options: { x: number; y: number; }) => Promise<void>
```

| Param         | Type                                   |
| ------------- | -------------------------------------- |
| **`options`** | <code>{ x: number; y: number; }</code> |

--------------------


### setDefaultUIElementURL(...)

```typescript
setDefaultUIElementURL(url: string) => Promise<void>
```

Web Only

| Param     | Type                |
| --------- | ------------------- |
| **`url`** | <code>string</code> |

--------------------


### setElement(...)

```typescript
setElement(ele: any) => Promise<void>
```

Web Only

| Param     | Type             |
| --------- | ---------------- |
| **`ele`** | <code>any</code> |

--------------------


### startCamera()

```typescript
startCamera() => Promise<void>
```

--------------------


### stopCamera()

```typescript
stopCamera() => Promise<void>
```

--------------------


### takeSnapshot(...)

```typescript
takeSnapshot(options: { quality?: number; }) => Promise<{ base64: string; }>
```

take a snapshot as base64.

| Param         | Type                               |
| ------------- | ---------------------------------- |
| **`options`** | <code>{ quality?: number; }</code> |

**Returns:** <code>Promise&lt;{ base64: string; }&gt;</code>

--------------------


### saveFrame()

```typescript
saveFrame() => Promise<{ success: boolean; }>
```

save a frame internally. Android and iOS only.

**Returns:** <code>Promise&lt;{ success: boolean; }&gt;</code>

--------------------


### takeSnapshot2(...)

```typescript
takeSnapshot2(options: { canvas: HTMLCanvasElement; maxLength?: number; }) => Promise<{ scaleRatio?: number; }>
```

take a snapshot on to a canvas. Web Only

| Param         | Type                                              |
| ------------- | ------------------------------------------------- |
| **`options`** | <code>{ canvas: any; maxLength?: number; }</code> |

**Returns:** <code>Promise&lt;{ scaleRatio?: number; }&gt;</code>

--------------------


### takePhoto(...)

```typescript
takePhoto(options: { pathToSave?: string; includeBase64?: boolean; }) => Promise<{ path?: string; base64?: string; blob?: Blob; }>
```

| Param         | Type                                                           |
| ------------- | -------------------------------------------------------------- |
| **`options`** | <code>{ pathToSave?: string; includeBase64?: boolean; }</code> |

**Returns:** <code>Promise&lt;{ path?: string; base64?: string; blob?: any; }&gt;</code>

--------------------


### toggleTorch(...)

```typescript
toggleTorch(options: { on: boolean; }) => Promise<void>
```

| Param         | Type                          |
| ------------- | ----------------------------- |
| **`options`** | <code>{ on: boolean; }</code> |

--------------------


### getOrientation()

```typescript
getOrientation() => Promise<{ "orientation": "PORTRAIT" | "LANDSCAPE"; }>
```

get the orientation of the device.

**Returns:** <code>Promise&lt;{ orientation: 'PORTRAIT' | 'LANDSCAPE'; }&gt;</code>

--------------------


### startRecording()

```typescript
startRecording() => Promise<void>
```

--------------------


### stopRecording(...)

```typescript
stopRecording(options: { includeBase64?: boolean; }) => Promise<{ path?: string; base64?: string; blob?: Blob; }>
```

| Param         | Type                                      |
| ------------- | ----------------------------------------- |
| **`options`** | <code>{ includeBase64?: boolean; }</code> |

**Returns:** <code>Promise&lt;{ path?: string; base64?: string; blob?: any; }&gt;</code>

--------------------


### setLayout(...)

```typescript
setLayout(options: { top: string; left: string; width: string; height: string; }) => Promise<void>
```

| Param         | Type                                                                       |
| ------------- | -------------------------------------------------------------------------- |
| **`options`** | <code>{ top: string; left: string; width: string; height: string; }</code> |

--------------------


### requestCameraPermission()

```typescript
requestCameraPermission() => Promise<void>
```

--------------------


### requestMicroPhonePermission()

```typescript
requestMicroPhonePermission() => Promise<void>
```

--------------------


### isOpen()

```typescript
isOpen() => Promise<{ isOpen: boolean; }>
```

**Returns:** <code>Promise&lt;{ isOpen: boolean; }&gt;</code>

--------------------


### addListener('onPlayed', ...)

```typescript
addListener(eventName: 'onPlayed', listenerFunc: onPlayedListener) => Promise<PluginListenerHandle>
```

| Param              | Type                                                          |
| ------------------ | ------------------------------------------------------------- |
| **`eventName`**    | <code>'onPlayed'</code>                                       |
| **`listenerFunc`** | <code><a href="#onplayedlistener">onPlayedListener</a></code> |

**Returns:** <code>Promise&lt;<a href="#pluginlistenerhandle">PluginListenerHandle</a>&gt;</code>

--------------------


### addListener('onOrientationChanged', ...)

```typescript
addListener(eventName: 'onOrientationChanged', listenerFunc: onOrientationChangedListener) => Promise<PluginListenerHandle>
```

| Param              | Type                                                                                  |
| ------------------ | ------------------------------------------------------------------------------------- |
| **`eventName`**    | <code>'onOrientationChanged'</code>                                                   |
| **`listenerFunc`** | <code><a href="#onorientationchangedlistener">onOrientationChangedListener</a></code> |

**Returns:** <code>Promise&lt;<a href="#pluginlistenerhandle">PluginListenerHandle</a>&gt;</code>

--------------------


### removeAllListeners()

```typescript
removeAllListeners() => Promise<void>
```

--------------------


### Interfaces


#### ScanRegion

measuredByPercentage: 0 in pixel, 1 in percent

| Prop                       | Type                |
| -------------------------- | ------------------- |
| **`left`**                 | <code>number</code> |
| **`top`**                  | <code>number</code> |
| **`right`**                | <code>number</code> |
| **`bottom`**               | <code>number</code> |
| **`measuredByPercentage`** | <code>number</code> |


#### PluginListenerHandle

| Prop         | Type                                      |
| ------------ | ----------------------------------------- |
| **`remove`** | <code>() =&gt; Promise&lt;void&gt;</code> |


### Type Aliases


#### onPlayedListener

<code>(result: { resolution: string; }): void</code>


#### onOrientationChangedListener

<code>(): void</code>

</docgen-api>
