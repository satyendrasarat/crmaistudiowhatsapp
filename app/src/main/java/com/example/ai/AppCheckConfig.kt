package com.example.ai

import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.BuildConfig
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.recaptcha.RecaptchaAppCheckProviderFactory

private const val TAG = "AppCheckInit"

private const val DEBUG_TOKEN_EXTRA = "FIREBASE_APPCHECK_DEBUG_TOKEN"
private const val DEBUG_STORE_PREFIX = "com.google.firebase.appcheck.debug.store."
private const val DEBUG_SECRET_KEY = "com.google.firebase.appcheck.debug.DEBUG_SECRET"
private const val RECAPTCHA_SITE_KEY_RES = "recaptcha_site_key"

@Volatile
private var appCheckProviderInstalled = false

/**
 * Guarantees the default FirebaseApp is initialized in the current process.
 * Falls back to building FirebaseOptions directly from merged Google Services string
 * resources if FirebaseApp.initializeApp(context) returns null (e.g., when namespace
 * differs from applicationId or during background service startup).
 */
fun ensureFirebaseInitialized(context: Context): FirebaseApp? {
    val appContext = context.applicationContext ?: context
    synchronized(FirebaseApp::class.java) {
        if (FirebaseApp.getApps(appContext).isNotEmpty()) {
            return runCatching { FirebaseApp.getInstance() }.getOrNull()
        }

        val defaultApp = runCatching { FirebaseApp.initializeApp(appContext) }.getOrNull()
        if (defaultApp != null) {
            Log.d(TAG, "Default FirebaseApp initialized via standard resource lookup.")
            return defaultApp
        }

        val options = buildFirebaseOptionsFromResources(appContext)
        if (options == null) {
            Log.e(TAG, "Unable to resolve google_app_id / google_api_key resources for FirebaseApp.")
            return null
        }

        return runCatching {
            FirebaseApp.initializeApp(appContext, options).also {
                Log.d(TAG, "Default FirebaseApp initialized via explicit FirebaseOptions fallback.")
            }
        }.onFailure { e ->
            Log.e(TAG, "Explicit FirebaseApp.initializeApp(context, options) failed", e)
        }.getOrNull()
    }
}

private fun buildFirebaseOptionsFromResources(context: Context): FirebaseOptions? {
    val appId = resolveStringResource(context, "google_app_id")
    val apiKey = resolveStringResource(context, "google_api_key")
    if (appId.isNullOrBlank() || apiKey.isNullOrBlank()) {
        return null
    }

    val builder = FirebaseOptions.Builder()
        .setApplicationId(appId)
        .setApiKey(apiKey)

    resolveStringResource(context, "project_id")?.takeIf { it.isNotBlank() }?.let {
        builder.setProjectId(it)
    }
    resolveStringResource(context, "gcm_defaultSenderId")?.takeIf { it.isNotBlank() }?.let {
        builder.setGcmSenderId(it)
    }
    resolveStringResource(context, "google_storage_bucket")?.takeIf { it.isNotBlank() }?.let {
        builder.setStorageBucket(it)
    }

    return builder.build()
}

private fun resolveStringResource(context: Context, name: String): String? {
    val packagesToCheck = linkedSetOf(context.packageName, "com.example")
    for (pkg in packagesToCheck) {
        val resId = context.resources.getIdentifier(name, "string", pkg)
        if (resId != 0) {
            return runCatching { context.getString(resId) }.getOrNull()
        }
    }
    return null
}

fun configureAppCheck(context: Context, intent: Intent?) {
    val appContext = context.applicationContext ?: context
    try {
        // 1. Ensure default FirebaseApp is initialized before touching FirebaseAppCheck
        val firebaseApp = ensureFirebaseInitialized(appContext)
        if (firebaseApp == null) {
            Log.e(TAG, "Skipping App Check setup because default FirebaseApp could not be initialized.")
            return
        }

        if (BuildConfig.DEBUG) {
            // 2. Debug: Read debug token from Intent extra
            val debugToken = intent?.getStringExtra(DEBUG_TOKEN_EXTRA)

            if (!debugToken.isNullOrEmpty()) {
                val persistenceKey = firebaseApp.persistenceKey
                val prefs = appContext.getSharedPreferences(
                    "$DEBUG_STORE_PREFIX$persistenceKey",
                    Context.MODE_PRIVATE
                )
                // Synchronous commit() ensures DebugAppCheckProviderFactory reads the persisted token immediately.
                prefs.edit()
                    .putString(DEBUG_SECRET_KEY, debugToken)
                    .commit()
                Log.d(TAG, "App Check debug secret configured successfully.")
            } else {
                Log.w(TAG, "No debug token found in Intent extra.")
            }

            if (!appCheckProviderInstalled || !debugToken.isNullOrEmpty()) {
                FirebaseAppCheck.getInstance(firebaseApp).installAppCheckProviderFactory(
                    DebugAppCheckProviderFactory.getInstance()
                )
                appCheckProviderInstalled = true
                Log.d(TAG, "DebugAppCheckProviderFactory installed.")
            }
        } else {
            // 3. Release: Look up recaptcha_site_key safely without throwing Resources.NotFoundException
            if (!appCheckProviderInstalled) {
                val siteKey = resolveStringResource(appContext, RECAPTCHA_SITE_KEY_RES)
                if (!siteKey.isNullOrBlank()) {
                    FirebaseAppCheck.getInstance(firebaseApp).installAppCheckProviderFactory(
                        RecaptchaAppCheckProviderFactory.getInstance(siteKey)
                    )
                    appCheckProviderInstalled = true
                    Log.d(TAG, "RecaptchaAppCheckProviderFactory installed.")
                } else {
                    Log.e(TAG, "recaptcha_site_key string resource not found or blank.")
                }
            }
        }
    } catch (e: Exception) {
        Log.e(TAG, "Failed to initialize Firebase App Check", e)
    }
}
