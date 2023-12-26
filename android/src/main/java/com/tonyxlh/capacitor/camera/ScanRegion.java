package com.tonyxlh.capacitor.camera;

public class ScanRegion {
    public int top;
    public int bottom;
    public int left;
    public int right;
    public int measuredByPercentage;
    public ScanRegion(int top,int bottom,int left,int right,int measuredByPercentage) {
     this.top = top;
     this.bottom = bottom;
     this.left = left;
     this.right = right;
     this.measuredByPercentage = measuredByPercentage;
    }
}
