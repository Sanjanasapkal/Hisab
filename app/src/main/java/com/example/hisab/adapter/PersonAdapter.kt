package com.example.hisab.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.hisab.R
import com.example.hisab.databinding.ItemPersonBinding
import com.example.hisab.model.Person
import com.example.hisab.util.CurrencyFormatter

/**
 * Adapter for rendering people and their current balances on the home dashboard.
 */
class PersonAdapter(
    private var people: List<Person>,
    private val onPersonClicked: (Person) -> Unit
) : RecyclerView.Adapter<PersonAdapter.PersonViewHolder>() {

    fun updateList(newList: List<Person>) {
        people = newList
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PersonViewHolder {
        val binding = ItemPersonBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return PersonViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PersonViewHolder, position: Int) {
        holder.bind(people[position])
    }

    override fun getItemCount(): Int = people.size

    inner class PersonViewHolder(private val binding: ItemPersonBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(person: Person) {
            val context = binding.root.context

            // Initial Avatar
            val initial = if (person.name.isNotBlank()) person.name.first().uppercase() else "?"
            binding.tvAvatarInitial.text = initial

            // Person Name
            binding.tvPersonName.text = person.name

            // Signed Balance & Formatting
            val balance = person.currentBalancePaise
            binding.tvBalanceAmount.text = CurrencyFormatter.formatSignedRupees(balance)

            // Balance explanation & dynamic color styling
            binding.tvBalanceDescription.text = CurrencyFormatter.getBalanceExplanation(person.name, balance)

            when {
                balance > 0 -> {
                    binding.tvBalanceAmount.setTextColor(ContextCompat.getColor(context, R.color.positive_balance))
                }
                balance < 0 -> {
                    binding.tvBalanceAmount.setTextColor(ContextCompat.getColor(context, R.color.negative_balance))
                }
                else -> {
                    binding.tvBalanceAmount.setTextColor(ContextCompat.getColor(context, R.color.secondary_text))
                }
            }

            // Click listener to navigate to account details
            binding.root.setOnClickListener {
                onPersonClicked(person)
            }
        }
    }
}
