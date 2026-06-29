package com.applemusic.clone

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.applemusic.clone.settings.AppSettingsController
import com.applemusic.clone.settings.LocalAppSettingsController
import com.applemusic.clone.ui.AppNavigation
import com.applemusic.clone.ui.theme.AppleMusicCloneTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val context = LocalContext.current
            val settingsController = remember { AppSettingsController(context.applicationContext) }
            val appSettings by settingsController.settings.collectAsState()
            AppleMusicCloneTheme(
                themeMode = appSettings.themeMode,
                useDynamicColor = appSettings.useDynamicColor,
                accentColorStyle = appSettings.accentColorStyle
            ) {
                CompositionLocalProvider(LocalAppSettingsController provides settingsController) {
                    PermissionGate {
                        AppNavigation()
                    }
                }
            }
        }
    }
}

@Composable
fun PermissionGate(content: @Composable () -> Unit) {
    val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

    var hasPermission by remember {
        mutableStateOf(false) // will be set below
    }
    var permissionChecked by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
        permissionChecked = true
    }

    // 检查并初始化权限状态
    val context = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            hasPermission = true
            permissionChecked = true
        } else {
            launcher.launch(permission)
        }
    }

    if (!permissionChecked) {
        // 等待权限检查
        Box(Modifier.fillMaxSize().background(Color.Black))
        return
    }

    if (hasPermission) {
        content()
    } else {
        // 权限被拒绝时的引导界面
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(40.dp)
            ) {
                Icon(
                    Icons.Default.FolderOpen,
                    contentDescription = null,
                    modifier = Modifier.size(72.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(20.dp))
                Text(
                    "需要访问音乐文件",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    "请在系统设置中授予「存储」权限以浏览您的音乐库。",
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(0.6f),
                    textAlign = TextAlign.Center,
                    lineHeight = 22.sp
                )
                Spacer(Modifier.height(28.dp))
                Button(
                    onClick = { launcher.launch(permission) },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("重试", color = Color.White, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
