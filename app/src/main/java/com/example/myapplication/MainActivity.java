package com.example.myapplication;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.view.WindowManager;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
public class MainActivity extends AppCompatActivity {

    private FirebaseAuth mAuth = FirebaseAuth.getInstance();
    private FirebaseUser user = mAuth.getCurrentUser();

    Runnable startFriendsActivity = new Runnable() {
        @Override
        public void run() {
            Intent friendsActivity = new Intent(MainActivity.this, FriendsActivity.class);
            friendsActivity.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(friendsActivity);
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        }
    };
    Handler handler = new Handler();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
    }

    @Override
    protected void onStart() {
        super.onStart();
        if(user!=null)
            handler.postDelayed(startFriendsActivity, 2000);
        else
        {
            setContentView(R.layout.activity_main);
            findViewById(R.id.app_name).setVisibility(View.VISIBLE);
            findViewById(R.id.login_button).setVisibility(View.VISIBLE);
            findViewById(R.id.signup_button).setVisibility(View.VISIBLE);
        }
    }

    public void SignUp(View view) {
        startActivity(new Intent(MainActivity.this, SignUpActivity.class));
    }

    public void Login(View view) {
        startActivity(new Intent(MainActivity.this, LoginActivity.class));
    }
}
