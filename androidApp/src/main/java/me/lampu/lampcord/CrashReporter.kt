package me.lampu.lampcord

import android.app.Activity
import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.view.ViewGroup
import android.widget.Button
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.io.File
import kotlin.system.exitProcess

/**
 * Debug builds only: any uncaught crash opens [CrashActivity] with the full stack trace and a
 * Copy button, so a tester can send the trace without adb/logcat. The activity runs in its own
 * ":crash" process, so a crash during startup can't take the report screen down with it.
 */
object CrashReporter {
    const val CRASH_PROCESS_SUFFIX = ":crash"
    private const val EXTRA_TRACE = "trace"

    fun isCrashProcess(): Boolean = currentProcessName().endsWith(CRASH_PROCESS_SUFFIX)

    fun install(app: Application) {
        if (app.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE == 0) return
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try {
                val trace = buildReport(thread, error)
                runCatching { File(app.filesDir, "last_crash.txt").writeText(trace) }
                app.startActivity(
                    Intent(app, CrashActivity::class.java)
                        .putExtra(EXTRA_TRACE, trace)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                )
                Process.killProcess(Process.myPid())
                exitProcess(10)
            } catch (_: Throwable) {
                previous?.uncaughtException(thread, error)
            }
        }
    }

    private fun buildReport(thread: Thread, error: Throwable): String = buildString {
        appendLine("Lampcord crash on thread '${thread.name}'")
        appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        appendLine()
        append(error.stackTraceToString())
    }

    private fun currentProcessName(): String =
        if (Build.VERSION.SDK_INT >= 28) Application.getProcessName()
        else runCatching { File("/proc/self/cmdline").readText().trim('\u0000', ' ') }.getOrDefault("")

    class CrashActivity : Activity() {
        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            val trace = intent.getStringExtra(EXTRA_TRACE)
                ?: runCatching { File(filesDir, "last_crash.txt").readText() }.getOrDefault("No crash report found.")

            val pad = (16 * resources.displayMetrics.density).toInt()
            val root = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(pad, pad * 2, pad, pad)
            }
            root.addView(TextView(this).apply {
                text = "Lampcord crashed"
                textSize = 22f
                setTypeface(typeface, Typeface.BOLD)
            })
            val buttons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            buttons.addView(Button(this).apply {
                text = "Copy"
                setOnClickListener {
                    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("Lampcord crash", trace))
                    Toast.makeText(this@CrashActivity, "Crash log copied", Toast.LENGTH_SHORT).show()
                }
            })
            buttons.addView(Button(this).apply {
                text = "Share"
                setOnClickListener {
                    startActivity(Intent.createChooser(
                        Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, trace),
                        "Share crash log"
                    ))
                }
            })
            buttons.addView(Button(this).apply {
                text = "Restart"
                setOnClickListener {
                    packageManager.getLaunchIntentForPackage(packageName)?.let {
                        startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
                    }
                    finish()
                    Process.killProcess(Process.myPid())
                }
            })
            root.addView(buttons)
            root.addView(ScrollView(this).apply {
                addView(HorizontalScrollView(context).apply {
                    addView(TextView(context).apply {
                        text = trace
                        textSize = 11f
                        typeface = Typeface.MONOSPACE
                        setTextIsSelectable(true)
                    })
                })
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
            setContentView(root)
        }
    }
}
