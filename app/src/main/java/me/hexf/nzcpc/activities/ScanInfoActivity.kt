package me.hexf.nzcpc.activities

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import me.hexf.nzcp.CovidPass
import me.hexf.nzcpc.R
import me.hexf.nzcpc.ScanStatus
import me.hexf.nzcpc.databinding.SettingsActivityBinding


const val COVIDPASS_EXTRA = "me.hexf.nzcpc.CovidPass"
const val SCANSTATUS_EXTRA = "me.hexf.nzcpc.ScanStatus"

class ScanInfoActivity : AppCompatActivity() {
    private lateinit var binding: SettingsActivityBinding


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = SettingsActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val passString = intent.getSerializableExtra(COVIDPASS_EXTRA) as String
        val status = intent.getSerializableExtra(SCANSTATUS_EXTRA) as ScanStatus
        var pass: CovidPass? = null
        if(status != ScanStatus.DECODING_FAILED)
            pass = CovidPass.createFromQRCodeString(passString)

        if (savedInstanceState == null) {
            supportFragmentManager
                .beginTransaction()
                .replace(R.id.settings, SettingsFragment(pass, status))
                .commit()
        }
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

    }

    class SettingsFragment(
        private val pass: CovidPass?,
        private val status: ScanStatus
    ) : PreferenceFragmentCompat() {
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            val ctx = preferenceManager.context
            val screen = preferenceManager.createPreferenceScreen(ctx)

            screen.addPreference(
                Preference(ctx).apply {
                    title = "Scan Status"
                    summary = status.toString()
                }
            )

            if(pass != null) {
                screen.addPreference(
                    Preference(ctx).apply {
                        title = "CovidPass UUID"
                        summary = pass.id.toString()
                    }
                )
                val credSubject = PreferenceCategory(ctx).apply {
                    title = "Credential Subject"
                }
                screen.addPreference(credSubject)

                credSubject.addPreference(
                    Preference(ctx).apply {
                        title = "Given Name"
                        summary = pass.credentialSubject.givenName
                    }
                )

                credSubject.addPreference(
                    Preference(ctx).apply {
                        title = "Family Name"
                        summary = pass.credentialSubject.familyName
                    }
                )

                credSubject.addPreference(
                    Preference(ctx).apply{
                        title = "Date of Birth"
                        summary = pass.credentialSubject.dateOfBirth.toString()
                    }
                )

                val validity = PreferenceCategory(ctx).apply{
                    title = "Validity Period"
                }
                screen.addPreference(validity)

                validity.addPreference(
                    Preference(ctx).apply{
                        title = "Not Valid Before"
                        summary = pass.notValidBefore.toString()
                    }
                )

                validity.addPreference(
                    Preference(ctx).apply {
                        title = "Not Valid After"
                        summary = pass.notValidAfter.toString()
                    }
                )

                val keyInfo = PreferenceCategory(ctx).apply {
                    title = "Key Info"
                }
                screen.addPreference(keyInfo)

                keyInfo.addPreference(
                    Preference(ctx).apply {
                        title = "Issuer"
                        summary = pass.issuer.toString()
                    }
                )

                keyInfo.addPreference(
                    Preference(ctx).apply {
                        title = "Key Id"
                        summary = pass.keyId.toString()
                    }
                )

                keyInfo.addPreference(
                    Preference(ctx).apply{
                        title = "Key Locator"
                        summary = "${pass.issuer}#${pass.keyId}"
                    }
                )




            }

            preferenceScreen = screen
        }
    }
}