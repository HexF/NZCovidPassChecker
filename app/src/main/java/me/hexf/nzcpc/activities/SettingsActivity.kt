package me.hexf.nzcpc.activities

import android.app.ProgressDialog
import android.content.ContentValues
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import me.hexf.nzcp.exceptions.DocumentResolvingException
import me.hexf.nzcpc.Application
import me.hexf.nzcpc.R
import me.hexf.nzcpc.databinding.SettingsActivityBinding
import me.hexf.nzcpc.resolvers.HttpResolver
import me.hexf.nzcpc.resolvers.SQLiteCachingResolver
import me.hexf.nzcpc.sqlite.DatabaseHelper
import java.net.URI
import java.util.*
import java.util.concurrent.Executor

class SettingsActivity : AppCompatActivity() {
    private lateinit var binding: SettingsActivityBinding
    private lateinit var app: Application


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = SettingsActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)

        app = (application as Application)

        val db = DatabaseHelper(applicationContext)

        if (savedInstanceState == null) {
            supportFragmentManager
                .beginTransaction()
                .replace(R.id.settings, SettingsFragment(db, app.executorService))
                .commit()
        }



        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    class SettingsFragment(
        private val database: DatabaseHelper,
        private val executor: Executor
    ) : PreferenceFragmentCompat() {
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            val ctx = preferenceManager.context
            val screen = preferenceManager.createPreferenceScreen(ctx)

            screen.addPreference(
                Preference(ctx).apply {
                    title = "Issuers"
                    summary = getString(R.string.settings_issuers_help)
                }
            )

            val trustedIssuers = PreferenceCategory(ctx).apply {
                title = "Trusted Issuers"
            }
            screen.addPreference(trustedIssuers)

            val untrustedIssuers = PreferenceCategory(ctx).apply {
                title = "Untrusted Issuers"
            }
            screen.addPreference(untrustedIssuers)

            val locators: MutableMap<String, Pair<Date?, Boolean>> = HashMap()

            database.readableDatabase.query(
                DatabaseHelper.TRUSTEDISSUERS_TABLE,
                arrayOf(
                    DatabaseHelper.TRUSTEDISSUERS_ISSUER_COLUMN
                ),
                null,
                null,
                null,
                null,
                null,
                null
            ).use { cursor ->
                while(cursor.moveToNext()){
                    locators[cursor.getString(0)] = Pair(null, true)
                }
            }

            database.readableDatabase.query(
                DatabaseHelper.DOCUMENTS_TABLE,
                arrayOf(
                    DatabaseHelper.DOCUMENTS_LOCATOR_COLUMN,
                    DatabaseHelper.DOCUMENTS_LAST_SYNC_COLUMN
                ),
                null,
                null,
                null,
                null,
                null,
                null
            ).use { cursor ->
                while(cursor.moveToNext()){
                    locators[cursor.getString(0)] = Pair(
                        Date(cursor.getLong(1) * 1000L),
                        locators.containsKey(cursor.getString(0))
                    )
                }
            }

            locators.forEach { locator ->
                val pref = Preference(ctx).apply {
                    title = getLocatorFriendlyName(locator.key)
                    summary =
                        if(locator.value.first == null) "Never Synced"
                        else "Last Synced: " + locator.value.first.toString()
                }

                pref.setOnPreferenceClickListener {
                    AlertDialog.Builder(ctx)
                        .setTitle(locator.key)
                        .setItems(arrayOf(
                            "Sync",
                            if (locator.value.second) "Untrust" else "Trust"
                        )) { _, n ->
                            if(n == 0){
                                syncDocument(URI.create(locator.key))
                            } else if(n == 1 && !locator.value.second){
                                database.writableDatabase.insert(
                                    DatabaseHelper.TRUSTEDISSUERS_TABLE,
                                    null,
                                    ContentValues().apply{
                                        put(DatabaseHelper.TRUSTEDISSUERS_ISSUER_COLUMN, locator.key)
                                    }
                                )
                            } else if(n == 1 && locator.value.second){
                                database.writableDatabase.delete(
                                    DatabaseHelper.TRUSTEDISSUERS_TABLE,
                                    DatabaseHelper.TRUSTEDISSUERS_ISSUER_COLUMN + " = ?",
                                    arrayOf(locator.key)
                                )
                            }

                            onCreatePreferences(savedInstanceState, rootKey)
                        }
                        .show()
                        .isShowing
                }

                if(locator.value.second) trustedIssuers.addPreference(pref)
                else untrustedIssuers.addPreference(pref)
            }

            preferenceScreen = screen
        }

        private fun syncDocument(locator: URI){
            val progress = ProgressDialog.show(
                activity, "", "Syncing..", true
            )
            executor.execute {
                try {
                    val cache = SQLiteCachingResolver(database, HttpResolver())
                    val doc = cache.parentResolver.resolveDidDocument(locator)
                    cache.updateCache(doc)

                    activity?.runOnUiThread {
                        progress.cancel()
                    }

                } catch (e: DocumentResolvingException) {
                    activity?.runOnUiThread {
                        progress.cancel()
                        Toast.makeText(
                            activity,
                            "Failed to sync: " + e.localizedMessage,
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        }


        private fun getLocatorFriendlyName(locator: String): String{
            return when(locator){
                "did:web:nzcp.identity.health.nz" -> "Ministry of Health"
                "did:web:nzcp.covid19.health.nz" -> "Ministry of Health Development"
                else -> locator
            }
        }

    }
}