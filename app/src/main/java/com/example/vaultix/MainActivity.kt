package com.example.vaultix

import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.core.view.GravityCompat
import androidx.fragment.app.commit
import com.example.vaultix.data.FirebaseRepository
import com.example.vaultix.databinding.ActivityMainBinding
import com.example.vaultix.ui.add.AddPasswordFragment
import com.example.vaultix.ui.home.HomeFragment
import com.example.vaultix.ui.login.LoginFragment
import com.example.vaultix.ui.master.MasterPasswordFragment
import com.example.vaultix.ui.welcome.WelcomeFragment
import com.google.android.material.navigation.NavigationView

class MainActivity : AppCompatActivity(),
    WelcomeFragment.Listener,
    LoginFragment.Listener,
    MasterPasswordFragment.Listener,
    HomeFragment.Listener,
    AddPasswordFragment.Listener,
    NavigationView.OnNavigationItemSelectedListener {

    private lateinit var binding: ActivityMainBinding
    private lateinit var drawerToggle: ActionBarDrawerToggle

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.topAppBar)

        drawerToggle = ActionBarDrawerToggle(
            this,
            binding.drawerLayout,
            binding.topAppBar,
            R.string.drawer_open,
            R.string.drawer_close
        )
        binding.drawerLayout.addDrawerListener(drawerToggle)
        drawerToggle.syncState()

        binding.navView.setNavigationItemSelectedListener(this)
        updateDrawerHeader()

        supportFragmentManager.addOnBackStackChangedListener {
            syncDrawerForCurrentFragment()
        }

        if (savedInstanceState == null) {
            openWelcomeScreen()
        }

        supportFragmentManager.executePendingTransactions()
        syncDrawerForCurrentFragment()
    }

    override fun onWelcomeFinished() {
        if (FirebaseRepository.currentUser() == null) {
            openLoginScreen()
        } else {
            openMasterScreen()
        }
    }

    override fun onAuthenticated() {
        updateDrawerHeader()
        openMasterScreen()
    }

    override fun onMasterUnlocked() {
        openHomeScreen()
    }

    override fun onAddPasswordRequested() {
        openAddPasswordScreen()
    }

    override fun onPasswordAdded() {
        supportFragmentManager.popBackStack()
    }

    override fun onBackPressed() {
        handleBackPress()
    }

    private fun handleBackPress() {
        if (binding.drawerLayout.isDrawerOpen(GravityCompat.START)) {
            binding.drawerLayout.closeDrawer(GravityCompat.START)
            return
        }

        when (supportFragmentManager.findFragmentById(R.id.fragmentContainer)) {
            is MasterPasswordFragment -> {
                FirebaseRepository.logout()
                SessionManager.clear()
                openLoginScreen(clearBackStack = true)
                return
            }

            is HomeFragment -> {
                FirebaseRepository.logout()
                SessionManager.clear()
                openLoginScreen(clearBackStack = true)
                return
            }
        }

        super.onBackPressed()
    }

    override fun onNavigationItemSelected(item: android.view.MenuItem): Boolean {
        when (item.itemId) {
            R.id.nav_profile -> {
                val email = FirebaseRepository.currentUser()?.email ?: getString(R.string.no_user)
                Toast.makeText(this, getString(R.string.profile_email, email), Toast.LENGTH_SHORT).show()
            }

            R.id.nav_settings -> {
                Toast.makeText(this, R.string.settings_coming_soon, Toast.LENGTH_SHORT).show()
            }

            R.id.nav_logout -> {
                FirebaseRepository.logout()
                SessionManager.clear()
                openLoginScreen(clearBackStack = true)
            }

            R.id.nav_other -> {
                Toast.makeText(this, R.string.other_coming_soon, Toast.LENGTH_SHORT).show()
            }
        }
        binding.drawerLayout.closeDrawer(GravityCompat.START)
        return true
    }

    private fun updateDrawerHeader() {
        val header = binding.navView.getHeaderView(0)
        val emailTextView = header.findViewById<TextView>(R.id.tvDrawerEmail)
        emailTextView.text = FirebaseRepository.currentUser()?.email ?: getString(R.string.no_user)
    }

    private fun openLoginScreen(clearBackStack: Boolean = false) {
        setToolbarVisible(true)
        setDrawerEnabled(false)
        if (clearBackStack) {
            supportFragmentManager.popBackStack(null, androidx.fragment.app.FragmentManager.POP_BACK_STACK_INCLUSIVE)
        }
        supportFragmentManager.commit {
            replace(R.id.fragmentContainer, LoginFragment.newInstance())
        }
    }

    private fun openMasterScreen() {
        setToolbarVisible(true)
        setDrawerEnabled(false)
        supportFragmentManager.commit {
            replace(R.id.fragmentContainer, MasterPasswordFragment.newInstance())
        }
    }

    private fun openHomeScreen() {
        setToolbarVisible(true)
        setDrawerEnabled(true)
        supportFragmentManager.commit {
            replace(R.id.fragmentContainer, HomeFragment.newInstance())
        }
    }

    private fun openAddPasswordScreen() {
        setToolbarVisible(true)
        setDrawerEnabled(false)
        supportFragmentManager.commit {
            replace(R.id.fragmentContainer, AddPasswordFragment.newInstance())
            addToBackStack(AddPasswordFragment::class.java.simpleName)
        }
    }

    private fun openWelcomeScreen() {
        setToolbarVisible(false)
        setDrawerEnabled(false)
        supportFragmentManager.commit {
            replace(R.id.fragmentContainer, WelcomeFragment.newInstance())
        }
    }

    private fun setToolbarVisible(visible: Boolean) {
        binding.topAppBar.visibility = if (visible) android.view.View.VISIBLE else android.view.View.GONE
    }

    private fun setDrawerEnabled(enabled: Boolean) {
        drawerToggle.isDrawerIndicatorEnabled = enabled
        supportActionBar?.setDisplayHomeAsUpEnabled(!enabled)
        supportActionBar?.setDisplayShowHomeEnabled(!enabled)
        binding.drawerLayout.setDrawerLockMode(
            if (enabled) androidx.drawerlayout.widget.DrawerLayout.LOCK_MODE_UNLOCKED
            else androidx.drawerlayout.widget.DrawerLayout.LOCK_MODE_LOCKED_CLOSED
        )

        if (!enabled) {
            binding.topAppBar.setNavigationOnClickListener {
                handleBackPress()
            }
        } else {
            binding.topAppBar.setNavigationOnClickListener {
                binding.drawerLayout.openDrawer(GravityCompat.START)
            }
            drawerToggle.syncState()
        }
    }

    private fun syncDrawerForCurrentFragment() {
        when (supportFragmentManager.findFragmentById(R.id.fragmentContainer)) {
            is HomeFragment -> setDrawerEnabled(true)
            else -> setDrawerEnabled(false)
        }
    }
}