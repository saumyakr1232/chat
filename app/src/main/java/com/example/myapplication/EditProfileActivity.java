package com.example.myapplication;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.drawable.RoundedBitmapDrawable;
import androidx.core.graphics.drawable.RoundedBitmapDrawableFactory;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.media.ThumbnailUtils;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.PopupWindow;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;

public class EditProfileActivity extends AppCompatActivity {
    private FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
    private FirebaseFirestore db = FirebaseFirestore.getInstance();
    private FirebaseStorage storage = FirebaseStorage.getInstance();

    private FrameLayout mainLayout;
    private Drawable overlayColor;
    private ImageView imageView;
    private TextView nameText;
    private TextView statusText;

    //profile variables
    private String uid;
    private String name;
    private String status;
    private String email;
    private Bitmap image;

    //popup windows
    private PopupWindow editNamePopup;
    private TextInputEditText editTextName;
    private PopupWindow editStatusPopup;
    private TextInputEditText editTextStatus;
    private PopupWindow editImagePopup;

    //activity codes
    private final int GET_IMAGE_CODE = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if(user == null) {
            finish();
            Toast.makeText(this, "You are not logged in, or your session may have expired", Toast.LENGTH_SHORT).show();
            return;
        }
        setContentView(R.layout.activity_edit_profile);
        setResult(RESULT_CANCELED);

        //intent variables
        uid = getIntent().getStringExtra("uid");
        name = getIntent().getStringExtra("name");
        status = getIntent().getStringExtra("status");
        email = getIntent().getStringExtra("email");
        image = getIntent().getParcelableExtra("image");
    }

    @Override
    protected void onStart() {
        super.onStart();

        //recheck variables with user data from firebase
        if(!uid.equals(user.getUid())  ||  !email.equals(user.getEmail())) {
            finish();
            Toast.makeText(this, "Something went wrong, we are unable to edit your profile right now", Toast.LENGTH_SHORT).show();
            return;
        }

        //cache some views
        imageView = findViewById(R.id.edit_profile_image);
        nameText = findViewById(R.id.edit_profile_name);
        statusText = findViewById(R.id.edit_profile_status);
        TextView emailText = findViewById(R.id.edit_profile_email);

        //set existing profile details
        nameText.setText(name);
        emailText.setText(email);
        if(image != null) {
            RoundedBitmapDrawable roundedBitmapDrawable = RoundedBitmapDrawableFactory.create(getResources(), image);
            roundedBitmapDrawable.setCircular(true);
            roundedBitmapDrawable.setAntiAlias(true);
            imageView.setImageDrawable(roundedBitmapDrawable);
        }
        else loadUserImage(uid);
        if(!status.equals("")) statusText.setText(status);
        else loadUserStatus(uid);

        //main UI
        LayoutInflater inflater = (LayoutInflater) this.getSystemService(LAYOUT_INFLATER_SERVICE);
        mainLayout = findViewById(R.id.edit_profile_main_window);
        overlayColor = new ColorDrawable(getResources().getColor(R.color.colorOverlay));

        //set different popups
        final int WRAP_CONTENT = -2;
        final int MATCH_PARENT = -1;
        if(inflater != null) {
            @SuppressLint("InflateParams") final View popupViewEditName = inflater.inflate(R.layout.popup_window_edit_name, null, false);
            editTextName = popupViewEditName.findViewById(R.id.popup_edit_text);
            editNamePopup = new PopupWindow(popupViewEditName, MATCH_PARENT, WRAP_CONTENT, true);
            editNamePopup.setAnimationStyle(R.style.AppTheme_NoActionBar_EditProfileActivity_WindowAnimation);
            editNamePopup.setOnDismissListener(new PopupWindow.OnDismissListener() {
                @Override
                public void onDismiss() {
                    mainLayout.setForeground(null);
                    editTextName.setText("");
                }
            });
            @SuppressLint("InflateParams") View popupViewEditStatus = inflater.inflate(R.layout.popup_window_edit_status, null, false);
            editTextStatus = popupViewEditStatus.findViewById(R.id.popup_edit_text);
            editStatusPopup = new PopupWindow(popupViewEditStatus, MATCH_PARENT, WRAP_CONTENT, true);
            editStatusPopup.setAnimationStyle(R.style.AppTheme_NoActionBar_EditProfileActivity_WindowAnimation);
            editStatusPopup.setOnDismissListener(new PopupWindow.OnDismissListener() {
                @Override
                public void onDismiss() {
                    mainLayout.setForeground(null);
                    editTextStatus.setText("");
                }
            });
            @SuppressLint("InflateParams") View popupViewEditImage = inflater.inflate(R.layout.popup_window_edit_image, null, false);
            editImagePopup = new PopupWindow(popupViewEditImage, MATCH_PARENT, WRAP_CONTENT, true);
            editImagePopup.setAnimationStyle(R.style.AppTheme_NoActionBar_EditProfileActivity_WindowAnimation);
            editImagePopup.setOnDismissListener(new PopupWindow.OnDismissListener() {
                @Override
                public void onDismiss() {
                    mainLayout.setForeground(null);
                }
            });
        }
        else //why is inflater null?
        {
            finish();
            Toast.makeText(this, "Unable to get system service for editing profile", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if(requestCode == GET_IMAGE_CODE  &&  resultCode != RESULT_CANCELED  &&  data != null  &&  data.getData() != null)
            loadNewImage(data.getData());
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        if(editNamePopup.isShowing()) {
            outState.putBoolean("name_popup", true);
            outState.putString("popup_text", ((EditText) editTextName).getText().toString());
        }
        if(editStatusPopup.isShowing()) {
            outState.putBoolean("status_popup", true);
            outState.putString("popup_text", ((EditText) editTextStatus).getText().toString());
        }
        if(editImagePopup.isShowing())
            outState.putBoolean("image_popup", true);
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onRestoreInstanceState(@NonNull Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        if(savedInstanceState.getBoolean("name_popup")) {
            mainLayout.setForeground(overlayColor);
            editTextName.setText(savedInstanceState.getString("popup_text"));
            mainLayout.post(new Runnable() {
                @Override
                public void run() {
                    editNamePopup.showAtLocation(mainLayout, Gravity.BOTTOM, 0, 0);
                }
            });
        }
        if(savedInstanceState.getBoolean("status_popup")) {
            mainLayout.setForeground(overlayColor);
            editTextStatus.setText(savedInstanceState.getString("popup_text"));
            mainLayout.post(new Runnable() {
                @Override
                public void run() {
                    editStatusPopup.showAtLocation(mainLayout, Gravity.BOTTOM, 0, 0);
                }
            });
        }
        if(savedInstanceState.getBoolean("image_popup")) {
            mainLayout.setForeground(overlayColor);
            mainLayout.post(new Runnable() {
                @Override
                public void run() {
                    editImagePopup.showAtLocation(mainLayout, Gravity.BOTTOM, 0, 0);
                }
            });
        }
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
    }

    //user interaction
    public void onBackPressed(View view) {
        if(editNamePopup.isShowing()) editNamePopup.dismiss();
        else if(editStatusPopup.isShowing()) editStatusPopup.dismiss();
        else if(editImagePopup.isShowing()) editImagePopup.dismiss();
        else onBackPressed();
    }

    public void editName(View view) {
        mainLayout.setForeground(overlayColor);
        editTextName.setText(name);
        editNamePopup.showAtLocation(mainLayout, Gravity.BOTTOM, 0, 0);
    }

    public void editStatus(View view) {
        mainLayout.setForeground(overlayColor);
        editTextStatus.setText(status);
        editStatusPopup.showAtLocation(mainLayout, Gravity.BOTTOM, 0, 0);
    }

    public void editImage(View view) {
        mainLayout.setForeground(overlayColor);
        editImagePopup.showAtLocation(mainLayout, Gravity.BOTTOM, 0, 0);
    }

    public void updateName(View view) {
        final String updatedName = ((EditText) editTextName).getText().toString();
        if(updatedName.length() < 8  ||  updatedName.equals(name)) return;

        db.document("users/" + uid).update("name", updatedName)
                .addOnSuccessListener(EditProfileActivity.this, new OnSuccessListener<Void>() {
                    @Override
                    public void onSuccess(Void aVoid) {
                        //generate keywords
                        ArrayList<String> keywords = new ArrayList<>();
                        for(int i=0; i<updatedName.length(); i++)
                        {
                            for (int j=i+1; j<=updatedName.length(); j++)
                                if(updatedName.charAt(i)!=' ') keywords.add(updatedName.substring(i,j).toLowerCase());
                        }
                        db.collection("users").document(user.getUid()).update("keywords", keywords)
                                .addOnSuccessListener(EditProfileActivity.this, new OnSuccessListener<Void>() {
                                    @Override
                                    public void onSuccess(Void aVoid) {
                                        //everything succeeded
                                        if(editNamePopup.isShowing()) editNamePopup.dismiss();
                                        Toast.makeText(EditProfileActivity.this, "Updated Name", Toast.LENGTH_SHORT).show();
                                        mainLayout.post(new Runnable() {
                                            @Override
                                            public void run() { nameText.setText(updatedName); }});
                                        name = updatedName;
                                        getIntent().putExtra("name", name);
                                        setResult(RESULT_OK, getIntent());
                                    }
                                }).addOnFailureListener(EditProfileActivity.this, new OnFailureListener() {
                            @Override
                            public void onFailure(@NonNull Exception e) {
                                Toast.makeText(EditProfileActivity.this, "Failed to update Name", Toast.LENGTH_SHORT).show();
                            }
                        });
                    }
                }).addOnFailureListener(EditProfileActivity.this, new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception e) {
                Toast.makeText(EditProfileActivity.this, "Failed to update Name", Toast.LENGTH_SHORT).show();
            }
        });
    }

    public void updateStatus(View view) {
        final String updatedStatus = ((EditText) editTextStatus).getText().toString();
        if(updatedStatus.length() < 4  ||  updatedStatus.equals(status)) return;

        db.document("users/" + uid).update("status", updatedStatus)
                .addOnCompleteListener(this, new OnCompleteListener<Void>() {
                    @Override
                    public void onComplete(@NonNull Task<Void> task) {
                        if (task.isSuccessful()) {
                            if (editStatusPopup.isShowing()) editStatusPopup.dismiss();
                            Toast.makeText(EditProfileActivity.this, "Updated Status", Toast.LENGTH_SHORT).show();
                            mainLayout.post(new Runnable() {
                                @Override
                                public void run() {
                                    statusText.setText(updatedStatus);
                                }
                            });
                            status = updatedStatus;
                            getIntent().putExtra("status", status);
                            setResult(RESULT_OK, getIntent());
                        } else {
                            Toast.makeText(EditProfileActivity.this, "Failed to update Status", Toast.LENGTH_SHORT).show();
                        }
                    }
                });
    }

    public void removeImage(View view) {

        db.document("users/" + uid).update("image", "")
                .addOnSuccessListener(this, new OnSuccessListener<Void>() {
                    @Override
                    public void onSuccess(Void aVoid) {
                        File imageFile = new File(getApplicationContext().getCacheDir().getAbsolutePath() + "/users/" + user.getUid() + "/image_cache/" + uid + ".jpg");
                        boolean success = true;
                        if(imageFile.exists()) success = imageFile.delete();

                        if(success) {
                            image = null;
                            if(editImagePopup.isShowing()) editImagePopup.dismiss();
                            mainLayout.post(new Runnable() {
                                @Override
                                public void run() { imageView.setImageDrawable(getResources().getDrawable(R.drawable.ic_account_circle_black_24dp)); }});
                            getIntent().putExtra("image", image);
                            setResult(RESULT_OK, getIntent());
                            Toast.makeText(EditProfileActivity.this, "Removed image from profile", Toast.LENGTH_SHORT).show();
                        }
                        else
                            Toast.makeText(EditProfileActivity.this, "Failed to remove image", Toast.LENGTH_SHORT).show();
                    }
                }).addOnFailureListener(this, new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception e) {
                Toast.makeText(EditProfileActivity.this, "Failed to remove image", Toast.LENGTH_SHORT).show();
            }
        });
    }

    public void addImage(View view) {
        Intent getImage = new Intent(Intent.ACTION_GET_CONTENT);
        getImage.setType("image/*");
        getImage.addCategory(Intent.CATEGORY_OPENABLE);
        getImage.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(getImage, GET_IMAGE_CODE);
    }

    private void loadNewImage(final Uri imageUri) {
        if(editImagePopup.isShowing()) editImagePopup.dismiss();
        Toast.makeText(this, "Please wait, updating profile image...", Toast.LENGTH_SHORT).show();

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    InputStream imageStream = getContentResolver().openInputStream(imageUri);
                    Bitmap updatedImageRaw = BitmapFactory.decodeStream(imageStream);
                    final Bitmap updatedImage = scaleBitmap(updatedImageRaw);
                    if(updatedImage == null) throw new FileNotFoundException();
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            updateImage(updatedImage);
                        }
                    });
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() { Toast.makeText(EditProfileActivity.this, "Unable to access image", Toast.LENGTH_SHORT).show(); }});
                }
            }
        }).start();
    }

    private void updateImage(final Bitmap updatedImage) {
        ByteArrayOutputStream imageOutStream = new ByteArrayOutputStream();
        updatedImage.compress(Bitmap.CompressFormat.JPEG, 100, imageOutStream);
        byte[] bitmapData = imageOutStream.toByteArray();
        ByteArrayInputStream imageInputStream = new ByteArrayInputStream(bitmapData);

        final StorageReference imageProfileRef = storage.getReference().child(getString(R.string.myapp_storage_profile_images)).child(uid + ".jpg");
        imageProfileRef.putStream(imageInputStream)
                .addOnSuccessListener(new OnSuccessListener<UploadTask.TaskSnapshot>() {
                    @Override
                    public void onSuccess(UploadTask.TaskSnapshot taskSnapshot) {
                        //uploaded image
                        imageProfileRef.getDownloadUrl()
                                .addOnSuccessListener(new OnSuccessListener<Uri>() {
                                    @Override
                                    public void onSuccess(Uri uri) {
                                        //got download url
                                        db.document("users/" + uid).update("image", uri.toString())
                                                .addOnSuccessListener(new OnSuccessListener<Void>() {
                                                    @Override
                                                    public void onSuccess(Void aVoid) {
                                                        //everything succeeded
                                                        image = updatedImage;
                                                        mainLayout.post(new Runnable() {
                                                            @Override
                                                            public void run() {
                                                                RoundedBitmapDrawable roundedBitmapDrawable = RoundedBitmapDrawableFactory.create(getResources(), updatedImage);
                                                                roundedBitmapDrawable.setCircular(true);
                                                                roundedBitmapDrawable.setAntiAlias(true);
                                                                imageView.setImageDrawable(roundedBitmapDrawable); }});
                                                        getIntent().putExtra("image", image);
                                                        setResult(RESULT_OK, getIntent());
                                                        Toast.makeText(EditProfileActivity.this, "Updated profile image, it might take an app restart to see these changes", Toast.LENGTH_SHORT).show();

                                                        //try save file locally, if not, no problem
                                                        File imageFile = new File(getApplicationContext().getCacheDir().getAbsolutePath() + "/users/" + user.getUid() + "/image_cache/" + uid + ".jpg");
                                                        try {
                                                            if(!imageFile.exists()  &&  !imageFile.createNewFile()) throw new IOException("Couldn't access storage");
                                                            FileOutputStream fos = new FileOutputStream(imageFile);
                                                            updatedImage.compress(Bitmap.CompressFormat.JPEG, 100, fos);
                                                            fos.flush();
                                                            fos.close();
                                                        } catch (IOException e) {
                                                            e.printStackTrace();
                                                        }
                                                    }
                                                }).addOnFailureListener(new OnFailureListener() {
                                            @Override
                                            public void onFailure(@NonNull Exception e) {
                                                Toast.makeText(EditProfileActivity.this, "Unable to upload image", Toast.LENGTH_SHORT).show();
                                            }
                                        });
                                    }
                                }).addOnFailureListener(new OnFailureListener() {
                            @Override
                            public void onFailure(@NonNull Exception e) {
                                Toast.makeText(EditProfileActivity.this, "Unable to upload image", Toast.LENGTH_SHORT).show();
                            }
                        });

                    }
                }).addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception e) {
                Toast.makeText(EditProfileActivity.this, "Unable to upload image", Toast.LENGTH_SHORT).show();
            }
        });
    }

    //helper methods
    private void loadUserStatus(String uid) {
        db.document("users/" + uid).get()
                .addOnSuccessListener(new OnSuccessListener<DocumentSnapshot>() {
                    @Override
                    public void onSuccess(DocumentSnapshot documentSnapshot) {
                        status = documentSnapshot.getString("status");
                        if(status == null  ||  status.equals(""))
                            status = getString(R.string.myapp_profile_status_default);
                        statusText.setAlpha(0);
                        statusText.setText(status);
                        statusText.animate()
                                .alpha(1)
                                .setDuration(200)
                                .start();
                    }
                }).addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception e) {
                status = getString(R.string.myapp_profile_status_default);
                statusText.setAlpha(0);
                statusText.setText(status);
                statusText.animate()
                        .alpha(1)
                        .setDuration(200)
                        .start();
            }
        });
    }

    private void loadUserImage(final String uid) {
        imageView.setImageDrawable(getResources().getDrawable(R.drawable.ic_account_circle_black_24dp));

        final File image_cache = new File(getApplicationContext().getCacheDir().getAbsolutePath() + "/users/" + user.getUid() + "/image_cache");
        final boolean success;
        if(!image_cache.exists()) success = image_cache.mkdirs();
        else success = true;

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    if (success) {
                        File[] imageFiles = image_cache.listFiles();
                        if (imageFiles == null) return;
                        for (File imageFile : imageFiles) {
                            String fileName = imageFile.getName();
                            String key = fileName.substring(0, fileName.lastIndexOf("."));
                            if (key.equals(uid))
                                image = BitmapFactory.decodeFile(imageFile.getAbsolutePath());
                        }
                    } else throw new Exception();
                }
                catch (Exception ignored) { }
                finally {
                    if(image == null) {
                        db.collection("users").document(uid).get()
                                .addOnSuccessListener(new OnSuccessListener<DocumentSnapshot>() {
                                    @Override
                                    public void onSuccess(final DocumentSnapshot documentSnapshot) {
                                        if (documentSnapshot != null && documentSnapshot.get("image") != null) {
                                            new Thread(new Runnable() {
                                                @Override
                                                public void run() {
                                                    try {
                                                        URL imageURL = new URL(documentSnapshot.getString("image"));
                                                        Bitmap profilePicRaw = BitmapFactory.decodeStream(imageURL.openConnection().getInputStream());
                                                        Bitmap profilePic = scaleBitmap(profilePicRaw);
                                                        image = profilePic;
                                                        File imageFile = new File(getApplicationContext().getCacheDir().getAbsolutePath() + "/users/" + user.getUid() + "/image_cache/" + uid + ".jpg");
                                                        if(!imageFile.exists()  &&  !imageFile.createNewFile()) throw new IOException("Couldn't access storage");
                                                        FileOutputStream fos = new FileOutputStream(imageFile);
                                                        profilePic.compress(Bitmap.CompressFormat.JPEG, 100, fos);
                                                        fos.flush();
                                                        fos.close();
                                                        final RoundedBitmapDrawable roundedProfilePic = RoundedBitmapDrawableFactory.create(getResources(), profilePic);
                                                        roundedProfilePic.setCircular(true);
                                                        roundedProfilePic.setAntiAlias(true);
                                                        imageView.post(new Runnable() {
                                                            @Override
                                                            public void run() { imageView.setImageDrawable(roundedProfilePic); }
                                                        });
                                                    } catch (IOException e) {
                                                        e.printStackTrace();
                                                    }
                                                }
                                            }).start();
                                        }
                                    }
                                });
                    }
                    else {
                        final RoundedBitmapDrawable roundedProfilePic = RoundedBitmapDrawableFactory.create(getResources(), image);
                        roundedProfilePic.setCircular(true);
                        roundedProfilePic.setAntiAlias(true);
                        imageView.post(new Runnable() {
                            @Override
                            public void run() { imageView.setImageDrawable(roundedProfilePic);
                            }
                        });
                    }
                }
            }
        }).start();
    }

    private Bitmap scaleBitmap(Bitmap input) {
        Bitmap output;
        int dimension = Math.min(input.getWidth(), input.getHeight());
        output = ThumbnailUtils.extractThumbnail(input, dimension, dimension);
        return Bitmap.createScaledBitmap(output, 144, 144, true);
    }
}
