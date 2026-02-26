package com.botsandro.kiosk

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
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

private const val PREFS_NAME = "kiosk_prefs"
private const val KEY_ADMIN_PIN_HASH = "admin_pin_hash"
private const val KEY_USER_PIN_HASH = "user_pin_hash"
private const val KEY_WHITELIST = "whitelist"
private const val KEY_BG_PERMISSION_PROMPTED = "bg_permission_prompted"

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
    private var pendingUserAuthAfterWhitelistedApp = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        drawerLayout = findViewById(R.id.drawerLayout)
        appsRecyclerView = findViewById(R.id.appsRecyclerView)
        whitelistContainer = findViewById(R.id.whitelistContainer)
        emptyStateText = findViewById(R.id.emptyStateText)

        val toolbar: MaterialToolbar = findViewById(R.id.toolbar)
        toolbar.setNavigationOnClickListener { tryOpenSettings() }

        findViewById<Button>(R.id.setAdminPinButton).setOnClickListener { setAdminPin() }
        findViewById<Button>(R.id.changeAdminPinButton).setOnClickListener { changeAdminPin() }
        findViewById<Button>(R.id.deleteAdminPinButton).setOnClickListener { deleteAdminPin() }
        findViewById<Button>(R.id.setUserPinButton).setOnClickListener { setUserPin() }
        findViewById<Button>(R.id.changeUserPinButton).setOnClickListener { changeUserPin() }
        findViewById<Button>(R.id.deleteUserPinButton).setOnClickListener { deleteUserPin() }
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

        ensureBackgroundPermissionPrompted()
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
        promptUserPinAfterWhitelistedAppIfNeeded()
        hideSystemUi()
    }

    private fun tryOpenSettings() {
        val adminPinHash = prefs.getString(KEY_ADMIN_PIN_HASH, null)
        if (adminPinHash.isNullOrBlank()) {
            toast("Configure um PIN Admin para abrir configurações")
            return
        }

        askPin("Digite o PIN Admin para abrir configurações") { pin ->
            if (hash(pin) == adminPinHash) {
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

    private fun setUserPin() {
        askPin("Digite o novo PIN Usuário") { pin ->
            if (pin.length < 4) {
                toast("PIN deve ter ao menos 4 dígitos")
                return@askPin
            }
            prefs.edit().putString(KEY_USER_PIN_HASH, hash(pin)).apply()
            toast("PIN Usuário salvo")
        }
    }

    private fun changeUserPin() {
        val currentHash = prefs.getString(KEY_USER_PIN_HASH, null)
        if (currentHash.isNullOrBlank()) {
            toast("Nenhum PIN Usuário cadastrado")
            return
        }

        askPin("Digite o PIN Usuário atual") currentPin@{ current ->
            if (hash(current) != currentHash) {
                toast("PIN Usuário atual inválido")
                return@currentPin
            }

            askPin("Digite o novo PIN Usuário") newPin@{ newPin ->
                if (newPin.length < 4) {
                    toast("PIN deve ter ao menos 4 dígitos")
                    return@newPin
                }
                prefs.edit().putString(KEY_USER_PIN_HASH, hash(newPin)).apply()
                toast("PIN Usuário alterado")
            }
        }
    }

    private fun deleteUserPin() {
        val currentHash = prefs.getString(KEY_USER_PIN_HASH, null)
        if (currentHash.isNullOrBlank()) {
            toast("Nenhum PIN Usuário cadastrado")
            return
        }

        askPin("Digite o PIN Usuário para excluir") { current ->
            if (hash(current) == currentHash) {
                prefs.edit().remove(KEY_USER_PIN_HASH).apply()
                toast("PIN Usuário removido")
            } else {
                toast("PIN Usuário inválido")
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
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun refreshWhitelistUi() {
        val whitelist = prefs.getStringSet(KEY_WHITELIST, emptySet()) ?: emptySet()
        val apps = queryLaunchableApps().filter { whitelist.contains(it.packageName) }

        emptyStateText.visibility = if (apps.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
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
        pendingUserAuthAfterWhitelistedApp = true
        launchApp(packageName)
    }

    private fun launchApp(packageName: String) {
        val launchIntent = packageManagerRef.getLaunchIntentForPackage(packageName)
        if (launchIntent == null) {
            toast("Não foi possível abrir o app")
            return
        }

        relockOnResume = true
        stopLockTaskIfActive()
        startActivity(launchIntent)
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
        val adminPinHash = prefs.getString(KEY_ADMIN_PIN_HASH, null)
        if (adminPinHash.isNullOrBlank()) {
            toast("Defina um PIN Admin antes de sair do modo kiosk")
            return
        }

        askPin("Digite o PIN Admin para sair do modo kiosk") { pin ->
            if (hash(pin) == adminPinHash) {
                relockOnResume = false
                pendingUserAuthAfterWhitelistedApp = false
                stopLockTaskIfActive()
                toast("Modo kiosk desativado")
            } else {
                toast("PIN Admin inválido")
            }
        }
    }

    private fun promptUserPinAfterWhitelistedAppIfNeeded() {
        if (!pendingUserAuthAfterWhitelistedApp) return

        val userPinHash = prefs.getString(KEY_USER_PIN_HASH, null)
        if (userPinHash.isNullOrBlank()) {
            pendingUserAuthAfterWhitelistedApp = false
            toast("Configure um PIN Usuário para controlar saída de apps da lista branca")
            return
        }

        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            hint = "PIN Usuário"
        }

        AlertDialog.Builder(this)
            .setTitle("Digite o PIN Usuário para voltar ao kiosk")
            .setCancelable(false)
            .setView(input)
            .setPositiveButton("Confirmar") { _, _ ->
                if (hash(input.text.toString()) == userPinHash) {
                    pendingUserAuthAfterWhitelistedApp = false
                    toast("Retorno ao kiosk liberado")
                } else {
                    toast("PIN Usuário inválido")
                    promptUserPinAfterWhitelistedAppIfNeeded()
                }
            }
            .show()
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
                try {
                    stopLockTask()
                } catch (_: IllegalStateException) {
                    // ignore
                }
            }
        }
    }

    private fun activateLockTaskIfAllowed() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            if (activityManager.lockTaskModeState == ActivityManager.LOCK_TASK_MODE_NONE) {
                try {
                    startLockTask()
                } catch (_: IllegalArgumentException) {
                    // Device owner mode not configured yet.
                }
            }
        }
    }

    private fun hideSystemUi() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }
}
