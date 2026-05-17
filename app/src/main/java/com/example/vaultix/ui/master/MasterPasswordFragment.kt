package com.example.vaultix.ui.master

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
import com.example.vaultix.databinding.FragmentMasterPasswordBinding
import com.example.vaultix.security.PasswordCrypto
import kotlinx.coroutines.launch

class MasterPasswordFragment : Fragment() {

    interface Listener {
        fun onMasterUnlocked()
    }

    private var listener: Listener? = null
    private var _binding: FragmentMasterPasswordBinding? = null
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
        _binding = FragmentMasterPasswordBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnUnlock.setOnClickListener {
            unlockVault()
        }
    }

    private fun unlockVault() {
        val masterPassword = binding.etMasterPassword.text?.toString().orEmpty()
        if (masterPassword.isBlank()) {
            Toast.makeText(requireContext(), R.string.master_required, Toast.LENGTH_SHORT).show()
            return
        }

        toggleLoading(true)
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val securityConfig = FirebaseRepository.getOrCreateUserSecurity {
                    PasswordCrypto.generateSalt()
                }
                val key = PasswordCrypto.deriveMasterKey(masterPassword, securityConfig.salt)

                val verifierCipher = securityConfig.masterVerifierCipher
                val verifierIv = securityConfig.masterVerifierIv
                if (!verifierCipher.isNullOrBlank() && !verifierIv.isNullOrBlank()) {
                    try {
                        val verifierPlain = PasswordCrypto.decrypt(verifierCipher, verifierIv, key)
                        if (verifierPlain != MASTER_VERIFIER_PLAINTEXT) {
                            Toast.makeText(requireContext(), R.string.master_invalid, Toast.LENGTH_SHORT).show()
                            return@launch
                        }
                    } catch (_: Exception) {
                        Toast.makeText(requireContext(), R.string.master_invalid, Toast.LENGTH_SHORT).show()
                        return@launch
                    }
                } else {
                    val verifierPayload = PasswordCrypto.encrypt(MASTER_VERIFIER_PLAINTEXT, key)
                    FirebaseRepository.saveMasterVerifier(
                        cipherTextBase64 = verifierPayload.cipherTextBase64,
                        ivBase64 = verifierPayload.ivBase64
                    )
                }

                SessionManager.setMasterKey(key)
                listener?.onMasterUnlocked()
            } catch (exception: Exception) {
                Toast.makeText(
                    requireContext(),
                    exception.localizedMessage ?: getString(R.string.error_generic),
                    Toast.LENGTH_LONG
                ).show()
            } finally {
                binding.etMasterPassword.text?.clear()
                toggleLoading(false)
            }
        }
    }

    private fun toggleLoading(isLoading: Boolean) {
        binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        binding.btnUnlock.isEnabled = !isLoading
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
        private const val MASTER_VERIFIER_PLAINTEXT = "VAULTIX_MASTER_VERIFIER_V1"

        fun newInstance() = MasterPasswordFragment()
    }
}
