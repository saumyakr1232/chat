package com.example.myapplication;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.constraintlayout.widget.ConstraintSet;
import androidx.core.graphics.drawable.RoundedBitmapDrawable;
import androidx.core.graphics.drawable.RoundedBitmapDrawableFactory;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.media.ThumbnailUtils;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Parcelable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.AbsoluteSizeSpan;
import android.text.style.ForegroundColorSpan;
import android.util.DisplayMetrics;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.android.material.appbar.AppBarLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public class ProfileActivity extends AppCompatActivity {
    FirebaseFirestore db = FirebaseFirestore.getInstance();
    FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
    private ConcurrentHashMap<String, Bitmap> imageCache = new ConcurrentHashMap<>();
    private List<FriendsActivity.FriendData> friends = new ArrayList<>();
    private MyAdapter adapter;

    //intent variables
    private String uid;
    private String name;
    private String status = "";
    private Bitmap image;
    private final int NEW_PROFILE_ACTIVITY_REQUEST_CODE = 0;
    private final int EDIT_PROFILE_REQUEST_CODE = 1;
    private boolean CLOSE_ALL = true;

    //main layout setup
    private AppBarLayout appBar;
    private ProgressBar progressBar;
    private TextView noFriendsMessage;
    private ListView content;
    private DisplayMetrics displayMetrics = new DisplayMetrics();
    private int STATUS_BAR_HEIGHT;

    private ConstraintLayout profileBackground;
    private int DEVICE_ORIENTATION;
    private final int DEVICE_ORIENTATION_LANDSCAPE = 0;
    private final int DEVICE_ORIENTATION_PORTRAIT = 1;
    private int COLLAPSED_HEIGHT;
    private int EXPANDED_HEIGHT;
    private int ACTION_BAR_SIZE;
    private float CURRENT_OFFSET_RATIO = 0;
    private Handler resetHeaderHandler = new Handler();
    private Handler animateHeaderHandler = new Handler();
    private Runnable resetHeader = new Runnable() {
        @Override
        public void run() {
            final float MAX_ANIM_MILLIS = 500; //time for changing offset ratio from 0 to 1
            if(CURRENT_OFFSET_RATIO > 0.3f) {
                for (int i=(int)(CURRENT_OFFSET_RATIO * MAX_ANIM_MILLIS), time=0; i<=MAX_ANIM_MILLIS; i++, time++) {
                    final int finalI = i;
                    animateHeaderHandler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() { performTranslation(finalI/MAX_ANIM_MILLIS);
                                }
                            });
                        }
                    }, time);
                }
            }
            else {
                for (int i=(int)(CURRENT_OFFSET_RATIO * MAX_ANIM_MILLIS), time=0; i>=0; i--, time++) {
                    final int finalI = i;
                    animateHeaderHandler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() { performTranslation(finalI/MAX_ANIM_MILLIS);
                                }
                            });
                        }
                    }, time);
                }
            }
        }
    };

    //profile content setup
    private ConstraintLayout profileContent;
    private ConstraintLayout profileImageLayout;
    private ConstraintLayout profileTextLayout;
    private TextView profileName;
    private TextView profileStatus;
    private ImageView profileImage;
    private boolean TOUCH_LISTENER_SET = false;
    private final float RATIO_BOOSTING_POWER_TRANSLATION = 1.35f;
    private final float RATIO_BOOSTING_POWER_IMAGE = 4f;
    private int IMAGE_MAX_MARGIN_TOP;
    private int IMAGE_MAX_MARGIN_BOTTOM;
    private int IMAGE_MAX_MARGIN_END;
    private int IMAGE_MAX_SIZE_DP = 120;
    private int IMAGE_MIN_SIZE_DP = 92;
    private int TEXT_MAX_MARGIN_TOP;
    private int TEXT_MAX_MARGIN_BOTTOM;
    private int TEXT_MAX_MARGIN_START;
    private int TEXT_MAX_MARGIN_END;

    //popup menu
    PopupMenu optionsMenu;
    ImageButton optionsButton;

    //helper classes
    class MyGestureDetector extends GestureDetector.SimpleOnGestureListener {
        @Override
        public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
            resetHeaderHandler.removeCallbacks(resetHeader);
            animateHeaderHandler.removeCallbacksAndMessages(null);
            super.onScroll(e1, e2, distanceX, distanceY);
            if(e1 == null  ||  e2 == null) return false;
            float scrollY = e2.getY() - e1.getY();
            float offsetToPerform = CURRENT_OFFSET_RATIO * (COLLAPSED_HEIGHT - EXPANDED_HEIGHT) + scrollY;
            offsetToPerform = Math.min(offsetToPerform, 0);
            offsetToPerform = Math.max(offsetToPerform, COLLAPSED_HEIGHT - EXPANDED_HEIGHT);
            float offsetRatio = offsetToPerform/(COLLAPSED_HEIGHT-EXPANDED_HEIGHT);
            performTranslation(offsetRatio);
            resetHeaderHandler.postDelayed(resetHeader, 500);
            return false;
        }
    }

    class MyAdapter extends BaseAdapter {
        private int mResource;
        private List<FriendsActivity.FriendData> data;

        MyAdapter(int resource, List<FriendsActivity.FriendData> data)
        {
            super();
            mResource = resource;
            this.data = data;
        }

        private FriendsActivity.FriendData getIem(int position) {
            return data.get(position);
        }

        @Override
        public int getCount() {
            return data.size();
        }

        @Override
        public FriendsActivity.FriendData getItem(int position) {
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
                LayoutInflater inflater = (LayoutInflater) ProfileActivity.this.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);
        setResult(RESULT_OK);

        //get intent variables
        name = getIntent().getStringExtra("name");
        uid = getIntent().getStringExtra("uid");
        image = getIntent().getParcelableExtra("image");
    }

    @Override
    protected void onStart() {
        super.onStart();

        //cache some views
        progressBar = findViewById(R.id.profileactivity_progressbar);
        noFriendsMessage = findViewById(R.id.no_friends_message);
        profileName = findViewById(R.id.profile_name);
        profileStatus = findViewById(R.id.profile_status);
        profileImage = findViewById(R.id.profile_image);
        profileContent = findViewById(R.id.profile_content);
        profileImageLayout = findViewById(R.id.profile_image_layout);
        profileTextLayout = findViewById(R.id.profile_text_layout);

        //orientation
        getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
        DEVICE_ORIENTATION =  (float)displayMetrics.widthPixels/displayMetrics.heightPixels > 0.75f ? DEVICE_ORIENTATION_LANDSCAPE : DEVICE_ORIENTATION_PORTRAIT;

        //set the profile header height
        profileBackground = findViewById(R.id.profile_background);
        ConstraintSet constraintSet = new ConstraintSet();
        constraintSet.clone(profileBackground);
        constraintSet.setDimensionRatio(R.id.profile_background_image, DEVICE_ORIENTATION == DEVICE_ORIENTATION_LANDSCAPE ? "4" : "1.125");
        constraintSet.applyTo(profileBackground);

        //calculate the different header heights
        profileBackground.post(new Runnable() {
            @Override
            public void run() {
                CURRENT_OFFSET_RATIO = 0;
                STATUS_BAR_HEIGHT = getStatusBarHeightPx();
                EXPANDED_HEIGHT = profileBackground.getHeight();
                COLLAPSED_HEIGHT = (int) Math.min(EXPANDED_HEIGHT, 192 * displayMetrics.density + STATUS_BAR_HEIGHT);
                IMAGE_MIN_SIZE_DP = DEVICE_ORIENTATION == DEVICE_ORIENTATION_PORTRAIT ? 92 : (int) (((COLLAPSED_HEIGHT - STATUS_BAR_HEIGHT - ACTION_BAR_SIZE) / displayMetrics.density) - 72);
                ACTION_BAR_SIZE = appBar.getHeight();
            }
        });

        //set profile and header
        TOUCH_LISTENER_SET = false;
        setProfile();
        setupHeader();

        //disable app bar border on api >= 21
        appBar = findViewById(R.id.profileactivity_fixed_appbar);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            appBar.setOutlineProvider(null);
        }

        //set up list view
        content = findViewById(R.id.content);
        adapter = new MyAdapter(R.layout.friend_layout_profile, friends);
        content.setAdapter(adapter);

        //set up options menu
        optionsButton = findViewById(R.id.imageButton_options);
        optionsMenu = new PopupMenu(this, optionsButton);
        optionsMenu.getMenuInflater().inflate(R.menu.profile_options, optionsMenu.getMenu());
        //style menu item text
        for (int i = 0; i < optionsMenu.getMenu().size(); i++) {
            MenuItem item = optionsMenu.getMenu().getItem(i);
            SpannableString itemText = new SpannableString(item.getTitle().toString());
            itemText.setSpan(new AbsoluteSizeSpan(40), 0, itemText.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            itemText.setSpan(new ForegroundColorSpan(Color.BLACK), 0, itemText.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            item.setTitle(itemText);
        }
        //setup menu item click listener
        optionsMenu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                switch (item.getItemId()) {
                    case R.id.profile_edit: editProfile(); break;
                    case R.id.profile_connect: profileConnect(); break;
                }
                return false;
            }
        });
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void onResume() {
        super.onResume();

        //set content
        readImageCache();
        if(friends.size() == 0) getFriends();
        else updateUI();

    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        outState.putParcelableArrayList("friends", (ArrayList<? extends Parcelable>) friends);
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onRestoreInstanceState(@NonNull Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);

        //restore friends
        friends.clear();
        ArrayList<FriendsActivity.FriendData> friendsTemp = savedInstanceState.getParcelableArrayList("friends");
        if(friendsTemp!=null) friends.addAll(friendsTemp);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if(requestCode == NEW_PROFILE_ACTIVITY_REQUEST_CODE  &&  resultCode == RESULT_CANCELED) {
            setResult(RESULT_CANCELED);
            finish();
        }
        if(requestCode == EDIT_PROFILE_REQUEST_CODE  &&  resultCode == RESULT_OK  &&  data != null)
        {
            name = data.getStringExtra("name");
            status = data.getStringExtra("status");
            image = data.getParcelableExtra("image");
        }
        //reset image let it load
        image = null;
    }

    @Override
    public void onBackPressed() {
        if(CLOSE_ALL) setResult(RESULT_CANCELED);
        super.onBackPressed();
    }

    //back button setup
    public void onBackPressed(View view) {
        CLOSE_ALL = false;
        onBackPressed();
    }

    //loading friends
    public void getFriends() {
        noFriendsMessage.setVisibility(View.INVISIBLE);
        progressBar.setVisibility(View.VISIBLE);

        db.collection("users").document(Objects.requireNonNull(getIntent().getStringExtra("uid"))).collection("friends").get()
                .addOnCompleteListener(new OnCompleteListener<QuerySnapshot>() {
                    @Override
                    public void onComplete(@NonNull Task<QuerySnapshot> task) {
                        if (task.isSuccessful() && task.getResult() != null) {
                            int NUMBER_OF_FRIENDS = 0;
                            friends.clear();
                            for (final QueryDocumentSnapshot friend : task.getResult())
                            {
                                final FriendsActivity.FriendData friendData = new FriendsActivity.FriendData();
                                friendData.name = "Unable to load name";
                                friendData.uid = friend.getId();
                                friendData.status = friend.getString("status");

                                //only filter those who are friends
                                if(friendData.status == null) friendData.status = "";
                                if(!friendData.status.equals(getString(R.string.myapp_friend_status_0))) continue;

                                NUMBER_OF_FRIENDS++;
                                db.collection("users").document(friend.getId()).get()
                                        .addOnCompleteListener(new OnCompleteListener<DocumentSnapshot>() {
                                            @Override
                                            public void onComplete(@NonNull final Task<DocumentSnapshot> task)
                                            {
                                                if(task.isSuccessful() && task.getResult()!=null)
                                                {
                                                    friendData.name = task.getResult().getString("name");

                                                    //add friend logo to Map
                                                    if (!imageCache.containsKey(friend.getId()))
                                                    {
                                                        new Thread(new Runnable() {
                                                            @Override
                                                            public void run() {
                                                                try {
                                                                    URL imageURL = new URL(task.getResult().getString("image"));
                                                                    Bitmap profile_pic_raw = BitmapFactory.decodeStream(imageURL.openConnection().getInputStream());
                                                                    Bitmap profile_pic = scaleBitmap(profile_pic_raw);
                                                                    imageCache.put(friend.getId(),profile_pic);
                                                                    updateUI();
                                                                } catch (Exception e) {
                                                                    e.printStackTrace();
                                                                }
                                                            }
                                                        }).start();
                                                    }
                                                }
                                                //add friend to List
                                                friends.add(friendData);
                                                updateUI();
                                            }
                                        });
                            }
                            if(NUMBER_OF_FRIENDS==0) updateUI();
                        } else
                            updateUI();
                    }
                });
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

    public Bitmap scaleBitmap(Bitmap input) {
        Bitmap output;
        int dimension = Math.min(input.getWidth(), input.getHeight());
        output = ThumbnailUtils.extractThumbnail(input, dimension, dimension);
        return Bitmap.createScaledBitmap(output, 144, 144, true);
    }

    public void updateUI(){
        progressBar.setVisibility(View.INVISIBLE);
        if(friends.size() == 0) noFriendsMessage.setVisibility(View.VISIBLE);
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                adapter.notifyDataSetChanged();
            }
        });
    }

    //set profile details
    private void setProfile(){
        //set name
        profileName.setText(name);

        //load status text
        if(status.equals(""))
            loadUserStatus(uid);
        else {
            //must set initial margins on text change
            profileStatus.setAlpha(0);
            profileStatus.setText(status);
            profileStatus.animate()
                    .alpha(1)
                    .setDuration(200)
                    .start();
            setupHeader();
        }

        //set image
        if(image != null) {
            RoundedBitmapDrawable roundedProfileImage = RoundedBitmapDrawableFactory.create(getResources(), image);
            roundedProfileImage.setCircular(true);
            roundedProfileImage.setAntiAlias(true);
            profileImage.setImageDrawable(roundedProfileImage);
        }
        else loadUserImage(uid);
    }

    private void loadUserStatus(String uid) {
        db.document("users/" + uid).get()
                .addOnSuccessListener(new OnSuccessListener<DocumentSnapshot>() {
                    @Override
                    public void onSuccess(DocumentSnapshot documentSnapshot) {
                        status = documentSnapshot.getString("status");
                        if(status == null  ||  status.equals(""))
                            status = getString(R.string.myapp_profile_status_default);
                        profileStatus.setAlpha(0);
                        profileStatus.setText(status);
                        profileStatus.animate()
                                .alpha(1)
                                .setDuration(200)
                                .start();
                        setupHeader();
                    }
                }).addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception e) {
                status = getString(R.string.myapp_profile_status_default);
                profileStatus.setAlpha(0);
                profileStatus.setText(status);
                profileStatus.animate()
                        .alpha(1)
                        .setDuration(200)
                        .start();
                setupHeader();
            }
        });
    }

    private void loadUserImage(final String uid) {
        profileImage.setImageDrawable(getResources().getDrawable(R.drawable.ic_account_circle_white_24dp));

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
                                        final RoundedBitmapDrawable roundedProfilePic = RoundedBitmapDrawableFactory.create(getResources(), profilePic);
                                        roundedProfilePic.setCircular(true);
                                        roundedProfilePic.setAntiAlias(true);
                                        profileImage.post(new Runnable() {
                                            @Override
                                            public void run() { profileImage.setImageDrawable(roundedProfilePic); }
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

    //header animation
    @SuppressLint("ClickableViewAccessibility")
    private void setupHeader() {
        if(DEVICE_ORIENTATION == DEVICE_ORIENTATION_PORTRAIT) {
            //set the profile content margins
            profileContent.post(new Runnable() {
                @Override
                public void run() {
                    setInitialMarginsPortrait();
                    //setup the scroll behaviour
                    if(!TOUCH_LISTENER_SET) {
                        final GestureDetector gestureDetector = new GestureDetector(new MyGestureDetector());
                        View.OnTouchListener gestureListener = new View.OnTouchListener() {
                            @Override
                            public boolean onTouch(View v, MotionEvent event) {
                                return gestureDetector.onTouchEvent(event);
                            }
                        };
                        content.setOnTouchListener(gestureListener);
                        TOUCH_LISTENER_SET = true;
                    }
                }
            });
        }
        else {
            profileContent.post(new Runnable() {
                @Override
                public void run() {
                    setMarginsLandscape();
                }
            });
        }
    }

    private void setMarginsLandscape() {
        //fix status bar margins
        if(Build.VERSION.SDK_INT >= 21) {
            RelativeLayout.LayoutParams profileContentParams = (RelativeLayout.LayoutParams) profileContent.getLayoutParams();
            profileContentParams.topMargin = STATUS_BAR_HEIGHT + ACTION_BAR_SIZE;
            profileContent.setLayoutParams(profileContentParams);

            ConstraintLayout.LayoutParams appBarParams = (ConstraintLayout.LayoutParams) appBar.getLayoutParams();
            appBarParams.topMargin = STATUS_BAR_HEIGHT;
            appBar.setLayoutParams(appBarParams);
        }

        IMAGE_MAX_MARGIN_END = (int)(displayMetrics.widthPixels - ((48 + IMAGE_MIN_SIZE_DP) * displayMetrics.density));
        TEXT_MAX_MARGIN_START = (int)((40 + IMAGE_MIN_SIZE_DP) * displayMetrics.density);
        TEXT_MAX_MARGIN_END = (int)(24 * displayMetrics.density);

        //set image constraints
        ConstraintLayout.LayoutParams imageLayoutParams = (ConstraintLayout.LayoutParams) profileImageLayout.getLayoutParams();
        imageLayoutParams.horizontalChainStyle = ConstraintLayout.LayoutParams.CHAIN_SPREAD;
        profileImageLayout.setLayoutParams(imageLayoutParams);
        //set image size
        ConstraintLayout.LayoutParams imageParams = (ConstraintLayout.LayoutParams) profileImage.getLayoutParams();
        imageParams.width = imageParams.height = (int) (IMAGE_MIN_SIZE_DP * displayMetrics.density);
        profileImage.setLayoutParams(imageParams);
        //set text constraints
        ConstraintLayout.LayoutParams textLayoutParams = (ConstraintLayout.LayoutParams) profileTextLayout.getLayoutParams();
        textLayoutParams.horizontalChainStyle = ConstraintLayout.LayoutParams.CHAIN_SPREAD;
        profileTextLayout.setLayoutParams(textLayoutParams);

        //connect the layouts
        ConstraintSet profileConstraintSet = new ConstraintSet();
        profileConstraintSet.clone(profileContent);
        profileConstraintSet.connect(R.id.profile_image_layout, ConstraintSet.END, R.id.profile_text_layout, ConstraintSet.START);
        profileConstraintSet.connect(R.id.profile_text_layout, ConstraintSet.START, R.id.profile_image_layout, ConstraintSet.END);
        profileConstraintSet.applyTo(profileContent);

        profileContent.setVisibility(View.VISIBLE);
    }

    private void setInitialMarginsPortrait() {
        //fix status bar margins
        if (Build.VERSION.SDK_INT >= 21) {
            RelativeLayout.LayoutParams profileContentParams = (RelativeLayout.LayoutParams) profileContent.getLayoutParams();
            profileContentParams.topMargin = STATUS_BAR_HEIGHT + ACTION_BAR_SIZE;
            profileContent.setLayoutParams(profileContentParams);

            ConstraintLayout.LayoutParams appBarParams = (ConstraintLayout.LayoutParams) appBar.getLayoutParams();
            appBarParams.topMargin = STATUS_BAR_HEIGHT;
            appBar.setLayoutParams(appBarParams);
        }

        //obtain height values
        float profileContentExpandedHeight = EXPANDED_HEIGHT - ACTION_BAR_SIZE - (24 * displayMetrics.density) - STATUS_BAR_HEIGHT;
        float profileTextNameHeight = profileName.getHeight();
        float profileTextStatusHeight = profileStatus.getHeight();
        float profileImageHeight = IMAGE_MAX_SIZE_DP * displayMetrics.density;
        float spaceBetweenImageAndText = 16 * displayMetrics.density;

        float profileContentInitialHeight = profileImageHeight + spaceBetweenImageAndText + profileTextNameHeight + profileTextStatusHeight;

        IMAGE_MAX_MARGIN_TOP = (int)((profileContentExpandedHeight - profileContentInitialHeight)/2);
        IMAGE_MAX_MARGIN_BOTTOM = (int)(profileContentExpandedHeight - (IMAGE_MAX_MARGIN_TOP + profileImageHeight));
        TEXT_MAX_MARGIN_TOP = (int)(IMAGE_MAX_MARGIN_TOP + profileImageHeight + spaceBetweenImageAndText);
        TEXT_MAX_MARGIN_BOTTOM = (int)((profileContentExpandedHeight - profileContentInitialHeight)/2);

        IMAGE_MAX_MARGIN_END = (int)(displayMetrics.widthPixels - ((48 + IMAGE_MIN_SIZE_DP) * displayMetrics.density));
        TEXT_MAX_MARGIN_START = (int)((40 + IMAGE_MIN_SIZE_DP) * displayMetrics.density);
        TEXT_MAX_MARGIN_END = (int)(24 * displayMetrics.density);

        performTranslation(CURRENT_OFFSET_RATIO);
        profileContent.setVisibility(View.VISIBLE);
    }

    private int getStatusBarHeightPx() {
        int result = 0;
        int resourceId = getResources().getIdentifier("status_bar_height", "dimen", "android");
        if (resourceId > 0) {
            result = getResources().getDimensionPixelSize(resourceId);
        }
        return result;
    }

    private void performTranslation(float offsetRatio) {
        //offset the header
        RelativeLayout.LayoutParams headerBackgroundParams = (RelativeLayout.LayoutParams) profileBackground.getLayoutParams();
        headerBackgroundParams.topMargin = (int) (offsetRatio * (COLLAPSED_HEIGHT - EXPANDED_HEIGHT));
        profileBackground.setLayoutParams(headerBackgroundParams);

        //offset the image
        ConstraintLayout.LayoutParams imageLayoutParams = (ConstraintLayout.LayoutParams) profileImageLayout.getLayoutParams();
        imageLayoutParams.topMargin = (int) ((1 - offsetRatio) * IMAGE_MAX_MARGIN_TOP);
        imageLayoutParams.bottomMargin = (int) (obtainDeceleratedRatio(1-offsetRatio) * IMAGE_MAX_MARGIN_BOTTOM);
        imageLayoutParams.setMarginEnd((int) (obtainDeceleratedRatio(offsetRatio) * IMAGE_MAX_MARGIN_END));
        profileImageLayout.setLayoutParams(imageLayoutParams);

        //transform image size
        ConstraintLayout.LayoutParams imageParams = (ConstraintLayout.LayoutParams) profileImage.getLayoutParams();
        imageParams.height = imageParams.width = (int)((IMAGE_MIN_SIZE_DP + (obtainAcceleratedRatio(1-offsetRatio, RATIO_BOOSTING_POWER_IMAGE) * (IMAGE_MAX_SIZE_DP - IMAGE_MIN_SIZE_DP))) * displayMetrics.density);
        profileImage.setLayoutParams(imageParams);

        //offset the text
        ConstraintLayout.LayoutParams textLayoutParams = (ConstraintLayout.LayoutParams) profileTextLayout.getLayoutParams();
        textLayoutParams.topMargin = (int) (obtainDeceleratedRatio(1-offsetRatio) * TEXT_MAX_MARGIN_TOP);
        textLayoutParams.bottomMargin = (int) ((1 - offsetRatio) * TEXT_MAX_MARGIN_BOTTOM);
        textLayoutParams.setMarginStart((int) (obtainDeceleratedRatio(offsetRatio) * TEXT_MAX_MARGIN_START));
        textLayoutParams.setMarginEnd((int) (offsetRatio * TEXT_MAX_MARGIN_END));
        profileTextLayout.setLayoutParams(textLayoutParams);

        //set the new offset ratio
        CURRENT_OFFSET_RATIO = offsetRatio;
    }

    private double obtainAcceleratedRatio(float ratio, float power) {
        return Math.pow(ratio, power);
    }

    private double obtainDeceleratedRatio(float ratio) {
        return 1 - Math.pow(1-ratio, RATIO_BOOSTING_POWER_TRANSLATION);
    }

    //options menu
    public void profileOptionsPressed(View view) {
        //prepare menu before showing
        Menu menu = optionsMenu.getMenu();
        SpannableString itemText;
        if (uid.equals(user.getUid())) {
            MenuItem profileConnect = menu.findItem(R.id.profile_connect);
            profileConnect.setEnabled(false);
            itemText = new SpannableString(profileConnect.getTitle().toString());
            itemText.setSpan(new ForegroundColorSpan(getResources().getColor(R.color.colorBlackLight)), 0, itemText.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            profileConnect.setTitle(itemText);

            MenuItem editProfile = menu.findItem(R.id.profile_edit);
            editProfile.setEnabled(true);
            itemText = new SpannableString(editProfile.getTitle().toString());
            itemText.setSpan(new ForegroundColorSpan(Color.BLACK), 0, itemText.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            editProfile.setTitle(itemText);
        } else {
            MenuItem editProfile = menu.findItem(R.id.profile_edit);
            editProfile.setEnabled(false);
            itemText = new SpannableString(editProfile.getTitle().toString());
            itemText.setSpan(new ForegroundColorSpan(getResources().getColor(R.color.colorBlackLight)), 0, itemText.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            editProfile.setTitle(itemText);

            MenuItem profileConnect = menu.findItem(R.id.profile_connect);
            profileConnect.setEnabled(true);
            itemText = new SpannableString(profileConnect.getTitle().toString());
            itemText.setSpan(new ForegroundColorSpan(Color.BLACK), 0, itemText.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            profileConnect.setTitle(itemText);
        }

        optionsMenu.show();
    }

    public void editProfile() {
        Intent intent = new Intent(this, EditProfileActivity.class);
        intent.putExtra("name", name);
        intent.putExtra("email", user.getEmail());
        intent.putExtra("uid", uid);
        intent.putExtra("status", status);
        intent.putExtra("image", image);
        startActivityForResult(intent, EDIT_PROFILE_REQUEST_CODE);
    }

    public void profileConnect() {

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
        startActivityForResult(intent, NEW_PROFILE_ACTIVITY_REQUEST_CODE);
    }
}