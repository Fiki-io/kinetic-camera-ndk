package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.ui.camera.CameraScreen
import com.example.ui.camera.CameraViewModel
import com.example.ui.theme.KineticCameraTheme
import com.example.ui.theme.MatteBlack
import com.example.ui.theme.NeutralGrey
import com.example.ui.theme.SoftOffWhite
import com.example.ui.theme.SurfaceSlate

class MainActivity : ComponentActivity() {

    private val cameraViewModel: CameraViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            KineticCameraTheme {
                CameraPermissionGate(
                    onPermissionsGranted = { hasAudio ->
                        CameraScreen(
                            viewModel = cameraViewModel,
                            hasAudioPermission = hasAudio,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                )
            }
        }
    }
}

@Composable
fun CameraPermissionGate(
    onPermissionsGranted: @Composable (hasAudio: Boolean) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    var hasAudioPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        hasCameraPermission = results[Manifest.permission.CAMERA] ?: hasCameraPermission
        hasAudioPermission = results[Manifest.permission.RECORD_AUDIO] ?: hasAudioPermission
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission || !hasAudioPermission) {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.CAMERA,
                    Manifest.permission.RECORD_AUDIO
                )
            )
        }
    }

    if (hasCameraPermission) {
        onPermissionsGranted(hasAudioPermission)
    } else {
        PermissionRequiredScreen(
            onRequestPermission = {
                permissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.CAMERA,
                        Manifest.permission.RECORD_AUDIO
                    )
                )
            }
        )
    }
}

@Composable
fun PermissionRequiredScreen(
    onRequestPermission: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MatteBlack)
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Izin Kamera Diperlukan",
                color = SoftOffWhite,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Aplikasi memerlukan akses kamera dan mikrofon untuk mengoperasikan pipeline pengambilan gambar dan perekaman video bitrate tinggi.",
                color = NeutralGrey,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                lineHeight = 18.sp
            )
            Spacer(modifier = Modifier.height(28.dp))
            Button(
                onClick = onRequestPermission,
                colors = ButtonDefaults.buttonColors(
                    containerColor = SoftOffWhite,
                    contentColor = MatteBlack
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("request_permission_button")
            ) {
                Text(
                    text = "Izinkan Akses",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp
                )
            }
        }
    }
}
