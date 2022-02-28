package com.example.myapplication;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Toast;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.FirebaseException;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class LoginActivity extends AppCompatActivity {

    private FirebaseAuth mAuth;
    private EditText email, pass;
    private ProgressBar progressBar;
    private Button loginButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);
        mAuth = FirebaseAuth.getInstance();
    }

    @Override
    protected void onStart() {
        super.onStart();
        FirebaseUser user = mAuth.getCurrentUser();

        if(user!=null){
            finish();
            return;
        }

        email = findViewById(R.id.login_email);
        pass = findViewById(R.id.login_pass);
        loginButton = findViewById(R.id.login_button);
        progressBar = findViewById(R.id.login_progressbar);

        email.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                loginButton.setEnabled(textIsPresent());
            }
            @Override
            public void afterTextChanged(Editable s) { }
        });
        pass.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                loginButton.setEnabled(textIsPresent());
            }
            @Override
            public void afterTextChanged(Editable s) { }
        });
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
    }

    public void onBackPressed(View view) {
        onBackPressed();
    }

    private boolean textIsPresent() {
        boolean textPresent = true;
        if(email.getText().toString().equals("")) textPresent = false;
        if(pass.getText().toString().equals("")) textPresent = false;
        return textPresent;
    }

    public void login(View view)
    {
        hideKeyboard(this);

        String EMAIL, PASS;
        EMAIL = email.getText().toString().trim();
        PASS = pass.getText().toString().trim();

        email.setEnabled(false);
        pass.setEnabled(false);
        progressBar.setVisibility(View.VISIBLE);
        loginButton.setText(R.string.myapp_loggingIn_text);
        loginButton.setEnabled(false);
        mAuth.signInWithEmailAndPassword(EMAIL,PASS)
                .addOnCompleteListener(LoginActivity.this, new OnCompleteListener<AuthResult>() {
                    @Override
                    public void onComplete(@NonNull Task<AuthResult> task) {
                        if(task.isSuccessful())
                        {
                            Toast.makeText(LoginActivity.this, "Login Successful", Toast.LENGTH_SHORT).show();
                            startFriendsActivity();
                            finish();
                        }
                        else
                        {
                            FirebaseException e = (FirebaseException)task.getException();
                            if(e!=null) Toast.makeText(LoginActivity.this, "Login Failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                            progressBar.setVisibility(View.INVISIBLE);
                            loginButton.setText(R.string.myapp_login_text);
                            loginButton.setEnabled(true);
                            email.setEnabled(true);
                            pass.setEnabled(true);
                        }
                    }
                });
    }

    public void hideKeyboard(Activity activity) {
        InputMethodManager imm = (InputMethodManager) activity.getSystemService(Activity.INPUT_METHOD_SERVICE);
        //Find the currently focused view, so we can grab the correct window token from it.
        View view = activity.getCurrentFocus();
        //If no view currently has focus, create a new one, just so we can grab a window token from it
        if (view == null) {
            view = new View(activity);
        }
        if(imm!=null) imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        view.clearFocus();
    }

    public void startFriendsActivity() {
        Intent friendsActivity = new Intent(this, FriendsActivity.class);
        friendsActivity.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(friendsActivity);
    }
}
