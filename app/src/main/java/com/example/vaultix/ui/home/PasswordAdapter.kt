package com.example.vaultix.ui.home

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.example.vaultix.data.UiPasswordEntry
import com.example.vaultix.databinding.ItemPasswordBinding

class PasswordAdapter(
    private val onDeleteClicked: (UiPasswordEntry) -> Unit
) : RecyclerView.Adapter<PasswordAdapter.PasswordViewHolder>() {
    private val items = mutableListOf<UiPasswordEntry>()

    fun submitList(newItems: List<UiPasswordEntry>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PasswordViewHolder {
        val binding = ItemPasswordBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return PasswordViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PasswordViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class PasswordViewHolder(
        private val binding: ItemPasswordBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: UiPasswordEntry) {
            binding.tvSite.text = item.site
            binding.tvPassword.text = if (item.isVisible) item.password else "••••••••••"
            binding.btnToggle.text = if (item.isVisible) {
                binding.root.context.getString(com.example.vaultix.R.string.hide)
            } else {
                binding.root.context.getString(com.example.vaultix.R.string.show)
            }

            binding.btnToggle.setOnClickListener {
                val updated = items[adapterPosition].copy(isVisible = !items[adapterPosition].isVisible)
                items[adapterPosition] = updated
                notifyItemChanged(adapterPosition)
            }

            binding.btnCopy.setOnClickListener {
                val current = items[adapterPosition]
                val clipboard = binding.root.context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("vaultix_password", current.password)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(
                    binding.root.context,
                    binding.root.context.getString(com.example.vaultix.R.string.password_copied),
                    Toast.LENGTH_SHORT
                ).show()
            }

            binding.btnDelete.setOnClickListener {
                onDeleteClicked(items[adapterPosition])
            }
        }
    }
}
