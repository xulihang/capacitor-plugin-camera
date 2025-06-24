//
//  PreviewView.swift
//  Document Scanner
//
//  Created by xulihang on 2022/11/28.
//

import AVFoundation
import Foundation
import UIKit


class PreviewView: UIView {
    override class var layerClass: AnyClass {
        return AVCaptureVideoPreviewLayer.self
    }
    
    /// Convenience wrapper to get layer as its statically known type.
    var videoPreviewLayer: AVCaptureVideoPreviewLayer {
        return layer as! AVCaptureVideoPreviewLayer
    }
}
