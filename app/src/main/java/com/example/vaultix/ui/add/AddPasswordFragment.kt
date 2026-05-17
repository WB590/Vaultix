package com.example.vaultix.ui.add

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.vaultix.R
import com.example.vaultix.SessionManager
import com.example.vaultix.data.FirebaseRepository
import com.example.vaultix.databinding.FragmentAddPasswordBinding
import com.example.vaultix.security.PasswordCrypto
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.launch
import java.security.SecureRandom

class AddPasswordFragment : Fragment() {

    private val secureRandom = SecureRandom()
    private val passwordAlphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"

    interface Listener {
        fun onPasswordAdded()
    }

    private var listener: Listener? = null
    private var _binding: FragmentAddPasswordBinding? = null
    private val binding get() = _binding!!

    override fun onAttach(context: Context) {
        super.onAttach(context)
        listener = context as? Listener
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAddPasswordBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.btnGeneratePassword.setOnClickListener {
            val generated = generateStrongPassword(16)
            binding.etPassword.setText(generated)
            Toast.makeText(requireContext(), R.string.password_generated, Toast.LENGTH_SHORT).show()
        }

        binding.btnSavePassword.setOnClickListener {
            savePassword()
        }
    }

    private fun generateStrongPassword(length: Int): String {
        val builder = StringBuilder(length)
        repeat(length) {
            val index = secureRandom.nextInt(passwordAlphabet.length)
            builder.append(passwordAlphabet[index])
        }
        return builder.toString()
    }

    private fun savePassword() {
        val site = binding.etSite.text?.toString()?.trim().orEmpty()
        val password = binding.etPassword.text?.toString().orEmpty()
        val key = SessionManager.getMasterKey() as? SecretKeySpec

        if (site.isBlank() || password.isBlank()) {
            Toast.makeText(requireContext(), R.string.login_fill_fields, Toast.LENGTH_SHORT).show()
            return
        }

        if (key == null) {
            Toast.makeText(requireContext(), R.string.master_required, Toast.LENGTH_SHORT).show()
            return
        }

        toggleLoading(true)
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val encryptedPayload = PasswordCrypto.encrypt(password, key)
                FirebaseRepository.addPassword(
                    site = site,
                    encryptedPassword = encryptedPayload.cipherTextBase64,
                    iv = encryptedPayload.ivBase64
                )
                Toast.makeText(requireContext(), R.string.password_saved, Toast.LENGTH_SHORT).show()
                listener?.onPasswordAdded()
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
        binding.btnSavePassword.isEnabled = !isLoading
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
        fun newInstance() = AddPasswordFragment()
    }
}
