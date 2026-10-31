package com.example.app

import android.app.Activity
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
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
        private const val PREFS = "deepfake_settings"
        private const val PREF_SERVER_URL = "server_url"
        private const val DEFAULT_SERVER_URL = "http://10.0.2.2:8000"
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
    private lateinit var serverUrlInput: EditText

    private var sourceFile: File? = null
    private var targetFile: File? = null

    private val serverBaseUrl: String
        get() = getSharedPreferences(PREFS, MODE_PRIVATE)
            .getString(PREF_SERVER_URL, DEFAULT_SERVER_URL)!!
            .trim()
            .trimEnd('/')

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        sourcePreview = findViewById(R.id.imgSource)
        targetPreview = findViewById(R.id.imgTarget)
        resultPreview = findViewById(R.id.imgResult)
        statusText = findViewById(R.id.txtStatus)
        swapButton = findViewById(R.id.btnSwap)
        serverUrlInput = findViewById(R.id.inputServerUrl)

        serverUrlInput.setText(serverBaseUrl)
        findViewById<Button>(R.id.btnSaveServer).setOnClickListener {
            saveServerUrl()
        }
        findViewById<Button>(R.id.btnTestServer).setOnClickListener {
            testServerConnection()
        }
        findViewById<Button>(R.id.btnSelectSource).setOnClickListener {
            openImagePicker(REQUEST_SOURCE)
        }
        findViewById<Button>(R.id.btnSelectTarget).setOnClickListener {
            openImagePicker(REQUEST_TARGET)
        }
        swapButton.setOnClickListener { submitFaceSwap() }

        updateSwapButton()
    }

    private fun saveServerUrl() {
        val value = serverUrlInput.text.toString().trim().trimEnd('/')
        val valid = value.startsWith("http://") || value.startsWith("https://")
        if (!valid || value.length < 12) {
            statusText.text = "Ingresá una dirección completa, por ejemplo http://192.168.1.20:8000"
            return
        }
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
            .putString(PREF_SERVER_URL, value)
            .apply()
        statusText.text = "Servidor guardado: $value"
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
            FileOutputStream(destination).use { output -> inputStream.copyTo(output) }
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


    private fun testServerConnection() {
        saveServerUrlSilently()
        val url = serverBaseUrl
        statusText.text = "Comprobando conexión con el motor IA…"
        val request = Request.Builder().url("$url/health").get().build()
        httpClient.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: IOException) {
                runOnUiThread {
                    statusText.text = "No se pudo conectar con $url. Verificá que el servidor esté activo y que la dirección sea correcta."
                }
            }

            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                response.use { res ->
                    val body = res.body?.string().orEmpty()
                    runOnUiThread {
                        statusText.text = if (res.isSuccessful && body.contains("\"models_loaded\"\\s*:\\s*true".toRegex())) {
                            "Conexión correcta: motor IA y modelos disponibles."
                        } else if (res.isSuccessful) {
                            "Servidor conectado, pero los modelos todavía no están cargados. Detalle: $body"
                        } else {
                            "El servidor respondió HTTP ${res.code}: $body"
                        }
                    }
                }
            }
        })
    }

    private fun submitFaceSwap() {
        val source = sourceFile
        val target = targetFile
        if (source == null || target == null || !source.exists() || !target.exists()) {
            statusText.text = "Seleccioná ambas imágenes antes de continuar."
            return
        }

        saveServerUrlSilently()
        swapButton.isEnabled = false
        statusText.text = "Conectando y procesando…"
        resultPreview.setImageDrawable(null)

        val multipart = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("source", source.name, source.asRequestBody("image/*".toMediaTypeOrNull()))
            .addFormDataPart("target", target.name, target.asRequestBody("image/*".toMediaTypeOrNull()))
            .build()

        val request = Request.Builder()
            .url("$serverBaseUrl/swap")
            .post(multipart)
            .build()

        httpClient.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: IOException) {
                runOnUiThread {
                    statusText.text = "No se pudo conectar con $serverBaseUrl. Revisá la dirección y que el servidor esté encendido."
                    updateSwapButton()
                }
            }

            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                response.use { res ->
                    if (!res.isSuccessful) {
                        val message = res.body?.string()?.take(500) ?: "HTTP ${res.code}"
                        runOnUiThread {
                            statusText.text = "Error del servidor: $message"
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

    private fun saveServerUrlSilently() {
        val value = serverUrlInput.text.toString().trim().trimEnd('/')
        if (value.startsWith("http://") || value.startsWith("https://")) {
            getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                .putString(PREF_SERVER_URL, value)
                .apply()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        sourceFile?.delete()
        targetFile?.delete()
    }
}
