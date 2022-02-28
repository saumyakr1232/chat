package com.example.myapplication;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.graphics.drawable.RoundedBitmapDrawable;
import androidx.core.graphics.drawable.RoundedBitmapDrawableFactory;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.media.ThumbnailUtils;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.SearchView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.Transaction;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public class AddFriendActivity extends AppCompatActivity {
    private FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
    private FirebaseFirestore db = FirebaseFirestore.getInstance();
    private String userQuery = "";

    //user data
    private ConcurrentHashMap<String, Bitmap> imageCache = new ConcurrentHashMap<>();
    private MyAdapter adapter;
    private List<Userdata> users = new ArrayList<>();
    private List<FriendsActivity.FriendData> addedUsers = new ArrayList<>();

    //UI
    private TextView searchNoResults;
    private ListView searchedUsers;
    private ProgressBar progressBar;

    static class Userdata implements Parcelable
    {
        String userName;
        String userUid;
        String userMessage;

        Userdata(){
            userName = "";
            userUid = "";
            userMessage = "";
        }

        Userdata(Parcel in) {
            userName = in.readString();
            userUid = in.readString();
            userMessage = in.readString();
        }

        public static final Creator<Userdata> CREATOR = new Creator<Userdata>() {
            @Override
            public Userdata createFromParcel(Parcel in) {
                return new Userdata(in);
            }

            @Override
            public Userdata[] newArray(int size) {
                return new Userdata[size];
            }
        };

        @Override
        public boolean equals(@Nullable Object obj) {
            if(obj == null) return false;
            if(!Userdata.class.isAssignableFrom(obj.getClass())) return false;
            Userdata userdata = (Userdata) obj;
            boolean result = true;
            if(!this.userMessage.equals(userdata.userMessage)) result = false;
            if(!this.userUid.equals(userdata.userUid)) result = false;
            if(!this.userName.equals(userdata.userName)) result = false;
            return result;
        }

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            dest.writeString(userName);
            dest.writeString(userUid);
            dest.writeString(userMessage);
        }
    }

    class MyAdapter extends BaseAdapter
    {
        private List<Userdata> data;

        MyAdapter(List<Userdata> data)
        {
            super();
            this.data = data;
        }

        class ViewHolder{
            TextView userName;
            TextView userUid;
            TextView userMessage;
            ImageView userImage;
            Button sendRequestButton;
            ConstraintLayout userLayout;
        }

        @Override
        public int getCount() {
            return data.size();
        }

        @Override
        public Userdata getItem(int position) {
            return data.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @NonNull
        @Override
        public View getView(final int position, View convertView, @NonNull ViewGroup parent)
        {
            ViewHolder holder;
            if(convertView == null) {
                LayoutInflater inflater = (LayoutInflater) getApplicationContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                if(inflater == null) return new View(getApplicationContext());
                convertView = inflater.inflate(R.layout.add_friend_layout, parent, false);
                holder = new ViewHolder();
                holder.userImage = convertView.findViewById(R.id.user_image);
                holder.userMessage = convertView.findViewById(R.id.request_status_message);
                holder.userName = convertView.findViewById(R.id.user_name);
                holder.userUid = convertView.findViewById(R.id.user_uid);
                holder.sendRequestButton = convertView.findViewById(R.id.send_request_button);
                holder.userLayout = convertView.findViewById(R.id.user_layout);
                convertView.setTag(holder);
            }
            holder = (ViewHolder) convertView.getTag();

            holder.userName.setText(getItem(position).userName);
            holder.userUid.setText(getItem(position).userUid);
            holder.userMessage.setText(getItem(position).userMessage);

            if(getItem(position).userMessage.equals(""))
                holder.userMessage.setVisibility(View.INVISIBLE);
            else
                holder.userMessage.setVisibility(View.VISIBLE);

            if(imageCache.containsKey(getItem(position).userUid))
            {
                RoundedBitmapDrawable roundedBitmapDrawable = RoundedBitmapDrawableFactory.create(getResources(), imageCache.get(holder.userUid.getText().toString()));
                roundedBitmapDrawable.setCircular(true);
                roundedBitmapDrawable.setAntiAlias(true);
                holder.userImage.setImageDrawable(roundedBitmapDrawable);
            }
            else
                holder.userImage.setImageDrawable(getResources().getDrawable(R.drawable.ic_account_circle_black_24dp));

            holder.userLayout.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    expandOption(v);
                }
            });
            holder.sendRequestButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) { sendRequest(v);
                }
            });
            holder.userImage.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    viewProfile(v);
                }
            });

            return convertView;
        }
    }
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_friend);

        adapter = new MyAdapter(users);

        SearchView userSearch = findViewById(R.id.user_search);
        userSearch.findViewById(getResources().getIdentifier("android:id/search_plate", null, null)).setBackgroundColor(Color.TRANSPARENT);
        userSearch.setIconified(false);
        userSearch.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                userQuery = query.toLowerCase();
                updateUI();
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                if(newText.equals("") || newText.length()>=userQuery.length()) {
                    userQuery = newText.toLowerCase();
                    updateUI();
                }
                return true;
            }
        });

        setResult(RESULT_CANCELED);
    }

    @Override
    protected void onStart() {
        super.onStart();

        progressBar = findViewById(R.id.progressBar_searching_user);
        searchedUsers = findViewById(R.id.searched_users);
        searchNoResults = findViewById(R.id.search_no_results);

        searchedUsers.setAdapter(adapter);
    }

    @Override
    protected void onResume() {
        super.onResume();
        readImageCache();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        outState.putParcelableArrayList("users", (ArrayList<? extends Parcelable>) users);
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onRestoreInstanceState(@NonNull Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);

        //restore users
        users.clear();
        ArrayList<Userdata> usersTemp = savedInstanceState.getParcelableArrayList("users");
        if(usersTemp!=null) users.addAll(usersTemp);
    }

    @Override
    public void onBackPressed() {
        getIntent().putParcelableArrayListExtra("friends", (ArrayList<? extends Parcelable>) addedUsers);
        setResult(RESULT_OK, getIntent());
        finish();
    }

    //main content
    public void updateUI() {
        progressBar.setVisibility(View.VISIBLE);
        searchedUsers.setVisibility(View.INVISIBLE);
        searchNoResults.setVisibility(View.INVISIBLE);

        db.collection("users")
                .whereArrayContains("keywords", userQuery)
                .get()
                .addOnCompleteListener(new OnCompleteListener<QuerySnapshot>() {
                    @Override
                    public void onComplete(@NonNull Task<QuerySnapshot> task) {
                        if(task.isSuccessful())
                        {
                            if(task.getResult()!=null && task.getResult().size()!=0) {
                                users.clear();
                                for (QueryDocumentSnapshot userDoc : task.getResult())
                                {
                                    if (user.getUid().equals(userDoc.getId())) continue;
                                    final Userdata userdata = new Userdata();
                                    userdata.userName = Objects.requireNonNull(userDoc.get("name")).toString();
                                    userdata.userUid = userDoc.getId();

                                    userdata.userMessage = "";
                                    userDoc.getReference().collection("friends").document(user.getUid()).get()
                                            .addOnCompleteListener(new OnCompleteListener<DocumentSnapshot>() {
                                                @Override
                                                public void onComplete(@NonNull Task<DocumentSnapshot> task) {
                                                    if (task.isSuccessful()) {
                                                        if (task.getResult() != null && task.getResult().exists()) {
                                                            if (Objects.requireNonNull(task.getResult().get("status")).toString().equals(getString(R.string.myapp_friend_status_0)))
                                                                userdata.userMessage = getString(R.string.myapp_isfriend_message);
                                                            if (Objects.requireNonNull(task.getResult().get("status")).toString().equals(getString(R.string.myapp_friend_status_received)))
                                                                userdata.userMessage = getString(R.string.myapp_requestsent_message);
                                                            if (Objects.requireNonNull(task.getResult().get("status")).toString().equals(getString(R.string.myapp_friend_status_sent)))
                                                                userdata.userMessage = getString(R.string.myapp_requestreceived_message);
                                                        }
                                                    } else
                                                        userdata.userMessage = "Unknown error";
                                                    if(!users.contains(userdata)) users.add(userdata);
                                                    adapter.notifyDataSetChanged();
                                                }
                                            });

                                    if(!imageCache.containsKey(userdata.userUid)){
                                        URL image_url = null;
                                        try {
                                            image_url = new URL(Objects.requireNonNull(userDoc.get("image")).toString());
                                        } catch (Exception e) {
                                            e.printStackTrace();
                                        }
                                        if (image_url != null) {
                                            final URL finalImage_url = image_url;
                                            new Thread(new Runnable() {
                                                @Override
                                                public void run() {
                                                    try {
                                                        Bitmap profile_pic_raw = BitmapFactory.decodeStream(finalImage_url.openConnection().getInputStream());
                                                        Bitmap profile_pic = scaleBitmap(profile_pic_raw);
                                                        imageCache.put(userdata.userUid, profile_pic);
                                                        runOnUiThread(new Runnable() {
                                                            @Override
                                                            public void run() {
                                                                adapter.notifyDataSetChanged();
                                                            }
                                                        });
                                                    } catch (IOException e) {
                                                        e.printStackTrace();
                                                    }
                                                }
                                            }).start();
                                        }
                                    }
                                }
                                progressBar.setVisibility(View.INVISIBLE);
                                if(!userQuery.equals("")){
                                    searchedUsers.setVisibility(View.VISIBLE);
                                    searchNoResults.setVisibility(View.INVISIBLE);
                                }
                            }
                            else
                            {
                                progressBar.setVisibility(View.INVISIBLE);
                                searchedUsers.setVisibility(View.INVISIBLE);
                                searchNoResults.setVisibility(View.VISIBLE);
                            }
                        }
                        else
                        {
                            progressBar.setVisibility(View.INVISIBLE);
                            searchedUsers.setVisibility(View.INVISIBLE);
                            Toast.makeText(AddFriendActivity.this, "Cant fetch data", Toast.LENGTH_SHORT).show();
                        }
                    }
                });
    }

    //helper methods
    public FriendsActivity.FriendData convertToFriend(Userdata userdata) {
        FriendsActivity.FriendData friendData = new FriendsActivity.FriendData();
        friendData.name = userdata.userName;
        friendData.uid = userdata.userUid;
        if (userdata.userMessage.equals(getString(R.string.myapp_isfriend_message)))
             friendData.status = getString(R.string.myapp_friend_status_0);
        else if (userdata.userMessage.equals(getString(R.string.myapp_requestsent_message)))
            friendData.status = getString(R.string.myapp_friend_status_sent);
        else if (userdata.userMessage.equals(getString(R.string.myapp_requestreceived_message)))
            friendData.status = getString(R.string.myapp_friend_status_received);
        else
            return null;
        return friendData;
    }

    public Bitmap scaleBitmap(Bitmap input) {
        Bitmap output;
        int dimension = Math.min(input.getWidth(), input.getHeight());
        output = ThumbnailUtils.extractThumbnail(input, dimension, dimension);
        return Bitmap.createScaledBitmap(output, 144, 144, true);
    }

    public void readImageCache() {
        imageCache.clear();

        final File image_cache = new File(getApplicationContext().getCacheDir().getAbsolutePath() + "/users/" + user.getUid() + "/image_cache");
        boolean success;
        if(!image_cache.exists()) success = image_cache.mkdirs();
        else success = true;
        if(!success) return;

        new Thread(new Runnable() {
            @Override
            public void run() {
                File[] imageFiles = image_cache.listFiles();
                if(imageFiles == null) return;
                for(File imageFile : imageFiles) {
                    try {
                        String fileName = imageFile.getName();
                        String key = fileName.substring(0, fileName.lastIndexOf("."));
                        Bitmap value = BitmapFactory.decodeFile(imageFile.getAbsolutePath());
                        imageCache.put(key, value);
                    } catch (NullPointerException e) {
                        e.printStackTrace();
                    }
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() { adapter.notifyDataSetChanged();
                        }
                    });
                }
            }
        }).start();
    }

    //user interaction action bar
    public void onBackPressed(View view) {
        onBackPressed();
    }

    //user interaction with content
    public void viewProfile(View view){
        ConstraintLayout parent = (ConstraintLayout) view.getParent();
        String uid = ((TextView) parent.findViewById(R.id.user_uid)).getText().toString();
        String name = ((TextView) parent.findViewById(R.id.user_name)).getText().toString();
        startProfileActivity(uid, name);
    }

    public void startProfileActivity(String uid, String name) {
        Intent intent = new Intent(this, ProfileActivity.class);
        intent.putExtra("uid", uid);
        intent.putExtra("name", name);
        intent.putExtra("image", imageCache.get(uid));
        startActivity(intent);
    }

    public void sendRequest(final View view) {
        final View parent = (View)view.getParent();
        if(parent.findViewById(R.id.request_status_message).isShown()) return;

        final String uid = ((TextView) parent.findViewById(R.id.user_uid)).getText().toString();

        db.runTransaction(new Transaction.Function<Void>() {
            @Nullable
            @Override
            public Void apply(@NonNull Transaction transaction) {
                final Map<String, Object> friendData = new HashMap<>();
                final Map<String, Object> userData = new HashMap<>();

                userData.put("status", getString(R.string.myapp_friend_status_received));
                friendData.put("status", getString(R.string.myapp_friend_status_sent));

                transaction.set(db.collection("users").document(user.getUid()).collection("friends").document(uid), friendData);
                transaction.set(db.collection("users").document(uid).collection("friends").document(user.getUid()), userData);

                return null;
            }
        }).addOnCompleteListener(new OnCompleteListener<Void>() {
            @Override
            public void onComplete(@NonNull Task<Void> task) {
                if(task.isSuccessful())
                {
                    for (Userdata userdata : users) {
                        if(userdata.userUid.equals(uid))
                        {
                            userdata.userMessage = getString(R.string.myapp_requestsent_message);
                            addedUsers.add(convertToFriend(userdata));
                            break;
                        }
                    }
                    view.setVisibility(View.GONE);
                    adapter.notifyDataSetChanged();
                }
                else
                    Toast.makeText(AddFriendActivity.this, "Unable to send request", Toast.LENGTH_SHORT).show();
            }
        });
    }

    public void expandOption(View view) {
        //Toast.makeText(AddFriendActivity.this, "Called", Toast.LENGTH_SHORT).show();
        View parent = (View)view.getParent();
        if(parent.findViewById(R.id.send_request_button).isShown())
            parent.findViewById(R.id.send_request_button).setVisibility(View.GONE);
        else if (!parent.findViewById(R.id.request_status_message).isShown())
            parent.findViewById(R.id.send_request_button).setVisibility(View.VISIBLE);
    }
}
