package com.example.smileapp;

import io.github.jan.supabase.SupabaseClient;
import io.github.jan.supabase.SupabaseClientBuilder;
import io.github.jan.supabase.auth.Auth;
import io.github.jan.supabase.postgrest.Postgrest;
import io.github.jan.supabase.storage.Storage;
import io.github.jan.supabase.realtime.Realtime;
import kotlin.Unit;

public class SupabaseManager {
    private static SupabaseClient client;
    
    public static final String SUPABASE_URL = "https://gzqpqqngtcqcrugwwosy.supabase.co";
    public static final String SUPABASE_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Imd6cXBxcW5ndGNxY3J1Z3d3b3N5Iiwicm9sZSI6ImFub24iLCJpYXQiOjE3NzIwMDEzNzEsImV4cCI6MjA4NzU3NzM3MX0.qXeGCaXJL_qfRw7ByoyznuR1cMZYIJnRdN0Tn2EDRhg";

    public static synchronized SupabaseClient getClient() {
        if (client == null) {
            SupabaseClientBuilder builder = new SupabaseClientBuilder(SUPABASE_URL, SUPABASE_KEY);
            builder.install(Auth.Companion, config -> Unit.INSTANCE);
            builder.install(Postgrest.Companion, config -> Unit.INSTANCE);
            builder.install(Storage.Companion, config -> Unit.INSTANCE);
            builder.install(Realtime.Companion, config -> Unit.INSTANCE);
            client = builder.build();
        }
        return client;
    }
}
