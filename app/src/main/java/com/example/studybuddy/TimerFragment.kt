package com.example.studybuddy

import android.Manifest
import android.app.AlertDialog
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import java.util.Calendar
import java.util.concurrent.TimeUnit

class TimerFragment : Fragment() {

    private var timer: CountDownTimer? = null
    private var isTimerRunning = false
    private var timeLeftInMillis: Long = 0
    private var currentCycle = 1
    private var totalFocusMinutes = 0

    private lateinit var prefsManager: PrefsManager
    private lateinit var db: FirebaseFirestore

    private val TAG = "TimerFragment"

    private lateinit var tvTimer: TextView
    private lateinit var btnStartPause: Button
    private lateinit var btnReset: Button
    private lateinit var btnSkip: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var tvCycle: TextView
    private lateinit var tvTotalMinutes: TextView
    private lateinit var btnTestNotification: Button

    private val NOTIFICATION_PERMISSION_CODE = 123

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.activity_timer_fragment, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialize Firestore
        try {
            db = Firebase.firestore
            Log.d(TAG, "Firestore initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing Firestore: ${e.message}")
        }

        // Initialize views
        tvTimer = view.findViewById(R.id.tv_timer)
        btnStartPause = view.findViewById(R.id.btn_start_pause)
        btnReset = view.findViewById(R.id.btn_reset)
        btnSkip = view.findViewById(R.id.btn_skip)
        progressBar = view.findViewById(R.id.progress_bar)
        tvCycle = view.findViewById(R.id.tv_cycle)
        tvTotalMinutes = view.findViewById(R.id.tv_total_minutes)

        // Add test notification button - you need to add this to your layout
        // btnTestNotification = view.findViewById(R.id.btn_test_notification)
        // btnTestNotification.setOnClickListener { showSessionCompleteNotification() }

        prefsManager = PrefsManager(requireContext())

        // Check and request notification permission for Android 13+
        checkNotificationPermission()

        loadSettings()
        updateTimerUI()
        updateCycleText()

        val storedMinutes = prefsManager.getTotalFocusMinutes()
        totalFocusMinutes = storedMinutes
        tvTotalMinutes.text = getString(R.string.total_minutes_format, totalFocusMinutes)

        btnStartPause.setOnClickListener {
            if (isTimerRunning) pauseTimer() else startTimer()
        }

        btnReset.setOnClickListener {
            showResetConfirmationDialog()
        }

        btnSkip.setOnClickListener {
            showSkipConfirmationDialog()
        }
    }

    private fun checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    requireActivity(),
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    NOTIFICATION_PERMISSION_CODE
                )
            }
        }
    }

    private fun loadSettings() {
        val focusDuration = prefsManager.getFocusDuration()
        timeLeftInMillis = TimeUnit.MINUTES.toMillis(focusDuration.toLong())
    }

    private fun startTimer() {
        timer = object : CountDownTimer(timeLeftInMillis, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                timeLeftInMillis = millisUntilFinished
                updateTimerUI()
            }

            override fun onFinish() {
                isTimerRunning = false
                btnStartPause.text = getString(R.string.start)
                btnStartPause.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_play, 0, 0, 0)

                if (currentCycle % 2 != 0) {
                    // Focus session completed
                    val focusDuration = prefsManager.getFocusDuration()
                    totalFocusMinutes += focusDuration
                    prefsManager.saveTotalFocusMinutes(totalFocusMinutes)
                    tvTotalMinutes.text = getString(R.string.total_minutes_format, totalFocusMinutes)

                    // Update Firestore directly
                    updateTodaysFocusTime(focusDuration)
                }

                currentCycle++
                updateCycleText()
                showSessionCompleteNotification()

                timeLeftInMillis = if (currentCycle % 2 == 0) {
                    TimeUnit.MINUTES.toMillis(prefsManager.getBreakDuration().toLong())
                } else {
                    TimeUnit.MINUTES.toMillis(prefsManager.getFocusDuration().toLong())
                }

                updateTimerUI()
            }
        }.start()

        isTimerRunning = true
        btnStartPause.text = getString(R.string.pause)
        btnStartPause.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_pause, 0, 0, 0)
    }

    // Directly update Firestore with today's focus time
    private fun updateTodaysFocusTime(additionalMinutes: Int) {
        try {
            // Ensure Firestore is initialized
            if (!::db.isInitialized) {
                Log.e(TAG, "Firestore not initialized, initializing now")
                try {
                    db = Firebase.firestore
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to initialize Firestore: ${e.message}")
                    return
                }
            }

            Log.d(TAG, "Updating today's focus time with $additionalMinutes additional minutes")

            val calendar = Calendar.getInstance()
            val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK) // 1 (Sunday) to 7 (Saturday)

            // Create document ID using day number
            val documentId = "day_$dayOfWeek"

            // Data to save
            val data = hashMapOf(
                "dayOfWeek" to dayOfWeek,
                "minutes" to additionalMinutes, // Start with just the new minutes
                "lastUpdated" to com.google.firebase.Timestamp.now()
            )

            Log.d(TAG, "Attempting to update Firestore with data: $data")

            // Create the necessary collections and document
            db.collection("focus_time")
                .document("app_stats")
                .set(hashMapOf("created" to true))
                .addOnSuccessListener {
                    Log.d(TAG, "Successfully created/updated app_stats document")

                    // Now create/update the daily stats document
                    db.collection("focus_time")
                        .document("app_stats")
                        .collection("daily_stats")
                        .document(documentId)
                        .get()
                        .addOnSuccessListener { document ->
                            // If document exists, add to existing minutes
                            if (document.exists()) {
                                Log.d(TAG, "Document exists: ${document.data}")
                                val currentMinutes = document.getLong("minutes")?.toInt() ?: 0
                                data["minutes"] = currentMinutes + additionalMinutes
                                Log.d(TAG, "Adding to existing minutes: $currentMinutes + $additionalMinutes = ${currentMinutes + additionalMinutes}")
                            } else {
                                Log.d(TAG, "Document does not exist, creating new one")
                            }

                            // Set the document with data
                            db.collection("focus_time")
                                .document("app_stats")
                                .collection("daily_stats")
                                .document(documentId)
                                .set(data)
                                .addOnSuccessListener {
                                    Log.d(TAG, "Successfully created/updated focus time for day $dayOfWeek")

                                    // Notify StatsFragment to refresh if it's visible
                                    try {
                                        val activity = requireActivity() as? MainActivity
                                        val statsFragment = activity?.supportFragmentManager?.findFragmentByTag("StatsFragment") as? StatsFragment
                                        statsFragment?.loadFocusDataAndSetupChart()
                                    } catch (e: Exception) {
                                        Log.e(TAG, "Error notifying StatsFragment: ${e.message}")
                                    }
                                }
                                .addOnFailureListener { e ->
                                    Log.e(TAG, "Error setting focus time document: ${e.message}")
                                    e.printStackTrace()
                                }
                        }
                        .addOnFailureListener { e ->
                            Log.e(TAG, "Error getting current focus time: ${e.message}")
                            e.printStackTrace()

                            // Still try to create the document even if get fails
                            db.collection("focus_time")
                                .document("app_stats")
                                .collection("daily_stats")
                                .document(documentId)
                                .set(data)
                                .addOnSuccessListener {
                                    Log.d(TAG, "Created new focus time document for day $dayOfWeek")
                                }
                                .addOnFailureListener { e2 ->
                                    Log.e(TAG, "Error creating new focus time document: ${e2.message}")
                                    e2.printStackTrace()
                                }
                        }
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Error creating app_stats document: ${e.message}")
                    e.printStackTrace()

                    // Try a direct write to the daily stats document if the parent document creation fails
                    db.collection("focus_time")
                        .document("app_stats")
                        .collection("daily_stats")
                        .document(documentId)
                        .set(data)
                        .addOnSuccessListener {
                            Log.d(TAG, "Direct write to daily stats succeeded for day $dayOfWeek")
                        }
                        .addOnFailureListener { e2 ->
                            Log.e(TAG, "Direct write to daily stats failed: ${e2.message}")
                            e2.printStackTrace()
                        }
                }
        } catch (e: Exception) {
            Log.e(TAG, "Error in updateTodaysFocusTime: ${e.message}")
            e.printStackTrace() // Print the full stack trace for debugging
        }
    }

    private fun pauseTimer() {
        timer?.cancel()
        isTimerRunning = false
        btnStartPause.text = getString(R.string.resume)
        btnStartPause.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_play, 0, 0, 0)
    }

    private fun showResetConfirmationDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.reset_timer)
            .setMessage(R.string.reset_timer_confirm)
            .setPositiveButton(R.string.yes) { _, _ ->
                resetTimer()
            }
            .setNegativeButton(R.string.no, null)
            .show()
    }

    private fun resetTimer() {
        timer?.cancel()
        isTimerRunning = false
        currentCycle = 1
        updateCycleText()

        timeLeftInMillis = TimeUnit.MINUTES.toMillis(prefsManager.getFocusDuration().toLong())
        updateTimerUI()

        btnStartPause.text = getString(R.string.start)
        btnStartPause.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_play, 0, 0, 0)
    }

    private fun showSkipConfirmationDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.skip_session)
            .setMessage(R.string.skip_session_confirm)
            .setPositiveButton(R.string.yes) { _, _ ->
                skipCurrentSession()
            }
            .setNegativeButton(R.string.no, null)
            .show()
    }

    private fun skipCurrentSession() {
        timer?.cancel()
        currentCycle++
        updateCycleText()

        timeLeftInMillis = if (currentCycle % 2 == 0) {
            TimeUnit.MINUTES.toMillis(prefsManager.getBreakDuration().toLong())
        } else {
            TimeUnit.MINUTES.toMillis(prefsManager.getFocusDuration().toLong())
        }

        updateTimerUI()
        isTimerRunning = false
        btnStartPause.text = getString(R.string.start)
        btnStartPause.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_play, 0, 0, 0)
    }

    private fun updateTimerUI() {
        val minutes = TimeUnit.MILLISECONDS.toMinutes(timeLeftInMillis)
        val seconds = TimeUnit.MILLISECONDS.toSeconds(timeLeftInMillis) % 60

        tvTimer.text = String.format("%02d:%02d", minutes, seconds)

        val maxTime = if (currentCycle % 2 == 0) {
            TimeUnit.MINUTES.toMillis(prefsManager.getBreakDuration().toLong())
        } else {
            TimeUnit.MINUTES.toMillis(prefsManager.getFocusDuration().toLong())
        }

        val progress = ((maxTime - timeLeftInMillis) / maxTime.toFloat() * 100).toInt()
        progressBar.progress = progress
    }

    private fun updateCycleText() {
        tvCycle.text = if (currentCycle % 2 == 0) {
            getString(R.string.break_time)
        } else {
            getString(R.string.focus_time)
        }
    }

    private fun showSessionCompleteNotification() {
        try {
            val notificationManager = requireContext().getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            // Create an explicit intent for the MainActivity
            val intent = Intent(requireContext(), MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }

            // Create a pending intent with proper flags
            val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }

            val pendingIntent = PendingIntent.getActivity(
                requireContext(),
                0,
                intent,
                pendingIntentFlags
            )

            // Determine the notification message based on the current cycle
            val message = if (currentCycle % 2 == 0) {
                getString(R.string.break_time_notification)
            } else {
                getString(R.string.focus_time_notification)
            }

            // Build the notification
            val notification = NotificationCompat.Builder(requireContext(), MainActivity.CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_timer)
                .setContentTitle(getString(R.string.app_name))
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setVibrate(longArrayOf(0, 1000, 500, 1000))
                .build()

            // Show the notification
            notificationManager.notify(1, notification)

            // Log for debugging (you can remove this in production)
            println("Notification sent: $message")
        } catch (e: Exception) {
            // Log any exceptions that occur when showing the notification
            e.printStackTrace()
            println("Error showing notification: ${e.message}")
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        timer?.cancel()
    }
}