package gregoiregeis.lesspass

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.support.design.widget.BottomSheetBehavior
import android.support.design.widget.FloatingActionButton
import android.support.v7.app.AppCompatActivity
import android.text.Editable
import android.text.InputFilter
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.autofill.AutofillManager
import android.widget.EditText
import android.widget.SeekBar
import android.widget.TextView
import kotlinx.coroutines.experimental.*
import org.jetbrains.anko.*
import org.jetbrains.anko.design.coordinatorLayout
import org.jetbrains.anko.design.floatingActionButton
import org.jetbrains.anko.design.textInputLayout
import org.jetbrains.anko.sdk25.coroutines.onCheckedChange

private inline fun EditText.onTextChanged(crossinline cb: (CharSequence) -> Unit) {
    this.addTextChangedListener(object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun afterTextChanged(s: Editable?) {}

        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
            cb(s!!)
        }
    })
}

private inline fun SeekBar.onSeekBarChange(crossinline cb: (Int) -> Unit) {
    this.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
        override fun onStartTrackingTouch(seekBar: SeekBar?) {}
        override fun onStopTrackingTouch(seekBar: SeekBar?) {}

        override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
            cb(progress)
        }
    })
}

private fun getTextSize(len: Int) = when {
    len <= 16 -> 30f
    len <= 22 -> 27f
    len <= 27 -> 24f
    len <= 32 -> 20f
    len <= 40 -> 16f
    len <= 48 -> 14f
    len <= 56 -> 12f
    else      -> 10f
}

class MainActivity : AppCompatActivity() {
    lateinit var settings: Settings
    lateinit var bottomSheetBehavior: BottomSheetBehavior<View>

    var website        = ""
    var username       = ""
    var masterPassword = ""
    var generatedPass  = ""

    var darkMode = false
    var showPass = true

    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        settings = Settings.load(this)

        defaultSharedPreferences.apply {
            darkMode = getBoolean("darkMode", false)
            showPass = getBoolean("showPassword", true)
        }

        coordinatorLayout {

            var generatedPasswordView: TextView? = null
            var fab: FloatingActionButton? = null
            var fingerprintView: TextView? = null

            var generateJob: Job? = null

            fun triggerUpdate() {
                fab!!.hide()
                generatedPasswordView!!.text = ""

                if (website.isNotBlank() && username.isNotBlank() && masterPassword.isNotBlank()
                        && settings.charSets != 0 && settings.iterations > 0) {
                    generateJob?.apply { if (isActive) cancel() }

                    generateJob = GlobalScope.launch(Dispatchers.Default) {
                        generatedPass = settings.generatePassword(masterPassword, website, username)

                        GlobalScope.launch(Dispatchers.Main) {
                            if (showPass)
                                generatedPasswordView!!.text = generatedPass
                            else
                                generatedPasswordView!!.text = "*".repeat(generatedPass.length)

                            if (bottomSheetBehavior.state == BottomSheetBehavior.STATE_COLLAPSED)
                                fab!!.show()

                            // Adapt text size in order not to be too large
                            generatedPasswordView!!.textSize = getTextSize(generatedPass.length)
                        }
                    }
                }
            }

            savedInstanceState?.apply {
                website        = getString("website", "")
                username       = getString("username", "")
                masterPassword = getString("master", "")
                generatedPass  = getString("generatedPassword", "")

                if (darkMode) {
                    setTheme(R.style.AppTheme_Dark)
                }

                triggerUpdate()
            }


            // Main inputs:

            verticalLayout {
                frameLayout {
                    id = R.id.main_password_view
                    backgroundColorResource = R.color.colorPrimary

                    generatedPasswordView = textView {
                        textColor = Color.WHITE

                        if (generatedPass.isNotBlank()) {
                            textSize = getTextSize(generatedPass.length)

                            if (showPass)
                                text = generatedPass
                            else
                                text = "*".repeat(generatedPass.length)
                        }

                        gravity = Gravity.CENTER_HORIZONTAL
                        textAlignment = TextView.TEXT_ALIGNMENT_CENTER
                        isSelectable = true

                        typeface = Typeface.MONOSPACE

                        setPadding(0, dip(72), 0, dip(72))
                    }.lparams(width = wrapContent, height = wrapContent) {
                        gravity = Gravity.CENTER
                    }
                }

                verticalLayout {
                    padding = dip(24)

                    textInputLayout {
                        editText {
                            hintResource = R.string.website

                            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
                            typeface = Typeface.MONOSPACE

                            setText(website)

                            onTextChanged {
                                website = it.toString()
                                triggerUpdate()
                            }
                        }
                    }

                    textInputLayout {
                        editText {
                            hintResource = R.string.username

                            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
                            typeface = Typeface.MONOSPACE

                            setText(username)

                            onTextChanged {
                                username = it.toString()
                                triggerUpdate()
                            }
                        }
                    }

                    textInputLayout {
                        setTypeface(Typeface.MONOSPACE)

                        editText {
                            hintResource = R.string.master

                            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                            typeface = Typeface.MONOSPACE

                            setText(masterPassword)

                            onTextChanged {
                                masterPassword = it.toString()

                                if (it.isNotBlank()) {
                                    val fingerprint = generateFingerprintHash(masterPassword)
                                    val fingerprintSpan = getFingerprintSpan(fingerprint)

                                    fingerprintView!!.setText(fingerprintSpan, TextView.BufferType.SPANNABLE)
                                } else {
                                    fingerprintView!!.text = ""
                                }

                                triggerUpdate()
                            }
                        }
                    }

                    fingerprintView = textView {
                        typeface = resources.getFont(R.font.fontawesome)
                        textSize = 30f
                        letterSpacing = .6f

                        if (masterPassword.isNotBlank()) {
                            val fingerprint = generateFingerprintHash(masterPassword)
                            val fingerprintSpan = getFingerprintSpan(fingerprint)

                            fingerprintView!!.setText(fingerprintSpan, TextView.BufferType.SPANNABLE)
                        }
                    }.lparams(width = wrapContent, height = wrapContent) {
                        topMargin = dip(10f)
                        gravity = Gravity.CENTER
                    }
                }
            }.lparams(width = matchParent)


            // Floating action button:

            fab = floatingActionButton {
                imageResource = R.drawable.ic_content_copy
                visibility = if (generatedPass.isBlank()) View.GONE else View.VISIBLE

                setOnClickListener {
                    clipboardManager.primaryClip = ClipData.newPlainText("password", generatedPass)

                    GlobalScope.launch(Dispatchers.Main) {
                        imageResource = R.drawable.ic_check
                        delay(1400)
                        imageResource = R.drawable.ic_content_copy
                    }
                }

            }.lparams {
                anchorGravity = Gravity.END or Gravity.BOTTOM
                anchorId = R.id.main_password_view

                margin = dip(12)
            }


            // Bottom sheet:

            val bottomSheetPeekHeight = dip(64)

            bottomSheetBehavior = BottomSheetBehavior<View>(context, null).apply {
                isHideable = true
                peekHeight = bottomSheetPeekHeight

                setBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
                    override fun onSlide(bottomSheet: View, slideOffset: Float) {}

                    override fun onStateChanged(bottomSheet: View, newState: Int) {
                        if (newState != BottomSheetBehavior.STATE_COLLAPSED) {
                            fab.hide()
                        } else if (generatedPass.isNotEmpty()) {
                            fab.show()
                        }
                    }
                })
            }

            verticalLayout {
                val paddingPx = dip(12)
                var previewView: TextView? = null

                padding = paddingPx
                clipToPadding = false

                backgroundColorResource = R.color.background_material_light

                // Preview
                frameLayout {
                    backgroundColorResource = R.color.design_snackbar_background_color

                    previewView = textView {
                        textColor = Color.WHITE
                        typeface = Typeface.MONOSPACE
                    }.lparams(width = wrapContent, height = wrapContent) {
                        gravity = Gravity.CENTER
                    }
                }.lparams(width = matchParent, height = bottomSheetPeekHeight) {
                    setMargins(-paddingPx, -paddingPx, -paddingPx, 0)
                }

                fun updatePreview() {
                    previewView!!.text = settings.generatePreview(context)
                    triggerUpdate()
                }

                updatePreview()

                // Settings
                val settingNameTopMargin = dip(14)
                val settingNameAppearance = R.style.Base_TextAppearance_AppCompat_Subhead

                textView(R.string.length) {
                    textAppearance = settingNameAppearance
                }.lparams {
                    topMargin = settingNameTopMargin
                }

                seekBar {
                    progress = settings.length
                    tooltipText = getText(R.string.length)

                    min = 4
                    max = 64

                    onSeekBarChange {
                        settings.length = progress
                        updatePreview()
                    }
                }

                textView(R.string.counter) {
                    textAppearance = settingNameAppearance
                }.lparams {
                    topMargin = settingNameTopMargin
                }

                seekBar {
                    progress = settings.counter
                    tooltipText = getText(R.string.counter)

                    min = 1
                    max = 100

                    onSeekBarChange {
                        settings.counter = progress
                        updatePreview()
                    }
                }

                textView(R.string.iterations) {
                    textAppearance = settingNameAppearance
                }.lparams {
                    topMargin = settingNameTopMargin
                }

                editText {
                    hintResource = R.string.iterations
                    inputType = InputType.TYPE_CLASS_NUMBER

                    filters += InputFilter.LengthFilter(8)

                    setText(settings.iterations.toString())

                    onTextChanged {
                        try {
                            settings.iterations = text.toString().toInt()
                        } catch (_: Exception) {
                            settings.iterations = 0
                        }
                        updatePreview()
                    }
                }

                textView(R.string.charsets) {
                    textAppearance = settingNameAppearance
                }.lparams {
                    topMargin = settingNameTopMargin
                }

                checkBox {
                    hintResource = R.string.lowercase
                    isChecked = settings.charSets.hasFlag(CharacterSet.Lowercase)

                    onCheckedChange { _, isChecked ->
                        if (isChecked) {
                            settings.charSets = settings.charSets or CharacterSet.Lowercase
                        } else {
                            settings.charSets = settings.charSets and CharacterSet.Lowercase.inv()
                        }
                        updatePreview()
                    }
                }
                checkBox {
                    hintResource = R.string.uppercase
                    isChecked = settings.charSets.hasFlag(CharacterSet.Uppercase)

                    onCheckedChange { _, isChecked ->
                        if (isChecked) {
                            settings.charSets = settings.charSets or CharacterSet.Uppercase
                        } else {
                            settings.charSets = settings.charSets and CharacterSet.Uppercase.inv()
                        }
                        updatePreview()
                    }
                }
                checkBox {
                    hintResource = R.string.numbers
                    isChecked = settings.charSets.hasFlag(CharacterSet.Numbers)

                    onCheckedChange { _, isChecked ->
                        if (isChecked) {
                            settings.charSets = settings.charSets or CharacterSet.Numbers
                        } else {
                            settings.charSets = settings.charSets and CharacterSet.Numbers.inv()
                        }
                        updatePreview()
                    }
                }
                checkBox {
                    hintResource = R.string.symbols
                    isChecked = settings.charSets.hasFlag(CharacterSet.Symbols)

                    onCheckedChange { _, isChecked ->
                        if (isChecked) {
                            settings.charSets = settings.charSets or CharacterSet.Symbols
                        } else {
                            settings.charSets = settings.charSets and CharacterSet.Symbols.inv()
                        }
                        updatePreview()
                    }
                }

                textView(R.string.algorithm) {
                    textAppearance = settingNameAppearance
                }.lparams {
                    topMargin = settingNameTopMargin
                }

                radioGroup {
                    tooltipText = getText(R.string.algorithm)

                    radioButton {
                        text = "SHA256"
                        id = R.id.main_sha256_button

                        onCheckedChange { _, isChecked -> if (isChecked) {
                            settings.algo = Algorithm.SHA256
                            updatePreview()
                        }}
                    }
                    radioButton {
                        text = "SHA384"
                        id = R.id.main_sha384_button

                        onCheckedChange { _, isChecked -> if (isChecked) {
                            settings.algo = Algorithm.SHA384
                            updatePreview()
                        }}
                    }
                    radioButton {
                        text = "SHA512"
                        id = R.id.main_sha512_button

                        onCheckedChange { _, isChecked -> if (isChecked) {
                            settings.algo = Algorithm.SHA512
                            updatePreview()
                        }}
                    }

                    check(when (settings.algo) {
                        Algorithm.SHA256 -> R.id.main_sha256_button
                        Algorithm.SHA384 -> R.id.main_sha384_button
                        Algorithm.SHA512 -> R.id.main_sha512_button
                    })
                }

                textView(R.string.show_password) {
                    textAppearance = settingNameAppearance
                }.lparams {
                    topMargin = settingNameTopMargin
                }

                switch {
                    isChecked = showPass
                    hintResource = if (isChecked) R.string.show_password_on else R.string.show_password_off

                    onCheckedChange { _, isChecked ->
                        showPass = isChecked
                        hintResource = if (isChecked) R.string.show_password_on else R.string.show_password_off

                        if (generatedPass.isNotBlank()) {
                            if (isChecked) {
                                generatedPasswordView!!.text = generatedPass
                            } else {
                                generatedPasswordView!!.text = "*".repeat(generatedPass.length)
                            }
                        }
                    }
                }

                textView(R.string.dark_mode) {
                    textAppearance = settingNameAppearance
                }.lparams {
                    topMargin = settingNameTopMargin
                }

                switch {
                    isChecked = darkMode
                    hintResource = if (isChecked) R.string.dark_mode_on else R.string.dark_mode_off

                    onCheckedChange { _, isChecked ->
                        darkMode = isChecked
                        hintResource = if (isChecked) R.string.dark_mode_on else R.string.dark_mode_off

                        setTheme(if (isChecked) R.style.AppTheme_Dark else R.style.AppTheme)
                    }
                }

                // Autofill

                val autofillManager = getSystemService(AutofillManager::class.java)

                if (autofillManager.isAutofillSupported) {
                    textView(R.string.autofill) {
                        textAppearance = settingNameAppearance
                    }.lparams {
                        topMargin = settingNameTopMargin
                    }

                    switch {
                        isChecked = autofillManager.hasEnabledAutofillServices()
                        hintResource = if (isChecked) R.string.autofill_on else R.string.autofill_off

                        onCheckedChange { _, isChecked ->
                            if (isChecked) {
                                hintResource = R.string.autofill_on

                                startActivityForResult(Intent(android.provider.Settings.ACTION_REQUEST_SET_AUTOFILL_SERVICE).apply {
                                    data = Uri.parse("package:$packageName")
                                }, 0)
                            } else {
                                hintResource = R.string.autofill_off

                                autofillManager.disableAutofillServices()
                            }
                        }
                    }
                }
            }.lparams(width = matchParent) {
                behavior = bottomSheetBehavior
            }
        }
    }

    override fun onBackPressed() {
        if (bottomSheetBehavior.state == BottomSheetBehavior.STATE_EXPANDED) {
            bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
        } else {
            super.onBackPressed()
        }
    }

    override fun onSaveInstanceState(outState: Bundle?) {
        super.onSaveInstanceState(outState)

        @Suppress("NAME_SHADOWING")
        val outState = outState!!

        outState.putString("website",  website)
        outState.putString("username", username)
        outState.putString("master",   masterPassword)
        outState.putString("generatedPassword", generatedPass)
    }

    override fun onPause() {
        settings.save(this)

        defaultSharedPreferences.edit().apply {
            putBoolean("darkMode", darkMode)
            putBoolean("showPassword", showPass)

            apply()
        }

        super.onPause()
    }
}
