package com.example

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.delay
import androidx.compose.material.icons.filled.KeyboardArrowUp
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

enum class LaunchState {
    INITIAL, PARTICLE_APPEARS, MODEL_LOADING, MODEL_ROTATING, TRANSITIONING
}

@Composable
fun BrainLaunchScreen(
    onNavigateToHome: () -> Unit
) {
    var state by remember { mutableStateOf(LaunchState.INITIAL) }

    LaunchedEffect(Unit) {
        state = LaunchState.PARTICLE_APPEARS
        delay(800)
        state = LaunchState.MODEL_LOADING
        delay(400)
        state = LaunchState.MODEL_ROTATING
        delay(15000)
        state = LaunchState.TRANSITIONING
        delay(800)
        onNavigateToHome()
    }

    val triggerNavigation = {
        if (state != LaunchState.TRANSITIONING) {
            state = LaunchState.TRANSITIONING
            onNavigateToHome()
        }
    }

    val sceneAlpha by animateFloatAsState(
        targetValue = when (state) {
            LaunchState.INITIAL, LaunchState.PARTICLE_APPEARS -> 0f
            LaunchState.TRANSITIONING -> 0f
            else -> 1f
        },
        animationSpec = tween(
            durationMillis = if (state == LaunchState.TRANSITIONING) 800 else 2000,
            easing = LinearOutSlowInEasing
        ),
        label = "SceneAlpha"
    )

    val density = LocalDensity.current
    val dragThresholdPx = with(density) { -100.dp.toPx() }
    var accumulatedDragY by remember { mutableFloatStateOf(0f) }
    
    var userRotationX by remember { mutableFloatStateOf(0f) }
    var userRotationY by remember { mutableFloatStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }
    var velocityX by remember { mutableFloatStateOf(0f) }
    var velocityY by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(Unit) {
        var lastTime = 0L
        while (isActive) {
            withFrameMillis { time ->
                if (lastTime == 0L) lastTime = time
                val dt = (time - lastTime) / 1000f
                lastTime = time

                if (!isDragging) {
                    userRotationY += velocityX * dt
                    userRotationX += velocityY * dt
                    userRotationX = userRotationX.coerceIn(-80f, 80f)

                    velocityX *= 0.95f
                    velocityY *= 0.95f
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFF001F29), Color.Black),
                    radius = 2000f
                )
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {
                    triggerNavigation()
                }
            )
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { 
                        isDragging = true 
                        accumulatedDragY = 0f 
                        velocityX = 0f
                        velocityY = 0f
                    },
                    onDragEnd = {
                        isDragging = false
                        if (accumulatedDragY < dragThresholdPx) {
                            triggerNavigation()
                        }
                        accumulatedDragY = 0f
                    },
                    onDragCancel = { 
                        isDragging = false 
                        accumulatedDragY = 0f
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        accumulatedDragY += dragAmount.y
                        
                        val dragSens = 0.35f
                        userRotationY += dragAmount.x * dragSens
                        userRotationX += dragAmount.y * dragSens
                        userRotationX = userRotationX.coerceIn(-80f, 80f)
                        
                        velocityX = dragAmount.x * dragSens * 60f
                        velocityY = dragAmount.y * dragSens * 60f
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        NeuralParticleSystem(state)

        if (state >= LaunchState.MODEL_LOADING) {
            val titleAlpha by animateFloatAsState(
                targetValue = if (state == LaunchState.TRANSITIONING) 0f else if (sceneAlpha > 0.5f) 1f else 0f,
                animationSpec = tween(if (state == LaunchState.TRANSITIONING) 600 else 1500, easing = FastOutSlowInEasing),
                label = "TitleAlpha"
            )
            val titleOffset by animateFloatAsState(
                targetValue = if (state == LaunchState.TRANSITIONING) -100f else 0f,
                animationSpec = tween(800, easing = FastOutSlowInEasing),
                label = "TitleOffset"
            )
            
            Text(
                text = "M E D I V A",
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 100.dp)
                    .offset(y = titleOffset.dp)
                    .alpha(titleAlpha),
                style = MaterialTheme.typography.displayMedium.copy(
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Light,
                    letterSpacing = androidx.compose.ui.unit.TextUnit(12f, androidx.compose.ui.unit.TextUnitType.Sp),
                    shadow = androidx.compose.ui.graphics.Shadow(
                        color = Color(0xFF00E5FF).copy(alpha = 0.5f),
                        offset = Offset(0f, 8f),
                        blurRadius = 24f
                    )
                )
            )
        }

        if (state >= LaunchState.MODEL_LOADING) {
            val auraAlpha by animateFloatAsState(
                targetValue = if (state == LaunchState.TRANSITIONING) 0f else if (sceneAlpha > 0.5f) 0.6f else 0f,
                animationSpec = tween(2000),
                label = "AuraAlpha"
            )
            
            Box(
                modifier = Modifier
                    .size(480.dp)
                    .alpha(auraAlpha)
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(Color(0xFF00E5FF).copy(alpha = 0.35f), Color.Transparent)
                        ),
                        shape = androidx.compose.foundation.shape.CircleShape
                    )
            )
        }

        if (state >= LaunchState.MODEL_LOADING) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(sceneAlpha),
                contentAlignment = Alignment.Center
            ) {
                HeroBrainScene(
                    state = state,
                    userRotationX = userRotationX,
                    userRotationY = userRotationY
                )
            }
        }

        if (state == LaunchState.PARTICLE_APPEARS || (state == LaunchState.MODEL_LOADING && sceneAlpha < 0.5f)) {
            val pulsingScale by animateFloatAsState(
                targetValue = if (state == LaunchState.MODEL_LOADING) 20f else 1f,
                animationSpec = tween(1000, easing = FastOutSlowInEasing),
                label = "PulsingScale"
            )
            val pulsingAlpha by animateFloatAsState(
                targetValue = if (state == LaunchState.MODEL_LOADING) 0f else 1f,
                animationSpec = tween(1000),
                label = "PulsingAlpha"
            )
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .scale(pulsingScale)
                    .alpha(pulsingAlpha)
                    .background(Color(0xFF00E5FF), shape = androidx.compose.foundation.shape.CircleShape)
            )
        }

        if (state == LaunchState.MODEL_ROTATING) {
            val buttonAlpha by animateFloatAsState(
                targetValue = if (sceneAlpha > 0.8f) 1f else 0f,
                animationSpec = tween(600),
                label = "ButtonAlpha"
            )
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 64.dp)
                    .alpha(buttonAlpha)
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { triggerNavigation() }
                    .padding(horizontal = 24.dp, vertical = 12.dp)
            ) {
                val infiniteTransition = rememberInfiniteTransition(label = "chevron_anim")
                val offset by infiniteTransition.animateFloat(
                    initialValue = 0f,
                    targetValue = -15f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(1000, easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "chevron_bounce"
                )

                Icon(
                    imageVector = androidx.compose.material.icons.Icons.Filled.KeyboardArrowUp,
                    contentDescription = "Swipe Up",
                    tint = Color(0xFF00E5FF).copy(alpha = 0.8f),
                    modifier = Modifier
                        .size(32.dp)
                        .offset(y = offset.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "TAP / SWIPE TO EXPLORE",
                    color = Color(0xFF00E5FF).copy(alpha = 0.6f),
                    style = MaterialTheme.typography.labelLarge.copy(letterSpacing = androidx.compose.ui.unit.TextUnit(2f, androidx.compose.ui.unit.TextUnitType.Sp))
                )
            }
        }
    }
}

@Composable
fun HeroBrainScene(
    state: LaunchState,
    userRotationX: Float,
    userRotationY: Float
) {
    val transitionProgress by animateFloatAsState(
        targetValue = if (state == LaunchState.TRANSITIONING) 1f else 0f,
        animationSpec = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
        label = "transitionProgress"
    )

    val brainNodes = remember {
        val list = mutableListOf<Position3D>()
        val random = Random(42)
        
        for (i in 0 until 110) {
            val isLeft = random.nextBoolean()
            val theta = random.nextFloat() * 2f * Math.PI.toFloat()
            val phi = random.nextFloat() * Math.PI.toFloat()
            
            val rX = 0.38f + random.nextFloat() * 0.08f
            val rY = 0.52f + random.nextFloat() * 0.08f
            val rZ = 0.38f + random.nextFloat() * 0.08f
            
            val baseX = sin(phi) * cos(theta) * rX
            val baseY = sin(phi) * sin(theta) * rY
            val baseZ = cos(phi) * rZ
            
            val posX = if (isLeft) baseX - 0.03f else baseX + 0.03f
            var posY = baseY
            var posZ = baseZ
            
            if (posY < -0.15f && posZ < 0f) {
                posY -= 0.08f
                posZ -= 0.04f
            }
            list.add(Position3D(posX, posY, posZ))
        }
        
        for (i in 0 until 30) {
            val theta = random.nextFloat() * 2f * Math.PI.toFloat()
            val phi = random.nextFloat() * Math.PI.toFloat()
            val posX = sin(phi) * cos(theta) * 0.18f
            val posY = -0.32f + sin(phi) * sin(theta) * 0.12f
            val posZ = -0.28f + cos(phi) * 0.12f
            list.add(Position3D(posX, posY, posZ))
        }

        for (i in 0 until 15) {
            val theta = random.nextFloat() * 2f * Math.PI.toFloat()
            val h = -0.38f - random.nextFloat() * 0.22f
            val posX = cos(theta) * 0.06f
            val posY = h
            val posZ = -0.08f + sin(theta) * 0.06f
            list.add(Position3D(posX, posY, posZ))
        }
        
        list
    }

    val brainSynapses = remember(brainNodes) {
        val connections = mutableListOf<Pair<Int, Int>>()
        val threshold = 0.24f
        for (i in brainNodes.indices) {
            val p1 = brainNodes[i]
            val distances = brainNodes.indices
                .filter { it != i }
                .map { j -> j to p1.distanceTo(brainNodes[j]) }
                .filter { it.second < threshold }
                .sortedBy { it.second }
                .take(3)
            
            distances.forEach { (j, _) ->
                if (i < j) {
                    connections.add(Pair(i, j))
                }
            }
        }
        connections
    }

    val infiniteRotation = rememberInfiniteTransition(label = "brain_system_rotation")
    val autoAngle by infiniteRotation.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(28000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "autoAngle"
    )

    val infiniteTime = rememberInfiniteTransition(label = "brain_system_time")
    val elapsedSeconds by infiniteTime.animateFloat(
        initialValue = 0f,
        targetValue = 100f,
        animationSpec = infiniteRepeatable(
            animation = tween(100000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "time"
    )

    val zoomScale by animateFloatAsState(
        targetValue = if (state == LaunchState.TRANSITIONING) 6.0f else 1f,
        animationSpec = tween(1200, easing = FastOutSlowInEasing),
        label = "zoomScale"
    )

    val sparks = remember(brainSynapses) {
        val synapsesList = brainSynapses.toList()
        if (synapsesList.isNotEmpty()) {
            List(45) {
                SparkDef(
                    connection = synapsesList[kotlin.random.Random.nextInt(synapsesList.size)],
                    speed = kotlin.random.Random.nextFloat() * 2.0f + 0.5f,
                    phase = kotlin.random.Random.nextFloat() * 2f * Math.PI.toFloat()
                )
            }
        } else emptyList()
    }

    val breathingOffset = sin(elapsedSeconds * 1.5f) * 12f
    val driftOffsetX = cos(elapsedSeconds * 0.3f) * 8f
    val driftOffsetY = sin(elapsedSeconds * 0.4f) * 8f

    Canvas(modifier = Modifier.fillMaxSize()) {
        val cx = size.width / 2f
        val cy = size.height / 2f + breathingOffset + driftOffsetY
        
        val radiusMultiplier = minOf(size.width, size.height) * 0.65f * zoomScale
        
        val radY = Math.toRadians((autoAngle + userRotationY).toDouble()).toFloat()
        val radX = Math.toRadians(userRotationX.toDouble()).toFloat()
        
        val cosY = cos(radY)
        val sinY = sin(radY)
        val cosX = cos(radX)
        val sinX = sin(radX)
        
        val baseCameraZ = 3.2f
        val targetCameraZ = -0.5f
        val cameraZ = baseCameraZ - (baseCameraZ - targetCameraZ) * transitionProgress
        
        val projectedNodes = brainNodes.map { node ->
            // Rotate around X-axis
            val y1 = node.y * cosX - node.z * sinX
            val z1 = node.y * sinX + node.z * cosX
            
            // Rotate around Y-axis
            val x2 = node.x * cosY - z1 * sinY
            val z2 = node.x * sinY + z1 * cosY
            
            val y2 = y1

            val radZ = sin(elapsedSeconds * 0.2f) * 0.05f
            val cosZ = cos(radZ)
            val sinZ = sin(radZ)
            val finalX = x2 * cosZ - y2 * sinZ
            val finalY = x2 * sinZ + y2 * cosZ
            
            val zDiff = cameraZ - z2
            val isVis = zDiff > 0.1f
            val scaleFactor = if (isVis) 1.2f / zDiff else 0f
            
            val px = cx + (finalX + (driftOffsetX / 500f)) * radiusMultiplier * scaleFactor
            val py = cy - finalY * radiusMultiplier * scaleFactor
            
            ProjectedNode(
                px = px,
                py = py,
                depthAlpha = if (isVis) (1f - (z2 + 1f)/2.5f).coerceIn(0f, 1f) else 0f,
                scaleFactor = scaleFactor,
                isBehindCamera = !isVis
            )
        }

        brainSynapses.forEach { (i, j) ->
            val p1 = projectedNodes[i]
            val p2 = projectedNodes[j]
            
            if (!p1.isBehindCamera && !p2.isBehindCamera) {
                val combinedAlpha = p1.depthAlpha * p2.depthAlpha * 0.45f
                val strokeWidth = 1.2f.dp.toPx() * minOf(p1.scaleFactor, p2.scaleFactor)
                
                drawLine(
                    color = Color(0xFF00E5FF).copy(alpha = combinedAlpha),
                    start = Offset(p1.px, p1.py),
                    end = Offset(p2.px, p2.py),
                    strokeWidth = strokeWidth
                )
            }
        }

        sparks.forEach { spark ->
            val progress = (sin(elapsedSeconds * spark.speed + spark.phase) + 1f) / 2f
            
            val p1 = projectedNodes[spark.connection.first]
            val p2 = projectedNodes[spark.connection.second]
            
            if (!p1.isBehindCamera && !p2.isBehindCamera) {
                val sparkX = p1.px + (p2.px - p1.px) * progress
                val sparkY = p1.py + (p2.py - p1.py) * progress
                val z1State = p1.depthAlpha
                val z2State = p2.depthAlpha
                val sparkDepthAlpha = z1State + (z2State - z1State) * progress
                
                if (sparkDepthAlpha > 0f) {
                    val scale1 = p1.scaleFactor
                    val scale2 = p2.scaleFactor
                    val sparkScale = scale1 + (scale2 - scale1) * progress
                    
                    val sparkRadius = 2f.dp.toPx() * sparkScale
                    
                    val breathingVal = (sin(elapsedSeconds * 3f + spark.phase) + 1f) / 2f
                    val activeSparkRadius = sparkRadius * (1f + breathingVal * 0.6f)
                    
                    drawCircle(
                        color = Color.White.copy(alpha = sparkDepthAlpha * (0.6f + breathingVal * 0.4f)),
                        radius = activeSparkRadius,
                        center = Offset(sparkX, sparkY)
                    )
                    
                    drawCircle(
                        color = Color(0xFF00E5FF).copy(alpha = sparkDepthAlpha * 0.5f * breathingVal),
                        radius = activeSparkRadius * 2.5f,
                        center = Offset(sparkX, sparkY)
                    )
                }
            }
        }

        projectedNodes.forEachIndexed { idx, p ->
            if (!p.isBehindCamera) {
                val nodeRadius = 3.5f.dp.toPx() * p.scaleFactor
                val alpha = p.depthAlpha.coerceIn(0f, 1f)
                
                if (idx % 8 == 0) {
                    val pulse = (sin(elapsedSeconds * 3f + idx) + 1f) / 2f
                    drawCircle(
                        color = Color(0xFF00E5FF).copy(alpha = alpha * 0.25f * (1f - pulse)),
                        radius = nodeRadius * (1.5f + pulse * 2f),
                        center = Offset(p.px, p.py)
                    )
                }
                
                drawCircle(
                    color = Color.White.copy(alpha = alpha * 0.9f),
                    radius = nodeRadius * 0.6f,
                    center = Offset(p.px, p.py)
                )
                
                drawCircle(
                    color = Color(0xFF00E5FF).copy(alpha = alpha),
                    radius = nodeRadius,
                    center = Offset(p.px, p.py)
                )
            }
        }
    }
}

@Composable
fun NeuralParticleSystem(state: LaunchState) {
    var time by remember { mutableFloatStateOf(0f) }
    
    LaunchedEffect(state) {
        var lastTime = 0L
        if (state >= LaunchState.PARTICLE_APPEARS) {
            while (isActive) {
                withFrameMillis { frameTime ->
                    if(lastTime == 0L) lastTime = frameTime
                    val delta = (frameTime - lastTime) / 1000f
                    lastTime = frameTime
                    time += if (state == LaunchState.TRANSITIONING) delta * 6f else delta
                }
            }
        }
    }

    val particles = remember {
        List(150) {
            NeuralParticle(
                angle = Random.nextFloat() * 360f,
                radiusRatio = Random.nextFloat(),
                zDepth = Random.nextFloat() * 0.9f + 0.1f,
                speed = Random.nextFloat() * 20f + 5f,
                baseRadius = Random.nextFloat() * 3f + 1f
            )
        }
    }

    val activeAlpha by animateFloatAsState(
        targetValue = when (state) {
            LaunchState.INITIAL, LaunchState.PARTICLE_APPEARS -> 0f
            else -> 1f
        },
        animationSpec = tween(1500),
        label = "ParticlesSystemAlpha"
    )

    Canvas(modifier = Modifier.fillMaxSize().alpha(activeAlpha)) {
        val cx = size.width / 2
        val cy = size.height / 2
        val maxRadiusX = size.width * 0.9f
        val maxRadiusY = size.height * 0.9f
        
        particles.forEach { p ->
            val animatedZ = if (state == LaunchState.TRANSITIONING) {
                p.zDepth + (time % 5f) * 1.5f 
            } else p.zDepth
            
            val currentAngle = Math.toRadians((p.angle + time * p.speed * p.zDepth).toDouble())
            
            val orbitRadiusX = maxRadiusX * p.radiusRatio * animatedZ
            val orbitRadiusY = maxRadiusY * p.radiusRatio * animatedZ
            
            val px = cx + cos(currentAngle).toFloat() * orbitRadiusX
            val py = cy + sin(currentAngle).toFloat() * orbitRadiusY
            
            val currentRadius = p.baseRadius * animatedZ
            val baseAlpha = 0.7f * (1f - p.zDepth)
            
            val transitionAlpha = if (state == LaunchState.TRANSITIONING) {
                maxOf(0f, 1f - (animatedZ - 1.2f))
            } else 1f
            
            drawCircle(
                color = Color(0xFF00E5FF).copy(alpha = minOf(1f, maxOf(0f, baseAlpha * transitionAlpha))),
                radius = currentRadius,
                center = Offset(px, py)
            )
        }
    }
}

data class NeuralParticle(
    var angle: Float,
    var radiusRatio: Float,
    var zDepth: Float,
    var speed: Float,
    val baseRadius: Float
)

data class Position3D(val x: Float, val y: Float, val z: Float) {
    fun distanceTo(other: Position3D): Float {
        val dx = x - other.x
        val dy = y - other.y
        val dz = z - other.z
        return kotlin.math.sqrt(dx * dx + dy * dy + dz * dz)
    }
}

data class ProjectedNode(
    val px: Float,
    val py: Float,
    val depthAlpha: Float,
    val scaleFactor: Float,
    val isBehindCamera: Boolean
)

data class SparkDef(
    val connection: Pair<Int, Int>,
    val speed: Float,
    val phase: Float
)

