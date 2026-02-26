package com.botsandro.kiosk

import android.app.ActivityManager
import android.app.ActivityOptions
import android.app.admin.DevicePolicyManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val PREFS_NAME = "kiosk_prefs"
private const val KEY_ADMIN_PIN_HASH = "admin_pin_hash"
private const val KEY_WHITELIST = "whitelist"
private const val KEY_BG_PERMISSION_PROMPTED = "bg_permission_prompted"
private const val LOCK_TASK_RETRY_DELAY_MS = 1000L
private const val KEY_SYSTEM_HOME_COMPONENT = "system_home_component"
private const val KEY_KIOSK_ENABLED = "kiosk_enabled"

// Gesto: 4 dedos por 5 segundos
private const val EXIT_GESTURE_FINGERS = 4
private const val EXIT_GESTURE_HOLD_MS = 5000L

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

    // Status bar própria (Wi-Fi / Bateria / Hora)
    private lateinit var wifiText: TextView
    private lateinit var batteryText: TextView
    private lateinit var timeText: TextView

    private val relockHandler = Handler(Looper.getMainLooper())
    private var relockOnResume = true

    // ===== Gesto 4 dedos por 5s =====
    private val gestureHandler = Handler(Looper.getMainLooper())
    private var gestureArmed = false
    private var gestureTriggered = false
    private var gestureStartMs = 0L

    private val exitGestureRunnable = Runnable {
        if (gestureTriggered) return@Runnable
        gestureTriggered = true
        // 🔓 Sai do kiosk (sem PIN). O drawer já é protegido por PIN.
        exitKioskModeNoPin()
    }

    private val uiHandler = Handler(Looper.getMainLooper())
    private val timeFormat = SimpleDateFormat("HH:mm", Locale("pt", "BR"))
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            val pct = if (level >= 0 && scale > 0) (level * 100 / scale) else -1
            batteryText.text = if (pct >= 0) "🔋 $pct%" else "🔋 --%"
        }
    }

    private val timeTicker = object : Runnable {
        override fun run() {
            timeText.text = timeFormat.format(Date())
            uiHandler.postDelayed(this, 1000L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Evita overlay estranho em alguns devices
        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK

        drawerLayout = findViewById(R.id.drawerLayout)
        appsRecyclerView = findViewById(R.id.appsRecyclerView)
        whitelistContainer = findViewById(R.id.whitelistContainer)
        emptyStateText = findViewById(R.id.emptyStateText)

        wifiText = findViewById(R.id.wifiText)
        batteryText = findViewById(R.id.batteryText)
        timeText = findViewById(R.id.timeText)

        // bloqueia swipe para abrir drawer da direita
        drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED, Gravity.END)
        drawerLayout.addDrawerListener(object : DrawerLayout.SimpleDrawerListener() {
            override fun onDrawerClosed(drawerView: View) {
                drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED, Gravity.END)
            }
        })

        val toolbar: MaterialToolbar = findViewById(R.id.toolbar)
        toolbar.setNavigationOnClickListener { tryOpenSettings() }

        findViewById<Button>(R.id.setAdminPinButton).setOnClickListener { setAdminPin() }
        findViewById<Button>(R.id.changeAdminPinButton).setOnClickListener { changeAdminPin() }
        findViewById<Button>(R.id.deleteAdminPinButton).setOnClickListener { deleteAdminPin() }
        findViewById<Button>(R.id.addWhitelistAppButton).setOnClickListener { addWhitelistApp() }
        findViewById<Button>(R.id.backgroundPermissionButton).setOnClickListener { requestBackgroundPermission() }

        // ✅ Sair do kiosk: sem PIN (o drawer já exige PIN para abrir)
        findViewById<Button>(R.id.exitKioskButton).setOnClickListener { exitKioskModeNoPin() }

        appsRecyclerView.layoutManager = GridLayoutManager(this, 3)
        refreshWhitelistUi()

        // ✅ garante que o "launcher real" seja salvo (e nunca você mesmo)
        saveSystemHomeIfMissing()

        // default: kiosk ligado (primeira execução)
        if (!prefs.contains(KEY_KIOSK_ENABLED)) {
            prefs.edit().putBoolean(KEY_KIOSK_ENABLED, true).apply()
        }

        // ✅ Sempre que o app abre e kioskEnabled=true => entra em modo fixado
val kioskEnabled = prefs.getBoolean(KEY_KIOSK_ENABLED, true)
if (kioskEnabled) {
    applyLockTaskWhitelistFromPrefs()      // garante allowlist (app + whitelist)
    applyLockTaskFeaturesLockedDown()
    activateLockTaskIfAllowed()
    hideSystemUi()
} else {
            setKioskHomeEnabled(false)
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (drawerLayout.isDrawerOpen(Gravity.END)) {
                    drawerLayout.closeDrawer(Gravity.END)
                }
            }
        })

        ensureBackgroundPermissionPrompted()

        // ===== Statusbar própria =====
        registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        uiHandler.post(timeTicker)

        // ⚠️ precisa de android.permission.ACCESS_NETWORK_STATE no Manifest
        startNetworkMonitoring()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && prefs.getBoolean(KEY_KIOSK_ENABLED, true)) hideSystemUi()
    }

    override fun onResume() {
        super.onResume()

val kioskEnabled = prefs.getBoolean(KEY_KIOSK_ENABLED, true)
if (kioskEnabled) {
    applyLockTaskWhitelistFromPrefs()      // garante allowlist (app + whitelist)
    applyLockTaskFeaturesLockedDown()
    activateLockTaskIfAllowed()
    hideSystemUi()
} else {
            relockOnResume = true
        }

        applyLockTaskFeaturesLockedDown()
        hideSystemUi()
    }

    override fun onPause() {
        super.onPause()
        cancelExitGesture()
    }

    override fun onDestroy() {
        super.onDestroy()
        try { unregisterReceiver(batteryReceiver) } catch (_: Exception) {}
        uiHandler.removeCallbacks(timeTicker)
        stopNetworkMonitoring()
        cancelExitGesture()
    }

    // ==========================
    // Gesto 4 dedos por 5s
    // ==========================
    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        try {
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN,
                MotionEvent.ACTION_POINTER_DOWN -> {
                    if (!gestureTriggered && ev.pointerCount >= EXIT_GESTURE_FINGERS) {
                        if (!gestureArmed) {
                            gestureArmed = true
                            gestureStartMs = SystemClock.elapsedRealtime()
                            gestureHandler.removeCallbacks(exitGestureRunnable)
                            gestureHandler.postDelayed(exitGestureRunnable, EXIT_GESTURE_HOLD_MS)
                        }
                    }
                }

                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_POINTER_UP,
                MotionEvent.ACTION_CANCEL -> {
                    // ACTION_POINTER_UP ainda conta o dedo que está saindo, então -1
                    val remaining = ev.pointerCount - 1
                    if (remaining < EXIT_GESTURE_FINGERS) {
                        cancelExitGesture()
                    }
                }
            }
        } catch (_: Exception) {}

        return super.dispatchTouchEvent(ev)
    }

    private fun cancelExitGesture() {
        gestureHandler.removeCallbacks(exitGestureRunnable)
        gestureArmed = false
        gestureTriggered = false
        gestureStartMs = 0L
    }

    // ==========================
    // UI / PIN / Drawer
    // ==========================
private fun tryOpenSettings() {
    val adminPinHash = prefs.getString(KEY_ADMIN_PIN_HASH, null)

    // ✅ Se ainda não existe PIN, deixa abrir configurações
    if (adminPinHash.isNullOrBlank()) {
        toast("Defina um PIN Admin antes de sair do modo kiosk")
        drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED, Gravity.END)
        drawerLayout.openDrawer(Gravity.END)
        return
    }

    // 🔐 Se já existe PIN, exige autenticação
    askPin("Digite o PIN Admin para abrir configurações") { pin ->
        if (hash(pin) == adminPinHash) {
            drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED, Gravity.END)
            drawerLayout.openDrawer(Gravity.END)
        } else {
            toast("PIN Admin inválido")
        }
    }
}
    private fun setAdminPin() {
        askPin("Digite o novo PIN Admin") { pin ->
            if (pin.length < 4) {
                toast("PIN deve ter ao menos 4 dígitos")
                return@askPin
            }
            prefs.edit().putString(KEY_ADMIN_PIN_HASH, hash(pin)).apply()
            toast("PIN Admin salvo")
        }
    }

    private fun changeAdminPin() {
        val currentHash = prefs.getString(KEY_ADMIN_PIN_HASH, null)
        if (currentHash.isNullOrBlank()) {
            toast("Nenhum PIN Admin cadastrado")
            return
        }

        askPin("Digite o PIN Admin atual") currentPin@{ current ->
            if (hash(current) != currentHash) {
                toast("PIN Admin atual inválido")
                return@currentPin
            }

            askPin("Digite o novo PIN Admin") newPin@{ newPin ->
                if (newPin.length < 4) {
                    toast("PIN deve ter ao menos 4 dígitos")
                    return@newPin
                }
                prefs.edit().putString(KEY_ADMIN_PIN_HASH, hash(newPin)).apply()
                toast("PIN Admin alterado")
            }
        }
    }

    private fun deleteAdminPin() {
        val currentHash = prefs.getString(KEY_ADMIN_PIN_HASH, null)
        if (currentHash.isNullOrBlank()) {
            toast("Nenhum PIN Admin cadastrado")
            return
        }

        askPin("Digite o PIN Admin para excluir") { current ->
            if (hash(current) == currentHash) {
                prefs.edit().remove(KEY_ADMIN_PIN_HASH).apply()
                toast("PIN Admin removido")
            } else {
                toast("PIN Admin inválido")
            }
        }
    }

    private fun addWhitelistApp() {
        val whitelist = prefs.getStringSet(KEY_WHITELIST, emptySet())?.toMutableSet() ?: mutableSetOf()
        val availableApps = queryLaunchableApps().filterNot { whitelist.contains(it.packageName) }

        if (availableApps.isEmpty()) {
            toast("Nenhum app disponível para adicionar")
            return
        }

        val labels = availableApps.map { it.label }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Selecione um app")
            .setItems(labels) { _, index ->
                whitelist.add(availableApps[index].packageName)
                prefs.edit().putStringSet(KEY_WHITELIST, whitelist).apply()
                refreshWhitelistUi()
                applyLockTaskWhitelistFromPrefs()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun refreshWhitelistUi() {
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

            val removeButton = Button(this).apply {
                text = "Remover"
                setOnClickListener {
                    val updated = whitelist.toMutableSet().apply { remove(app.packageName) }
                    prefs.edit().putStringSet(KEY_WHITELIST, updated).apply()
                    refreshWhitelistUi()
                    applyLockTaskWhitelistFromPrefs()
                }
            }

            row.addView(label)
            row.addView(removeButton)
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
        val launchIntent = packageManagerRef.getLaunchIntentForPackage(packageName)?.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (launchIntent == null) {
            toast("Não foi possível abrir o app")
            return
        }

        relockOnResume = true
        startActivity(launchIntent)
    }

    @Suppress("unused")
    private fun launchWhitelistedAppWithBestEffortPinning(packageName: String, launchIntent: Intent) {
        val canLockTargetPackage = isLockTaskPermitted(packageName)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && canLockTargetPackage) {
            val options = ActivityOptions.makeBasic().setLockTaskEnabled(true).toBundle()
            startActivity(launchIntent, options)
            return
        }

        startActivity(launchIntent)
        relockHandler.postDelayed({
            if (!isFinishing && !isDestroyed) {
                activateLockTaskIfAllowed()
            }
        }, LOCK_TASK_RETRY_DELAY_MS)
    }

    private fun isLockTaskPermitted(packageName: String): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return false
        val devicePolicyManager = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        return try {
            devicePolicyManager.isLockTaskPermitted(packageName)
        } catch (_: Exception) {
            false
        }
    }

    private fun ensureBackgroundPermissionPrompted() {
        val alreadyPrompted = prefs.getBoolean(KEY_BG_PERMISSION_PROMPTED, false)
        if (alreadyPrompted) return

        prefs.edit().putBoolean(KEY_BG_PERMISSION_PROMPTED, true).apply()

        AlertDialog.Builder(this)
            .setTitle("Permissão de segundo plano")
            .setMessage("Para melhorar a estabilidade do kiosk, permita execução em segundo plano na próxima tela.")
            .setPositiveButton("Permitir") { _, _ -> requestBackgroundPermission() }
            .setNegativeButton("Agora não", null)
            .show()
    }

    private fun requestBackgroundPermission() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        val pkg = packageName
        if (powerManager.isIgnoringBatteryOptimizations(pkg)) {
            toast("Permissão de segundo plano já concedida")
            return
        }

        try {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:$pkg")
            }
            startActivity(intent)
        } catch (_: Exception) {
            val fallback = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            startActivity(fallback)
        }
    }

    // ==========================
    // SAIR DO KIOSK (sem PIN)
    // ==========================
    private fun exitKioskModeNoPin() {

        relockOnResume = false
        cancelExitGesture()

        stopLockTaskIfActive()
        //disableLockTaskPackages()

        // ✅ desabilita o HOME do kiosk (alias) para o sistema não voltar pra você
        setKioskHomeEnabled(false)

        val systemHome = getSavedSystemHome() ?: findBestSystemHomeComponent()

        if (isDeviceOwner()) {
            try { dpm().clearPackagePersistentPreferredActivities(adminComponent(), packageName) } catch (_: Exception) {}
            if (systemHome != null) setPersistentHome(systemHome)
        }

        toast("Modo kiosk desativado")
        launchRealHome(systemHome)

        finishAndRemoveTask()
    }

    private fun disableLockTaskPackages() {
        if (!isDeviceOwner()) return
        try { dpm().setLockTaskPackages(adminComponent(), emptyArray()) } catch (_: Exception) {}
    }

    private fun askPin(title: String, onConfirm: (String) -> Unit) {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            hint = "PIN"
        }

        AlertDialog.Builder(this)
            .setTitle(title)
            .setView(input)
            .setPositiveButton("Confirmar") { _, _ -> onConfirm(input.text.toString()) }
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            if (activityManager.lockTaskModeState != ActivityManager.LOCK_TASK_MODE_NONE) {
                try { stopLockTask() } catch (_: IllegalStateException) {}
            }
        }
    }

    private fun activateLockTaskIfAllowed() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            if (activityManager.lockTaskModeState == ActivityManager.LOCK_TASK_MODE_NONE) {
                try { startLockTask() } catch (_: IllegalArgumentException) {}
            }
        }
    }

    private fun hideSystemUi() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

    // =======================
    // Statusbar própria: Rede
    // =======================
    private fun startNetworkMonitoring() {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        fun updateNow() {
            val active = cm.activeNetwork
            val caps = cm.getNetworkCapabilities(active)

            val hasWifi = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
            val hasCell = caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true
            val hasEth = caps?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true

            wifiText.text = when {
                hasWifi -> "Wi-Fi: Conectado"
                hasEth -> "Rede: Cabo"
                hasCell -> "Rede: Móvel"
                else -> "Rede: Offline"
            }
        }

        updateNow()

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) = updateNow()
            override fun onLost(network: Network) = updateNow()
            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) = updateNow()
        }

        networkCallback = callback
        val request = NetworkRequest.Builder().build()
        cm.registerNetworkCallback(request, callback)
    }

    private fun stopNetworkMonitoring() {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        networkCallback?.let { cb ->
            try { cm.unregisterNetworkCallback(cb) } catch (_: Exception) {}
        }
        networkCallback = null
    }

    // =======================
    // Device Owner helpers
    // =======================
    private fun dpm(): DevicePolicyManager =
        getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager

    private fun adminComponent(): ComponentName =
        ComponentName(this, KioskDeviceAdminReceiver::class.java)

    private fun isDeviceOwner(): Boolean =
        try { dpm().isDeviceOwnerApp(packageName) } catch (_: Exception) { false }

    private fun applyLockTaskFeaturesLockedDown() {
        if (!isDeviceOwner()) return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return
        try { dpm().setLockTaskFeatures(adminComponent(), DevicePolicyManager.LOCK_TASK_FEATURE_NONE) } catch (_: Exception) {}
    }

    private fun findBestSystemHomeComponent(): ComponentName? {
        val pm = packageManager
        val resolved = pm.resolveActivity(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),
            PackageManager.MATCH_DEFAULT_ONLY
        )

        val ai = resolved?.activityInfo
        if (ai != null) {
            val c = ComponentName(ai.packageName, ai.name)

            val isFallbackHome =
                (c.packageName == "com.android.settings" && c.className.contains("FallbackHome", ignoreCase = true))

            if (!isFallbackHome && c.packageName != packageName) return c
        }

        val list = pm.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),
            0
        )

        val candidates = list
            .mapNotNull { it.activityInfo?.let { a -> ComponentName(a.packageName, a.name) } }
            .filterNot { it.packageName == packageName }
            .filterNot { it.packageName == "com.android.settings" && it.className.contains("FallbackHome", true) }

        if (candidates.isEmpty()) return null

        val preferred = candidates.firstOrNull { c ->
            val n = c.className.lowercase()
            val p = c.packageName.lowercase()
            p.contains("launcher") || n.contains("launcher") || n.contains("quickstep")
        }

        return preferred ?: candidates.first()
    }

    private fun saveSystemHomeIfMissing() {
        if (prefs.contains(KEY_SYSTEM_HOME_COMPONENT)) {
            val existing = getSavedSystemHome()
            if (existing != null && existing.packageName != packageName) return
        }

        val systemHome = findBestSystemHomeComponent()
        if (systemHome != null && systemHome.packageName != packageName) {
            prefs.edit().putString(KEY_SYSTEM_HOME_COMPONENT, systemHome.flattenToString()).apply()
        }
    }

    private fun getSavedSystemHome(): ComponentName? {
        val flat = prefs.getString(KEY_SYSTEM_HOME_COMPONENT, null) ?: return null
        val c = try { ComponentName.unflattenFromString(flat) } catch (_: Exception) { null }
        if (c == null || c.packageName == packageName) return null
        return c
    }

    private fun launchRealHome(systemHome: ComponentName?) {
        if (systemHome == null) {
            val intent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                addCategory(Intent.CATEGORY_DEFAULT)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            startActivity(intent)
            return
        }

        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            addCategory(Intent.CATEGORY_DEFAULT)
            component = systemHome
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
    }

    private fun setPersistentHome(component: ComponentName) {
        if (!isDeviceOwner()) return
        val filter = IntentFilter(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            addCategory(Intent.CATEGORY_DEFAULT)
        }
        try { dpm().addPersistentPreferredActivity(adminComponent(), filter, component) } catch (_: Exception) {}
    }

    private fun applyLockTaskWhitelistFromPrefs() {
        if (!isDeviceOwner()) return
        val whitelist = prefs.getStringSet(KEY_WHITELIST, emptySet()) ?: emptySet()
        val pkgs = (whitelist + setOf(packageName)).toTypedArray()
        try { dpm().setLockTaskPackages(adminComponent(), pkgs) } catch (_: Exception) {}
    }

    // =======================
    // Kiosk HOME alias enable/disable
    // =======================
    private fun setKioskHomeEnabled(enabled: Boolean) {
        // Requer no Manifest:
        // <activity-alias android:name=".KioskHome" android:targetActivity=".MainActivity" ... />
        val alias = ComponentName(this, "$packageName.KioskHome")

        val newState = if (enabled)
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        else
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED

        try {
            packageManager.setComponentEnabledSetting(
                alias,
                newState,
                PackageManager.DONT_KILL_APP
            )
        } catch (_: Exception) {}
    }
}
