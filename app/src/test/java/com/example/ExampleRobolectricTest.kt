package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.ai.ensureFirebaseInitialized
import com.google.firebase.FirebaseApp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("ReplyFlow", appName)
    }

    @Test
    fun `default FirebaseApp initializes cleanly from google-services resources`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val app = ensureFirebaseInitialized(context)
        assertNotNull("Expected default FirebaseApp to initialize", app)
        assertEquals(FirebaseApp.DEFAULT_APP_NAME, app!!.name)
        assertEquals("gen-lang-client-0657077058", app.options.projectId)
        assertNotNull(FirebaseApp.getInstance())
    }
}
