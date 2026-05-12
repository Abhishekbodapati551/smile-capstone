package com.example.smileapp;

import android.content.Context;
import android.content.SharedPreferences;

public class SessionManager {
    private static final String PREF_NAME = "SmileAppSession";
    private static final String KEY_USER_ID = "user_id";
    private static final String KEY_ROLE = "role";
    private static final String KEY_IS_LOGGED_IN = "isLoggedIn";
    private static final String KEY_SAVED_EMAIL = "saved_email";
    private static final String KEY_SAVED_PASSWORD = "saved_password";

    private SharedPreferences pref;
    private SharedPreferences.Editor editor;
    private Context context;

    public SessionManager(Context context) {
        this.context = context;
        pref = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        editor = pref.edit();
    }

    public void createLoginSession(String userId, String role) {
        editor.putBoolean(KEY_IS_LOGGED_IN, true);
        editor.putString(KEY_USER_ID, userId);
        editor.putString(KEY_ROLE, role);
        editor.commit();
    }

    public boolean isLoggedIn() {
        return pref.getBoolean(KEY_IS_LOGGED_IN, false);
    }

    public String getUserId() {
        return pref.getString(KEY_USER_ID, null);
    }

    public String getUserRole() {
        return pref.getString(KEY_ROLE, null);
    }

    public void logoutUser() {
        // Keep saved credentials even on logout
        String email = getSavedEmail();
        String password = getSavedPassword();
        editor.clear();
        saveCredentials(email, password);
        editor.commit();
    }

    public void saveCredentials(String email, String password) {
        editor.putString(KEY_SAVED_EMAIL, email);
        editor.putString(KEY_SAVED_PASSWORD, password);
        editor.apply();
    }

    public String getSavedEmail() {
        return pref.getString(KEY_SAVED_EMAIL, "");
    }

    public String getSavedPassword() {
        return pref.getString(KEY_SAVED_PASSWORD, "");
    }
}
