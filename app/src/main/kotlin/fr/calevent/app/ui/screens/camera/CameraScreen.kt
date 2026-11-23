package fr.calevent.app.ui.screens.camera

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.view.View
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import android.os.Handler
import android.os.Looper
import android.view.Surface
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.compose.foundation.layout.aspectRatio
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview as CameraPreview
import androidx.camera.core.UseCaseGroup
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import fr.calevent.app.capture.CapturedPhotoStore
import fr.calevent.app.ui.components.PrimaryButton
import fr.calevent.app.ui.components.SecondaryButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.calevent.app.R
import fr.calevent.app.ui.ScanViewModel
import fr.calevent.app.ui.components.PlanningPhotoPlaceholder
import fr.calevent.app.ui.components.PlanningPhotoVariant
import fr.calevent.app.ui.icons.CalEventIcons
import fr.calevent.app.ui.theme.CalEventTheme
import fr.calevent.app.ui.theme.ThemeMode

// The camera viewfinder is dark in both themes (like any camera app), so these overlay colours
// are fixed on purpose rather than theme tokens: a translucent black "glass" under white content.
private val ViewfinderBg = Color(0xFF0B0F13)
private val Glass = Color(0x9E0C1014)
private val OnGlass = Color.White

/** Flash modes cycled by the top-right control. */
private enum class Flash(val mode: Int, val label: Int, val state: Int) {
    Auto(ImageCapture.FLASH_MODE_AUTO, R.string.camera_flash_auto_label, R.string.camera_flash_auto),
    On(ImageCapture.FLASH_MODE_ON, R.string.camera_flash_on_label, R.string.camera_flash_on),
    Off(ImageCapture.FLASH_MODE_OFF, R.string.camera_flash_off_label, R.string.camera_flash_off),
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/**
 * Camera capture with a live CameraX preview. The camera permission is requested on arrival (not at launch);
 * when refused, the screen explains it and the gallery import stays available. The photo goes to
 * [CapturedPhotoStore] (app cache only) and then [onNext]; close goes to [onBack].
 */
@Composable
fun CameraScreen(viewModel: ScanViewModel, onBack: () -> Unit, onNext: () -> Unit) {
    val context = LocalContext.current
    val inspection = LocalInspectionMode.current
    val scope = rememberCoroutineScope()
    var flash by rememberSaveable { mutableStateOf(Flash.Auto) }
    var granted by remember { mutableStateOf(inspection || hasCameraPermission(context)) }
    var asked by rememberSaveable { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    // Highest available 4:3 still (what the phone's own camera app saves) and near-lossless JPEG: OCR
    // needs every pixel of small table text.
    val imageCapture = remember {
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
            .setResolutionSelector(
                ResolutionSelector.Builder()
                    .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                    .setResolutionStrategy(ResolutionStrategy.HIGHEST_AVAILABLE_STRATEGY)
                    .build(),
            )
            .setJpegQuality(97)
            .build()
    }
    val focus = remember { FocusHandle() }
    val errorImport = stringResource(R.string.camera_error_import)
    val errorCapture = stringResource(R.string.camera_error_capture)

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        granted = it
        asked = true
    }
    val pickLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            busy = true
            scope.launch {
                val ok = withContext(Dispatchers.IO) {
                    runCatching {
                        val target = CapturedPhotoStore.newCaptureFile(context)
                        context.contentResolver.openInputStream(uri)!!.use { input -> target.outputStream().use { input.copyTo(it) } }
                        target
                    }.getOrNull()
                }
                busy = false
                if (ok != null) {
                    CapturedPhotoStore.set(Uri.fromFile(ok))
                    onNext()
                } else error = errorImport
            }
        }
    }
    val pickImage = { pickLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }

    LaunchedEffect(Unit) { if (!granted) permissionLauncher.launch(Manifest.permission.CAMERA) }
    // Coming back from system settings: re-read the permission.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME && !inspection) granted = hasCameraPermission(context) }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }
    imageCapture.flashMode = flash.mode

    val shoot = shoot@{
        if (busy) return@shoot
        busy = true
        val file = CapturedPhotoStore.newCaptureFile(context)
        // Focus and meter at the centre first, then shoot once locked (or after a short timeout).
        focus.focusThen(context) {
        imageCapture.takePicture(
            ImageCapture.OutputFileOptions.Builder(file).build(),
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    busy = false
                    CapturedPhotoStore.set(Uri.fromFile(file))
                    onNext()
                }
                override fun onError(exception: ImageCaptureException) {
                    busy = false
                    file.delete()
                    error = errorCapture
                }
            },
        )
        }
    }

    Box(Modifier.fillMaxSize().background(ViewfinderBg)) {
        if (!granted) {
            val activity = context.findActivity()
            // After a refusal, no rationale means "don't ask again" (or a policy block): only settings can help.
            val permanent = asked && activity != null && !ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.CAMERA)
            PermissionDenied(
                permanent = permanent,
                onAllow = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                onSettings = {
                    context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)))
                },
                onImport = pickImage,
                onClose = onBack,
            )
            return@Box
        }
        if (inspection) {
            PlanningPhotoPlaceholder(
                PlanningPhotoVariant.Weekly,
                Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(start = 44.dp, end = 44.dp, top = 118.dp).fillMaxWidth().height(420.dp),
            )
        } else {
            // 4:3 portrait like the sensor: preview, guide frame and saved photo show the same area.
            LiveViewfinder(imageCapture, focus, Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 80.dp).fillMaxWidth().aspectRatio(3f / 4f))
        }
        // Guide frame (white corners, 4 dp), drawn around the sheet.
        GuideFrame(Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 80.dp).fillMaxWidth().aspectRatio(3f / 4f).padding(20.dp))

        TopControls(
            flash = flash,
            onFlash = { flash = Flash.entries[(flash.ordinal + 1) % Flash.entries.size] },
            onClose = onBack,
            modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp),
        )

        val message = error
        if (message != null) {
            GlassPill(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(start = 20.dp, end = 20.dp, bottom = 210.dp)) {
                Text(message, style = CalEventTheme.type.secondary.copy(fontSize = 14.sp, fontWeight = FontWeight.Medium), color = OnGlass)
            }
        }
        GlassPill(
            Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(start = 20.dp, end = 20.dp, bottom = 150.dp),
        ) {
            Text(stringResource(R.string.camera_tip), style = CalEventTheme.type.secondary.copy(fontSize = 14.sp, fontWeight = FontWeight.Medium), color = OnGlass)
        }

        Row(
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().fillMaxWidth().padding(start = 32.dp, end = 32.dp, bottom = 34.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            val importCd = stringResource(R.string.camera_import_cd)
            Box(
                Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Glass, RoundedCornerShape(14.dp))
                    .border(2.dp, OnGlass.copy(alpha = 0.85f), RoundedCornerShape(14.dp))
                    .clickable(role = Role.Button, onClick = pickImage)
                    .semantics { contentDescription = importCd },
                contentAlignment = Alignment.Center,
            ) { Icon(CalEventIcons.Gallery, contentDescription = null, tint = OnGlass, modifier = Modifier.size(26.dp)) }
            Shutter(onClick = { shoot() })
            // Balances the row so the shutter stays centred.
            Box(Modifier.size(52.dp))
        }
    }
}

private fun hasCameraPermission(context: Context) =
    ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

/** Holds the bound camera so the shutter can focus first. */
private class FocusHandle {
    var camera: Camera? = null
    var view: PreviewView? = null

    fun focusThen(context: Context, action: () -> Unit) {
        val cam = camera
        val v = view
        if (cam == null || v == null || v.width == 0) { action(); return }
        var done = false
        val once = { if (!done) { done = true; action() } }
        val handler = Handler(Looper.getMainLooper())
        runCatching {
            val point = v.meteringPointFactory.createPoint(v.width / 2f, v.height / 2f)
            val future = cam.cameraControl.startFocusAndMetering(
                FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE).build(),
            )
            future.addListener({ handler.removeCallbacksAndMessages(null); once() }, ContextCompat.getMainExecutor(context))
            handler.postDelayed({ once() }, 1500)
        }.onFailure { once() }
    }
}

/** CameraX preview bound to the lifecycle; [imageCapture] is bound with it. */
@Composable
private fun LiveViewfinder(imageCapture: ImageCapture, focus: FocusHandle, modifier: Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember {
        PreviewView(context).apply { scaleType = PreviewView.ScaleType.FIT_CENTER; implementationMode = PreviewView.ImplementationMode.COMPATIBLE }
    }
    DisposableEffect(lifecycleOwner) {
        val future = ProcessCameraProvider.getInstance(context)
        var provider: ProcessCameraProvider? = null
        future.addListener({
            val p = future.get()
            provider = p
            val preview = CameraPreview.Builder()
                .setResolutionSelector(ResolutionSelector.Builder().setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY).build())
                .build().also { it.surfaceProvider = previewView.surfaceProvider }
            // The view port makes the captured JPEG cover exactly what the (FILL_CENTER) preview shows.
            // It is only known once the view is laid out.
            fun bind() {
                runCatching {
                    val group = UseCaseGroup.Builder().addUseCase(preview).addUseCase(imageCapture)
                    previewView.viewPort?.let { group.setViewPort(it) }
                    val rotation = previewView.display?.rotation ?: Surface.ROTATION_0
                    preview.targetRotation = rotation
                    imageCapture.targetRotation = rotation
                    p.unbindAll()
                    focus.view = previewView
                    focus.camera = p.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, group.build())
                }
            }
            if (previewView.viewPort != null) bind()
            else previewView.addOnLayoutChangeListener(object : View.OnLayoutChangeListener {
                override fun onLayoutChange(v: View?, l: Int, t: Int, r: Int, b: Int, ol: Int, ot: Int, or: Int, ob: Int) {
                    if (previewView.viewPort != null) {
                        previewView.removeOnLayoutChangeListener(this)
                        bind()
                    }
                }
            })
        }, ContextCompat.getMainExecutor(context))
        onDispose { focus.camera = null; provider?.unbindAll() }
    }
    AndroidView(factory = { previewView }, modifier = modifier)
}

/** Shown when the camera permission is missing: explanation, retry or settings, and gallery import. */
@Composable
private fun PermissionDenied(permanent: Boolean, onAllow: () -> Unit, onSettings: () -> Unit, onImport: () -> Unit, onClose: () -> Unit) {
    val t = CalEventTheme.type
    val closeCd = stringResource(R.string.camera_close_cd)
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier.statusBarsPadding().padding(16.dp).size(44.dp).clip(CircleShape).background(Glass, CircleShape).clickable(role = Role.Button, onClick = onClose),
            contentAlignment = Alignment.Center,
        ) { Icon(CalEventIcons.Close, contentDescription = closeCd, tint = OnGlass, modifier = Modifier.size(22.dp)) }
        Column(
            Modifier.align(Alignment.Center).padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Icon(CalEventIcons.Camera, contentDescription = null, tint = OnGlass, modifier = Modifier.size(48.dp))
            Text(stringResource(R.string.camera_permission_title), style = t.section, color = OnGlass, textAlign = TextAlign.Center)
            Text(
                stringResource(if (permanent) R.string.camera_permission_body_permanent else R.string.camera_permission_body),
                style = t.body, color = OnGlass.copy(alpha = 0.85f), textAlign = TextAlign.Center,
            )
        }
        Column(
            Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (permanent) PrimaryButton(stringResource(R.string.camera_permission_settings), onClick = onSettings, modifier = Modifier.fillMaxWidth())
            else PrimaryButton(stringResource(R.string.camera_permission_allow), onClick = onAllow, modifier = Modifier.fillMaxWidth())
            SecondaryButton(stringResource(R.string.camera_permission_import), onClick = onImport, icon = CalEventIcons.Gallery, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun TopControls(flash: Flash, onFlash: () -> Unit, onClose: () -> Unit, modifier: Modifier = Modifier) {
    val t = CalEventTheme.type
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        val closeCd = stringResource(R.string.camera_close_cd)
        Box(
            Modifier.size(44.dp).clip(CircleShape).background(Glass, CircleShape).clickable(role = Role.Button, onClick = onClose),
            contentAlignment = Alignment.Center,
        ) { Icon(CalEventIcons.Close, contentDescription = closeCd, tint = OnGlass, modifier = Modifier.size(22.dp)) }

        GlassPill(Modifier.heightIn(min = 30.dp), vertical = 6.dp) {
            Icon(CalEventIcons.ShieldOffline, contentDescription = null, tint = OnGlass, modifier = Modifier.size(16.dp))
            Text(stringResource(R.string.camera_offline), style = t.chip.copy(fontSize = 13.sp), color = OnGlass)
        }

        val flashState = stringResource(flash.state)
        val flashCd = stringResource(R.string.camera_flash_toggle_cd, flashState)
        Row(
            Modifier
                .heightIn(min = 44.dp)
                .clip(CircleShape)
                .background(Glass, CircleShape)
                .clickable(role = Role.Button, onClick = onFlash)
                .semantics { contentDescription = flashCd }
                .padding(start = 10.dp, end = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(CalEventIcons.Flash, contentDescription = null, tint = OnGlass, modifier = Modifier.size(22.dp))
            Text(stringResource(flash.label), style = t.chip.copy(fontSize = 14.sp), color = OnGlass)
        }
    }
}

@Composable
private fun GlassPill(modifier: Modifier = Modifier, vertical: androidx.compose.ui.unit.Dp = 10.dp, content: @Composable () -> Unit) {
    Row(
        modifier.background(Glass, RoundedCornerShape(15.dp)).padding(horizontal = 14.dp, vertical = vertical),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) { content() }
}

/** White ring with a 64 dp inner disc: the touch target is the full 80 dp. */
@Composable
private fun Shutter(onClick: () -> Unit) {
    val cd = stringResource(R.string.camera_shutter_cd)
    Box(
        Modifier
            .size(80.dp)
            .clip(CircleShape)
            .border(BorderStroke(4.dp, OnGlass), CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = cd },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(64.dp).background(OnGlass, CircleShape))
    }
}

@Composable
private fun GuideFrame(modifier: Modifier) {
    val cd = stringResource(R.string.camera_frame_cd)
    Box(
        modifier
            .semantics { contentDescription = cd }
            .drawBehind {
                val stroke = 4.dp.toPx()
                val len = 44.dp.toPx()
                val r = 18.dp.toPx()
                val w = size.width
                val h = size.height
                val s = Stroke(stroke)
                // Four corner brackets, each an arc plus two straight legs.
                fun corner(x: Float, y: Float, dx: Float, dy: Float) {
                    drawLine(OnGlass, Offset(x, y + dy * r), Offset(x, y + dy * len), stroke)
                    drawLine(OnGlass, Offset(x + dx * r, y), Offset(x + dx * len, y), stroke)
                    val left = if (dx > 0) x else x - 2 * r
                    val top = if (dy > 0) y else y - 2 * r
                    drawArc(OnGlass, startAngle = if (dx > 0 && dy > 0) 180f else if (dx < 0 && dy > 0) 270f else if (dx < 0) 0f else 90f, sweepAngle = 90f, useCenter = false, topLeft = Offset(left, top), size = Size(2 * r, 2 * r), style = s)
                }
                corner(0f, 0f, 1f, 1f)
                corner(w, 0f, -1f, 1f)
                corner(0f, h, 1f, -1f)
                corner(w, h, -1f, -1f)
            },
    )
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun CameraScreenLightPreview() = CalEventTheme(ThemeMode.LIGHT) { CameraScreen(remember { ScanViewModel() }, {}, {}) }

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun CameraScreenDarkPreview() = CalEventTheme(ThemeMode.DARK) { CameraScreen(remember { ScanViewModel() }, {}, {}) }
