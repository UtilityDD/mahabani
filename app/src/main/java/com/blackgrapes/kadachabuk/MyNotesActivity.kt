package com.blackgrapes.kadachabuk

import android.content.Context
import android.widget.EditText
import androidx.core.app.ShareCompat
import android.os.Bundle
import android.content.res.Configuration
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.view.inputmethod.InputMethodManager
import android.widget.ImageButton
import android.util.TypedValue
import android.view.ViewGroup
import android.widget.Toast
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.card.MaterialCardView
import com.google.android.material.appbar.MaterialToolbar
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val NOTES_PREFS = "MyNotesPrefs"
private const val KEY_NOTES = "notes"

data class NoteItem(val text: String, val timestamp: Long, val originalJson: String)

class MyNotesActivity : AppCompatActivity()  {

    private lateinit var recyclerView: RecyclerView
    private lateinit var noNotesTextView: TextView
    private lateinit var noteAdapter: NoteAdapter
    private lateinit var notes: MutableList<NoteItem>
    private lateinit var toolbar: MaterialToolbar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_my_notes)

        // Allow content to draw behind the system bars
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // Use the centralized utility to set the status bar icon color.
        WindowUtils.setStatusBarIconColor(window)

        toolbar = findViewById(R.id.toolbar_notes)
        ViewCompat.setOnApplyWindowInsetsListener(toolbar) { view, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            // Apply the top inset as padding
            view.setPadding(view.paddingLeft, insets.top, view.paddingRight, view.paddingBottom)

            // Increase the toolbar's height to accommodate the new padding
            val typedValue = TypedValue()
            theme.resolveAttribute(android.R.attr.actionBarSize, typedValue, true)
            val actionBarSize = TypedValue.complexToDimensionPixelSize(typedValue.data, resources.displayMetrics)
            view.layoutParams.height = actionBarSize + insets.top

            // Consume the insets
            WindowInsetsCompat.CONSUMED
        }

        recyclerView = findViewById(R.id.recycler_view_notes)
        noNotesTextView = findViewById(R.id.text_view_no_notes)

        toolbar.setNavigationOnClickListener {
            finish()
        }

        toolbar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.action_delete_all_notes -> {
                    showDeleteAllConfirmationDialog()
                    true
                }
                else -> false
            }
        }

        notes = getSavedNotes().toMutableList()
        noteAdapter = NoteAdapter(
            notes,
            onDeleteClick = { noteItem, position ->
                showDeleteConfirmationDialog(noteItem, position)
            },
            onShareClick = { noteItem -> shareNote(noteItem.text) },
            onSaveClick = { noteItem, newText, position ->
                saveEditedNote(noteItem, newText, position)
            }
        )

        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = noteAdapter

        updateEmptyState()
        updateTitle()
    }

    private fun updateTitle() {
        toolbar.title = "My Notes (${noteAdapter.itemCount})"
    }

    private fun shareNote(noteText: String) {
        ShareCompat.IntentBuilder(this)
            .setType("text/plain")
            .setText(noteText)
            .startChooser()
    }

    private fun getSavedNotes(): List<NoteItem> {
        val prefs = getSharedPreferences(NOTES_PREFS, Context.MODE_PRIVATE)
        val notesJsonSet = prefs.getStringSet(KEY_NOTES, emptySet()) ?: emptySet()

        return notesJsonSet.mapNotNull { jsonString ->
            try {
                val jsonObject = JSONObject(jsonString)
                val text = jsonObject.getString("text")
                val timestamp = jsonObject.getLong("timestamp")
                NoteItem(text, timestamp, jsonString)
            } catch (e: Exception) {
                // Handle legacy plain string notes if they exist
                NoteItem(jsonString, 0, jsonString)
            }
        }.sortedByDescending { it.timestamp }
    }

    private fun showDeleteConfirmationDialog(noteItem: NoteItem, position: Int) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Delete Note")
            .setMessage("Are you sure you want to delete this note?")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Delete") { _, _ ->
                deleteNote(noteItem, position)
            }
            .show()
    }

    private fun deleteNote(noteItem: NoteItem, position: Int) {
        // Remove from SharedPreferences
        val prefs = getSharedPreferences(NOTES_PREFS, Context.MODE_PRIVATE)
        val existingNotes = prefs.getStringSet(KEY_NOTES, emptySet())?.toMutableSet() ?: return
        existingNotes.remove(noteItem.originalJson)
        prefs.edit().putStringSet(KEY_NOTES, existingNotes).apply()

        // Remove from the local list and notify the adapter
        notes.removeAt(position)
        noteAdapter.notifyItemRemoved(position)

        updateEmptyState()
        updateTitle()
    }

    private fun saveEditedNote(oldNoteItem: NoteItem, newText: String, position: Int) {
        // Create a new NoteItem with the updated text and a new timestamp
        val newNoteObject = JSONObject()
        newNoteObject.put("text", newText)
        newNoteObject.put("timestamp", System.currentTimeMillis())

        val newNoteItem = NoteItem(newText, newNoteObject.getLong("timestamp"), newNoteObject.toString())

        // Update SharedPreferences
        val prefs = getSharedPreferences(NOTES_PREFS, Context.MODE_PRIVATE)
        val existingNotes = prefs.getStringSet(KEY_NOTES, emptySet())?.toMutableSet() ?: mutableSetOf()

        // Remove the old note and add the new one
        existingNotes.remove(oldNoteItem.originalJson)
        existingNotes.add(newNoteItem.originalJson)
        prefs.edit().putStringSet(KEY_NOTES, existingNotes).apply()

        // Update the local list and notify the adapter
        notes[position] = newNoteItem
        // Sort again to maintain order by timestamp
        notes.sortByDescending { it.timestamp }
        noteAdapter.notifyDataSetChanged() // Use notifyDataSetChanged after sorting
    }

    private fun showDeleteAllConfirmationDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Delete All Notes?")
            .setMessage("This will permanently delete all of your saved notes. This action cannot be undone.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Delete All") { _, _ ->
                deleteAllNotes()
            }
            .show()
    }

    private fun deleteAllNotes() {
        // Clear from SharedPreferences
        val prefs = getSharedPreferences(NOTES_PREFS, Context.MODE_PRIVATE)
        prefs.edit().clear().apply()

        // Clear the local list and notify the adapter
        notes.clear()
        noteAdapter.notifyDataSetChanged()

        Toast.makeText(this, "All notes have been deleted.", Toast.LENGTH_SHORT).show()
        updateEmptyState()
        updateTitle()
    }

    private fun updateEmptyState() {
        if (notes.isEmpty()) {
            recyclerView.visibility = View.GONE
            noNotesTextView.visibility = View.VISIBLE
        } else {
            recyclerView.visibility = View.VISIBLE
            noNotesTextView.visibility = View.GONE
        }
    }
}

class NoteAdapter(
    private val notes: MutableList<NoteItem>,
    private val onDeleteClick: (NoteItem, Int) -> Unit,
    private val onShareClick: (NoteItem) -> Unit,
    private val onSaveClick: (NoteItem, String, Int) -> Unit
) : RecyclerView.Adapter<NoteAdapter.NoteViewHolder>() {

    class NoteViewHolder(val view: View) : RecyclerView.ViewHolder(view) {
        // The views are now directly part of the inflated layout.
        val cardView: MaterialCardView = view as MaterialCardView
        val noteContent: EditText = view.findViewById(R.id.text_view_note_content)
        val noteDate: TextView = view.findViewById(R.id.text_view_note_date)
        val shareButton: ImageButton = view.findViewById(R.id.button_share_note)
        val deleteButton: ImageButton = view.findViewById(R.id.button_delete_note)
        val saveButton: ImageButton = view.findViewById(R.id.button_save_note)
        val editButton: ImageButton = view.findViewById(R.id.button_edit_note)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NoteViewHolder {
        // Inflate the complete item_note.xml layout directly.
        // This is simpler and more reliable than building the layout programmatically.
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_note, parent, false)
        return NoteViewHolder(view)
    }

    override fun onBindViewHolder(holder: NoteViewHolder, position: Int) {
        val noteItem = notes[position]
        holder.noteContent.setText(noteItem.text)
        // Ensure the initial state is not editable
        holder.noteContent.isFocusable = false
        holder.noteContent.isFocusableInTouchMode = false
        holder.noteContent.isCursorVisible = false

        fun enterEditMode() {
            holder.noteContent.isFocusable = true
            holder.noteContent.isFocusableInTouchMode = true
            holder.noteContent.isCursorVisible = true
            holder.noteContent.requestFocus()
            holder.noteContent.setSelection(holder.noteContent.text.length) // Move cursor to the end

            // Show keyboard
            val imm = holder.view.context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(holder.noteContent, InputMethodManager.SHOW_IMPLICIT)

            // Show save button, hide others
            holder.saveButton.visibility = View.VISIBLE
            holder.editButton.visibility = View.GONE
            holder.shareButton.visibility = View.GONE
            holder.deleteButton.visibility = View.GONE

            // Change card color to indicate editing
            val typedValue = TypedValue()
            holder.view.context.theme.resolveAttribute(com.google.android.material.R.attr.colorSurfaceContainerHighest, typedValue, true)
            holder.cardView.setCardBackgroundColor(typedValue.data)
        }

        fun exitEditMode(saveChanges: Boolean) {
            if (saveChanges) {
                val newText = holder.noteContent.text.toString()
                onSaveClick(noteItem, newText, holder.adapterPosition)
            }

            // Hide keyboard
            val imm = holder.view.context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(holder.view.windowToken, 0)

            // Restore original state
            holder.noteContent.isFocusable = false
            holder.noteContent.isFocusableInTouchMode = false
            holder.noteContent.isCursorVisible = false
            holder.noteContent.clearFocus()

            holder.saveButton.visibility = View.GONE
            holder.editButton.visibility = View.VISIBLE
            holder.shareButton.visibility = View.VISIBLE
            holder.deleteButton.visibility = View.VISIBLE

            // Restore original card color
            val typedValue = TypedValue()
            holder.view.context.theme.resolveAttribute(com.google.android.material.R.attr.colorSurfaceContainer, typedValue, true)
            holder.cardView.setCardBackgroundColor(typedValue.data)
        }

        holder.editButton.setOnClickListener {
            enterEditMode()
        }

        holder.saveButton.setOnClickListener {
            exitEditMode(saveChanges = true)
        }

        if (noteItem.timestamp > 0) {
            val sdf = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()) // Example format
            holder.noteDate.text = sdf.format(Date(noteItem.timestamp))
            holder.noteDate.visibility = View.VISIBLE
        } else {
            holder.noteDate.visibility = View.GONE
        }
        holder.shareButton.setOnClickListener { onShareClick(noteItem) }
        holder.deleteButton.setOnClickListener { onDeleteClick(noteItem, holder.adapterPosition) }
    }

    override fun getItemCount() = notes.size
}