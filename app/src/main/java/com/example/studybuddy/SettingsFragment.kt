package com.example.studybuddy

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Button
import androidx.fragment.app.Fragment

class SettingsFragment : Fragment() {

    private lateinit var prefsManager: PrefsManager

    private lateinit var tvFocusDuration: TextView
    private lateinit var tvBreakDuration: TextView
    private lateinit var seekBarFocus: SeekBar
    private lateinit var seekBarBreak: SeekBar
    private lateinit var btnResetStats: Button
    private lateinit var tvResetConfirmation: TextView

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.activity_settings_fragment, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialize views after view is created
        tvFocusDuration = view.findViewById(R.id.tvFocusDuration)
        tvBreakDuration = view.findViewById(R.id.tvBreakDuration)
        seekBarFocus = view.findViewById(R.id.seekBarFocus)
        seekBarBreak = view.findViewById(R.id.seekBarBreak)
        btnResetStats = view.findViewById(R.id.btnResetStats)
        tvResetConfirmation = view.findViewById(R.id.tvResetConfirmation)

        prefsManager = PrefsManager(requireContext())

        // Load and set current durations
        seekBarFocus.progress = prefsManager.getFocusDuration() - 1
        seekBarBreak.progress = prefsManager.getBreakDuration() - 1

        updateTextViews()

        // Set SeekBar listeners
        seekBarFocus.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val duration = progress + 1
                tvFocusDuration.text = getString(R.string.focus_duration_value, duration)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                val duration = seekBar?.progress?.plus(1) ?: 25
                prefsManager.saveFocusDuration(duration)
            }
        })

        seekBarBreak.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val duration = progress + 1
                tvBreakDuration.text = getString(R.string.break_duration_value, duration)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                val duration = seekBar?.progress?.plus(1) ?: 5
                prefsManager.saveBreakDuration(duration)
            }
        })

        // Reset button
        btnResetStats.setOnClickListener {
            prefsManager.saveTotalFocusMinutes(0)
            tvResetConfirmation.visibility = View.VISIBLE
        }
    }

    private fun updateTextViews() {
        tvFocusDuration.text = getString(R.string.focus_duration_value, prefsManager.getFocusDuration())
        tvBreakDuration.text = getString(R.string.break_duration_value, prefsManager.getBreakDuration())
    }
}
