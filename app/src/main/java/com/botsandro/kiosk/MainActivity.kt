diff --git a/app/src/main/java/com/botsandro/kiosk/MainActivity.kt b/app/src/main/java/com/botsandro/kiosk/MainActivity.kt
index 29d6dcf19bf858517cd0a2a6e118b519a1669de3..287235a638790603ef6a81d9df6737451fe220eb 100644
--- a/app/src/main/java/com/botsandro/kiosk/MainActivity.kt
+++ b/app/src/main/java/com/botsandro/kiosk/MainActivity.kt
@@ -1,129 +1,133 @@
 package com.botsandro.kiosk
 
 import android.app.ActivityManager
+import android.app.admin.DevicePolicyManager
 import android.content.Context
 import android.content.Intent
 import android.content.pm.PackageManager
 import android.graphics.drawable.Drawable
 import android.os.Build
 import android.net.Uri
 import android.os.Bundle
 import android.os.PowerManager
 import android.text.InputType
 import android.provider.Settings
 import android.view.Gravity
 import android.view.View
 import android.widget.Button
 import android.widget.EditText
 import android.widget.LinearLayout
 import android.widget.TextView
 import android.widget.Toast
 import androidx.activity.OnBackPressedCallback
 import androidx.appcompat.app.AlertDialog
 import androidx.appcompat.app.AppCompatActivity
 import androidx.drawerlayout.widget.DrawerLayout
 import androidx.recyclerview.widget.GridLayoutManager
 import androidx.recyclerview.widget.RecyclerView
 import com.google.android.material.appbar.MaterialToolbar
 import java.security.MessageDigest
 
 private const val PREFS_NAME = "kiosk_prefs"
 private const val KEY_PIN_HASH = "pin_hash"
 private const val KEY_WHITELIST = "whitelist"
+private const val KEY_BACKGROUND_PROMPT_SHOWN = "background_prompt_shown"
 
 data class KioskApp(
     val packageName: String,
     val label: String,
     val icon: Drawable
 )
 
 class MainActivity : AppCompatActivity() {
 
     private val prefs by lazy { getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }
     private val packageManagerRef: PackageManager by lazy { packageManager }
 
     private lateinit var drawerLayout: DrawerLayout
     private lateinit var appsRecyclerView: RecyclerView
     private lateinit var whitelistContainer: LinearLayout
     private lateinit var emptyStateText: TextView
 
     private var relockOnResume = true
 
     override fun onCreate(savedInstanceState: Bundle?) {
         super.onCreate(savedInstanceState)
         setContentView(R.layout.activity_main)
 
         drawerLayout = findViewById(R.id.drawerLayout)
         appsRecyclerView = findViewById(R.id.appsRecyclerView)
         whitelistContainer = findViewById(R.id.whitelistContainer)
         emptyStateText = findViewById(R.id.emptyStateText)
 
         val toolbar: MaterialToolbar = findViewById(R.id.toolbar)
         toolbar.setNavigationOnClickListener { tryOpenSettings() }
 
         findViewById<Button>(R.id.setPinButton).setOnClickListener { setPin() }
         findViewById<Button>(R.id.changePinButton).setOnClickListener { changePin() }
         findViewById<Button>(R.id.deletePinButton).setOnClickListener { deletePin() }
         findViewById<Button>(R.id.addWhitelistAppButton).setOnClickListener { addWhitelistApp() }
         findViewById<Button>(R.id.backgroundPermissionButton).setOnClickListener { requestBackgroundPermission() }
         findViewById<Button>(R.id.exitKioskButton).setOnClickListener { requestExitKioskMode() }
 
         appsRecyclerView.layoutManager = GridLayoutManager(this, 3)
         refreshWhitelistUi()
 
         onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
             override fun handleOnBackPressed() {
                 if (drawerLayout.isDrawerOpen(Gravity.END)) {
                     drawerLayout.closeDrawer(Gravity.END)
                 }
             }
         })
 
+        requestBackgroundPermissionOnFirstOpen()
         activateLockTaskIfAllowed()
         hideSystemUi()
     }
 
     override fun onWindowFocusChanged(hasFocus: Boolean) {
         super.onWindowFocusChanged(hasFocus)
         if (hasFocus) hideSystemUi()
     }
 
     override fun onResume() {
         super.onResume()
         if (relockOnResume) {
             activateLockTaskIfAllowed()
         } else {
             relockOnResume = true
         }
         hideSystemUi()
     }
 
     private fun tryOpenSettings() {
         val pinHash = prefs.getString(KEY_PIN_HASH, null)
         if (pinHash.isNullOrBlank()) {
-            drawerLayout.openDrawer(Gravity.END)
+            toast("Defina um PIN para proteger as configurações")
+            setPin()
             return
         }
 
         askPin("Digite o PIN para abrir configurações") { pin ->
             if (hash(pin) == pinHash) {
                 drawerLayout.openDrawer(Gravity.END)
             } else {
                 toast("PIN inválido")
             }
         }
     }
 
     private fun setPin() {
         askPin("Digite o novo PIN") { pin ->
             if (pin.length < 4) {
                 toast("PIN deve ter ao menos 4 dígitos")
                 return@askPin
             }
             prefs.edit().putString(KEY_PIN_HASH, hash(pin)).apply()
             toast("PIN salvo")
         }
     }
 
     private fun changePin() {
         val currentHash = prefs.getString(KEY_PIN_HASH, null)
@@ -191,172 +195,200 @@ class MainActivity : AppCompatActivity() {
         val whitelist = prefs.getStringSet(KEY_WHITELIST, emptySet()) ?: emptySet()
         val apps = queryLaunchableApps().filter { whitelist.contains(it.packageName) }
 
         emptyStateText.visibility = if (apps.isEmpty()) View.VISIBLE else View.GONE
         appsRecyclerView.adapter = AppShortcutAdapter(apps) { app -> launchWhitelistedApp(app.packageName) }
 
         whitelistContainer.removeAllViews()
         if (apps.isEmpty()) {
             val text = TextView(this)
             text.text = "Lista branca vazia"
             whitelistContainer.addView(text)
             return
         }
 
         apps.forEach { app ->
             val row = LinearLayout(this).apply {
                 orientation = LinearLayout.HORIZONTAL
                 gravity = Gravity.CENTER_VERTICAL
             }
 
             val label = TextView(this).apply {
                 text = app.label
                 layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
             }
 
-            val remove = Button(this).apply {
+            val removeButton = Button(this).apply {
                 text = "Remover"
                 setOnClickListener {
-                    val updated = whitelist.toMutableSet().apply { remove(app.packageName) }
+                    val updated = whitelist.toMutableSet()
+                    updated.remove(app.packageName)
                     prefs.edit().putStringSet(KEY_WHITELIST, updated).apply()
                     refreshWhitelistUi()
                 }
             }
 
             row.addView(label)
-            row.addView(remove)
+            row.addView(removeButton)
             whitelistContainer.addView(row)
         }
     }
 
     private fun queryLaunchableApps(): List<KioskApp> {
         val intent = Intent(Intent.ACTION_MAIN, null).addCategory(Intent.CATEGORY_LAUNCHER)
         val resolved = packageManagerRef.queryIntentActivities(intent, 0)
 
         return resolved
             .map {
                 KioskApp(
                     packageName = it.activityInfo.packageName,
                     label = it.loadLabel(packageManagerRef).toString(),
                     icon = it.loadIcon(packageManagerRef)
                 )
             }
             .sortedBy { it.label.lowercase() }
     }
 
     private fun launchWhitelistedApp(packageName: String) {
         drawerLayout.closeDrawer(Gravity.END)
         launchApp(packageName)
     }
 
     private fun launchApp(packageName: String) {
-
         val launchIntent = packageManagerRef.getLaunchIntentForPackage(packageName)
         if (launchIntent == null) {
             toast("Não foi possível abrir o app")
             return
         }
 
-        startActivity(launchIntent)
+        val lockTaskActive = isLockTaskActive()
+        val targetIsLockTaskPermitted = isLockTaskPermitted(packageName)
+
+        if (lockTaskActive && !targetIsLockTaskPermitted) {
+            stopLockTaskIfActive()
+            toast("Abrindo app permitido. O modo kiosk será retomado ao voltar.")
+        }
+
+        try {
+            startActivity(launchIntent)
+        } catch (_: Exception) {
+            if (lockTaskActive) {
+                activateLockTaskIfAllowed()
+            }
+            toast("Não foi possível abrir o app")
+        }
     }
 
     private fun requestBackgroundPermission() {
         val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
         val packageName = packageName
         if (powerManager.isIgnoringBatteryOptimizations(packageName)) {
             toast("Permissão de segundo plano já concedida")
             return
         }
 
         try {
             val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                 data = Uri.parse("package:$packageName")
             }
             startActivity(intent)
         } catch (_: Exception) {
             val fallback = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
             startActivity(fallback)
         }
     }
 
     private fun requestExitKioskMode() {
         val pinHash = prefs.getString(KEY_PIN_HASH, null)
         if (pinHash.isNullOrBlank()) {
-            relockOnResume = false
-            stopLockTaskIfActive()
-            toast("Modo kiosk desativado")
+            toast("Defina um PIN antes de liberar a saída do modo kiosk")
+            setPin()
             return
         }
 
         askPin("Digite o PIN para sair do modo kiosk") { pin ->
             if (hash(pin) == pinHash) {
                 relockOnResume = false
                 stopLockTaskIfActive()
                 toast("Modo kiosk desativado")
             } else {
                 toast("PIN inválido")
             }
         }
     }
 
     private fun askPin(title: String, onConfirm: (String) -> Unit) {
         val input = EditText(this).apply {
             inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
             hint = "PIN"
         }
 
         AlertDialog.Builder(this)
             .setTitle(title)
             .setView(input)
             .setPositiveButton("Confirmar") { _, _ ->
                 onConfirm(input.text.toString())
             }
             .setNegativeButton("Cancelar", null)
             .show()
     }
 
     private fun hash(input: String): String {
         val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
         return bytes.joinToString("") { "%02x".format(it) }
     }
 
     private fun toast(message: String) {
         Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
     }
 
     private fun stopLockTaskIfActive() {
-        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
-            val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
-            if (activityManager.lockTaskModeState != ActivityManager.LOCK_TASK_MODE_NONE) {
-                try {
-                    stopLockTask()
-                } catch (_: IllegalStateException) {
-                    // ignore
-                }
+        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && isLockTaskActive()) {
+            try {
+                stopLockTask()
+            } catch (_: IllegalStateException) {
+                // ignore
             }
         }
     }
 
     private fun activateLockTaskIfAllowed() {
-        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
-            val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
-            if (activityManager.lockTaskModeState == ActivityManager.LOCK_TASK_MODE_NONE) {
-                try {
-                    startLockTask()
-                } catch (_: IllegalArgumentException) {
-                    // Device owner mode not configured yet.
-                }
+        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !isLockTaskActive()) {
+            try {
+                startLockTask()
+            } catch (_: IllegalArgumentException) {
+                // Device owner mode not configured yet.
             }
         }
     }
 
+    private fun isLockTaskActive(): Boolean {
+        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return false
+        val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
+        return activityManager.lockTaskModeState != ActivityManager.LOCK_TASK_MODE_NONE
+    }
+
+    private fun isLockTaskPermitted(targetPackage: String): Boolean {
+        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return false
+        val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
+        return dpm.isLockTaskPermitted(targetPackage)
+    }
+
+    private fun requestBackgroundPermissionOnFirstOpen() {
+        val alreadyPrompted = prefs.getBoolean(KEY_BACKGROUND_PROMPT_SHOWN, false)
+        if (alreadyPrompted) return
+
+        prefs.edit().putBoolean(KEY_BACKGROUND_PROMPT_SHOWN, true).apply()
+        requestBackgroundPermission()
+    }
+
     private fun hideSystemUi() {
         window.decorView.systemUiVisibility = (
             View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                 or View.SYSTEM_UI_FLAG_FULLSCREEN
                 or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                 or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                 or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                 or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
             )
     }
 }
