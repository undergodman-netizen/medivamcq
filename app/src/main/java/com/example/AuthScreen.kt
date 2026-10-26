package com.example

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.designsystem.*
import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import androidx.compose.ui.res.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthScreen(
    viewModel: AuthViewModel,
    onLoginSuccess: () -> Unit
) {
    val appState by viewModel.state.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current
    val context = LocalContext.current

    val backgroundGradient = Brush.verticalGradient(
        colors = listOf(
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.22f),
            MaterialTheme.colorScheme.background,
            MaterialTheme.colorScheme.background
        )
    )

    val webClientId = stringResource(id = R.string.default_web_client_id)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                val account = task.getResult(ApiException::class.java)
                if (account != null && account.idToken != null) {
                    val idToken = account.idToken!!
                    viewModel.signInWithGoogle(idToken, context) { success, errorMsg ->
                        if (success) {
                            android.widget.Toast.makeText(context, "Logged in successfully!", android.widget.Toast.LENGTH_SHORT).show()
                            onLoginSuccess()
                        } else {
                            val msg = errorMsg ?: "Google Sign-In verification failed"
                            viewModel.setAuthError(msg)
                            android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_LONG).show()
                        }
                    }
                } else {
                    val errMsg = "Failed to retrieve Google Account credentials."
                    viewModel.setAuthError(errMsg)
                    android.widget.Toast.makeText(context, errMsg, android.widget.Toast.LENGTH_LONG).show()
                }
            } catch (e: ApiException) {
                if (e.statusCode != 12501) { // SIGN_IN_CANCELLED
                    val errMsg = "Google Sign-In error: ${e.localizedMessage ?: e.toString()} (status: ${e.statusCode})"
                    viewModel.setAuthError(errMsg)
                    android.widget.Toast.makeText(context, errMsg, android.widget.Toast.LENGTH_LONG).show()
                } else {
                    viewModel.setAuthError(null)
                }
            } catch (e: Exception) {
                val errMsg = "Google Sign-In failed: ${e.localizedMessage ?: e.toString()}"
                viewModel.setAuthError(errMsg)
                android.widget.Toast.makeText(context, errMsg, android.widget.Toast.LENGTH_LONG).show()
            }
        } else {
            viewModel.setAuthError(null)
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize().testTag("auth_screen"),
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { },
                navigationIcon = {
                    // Hidden
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    navigationIconContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(backgroundGradient)
                .padding(innerPadding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Header
                Box(
                    modifier = Modifier.size(80.dp).clip(RoundedCornerShape(24.dp)).background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Bolt,
                        contentDescription = "Fast Onboarding",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(48.dp)
                    )
                }
                Spacer(modifier = Modifier.height(24.dp))
                
                Text(
                    text = "Welcome",
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold),
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Sign in to instantly sync your data and pick up right where you left off.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                
                Spacer(modifier = Modifier.height(48.dp))

                // Action: Google Sign In
                Button(
                    onClick = {
                        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                            .requestIdToken(webClientId)
                            .requestEmail()
                            .build()
                        val googleSignInClient = GoogleSignIn.getClient(context, gso)
                        launcher.launch(googleSignInClient.signInIntent)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .testTag("google_auth_button_primary")
                        .shadow(elevation = 8.dp, shape = RoundedCornerShape(32.dp)),
                    shape = RoundedCornerShape(32.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color.White)
                                .border(0.5.dp, Color(0xFFE2E2E2), RoundedCornerShape(14.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "G",
                                color = Color(0xFF4285F4), // Google Blue
                                fontWeight = FontWeight.Black,
                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Black)
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text = "Continue with Google",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                if (appState.authErrorMessage != null) {
                    Spacer(modifier = Modifier.height(16.dp))
                    WedikaCard {
                        Text(
                            text = appState.authErrorMessage.orEmpty(),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            // --- Loading Overlay ---
            AppLoadingOverlay(
                visible = appState.isAuthLoading || appState.isDownloadingDb,
                message = if (appState.isDownloadingDb) "Syncing Database..." else "Authenticating...",
                subMessage = if (appState.isDownloadingDb) {
                    val progress = appState.importProgress
                    if (progress != null) "${(progress * 100).toInt()}%" else null
                } else null
            )
        }
    }
}
