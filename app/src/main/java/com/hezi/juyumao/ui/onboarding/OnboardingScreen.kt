package com.hezi.juyumao.ui.onboarding

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.theme.MiuixTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel

/**
 * 首次使用引导页（T12）：P1 品牌 / P2 核心能力 / P3 权限说明 / P4 收尾
 * 完成或跳过均可进入首页；「连接 NAS」可跳转 SMB 页。
 */
@Composable
fun OnboardingScreen(
    onStart: () -> Unit,
    onConnectNas: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val pagerState = rememberPagerState(pageCount = { 4 })
    val coroutineScope = rememberCoroutineScope()

    // 权限申请（P4 集中申请，拒绝不阻塞）
    val permissionStatus = remember { mutableStateMapOf<String, Boolean>() }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        grants.forEach { (permission, granted) ->
            permissionStatus[permission] = granted
        }
    }

    // 初始化权限状态（已授权/未授权）
    LaunchedEffect(Unit) {
        OnboardingPermissions.requiredPermissions.forEach { permission ->
            val granted = ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
            permissionStatus[permission] = granted
        }
    }

    // Onboarding 艺术化深色引导页：渐变/白字为设计意图，不随 Monet 变色
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF1A1A2E), Color(0xFF16213E))
                )
            ),
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            when (page) {
                0 -> OnboardingPage1()
                1 -> OnboardingPage2()
                2 -> OnboardingPage3()
                3 -> OnboardingPage4(
                    permissionStatus = permissionStatus,
                    onRequestPermissions = {
                        permissionLauncher.launch(OnboardingPermissions.requiredPermissions.toTypedArray())
                    },
                    onStart = onStart,
                    onConnectNas = onConnectNas,
                )
            }
        }

        // 页码指示器
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            repeat(4) { index ->
                Box(
                    modifier = Modifier
                        .size(if (pagerState.currentPage == index) 10.dp else 8.dp)
                        .clip(CircleShape)
                        .background(
                            color = if (pagerState.currentPage == index)
                                com.hezi.juyumao.ui.theme.LocalExtendedColors.current.hiResGold
                            else Color.White.copy(alpha = 0.3f),
                        ),
                )
            }
        }

        // 右上角跳过按钮（非末页）
        if (pagerState.currentPage < 3) {
            TextButton(
                text = "跳过",
                onClick = { viewModel.completeOnboarding(); onStart() },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 48.dp, end = 16.dp),
                colors = top.yukonga.miuix.kmp.basic.ButtonDefaults.textButtonColors(textColor = Color.White.copy(alpha = 0.7f)),
            )
        }
    }
}

/** 每页元素 staggered 渐入 */
@Composable
private fun AnimatedEntrance(
    delayMs: Int,
    visible: Boolean = true,
    content: @Composable () -> Unit,
) {
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow,
        ),
        label = "entrance_alpha",
    )
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(spring(stiffness = Spring.StiffnessLow)) +
            slideInVertically(spring(stiffness = Spring.StiffnessLow)) { it / 4 },
    ) {
        content()
    }
}

/** P1 品牌页 */
@Composable
private fun OnboardingPage1() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        AnimatedEntrance(delayMs = 0) {
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .background(
                        brush = Brush.radialGradient(
                            listOf(
                                com.hezi.juyumao.ui.theme.LocalExtendedColors.current.hiResGold.copy(alpha = 0.3f),
                                Color.Transparent,
                            )
                        ),
                        shape = CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = com.hezi.juyumao.ui.theme.LocalExtendedColors.current.hiResGold,
                    modifier = Modifier.size(56.dp),
                )
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
        AnimatedEntrance(delayMs = 150) {
            Text(
                "局域猫",
                style = MiuixTheme.textStyles.title1.copy(fontWeight = FontWeight.Bold),
                color = Color.White,
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        AnimatedEntrance(delayMs = 300) {
            Text(
                "局域网 NAS 音乐播放器",
                style = MiuixTheme.textStyles.title4,
                color = Color.White.copy(alpha = 0.7f),
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        AnimatedEntrance(delayMs = 450) {
            Text(
                "直连 SMB 服务器，畅享无损高音质",
                style = MiuixTheme.textStyles.body2,
                color = Color.White.copy(alpha = 0.5f),
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** P2 核心能力页 */
@Composable
private fun OnboardingPage2() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            "核心能力",
            style = MiuixTheme.textStyles.title2.copy(fontWeight = FontWeight.Bold),
            color = Color.White,
            modifier = Modifier.padding(bottom = 24.dp),
        )
        AnimatedEntrance(delayMs = 100) {
            FeatureCard(Icons.Default.Cloud, "NAS 直连串流", "SMB 协议直连，免拷贝在线播放")
        }
        Spacer(modifier = Modifier.height(12.dp))
        AnimatedEntrance(delayMs = 250) {
            FeatureCard(
                Icons.Default.HighQuality,
                "Hi-Res 高音质",
                "采样率/位深完整展示，金色 Hi-Res 标识",
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        AnimatedEntrance(delayMs = 400) {
            FeatureCard(Icons.Default.Lyrics, "沉浸歌词与均衡器", "逐行歌词 + 专业均衡器与音效")
        }
    }
}

@Composable
private fun FeatureCard(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, desc: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.defaultColors(color = Color.White.copy(alpha = 0.08f)),
        cornerRadius = 14.dp,
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(
                        color = com.hezi.juyumao.ui.theme.LocalExtendedColors.current.hiResGold.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(10.dp),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, null, tint = com.hezi.juyumao.ui.theme.LocalExtendedColors.current.hiResGold,
                    modifier = Modifier.size(24.dp))
            }
            Column {
                Text(title, style = MiuixTheme.textStyles.subtitle, color = Color.White)
                Text(desc, style = MiuixTheme.textStyles.footnote1,
                    color = Color.White.copy(alpha = 0.6f))
            }
        }
    }
}

/** P3 权限说明页（仅说明不弹窗） */
@Composable
private fun OnboardingPage3() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            "权限说明",
            style = MiuixTheme.textStyles.title2.copy(fontWeight = FontWeight.Bold),
            color = Color.White,
            modifier = Modifier.padding(bottom = 24.dp),
        )
        AnimatedEntrance(delayMs = 100) {
            PermissionExplainCard(Icons.Default.Notifications, "通知权限", "播放控制与进度显示")
        }
        Spacer(modifier = Modifier.height(12.dp))
        AnimatedEntrance(delayMs = 250) {
            PermissionExplainCard(Icons.Default.PhoneAndroid, "本地音频", "扫描设备上的本地音乐")
        }
        Spacer(modifier = Modifier.height(24.dp))
        AnimatedEntrance(delayMs = 400) {
            Text(
                "所有权限仅用于本地功能，不上传任何数据",
                style = MiuixTheme.textStyles.footnote1,
                color = Color.White.copy(alpha = 0.5f),
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun PermissionExplainCard(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, desc: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.defaultColors(color = Color.White.copy(alpha = 0.08f)),
        cornerRadius = 14.dp,
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(icon, null, tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.size(28.dp))
            Column {
                Text(title, style = MiuixTheme.textStyles.subtitle, color = Color.White)
                Text(desc, style = MiuixTheme.textStyles.footnote1,
                    color = Color.White.copy(alpha = 0.6f))
            }
        }
    }
}

/** P4 收尾页：权限申请 + 开始使用 + 连接 NAS */
@Composable
private fun OnboardingPage4(
    permissionStatus: Map<String, Boolean>,
    onRequestPermissions: () -> Unit,
    onStart: () -> Unit,
    onConnectNas: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        AnimatedEntrance(delayMs = 0) {
            Text(
                "准备好了吗？",
                style = MiuixTheme.textStyles.title2.copy(fontWeight = FontWeight.Bold),
                color = Color.White,
            )
        }
        Spacer(modifier = Modifier.height(20.dp))

        // 权限状态列表
        AnimatedEntrance(delayMs = 150) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OnboardingPermissions.requiredPermissions.forEach { permission ->
                    val granted = permissionStatus[permission] ?: false
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                color = Color.White.copy(alpha = 0.06f),
                                shape = RoundedCornerShape(10.dp),
                            )
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(
                            if (granted) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                            null,
                            tint = if (granted)
                                com.hezi.juyumao.ui.theme.LocalExtendedColors.current.hiResGold
                            else Color.White.copy(alpha = 0.4f),
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            OnboardingPermissions.permissionLabel(permission),
                            style = MiuixTheme.textStyles.body2,
                            color = Color.White.copy(alpha = 0.9f),
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            if (granted) "已授权" else "未授权",
                            style = MiuixTheme.textStyles.footnote2,
                            color = if (granted) com.hezi.juyumao.ui.theme.LocalExtendedColors.current.hiResGold
                                    else Color.White.copy(alpha = 0.4f),
                        )
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(12.dp))

        // 申请权限按钮
        AnimatedEntrance(delayMs = 250) {
            OutlinedButton(
                onClick = onRequestPermissions,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
            ) {
                Icon(Icons.Default.Security, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("授权权限（可跳过）")
            }
        }
        Spacer(modifier = Modifier.height(12.dp))

        // 连接 NAS（次级）
        AnimatedEntrance(delayMs = 350) {
            OutlinedButton(
                onClick = onConnectNas,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = com.hezi.juyumao.ui.theme.LocalExtendedColors.current.hiResGold),
            ) {
                Icon(Icons.Default.Cloud, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("连接 NAS")
            }
        }
        Spacer(modifier = Modifier.height(12.dp))

        // 开始使用（主按钮）
        AnimatedEntrance(delayMs = 450) {
            Button(
                onClick = onStart,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = top.yukonga.miuix.kmp.basic.ButtonDefaults.buttonColors(
                    color = com.hezi.juyumao.ui.theme.LocalExtendedColors.current.hiResGold,
                    contentColor = Color.Black,
                ),
                cornerRadius = 14.dp,
            ) {
                Text("开始使用", style = MiuixTheme.textStyles.title4.copy(fontWeight = FontWeight.Bold))
            }
        }
    }
}
