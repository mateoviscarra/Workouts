package com.mateoviscarra.doitall.calendar

import android.app.Activity
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.mateoviscarra.doitall.calendar.CalendarInfo
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Warning
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val TAG = "CalendarSettingsScreen"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarSettingsScreen(
    onBack: () -> Unit,
    calendarManager: CalendarManager
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val clipboardManager = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    var isConnecting by remember { mutableStateOf(false) }
    var connectionStatus by remember { mutableStateOf(calendarManager.getAuthState()) }
    var showDisconnectDialog by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var successMessage by remember { mutableStateOf<String?>(null) }

    Log.d(TAG, "CalendarSettingsScreen init, connectionStatus: $connectionStatus")

    // Automatically check/validate existing session on screen entry
    LaunchedEffect(Unit) {
        val currentAccount = calendarManager.getLastSignedInAccount()
        if (currentAccount != null) {
            val silentClient = if (activity != null) {
                calendarManager.getGoogleSignInClient(activity)
            } else {
                calendarManager.getGoogleSignInClient()
            }
            silentClient.silentSignIn()
                .addOnSuccessListener { account ->
                    Log.d(TAG, "Initial silent sign-in succeeded: ${account.email}")
                    calendarManager.setAuthSuccess()
                    connectionStatus = calendarManager.getAuthState()
                }
                .addOnFailureListener { e ->
                    Log.w(TAG, "Initial silent sign-in failed", e)
                    val statusCode = (e as? ApiException)?.statusCode
                    calendarManager.setAuthFailure("Session expired or key mismatch (Code $statusCode)")
                    connectionStatus = calendarManager.getAuthState()
                }
        }
    }

    val signInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        Log.d(TAG, "Sign-in result: resultCode=${result.resultCode}, data=${result.data}")
        isConnecting = false
        
        // First try silent sign-in - check if there's already a signed in account
        val silentClient = if (activity != null) {
            calendarManager.getGoogleSignInClient(activity)
        } else {
            calendarManager.getGoogleSignInClient()
        }
        silentClient.silentSignIn().addOnSuccessListener { account ->
            Log.d(TAG, "Silent sign-in success: ${account.email}")
            calendarManager.setAuthSuccess()
            connectionStatus = calendarManager.getAuthState()
            errorMessage = null
            successMessage = "Successfully connected to Google Calendar!"
        }.addOnFailureListener { e ->
            Log.e(TAG, "Silent sign-in failed, trying explicit", e)
            // Try explicit sign-in result
            try {
                val task = result.data?.let { GoogleSignIn.getSignedInAccountFromIntent(it) }
                task?.addOnSuccessListener { account ->
                    Log.d(TAG, "Explicit sign-in success: ${account.email}")
                    calendarManager.setAuthSuccess()
                    connectionStatus = calendarManager.getAuthState()
                    errorMessage = null
                    successMessage = "Successfully connected to Google Calendar!"
                }?.addOnFailureListener { e2 ->
                    Log.e(TAG, "Explicit sign-in also failed", e2)
                    val statusCode = (e2 as? ApiException)?.statusCode
                    calendarManager.setAuthFailure("Sign-in failed (Code $statusCode)")
                    connectionStatus = calendarManager.getAuthState()
                    errorMessage = if (statusCode == 10 || statusCode == CommonStatusCodes.DEVELOPER_ERROR) {
                        "DEVELOPER_ERROR_10"
                    } else {
                        "Sign-in failed: ${e2.localizedMessage ?: "Status code $statusCode"}"
                    }
                }
            } catch (e2: Exception) {
                Log.e(TAG, "Error handling sign-in", e2)
                errorMessage = "Sign-in error: ${e2.message}"
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Calendar Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            val hasAuthError = calendarManager.hasAuthError
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = when {
                        connectionStatus.isConnected -> MaterialTheme.colorScheme.primaryContainer
                        hasAuthError -> MaterialTheme.colorScheme.errorContainer
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    }
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = when {
                                connectionStatus.isConnected -> Icons.Default.Check
                                hasAuthError -> Icons.Default.Warning
                                else -> Icons.Default.CalendarMonth
                            },
                            contentDescription = null,
                            tint = when {
                                connectionStatus.isConnected -> MaterialTheme.colorScheme.onPrimaryContainer
                                hasAuthError -> MaterialTheme.colorScheme.onErrorContainer
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = when {
                                connectionStatus.isConnected -> "Google Calendar Connected"
                                hasAuthError -> "Reconnection Required"
                                else -> "Google Calendar Not Connected"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = when {
                                connectionStatus.isConnected -> MaterialTheme.colorScheme.onPrimaryContainer
                                hasAuthError -> MaterialTheme.colorScheme.onErrorContainer
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                    if (connectionStatus.isConnected) {
                        Text(
                            text = "Connected as ${connectionStatus.email ?: "Google account"}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = "Your workouts can be synced to Google Calendar.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    } else if (hasAuthError) {
                        if (!connectionStatus.email.isNullOrBlank()) {
                            Text(
                                text = "Account: ${connectionStatus.email}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                        Text(
                            text = "Calendar authentication failed or the app signing key changed. Please sign in again below to re-authorize.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    } else {
                        Text(
                            text = "Connect your Google Calendar to sync workout schedules.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (errorMessage != null) {
                if (errorMessage == "DEVELOPER_ERROR_10") {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Warning,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Google Cloud Setup (Error 10)",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                                IconButton(onClick = { errorMessage = null }) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Dismiss",
                                        tint = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                            }

                            Text(
                                text = "This Windows build uses a new debug keystore SHA-1. In your existing Google Cloud project, simply add this SHA-1 as a second Android OAuth Client:\n\n" +
                                        "1. Go to console.cloud.google.com\n" +
                                        "2. Select your project → APIs & Services → Credentials\n" +
                                        "3. Click Create Credentials → OAuth client ID\n" +
                                        "4. Application type: Android\n" +
                                        "5. Package name: com.mateoviscarra.doitall\n" +
                                        "6. SHA-1 fingerprint: paste the key below",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )

                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surface
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "80:22:73:88:96:7D:30:D4:6C:BF:A6:F0:94:65:A4:2D:97:AF:88:53",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.weight(1f)
                                    )
                                    IconButton(
                                        onClick = {
                                            clipboardManager.setText(
                                                AnnotatedString("80:22:73:88:96:7D:30:D4:6C:BF:A6:F0:94:65:A4:2D:97:AF:88:53")
                                            )
                                            successMessage = "SHA-1 copied to clipboard!"
                                        }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ContentCopy,
                                            contentDescription = "Copy SHA-1"
                                        )
                                    }
                                }
                            }

                            Text(
                                text = "Once saved in Google Cloud Console, wait ~1 minute for propagation, then tap 'Sign in with Google' below.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                } else {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = errorMessage!!,
                                modifier = Modifier.weight(1f),
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            IconButton(onClick = { errorMessage = null }) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Dismiss",
                                    tint = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    }
                }
            }

            if (successMessage != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer
                    )
                ) {
                    Text(
                        text = successMessage!!,
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "How it works",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Sign in with your Google account to sync workouts. " +
                               "Your credentials stay on your device and Google handles the authentication. " +
                               "This means you won't be logged out unexpectedly.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            if (connectionStatus.isConnected) {
                // Calendar selection - manual entry
                var calendarIdInput by remember { mutableStateOf(calendarManager.getSelectedCalendarId()) }
                var isEditing by remember { mutableStateOf(false) }

                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Calendar",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )

                        Text(
                            text = "Enter the calendar ID to sync workouts to. Find it in Google Calendar settings (Calendar ID in the calendar settings page, usually looks like yourname@group.calendar.google.com)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        OutlinedTextField(
                            value = calendarIdInput,
                            onValueChange = { calendarIdInput = it },
                            label = { Text("Calendar ID") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            enabled = isEditing || calendarIdInput.isEmpty()
                        )

                        if (calendarIdInput.isNotEmpty() && !isEditing) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { isEditing = true },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Edit")
                                }
                                Button(
                                    onClick = {
                                        calendarManager.setSelectedCalendarId(calendarIdInput)
                                        successMessage = "Calendar saved!"
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Save")
                                }
                            }
                        } else if (isEditing) {
                            Button(
                                onClick = {
                                    calendarManager.setSelectedCalendarId(calendarIdInput)
                                    isEditing = false
                                    successMessage = "Calendar saved!"
                                },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = calendarIdInput.isNotEmpty()
                            ) {
                                Text("Save Calendar ID")
                            }
                        }

                        if (calendarIdInput == "primary") {
                            Text(
                                text = "Using your primary/default calendar",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                OutlinedButton(
                    onClick = { showDisconnectDialog = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.LinkOff, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Disconnect Calendar")
                }
            } else {
                Button(
                    onClick = {
                        if (activity == null) {
                            errorMessage = "Cannot start sign-in (Activity unavailable)"
                            return@Button
                        }
                        Log.d(TAG, "Starting Google Sign-In...")
                        isConnecting = true
                        errorMessage = null
                        val signInClient = calendarManager.getGoogleSignInClient(activity)
                        signInLauncher.launch(signInClient.signInIntent)
                    },
                    enabled = !isConnecting,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isConnecting) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .padding(end = 8.dp)
                                .height(20.dp)
                                .width(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Icon(Icons.Default.Link, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(if (isConnecting) "Connecting..." else "Sign in with Google")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Your credentials are stored securely on your device and managed by Google Play Services.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    if (showDisconnectDialog) {
        AlertDialog(
            onDismissRequest = { showDisconnectDialog = false },
            title = { Text("Disconnect Calendar?") },
            text = { Text("This will sign you out of Google Calendar. You can sign in again anytime.") },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            calendarManager.disconnect()
                            connectionStatus = CalendarAuthState(isConnected = false)
                            showDisconnectDialog = false
                            successMessage = "Disconnected from Google Calendar."
                        }
                    }
                ) {
                    Text("Disconnect")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDisconnectDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    LaunchedEffect(successMessage) {
        if (successMessage != null) {
            delay(3000)
            successMessage = null
        }
    }
}