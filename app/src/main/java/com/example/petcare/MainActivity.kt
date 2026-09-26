package com.example.petcare

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.text.Editable
import android.text.TextWatcher
import android.text.method.HideReturnsTransformationMethod
import android.text.method.PasswordTransformationMethod
import android.view.View
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.credentials.Credential
import androidx.credentials.CredentialManager
import androidx.credentials.CredentialManagerCallback
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException

class MainActivity : AppCompatActivity() {
    private lateinit var database: AuthDatabaseHelper
    private lateinit var sessionManager: SessionManager
    private lateinit var welcomePanel: View
    private lateinit var loginPanel: View
    private lateinit var signUpPanel: View

    // Password strength views
    private lateinit var segment1: View
    private lateinit var segment2: View
    private lateinit var segment3: View
    private lateinit var segment4: View
    private lateinit var textPasswordStrength: TextView

    // Checkboxes
    private lateinit var termsCheck: CheckBox
    private lateinit var rememberMeCheck: CheckBox

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        database = AuthDatabaseHelper(this)
        sessionManager = SessionManager(this)

        if (sessionManager.isLoggedIn()) {
            startActivity(Intent(this, DashboardActivity::class.java))
            finish()
            return
        }

        updateStatusBarIcons()

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        welcomePanel = findViewById(R.id.welcomePanel)
        loginPanel = findViewById(R.id.loginPanel)
        signUpPanel = findViewById(R.id.signUpPanel)

        // Password strength elements
        segment1 = findViewById(R.id.strengthSegment1)
        segment2 = findViewById(R.id.strengthSegment2)
        segment3 = findViewById(R.id.strengthSegment3)
        segment4 = findViewById(R.id.strengthSegment4)
        textPasswordStrength = findViewById(R.id.textPasswordStrength)

        termsCheck = findViewById(R.id.termsCheck)
        rememberMeCheck = findViewById(R.id.rememberMeCheck)

        // Welcome Landing Screen Action Buttons
        findViewById<View>(R.id.welcomeCreateAccountButton).setOnClickListener { showSignUp() }
        findViewById<View>(R.id.welcomeLoginButton).setOnClickListener { showLogin() }

        // Back buttons on Login and Sign Up headers
        findViewById<View>(R.id.loginBackButton)?.setOnClickListener { showWelcome() }
        findViewById<View>(R.id.signUpBackButton)?.setOnClickListener { showWelcome() }

        // Switch panel buttons inside login & sign up forms
        findViewById<View>(R.id.showSignUpButton).setOnClickListener { showSignUp() }
        findViewById<View>(R.id.showLoginButton).setOnClickListener { showLogin() }

        // Primary action buttons
        findViewById<View>(R.id.loginButton).setOnClickListener { login() }
        findViewById<View>(R.id.signUpButton).setOnClickListener { signUp() }

        // Continue with Google (same flow for sign in and sign up)
        findViewById<View>(R.id.buttonGoogleSignIn)?.setOnClickListener { signInWithGoogle() }
        findViewById<View>(R.id.buttonGoogleSignUp)?.setOnClickListener { signInWithGoogle() }

        // Biometric card quick action
        findViewById<View>(R.id.biometricCard)?.setOnClickListener {
            handleBiometricLogin()
        }

        // Forgot Password
        findViewById<View>(R.id.forgotPasswordLink)?.setOnClickListener {
            startActivity(Intent(this, ForgotPasswordActivity::class.java))
        }

        // Setup Password Visibility Toggles
        setupPasswordToggle(R.id.loginPasswordInput, R.id.loginPasswordEye)
        setupPasswordToggle(R.id.signUpPasswordInput, R.id.signUpPasswordEye)
        setupPasswordToggle(R.id.signUpConfirmPasswordInput, R.id.signUpConfirmPasswordEye)

        // Real-time password strength meter listener
        setupPasswordStrengthWatcher()

        // Device back button handling
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (loginPanel.visibility == View.VISIBLE || signUpPanel.visibility == View.VISIBLE) {
                    showWelcome()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })

        // Default to Welcome landing screen
        showWelcome()
    }

    private fun setupPasswordToggle(inputId: Int, eyeId: Int) {
        val input = findViewById<EditText>(inputId)
        val eye = findViewById<ImageView>(eyeId)
        var isVisible = false

        eye.setOnClickListener {
            isVisible = !isVisible
            if (isVisible) {
                input.transformationMethod = HideReturnsTransformationMethod.getInstance()
                eye.setImageResource(R.drawable.ic_eye_off)
            } else {
                input.transformationMethod = PasswordTransformationMethod.getInstance()
                eye.setImageResource(R.drawable.ic_eye)
            }
            input.setSelection(input.text.length)
        }
    }

    private fun setupPasswordStrengthWatcher() {
        val signUpPasswordInput = findViewById<EditText>(R.id.signUpPasswordInput)
        signUpPasswordInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                updatePasswordStrengthMeter(s?.toString() ?: "")
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun updatePasswordStrengthMeter(password: String) {
        val inactive = R.drawable.bg_strength_inactive
        val weak = R.drawable.bg_strength_weak
        val medium = R.drawable.bg_strength_medium
        val strong = R.drawable.bg_strength_strong

        when {
            password.isEmpty() -> {
                segment1.setBackgroundResource(inactive)
                segment2.setBackgroundResource(inactive)
                segment3.setBackgroundResource(inactive)
                segment4.setBackgroundResource(inactive)
                textPasswordStrength.text = "Enter a password"
                textPasswordStrength.setTextColor(ContextCompat.getColor(this, R.color.auth_text_grey))
            }
            password.length < 6 -> {
                segment1.setBackgroundResource(weak)
                segment2.setBackgroundResource(inactive)
                segment3.setBackgroundResource(inactive)
                segment4.setBackgroundResource(inactive)
                textPasswordStrength.text = "Weak password (at least 6 characters required)"
                textPasswordStrength.setTextColor(ContextCompat.getColor(this, R.color.strength_weak))
            }
            password.length in 6..7 -> {
                segment1.setBackgroundResource(medium)
                segment2.setBackgroundResource(medium)
                segment3.setBackgroundResource(medium)
                segment4.setBackgroundResource(inactive)
                textPasswordStrength.text = "Medium password strength"
                textPasswordStrength.setTextColor(ContextCompat.getColor(this, R.color.strength_medium))
            }
            else -> {
                segment1.setBackgroundResource(strong)
                segment2.setBackgroundResource(strong)
                segment3.setBackgroundResource(strong)
                segment4.setBackgroundResource(strong)
                textPasswordStrength.text = "✓ Strong password"
                textPasswordStrength.setTextColor(ContextCompat.getColor(this, R.color.strength_strong))
            }
        }
    }

    private fun login() {
        val email = textOf(R.id.loginEmailInput)
        val password = textOf(R.id.loginPasswordInput)

        when {
            email.isBlank() || password.isBlank() -> showMessage("Enter email and password.")
            else -> {
                val userName = database.getUserName(email, password)
                if (userName != null) {
                    if (rememberMeCheck.isChecked) {
                        sessionManager.saveEmail(email)
                    } else {
                        sessionManager.clearSavedEmail()
                    }
                    completeSignIn(userName, email)
                } else {
                    findViewById<EditText>(R.id.loginPasswordInput).text?.clear()
                    showMessage("Invalid email or password.")
                }
            }
        }
    }

    private fun signUp() {
        val name = textOf(R.id.signUpNameInput)
        val email = textOf(R.id.signUpEmailInput)
        val password = textOf(R.id.signUpPasswordInput)
        val confirmPassword = textOf(R.id.signUpConfirmPasswordInput)

        when {
            name.isBlank() || email.isBlank() || password.isBlank() || confirmPassword.isBlank() -> {
                showMessage("Complete all required fields.")
            }
            !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches() -> {
                showMessage("Enter a valid email address.")
            }
            password.length < 6 -> {
                showMessage("Password must be at least 6 characters.")
            }
            password != confirmPassword -> {
                showMessage("Passwords do not match.")
            }
            !termsCheck.isChecked -> {
                showMessage("Please accept the Terms of Service & Privacy Policy.")
            }
            database.emailExists(email) -> {
                showMessage("An account already exists for this email.")
            }
            database.createUser(name, email, password) -> {
                showMessage("Account created. You can login now.")
                showLogin()
                findViewById<EditText>(R.id.loginEmailInput).setText(email)
                findViewById<EditText>(R.id.loginPasswordInput).requestFocus()
            }
            else -> showMessage("Could not create account.")
        }
    }

    /** Shared end of every successful sign-in: start the session and remember the account for biometric unlock. */
    private fun completeSignIn(userName: String, email: String) {
        sessionManager.saveUser(userName, email)
        sessionManager.setBiometricEmail(email)
        startActivity(Intent(this, DashboardActivity::class.java))
        finish()
    }

    // ---------- Biometric login ----------

    // BIOMETRIC_STRONG | DEVICE_CREDENTIAL isn't supported before Android 11 and would crash the prompt on Android 10.
    private fun biometricAuthenticators(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
        } else {
            BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL
        }

    /** Biometrics only unlock the account that last signed in on this device with a password or Google. */
    private fun biometricAccountEmail(): String? {
        val sessionEmail = (sessionManager.getBiometricEmail() ?: sessionManager.getSavedEmail())
            ?.takeIf { it.isNotBlank() && database.emailExists(it) }
        if (sessionEmail != null) return sessionEmail

        val inputEmail = textOf(R.id.loginEmailInput).takeIf { it.isNotBlank() && database.emailExists(it) }
        if (inputEmail != null) return inputEmail

        return null
    }

    private fun updateBiometricCard() {
        val subtitle = findViewById<TextView>(R.id.biometricSubtitle) ?: return
        subtitle.text = biometricAccountEmail()?.let { "Sign in as $it" }
            ?: "Sign in or enter email to enable"
    }

    private fun handleBiometricLogin() {
        val targetEmail = biometricAccountEmail()
        if (targetEmail == null) {
            showMessage("Please enter your registered email address or sign in once with password to enable biometric login.")
            return
        }

        val biometricManager = BiometricManager.from(this)
        val authenticators = biometricAuthenticators()

        when (biometricManager.canAuthenticate(authenticators)) {
            BiometricManager.BIOMETRIC_SUCCESS -> showBiometricPrompt(targetEmail)
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED ->
                showMessage("No fingerprint, face or screen lock set up. Add one in your device settings.")
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE ->
                showMessage("Biometric sensor is currently unavailable. Try again later.")
            else -> showBiometricPrompt(targetEmail)
        }
    }

    private fun showBiometricPrompt(targetEmail: String) {
        val executor = ContextCompat.getMainExecutor(this)
        val biometricPrompt = BiometricPrompt(this, executor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                val userName = database.getUserNameByEmail(targetEmail)
                if (userName == null) {
                    showMessage("Account not found. Please sign in with your password.")
                    return
                }
                completeSignIn(userName, targetEmail)
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                if (errorCode != BiometricPrompt.ERROR_USER_CANCELED && errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON) {
                    showMessage("Biometric login error: $errString")
                }
            }
            // onAuthenticationFailed (unrecognised finger) is already reported inside the system prompt.
        })

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock PetCare")
            .setSubtitle("Sign in as $targetEmail")
            .setAllowedAuthenticators(biometricAuthenticators())
            .build()

        biometricPrompt.authenticate(promptInfo)
    }

    // ---------- Continue with Google ----------

    private fun signInWithGoogle() {
        val webClientId = getString(R.string.google_web_client_id)
        if (webClientId.isBlank()) {
            showMessage("Google sign-in isn't configured yet: add google_web_client_id in strings.xml.")
            return
        }

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(GetSignInWithGoogleOption.Builder(webClientId).build())
            .build()

        CredentialManager.create(this).getCredentialAsync(
            this,
            request,
            CancellationSignal(),
            ContextCompat.getMainExecutor(this),
            object : CredentialManagerCallback<GetCredentialResponse, GetCredentialException> {
                override fun onResult(result: GetCredentialResponse) {
                    handleGoogleCredential(result.credential)
                }

                override fun onError(e: GetCredentialException) {
                    when (e) {
                        is GetCredentialCancellationException -> Unit // user closed the account picker
                        is NoCredentialException -> showMessage("No Google account found on this device. Add one in Settings.")
                        else -> showMessage("Google sign-in failed: ${e.message ?: e.type}")
                    }
                }
            }
        )
    }

    private fun handleGoogleCredential(credential: Credential) {
        if (credential !is CustomCredential ||
            credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            showMessage("Google sign-in returned an unexpected credential.")
            return
        }

        val googleCredential = try {
            GoogleIdTokenCredential.createFrom(credential.data)
        } catch (_: GoogleIdTokenParsingException) {
            showMessage("Could not read your Google account details.")
            return
        }

        val email = googleCredential.id
        val name = googleCredential.displayName?.takeIf { it.isNotBlank() } ?: email.substringBefore("@")

        // Creates a local account on first use; an existing account with the same email is reused.
        if (!database.createSocialUser(name, email)) {
            showMessage("Could not create your PetCare account.")
            return
        }
        val userName = database.getUserNameByEmail(email) ?: name
        showMessage("Signed in as $email")
        completeSignIn(userName, email)
    }

    private fun showWelcome() {
        resetLoginForm()
        resetSignUpForm()
        welcomePanel.visibility = View.VISIBLE
        loginPanel.visibility = View.GONE
        signUpPanel.visibility = View.GONE
    }

    private fun showLogin() {
        if (loginPanel.visibility != View.VISIBLE) resetLoginForm()
        resetSignUpForm()
        welcomePanel.visibility = View.GONE
        loginPanel.visibility = View.VISIBLE
        signUpPanel.visibility = View.GONE
        updateBiometricCard()
    }

    private fun showSignUp() {
        resetLoginForm()
        if (signUpPanel.visibility != View.VISIBLE) resetSignUpForm()
        welcomePanel.visibility = View.GONE
        loginPanel.visibility = View.GONE
        signUpPanel.visibility = View.VISIBLE
    }

    private fun textOf(inputId: Int): String =
        findViewById<EditText>(inputId).text?.toString()?.trim().orEmpty()

    /** Back to the initial state: only a "Remember me" email is kept; the password is always cleared. */
    private fun resetLoginForm() {
        val savedEmail = sessionManager.getSavedEmail().orEmpty()
        findViewById<EditText>(R.id.loginEmailInput).setText(savedEmail)
        findViewById<EditText>(R.id.loginPasswordInput).text?.clear()
        rememberMeCheck.isChecked = savedEmail.isNotBlank()
    }

    private fun resetSignUpForm() {
        listOf(R.id.signUpNameInput, R.id.signUpEmailInput, R.id.signUpPasswordInput, R.id.signUpConfirmPasswordInput)
            .forEach { findViewById<EditText>(it).text?.clear() }
        termsCheck.isChecked = false
        updatePasswordStrengthMeter("")
    }

    private fun showMessage(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun updateStatusBarIcons() {
        val isDarkMode = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = !isDarkMode
    }
}
