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
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class SignUpActivity extends AppCompatActivity {
    private FirebaseAuth mAuth = FirebaseAuth.getInstance();
    private FirebaseFirestore db = FirebaseFirestore.getInstance();

    private String EMAIL;
    private String NAME;
    private EditText name, email, pass, pass2;
    private ProgressBar progressBar;
    private Button createAccountButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sign_up);
    }

    @Override
    protected void onStart() {
        super.onStart();

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if(user!=null)
        {
            finish();
            return;
        }

        email = findViewById(R.id.signup_email);
        pass = findViewById(R.id.signup_pass);
        pass2 = findViewById(R.id.signup_pass2);
        name = findViewById(R.id.signup_name);
        createAccountButton = findViewById(R.id.create_account_button);
        progressBar = findViewById(R.id.signup_progressbar);

        name.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                createAccountButton.setEnabled(textIsPresent());
            }
            @Override
            public void afterTextChanged(Editable s) { }
        });
        email.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                createAccountButton.setEnabled(textIsPresent());
            }
            @Override
            public void afterTextChanged(Editable s) { }
        });
        pass.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                createAccountButton.setEnabled(textIsPresent());
            }
            @Override
            public void afterTextChanged(Editable s) { }
        });
        pass2.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                createAccountButton.setEnabled(textIsPresent());
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
        if(name.getText().toString().equals("")) textPresent = false;
        if(pass.getText().toString().equals("")) textPresent = false;
        if(pass2.getText().toString().equals("")) textPresent = false;
        return textPresent;
    }

    public void createAccount(View view)
    {
        hideKeyboard(this);

        EMAIL = email.getText().toString().trim();
        String PASS = pass.getText().toString().trim();
        String PASS2 = pass2.getText().toString().trim();
        NAME = name.getText().toString().trim();

        if(NAME.length()<8)
        {
            Toast.makeText(SignUpActivity.this, "Username must be at least 8 characters long", Toast.LENGTH_SHORT).show();
            progressBar.setVisibility(View.INVISIBLE);
            return;
        }
        if(NAME.length()>30)
        {
            Toast.makeText(SignUpActivity.this, "Username can be at most 30 characters long", Toast.LENGTH_SHORT).show();
            progressBar.setVisibility(View.INVISIBLE);
            return;
        }
        if(PASS.length()<8)
        {
            Toast.makeText(SignUpActivity.this, "Password must be at least 8 characters long", Toast.LENGTH_SHORT).show();
            progressBar.setVisibility(View.INVISIBLE);
            return;
        }
        if(!PASS.equals(PASS2))
        {
            Toast.makeText(SignUpActivity.this, "Passwords do not match", Toast.LENGTH_SHORT).show();
            progressBar.setVisibility(View.INVISIBLE);
            return;
        }

        email.setEnabled(false);
        pass.setEnabled(false);
        name.setEnabled(false);
        pass2.setEnabled(false);
        createAccountButton.setEnabled(false);
        createAccountButton.setText(R.string.myapp_registering_text);
        progressBar.setVisibility(View.VISIBLE);
        mAuth.createUserWithEmailAndPassword(EMAIL, PASS)
                .addOnCompleteListener(this, new OnCompleteListener<AuthResult>() {
                    @Override
                    public void onComplete(@NonNull Task<AuthResult> task) {
                        if(task.isSuccessful()  &&  mAuth.getCurrentUser() != null)
                        {
                            Map<String, Object> user = new HashMap<>();
                            user.put("name", NAME);
                            user.put("email", EMAIL);

                            db.collection("users").document(mAuth.getCurrentUser().getUid()).set(user)
                                    .addOnCompleteListener(new OnCompleteListener<Void>() {
                                        @Override
                                        public void onComplete(@NonNull Task<Void> task) {
                                            if(task.isSuccessful())
                                            {
                                                Toast.makeText(SignUpActivity.this, "Registration successful", Toast.LENGTH_SHORT).show();
                                                generateKeywords();
                                                startFriendsActivity();
                                                finish();
                                            }
                                            else
                                            {
                                                Toast.makeText(SignUpActivity.this, "FireStore failed", Toast.LENGTH_SHORT).show();
                                                finish();
                                            }
                                        }
                                    });
                        }
                        else
                        {
                            FirebaseException e = (FirebaseException)task.getException();
                            if(e!=null) Toast.makeText(SignUpActivity.this, "Registration failed: "+e.getMessage(), Toast.LENGTH_SHORT).show();
                            progressBar.setVisibility(View.INVISIBLE);
                            createAccountButton.setEnabled(true);
                            createAccountButton.setText(R.string.myapp_createAccount_text);
                            name.setEnabled(true);
                            pass.setEnabled(true);
                            email.setEnabled(true);
                            pass2.setEnabled(true);
                        }
                    }
                });
    }

    public void generateKeywords() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if(user==null)
            return;
        ArrayList<String> keywords = new ArrayList<>();
        for(int i=0; i<NAME.length(); i++)
        {
            for (int j=i+1; j<=NAME.length(); j++)
                if(NAME.charAt(i)!=' ') keywords.add(NAME.substring(i,j).toLowerCase());
        }
        db.collection("users").document(user.getUid()).update("keywords", keywords)
                .addOnCompleteListener(new OnCompleteListener<Void>() {
                    @Override
                    public void onComplete(@NonNull Task<Void> task) {
                        //if(task.isSuccessful())
                            //Toast.makeText(SignUpActivity.this, "Updated keywords", Toast.LENGTH_SHORT).show();
                        //else
                            //Toast.makeText(SignUpActivity.this, "Failed to update keywords", Toast.LENGTH_SHORT).show();
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

    public void startFriendsActivity()
    {
        Intent friendsActivity = new Intent(this, FriendsActivity.class);
        friendsActivity.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(friendsActivity);
    }
}
