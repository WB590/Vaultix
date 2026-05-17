package com.example.vaultix.ui.login

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.vaultix.R
import com.example.vaultix.data.FirebaseRepository
import com.example.vaultix.databinding.FragmentLoginBinding
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import kotlinx.coroutines.launch

class LoginFragment : Fragment() {

    interface Listener {
        fun onAuthenticated()
    }

    private var listener: Listener? = null
    private var _binding: FragmentLoginBinding? = null
    private val binding get() = _binding!!
    private lateinit var googleSignInClient: GoogleSignInClient
    private lateinit var googleWebClientId: String

    private val googleSignInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != android.app.Activity.RESULT_OK) {
            toggleLoading(false)
            return@registerForActivityResult
        }

        handleGoogleSignInResult(result.data)
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
        _binding = FragmentLoginBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        googleWebClientId = getString(R.string.default_web_client_id)
        googleSignInClient = buildGoogleSignInClient()

        binding.btnLogin.setOnClickListener {
            submit(isRegister = false)
        }

        binding.btnRegister.setOnClickListener {
            submit(isRegister = true)
        }

        binding.btnGoogle.setOnClickListener {
            startGoogleSignIn()
        }
    }

    private fun buildGoogleSignInClient(): GoogleSignInClient {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(googleWebClientId)
            .requestEmail()
            .build()
        return GoogleSignIn.getClient(requireContext(), gso)
    }

    private fun startGoogleSignIn() {
        if (googleWebClientId.isBlank()) {
            Toast.makeText(requireContext(), R.string.google_web_client_not_configured, Toast.LENGTH_LONG).show()
            return
        }

        toggleLoading(true)
        googleSignInClient.signOut().addOnCompleteListener {
            googleSignInLauncher.launch(googleSignInClient.signInIntent)
        }
    }

    private fun handleGoogleSignInResult(data: Intent?) {
        val task = GoogleSignIn.getSignedInAccountFromIntent(data)
        try {
            val account = task.getResult(ApiException::class.java)
            completeGoogleFirebaseLogin(account)
        } catch (exception: ApiException) {
            toggleLoading(false)
            Toast.makeText(
                requireContext(),
                getString(R.string.google_sign_in_failed_with_code, exception.statusCode),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun completeGoogleFirebaseLogin(account: GoogleSignInAccount?) {
        val idToken = account?.idToken
        if (idToken.isNullOrBlank()) {
            toggleLoading(false)
            Toast.makeText(requireContext(), R.string.google_missing_token, Toast.LENGTH_LONG).show()
            return
        }

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                FirebaseRepository.loginWithGoogleIdToken(idToken)
                listener?.onAuthenticated()
            } catch (exception: Exception) {
                Toast.makeText(
                    requireContext(),
                    exception.localizedMessage ?: getString(R.string.google_sign_in_failed),
                    Toast.LENGTH_LONG
                ).show()
            } finally {
                toggleLoading(false)
            }
        }
    }

    private fun submit(isRegister: Boolean) {
        val email = binding.etEmail.text?.toString()?.trim().orEmpty()
        val password = binding.etPassword.text?.toString().orEmpty()

        if (email.isBlank() || password.isBlank()) {
            Toast.makeText(requireContext(), R.string.login_fill_fields, Toast.LENGTH_SHORT).show()
            return
        }

        toggleLoading(true)
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                if (isRegister) {
                    FirebaseRepository.register(email, password)
                } else {
                    FirebaseRepository.login(email, password)
                }
                listener?.onAuthenticated()
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
        binding.btnLogin.isEnabled = !isLoading
        binding.btnRegister.isEnabled = !isLoading
        binding.btnGoogle.isEnabled = !isLoading
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
        fun newInstance() = LoginFragment()
    }
}
