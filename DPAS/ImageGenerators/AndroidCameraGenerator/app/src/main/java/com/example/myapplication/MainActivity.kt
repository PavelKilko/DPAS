package com.example.myapplication

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AppNavigation(lifecycleOwner = this@MainActivity)
        }
    }
}

@Composable
fun AppNavigation(lifecycleOwner: LifecycleOwner) {
    val navController = rememberNavController()
    val newImageCapture = ImageCapture.Builder().build()

    NavHost(navController = navController, startDestination = "home") {
        composable("home") {
            HomeScreen(navController = navController)
        }
        composable("gen-img-sender") {
            ImageSenderScreen(isGeneratedImage = true)
        }
        composable("cam-img-sender") {
            ImageSenderScreen(lifecycleOwner, newImageCapture)
        }
    }
}

@Composable
fun HomeScreen(navController: NavController) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Button(onClick = { navController.navigate("gen-img-sender") }) {
            Text("Generated Image Sender")
        }
        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = { navController.navigate("cam-img-sender") }) {
            Text("Camera Image Sender")
        }
    }
}

@Composable
fun ImageSenderScreen(
    lifecycleOwner: LifecycleOwner? = null,
    imageCapture: ImageCapture? = null,
    isGeneratedImage: Boolean = false
) {
    var ipAddress by remember { mutableStateOf("192.168.0.243") }
    var port by remember { mutableStateOf("9999") }
    var delay by remember { mutableStateOf("5") }

    val ipAddressFocusRequester = remember { FocusRequester() }
    val portFocusRequester = remember { FocusRequester() }
    val intervalFocusRequester = remember { FocusRequester() }

    val focusManager = LocalFocusManager.current

    var isSending by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (!isGeneratedImage && lifecycleOwner != null && imageCapture != null) {
            CameraPreview(
                lifecycleOwner = lifecycleOwner,
                modifier = Modifier.width(300.dp),
                imageCapture = imageCapture
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

        TextField(
            value = ipAddress,
            onValueChange = { ipAddress = it },
            label = { Text("Enter IPv4 Address") },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            keyboardActions = KeyboardActions(onNext = { portFocusRequester.requestFocus() }),
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(ipAddressFocusRequester)
        )
        Spacer(modifier = Modifier.height(8.dp))
        TextField(
            value = port,
            onValueChange = { port = it },
            label = { Text("Enter Port Number") },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            keyboardActions = KeyboardActions(onNext = { intervalFocusRequester.requestFocus() }),
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(portFocusRequester)
        )
        Spacer(modifier = Modifier.height(8.dp))
        TextField(
            value = delay,
            onValueChange = { delay = it },
            label = { Text("Enter Delay Interval (in seconds)") },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
            modifier = Modifier.fillMaxWidth()
                .focusRequester(intervalFocusRequester)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Button(
                onClick = {
                    if (ipAddress.isNotEmpty() && port.isNotEmpty()) {
                        coroutineScope.launch {
                            if (isGeneratedImage) {
                                val image = generateRandomImage()
                                sendImage(ipAddress, port, image, context)
                            } else if (imageCapture != null && lifecycleOwner != null) {
                                captureAndSendImage(ipAddress, port, imageCapture, Executors.newSingleThreadExecutor(), context)
                            }
                        }
                    }
                }
            ) {
                Icon(Icons.Rounded.PhotoCamera, contentDescription = "Send Single Image")
            }
            Button(
                onClick = {
                    if (ipAddress.isNotEmpty() && port.isNotEmpty()) {
                        if (!isSending) {
                            isSending = true
                            coroutineScope.launch {
                                val delayInterval = delay.toLong() * 1000
                                while (isSending) {
                                    if (isGeneratedImage) {
                                        val image = generateRandomImage()
                                        sendImage(ipAddress, port, image, context)
                                    } else if (imageCapture != null && lifecycleOwner != null) {
                                        captureAndSendImage(ipAddress, port, imageCapture, Executors.newSingleThreadExecutor(), context)
                                    }
                                    delay(delayInterval)
                                }
                            }
                        } else {
                            isSending = false
                        }
                    }
                }
            ) {
                Text(if (!isSending) "Start Sending Images at Interval" else "Stop Sending Images at Interval")
            }
        }
    }
}

@Composable
fun CameraPreview(
    modifier: Modifier = Modifier,
    lifecycleOwner: LifecycleOwner,
    imageCapture: ImageCapture
) {
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            PreviewView(ctx).also { previewView ->
                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                cameraProviderFuture.addListener({
                    val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()
                    val preview = androidx.camera.core.Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }
                    val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                    try {
                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            cameraSelector,
                            preview,
                            imageCapture
                        )
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }, ContextCompat.getMainExecutor(ctx))
            }
        }
    )
}

fun captureAndSendImage(
    ipAddress: String,
    port: String,
    imageCapture: ImageCapture,
    executor: ExecutorService,
    context: Context
) {
    imageCapture.takePicture(executor, object : ImageCapture.OnImageCapturedCallback() {
        override fun onCaptureSuccess(image: ImageProxy) {
            Log.i("CAPTURE_SUCCESS", "SUCCESS")
            showToast(context, "Photo capture succeeded")
            val buffer = image.planes[0].buffer
            val data = ByteArray(buffer.remaining())
            buffer.get(data)
            image.close()
            sendImage(ipAddress, port, data, context)
        }

        override fun onError(exception: ImageCaptureException) {
            Log.i("CAPTURE_ERROR", exception.toString())
            val msg = "Photo capture failed: $exception"
            showToast(context, msg)
        }
    })
}

fun showToast(context: Context, message: String) {
    Handler(Looper.getMainLooper()).post {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }
}

suspend fun sendImage(ipAddress: String, port: String, image: Bitmap, context: Context) {
    withContext(Dispatchers.IO) {
        try {
            val imageData = encodeImageAsJPEG(image)
            val client = OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build()
            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart(
                    "image",
                    "image.jpg",
                    imageData.toRequestBody("image/jpeg".toMediaType())
                )
                .build()
            val request = Request.Builder()
                .url("http://$ipAddress:$port/process")
                .post(requestBody)
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.i("SEND_GEN_IMAGE", "Failed to send image: ${response.message}")
                    showToast(context, "Failed to send image: ${response.message}")
                } else {
                    Log.i("SEND_GEN_IMAGE", "Image successfully sent")
                    showToast(context, "Image successfully sent")
                }
            }
        } catch (e: IOException) {
            Log.e("SEND_GEN_IMAGE", "Error sending image", e)
            showToast(context, "Error sending image: ${e.message}")
        }
    }
}

fun sendImage(ipAddress: String, port: String, image: ByteArray, context: Context) {
    try {
        val client = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                "image",
                "image.jpg",
                image.toRequestBody("image/jpeg".toMediaType())
            )
            .build()
        val request = Request.Builder()
            .url("http://$ipAddress:$port/process")
            .post(requestBody)
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                Log.i("SEND_GEN_IMAGE", "Failed to send image: ${response.message}")
                showToast(context, "Failed to send image: ${response.message}")
            } else {
                Log.i("SEND_GEN_IMAGE", "Image successfully sent")
                showToast(context, "Image successfully sent")
            }
        }
    } catch (e: IOException) {
        Log.e("SEND_GEN_IMAGE", "Error sending image", e)
        showToast(context, "Error sending image: ${e.message}")
    }
}

fun generateRandomImage(): Bitmap {
    val width = 256
    val height = 256
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    for (x in 0 until width) {
        for (y in 0 until height) {
            val randomColor = Color.rgb((0..255).random(), (0..255).random(), (0..255).random())
            bitmap.setPixel(x, y, randomColor)
        }
    }
    return bitmap
}

fun encodeImageAsJPEG(image: Bitmap): ByteArray {
    val outputStream = ByteArrayOutputStream()
    image.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)
    return outputStream.toByteArray()
}
