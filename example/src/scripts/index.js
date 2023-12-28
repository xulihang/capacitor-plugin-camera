import '../styles/index.scss';
import { CameraPreview } from "capacitor-plugin-camera";
import { Capacitor } from '@capacitor/core';

console.log('webpack starterkit');

let onPlayedListener;
let onOrientationChangedListener;
let captureBtn = document.getElementById("captureButton");
let takePhotoButton = document.getElementById("takePhotoButton");
let zoominBtn = document.getElementById("zoominButton");
let zoomoutBtn = document.getElementById("zoomoutButton");
let startBtn = document.getElementById("startBtn");
let toggleTorchBtn = document.getElementById("toggleTorchButton");
startBtn.addEventListener("click",startCamera);
captureBtn.addEventListener("click",captureAndClose);
takePhotoButton.addEventListener("click",takePhotoAndClose);
zoominBtn.addEventListener("click",zoomin);
zoomoutBtn.addEventListener("click",zoomout);
toggleTorchBtn.addEventListener("click",toggleTorch);
document.getElementsByClassName("overlay")[0].addEventListener("click",function(e){
  displayFocusHint(e);
})

let torchStatus = false;

initialize();

async function initialize(){
  startBtn.innerText = "Initializing...";
  if (!Capacitor.isNativePlatform()) {
    await CameraPreview.setElement(document.getElementsByClassName("camera")[0]);
  }
  await CameraPreview.initialize();
  if (onPlayedListener) {
    await onPlayedListener.remove();
  }
  if (onOrientationChangedListener) {
    await onOrientationChangedListener.remove();
  }
  onPlayedListener = await CameraPreview.addListener('onPlayed', async (res) => {
    console.log("onPlayed");
    console.log(res);
    updateResolutionSelect(res.resolution);
    updateCameraSelect();
    updateViewfinder(res.resolution);
    updateOverlay(res.resolution);
  });
  onOrientationChangedListener = await CameraPreview.addListener('onOrientationChanged',async () => {
    console.log("onOrientationChanged");
    setTimeout(updateViewfinder,500);
    setTimeout(updateOverlay,500);
  });
  await CameraPreview.requestCameraPermission();
  await CameraPreview.setScanRegion({region:{top:20,left:10,right:90,bottom:60,measuredByPercentage:1}});
  await loadCameras();
  loadResolutions();
  startBtn.innerText = "Start Camera";
  startBtn.disabled = "";
}

async function updateViewfinder(res){
  if (!res) {
    res = (await CameraPreview.getResolution()).resolution;
  }
  let width = res.split("x")[0];
  let height = res.split("x")[1];
  if (Capacitor.isNativePlatform()) {
    let orientation = (await CameraPreview.getOrientation()).orientation;
    if (orientation === "PORTRAIT") {
      width = res.split("x")[1];
      height = res.split("x")[0];
    }
  }
  let viewFinder = document.querySelector("view-finder");
  viewFinder.width = width;
  viewFinder.height = height;
  viewFinder.left = width * 0.1;
  viewFinder.top = height * 0.2;
  viewFinder.right = width * 0.9;
  viewFinder.bottom = height * 0.6;
}

async function updateOverlay(res){
  if (!res) {
    res = (await CameraPreview.getResolution()).resolution;
  }
  let width = res.split("x")[0];
  let height = res.split("x")[1];
  if (Capacitor.isNativePlatform()) {
    let orientation = (await CameraPreview.getOrientation()).orientation;
    if (orientation === "PORTRAIT") {
      width = res.split("x")[1];
      height = res.split("x")[0];
    }
  }
  let overlay = document.getElementsByClassName("overlay")[0];
  overlay.setAttribute("viewBox","0 0 "+width+" "+height);
}

async function startCamera(){
  await CameraPreview.startCamera();
  toggleControlsDisplay(true);
}

function toggleControlsDisplay(show){
  if (show) {
    document.getElementsByClassName("home")[0].style.display = "none";
    document.getElementsByClassName("controls")[0].style.display = "";
  }else {
    document.getElementsByClassName("home")[0].style.display = "";
    document.getElementsByClassName("controls")[0].style.display = "none";
  }
}

async function captureAndClose(){
  let result = await CameraPreview.takeSnapshot({quality:85});
  let base64 = result.base64;
  document.getElementById("captured").src = "data:image/jpeg;base64," + base64;
  await CameraPreview.stopCamera();
  toggleControlsDisplay(false);
}

async function takePhotoAndClose(){
  let result = await CameraPreview.takePhoto({includeBase64:true});
  if (result.base64) {
    document.getElementById("captured").src = "data:image/jpeg;base64," + result.base64;
  }else if (result.blob){
    document.getElementById("captured").src = URL.createObjectURL(result.blob);
  }
  await CameraPreview.stopCamera();
  toggleControlsDisplay(false);
}

async function loadCameras(){
  let cameraSelect = document.getElementById("cameraSelect");
  cameraSelect.innerHTML = "";
  let result = await CameraPreview.getAllCameras();
  let cameras = result.cameras;
  for (let i = 0; i < cameras.length; i++) {
    cameraSelect.appendChild(new Option(cameras[i], i));
  }
  cameraSelect.addEventListener("change", async function() {
    console.log("camera changed");
    let camSelect = document.getElementById("cameraSelect");
    await CameraPreview.selectCamera({cameraID:camSelect.selectedOptions[0].label});
  });
}

function loadResolutions(){
  let resSelect = document.getElementById("resolutionSelect");
  resSelect.innerHTML = "";
  resSelect.appendChild(new Option("ask 480P", 1));
  resSelect.appendChild(new Option("ask 720P", 2));
  resSelect.appendChild(new Option("ask 1080P", 3));
  resSelect.appendChild(new Option("ask 2K", 4));
  resSelect.appendChild(new Option("ask 4K", 5));
  resSelect.addEventListener("change", async function() {
    let resSelect = document.getElementById("resolutionSelect");
    let lbl = resSelect.selectedOptions[0].label;
    if (lbl.indexOf("ask") != -1) {
      let res = parseInt(resSelect.selectedOptions[0].value);
      await CameraPreview.setResolution({resolution:res});
    }
  });
}

async function updateResolutionSelect(newRes){
  let resSelect = document.getElementById("resolutionSelect");
  for (let index = resSelect.options.length - 1; index >=0 ; index--) {
    let option = resSelect.options[index];
    if (option.label.indexOf("got") != -1) {
      resSelect.removeChild(option);
    }
  }
  resSelect.appendChild(new Option("got "+newRes,"got "+newRes));
  resSelect.selectedIndex = resSelect.length - 1;
}

async function updateCameraSelect(){
  let cameraSelect = document.getElementById("cameraSelect");
  let selectedCamera = (await CameraPreview.getSelectedCamera()).selectedCamera;
  for (let i = 0; i < cameraSelect.options.length; i++) {
    if (cameraSelect.options[i].label === selectedCamera) {
      cameraSelect.selectedIndex = i;
      return;
    }
  }
}

async function zoomin(){
  await CameraPreview.setZoom({factor:2.5});
}

async function zoomout(){
  await CameraPreview.setZoom({factor:1.0});
}

async function toggleTorch(){
  try {
    let desiredStatus = !torchStatus;
    await CameraPreview.toggleTorch({on:desiredStatus});
    torchStatus = desiredStatus;   
  } catch (error) {
    alert(error);
  }
}

function displayFocusHint(e){
  clearFocusHint();
  
  let overlay = document.getElementsByClassName("overlay")[0];
  let coord = getMousePosition(e,document.getElementsByClassName("overlay")[0]);
  let svgX = coord.x;
  let svgY = coord.y;
  let polygon = document.createElementNS("http://www.w3.org/2000/svg","polygon");
  let lr = {};
  let padding = 25;
  lr.x1 = svgX - padding;
  lr.y1 = svgY - padding;
  lr.x2 = svgX + padding;
  lr.y2 = svgY - padding;
  lr.x3 = svgX + padding;
  lr.y3 = svgY + padding;
  lr.x4 = svgX - padding;
  lr.y4 = svgY + padding;
  polygon.setAttribute("points",getPointsData(lr));
  polygon.setAttribute("class","focus-polygon");
  overlay.append(polygon);

  let viewBox = overlay.getAttribute("viewBox");
  let frameWidth = viewBox.split(" ")[2];
  let frameHeight = viewBox.split(" ")[3];
  let x = e.clientX/document.getElementsByClassName("overlay")[0].clientWidth;
  let y = e.clientY/document.getElementsByClassName("overlay")[0].clientHeight;
  x = parseInt(x*frameWidth);
  y = parseInt(y*frameHeight);
  console.log(x);
  console.log(y);
  CameraPreview.setFocus({x: x, y: y})
  setTimeout(clearFocusHint, 2000)
}

function getPointsData(lr){
  let pointsData = lr.x1+","+lr.y1 + " ";
  pointsData = pointsData+ lr.x2+","+lr.y2 + " ";
  pointsData = pointsData+ lr.x3+","+lr.y3 + " ";
  pointsData = pointsData+ lr.x4+","+lr.y4;
  return pointsData;
}

function clearFocusHint(){
  let overlay = document.getElementsByClassName("overlay")[0];
  clearElements(overlay,"polygon");
}

function clearElements(parent, tagName){
  let elements = parent.getElementsByTagName(tagName);
  for (let index = elements.length - 1; index >= 0; index--) {
    const element = elements[index];
    element.remove();
  }
}

 //Convert the screen coordinates to the SVG's coordinates from https://www.petercollingridge.co.uk/tutorials/svg/interactive/dragging/
 function getMousePosition(e,svg) {
  let CTM = svg.getScreenCTM();
  if (e.targetTouches) { //if it is a touch event
    let x = e.targetTouches[0].clientX;
    let y = e.targetTouches[0].clientY;
    return {
      x: (x - CTM.e) / CTM.a,
      y: (y - CTM.f) / CTM.d
    };
  }else{
    return {
      x: (e.clientX - CTM.e) / CTM.a,
      y: (e.clientY - CTM.f) / CTM.d
    };
  }
}
