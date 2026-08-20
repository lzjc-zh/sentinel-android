package com.deepseek.lzjc.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import com.deepseek.lzjc.R
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(
    onSplashFinished: () -> Unit
) {
    LaunchedEffect(Unit) {
        delay(1200)
        onSplashFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Image(
                painter = painterResource(id = R.mipmap.ic_launcher),
                contentDescription = "哨兵 Logo",
                modifier = Modifier.size(160.dp)
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = buildAnnotatedString {
                    withStyle(SpanStyle(color = Color(0xFF4D6BFE))) { append("哨") }
                    withStyle(SpanStyle(color = Color(0xFFFF6A00))) { append("兵") }
                },
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                letterSpacing = 4.sp
            )
            Text(
                "AI 用量监控",
                color = Color(0xFF999999),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}
