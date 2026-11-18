package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.FolioAppShell
import com.example.ui.auth.AuthScreen
import com.example.ui.auth.authStateFlow
import com.example.ui.theme.FolioPdfTheme
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FolioPdfTheme {
                FolioRootNavigation()
            }
        }
    }
}

@Composable
fun FolioRootNavigation(auth: FirebaseAuth = Firebase.auth) {
    val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)
    var testUserName by remember { mutableStateOf<String?>(null) }
    val user = currentUser

    if (user != null) {
        key(user.uid) {
            FolioAppShell(
                currentUserId = user.uid,
                userName = user.displayName,
                isTestMode = false
            )
        }
    } else if (testUserName != null) {
        val activeTestUser = testUserName!!
        key("test_$activeTestUser") {
            FolioAppShell(
                currentUserId = activeTestUser,
                userName = activeTestUser,
                isTestMode = true,
                onTestSignOut = { testUserName = null }
            )
        }
    } else {
        AuthScreen(
            onAuthSuccess = {},
            onTestUserSignIn = { username -> testUserName = username }
        )
    }
}
