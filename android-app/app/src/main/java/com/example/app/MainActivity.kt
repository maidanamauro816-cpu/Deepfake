package com.example.app

import android.app.Activity
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit

class MainActivity : Activity() {

    companion object {
        private const val REQUEST_SOURCE = 1001
        private const val REQUEST_TARGET = 1002

        // Android Emulator -> host computer. For a physical phone, replace
        // 10.0.2.2 with the computer's LAN IP (for example, 192.168.1.20).
        private const val BASE_URL = "http://10.0.2.2:8000"
        private const val SWAP_ENDPOINT = "$BASE_URL/swap"
    }

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .readTimeout(180, TimeUnit.SECONDS)
            .callTimeout(240, TimeUnit.SECONDS)
            .build()
    }

    private lateinit var sourcePreview: ImageView
    private lateinit var targetPreview: ImageView
    private lateinit var resultPreview: ImageView
    private lateinit var statusText: TextView
    private lateinit var swapButton: Button

    private var sourceFile: File? = null
    private var targetFile: File? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        sourcePreview = findViewById(R.id.imgSource)
        targetPreview = findViewById(R.id.imgTarget)
        resultPreview = findViewById(R.id.imgResult)
        statusText = findViewById(R.id.txtStatus)
        swapButton = findViewById(R.id.btnSwap)

        findViewById<Button>(R.id.btnSelectSource).setOnClickListener {
            openImagePicker(REQUEST_SOURCE)
        }
        findViewById<Button>(R.id.btnSelectTarget).setOnClickListener {
            openImagePicker(REQUEST_TARGET)
        }
        swapButton.setOnClickListener { submitFaceSwap() }

        updateSwapButton()
    }

    private fun openImagePicker(requestCode: Int) {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "image/*"
        }
        startActivityForResult(intent, requestCode)
    }

    @Deprecated("Uses startActivityForResult for compatibility with a plain Activity")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (resultCode != RESULT_OK) return
        val uri: Uri = data?.data ?: return

        try {
            // Copy the selected content URI into app cache. The network request
            // can then read a normal File without depending on gallery URI access.
            val cachedImage = copyUriToCache(uri)
            when (requestCode) {
                REQUEST_SOURCE -> {
                    sourceFile?.delete()
                    sourceFile = cachedImage
                    sourcePreview.setImageBitmap(BitmapFactory.decodeFile(cachedImage.absolutePath))
                }
                REQUEST_TARGET -> {
                    targetFile?.delete()
                    targetFile = cachedImage
                    targetPreview.setImageBitmap(BitmapFactory.decodeFile(cachedImage.absolutePath))
                }
            }
            statusText.text = "Imagen cargada correctamente."
            updateSwapButton()
        } catch (exception: Exception) {
            statusText.text = "No se pudo leer la imagen: ${exception.localizedMessage ?: "error"}"
        }
    }

    @Throws(IOException::class)
    private fun copyUriToCache(uri: Uri): File {
        val destination = File.createTempFile("face_swap_", ".img", cacheDir)
        val input = contentResolver.openInputStream(uri)
            ?: throw IOException("No se pudo abrir la imagen seleccionada")

        input.use { inputStream ->
            FileOutputStream(destination).use { output ->
                inputStream.copyTo(output)
            }
        }
        if (destination.length() == 0L) {
            destination.delete()
            throw IOException("El archivo seleccionado está vacío")
        }
        return destination
    }

    private fun updateSwapButton() {
        swapButton.isEnabled = sourceFile?.exists() == true && targetFile?.exists() == true
    }

    private fun submitFaceSwap() {
        val source = sourceFile
        val target = targetFile

        if (source == null || target == null || !source.exists() || !target.exists()) {
            statusText.text = "Seleccioná ambas imágenes antes de continuar."
            return
        }

        swapButton.isEnabled = false
        statusText.text = "Procesando en el servidor…"
        resultPreview.setImageDrawable(null)

        val sourceBody = source.asRequestBody("image/*".toMediaTypeOrNull())
        val targetBody = target.asRequestBody("image/*".toMediaTypeOrNull())

        val multipart = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("source", source.name, sourceBody)
            .addFormDataPart("target", target.name, targetBody)
            .build()

        val request = Request.Builder()
            .url(SWAP_ENDPOINT)
            .post(multipart)
            .build()

        httpClient.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: IOException) {
                runOnUiThread {
                    statusText.text = "Error de conexión: ${e.localizedMessage ?: "sin conexión"}"
                    updateSwapButton()
                }
            }

            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                response.use { res ->
                    if (!res.isSuccessful) {
                        val message = res.body?.string()?.take(500)
                            ?: "HTTP ${res.code}"
                        runOnUiThread {
                            statusText.text = "El servidor respondió con error: $message"
                            updateSwapButton()
                        }
                        return
                    }

                    val bytes = res.body?.bytes()
                    if (bytes == null || bytes.isEmpty()) {
                        runOnUiThread {
                            statusText.text = "El servidor devolvió una respuesta vacía."
                            updateSwapButton()
                        }
                        return
                    }

                    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    runOnUiThread {
                        if (bitmap != null) {
                            resultPreview.setImageBitmap(bitmap)
                            statusText.text = "Procesamiento completado."
                        } else {
                            statusText.text = "La respuesta no contiene una imagen válida."
                        }
                        updateSwapButton()
                    }
                }
            }
        })
    }

    override fun onDestroy() {
        super.onDestroy()
        sourceFile?.delete()
        targetFile?.delete()
    }
}
