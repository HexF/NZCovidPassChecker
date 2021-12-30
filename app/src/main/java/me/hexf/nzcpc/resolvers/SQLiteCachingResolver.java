package me.hexf.nzcpc.resolvers;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.net.URI;

import foundation.identity.did.DIDDocument;
import me.hexf.nzcp.exceptions.DocumentResolvingException;
import me.hexf.nzcp.resolvers.IResolver;
import me.hexf.nzcpc.sqlite.DatabaseHelper;

public class SQLiteCachingResolver implements IResolver {
    public DatabaseHelper databaseHelper;
    public IResolver parentResolver;
    public boolean resolveFromParent = true;

    public SQLiteCachingResolver(DatabaseHelper databaseHelper, IResolver parentResolver){
        this.databaseHelper = databaseHelper;
        this.parentResolver = parentResolver;
    }

    public SQLiteCachingResolver updateCache(DIDDocument document){
        String documentJson = document.toJson();
        ContentValues values = new ContentValues();
        values.put(DatabaseHelper.DOCUMENTS_LOCATOR_COLUMN, document.getId().toString());
        values.put(DatabaseHelper.DOCUMENTS_DOCUMENT_COLUMN, documentJson);

        SQLiteDatabase db = databaseHelper.getWritableDatabase();

        if(getFromCache(document.getId()) == null) {
            // insert
            db.insert(
                    DatabaseHelper.DOCUMENTS_TABLE,
                    null,
                    values
            );

        } else {
            // update
            db.update(
                    DatabaseHelper.DOCUMENTS_TABLE,
                    values,
                    DatabaseHelper.DOCUMENTS_LOCATOR_COLUMN + " = ?",
                    new String[]{
                            document.getId().toString()
                    }
            );
        }

        return this;
    }

    public DIDDocument getFromCache(URI uri){

        try (Cursor cursor = databaseHelper.getReadableDatabase().query("documents",
                new String[]{
                        DatabaseHelper.DOCUMENTS_LOCATOR_COLUMN,
                        DatabaseHelper.DOCUMENTS_DOCUMENT_COLUMN
                },
                DatabaseHelper.DOCUMENTS_LOCATOR_COLUMN + " = ?",
                new String[]{
                        uri.toString()
                },
                null,
                null,
                null,
                "1"
        )) {
            if (cursor != null) {
                if (cursor.moveToFirst())
                    return DIDDocument.fromJson(cursor.getString(1));
            }
            return null;
        }
    }

    @Override
    public DIDDocument resolveDidDocument(URI uri) throws DocumentResolvingException {
        DIDDocument fromCache = getFromCache(uri);
        if(fromCache != null) {
            return fromCache;
        } else if(resolveFromParent) {
            DIDDocument resolved = parentResolver.resolveDidDocument(uri);
            updateCache(resolved);
            return resolved;
        }else{
            throw new DocumentResolvingException("The requests document is not contained in the cache");
        }

    }
}
