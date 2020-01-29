package com.draco.resolutionchanger

import android.Manifest
import android.content.DialogInterface
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Handler
import android.preference.PreferenceManager
import android.util.DisplayMetrics
import android.view.WindowManager
import android.view.animation.AnimationUtils
import android.widget.Button
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import kotlin.concurrent.fixedRateTimer
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt


class MainActivity : AppCompatActivity() {
    /* Command that user must run to grant permissions */
    private val adbCommand = "pm grant ${BuildConfig.APPLICATION_ID} android.permission.WRITE_SECURE_SETTINGS"

    /* Resolution presets */
    class Resolution(var x: Int, var y: Int) {
        private fun orientation(x: Int, y: Int): String {
            return if (x > y)
                "Landscape"
            else
                "Portrait"
        }
        fun displayName(): String {
            return "${x}x${y} - ${orientation(x, y)}"
        }
    }

    private val presets = listOf(
            Resolution(480, 640),
            Resolution(480, 960),
            Resolution(600, 800),
            Resolution(600, 1200),
            Resolution(720, 1280),
            Resolution(720, 1440),
            Resolution(768, 1366),
            Resolution(768, 1536),
            Resolution(900, 1600),
            Resolution(900, 1800),
            Resolution(1080, 1920),
            Resolution(1080, 2160),
            Resolution(1440, 2560),
            Resolution(1440, 2880),
            Resolution(2160, 3840),
            Resolution(2160, 4320),

            Resolution(640, 480),
            Resolution(960, 480),
            Resolution(800, 600),
            Resolution(1200, 600),
            Resolution(1280, 720),
            Resolution(1440, 720),
            Resolution(1366, 768),
            Resolution(1536, 768),
            Resolution(1600, 900),
            Resolution(1800, 900),
            Resolution(1920, 1080),
            Resolution(2160, 1080),
            Resolution(2560, 1440),
            Resolution(2880, 1440),
            Resolution(3840, 2160),
            Resolution(4320, 2160)
    )

    /* Preferences */
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var editor: SharedPreferences.Editor

    /* Internal classes */
    private lateinit var wmApi: WmApi

    /* UI elements */
    private lateinit var pickPreset: Button
    private lateinit var width: EditText
    private lateinit var height: EditText
    private lateinit var density: EditText
    private lateinit var reset: Button
    private lateinit var apply: Button

    /* Default screen size and density on start */
    object DefaultScreenSpecs {
        var width: Int = 0
        var height: Int = 0
        var density: Int = 0
        var diagInches: Double = 0.0
        var diagPixels: Double = 0.0

        /* Set DefaultScreenSpecs to current settings */
        fun setup(windowManager: WindowManager) {
            val dm = DisplayMetrics()
            windowManager.defaultDisplay.getMetrics(dm)

            width = dm.widthPixels
            height = dm.heightPixels
            density = dm.densityDpi

            val wi = dm.widthPixels.toDouble() / dm.xdpi.toDouble()
            val hi = dm.heightPixels.toDouble() / dm.ydpi.toDouble()

            diagInches = sqrt(wi.pow(2.0) + hi.pow(2.0))
            diagPixels = sqrt(dm.widthPixels.toDouble().pow(2) +
                    dm.heightPixels.toDouble().pow(2))
        }
    }

    /* Estimate proper DPI for device */
    private fun calculateDPI(x: Int, y: Int): Int {
        val diagPixels = sqrt(x.toDouble().pow(2) + y.toDouble().pow(2)).toInt()
        return (diagPixels / DefaultScreenSpecs.diagInches).roundToInt()
    }

    /* Apply resolution and density */
    private fun apply() {
        var w = 0
        var h = 0
        var d = 0

        w = try {
            Integer.parseInt(width.text.toString())
        } catch (_: Exception) {
            DefaultScreenSpecs.width
        }

        h = try {
            Integer.parseInt(height.text.toString())
        } catch (_: Exception) {
            DefaultScreenSpecs.height
        }

        d = try {
            Integer.parseInt(density.text.toString())
        } catch (_: Exception) {
            calculateDPI(w, h)
        }

        wmApi.setBypassBlacklist(true)
        wmApi.setDisplayResolution(w, h)
        wmApi.setDisplayDensity(d)

        /* Delay because when we change resolution, window changes */
        Handler().postDelayed({
            showWarningDialog()
            updateEditTexts()
        }, 500)
    }

    /* Show 5 second countdown */
    private fun showWarningDialog() {
        var dismissed = false
        val confirmMessage = "If these settings look correct, press Confirm to keep them.\n\n"

        val dialog = AlertDialog.Builder(this)
                .setTitle("Confirm Settings")
                .setMessage(confirmMessage)
                .setPositiveButton("Confirm") { _: DialogInterface, _: Int ->
                    dismissed = true
                }
                .setCancelable(false)
                .create()

        dialog.show()

        object : CountDownTimer(5000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                if (dismissed) {
                    this.cancel()
                } else {
                    val secondsLeft = ((millisUntilFinished / 1000) + 1)
                    dialog.setMessage(confirmMessage + "Resetting in " + secondsLeft + " seconds.")
                }
            }

            override fun onFinish() {
                if (!dismissed) {
                    dialog.dismiss()
                    reset()
                }
            }
        }.start()
    }

    /* Use new resolution for text */
    private fun updateEditTexts() {
        /* Read screen specs */
        DefaultScreenSpecs.setup(windowManager)

        width.setText(DefaultScreenSpecs.width.toString())
        height.setText(DefaultScreenSpecs.height.toString())
        density.setText(DefaultScreenSpecs.density.toString())
    }

    /* Reset resolution and density */
    private fun reset() {
        wmApi.setBypassBlacklist(true)
        wmApi.clearDisplayDensity()
        wmApi.clearDisplayResolution()

        /* Restart activity because windowManager breaks after reset */
        finish()
        overridePendingTransition(0, 0)
        startActivity(intent)
        overridePendingTransition(0, 0)
    }

    /* Ask user to pick from a list of resolution presets */
    private fun showPresetDialog() {
        val displayNamePresets = arrayOfNulls<String>(presets.size)

        /* Fill array with display names for each preset */
        for (i in presets.indices)
            displayNamePresets[i] = presets[i].displayName()

        AlertDialog.Builder(this)
                .setTitle("Presets")
                .setItems(displayNamePresets) { _: DialogInterface, which: Int ->
                    wmApi.setBypassBlacklist(true)
                    wmApi.setDisplayResolution(presets[which].x, presets[which].y)
                    wmApi.setDisplayDensity(calculateDPI(presets[which].x, presets[which].y))

                    /* Delay because when we change resolution, window changes */
                    Handler().postDelayed({
                        showWarningDialog()
                        updateEditTexts()
                    }, 500)
                }
                .show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        /* Configure classes for our activity */
        wmApi = WmApi(contentResolver)

        /* Setup preferences */
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(applicationContext)
        editor = sharedPreferences.edit()

        /* Setup UI elements */
        pickPreset = findViewById(R.id.pick_preset)
        width = findViewById(R.id.width)
        height = findViewById(R.id.height)
        density = findViewById(R.id.density)
        reset = findViewById(R.id.reset)
        apply = findViewById(R.id.apply)

        pickPreset.setOnClickListener {
            showPresetDialog()
        }

        reset.setOnClickListener {
            reset()

            reset.startAnimation(AnimationUtils.loadAnimation(this, R.anim.press))
        }

        apply.setOnClickListener {
            apply()

            apply.startAnimation(AnimationUtils.loadAnimation(this, R.anim.press))
        }

        /* Request or confirm if we can perform proper commands */
        checkPermissions()

        /* Show the current display config */
        updateEditTexts()
    }

    private fun hasPermissions(): Boolean {
        val permissionCheck = ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_SECURE_SETTINGS)
        return permissionCheck == PackageManager.PERMISSION_GRANTED
    }

    private fun checkPermissions() {
        if (hasPermissions())
            return

        val dialog = AlertDialog.Builder(this)
                .setTitle("Missing Permissions")
                .setMessage(getString(R.string.adb_tutorial) + "adb shell $adbCommand")
                .setPositiveButton("Check Again", null)
                .setNeutralButton("Setup ADB", null)
                .setCancelable(false)
                .create()

        dialog.setOnShowListener {
            /* We don't dismiss on Check Again unless we actually have the permission */
            val positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            positiveButton.setOnClickListener {
                if (hasPermissions())
                    dialog.dismiss()
            }

            /* Open tutorial but do not dismiss until user presses Check Again */
            val neutralButton = dialog.getButton(AlertDialog.BUTTON_NEUTRAL)
            neutralButton.setOnClickListener {
                val uri = Uri.parse("https://www.xda-developers.com/install-adb-windows-macos-linux/")
                val intent = Intent(Intent.ACTION_VIEW, uri)
                startActivity(intent)
            }
        }

        dialog.show()

        /* Check every second if the permission was granted */
        fixedRateTimer("permissionCheck", false, 0, 1000) {
            if (hasPermissions()) {
                dialog.dismiss()
                this.cancel()
            }
        }
    }
}
