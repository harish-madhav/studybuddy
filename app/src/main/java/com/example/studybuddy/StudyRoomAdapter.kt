package com.example.studybuddy

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

data class StudyRoom(val name: String, val description: String)

class StudyRoomAdapter(private val rooms: List<StudyRoom>) :
    RecyclerView.Adapter<StudyRoomAdapter.StudyRoomViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StudyRoomViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_study_room, parent, false)
        return StudyRoomViewHolder(view)
    }

    override fun onBindViewHolder(holder: StudyRoomViewHolder, position: Int) {
        val room = rooms[position]
        holder.roomNameText.text = room.name
        holder.roomDescriptionText.text = room.description
    }

    override fun getItemCount(): Int = rooms.size

    class StudyRoomViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val roomNameText: TextView = itemView.findViewById(R.id.room_name)
        val roomDescriptionText: TextView = itemView.findViewById(R.id.room_description)
    }
}
