package com.el.silatpro.ui.riwayat

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.el.silatpro.databinding.ItemFeedbackBinding

class AdapterFeedback(private val daftarFeedback: List<String>) :
    RecyclerView.Adapter<AdapterFeedback.ViewHolder>() {

    inner class ViewHolder(private val binding: ItemFeedbackBinding) : RecyclerView.ViewHolder(binding.root) {
        fun ikat(teks: String) {
            binding.txtIsiFeedback.text = teks
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemFeedbackBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.ikat(daftarFeedback[position])
    }

    override fun getItemCount(): Int = daftarFeedback.size
}
