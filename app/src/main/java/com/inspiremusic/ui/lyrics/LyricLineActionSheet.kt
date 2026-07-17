package com.inspiremusic.ui.lyrics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.inspiremusic.data.LyricCardExporter
import com.inspiremusic.model.AudioItem
import com.inspiremusic.model.LrcLine
import com.inspiremusic.ui.components.LiquidGlassBottomSheetFrame
import com.inspiremusic.ui.components.LiquidGlassBottomSheetModifier
import com.inspiremusic.ui.components.LiquidGlassBottomSheetShape
import com.inspiremusic.ui.theme.LocalAppIsDark
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LyricLineActionSheet(
    song: AudioItem,
    line: LrcLine,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isDark = LocalAppIsDark.current
    val textColor = if (isDark) Color.White else Color(0xFF17171A)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        modifier = LiquidGlassBottomSheetModifier,
        containerColor = Color.Transparent,
        shape = LiquidGlassBottomSheetShape,
        dragHandle = null,
        scrimColor = Color.Black.copy(alpha = 0.44f)
    ) {
        LiquidGlassBottomSheetFrame(useSharedBackdrop = false) {
            Column(
                modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 22.dp, vertical = 8.dp)
            ) {
                Text(line.text, color = textColor, fontSize = 22.sp, lineHeight = 30.sp, fontWeight = FontWeight.Black)
                line.translation?.let { Text(it, color = textColor.copy(alpha = 0.58f), fontSize = 14.sp, modifier = Modifier.padding(top = 6.dp)) }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = { onToggleFavorite(); onDismiss() },
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(if (isFavorite) Icons.Default.Star else Icons.Default.StarBorder, null)
                        Text(if (isFavorite) "取消收藏" else "收藏", modifier = Modifier.padding(start = 7.dp))
                    }
                    Button(
                        onClick = { scope.launch { LyricCardExporter.share(context, song, line) } },
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Share, null)
                        Text("分享卡片", modifier = Modifier.padding(start = 7.dp))
                    }
                }
            }
        }
    }
}
