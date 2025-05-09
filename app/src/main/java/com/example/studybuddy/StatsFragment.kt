package com.example.studybuddy

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.github.mikephil.charting.charts.BarChart
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import java.util.Calendar
import android.util.Log

class StatsFragment : Fragment() {

    private lateinit var tvTotalMinutes: TextView
    private lateinit var tvTotalHours: TextView
    private lateinit var barChart: BarChart

    private lateinit var prefsManager: PrefsManager
    private lateinit var db: FirebaseFirestore

    // Array for day names
    private val dayNames = arrayOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
    private val TAG = "StatsFragment"

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        val rootView = inflater.inflate(R.layout.activity_stats_fragment, container, false)

        // Initialize views using rootView
        tvTotalMinutes = rootView.findViewById(R.id.tvTotalMinutes)
        tvTotalHours = rootView.findViewById(R.id.tvTotalHours)
        barChart = rootView.findViewById(R.id.barChart)

        // Initialize PrefsManager
        prefsManager = PrefsManager(requireContext())

        try {
            // Initialize Firestore with explicit app instance
            db = Firebase.firestore
            // Enable Firestore logging for debugging
            FirebaseFirestore.setLoggingEnabled(true)
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing Firestore: ${e.message}")
            // Handle the initialization error
        }

        // Display total minutes from SharedPreferences
        updateTotalTimeDisplayFromPrefs()

        return rootView
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Load data and setup chart after the view is created
        loadFocusDataAndSetupChart()

        // Uncomment this to test Firestore write functionality
        // testFirestoreWrite()
    }

    // Test function to verify Firestore writes are working
    private fun testFirestoreWrite() {
        try {
            Log.d(TAG, "Attempting test write to Firestore")
            db.collection("test_collection")
                .document("test_document")
                .set(hashMapOf("test" to true, "timestamp" to com.google.firebase.Timestamp.now()))
                .addOnSuccessListener {
                    Log.d(TAG, "Test write to Firestore succeeded!")
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Test write to Firestore failed: ${e.message}")
                }
        } catch (e: Exception) {
            Log.e(TAG, "Exception during test write: ${e.message}")
        }
    }

    private fun updateTotalTimeDisplayFromPrefs() {
        val totalMinutes = prefsManager.getTotalFocusMinutes()

        // Display total focus minutes
        tvTotalMinutes.text = getString(R.string.total_focus_time, totalMinutes)

        // Calculate time in hours and minutes
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        tvTotalHours.text = getString(R.string.total_hours_minutes_format, hours, minutes)
    }

    fun loadFocusDataAndSetupChart() {
        // Check if fragment is still attached and Firestore is initialized
        if (!isAdded || !::db.isInitialized) {
            Log.d(TAG, "Fragment not attached or Firestore not initialized")
            return
        }

        try {
            // Reference to the focus time collection
            val focusTimeRef = db.collection("focus_time").document("app_stats").collection("daily_stats")
            Log.d(TAG, "Attempting to load focus data from Firestore")

            focusTimeRef.get()
                .addOnSuccessListener { documents ->
                    // Check if there are any documents
                    Log.d(TAG, "Firestore query returned ${documents.size()} documents")

                    // Check if fragment is still attached
                    if (!isAdded) {
                        Log.d(TAG, "Fragment is no longer attached, skipping update")
                        return@addOnSuccessListener
                    }

                    // Default values for each day (0 minutes if no data exists)
                    val weeklyData = IntArray(7) { 0 }
                    var totalFirestoreMinutes = 0

                    for (document in documents) {
                        try {
                            Log.d(TAG, "Processing document: ${document.id}, data: ${document.data}")
                            // Get dayOfWeek (1-7, where 1 is Sunday)
                            val dayOfWeek = document.getLong("dayOfWeek")?.toInt()
                            if (dayOfWeek == null) {
                                Log.e(TAG, "Document missing dayOfWeek: ${document.id}")
                                continue
                            }

                            // Calculate array index (0-6)
                            val index = dayOfWeek - 1

                            if (index in 0..6) {
                                val minutes = document.getLong("minutes")?.toInt() ?: 0
                                weeklyData[index] = minutes
                                totalFirestoreMinutes += minutes
                                Log.d(TAG, "Day $dayOfWeek (index $index): $minutes minutes")
                            } else {
                                Log.e(TAG, "Invalid day index: $index from dayOfWeek: $dayOfWeek")
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error processing document: ${e.message}")
                        }
                    }

                    // Update total minutes in SharedPreferences if Firestore has more data
                    if (totalFirestoreMinutes > prefsManager.getTotalFocusMinutes()) {
                        Log.d(TAG, "Updating total minutes from Firestore: $totalFirestoreMinutes")
                        prefsManager.saveTotalFocusMinutes(totalFirestoreMinutes)
                        updateTotalTimeDisplayFromPrefs()
                    }

                    // Setup chart with the loaded data
                    setupChart(weeklyData)
                    Log.d(TAG, "Chart data updated: ${weeklyData.contentToString()}")
                }
                .addOnFailureListener { exception ->
                    // Handle error loading data
                    Log.e(TAG, "Error loading focus data: ${exception.message}")
                    if (isAdded) {
                        setupChart(IntArray(7) { 0 }) // Setup with empty data on failure
                    }
                }
        } catch (e: Exception) {
            Log.e(TAG, "Error in loadFocusDataAndSetupChart: ${e.message}")
            e.printStackTrace()
        }
    }

    private fun setupChart(weeklyData: IntArray) {
        // Ensure fragment is attached to context
        if (!isAdded) {
            Log.d(TAG, "Fragment is not attached, skipping chart setup")
            return
        }

        try {
            val entries = ArrayList<BarEntry>()

            val calendar = Calendar.getInstance()
            val currentDay = calendar.get(Calendar.DAY_OF_WEEK) - 1 // Adjust to 0-based index (0=Sunday)
            Log.d(TAG, "Current day of week: $currentDay")

            // Populate bar chart entries with weekly data
            for (i in weeklyData.indices) {
                entries.add(BarEntry(i.toFloat(), weeklyData[i].toFloat()))
            }

            // Create dataset and set properties
            val dataSet = BarDataSet(entries, getString(R.string.daily_focus_minutes))

            // Set up colors for each bar (highlight current day)
            val colors = ArrayList<Int>()
            for (i in 0 until 7) {
                if (i == currentDay) {
                    colors.add(resources.getColor(R.color.colorAccent, null))
                } else {
                    colors.add(resources.getColor(R.color.colorPrimary, null))
                }
            }

            dataSet.colors = colors

            val data = BarData(dataSet)
            data.barWidth = 0.7f

            // Configure chart
            barChart.data = data
            barChart.description.isEnabled = false
            barChart.legend.isEnabled = false

            val xAxis = barChart.xAxis
            xAxis.position = XAxis.XAxisPosition.BOTTOM
            xAxis.granularity = 1f
            xAxis.setDrawGridLines(false)
            xAxis.valueFormatter = IndexAxisValueFormatter(dayNames)

            barChart.axisLeft.axisMinimum = 0f
            barChart.axisRight.isEnabled = false

            // Animate chart
            barChart.animateY(1000)

            // Refresh the chart
            barChart.invalidate()

            Log.d(TAG, "Chart setup complete")
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up chart: ${e.message}")
            e.printStackTrace()
        }
    }

    // Call this method when the user completes a focus session
    fun updateTodaysFocusTime(additionalMinutes: Int) {
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

            // Update total minutes in SharedPreferences first
            val currentTotalMinutes = prefsManager.getTotalFocusMinutes()
            val newTotalMinutes = currentTotalMinutes + additionalMinutes
            prefsManager.saveTotalFocusMinutes(newTotalMinutes)
            Log.d(TAG, "Updated SharedPreferences from $currentTotalMinutes to $newTotalMinutes minutes")

            // Update total time display if fragment is attached
            if (isAdded) {
                updateTotalTimeDisplayFromPrefs()
            }

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
            // This approach ensures the collections exist before trying to set data
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
                                    // Refresh the chart after update if fragment is still attached
                                    if (isAdded) {
                                        loadFocusDataAndSetupChart()
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
                                    if (isAdded) {
                                        loadFocusDataAndSetupChart()
                                    }
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
                            if (isAdded) {
                                loadFocusDataAndSetupChart()
                            }
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
}