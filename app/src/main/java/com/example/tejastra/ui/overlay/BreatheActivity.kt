package com.example.tejastra.ui.overlay

import android.os.Bundle
import android.os.CountDownTimer
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import com.example.tejastra.service.TejAstraAccessibilityService
import com.example.tejastra.ui.theme.*

/**
 * Full-screen overlay activity shown when a blocked app is opened.
 * Forces user to breathe for 5 seconds and type a reason.
 */
class BreatheActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val packageName = intent.getStringExtra("package_name") ?: ""
        val appName = intent.getStringExtra("app_name") ?: "this app"
        val timeLimit = intent.getIntExtra("time_limit", 5)
        val isScrollBlock = intent.getBooleanExtra("is_scroll_block", false)
        val isDailyLimit = intent.getBooleanExtra("is_daily_limit", false)
        val isSessionExpired = intent.getBooleanExtra("is_session_expired", false)
        val isReelsBlock = intent.getBooleanExtra("is_reels_block", false)
        val isCreditLimit = intent.getBooleanExtra("is_credit_limit", false)
        val isDeepWorkBlock = intent.getBooleanExtra("is_deep_work_block", false)
        val deepWorkReason = intent.getStringExtra("deep_work_reason")

        setContent {
            TejAstraTheme {
                BreatheScreen(
                    appName = appName,
                    timeLimit = timeLimit,
                    isScrollBlock = isScrollBlock,
                    isDailyLimit = isDailyLimit,
                    isSessionExpired = isSessionExpired,
                    isReelsBlock = isReelsBlock,
                    isCreditLimit = isCreditLimit,
                    isDeepWorkBlock = isDeepWorkBlock,
                    deepWorkReason = deepWorkReason,
                    onProceed = {
                        TejAstraAccessibilityService.instance?.onBreatheComplete(packageName, timeLimit)
                        
                        if (packageName.isNotEmpty()) {
                            val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
                            if (launchIntent != null) {
                                launchIntent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                startActivity(launchIntent)
                            }
                        }
                        finish()
                    },
                    onGoBack = {
                        TejAstraAccessibilityService.instance?.resetBreatheTracking()
                        finish()
                        // Go home
                        val homeIntent = android.content.Intent(android.content.Intent.ACTION_MAIN).apply {
                            addCategory(android.content.Intent.CATEGORY_HOME)
                            flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        startActivity(homeIntent)
                    }
                )
            }
        }
    }
}

@Composable
fun BreatheScreen(
    appName: String,
    timeLimit: Int,
    isScrollBlock: Boolean,
    isDailyLimit: Boolean,
    isSessionExpired: Boolean,
    isReelsBlock: Boolean,
    isCreditLimit: Boolean,
    isDeepWorkBlock: Boolean,
    deepWorkReason: String?,
    onProceed: () -> Unit,
    onGoBack: () -> Unit,
) {
    var phase by remember { 
        mutableIntStateOf(if (isSessionExpired || isReelsBlock || isCreditLimit || isDeepWorkBlock) 3 else 0) 
    }
    // 0 = breathing animation
    // 1 = type reason
    // 2 = ready to proceed
    // 3 = informational block (reels / session expired)

    var breatheSeconds by remember { mutableIntStateOf(5) }
    var typedReason by remember { mutableStateOf("") }
    var showContent by remember { mutableStateOf(false) }

    val breatheScale = rememberInfiniteTransition(label = "breathe")
    val scale by breatheScale.animateFloat(
        initialValue = 0.6f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(2500, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breatheScale"
    )

    val breatheAlpha by breatheScale.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(2500, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breatheAlpha"
    )

    // Fade in
    LaunchedEffect(Unit) {
        showContent = true
    }

    // Countdown timer for breathing
    LaunchedEffect(phase) {
        if (phase == 0) {
            object : CountDownTimer(5000, 1000) {
                override fun onTick(millisUntilFinished: Long) {
                    breatheSeconds = (millisUntilFinished / 1000).toInt() + 1
                }

                override fun onFinish() {
                    breatheSeconds = 0
                    phase = 1
                }
            }.start()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Void)
            .systemBarsPadding()
            .imePadding(),
        contentAlignment = Alignment.Center
    ) {
        AnimatedVisibility(
            visible = showContent,
            enter = fadeIn(tween(800)),
            exit = fadeOut(tween(400))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 40.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                when (phase) {
                    0 -> {
                        // ── Breathing Phase ──
                        Text(
                            text = if (isDailyLimit) "your daily limit is reached"
                            else if (isScrollBlock) "you've been scrolling mindlessly"
                            else "hold on",
                            style = MaterialTheme.typography.labelMedium,
                            color = TextTertiary,
                            letterSpacing = 3.sp,
                        )

                        Spacer(modifier = Modifier.height(48.dp))

                        // Breathing circle
                        Box(
                            modifier = Modifier
                                .size(120.dp)
                                .scale(scale)
                                .alpha(breatheAlpha)
                                .background(Snow, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "$breatheSeconds",
                                style = MaterialTheme.typography.displayMedium,
                                color = Void,
                                fontWeight = FontWeight.Thin,
                            )
                        }

                        Spacer(modifier = Modifier.height(48.dp))

                        Text(
                            text = "breathe",
                            style = MaterialTheme.typography.headlineLarge,
                            color = Snow,
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = "inhale deeply and hold",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary,
                        )
                    }

                    1 -> {
                        // ── Type Reason Phase ──
                        if (!isDailyLimit) {
                            Text(
                                text = "why do you need to open",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary,
                            )
    
                            Spacer(modifier = Modifier.height(8.dp))
    
                            Text(
                                text = appName.lowercase(),
                                style = MaterialTheme.typography.headlineLarge,
                                color = Snow,
                            )
    
                            Spacer(modifier = Modifier.height(8.dp))
    
                            Text(
                                text = "or are you opening it mindlessly?",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextTertiary,
                            )
                        } else {
                            Text(
                                text = "limit reached",
                                style = MaterialTheme.typography.headlineLarge,
                                color = Snow,
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "you've exhausted your time on ${appName.lowercase()} today.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextTertiary,
                                textAlign = TextAlign.Center,
                            )
                        }

                        Spacer(modifier = Modifier.height(48.dp))

                        if (!isDailyLimit) {
                            val isReasonValid = typedReason.trim().isNotEmpty()

                            OutlinedTextField(
                                value = typedReason,
                                onValueChange = { typedReason = it },
                                modifier = Modifier.fillMaxWidth(),
                                placeholder = {
                                    Text(
                                        "type your reason...",
                                        color = TextDisabled,
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Snow,
                                    unfocusedTextColor = Snow,
                                    cursorColor = Snow,
                                    focusedBorderColor = BorderMedium,
                                    unfocusedBorderColor = BorderSubtle,
                                ),
                                shape = RoundedCornerShape(12.dp),
                                singleLine = false,
                                minLines = 2,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                                keyboardActions = KeyboardActions(
                                    onGo = {
                                        if (isReasonValid) {
                                            phase = 2
                                            onProceed()
                                        }
                                    }
                                )
                            )
    
                            Spacer(modifier = Modifier.height(48.dp))
    
                            // Proceed button — always visible but enabled if reason typed
                            Button(
                                onClick = {
                                    phase = 2
                                    onProceed()
                                },
                                enabled = isReasonValid,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Snow,
                                    contentColor = Void,
                                    disabledContainerColor = Snow.copy(alpha = 0.3f),
                                    disabledContentColor = Void.copy(alpha = 0.5f)
                                ),
                                shape = RoundedCornerShape(12.dp),
                            ) {
                                Text(
                                    text = if (timeLimit > 0) "open for $timeLimit min" else "continue",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
    
                            Spacer(modifier = Modifier.height(16.dp))
                        }

                        TextButton(onClick = onGoBack) {
                            Text(
                                text = if (isDailyLimit) "step back" else "I don't need it",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextTertiary,
                            )
                        }
                    }
                    
                    3 -> {
                        // ── Block Info Phase ──
                        Text(
                            text = when {
                                isSessionExpired -> "session expired"
                                isReelsBlock -> "reels blocked"
                                isCreditLimit -> "attention exhausted"
                                isDeepWorkBlock -> "deep work mode"
                                else -> "blocked"
                            },
                            style = MaterialTheme.typography.headlineLarge,
                            color = Snow,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = when {
                                isSessionExpired -> "your time limit for ${appName.lowercase()} has ended." 
                                isReelsBlock -> "short-form content is blocked on ${appName.lowercase()}."
                                isCreditLimit -> "you have no attention credits left for today. reset happens at midnight."
                                isDeepWorkBlock -> deepWorkReason ?: "You are in Deep Work Mode. Distractions are not allowed."
                                else -> "access restricted."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextTertiary,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(modifier = Modifier.height(48.dp))
                        Button(
                            onClick = onGoBack,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Snow,
                                contentColor = Void
                            ),
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Text(
                                text = "understood",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
            }
        }

        // Bottom attribution
        Text(
            text = "tejastra",
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp),
            style = MaterialTheme.typography.labelSmall,
            color = TextDisabled,
            letterSpacing = 4.sp,
        )
    }
}

private val EaseInOutCubic = CubicBezierEasing(0.65f, 0f, 0.35f, 1f)
