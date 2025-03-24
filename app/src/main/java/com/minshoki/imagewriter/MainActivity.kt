package com.minshoki.imagewriter

import android.net.Uri
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.minshoki.core.design.dialog.dialog
import com.minshoki.image_editor.core.EditorMode
import com.minshoki.image_editor.core.contract.ImageEditorContract
import com.minshoki.image_editor.core.contract.ImageEditorViewerContract
import com.minshoki.image_editor.model.ImageEditorViewerModel
import com.minshoki.image_editor.model.ImageEditorViewerSimpleModel

class MainActivity : AppCompatActivity() {

    private val getImageUriContract = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { result ->
        if(result != null) {
            imageEditorViewerContract.launch(
                ImageEditorViewerContract.Request(
                    uris = listOf(result)
                )
            )
        }
    }

    private val imageEditorViewerContract = registerForActivityResult(ImageEditorViewerContract()) { result ->

    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        dialog {
            message("test")
            positiveListener {
                getImageUriContract.launch(PickVisualMediaRequest.Builder()
                    .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    .build())
            }
        }
    }
}