package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.auth.signOutUser
import com.example.firebase.configureAppCheck
import com.example.ui.auth.AuthScreen
import com.example.ui.chat.ChatScreen
import com.example.ui.chat.ChatViewModel
import com.example.ui.theme.EishlarMaxTheme
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // STEP 1: Configure App Check with launch intent before any UI or Firebase operations
        configureAppCheck(this, intent)

        // STEP 2: Enable Edge-to-Edge and render UI
        enableEdgeToEdge()
        setContent {
            EishlarMaxTheme {
                EishlarAppRoot()
            }
        }
    }
}

@Composable
fun EishlarAppRoot() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var currentUser by remember { mutableStateOf(Firebase.auth.currentUser) }
    var offlineGuestMode by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            currentUser = auth.currentUser
            if (auth.currentUser != null) {
                offlineGuestMode = false
            }
        }
        Firebase.auth.addAuthStateListener(listener)
        onDispose { Firebase.auth.removeAuthStateListener(listener) }
    }

    val user = currentUser
    if (user != null) {
        val uid = user.uid
        key(uid) {
            val chatViewModel: ChatViewModel = viewModel(
                key = uid,
                factory = ChatViewModel.provideFactory(
                    currentUserId = uid,
                    isOfflineGuestOnly = false
                )
            )
            ChatScreen(
                viewModel = chatViewModel,
                userEmail = user.email,
                isOfflineGuestOnly = false,
                onSignOutOrSwitchAccount = {
                    signOutUser(context, scope)
                }
            )
        }
    } else if (offlineGuestMode) {
        BackHandler { offlineGuestMode = false }
        key("offline_guest") {
            val offlineViewModel: ChatViewModel = viewModel(
                key = "offline_guest",
                factory = ChatViewModel.provideFactory(
                    currentUserId = null,
                    isOfflineGuestOnly = true
                )
            )
            ChatScreen(
                viewModel = offlineViewModel,
                userEmail = null,
                isOfflineGuestOnly = true,
                onSignOutOrSwitchAccount = { offlineGuestMode = false }
            )
        }
    } else {
        AuthScreen(
            onAuthSuccess = { currentUser = Firebase.auth.currentUser },
            onOpenOfflineMode = { offlineGuestMode = true }
        )
    }
}
