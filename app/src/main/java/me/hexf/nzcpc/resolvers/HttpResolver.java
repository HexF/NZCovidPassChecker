package me.hexf.nzcpc.resolvers;

import android.util.Log;


import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;

import javax.net.ssl.HttpsURLConnection;

import foundation.identity.did.DIDDocument;
import me.hexf.nzcp.exceptions.DocumentNotFoundException;
import me.hexf.nzcp.exceptions.DocumentResolvingException;
import me.hexf.nzcp.resolvers.IResolver;

public class HttpResolver implements IResolver {

    @Override
    public DIDDocument resolveDidDocument(URI uri) throws DocumentResolvingException {
        if(!uri.getScheme().equals("did"))
            throw new DocumentResolvingException("The provided scheme is invalid");

        String[] parts = uri.getSchemeSpecificPart().split(":", 2);
        if(!parts[0].equals("web"))
            throw new DocumentResolvingException("The provided DID method is unresolvable");

        String requestUrlString = "https://" + parts[1].replace(":", "/");
        URI requestUri = URI.create(requestUrlString);

        if(requestUri.getPath().equals(""))
            requestUri = URI.create(requestUri + "/.well-known");

        try {
            URL requestUrl = new URL(requestUri + "/did.json");

            HttpURLConnection connection = (HttpURLConnection) requestUrl.openConnection();
            Log.d("HttpResolver", "Querying"+requestUrl);
            if(connection.getResponseCode() == HttpsURLConnection.HTTP_NOT_FOUND)
                throw new DocumentNotFoundException(uri);
            else if(connection.getResponseCode() != HttpsURLConnection.HTTP_OK)
                throw new DocumentResolvingException("Response was not 200 OK");

            return DIDDocument.fromJson(
                    new InputStreamReader(connection.getInputStream())
            );
        } catch (IOException e) {
            throw new DocumentResolvingException("Failed to resolve document");
        }

    }
}