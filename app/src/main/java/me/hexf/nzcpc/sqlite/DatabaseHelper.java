package me.hexf.nzcpc.sqlite;

import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.net.URI;

import kotlin.NotImplementedError;
import me.hexf.nzcpc.BuildConfig;

public class DatabaseHelper extends SQLiteOpenHelper {
    private static final int DB_VERSION = 1;

    public static final String DOCUMENTS_TABLE = "documents";
    public static final String DOCUMENTS_LOCATOR_COLUMN = "locator";
    public static final String DOCUMENTS_DOCUMENT_COLUMN = "document";
    public static final String DOCUMENTS_LAST_SYNC_COLUMN = "last_sync";

    public static final String TRUSTEDISSUERS_TABLE = "trusted_issuers";
    public static final String TRUSTEDISSUERS_ISSUER_COLUMN = "issuer";

    public DatabaseHelper(Context context) {
        super(context, "HEXFCOVIDCHECKER.DB", null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + DOCUMENTS_TABLE + " (\n" +
                DOCUMENTS_LOCATOR_COLUMN + " TEXT NOT NULL UNIQUE,\n" +
                DOCUMENTS_DOCUMENT_COLUMN + " TEXT NOT NULL,\n" +
                DOCUMENTS_LAST_SYNC_COLUMN + " INTEGER DEFAULT (strftime('%s', 'now'))\n" +
                ");");

        db.execSQL("CREATE TABLE " + TRUSTEDISSUERS_TABLE + " (\n" +
                TRUSTEDISSUERS_ISSUER_COLUMN + " TEXT NOT NULL UNIQUE\n" +
                ");");

        ContentValues trustedIssuers = new ContentValues();
        trustedIssuers.put(TRUSTEDISSUERS_ISSUER_COLUMN, "did:web:nzcp.identity.health.nz");
        db.insert(TRUSTEDISSUERS_TABLE, null, trustedIssuers);

        if(BuildConfig.DEBUG) {
            trustedIssuers = new ContentValues();
            trustedIssuers.put(TRUSTEDISSUERS_ISSUER_COLUMN, "did:web:nzcp.covid19.health.nz");
            db.insert(TRUSTEDISSUERS_TABLE, null, trustedIssuers);
        }

    }

    @Override
    public void onUpgrade(SQLiteDatabase sqLiteDatabase, int oldVersion, int newVersion) {
        throw new NotImplementedError();
    }
}
