package com.example.myapplication;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.constraintlayout.widget.Guideline;
import androidx.core.graphics.drawable.RoundedBitmapDrawable;
import androidx.core.graphics.drawable.RoundedBitmapDrawableFactory;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.ThumbnailUtils;
import android.os.Build;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewAnimationUtils;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.example.myapplication.views.NonScrollListView;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.android.material.appbar.AppBarLayout;
import com.google.android.material.navigation.NavigationView;
import com.google.common.reflect.TypeToken;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.WriteBatch;
import com.google.gson.Gson;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;

public class FriendsActivity extends AppCompatActivity {
    private FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
    private FirebaseFirestore db = FirebaseFirestore.getInstance();

    private ArrayList<FriendData> friends = new ArrayList<>();
    private List<FriendData> friendsListUidStrings, friendsRequestListUidStrings, friendsRequestedListUidStrings;
    private ConcurrentHashMap<String, Bitmap> imageCache = new ConcurrentHashMap<>();
    private String friendFilter = "";
    private MyAdapter adapter1, adapter2, adapter3;

    //main UI
    private TextView noFriendsHeader;
    private ScrollView friendActivityScrollView;
    private TextView friendRequestsHeader;
    private NonScrollListView friendRequestsList;
    private TextView sentRequestsHeader;
    private NonScrollListView sentRequestsList;
    private ProgressBar friendActivityProgressBar;
    private TextView friendsHeader;
    private NonScrollListView friendsList;

    //app bar
    private ConstraintLayout searchLayout;
    private int searchLayoutCX;
    private int searchLayoutCY;
    private boolean SEARCH_LAYOUT_ACTIVE = false;
    private EditText searchEditText;
    private ImageButton searchLayoutClearButton;

    //drawer
    private DrawerLayout drawer;
    private NavigationView drawerView;
    private ImageView drawerHeaderIcon;
    private String userDisplayName;

    //activity codes
    private final int ADD_FRIEND_REQUEST_CODE = 0;
    private final int CALL_REQUEST_CODE = 1;

    //webrtc
    private boolean CALL_STARTED = false;

    //helper classes
    static class FriendData implements Parcelable {
        String name;
        String uid;
        String status;
        String imageUrl;

        FriendData()
        {
            name = "";
            uid = "";
            status = "";
            imageUrl = "";
        }

        FriendData(Parcel in) {
            name = in.readString();
            uid = in.readString();
            status = in.readString();
            imageUrl = in.readString();
        }

        @Override
        public boolean equals(@Nullable Object obj) {
            if(obj == null) return false;
            if(!FriendData.class.isAssignableFrom(obj.getClass())) return false;
            FriendData friendData = (FriendData) obj;
            boolean result = true;
            if(!this.uid.equals(friendData.uid)) result = false;
            if(!this.status.equals(friendData.status)) result = false;
            if(!this.name.equals(friendData.name)) result = false;
            if(!this.imageUrl.equals(friendData.imageUrl)) result = false;
            return result;

        }

        public static final Creator<FriendData> CREATOR = new Creator<FriendData>() {
            @Override
            public FriendData createFromParcel(Parcel in) {
                return new FriendData(in);
            }

            @Override
            public FriendData[] newArray(int size) {
                return new FriendData[size];
            }
        };

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {

            dest.writeString(name);
            dest.writeString(uid);
            dest.writeString(status);
            dest.writeString(imageUrl);
        }
    }

    class MyAdapter extends BaseAdapter {
        private int mResource;
        private List<FriendData> data;

        MyAdapter(int resource, List<FriendData> data)
        {
            super();
            mResource = resource;
            this.data = data;
        }

        private FriendData getIem(int position) {
            return data.get(position);
        }

        @Override
        public int getCount() {
            return data.size();
        }

        @Override
        public FriendData getItem(int position) {
            return data.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @NonNull
        @Override
        public View getView(final int position, View convertView,@NonNull ViewGroup parent)
        {
            View friendItem;
            if(convertView == null)
            {
                LayoutInflater inflater = (LayoutInflater) FriendsActivity.this.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                if (inflater == null) return new View(getApplicationContext());
                friendItem = inflater.inflate(mResource, parent, false);
            }
            else
                friendItem = convertView;

            TextView friendName = friendItem.findViewById(R.id.user_name);
            friendName.setText(getIem(position).name);

            TextView friendUid = friendItem.findViewById(R.id.user_uid);
            friendUid.setText(getIem(position).uid);

            ImageView friendImage = friendItem.findViewById(R.id.user_image);
            if(imageCache.containsKey(getIem(position).uid))
            {
                RoundedBitmapDrawable roundedBitmapDrawable = RoundedBitmapDrawableFactory.create(getResources(), imageCache.get(getItem(position).uid));
                roundedBitmapDrawable.setCircular(true);
                roundedBitmapDrawable.setAntiAlias(true);
                friendImage.setImageDrawable(roundedBitmapDrawable);
            }
            else
                friendImage.setImageResource(R.drawable.ic_account_circle_black_24dp);

            return friendItem;
        }
    }

    class UpdateFriendsData extends TimerTask{
        @Override
        public void run() {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    updateFriendCache();
                }
            });
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_friends);
        if(user==null) finish();
    }

    @Override
    protected void onStart() {
        super.onStart();

        friendActivityProgressBar = findViewById(R.id.friendactivity_progressbar);
        noFriendsHeader = findViewById(R.id.no_friends_message);
        friendActivityScrollView = findViewById(R.id.friendactivity_scrollview);
        friendRequestsHeader = findViewById(R.id.friendrequests_header);
        sentRequestsHeader = findViewById(R.id.sentrequests_header);
        friendsHeader = findViewById(R.id.friends_header);

        noFriendsHeader.setVisibility(View.INVISIBLE);
        friendActivityProgressBar.setVisibility(View.VISIBLE);
        friendActivityScrollView.setVisibility(View.GONE);

        setupDrawer();
        searchLayout = findViewById(R.id.search_layout);
        final AppBarLayout appBar = findViewById(R.id.friendactivity_app_bar);
        appBar.post(new Runnable() {
            @Override
            public void run() {
                searchLayoutCY = appBar.getHeight()/2;
                searchLayoutCX = appBar.getWidth() - searchLayoutCY;
            }
        });

        friendsListUidStrings = new ArrayList<>();
        friendsRequestListUidStrings = new ArrayList<>();
        friendsRequestedListUidStrings = new ArrayList<>();

        adapter1 = new MyAdapter(R.layout.friend_layout, friendsListUidStrings);
        friendsList = findViewById(R.id.friends_list);
        friendsList.setAdapter(adapter1);

        adapter2 = new MyAdapter(R.layout.friend_request_received, friendsRequestListUidStrings);
        friendRequestsList = findViewById(R.id.friendrequests_list);
        friendRequestsList.setAdapter(adapter2);

        adapter3 = new MyAdapter(R.layout.friend_request_sent, friendsRequestedListUidStrings);
        sentRequestsList = findViewById(R.id.sentrequests_list);
        sentRequestsList.setAdapter(adapter3);

        searchLayoutClearButton = findViewById(R.id.search_button_search_layout);
        searchEditText = findViewById(R.id.search_edit_text);
        if(searchEditText.getText().toString().equals("")) searchLayoutClearButton.setClickable(false);
        searchEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                friendFilter = s.toString();
                if(friendFilter.equals(""))
                {
                    searchLayoutClearButton.setImageDrawable(getResources().getDrawable(R.drawable.ic_search_black_24dp));
                    searchLayoutClearButton.setClickable(false);
                }
                else
                {
                    searchLayoutClearButton.setImageDrawable(getResources().getDrawable(R.drawable.ic_clear_black_24dp));
                    searchLayoutClearButton.setClickable(true);
                }
                updateUI();
            }
            @Override
            public void afterTextChanged(Editable s) { }
        });

        Timer updateFriendsTimer = new Timer();
        UpdateFriendsData updateFriendsDataTask = new UpdateFriendsData();
        updateFriendsTimer.scheduleAtFixedRate(updateFriendsDataTask, 60000, 60000);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if(SEARCH_LAYOUT_ACTIVE)
        {
            searchLayoutClearButton.post(new Runnable() {
                @Override
                public void run() {
                    searchIconPressed(searchLayoutClearButton);
                }
            });
        }
        readImageCache();
        readFriendsFromJson();
        updateFriendCache();

        setupCallListener();
    }

    @Override
    public void onBackPressed() {
        if(drawer.isDrawerOpen(drawerView))
            drawer.closeDrawer(drawerView);
        else if(SEARCH_LAYOUT_ACTIVE)
        {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                Animator anim = ViewAnimationUtils.createCircularReveal(searchLayout, searchLayoutCX, searchLayoutCY, searchLayoutCX, 0f);
                anim.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        searchLayout.setVisibility(View.INVISIBLE);
                        SEARCH_LAYOUT_ACTIVE = false;
                    }
                });
                anim.start();
            }
            else
            {
                searchLayout.animate()
                        .alpha(0)
                        .setDuration(500)
                        .start();
                searchLayout.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        searchLayout.setVisibility(View.INVISIBLE);
                        SEARCH_LAYOUT_ACTIVE = false;
                    }
                }, 500);
            }
            hideKeyboard(this);
            searchEditText.setText("");
            searchEditText.clearFocus();
            updateUI();
        }
        else
            super.onBackPressed();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        outState.putParcelableArrayList("friends", friends);
        outState.putString("searchText", searchEditText.getText().toString());
        outState.putBoolean("searchActive", SEARCH_LAYOUT_ACTIVE);
        searchEditText.setText("");
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onRestoreInstanceState(@NonNull Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);

        //restore friends
        friends = savedInstanceState.getParcelableArrayList("friends");

        //restore search
        String searchText = savedInstanceState.getString("searchText");
        SEARCH_LAYOUT_ACTIVE = savedInstanceState.getBoolean("searchActive");
        if(searchText!=null  &&  SEARCH_LAYOUT_ACTIVE) searchEditText.setText(searchText);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if(requestCode == ADD_FRIEND_REQUEST_CODE  &&  data!=null)
        {
            for (Parcelable friendData : Objects.requireNonNull(data.getParcelableArrayListExtra("friends"))) {
                FriendData newFriend = (FriendData) friendData;
                friends.add(newFriend);
                writeFriendsToJson();
                updateUI();
            }
        }
        if(requestCode == CALL_REQUEST_CODE)
            CALL_STARTED = false;
    }

    //drawer setup
    private int getStatusBarHeightPx() {
        int result = 0;
        int resourceId = getResources().getIdentifier("status_bar_height", "dimen", "android");
        if (resourceId > 0) {
            result = getResources().getDimensionPixelSize(resourceId);
        }
        return result;
    }

    private int getDrawerWidthPx() {
        int maxWidthDp;
        DisplayMetrics displayMetrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
        if(displayMetrics.widthPixels > 1023  ||  displayMetrics.heightPixels > 1023) {
            //large screen
            maxWidthDp = 320;
        } else {
            //small screen
            maxWidthDp = 280;
        }

        float screenWidthDp = displayMetrics.widthPixels/displayMetrics.density;
        float actionBarSizeDp = 56;
        TypedValue typedValue = new TypedValue();
        if (getTheme().resolveAttribute(android.R.attr.actionBarSize, typedValue, true))
            actionBarSizeDp = TypedValue.complexToDimensionPixelSize(typedValue.data, getResources().getDisplayMetrics())/displayMetrics.density;
        double resultDp = Math.min(maxWidthDp, screenWidthDp - actionBarSizeDp);

        return (int)(resultDp * displayMetrics.density);
    }

    private int getDrawerHeaderHeightPx() {
        return (int) Math.max((9f/16)*getDrawerWidthPx(), 148);
    }

    private void setupDrawer() {
        drawer = findViewById(R.id.drawer_layout);
        drawerView = findViewById(R.id.drawer_layout_navigation_view);
        DrawerLayout.LayoutParams params = (DrawerLayout.LayoutParams) drawerView.getLayoutParams();
        params.width = getDrawerWidthPx();
        drawerView.setLayoutParams(params);
        drawerView.setNavigationItemSelectedListener(new NavigationView.OnNavigationItemSelectedListener() {
            @Override
            public boolean onNavigationItemSelected(@NonNull MenuItem item) {
                drawer.closeDrawers();
                if(item.getItemId() == R.id.drawer_logout)
                    logOut();
                if(item.getItemId() == R.id.drawer_profile)
                    startProfileActivity(user.getUid(), userDisplayName);
                return false;
            }
        });

        final View drawerHeaderView = drawerView.getHeaderView(0).findViewById(R.id.material_drawer_account_header);
        drawerHeaderView.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, getDrawerHeaderHeightPx()));
        TextView drawerHeaderEmail = drawerHeaderView.findViewById(R.id.material_drawer_account_header_email);
        drawerHeaderIcon = drawerHeaderView.findViewById(R.id.material_drawer_account_header_current);
        drawerHeaderEmail.setText(user.getEmail());

        Guideline statusBarGuideline = drawerHeaderView.findViewById(R.id.material_drawer_statusbar_guideline);
        statusBarGuideline.setGuidelineBegin(getStatusBarHeightPx());

        db.document("users/" + user.getUid()).get()
                .addOnSuccessListener(this, new OnSuccessListener<DocumentSnapshot>() {
                    @Override
                    public void onSuccess(DocumentSnapshot documentSnapshot) {
                        userDisplayName = documentSnapshot.getString("name");
                        if(userDisplayName == null) userDisplayName = "";

                        //update name
                        TextView drawerHeaderName = drawerHeaderView.findViewById(R.id.material_drawer_account_header_name);
                        drawerHeaderName.setText(userDisplayName);
                    }
                });
    }

    private void loadUserImage() {
        drawerHeaderIcon.setImageDrawable(getResources().getDrawable(R.drawable.ic_account_circle_white_24dp));
        if(imageCache.containsKey(user.getUid())) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    Bitmap profile_pic = scaleBitmap(Objects.requireNonNull(imageCache.get(user.getUid())));
                    imageCache.put(user.getUid(), profile_pic);
                    drawerHeaderIcon.post(new Runnable() {
                        @Override
                        public void run() {
                            RoundedBitmapDrawable roundedBitmapDrawable = RoundedBitmapDrawableFactory.create(getResources(), imageCache.get(user.getUid()));
                            roundedBitmapDrawable.setCircular(true);
                            roundedBitmapDrawable.setAntiAlias(true);
                            drawerHeaderIcon.setImageDrawable(roundedBitmapDrawable);
                        }
                    });
                }
            }).start();
        }
        else {
            db.collection("users").document(user.getUid()).get()
                    .addOnSuccessListener(this, new OnSuccessListener<DocumentSnapshot>() {
                        @Override
                        public void onSuccess(DocumentSnapshot documentSnapshot) {
                            URL image_url = null;
                            try {
                                image_url = new URL(Objects.requireNonNull(documentSnapshot.get("image")).toString());
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
                                            File imageFile = new File(getApplicationContext().getCacheDir().getAbsolutePath() + "/users/" + user.getUid() + "/image_cache/" + user.getUid() + ".jpg");
                                            if(!imageFile.exists()  &&  !imageFile.createNewFile()) throw new IOException("Couldn't access storage");
                                            FileOutputStream fos = new FileOutputStream(imageFile);
                                            profile_pic.compress(Bitmap.CompressFormat.JPEG, 100, fos);
                                            fos.flush();
                                            fos.close();
                                            imageCache.put(user.getUid(), profile_pic);
                                            drawerHeaderIcon.post(new Runnable() {
                                                @Override
                                                public void run() {
                                                    RoundedBitmapDrawable roundedBitmapDrawable = RoundedBitmapDrawableFactory.create(getResources(), imageCache.get(user.getUid()));
                                                    roundedBitmapDrawable.setCircular(true);
                                                    roundedBitmapDrawable.setAntiAlias(true);
                                                    drawerHeaderIcon.setImageDrawable(roundedBitmapDrawable);
                                                }
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
    }

    //main content
    public void updateFriendCache() {

        db.collection("users").document(user.getUid()).collection("friends").get()
                .addOnCompleteListener(this, new OnCompleteListener<QuerySnapshot>() {
                    @Override
                    public void onComplete(@NonNull Task<QuerySnapshot> task) {
                        if (task.isSuccessful() && task.getResult() != null) {
                            for (final QueryDocumentSnapshot friend : task.getResult())
                            {
                                final FriendData friendData = new FriendData();
                                friendData.uid = friend.getId();
                                friendData.status = friend.getString("status");

                                db.collection("users").document(friend.getId()).get()
                                        .addOnCompleteListener(FriendsActivity.this, new OnCompleteListener<DocumentSnapshot>() {
                                            @Override
                                            public void onComplete(@NonNull final Task<DocumentSnapshot> task)
                                            {
                                                if(task.isSuccessful() && task.getResult()!=null)
                                                {
                                                    friendData.name = task.getResult().getString("name");
                                                    if(friendData.name == null) friendData.name = "";
                                                    final String prevImageUrlBackup = friendData.imageUrl;
                                                    friendData.imageUrl = task.getResult().getString("image");
                                                    if(friendData.imageUrl == null) friendData.imageUrl = "";

                                                    boolean imageChanged;
                                                    if(!friends.contains(friendData)) {
                                                        imageChanged = true;
                                                        for(FriendData data : friends) {
                                                            if(data.uid.equals(friendData.uid)) {
                                                                if(data.imageUrl.equals(friendData.imageUrl)) imageChanged = false;
                                                                friends.remove(data);
                                                                break;
                                                            }
                                                        }
                                                    } else return;

                                                    friends.add(friendData);
                                                    writeFriendsToJson();
                                                    updateUI();

                                                    if(imageChanged) {
                                                        new Thread(new Runnable() {
                                                            @Override
                                                            public void run() {
                                                                try {
                                                                    URL imageURL = new URL(task.getResult().getString("image"));
                                                                    Bitmap profile_pic_raw = BitmapFactory.decodeStream(imageURL.openConnection().getInputStream());
                                                                    Bitmap profile_pic = scaleBitmap(profile_pic_raw);
                                                                    File imageFile = new File(getApplicationContext().getCacheDir().getAbsolutePath() + "/users/" + user.getUid() + "/image_cache/" + friend.getId() + ".jpg");
                                                                    if (!imageFile.exists()  &&  !imageFile.createNewFile()) throw new IOException("Couldn't access storage");
                                                                    FileOutputStream fos = new FileOutputStream(imageFile);
                                                                    profile_pic.compress(Bitmap.CompressFormat.JPEG, 100, fos);
                                                                    fos.flush();
                                                                    fos.close();
                                                                    imageCache.put(friend.getId(), profile_pic);
                                                                    runOnUiThread(new Runnable() {
                                                                        @Override
                                                                        public void run() { updateUI(); }});
                                                                } catch (MalformedURLException e) {
                                                                    File imageFile = new File(getApplicationContext().getCacheDir().getAbsolutePath() + "/users/" + user.getUid() + "/image_cache/" + friend.getId() + ".jpg");
                                                                    if (imageFile.exists()) Log.e("IMAGE_DELETED", String.valueOf(imageFile.delete()));
                                                                    runOnUiThread(new Runnable() {
                                                                        @Override
                                                                        public void run() {
                                                                            imageCache.remove(friend.getId());
                                                                            updateUI();
                                                                        }
                                                                    });
                                                                }
                                                                catch (IOException e) {
                                                                    e.printStackTrace();
                                                                    runOnUiThread(new Runnable() {
                                                                        @Override
                                                                        public void run() {
                                                                            for(FriendData data : friends) {
                                                                                if(data.uid.equals(friendData.uid)) {
                                                                                    data.imageUrl = prevImageUrlBackup;
                                                                                    break;
                                                                                }
                                                                            }
                                                                            writeFriendsToJson();
                                                                        }
                                                                    });
                                                                }
                                                            }
                                                        }).start();
                                                    }
                                                }
                                                else
                                                    Toast.makeText(FriendsActivity.this, "Couldn't refresh friends. Try checking your internet connection or login again.", Toast.LENGTH_SHORT).show();
                                            }
                                        });
                            }
                            //remove friends which are no longer in fireStore db
                            Iterator<FriendData> iterator = friends.iterator();
                            while (iterator.hasNext())
                            {
                                FriendData friendData = iterator.next();
                                boolean exists = false;
                                for(QueryDocumentSnapshot friend : task.getResult())
                                    if(friendData.uid.equals(friend.getId())) exists = true;
                                if(!exists) iterator.remove();
                            }
                            writeFriendsToJson();
                            updateUI();

                            //update UI when friends size becomes 0
                            //if(task.getResult().size()==0) updateUI();
                        } else
                            Toast.makeText(FriendsActivity.this, "Couldn't refresh friends. Try checking your internet connection or login again.", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    public void updateUI() {
        noFriendsHeader.setVisibility(View.INVISIBLE);

        if(friends != null) {
            if (friends.size() != 0)
            {
                friendsListUidStrings.clear();
                friendsRequestListUidStrings.clear();
                friendsRequestedListUidStrings.clear();
                for (FriendData friend : friends) {
                    if (friend.status.equals(getString(R.string.myapp_friend_status_received))  &&  friend.name.toLowerCase().contains(friendFilter.toLowerCase()))
                        friendsRequestListUidStrings.add(friend);
                    if (friend.status.equals(getString(R.string.myapp_friend_status_0))  &&  friend.name.toLowerCase().contains(friendFilter.toLowerCase()))
                        friendsListUidStrings.add(friend);
                    if (friend.status.equals(getString(R.string.myapp_friend_status_sent))  &&  friend.name.toLowerCase().contains(friendFilter.toLowerCase()))
                        friendsRequestedListUidStrings.add(friend);
                }

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        adapter1.notifyDataSetChanged();
                    }
                });
                if (friendsListUidStrings.size() == 0)
                {
                    friendsHeader.setVisibility(View.GONE);
                    friendsList.setVisibility(View.GONE);
                }
                else
                {
                    friendsHeader.setVisibility(View.VISIBLE);
                    friendsList.setVisibility(View.VISIBLE);
                }

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        adapter2.notifyDataSetChanged();
                    }
                });
                if (friendsRequestListUidStrings.size() == 0)
                {
                    friendRequestsHeader.setVisibility(View.GONE);
                    friendRequestsList.setVisibility(View.GONE);
                }
                else
                {
                    friendRequestsHeader.setVisibility(View.VISIBLE);
                    friendRequestsList.setVisibility(View.VISIBLE);
                }

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        adapter3.notifyDataSetChanged();
                    }
                });
                if (friendsRequestedListUidStrings.size() == 0)
                {
                    sentRequestsHeader.setVisibility(View.GONE);
                    sentRequestsList.setVisibility(View.GONE);
                }
                else
                {
                    sentRequestsHeader.setVisibility(View.VISIBLE);
                    sentRequestsList.setVisibility(View.VISIBLE);
                }

                if(friendsListUidStrings.size() + friendsRequestedListUidStrings.size() + friendsRequestListUidStrings.size() == 0)
                    noFriendsHeader.setVisibility(View.VISIBLE);
                friendActivityProgressBar.setVisibility(View.INVISIBLE);
                friendActivityScrollView.setVisibility(View.VISIBLE);

            } else {
                friendActivityProgressBar.setVisibility(View.INVISIBLE);
                friendActivityScrollView.setVisibility(View.VISIBLE);
                friendRequestsHeader.setVisibility(View.GONE);
                friendRequestsList.setVisibility(View.GONE);
                sentRequestsHeader.setVisibility(View.GONE);
                sentRequestsList.setVisibility(View.GONE);
                noFriendsHeader.setVisibility(View.VISIBLE);
            }
        }
        else {
            Toast.makeText(FriendsActivity.this, "Failed to load friends", Toast.LENGTH_SHORT).show();
            friendActivityProgressBar.setVisibility(View.INVISIBLE);
            friendActivityScrollView.setVisibility(View.VISIBLE);
            friendRequestsHeader.setVisibility(View.GONE);
            friendRequestsList.setVisibility(View.GONE);
            sentRequestsHeader.setVisibility(View.GONE);
            sentRequestsList.setVisibility(View.GONE);
        }
    }

    public Bitmap scaleBitmap(Bitmap input) {
        Bitmap output;
        int dimension = Math.min(input.getWidth(), input.getHeight());
        output = ThumbnailUtils.extractThumbnail(input, dimension, dimension);
        return Bitmap.createScaledBitmap(output, 144, 144, true);
    }

    //user interaction with main content
    public void cancelFriendRequest(View view) {
        final String uid = ((TextView)((View)view.getParent()).findViewById(R.id.user_uid)).getText().toString();

        WriteBatch batch = db.batch();
        batch.delete(db.collection("users").document(uid).collection("friends").document(user.getUid()));
        batch.delete(db.collection("users").document(user.getUid()).collection("friends").document(uid));
        batch.commit()
                .addOnCompleteListener(this, new OnCompleteListener<Void>() {
                    @Override
                    public void onComplete(@NonNull Task<Void> task) {
                        if(task.isSuccessful())
                        {
                            Toast.makeText(FriendsActivity.this, "Cancelled friend request", Toast.LENGTH_SHORT).show();
                            for (FriendData friend: friends)
                            {
                                if(friend.uid.equals(uid))
                                {
                                    friends.remove(friend);
                                    writeFriendsToJson();
                                    updateUI();
                                    break;
                                }
                            }
                        }
                        else
                            Toast.makeText(FriendsActivity.this, "Unable to remove friend", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    public void rejectFriendRequest(View view) {
        final String uid = ((TextView)((View)view.getParent()).findViewById(R.id.user_uid)).getText().toString();

        WriteBatch batch = db.batch();
        batch.delete(db.collection("users").document(uid).collection("friends").document(user.getUid()));
        batch.delete(db.collection("users").document(user.getUid()).collection("friends").document(uid));
        batch.commit()
                .addOnCompleteListener(this, new OnCompleteListener<Void>() {
                    @Override
                    public void onComplete(@NonNull Task<Void> task) {
                        if(task.isSuccessful())
                        {
                            Toast.makeText(FriendsActivity.this, "Rejected friend", Toast.LENGTH_SHORT).show();
                            for (FriendData friend: friends)
                            {
                                if(friend.uid.equals(uid))
                                {
                                    friends.remove(friend);
                                    writeFriendsToJson();
                                    updateUI();
                                    break;
                                }
                            }
                        }
                        else
                            Toast.makeText(FriendsActivity.this, "Unable to reject friend", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    public void acceptFriendRequest(View view) {
        final String uid = ((TextView)((View)view.getParent()).findViewById(R.id.user_uid)).getText().toString();

        WriteBatch batch = db.batch();
        batch.update(db.collection("users").document(uid).collection("friends").document(user.getUid()), "status", "friend");
        batch.update(db.collection("users").document(user.getUid()).collection("friends").document(uid), "status", "friend");
        batch.commit()
                .addOnCompleteListener(this, new OnCompleteListener<Void>() {
                    @Override
                    public void onComplete(@NonNull Task<Void> task) {
                        if(task.isSuccessful())
                        {
                            Toast.makeText(FriendsActivity.this, "Accepted friend", Toast.LENGTH_SHORT).show();
                            for (FriendData friend: friends)
                            {
                                if(friend.uid.equals(uid))
                                {
                                    friend.status = "friend";
                                    updateUI();
                                    break;
                                }
                            }
                        }
                        else
                            Toast.makeText(FriendsActivity.this, "Unable to accept friend", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    public void viewProfile(View view){
        ConstraintLayout parent = (ConstraintLayout) view.getParent();
        String uid = ((TextView) parent.findViewById(R.id.user_uid)).getText().toString();
        String name = ((TextView) parent.findViewById(R.id.user_name)).getText().toString();
        startProfileActivity(uid, name);
    }

    //new activities
    public void logOut() {
        //delete user cache
        File userCache = new File(getApplicationContext().getCacheDir().getAbsolutePath() + "/users/" + user.getUid());
        if(userCache.exists()) Log.d("CACHE DELETE", String.valueOf(userCache.delete()));

        FirebaseAuth.getInstance().signOut();
        Intent main = new Intent(this, MainActivity.class);
        main.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(main);
        //Snackbar.makeText(this, "Logged Out", Toast.LENGTH_SHORT).show();
    }

    public void addFriend(View view) {
        Intent intent = new Intent(this, AddFriendActivity.class);
        startActivityForResult(intent, ADD_FRIEND_REQUEST_CODE);
    }

    public void startChat(View view) {
        Intent intent = new Intent(this, ChatActivity.class);
        String friendUid = ((TextView)view.findViewById(R.id.user_uid)).getText().toString();
        intent.putExtra("uid", friendUid);
        intent.putExtra("name", ((TextView)view.findViewById(R.id.user_name)).getText().toString());
        intent.putExtra("image", imageCache.get(friendUid));
        startActivity(intent);
    }

    public void startProfileActivity(String uid, String name) {
        Intent intent = new Intent(this, ProfileActivity.class);
        intent.putExtra("uid", uid);
        intent.putExtra("name", name);
        intent.putExtra("image", imageCache.get(uid));
        startActivity(intent);
    }

    private void setupCallListener() {
        db.document("users/" + user.getUid()).addSnapshotListener(this, new EventListener<DocumentSnapshot>() {
            @Override
            public void onEvent(@Nullable DocumentSnapshot value, @Nullable FirebaseFirestoreException error) {
                if(value != null  &&  value.get("sdp") != null)
                {
                    Intent callIntent = new Intent(FriendsActivity.this, CallActivity.class);
                    callIntent.putExtra("userUid", user.getUid());
                    callIntent.putExtra("friendUid", (String) value.get("call"));
                    if(!CALL_STARTED) startActivityForResult(callIntent, CALL_REQUEST_CODE);
                    CALL_STARTED = true;
                }
            }
        });
    }

    //user interaction with action bar
    public void menuButtonClicked(View view){
        drawer.openDrawer(GravityCompat.START, true);
    }

    public void searchIconPressed(View view) {
        if(searchLayout.isShown()) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Animator anim = ViewAnimationUtils.createCircularReveal(searchLayout, searchLayoutCX, searchLayoutCY, 0f, searchLayoutCX);
            searchLayout.setVisibility(View.VISIBLE);
            searchLayout.setAlpha(1);
            anim.start();
        } else {
            searchLayout.setAlpha(0);
            searchLayout.setVisibility(View.VISIBLE);
            searchLayout.animate()
                    .alpha(1)
                    .setDuration(500)
                    .start();
        }
        searchEditText.requestFocus();
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if(imm!=null) imm.showSoftInput(searchEditText, InputMethodManager.SHOW_IMPLICIT);
        SEARCH_LAYOUT_ACTIVE = true;
    }

    public void clearIconPressed(View view) {
        searchEditText.setText("");
    }

    public void backIconPressed(View view) {
        onBackPressed();
    }

    //helper methods
    public void hideKeyboard(Activity activity) {
        InputMethodManager imm = (InputMethodManager) activity.getSystemService(Activity.INPUT_METHOD_SERVICE);
        //Find the currently focused view, so we can grab the correct window token from it.
        View view = activity.getCurrentFocus();
        //If no view currently has focus, create a new one, just so we can grab a window token from it
        if (view == null) {
            view = new View(activity);
        }
        if(imm!=null) imm.hideSoftInputFromWindow(view.getWindowToken(), InputMethodManager.HIDE_NOT_ALWAYS);
        view.clearFocus();
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
                        public void run() {
                            adapter1.notifyDataSetChanged();
                            adapter2.notifyDataSetChanged();
                            adapter3.notifyDataSetChanged();
                        }
                    });
                }
                //load user image after this
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        loadUserImage();
                    }
                });
            }
        }).start();
    }

    public void writeFriendsToJson() {
        File userDir = new File(getApplicationContext().getCacheDir().getAbsolutePath() + "/users/" + user.getUid());
        boolean success = true;
        if(!userDir.exists()) success = userDir.mkdirs();
        if(!success) return;

        File friendsCache = new File(getApplicationContext().getCacheDir().getAbsolutePath() + "/users/" + user.getUid() + "/friends_cache.json");
        try {
            success = true;
            if(!friendsCache.exists()) success = friendsCache.createNewFile();
            if(!success) return;

            FileWriter fileWriter = new FileWriter(friendsCache);
            BufferedWriter bufferedWriter = new BufferedWriter(fileWriter);
            Gson gson = new Gson();
            Type friendsType = new TypeToken<ArrayList<FriendData>>() {}.getType();
            String json = gson.toJson(friends, friendsType);
            bufferedWriter.write(json);
            bufferedWriter.close();
            fileWriter.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void readFriendsFromJson() {

        File userDir = new File(getApplicationContext().getCacheDir().getAbsolutePath() + "/users/" + user.getUid());
        boolean success = true;
        if(!userDir.exists()) success = userDir.mkdirs();
        if(!success) return;

        File friendsCache = new File(getApplicationContext().getCacheDir().getAbsolutePath() + "/users/" + user.getUid() + "/friends_cache.json");
        try {
            success = true;
            if(!friendsCache.exists()) success = friendsCache.createNewFile();
            if(!success) return;

            friends.clear();
            FileReader fileReader = new FileReader(friendsCache);
            BufferedReader bufferedReader = new BufferedReader(fileReader);
            Gson gson = new Gson();
            Type friendsType = new TypeToken<ArrayList<FriendData>>() {}.getType();
            ArrayList arrayList = gson.fromJson(bufferedReader, friendsType);
            if(arrayList == null) return;
            for(Object data : arrayList)
                friends.add((FriendData) data);
            bufferedReader.close();
            fileReader.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        if(friends.size() != 0) updateUI();
    }
}
