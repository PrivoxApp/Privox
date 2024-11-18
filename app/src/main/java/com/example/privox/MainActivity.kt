package com.example.privox

import android.Manifest
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.net.Uri
import android.os.*
import android.provider.DocumentsContract
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RadialGradientShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import dev.jeziellago.compose.markdowntext.MarkdownText
import kotlinx.coroutines.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
class MainActivity : ComponentActivity() {
    private val TAG = "PrivoxDebug"
    private var isRecording = mutableStateOf(false)
    private var introComplete = mutableStateOf(false)
    private var showSettings = mutableStateOf(false)
    private var fileDuration = mutableStateOf(60f)
    private val recordedFiles = mutableStateListOf<File>()

    private val permissionRequestCode = 123

    private var isFileRolloverEnabled = mutableStateOf(true)
    private var saveAsZip = mutableStateOf(true)

    private val recordingElapsedTime = mutableStateOf(0L)

    private val createDocument = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri: Uri? ->
        uri?.let { saveLocation ->
            if (recordedFiles.isNotEmpty()) {
                saveRecordingTo(recordedFiles, saveLocation)
            }
        }
    }

    private val selectDirectory = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let { directoryUri ->
            if (recordedFiles.isNotEmpty()) {
                saveRecordingsToDirectory(recordedFiles, directoryUri)
            }
        }
    }

    // Variables for EULA and Privacy Policy
    private var showEULA = mutableStateOf(false)
    private var showPrivacyPolicy = mutableStateOf(false)

    // EULA and Privacy Policy texts
    private val eulaText = """
# **Privox End-User License Agreement (EULA)**

## **1. Introduction**

Welcome to **Privox**! This End-User License Agreement ("Agreement") governs your use of the Privox application ("App"). By downloading, installing, or using Privox, you agree to comply with and be bound by the terms of this Agreement.

## **2. Acceptance of Terms**

By accessing or using Privox, you acknowledge that you have read, understood, and agree to be bound by this Agreement. If you do not agree to these terms, do not download, install, or use Privox.

## **3. License Grant**

Privox grants you a **non-transferable, non-exclusive license** to use the App on your personal device for its intended purposes.

## **4. Restrictions**

You agree not to:

- **Modify** the App in any way, including altering its source code or functionality.
- **Distribute**, sell, rent, lease, sublicense, or otherwise transfer the App to others.
- **Reverse-engineer**, decompile, deconstruct, or attempt to derive the source code of the App.
- **Use** the App for any unlawful purposes or in a manner that violates any applicable laws or regulations.

## **5. Privacy**

Privox is committed to protecting your privacy. Please refer to our [Privacy Policy](#) for detailed information on how we handle your data and the permissions required for the App's functionality.

## **6. Battery Optimization**

Privox requires **continuous operation** to record audio effectively. To ensure uninterrupted recording, the App may request you to **opt out of battery optimizations**. This allows Privox to run in the background without being stopped by the system.

## **7. Ownership**

All rights, title, and interest in and to Privox, including all intellectual property rights, are owned by Privox and its licensors. This Agreement does not grant you any rights to trademarks, service marks, or logos used by Privox.

## **8. Termination**

This Agreement is effective until terminated. Privox may terminate this Agreement at any time without notice if you fail to comply with any terms herein. Upon termination, you must cease all use of the App and destroy all copies, full or partial, of the App.

## **9. Limitation of Liability**

Privox is provided "as is" without any warranties of any kind, either express or implied. Privox and its developers are not liable for any direct, indirect, incidental, special, consequential, or exemplary damages arising from your use of the App, even if Privox has been advised of the possibility of such damages.

## **10. Governing Law**

This Agreement shall be governed by and construed in accordance with the laws of the State of California, USA, without regard to its conflict of law principles.

## **11. Contact Information**

If you have any questions or concerns about this Agreement or Privox's practices, please contact us at:

📧 **Email:** [privoxapp@gmail.com](mailto:privoxapp@gmail.com)  
📂 **GitHub Repository:** [https://github.com/PrivoxApp/Privox](https://github.com/PrivoxApp/Privox)
"""

    private val privacyPolicyText = """
# **Privox Privacy Policy**

## **1. Introduction**

Welcome to **Privox**! Your privacy is important to us. This Privacy Policy explains how Privox handles your information, the permissions it requires, and your rights regarding your data.

## **2. Permissions We Require**

To provide you with a seamless audio recording experience, Privox requests the following permissions:

### **a. Microphone (`RECORD_AUDIO`)**
- **Purpose:** Allows Privox to capture audio recordings from your device's microphone.
- **Usage:** Essential for recording your voice or any ambient sounds.

### **b. Wake Lock (`WAKE_LOCK`)**
- **Purpose:** Keeps your device's CPU awake during recording sessions.
- **Usage:** Ensures uninterrupted recording by preventing your device from sleeping while Privox is active.

### **c. Foreground Service (`FOREGROUND_SERVICE`)**
- **Purpose:** Allows Privox to run a foreground service for continuous recording.
- **Usage:** Ensures the app can record audio even when in the background or when the screen is off.

## **3. Battery Optimization**

### **Opting Out of Battery Optimizations**

- **Purpose:** Some devices may stop background services to conserve battery, interrupting recording.
- **Advantage:** Opting out allows Privox to continue recording without interruption.
- **Drawback:** May result in increased battery usage.

**Note:** You can choose to opt out during recording. This setting can be changed at any time in your device's settings.

## **4. Data Collection and Usage**

### **a. No Personal Data Collection**
- **What We Collect:** Privox does **not** collect, store, or transmit any personal data.
- **Recordings:** All audio recordings are saved **locally** on your device in the app-specific directory.

### **b. No Tracking or Analytics**
- Privox operates without tracking your usage or collecting analytics data. Your interactions with the app remain private and are not monitored. Your data will never be used for AI training.

## **5. Data Storage and Security**

### **a. Local Storage**
- **Where Data is Stored:** All recordings are stored directly on your device within Privox's dedicated folder.
- **Access:** Only you can access these recordings unless you choose to share them using the app's sharing features.

### **b. Data Security**
- **Protection Measures:** Privox ensures that your recordings are securely stored on your device. We do not implement any external data storage solutions, minimizing the risk of unauthorized access.

## **6. Data Sharing**

- **No Third-Party Sharing:** Privox does **not** share your recordings or any other data with third parties. It is your data only.
- **User-Controlled Sharing:** If you choose to share your recordings, it is done directly through the app's sharing features, and Privox does not have access to these shared files.

## **7. User Rights**

### **a. Access and Control**
- **Your Data:** Since Privox does not collect or store personal data, you have complete control over your recordings.
- **Deletion:** You can delete any recordings at your discretion directly from your device.

## **8. Changes to This Privacy Policy**

We may update this Privacy Policy from time to time to reflect changes in our practices or for other operational, legal, or regulatory reasons.

- **Notification of Changes:** Any changes will be posted within the app and updated on our [GitHub Repository](https://github.com/PrivoxApp/Privox) which you are free to inspect for transparency.
- **Effective Date:** The current version of this Privacy Policy is effective as of **November 15, 2024**.

## **9. Contact Us**

If you have any questions or concerns about this Privacy Policy or Privox's practices, please contact us at:

📧 **Email:** [privoxapp@gmail.com](mailto:privoxapp@gmail.com)  
📂 **GitHub Repository:** [https://github.com/PrivoxApp/Privox)
"""

    @OptIn(ExperimentalComposeUiApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate started")
        setContent {
            Log.d(TAG, "setContent started")
            MaterialTheme {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black)
                ) {
                    EnhancedAuroraBackground(0f) // No amplitude in MainActivity now

                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        PrivoxTopBar(
                            onSettingsClick = { showSettings.value = true },
                            onShowEULA = { showEULA.value = true },
                            onShowPrivacyPolicy = { showPrivacyPolicy.value = true }
                        )

                        AnimatedTitle()

                        if (!introComplete.value) {
                            IntroMessages()
                        }

                        Spacer(modifier = Modifier.weight(1f))

                        PulsingRecordButton(
                            isRecording = isRecording.value,
                            onClick = {
                                Log.d(TAG, "Record button clicked")
                                if (checkPermissions()) {
                                    startStopRecording()
                                } else {
                                    requestPermissions()
                                }
                            },
                            isPulsing = isRecording.value
                        )

                        RecordingTimer(isRecording = isRecording.value)

                        Spacer(modifier = Modifier.height(20.dp))

                        AnimatedVisibility(
                            visible = !isRecording.value && recordedFiles.isNotEmpty(),
                            enter = fadeIn() + expandVertically(),
                            exit = fadeOut() + shrinkVertically()
                        ) {
                            SaveShareButtons()
                        }

                        Spacer(modifier = Modifier.weight(1f))

                        PrivacyInfo()
                    }

                    SettingsDialog(
                        isVisible = showSettings.value,
                        onDismiss = { showSettings.value = false },
                        isFileRolloverEnabled = isFileRolloverEnabled.value,
                        onFileRolloverEnabledChange = { isFileRolloverEnabled.value = it },
                        fileDuration = fileDuration.value,
                        onFileDurationChange = { fileDuration.value = it },
                        saveAsZip = saveAsZip.value,
                        onSaveAsZipChange = { saveAsZip.value = it }
                    )

                    if (showEULA.value) {
                        EULADialog(
                            onDismiss = { showEULA.value = false },
                            eulaText = eulaText
                        )
                    }

                    if (showPrivacyPolicy.value) {
                        PrivacyPolicyDialog(
                            onDismiss = { showPrivacyPolicy.value = false },
                            privacyPolicyText = privacyPolicyText
                        )
                    }
                }
                Log.d(TAG, "UI loaded")
            }
        }
    }

    @Composable
    fun EnhancedAuroraBackground(amplitude: Float) {
        val density = LocalDensity.current
        val baseAlpha = 0.2f
        val amplitudeMultiplier = 0.5f
        val alpha = (baseAlpha + amplitude * amplitudeMultiplier).coerceIn(0.2f, 0.8f)

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        brush = ShaderBrush(
                            RadialGradientShader(
                                colors = listOf(
                                    Color(0xFF64B5F6).copy(alpha = alpha),
                                    Color.Transparent
                                ),
                                center = Offset(0f, 0f),
                                radius = with(density) { 400.dp.toPx() }
                            )
                        )
                    )
            )

            Box(
                modifier = Modifier
                    .size(800.dp)
                    .graphicsLayer {
                        rotationZ = 45f
                    }
                    .blur(radius = (30 + amplitude * 70).dp)
                    .background(
                        brush = ShaderBrush(
                            RadialGradientShader(
                                colors = listOf(
                                    Color(0xFF2196F3).copy(alpha = alpha),
                                    Color(0xFF1976D2).copy(alpha = alpha * 0.6f),
                                    Color.Transparent
                                ),
                                center = Offset.Zero,
                                radius = with(density) { (200 + amplitude * 100).dp.toPx() }
                            )
                        ),
                        shape = CircleShape
                    )
            )
        }
    }

    @Composable
    fun PrivacyInfo() {
        Text(
            "100% Private • No Analytics • No Cloud",
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 12.sp,
            modifier = Modifier.padding(bottom = 16.dp)
        )
    }

    @Composable
    fun IntroMessages() {
        val messages = listOf(
            "100% local",
            "100% private",
            "no tracking",
            "no ads",
            "ever!"
        )

        var currentMessageIndex by remember { mutableStateOf(0) }

        LaunchedEffect(Unit) {
            for (i in messages.indices) {
                currentMessageIndex = i
                delay(800)
            }
            delay(1000)
            introComplete.value = true
        }

        AnimatedVisibility(
            visible = !introComplete.value,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Text(
                text = messages[currentMessageIndex],
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(16.dp)
            )
        }
    }

    @Composable
    fun AnimatedTitle() {
        val infiniteTransition = rememberInfiniteTransition()
        val scale by infiniteTransition.animateFloat(
            initialValue = 1f,
            targetValue = 1.05f,
            animationSpec = infiniteRepeatable(
                animation = tween(4000, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            )
        )

        Text(
            text = "PRIVOX",
            color = Color.White,
            fontSize = 48.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .padding(top = 32.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
        )
    }

    @Composable
    fun PulsingRecordButton(
        isRecording: Boolean,
        onClick: () -> Unit,
        isPulsing: Boolean
    ) {
        val buttonShape = if (isRecording) {
            RoundedCornerShape(8.dp)
        } else {
            CircleShape
        }

        val infiniteTransition = rememberInfiniteTransition()
        val scale by if (isPulsing) {
            infiniteTransition.animateFloat(
                initialValue = 1f,
                targetValue = 1.2f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1000, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse
                )
            )
        } else {
            remember { mutableStateOf(1f) }
        }

        val buttonSize = 80.dp * 1.5f

        Box(
            modifier = Modifier
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
        ) {
            FilledTonalButton(
                onClick = onClick,
                modifier = Modifier
                    .size(buttonSize)
                    .padding(8.dp)
                    .clip(buttonShape),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = Color.Red
                )
            ) {
                Icon(
                    imageVector = if (isRecording) Icons.Default.Stop else Icons.Default.Mic,
                    contentDescription = if (isRecording) "Stop Recording" else "Start Recording",
                    tint = Color.White,
                    modifier = Modifier.size(48.dp * 1.5f)
                )
            }
        }
    }

    @Composable
    fun RecordingTimer(isRecording: Boolean) {
        val elapsedTime = remember { mutableStateOf(0L) }

        if (isRecording) {
            LaunchedEffect(Unit) {
                val startTime = System.currentTimeMillis()
                while (isActive) {
                    elapsedTime.value = System.currentTimeMillis() - startTime
                    delay(1000)
                }
            }
        } else {
            elapsedTime.value = 0L
        }

        if (isRecording) {
            Text(
                text = formatElapsedTime(elapsedTime.value),
                color = Color.White,
                fontSize = 24.sp,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }

    private fun formatElapsedTime(elapsedTime: Long): String {
        val totalSeconds = elapsedTime / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return String.format("%02d:%02d:%02d", hours, minutes, seconds)
    }

    @Composable
    fun SaveShareButtons() {
        Row(
            modifier = Modifier.padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilledTonalButton(
                onClick = { initiateFileSave() },
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = Color(0xFF2196F3)
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Save,
                    contentDescription = "Save Recordings",
                    tint = Color.White
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Save")
            }

            FilledTonalButton(
                onClick = { shareRecordings() },
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = Color(0xFF2196F3)
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Share,
                    contentDescription = "Share Recordings",
                    tint = Color.White
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Share")
            }
        }
    }

    @Composable
    fun PrivoxTopBar(
        onSettingsClick: () -> Unit,
        onShowEULA: () -> Unit,
        onShowPrivacyPolicy: () -> Unit
    ) {
        SmallTopAppBar(
            title = { },
            colors = TopAppBarDefaults.smallTopAppBarColors(
                containerColor = Color.Transparent
            ),
            actions = {
                TextButton(onClick = onShowEULA) {
                    Text("EULA", color = Color.White, fontSize = 12.sp)
                }
                TextButton(onClick = onShowPrivacyPolicy) {
                    Text("Privacy Policy", color = Color.White, fontSize = 12.sp)
                }
                IconButton(onClick = onSettingsClick) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Settings",
                        tint = Color.White
                    )
                }
            }
        )
    }

    @Composable
    fun SettingsDialog(
        isVisible: Boolean,
        onDismiss: () -> Unit,
        isFileRolloverEnabled: Boolean,
        onFileRolloverEnabledChange: (Boolean) -> Unit,
        fileDuration: Float,
        onFileDurationChange: (Float) -> Unit,
        saveAsZip: Boolean,
        onSaveAsZipChange: (Boolean) -> Unit
    ) {
        if (isVisible) {
            Dialog(onDismissRequest = onDismiss) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentHeight(),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Column(
                        modifier = Modifier
                            .padding(24.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(
                            text = "Settings",
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        // Enable File Rollover Switch
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Enable File Rollover",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                            Switch(
                                checked = isFileRolloverEnabled,
                                onCheckedChange = onFileRolloverEnabledChange
                            )
                        }

                        if (isFileRolloverEnabled) {
                            Spacer(modifier = Modifier.height(16.dp))

                            Text(
                                text = "File Duration",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            Column(modifier = Modifier.padding(top = 16.dp)) {
                                Slider(
                                    value = fileDuration,
                                    onValueChange = onFileDurationChange,
                                    valueRange = 1f..120f,
                                    steps = 119
                                )
                                Text(
                                    text = "Create new file every ${fileDuration.toInt()} minutes",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "Note: Many transcription services have time limits (often 60 minutes)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.padding(top = 8.dp)
                                )
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // Save Option (Zipped or Individual mp3 files)
                            Text(
                                text = "Save Options",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            Column(modifier = Modifier.padding(top = 16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    RadioButton(
                                        selected = saveAsZip,
                                        onClick = { onSaveAsZipChange(true) }
                                    )
                                    Text(
                                        text = "Save as zipped version",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    RadioButton(
                                        selected = !saveAsZip,
                                        onClick = { onSaveAsZipChange(false) }
                                    )
                                    Text(
                                        text = "Save as individual mp3 files",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        Divider(modifier = Modifier.padding(vertical = 16.dp))

                        Text(
                            text = "Battery Optimization",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Text(
                            text = "To ensure uninterrupted recording, you can opt out of battery optimizations. This prevents the system from stopping the app while recording.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp)
                        )

                        Text(
                            text = "Advantages:",
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                        Text(
                            text = "- Continuous recording without interruptions.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Text(
                            text = "Drawbacks:",
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                        Text(
                            text = "- May consume more battery power.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Text(
                            text = "You can manage this setting at any time in your device's battery optimization settings.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp)
                        )

                        Divider(modifier = Modifier.padding(vertical = 16.dp))

                        Text(
                            text = "About Privox",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                PrivacyFeature("100% Private - All recordings stay on your device")
                                PrivacyFeature("No tracking or analytics")
                                PrivacyFeature("No cloud storage - Complete local control")
                                PrivacyFeature("No ads or third-party services")
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            }
        }
    }

    @Composable
    private fun PrivacyFeature(text: String) {
        Row(
            modifier = Modifier.padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    @Composable
    fun EULADialog(onDismiss: () -> Unit, eulaText: String) {
        Dialog(onDismissRequest = onDismiss) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.9f),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface
            ) {
                Column(
                    modifier = Modifier
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = "End-User License Agreement",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    val uriHandler = LocalUriHandler.current

                    MarkdownText(
                        markdown = eulaText,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        onLinkClicked = { url ->
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                            startActivity(intent)
                        }
                    )
                }
            }
        }
    }

    @Composable
    fun PrivacyPolicyDialog(onDismiss: () -> Unit, privacyPolicyText: String) {
        Dialog(onDismissRequest = onDismiss) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.9f),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface
            ) {
                Column(
                    modifier = Modifier
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = "Privacy Policy",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    val uriHandler = LocalUriHandler.current

                    MarkdownText(
                        markdown = privacyPolicyText,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        onLinkClicked = { url ->
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                            startActivity(intent)
                        }
                    )
                }
            }
        }
    }

    private fun initiateFileSave() {
        if (recordedFiles.isEmpty()) return

        if (saveAsZip.value) {
            val filename = "PRIVOX_Recordings.zip"
            createDocument.launch(filename)
        } else {
            // Launch directory picker
            selectDirectory.launch(null)
        }
    }

    private fun saveRecordingTo(sourceFiles: List<File>, destinationUri: Uri) {
        try {
            contentResolver.openOutputStream(destinationUri)?.use { outputStream ->
                val zipOutputStream = java.util.zip.ZipOutputStream(outputStream)
                sourceFiles.forEach { file ->
                    val entryName = file.name
                    zipOutputStream.putNextEntry(java.util.zip.ZipEntry(entryName))
                    file.inputStream().use { inputStream ->
                        inputStream.copyTo(zipOutputStream)
                    }
                    zipOutputStream.closeEntry()
                }
                zipOutputStream.close()
            }
            Toast.makeText(this, "Recordings saved successfully", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to save recordings", Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveRecordingsToDirectory(sourceFiles: List<File>, directoryUri: Uri) {
        try {
            // For each file, copy it to the selected directory
            sourceFiles.forEach { file ->
                val destUri = DocumentsContract.createDocument(
                    contentResolver,
                    directoryUri,
                    "audio/mp3",
                    file.name
                )
                destUri?.let { uri ->
                    contentResolver.openOutputStream(uri)?.use { outputStream ->
                        file.inputStream().use { inputStream ->
                            inputStream.copyTo(outputStream)
                        }
                    }
                }
            }
            Toast.makeText(this, "Recordings saved successfully", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to save recordings", Toast.LENGTH_SHORT).show()
        }
    }

    private fun shareRecordings() {
        try {
            if (recordedFiles.isEmpty()) {
                Toast.makeText(this, "No recordings to share", Toast.LENGTH_SHORT).show()
                return
            }

            if (saveAsZip.value) {
                // Create a temporary zip file
                val zipFile = File(cacheDir, "PRIVOX_Recordings.zip")
                zipFile.outputStream().use { outputStream ->
                    val zipOutputStream = java.util.zip.ZipOutputStream(outputStream)
                    recordedFiles.forEach { file ->
                        val entryName = file.name
                        zipOutputStream.putNextEntry(java.util.zip.ZipEntry(entryName))
                        file.inputStream().use { inputStream ->
                            inputStream.copyTo(zipOutputStream)
                        }
                        zipOutputStream.closeEntry()
                    }
                    zipOutputStream.close()
                }

                val uri = FileProvider.getUriForFile(
                    this,
                    "${applicationContext.packageName}.provider",
                    zipFile
                )

                val shareIntent = Intent().apply {
                    action = Intent.ACTION_SEND
                    type = "application/zip"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }

                if (shareIntent.resolveActivity(packageManager) != null) {
                    startActivity(Intent.createChooser(shareIntent, "Share Recordings"))
                } else {
                    Toast.makeText(this, "No app available to share files", Toast.LENGTH_SHORT).show()
                }
            } else {
                // Share individual mp3 files
                val uris = recordedFiles.map { file ->
                    FileProvider.getUriForFile(
                        this,
                        "${applicationContext.packageName}.provider",
                        file
                    )
                }

                val shareIntent = Intent().apply {
                    action = Intent.ACTION_SEND_MULTIPLE
                    type = "audio/*"
                    putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }

                if (shareIntent.resolveActivity(packageManager) != null) {
                    startActivity(Intent.createChooser(shareIntent, "Share Recordings"))
                } else {
                    Toast.makeText(this, "No app available to share audio", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: IllegalArgumentException) {
            Toast.makeText(this, "FileProvider not properly configured", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Error sharing recordings: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun startStopRecording() {
        if (isRecording.value) {
            stopRecordingService()
        } else {
            startRecordingService()
        }
    }

    private fun startRecordingService() {
        val intent = Intent(this, RecordingService::class.java)
        intent.putExtra("fileDuration", fileDuration.value)
        intent.putExtra("isFileRolloverEnabled", isFileRolloverEnabled.value)
        ContextCompat.startForegroundService(this, intent)
        isRecording.value = true

        // Request to ignore battery optimizations
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = getSystemService(POWER_SERVICE) as PowerManager
            val packageName = packageName
            if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                intent.data = Uri.parse("package:$packageName")
                startActivity(intent)
            }
        }
    }

    private fun stopRecordingService() {
        val intent = Intent(this, RecordingService::class.java)
        stopService(intent)
        isRecording.value = false
        // Retrieve recorded files from service
        recordedFiles.clear()
        recordedFiles.addAll(RecordingService.recordedFiles)
        RecordingService.recordedFiles.clear()
    }

    // Permission handling
    private fun checkPermissions(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestPermissions() {
        Log.d(TAG, "Requesting permissions")
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.RECORD_AUDIO),
            permissionRequestCode
        )
    }

    @Deprecated("Deprecated in Java")
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == permissionRequestCode &&
            grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) {
            Log.d(TAG, "Permission granted")
            startStopRecording()
        } else {
            Log.d(TAG, "Permission denied")
            Toast.makeText(this, "Recording permission denied.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "onDestroy called")
        if (isRecording.value) {
            stopRecordingService()
        }
    }
}

