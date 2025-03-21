package com.minshoki.image_editor.feature.facedetection

import android.graphics.Rect
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions

object FaceDetectionProcess {

    fun detectFace(input: InputImage, result: (faces: List<Rect>) -> Unit) {
        val options = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
            .setMinFaceSize(0.15f)
            .build()

        input.rotationDegrees

        val detector = FaceDetection.getClient(options)
        detector.process(input)
            .addOnSuccessListener { faces ->
                result(faces.map { it.boundingBox })
            }
            .addOnFailureListener { e ->
                e.printStackTrace()
            }
    }

}