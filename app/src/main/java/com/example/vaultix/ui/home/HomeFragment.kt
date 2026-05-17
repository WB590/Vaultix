package com.example.vaultix.ui.home

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.vaultix.R
import com.example.vaultix.SessionManager
import com.example.vaultix.data.FirebaseRepository
import com.example.vaultix.data.UiPasswordEntry
import com.example.vaultix.databinding.FragmentHomeBinding
import com.example.vaultix.security.PasswordCrypto
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.launch

class HomeFragment : Fragment() {

    interface Listener {
        fun onAddPasswordRequested()
    }

    private var listener: Listener? = null
    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private val adapter = PasswordAdapter { entry ->
        deletePassword(entry)
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        listener = context as? Listener
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.recyclerPasswords.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerPasswords.adapter = adapter

        binding.fabAdd.setOnClickListener {
            listener?.onAddPasswordRequested()
        }

        loadPasswords()
    }

    private fun loadPasswords() {
        val key = SessionManager.getMasterKey() as? SecretKeySpec
        if (key == null) {
            Toast.makeText(requireContext(), R.string.master_required, Toast.LENGTH_SHORT).show()
            return
        }

        toggleLoading(true)
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val encryptedEntries = FirebaseRepository.getPasswordEntries()
                val uiItems = encryptedEntries.mapNotNull { entry ->
                    try {
                        val plainPassword = PasswordCrypto.decrypt(entry.encryptedPassword, entry.iv, key)
                        UiPasswordEntry(
                            id = entry.id,
                            site = entry.site,
                            password = plainPassword
                        )
                    } catch (_: Exception) {
                        null
                    }
                }

                binding.tvEmpty.visibility = if (uiItems.isEmpty()) View.VISIBLE else View.GONE
                adapter.submitList(uiItems)
            } catch (exception: Exception) {
                Toast.makeText(
                    requireContext(),
                    exception.localizedMessage ?: getString(R.string.error_generic),
                    Toast.LENGTH_LONG
                ).show()
            } finally {
                toggleLoading(false)
            }
        }
    }

    private fun deletePassword(entry: UiPasswordEntry) {
        toggleLoading(true)
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                FirebaseRepository.deletePassword(entry.id)
                loadPasswords()
                Toast.makeText(requireContext(), R.string.password_deleted, Toast.LENGTH_SHORT).show()
            } catch (exception: Exception) {
                Toast.makeText(
                    requireContext(),
                    exception.localizedMessage ?: getString(R.string.error_generic),
                    Toast.LENGTH_LONG
                ).show()
            } finally {
                toggleLoading(false)
            }
        }
    }

    private fun toggleLoading(isLoading: Boolean) {
        binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onDetach() {
        super.onDetach()
        listener = null
    }

    companion object {
        fun newInstance() = HomeFragment()
    }
}
