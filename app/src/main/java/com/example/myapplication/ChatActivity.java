package com.example.myapplication;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.UriPermission;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.media.ThumbnailUtils;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;

import com.example.myapplication.graphics.CustomRoundedBitmapDrawable;
import com.example.myapplication.graphics.CustomRoundedBitmapDrawableFactory;
import com.example.myapplication.views.MessageListView;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.MetadataChanges;
import com.google.firebase.firestore.Transaction;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.StreamDownloadTask;
import com.google.firebase.storage.UploadTask;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.core.graphics.drawable.RoundedBitmapDrawable;
import androidx.core.graphics.drawable.RoundedBitmapDrawableFactory;

import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.StrictMode;
import android.os.storage.StorageManager;
import android.provider.OpenableColumns;
import android.text.Layout;
import android.text.Selection;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.method.BaseMovementMethod;
import android.text.style.AbsoluteSizeSpan;
import android.text.style.ClickableSpan;
import android.text.style.ForegroundColorSpan;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.TouchDelegate;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.MimeTypeMap;
import android.widget.AbsListView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.documentfile.provider.DocumentFile;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Timer;
import java.util.TimerTask;

public class ChatActivity extends AppCompatActivity {
    private final FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private final FirebaseStorage storage = FirebaseStorage.getInstance();
    private SQLiteDatabase sqlDB;
    private MessageListView messageListView;
    private final Object syncObj = new Object();

    //messages
    private String DB_PATH;
    private final String DB_NAME = "chat.db";
    private String userUid, friendUid;
    private MyAdapter adapter;
    private boolean CHAT_IS_LOADING = false;
    private String DATE_LAST_CHAT;
    private String DATE_OLDEST_CHAT = "";
    @SuppressWarnings("FieldCanBeLocal")
    private final int INITIAL_MESSAGE_SIZE = 100;
    private final int VERTICAL_SCROLL_OFFSET_LIMIT = 500;
    private boolean CAN_LOAD_MORE_MESSAGES = true;
    private boolean LOADING_PAST_CHAT_FOR_FIRST_TIME;
    private int SELECTED_ITEMS = 0;
    private final List<Message> messages = new ArrayList<>();
    private final ArrayList<String> selectedMessages = new ArrayList<>(50);
    private final ArrayList<View> selectedMessageViews = new ArrayList<>(50);
    private final ArrayList<String> syncCacheMessageId = new ArrayList<>();

    //gestures
    private View VIEW_BEING_TOUCHED;
    private final int SWIPE_THRESHOLD = 150;
    private final int LONG_PRESS_MOVEMENT_ALLOWANCE = 5;
    private boolean LONG_PRESS_MODE = false;
    private int startX, startY, translationX;
    private boolean click = false;

    //date
    private final Handler dateHandler = new Handler();
    private final Runnable resetDateDrop = new Runnable() {
        @Override
        public void run() {
            findViewById(R.id.chat_date_text).animate()
                    .translationY(0)
                    .setDuration(100)
                    .start();
        }
    };

    //file
    private final String[] IMAGE_FILE_EXTENSIONS = {".jpg", ".jpeg", ".jpe", ".jif", ".jfif", ".jfi", ".png", ".gif", ".webp", ".tiff", ".tif", ".psd", ".raw", ".arw", ".cr2", ".nrw", ".k25",
            ".bmp", ".dib", ".heif", ".heic", ".ind", ".indd", ".indt", ".jp2", ".j2k", ".jpf", ".jpx", ".jpm", ".mj2", ".svg", ".svgz", ".ai", ".eps"};
    private final Map<String, CustomRoundedBitmapDrawable> imageCache = new HashMap<>();
    private final int REQUEST_WRITE_PERMISSION = 0;
    private final int ATTACH_FILE_CODE = 1;
    private final int REQUEST_WRITE_PERMISSION_Q = 2;
    private DocumentFile appStorageDir;
    private final String STORAGE_DIRECTORY_URI = "STORAGE_DIRECTORY_URI";
    private SharedPreferences sharedPreferences;
    private final int IMAGE_PREVIEW_SIZE = 144;
    private final ArrayList<String> uploadCacheMessageId = new ArrayList<>();
    private final ArrayList<UploadTask> uploadTasks = new ArrayList<>();

    //popup menu
    private PopupMenu optionsMenu;

    //main UI
    private ImageView chatFriendIcon;
    private EditText chatbox;
    private TextView chatboxReply;
    private View chatboxReplyBorder;
    private TextView dateView;
    private Bitmap chatFriendIconCache;

    //helper classes
    class SyncMessages extends TimerTask{
        @Override
        public void run() {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    syncAllMessages();
                }
            });
        }
    }

    public static class Message {
        public String text;
        public String date;
        public String time;
        public String metadata;
        public String reply;
        public String reply_metadata;
        public String content;
        public String rowid;

        Message()
        {
            this.text = "";
            this.date = "";
            this.time = "";
            this.metadata = "";
            this.reply = "";
            this.reply_metadata = "";
            this.content = "";
            this.rowid = "";
        }

        @Override
        public boolean equals(@Nullable Object obj) {
            if(obj == null) return false;
            if(!Message.class.isAssignableFrom(obj.getClass())) return false;
            Message message = (Message) obj;
            boolean result = true;
            if(!this.text.equals(message.text)) result = false;
            if(!this.date.equals(message.date)) result = false;
            if(!this.time.equals(message.time)) result = false;
            if(!this.metadata.equals(message.metadata)) result = false;
            if(!this.reply_metadata.equals(message.reply_metadata)) result = false;
            if(!this.reply.equals(message.reply)) result = false;
            if(!this.content.equals(message.content)) result = false;
            if(!this.rowid.equals(message.rowid)) result = false;
            return result;
        }
    }

    class MyAdapter extends ArrayAdapter<Message> {
        private final LayoutInflater inflater;

        private final int ITEM_TYPE_MESSAGE_TEXT_SENT = 4;
        private final int ITEM_TYPE_MESSAGE_TEXT_RECEIVED = 3;
        private final int ITEM_TYPE_MESSAGE_FILE_SENT = 2;
        private final int ITEM_TYPE_MESSAGE_FILE_RECEIVED = 1;
        private final int ITEM_TYPE_MESSAGE_DATE = 0;

        private final TextView noChatsIndicator;

        class MessageHolder
        {
            ConstraintLayout messageContainer;
            TextView messageDate;
            TextView messageTime;
            TextView messageId;
            TextView messageMetadata;
            TextView messageReply;
            TextView messageText;
            View messageTriangle;
            View messageReplyBorder;
            View messagePadding;
            RelativeLayout messageFileContainer;
            TextView messageFileUri;
            ImageView messageFilePreview;
            ImageView messageFileStatus;
            ProgressBar messageFileStatusProgressBar;
        }

        MyAdapter(Context context, List<Message> data) {
            super(context, 0, data);
            this.inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            this.noChatsIndicator = findViewById(R.id.no_chats_indicator);
        }

        @Override
        public int getItemViewType(int position)
        {
            Message item = getItem(position);
            if (item != null) {
                switch (item.metadata)
                {
                    case "date": return ITEM_TYPE_MESSAGE_DATE;
                    case "sending":
                    case "sent": {
                        if(item.content == null  ||  item.content.equals(""))
                            return ITEM_TYPE_MESSAGE_TEXT_SENT;
                        else
                            return ITEM_TYPE_MESSAGE_FILE_SENT;
                    }
                    case "received": {
                        if(item.content == null  ||  item.content.equals(""))
                            return ITEM_TYPE_MESSAGE_TEXT_RECEIVED;
                        else
                            return ITEM_TYPE_MESSAGE_FILE_RECEIVED;
                    }
                }
            }
            return -1;
        }

        @Override
        public void add(@Nullable Message object) {
            checkOnMainThread();
            super.add(object);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public int getViewTypeCount() {
            return 5;
        }

        @Override
        public void notifyDataSetChanged() {
            super.notifyDataSetChanged();
            if(getCount()==0) {
                noChatsIndicator.setVisibility(View.VISIBLE);
            }
            else {
                noChatsIndicator.setVisibility(View.INVISIBLE);
            }

        }

        @SuppressLint("ClickableViewAccessibility")
        @NonNull
        @Override
        public View getView(final int position, View convertView, @NonNull ViewGroup parent)
        {
            MessageHolder holder;
            if(convertView == null)
            {
                holder = new MessageHolder();
                switch (getItemViewType(position))
                {
                    case ITEM_TYPE_MESSAGE_DATE: {
                        convertView = inflater.inflate(R.layout.message_date, parent, false);
                        holder.messageDate = convertView.findViewById(R.id.message_date_text);
                        convertView.setTag(holder);
                        break;
                    }
                    case ITEM_TYPE_MESSAGE_TEXT_RECEIVED: {
                        convertView = inflater.inflate(R.layout.message_layout_received, parent, false);
                        holder.messageContainer = convertView.findViewById(R.id.message_container);
                        holder.messageDate = convertView.findViewById(R.id.message_date);
                        holder.messageTime = convertView.findViewById(R.id.message_time);
                        holder.messageId = convertView.findViewById(R.id.message_id);
                        holder.messageMetadata = convertView.findViewById(R.id.message_metadata);
                        holder.messageReply = convertView.findViewById(R.id.message_textview_reply);
                        holder.messageText = convertView.findViewById(R.id.message_textview);
                        holder.messageTriangle = convertView.findViewById(R.id.message_triangle);
                        holder.messageReplyBorder = convertView.findViewById(R.id.message_reply_border);
                        holder.messagePadding = convertView.findViewById(R.id.message_padding);
                        convertView.setTag(holder);
                        break;
                    }
                    case ITEM_TYPE_MESSAGE_FILE_RECEIVED: {
                        convertView = inflater.inflate(R.layout.message_file_received, parent, false);
                        holder.messageContainer = convertView.findViewById(R.id.message_container);
                        holder.messageDate = convertView.findViewById(R.id.message_date);
                        holder.messageTime = convertView.findViewById(R.id.message_time);
                        holder.messageId = convertView.findViewById(R.id.message_id);
                        holder.messageMetadata = convertView.findViewById(R.id.message_metadata);
                        holder.messageReply = convertView.findViewById(R.id.message_textview_reply);
                        holder.messageText = convertView.findViewById(R.id.message_textview);
                        holder.messageTriangle = convertView.findViewById(R.id.message_triangle);
                        holder.messageReplyBorder = convertView.findViewById(R.id.message_reply_border);
                        holder.messagePadding = convertView.findViewById(R.id.message_padding);
                        holder.messageFileContainer = convertView.findViewById(R.id.message_file_container);
                        holder.messageFileUri = convertView.findViewById(R.id.message_file_uri);
                        holder.messageFilePreview = convertView.findViewById(R.id.message_file_preview);
                        holder.messageFileStatus = convertView.findViewById(R.id.message_file_status);
                        holder.messageFileStatusProgressBar = convertView.findViewById(R.id.message_file_status_progressbar);
                        convertView.setTag(holder);
                        break;
                    }
                    case ITEM_TYPE_MESSAGE_TEXT_SENT: {
                        convertView = inflater.inflate(R.layout.message_layout_sent, parent, false);
                        holder.messageContainer = convertView.findViewById(R.id.message_container);
                        holder.messageDate = convertView.findViewById(R.id.message_date);
                        holder.messageTime = convertView.findViewById(R.id.message_time);
                        holder.messageId = convertView.findViewById(R.id.message_id);
                        holder.messageMetadata = convertView.findViewById(R.id.message_metadata);
                        holder.messageReply = convertView.findViewById(R.id.message_textview_reply);
                        holder.messageText = convertView.findViewById(R.id.message_textview);
                        holder.messageTriangle = convertView.findViewById(R.id.message_triangle);
                        holder.messageReplyBorder = convertView.findViewById(R.id.message_reply_border);
                        holder.messagePadding = convertView.findViewById(R.id.message_padding);
                        convertView.setTag(holder);
                        break;
                    }
                    case ITEM_TYPE_MESSAGE_FILE_SENT: {
                        convertView = inflater.inflate(R.layout.message_file_sent, parent, false);
                        holder.messageContainer = convertView.findViewById(R.id.message_container);
                        holder.messageDate = convertView.findViewById(R.id.message_date);
                        holder.messageTime = convertView.findViewById(R.id.message_time);
                        holder.messageId = convertView.findViewById(R.id.message_id);
                        holder.messageMetadata = convertView.findViewById(R.id.message_metadata);
                        holder.messageReply = convertView.findViewById(R.id.message_textview_reply);
                        holder.messageText = convertView.findViewById(R.id.message_textview);
                        holder.messageTriangle = convertView.findViewById(R.id.message_triangle);
                        holder.messageReplyBorder = convertView.findViewById(R.id.message_reply_border);
                        holder.messagePadding = convertView.findViewById(R.id.message_padding);
                        holder.messageFileContainer = convertView.findViewById(R.id.message_file_container);
                        holder.messageFileUri = convertView.findViewById(R.id.message_file_uri);
                        holder.messageFilePreview = convertView.findViewById(R.id.message_file_preview);
                        holder.messageFileStatus = convertView.findViewById(R.id.message_file_status);
                        holder.messageFileStatusProgressBar = convertView.findViewById(R.id.message_file_status_progressbar);
                        convertView.setTag(holder);
                        break;
                    }
                    default: return new View(getApplicationContext());
                }
                if(getItemViewType(position) != ITEM_TYPE_MESSAGE_DATE)
                {
                    //setup touch listeners
                    convertView.setOnTouchListener(new View.OnTouchListener() {
                        int USER_IS_SWIPING = 0;

                        @Override
                        public boolean onTouch(View v, MotionEvent event) {
                            v.onTouchEvent(event);
                            switch (event.getAction() & MotionEvent.ACTION_MASK) {
                                case MotionEvent.ACTION_DOWN: {
                                    if (VIEW_BEING_TOUCHED != null) break;
                                    VIEW_BEING_TOUCHED = ((MessageHolder) v.getTag()).messageContainer;
                                    VIEW_BEING_TOUCHED.setTag(null);
                                    USER_IS_SWIPING = 0;
                                    startX = (int) event.getX();
                                    startY = (int) event.getY();
                                    click = true;
                                    break;
                                }
                                case MotionEvent.ACTION_UP: {
                                    if (click && VIEW_BEING_TOUCHED.getTag() == null) {
                                        //single click
                                        //Toast.makeText(ChatActivity.this, "short", Toast.LENGTH_SHORT).show();
                                        if (LONG_PRESS_MODE) {
                                            if (VIEW_BEING_TOUCHED.isSelected()) {
                                                VIEW_BEING_TOUCHED.setSelected(false);
                                                SELECTED_ITEMS--;
                                                selectedMessages.remove(((TextView) VIEW_BEING_TOUCHED.findViewById(R.id.message_id)).getText().toString());
                                                selectedMessageViews.remove(VIEW_BEING_TOUCHED);
                                                if (SELECTED_ITEMS == 0) {
                                                    LONG_PRESS_MODE = false;
                                                    invalidateOptionsMenu();
                                                }
                                            } else {
                                                VIEW_BEING_TOUCHED.setSelected(true);
                                                SELECTED_ITEMS++;
                                                selectedMessageViews.add(VIEW_BEING_TOUCHED);
                                                selectedMessages.add(((TextView) VIEW_BEING_TOUCHED.findViewById(R.id.message_id)).getText().toString());
                                            }
                                        }
                                        else
                                        {
                                            MessageHolder messageHolder = (MessageHolder)v.getTag();
                                            if(messageHolder.messageFilePreview != null) {
                                                int[] location = new int[2];
                                                messageHolder.messageFilePreview.getLocationOnScreen(location);
                                                Rect imageArea = new Rect(location[0], location[1], location[0] + messageHolder.messageFilePreview.getWidth(), location[1] + messageHolder.messageFilePreview.getHeight());
                                                Rect clickRect = new Rect(Math.min(location[0], (int) event.getRawX()), location[1], Math.max(location[0], (int) event.getRawX()), (int)event.getRawY());
                                                if(imageArea.contains(clickRect))
                                                {
                                                    //single click on file message
                                                    Uri fileUri = Uri.parse(messageHolder.messageFileUri.getText().toString());

                                                    if(fileUri.getScheme() != null  &&  (fileUri.getScheme().equals("http")  ||  fileUri.getScheme().equals("https"))) {
                                                        //file needs to be downloaded
                                                        downloadFile(messageHolder);
                                                    }
                                                    else if(messageHolder.messageMetadata.getText().toString().equals("sending")) {
                                                        //file needs to be uploaded
                                                        uploadFile(messageHolder);
                                                    }
                                                    else {
                                                        //file is downloaded
                                                        Uri finalFileUri;
                                                        if(fileUri.getScheme() != null  &&  fileUri.getScheme().equals("file")  &&  Build.VERSION.SDK_INT >= 24) {
                                                            if(fileUri.getPath() == null)
                                                                finalFileUri = Uri.parse("");
                                                            else
                                                                finalFileUri = FileProvider.getUriForFile(ChatActivity.this, BuildConfig.APPLICATION_ID + ".fileProvider", new File(fileUri.getPath()));
                                                        }
                                                        else
                                                            finalFileUri = fileUri;
                                                        Intent openFile = new Intent(Intent.ACTION_VIEW);
                                                        openFile.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                                                        openFile.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                                                        openFile.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
                                                        MimeTypeMap mimeTypeMap = MimeTypeMap.getSingleton();
                                                        String fileName = getFileName(finalFileUri);
                                                        String mimeType = mimeTypeMap.getMimeTypeFromExtension(fileName.substring(fileName.lastIndexOf('.') + 1));
                                                        try {
                                                            openFile.setDataAndType(finalFileUri, mimeType);
                                                            startActivity(openFile);
                                                        } catch (Exception e) {
                                                            Toast.makeText(ChatActivity.this, "Couldn't open file", Toast.LENGTH_LONG).show();
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    if (translationX > SWIPE_THRESHOLD  &&  !LONG_PRESS_MODE  &&  USER_IS_SWIPING == 1)
                                        replyMessageSelected((View)VIEW_BEING_TOUCHED.getParent());
                                    translationX = 0;
                                    click = false;
                                    VIEW_BEING_TOUCHED.setTranslationX(0);
                                    VIEW_BEING_TOUCHED = null;
                                    USER_IS_SWIPING = 0;
                                    break;
                                }
                                case MotionEvent.ACTION_CANCEL: {
                                    translationX = 0;
                                    click = false;
                                    VIEW_BEING_TOUCHED.setTranslationX(0);
                                    VIEW_BEING_TOUCHED = null;
                                    break;
                                }
                                case MotionEvent.ACTION_MOVE: {
                                    click = false;
                                    int dX = (int) (event.getX() - startX);
                                    int dY = (int) (event.getY() - startY);
                                    if(USER_IS_SWIPING == 0) {
                                        if (Math.abs(dY) / (float) Math.abs(dX) < 0.5)
                                            USER_IS_SWIPING = 1;    //gesture is swipe
                                        else
                                            USER_IS_SWIPING = -1;   //gesture is not swipe
                                    }
                                    if (Math.abs(dX) > LONG_PRESS_MOVEMENT_ALLOWANCE || Math.abs(dY) > LONG_PRESS_MOVEMENT_ALLOWANCE)
                                        VIEW_BEING_TOUCHED.setTag("swiped");
                                    translationX = (int) (200 * (1 - Math.exp(-0.005 * dX)));
                                    if (translationX < 0 || dX < 0) translationX = 0;
                                    if (!LONG_PRESS_MODE  &&  USER_IS_SWIPING == 1) VIEW_BEING_TOUCHED.setTranslationX(translationX);
                                    break;
                                }
                                default:
                                    break;
                            }
                            return true;
                        }
                    });
                    convertView.setOnLongClickListener(new View.OnLongClickListener() {
                        @Override
                        public boolean onLongClick(View v) {
                            View messageContainer = ((MessageHolder)v.getTag()).messageContainer;
                            if (VIEW_BEING_TOUCHED==messageContainer && VIEW_BEING_TOUCHED.getTag() == null) {
                                //long click
                                messageContainer.setTag("long_press");
                                //Toast.makeText(ChatActivity.this, "Long", Toast.LENGTH_SHORT).show();
                                if (LONG_PRESS_MODE) {
                                    if (messageContainer.isSelected()) {
                                        messageContainer.setSelected(false);
                                        SELECTED_ITEMS--;
                                        selectedMessageViews.remove(messageContainer);
                                        if (SELECTED_ITEMS == 0) {
                                            LONG_PRESS_MODE = false;
                                            invalidateOptionsMenu();
                                        }
                                        selectedMessages.remove(((MessageHolder) v.getTag()).messageId.getText().toString());
                                    } else {
                                        messageContainer.setSelected(true);
                                        SELECTED_ITEMS++;
                                        selectedMessageViews.add(messageContainer);
                                        selectedMessages.add(((MessageHolder) v.getTag()).messageId.getText().toString());
                                    }
                                } else {
                                    LONG_PRESS_MODE = true;
                                    invalidateOptionsMenu();
                                    messageContainer.setSelected(true);
                                    SELECTED_ITEMS++;
                                    selectedMessageViews.add(messageContainer);
                                    selectedMessages.add(((MessageHolder) v.getTag()).messageId.getText().toString());
                                }
                            }
                            return true;
                        }
                    });
                }
            }
            
            holder = (MessageHolder) convertView.getTag();

            //obtain message from data set
            Message message = getItem(position);
            if(message == null) return new View(getApplicationContext());

            //set date
            String date = message.date;
            holder.messageDate.setText(String.format("%s %s %s", getMonth(Integer.parseInt(date.substring(4, 6))), date.substring(6), date.substring(0, 4)));

            //done with ITEM_TYPE_MESSAGE_DATE
            if(getItemViewType(position)==ITEM_TYPE_MESSAGE_DATE) return convertView;
            
            //set message text
            int condensedPosition;
            if ((condensedPosition = getCondensedStringPosition(message.text)) != message.text.length()) {
                String condensedMessage = message.text.substring(0, condensedPosition);
                String fullMessage = message.text;
                SpannableString shortSpan = new SpannableString(condensedMessage + "\n...more");
                SpannableString longSpan = new SpannableString(fullMessage + "\n...less");
                ClickableSpan csMore = new ClickableSpan() {
                    @Override
                    public void onClick(@NonNull View textView) {
                        TextView messageText = (TextView) textView;
                        messageText.setText(((SpannableString[]) messageText.getTag())[1]);
                    }
                };
                ClickableSpan csLess = new ClickableSpan() {
                    @Override
                    public void onClick(@NonNull View textView) {
                        TextView messageText = (TextView) textView;
                        messageText.setText(((SpannableString[]) messageText.getTag())[0]);
                    }
                };
                shortSpan.setSpan(csMore, shortSpan.length() - 4, shortSpan.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                longSpan.setSpan(csLess, longSpan.length() - 4, longSpan.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                holder.messageText.setTag(new SpannableString[]{shortSpan, longSpan});
                holder.messageText.setText(shortSpan);
                holder.messageText.setMovementMethod(ClickableMovementMethod.getInstance());
                holder.messageText.setClickable(false);
                holder.messageText.setLongClickable(false);
            } else {
                holder.messageText.setText(message.text);
                holder.messageText.setTag(null);
            }
            
            //set time
            String time = message.time;
            String AM_PM = time.substring(0, 2);
            String clock = ((time.substring(2, 4).equals("00") && AM_PM.equals("PM")) ? "12" : time.substring(2, 4)) + time.substring(4);
            holder.messageTime.setText(String.format("%s%s", clock, AM_PM));

            //set metadata
            holder.messageMetadata.setText(message.metadata);

            //set reply
            if (message.reply_metadata != null && !message.reply_metadata.equals("")) {
                holder.messageReply.setText(message.reply);
                if (message.reply_metadata.equals("sent"))
                    holder.messageReplyBorder.setBackgroundColor(Color.parseColor("#FFFFFF"));
                else
                    holder.messageReplyBorder.setBackgroundColor(getResources().getColor(R.color.colorPrimary));
                holder.messageReply.setVisibility(View.VISIBLE);
                holder.messageReplyBorder.setVisibility(View.VISIBLE);

            } else
            {
                holder.messageReply.setVisibility(View.GONE);
                holder.messageReplyBorder.setVisibility(View.GONE);
            }

            //set triangle
            if (message.metadata.equals("sent")  ||  message.metadata.equals("sending")) {
                if(position + 1 < getCount()) {
                    Message messageNext = getItem(position + 1);
                    if (messageNext != null  &&  (messageNext.metadata.equals("sent")  ||  messageNext.metadata.equals("sending")))
                        holder.messageTriangle.setVisibility(View.INVISIBLE);
                    else
                        holder.messageTriangle.setVisibility(View.VISIBLE);
                }
                else
                    holder.messageTriangle.setVisibility(View.VISIBLE);
            }
            if (message.metadata.equals("received")) {
                if(position - 1 >= 0) {
                    Message messagePrev = getItem(position - 1);
                    if (messagePrev != null  &&  messagePrev.metadata.equals(message.metadata))
                        holder.messageTriangle.setVisibility(View.INVISIBLE);
                    else
                        holder.messageTriangle.setVisibility(View.VISIBLE);
                }
                else
                    holder.messageTriangle.setVisibility(View.VISIBLE);
            }

            //set padding
            if(position - 1 >= 0) {
                Message messagePrev = getItem(position - 1);
                if (messagePrev != null && (messagePrev.metadata.equals(message.metadata) ||
                        (message.metadata.equals("sent") && messagePrev.metadata.equals("sending")) ||
                        (message.metadata.equals("sending") && messagePrev.metadata.equals("sent"))))
                    holder.messagePadding.setLayoutParams(new ConstraintLayout.LayoutParams(ConstraintLayout.LayoutParams.MATCH_PARENT, 0));
                else
                    holder.messagePadding.setLayoutParams(new ConstraintLayout.LayoutParams(ConstraintLayout.LayoutParams.MATCH_PARENT, (int)(10 * getResources().getDisplayMetrics().density)));
            } else if(position == 0)
                holder.messagePadding.setLayoutParams(new ConstraintLayout.LayoutParams(ConstraintLayout.LayoutParams.MATCH_PARENT, 0));
            else
                holder.messagePadding.setLayoutParams(new ConstraintLayout.LayoutParams(ConstraintLayout.LayoutParams.MATCH_PARENT, (int)(10 * getResources().getDisplayMetrics().density)));


            //set id
            String messageId = message.date + message.rowid;
            holder.messageId.setText(messageId);

            //set selection highlight
            if (selectedMessages.contains(messageId))
                holder.messageContainer.setSelected(true);
            else
                holder.messageContainer.setSelected(false);

            //check message metadata
            if(message.metadata.equals("sending")  &&  !message.content.equals("file")) syncMessage(message);

            //done with ITEM_TYPE_MESSAGE_TEXT_SENT, ITEM_TYPE_MESSAGE_TEXT_RECEIVED
            if(message.content == null  ||  message.content.equals("")) return convertView;

            //how can this be null?
            if(holder.messageFileStatus==null) return convertView;

            //set uri on file message
            final Uri fileUri = Uri.parse(message.text);
            holder.messageFileUri.setText(fileUri.toString());

            //set file name on textView
            final String fileName = getFileName(fileUri);
            holder.messageText.setMovementMethod(null);
            holder.messageText.setTag(null);
            holder.messageText.setText(fileName);

            //set file status
            if(message.metadata.equals("sent")) {
                holder.messageFileStatus.setVisibility(View.VISIBLE);
                holder.messageFileStatus.setImageDrawable(getResources().getDrawable(R.drawable.ic_check_black_24dp));
                holder.messageFileStatusProgressBar.setVisibility(View.INVISIBLE);
            }
            else if(message.metadata.equals("sending")){
                holder.messageFileStatus.setVisibility(View.INVISIBLE);
                holder.messageFileStatus.setImageDrawable(getResources().getDrawable(R.drawable.ic_file_upload_black_24dp));
                holder.messageFileStatusProgressBar.setVisibility(View.VISIBLE);
                uploadFile(holder);
            }
            else {
                if (fileUri.getScheme() != null && (fileUri.getScheme().equals("http") || fileUri.getScheme().equals("https")))
                    holder.messageFileStatus.setImageDrawable(getResources().getDrawable(R.drawable.ic_file_download_black_24dp));
                else
                    holder.messageFileStatus.setImageDrawable(getResources().getDrawable(R.drawable.ic_check_black_24dp));
                holder.messageFileStatus.setVisibility(View.VISIBLE);
                holder.messageFileStatusProgressBar.setVisibility(View.INVISIBLE);
            }


            //set image in file preview if needed
            if(imageCache.containsKey(fileName))
            {
                holder.messageFilePreview.setImageDrawable(imageCache.get(fileName));
                holder.messageFileStatus.setVisibility(View.GONE);
            }
            else
            {
                holder.messageFilePreview.setImageDrawable(getResources().getDrawable(R.drawable.file_default_preview));
                for (String ext : IMAGE_FILE_EXTENSIONS) {
                    if (fileName.endsWith(ext)) {
                        new Thread(new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    BitmapFactory.Options filePreviewOptions = new BitmapFactory.Options();
                                    if(filePreviewOptions.outHeight == -1  ||  filePreviewOptions.outWidth == -1)
                                        throw new IOException("Image couldn't be processed");
                                    int fileImageSize = Math.max(filePreviewOptions.outHeight, filePreviewOptions.outWidth);
                                    filePreviewOptions.inSampleSize = fileImageSize/IMAGE_PREVIEW_SIZE;
                                    InputStream inputStream = getContentResolver().openInputStream(fileUri);
                                    Bitmap fileImageBitmap = BitmapFactory.decodeStream(inputStream, null, filePreviewOptions);
                                    if (fileImageBitmap != null) {
                                        int dimension = Math.min(fileImageBitmap.getWidth(), fileImageBitmap.getHeight());
                                        Bitmap fileImageBitmapThumbnail = ThumbnailUtils.extractThumbnail(fileImageBitmap, dimension, dimension);
                                        final CustomRoundedBitmapDrawable fileImageThumbnailRoundedDrawable = CustomRoundedBitmapDrawableFactory.create(getResources(), fileImageBitmapThumbnail);
                                        fileImageThumbnailRoundedDrawable.setCornerRadius(0.05f * fileImageBitmapThumbnail.getWidth());
                                        imageCache.put(fileName, fileImageThumbnailRoundedDrawable);
                                        runOnUiThread(new Runnable() {
                                            @Override
                                            public void run() {
                                                int firstItemIndex = messageListView.getFirstVisiblePosition();
                                                int offsetFromTop = messageListView.getChildAt(0) == null ? 0 : messageListView.getChildAt(0).getTop();
                                                updateUI();
                                                messageListView.setSelectionFromTop(firstItemIndex, offsetFromTop);
                                            }
                                        });
                                    }
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }

                            }
                        }).start();
                        break;
                    }
                }
            }

            //done with all view types
            return convertView;
        }

        private int getCondensedStringPosition(String text) {
            int newlineCount = 0;
            int i;
            for (i = 0; i < text.length(); i++) {
                if (i > 100) break;
                if (newlineCount > 2) break;
                if (text.charAt(i) == '\n') newlineCount++;
            }
            return i;
        }

        void checkOnMainThread() {
            if (BuildConfig.DEBUG) {
                if (Thread.currentThread() != Looper.getMainLooper().getThread()) {
                    throw new IllegalStateException("This method should be called from the Main Thread");
                }
            }
        }
    }

    static class ClickableMovementMethod extends BaseMovementMethod {

        private static ClickableMovementMethod sInstance;

        static ClickableMovementMethod getInstance() {
            if (sInstance == null) {
                sInstance = new ClickableMovementMethod();
            }
            return sInstance;
        }

        @Override
        public boolean canSelectArbitrarily() {
            return false;
        }

        @Override
        public boolean onTouchEvent(TextView widget, Spannable buffer, MotionEvent event) {

            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_DOWN) {

                int x = (int) event.getX();
                int y = (int) event.getY();
                x -= widget.getTotalPaddingLeft();
                y -= widget.getTotalPaddingTop();
                x += widget.getScrollX();
                y += widget.getScrollY();

                Layout layout = widget.getLayout();
                int line = layout.getLineForVertical(y);
                int off = layout.getOffsetForHorizontal(line, x);

                ClickableSpan[] link = buffer.getSpans(off, off, ClickableSpan.class);
                if (link.length > 0) {
                    if (action == MotionEvent.ACTION_UP) {
                        link[0].onClick(widget);
                    } else {
                        Selection.setSelection(buffer, buffer.getSpanStart(link[0]),
                                buffer.getSpanEnd(link[0]));
                    }
                    return true;
                } else {
                    Selection.removeSelection(buffer);
                }
            }

            return false;
        }

        @Override
        public void initialize(TextView widget, Spannable text) {
            Selection.removeSelection(text);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);

        if (user == null) {
            finish();
            Toast.makeText(this, "You need to be logged in!", Toast.LENGTH_SHORT).show();
            return;
        }

        checkWritePermission();

        //required for network operations on main thread
        StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
        StrictMode.setThreadPolicy(policy);

        StorageManager storageManager = (StorageManager) getSystemService(Context.STORAGE_SERVICE);
        String appPreferences = "Nosk_preferences";
        sharedPreferences = getSharedPreferences(appPreferences, MODE_PRIVATE);
        if(Build.VERSION.SDK_INT >= 29) {
            if(storageManager == null) {
                finish();
                return;
            }
            Intent storageAccessIntent = storageManager.getPrimaryStorageVolume().createOpenDocumentTreeIntent();
            storageAccessIntent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
            storageAccessIntent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            storageAccessIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            if(sharedPreferences.contains(STORAGE_DIRECTORY_URI)) {
                boolean hasPermission = false;
                for(UriPermission uriPermission : getContentResolver().getPersistedUriPermissions())
                {
                    if(uriPermission.getUri().toString().equals(sharedPreferences.getString(STORAGE_DIRECTORY_URI, null)))
                        hasPermission = true;
                }
                if(hasPermission)
                    appStorageDir = DocumentFile.fromTreeUri(this, Uri.parse(sharedPreferences.getString(STORAGE_DIRECTORY_URI, null)));
                else
                    startActivityForResult(storageAccessIntent, REQUEST_WRITE_PERMISSION_Q);
            }
            else
                startActivityForResult(storageAccessIntent, REQUEST_WRITE_PERMISSION_Q);
        }
        else
            appStorageDir = DocumentFile.fromFile(Environment.getExternalStorageDirectory());

        adapter = new MyAdapter(this, messages);
    }

    @Override
    protected void onStart() {
        super.onStart();
        setupAndInitialize();

        Timer syncTimer = new Timer();
        SyncMessages syncMessagesTask = new SyncMessages();
        syncTimer.schedule(syncMessagesTask, 60000);
    }

    @Override
    protected void onActivityResult (int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode)
        {
            case REQUEST_WRITE_PERMISSION: {
                if(ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_DENIED) {
                    finish();
                    return;
                }
            }
            case ATTACH_FILE_CODE: {
                if(resultCode!=RESULT_CANCELED  &&  data != null  &&  data.getData() != null) {
                    sendFile(Uri.parse(data.getData().toString()));
                }
                break;
            }
            case REQUEST_WRITE_PERMISSION_Q: {
                if(resultCode!=RESULT_CANCELED  &&  data != null  &&  data.getData() != null)
                {
                    Uri storageDirectoryUri = data.getData();
                    SharedPreferences.Editor editor = sharedPreferences.edit();
                    editor.putString(STORAGE_DIRECTORY_URI, storageDirectoryUri.toString());
                    editor.apply();
                    this.getContentResolver().takePersistableUriPermission(storageDirectoryUri, Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                }
                else
                {
                    finish();
                }
            }
        }
    }

    @Override
    public void onBackPressed() {
        if (LONG_PRESS_MODE) {
            clearSelection();
            return;
        }
        for (UploadTask uploadTask : uploadTasks)
            uploadTask.cancel();
        setResult(RESULT_OK, getIntent());
        finish();
    }

    //activity setup
    private void setupAndInitialize() {

        //cache some views
        dateView = findViewById(R.id.chat_date_text);
        chatFriendIcon = findViewById(R.id.chat_friend_icon);
        messageListView = findViewById(R.id.messages);
        chatbox = findViewById(R.id.chatbox);
        chatboxReply = findViewById(R.id.chatbox_reply);
        chatboxReplyBorder = findViewById(R.id.chatbox_reply_border);

        //setup options menu
        ImageButton optionsButton = findViewById(R.id.imageButton_options);
        optionsMenu = new PopupMenu(this, optionsButton);
        optionsMenu.getMenuInflater().inflate(R.menu.chat_options, optionsMenu.getMenu());
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
                    case R.id.delete_conversation:
                    case R.id.delete_select_chat:
                        deleteMessages();
                        break;
                }
                return false;
            }
        });

        //load friend image
        userUid = user.getUid();
        friendUid = getIntent().getStringExtra("uid");
        if (getIntent().getParcelableExtra("image") != null) {
            RoundedBitmapDrawable friend_icon = RoundedBitmapDrawableFactory.create(getResources(), (Bitmap) getIntent().getParcelableExtra("image"));
            friend_icon.setCircular(true);
            friend_icon.setAntiAlias(true);
            chatFriendIcon.setImageDrawable(friend_icon);
        } else if (chatFriendIconCache != null) {
            RoundedBitmapDrawable friend_icon = RoundedBitmapDrawableFactory.create(getResources(), chatFriendIconCache);
            friend_icon.setCircular(true);
            friend_icon.setAntiAlias(true);
            chatFriendIcon.setImageDrawable(friend_icon);
        }
        else loadUserImage(getIntent().getStringExtra("uid"));
        ((TextView) findViewById(R.id.chat_friend_name)).setText(getIntent().getStringExtra("name"));

        //setup chat adapter
        messageListView.setAdapter(adapter);
        adapter.notifyDataSetChanged();
        messageListView.setOnScrollListener(new AbsListView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(AbsListView view, int scrollState) {
                mayShowDate();
                if(messageListView.computeVerticalScrollOffset() < VERTICAL_SCROLL_OFFSET_LIMIT)
                    loadPastChats();
            }
            @Override
            public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) { }
        });

        //expand hit rect of reply clearer
        final View clearReplyParent = findViewById(R.id.clear_reply_parent);
        final DisplayMetrics displayMetrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
        clearReplyParent.post(new Runnable() {
            @Override
            public void run() {
                View clearReply = findViewById(R.id.clear_reply);
                Rect delegateArea = new Rect(clearReply.getLeft(), clearReply.getTop(), clearReply.getRight(), clearReply.getBottom());
                delegateArea.left -= 24 * displayMetrics.density;
                delegateArea.top -= 24 * displayMetrics.density;
                delegateArea.right += 12 * displayMetrics.density;
                delegateArea.bottom += 12 * displayMetrics.density;
                TouchDelegate touchDelegate = new TouchDelegate(delegateArea, clearReply);
                clearReplyParent.setTouchDelegate(touchDelegate);
            }
        });

        //setup chat DB
        DB_PATH = getApplicationContext().getFilesDir().getAbsolutePath() + "/users/" + userUid + "/friends/" + friendUid;
        File friendDir = new File(DB_PATH);
        boolean success = true;
        if (!friendDir.exists()) success = friendDir.mkdirs();
        if(!success) {
            Toast.makeText(this, "Couldn't access storage", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        DB_PATH += "/";

        //first-time setup on new chat DB
        sqlDB = SQLiteDatabase.openOrCreateDatabase(DB_PATH + DB_NAME, null);
        Cursor getTableMetadata = sqlDB.rawQuery("SELECT name FROM sqlite_master WHERE type='table' AND name=\"metadata\"", null);
        if (getTableMetadata.getCount() == 0) {
            sqlDB.execSQL("CREATE TABLE IF NOT EXISTS metadata(date_last_chat TEXT)");
            ContentValues metadataValue = new ContentValues();
            metadataValue.put("date_last_chat", "");
            sqlDB.insertOrThrow("metadata", null, metadataValue);
        }
        getTableMetadata.close();

        //get last date of chat from DB
        Cursor getLastDateOfChat = sqlDB.query("metadata", new String[]{"date_last_chat"}, null, null, null, null, null);
        getLastDateOfChat.moveToNext();
        DATE_LAST_CHAT = getLastDateOfChat.getString(getLastDateOfChat.getColumnIndex("date_last_chat"));
        DATE_OLDEST_CHAT = DATE_LAST_CHAT;
        getLastDateOfChat.close();

        //load chats using DATE_LAST_CHAT
        adapter.clear();        //reset adapter and boolean on restart
        CAN_LOAD_MORE_MESSAGES = true;
        loadChatsFromLocal(DATE_LAST_CHAT);
        LOADING_PAST_CHAT_FOR_FIRST_TIME = true;
        while (adapter.getCount() < INITIAL_MESSAGE_SIZE  &&  CAN_LOAD_MORE_MESSAGES) loadPastChats();
        LOADING_PAST_CHAT_FOR_FIRST_TIME = false;
        loadChatsFromCloud();

        //setup listener for incoming chat messages
        db.collection("users").document(userUid).collection("friends").document(friendUid)
                .addSnapshotListener(ChatActivity.this, MetadataChanges.INCLUDE, new EventListener<DocumentSnapshot>() {
                    @Override
                    public void onEvent(@Nullable DocumentSnapshot documentSnapshot, @Nullable FirebaseFirestoreException e) {
                        if (documentSnapshot != null && documentSnapshot.get("chat") != null && !documentSnapshot.getMetadata().hasPendingWrites())
                            loadChatsFromCloud();
                    }
                });
    }

    //load chats into app
    private void loadPastChats() {
        if (DATE_OLDEST_CHAT.equals("")) {
            CAN_LOAD_MORE_MESSAGES = false;
            return;
        }
        Cursor pastChatDate = sqlDB.query("'" + DATE_OLDEST_CHAT + "'", new String[]{"text"}, "metadata = 'date'", null, null, null, null);
        pastChatDate.moveToNext();
        DATE_OLDEST_CHAT = pastChatDate.getString(pastChatDate.getColumnIndex("text"));
        pastChatDate.close();
        int oldMessagesSize = adapter.getCount();
        loadChatsFromLocal(DATE_OLDEST_CHAT);
        final int numberOfItemsAdded = adapter.getCount()- oldMessagesSize;
        CHAT_IS_LOADING = false;
        if(!LOADING_PAST_CHAT_FOR_FIRST_TIME) {
            int firstItemIndex = messageListView.getFirstVisiblePosition();
            int offsetFromTop = messageListView.getChildAt(0) == null ? 0 : messageListView.getChildAt(0).getTop();
            messageListView.setSelectionFromTop(firstItemIndex + numberOfItemsAdded, offsetFromTop);
        }
    }

    private void loadChatsFromCloud() {
        CHAT_IS_LOADING = true;

        final List[] newMessages = new List[1];
        db.runTransaction(new Transaction.Function<Void>() {
            @Nullable
            @Override
            public Void apply(@NonNull Transaction transaction) throws FirebaseFirestoreException {
                DocumentSnapshot doc = transaction.get(db.collection("users").document(userUid).collection("friends").document(friendUid));
                newMessages[0] = (List) doc.get("chat");
                if (newMessages[0] == null) newMessages[0] = new ArrayList<>();
                transaction.update(db.collection("users").document(userUid).collection("friends").document(friendUid), "chat", null);
                return null;
            }
        }).addOnSuccessListener(new OnSuccessListener<Void>() {
            @Override
            public void onSuccess(Void aVoid) {
                for (Object mMessage : newMessages[0])
                {
                    final Message newMessage = convertToPojo(mMessage);

                    ContentValues messageValues = new ContentValues();
                    messageValues.put("text", newMessage.text);
                    messageValues.put("date", newMessage.date);
                    messageValues.put("time", newMessage.time);
                    messageValues.put("metadata", newMessage.metadata);
                    messageValues.put("reply", newMessage.reply);
                    messageValues.put("reply_metadata", newMessage.reply_metadata);
                    messageValues.put("content", newMessage.content);

                    try {
                        Cursor tableQuery = sqlDB.rawQuery("SELECT name FROM sqlite_master WHERE type='table' AND name=?", new String[]{newMessage.date});
                        if (tableQuery.getCount() == 0) {
                            sqlDB.execSQL("CREATE TABLE IF NOT EXISTS '" + newMessage.date + "'(text TEXT, date TEXT, time TEXT, metadata TEXT, content TEXT, reply TEXT, reply_metadata TEXT)");

                            ContentValues dateValue = new ContentValues();
                            dateValue.put("metadata", "date");
                            dateValue.put("date", newMessage.date);
                            dateValue.put("time", "AM00:00 ");
                            dateValue.put("reply", "");
                            dateValue.put("reply_metadata", "");
                            dateValue.put("content", "");
                            if (newMessage.date.compareTo(DATE_LAST_CHAT) > 0 || DATE_LAST_CHAT.equals("")) {
                                dateValue.put("text", DATE_LAST_CHAT);
                                sqlDB.insertOrThrow("'" + newMessage.date + "'", null, dateValue);
                            } else {
                                Cursor tables = sqlDB.rawQuery("SELECT name FROM sqlite_master WHERE type='table' AND name!='android_metadata' AND name!='metadata' ORDER BY name ASC", null);
                                String date_previous_chat = "";
                                while (tables.moveToNext()) {
                                    if (tables.getString(tables.getColumnIndex("name")).equals(newMessage.date) && !tables.isLast()) {
                                        tables.moveToNext();
                                        dateValue.put("text", date_previous_chat);
                                        sqlDB.insertOrThrow("'" + newMessage.date + "'", null, dateValue);
                                        dateValue.remove("date");
                                        dateValue.put("text", newMessage.date);
                                        sqlDB.update("'" + tables.getString(tables.getColumnIndex("name")) + "'", dateValue, "metadata = 'date'", null);
                                        break;
                                    }
                                    date_previous_chat = tables.getString(tables.getColumnIndex("name"));
                                }
                                tables.close();
                            }
                        }
                        tableQuery.close();
                        sqlDB.insertOrThrow("'" + newMessage.date + "'", null, messageValues);

                        Cursor rowId = sqlDB.rawQuery("SELECT \"rowid\" FROM \"" + newMessage.date + "\" ORDER BY \"rowid\" DESC LIMIT 1", null);
                        rowId.moveToNext();
                        newMessage.rowid = rowId.getString(rowId.getColumnIndex("rowid"));
                        if(rowId.getString(rowId.getColumnIndex("rowid")).equals("2"))
                        {
                            //reload date message into adapter
                            final Message dateMessage = new Message();
                            dateMessage.metadata = "date";
                            dateMessage.date = newMessage.date;
                            dateMessage.time = "AM00:00 ";
                            addAtCorrectPosition(dateMessage);
                        }
                        rowId.close();


                        addAtCorrectPosition(newMessage);
                        if (newMessage.date.compareTo(DATE_LAST_CHAT) > 0) {
                            ContentValues dateValue = new ContentValues();
                            dateValue.put("date_last_chat", newMessage.date);
                            DATE_LAST_CHAT = newMessage.date;
                            sqlDB.update("metadata", dateValue, null, null);
                        }
                    } catch (SQLException e) {
                        Log.e("SQL : ", Objects.requireNonNull(e.getMessage()));
                    }
                }
            }
        }).addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception e) {
                Toast.makeText(ChatActivity.this, "Fail:" + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
        CHAT_IS_LOADING = false;
    }

    private void loadChatsFromLocal(String date) {
        CHAT_IS_LOADING = true;

        if (date.equals("")) return;

        Cursor result = sqlDB.query("'" + date + "'",
                new String[]{"rowid", "text", "time", "date", "metadata", "reply", "content", "reply_metadata"},
                null,
                null,
                null,
                null,
                "\"time\" ASC");
        result.moveToLast();
        if(result.getCount()==1) return;
        while (!result.isBeforeFirst()) {
            final Message msg = new Message();

            msg.text = result.getString(result.getColumnIndex("text"));
            msg.date = result.getString(result.getColumnIndex("date"));
            msg.time = result.getString(result.getColumnIndex("time"));
            msg.metadata = result.getString(result.getColumnIndex("metadata"));
            msg.reply = result.getString(result.getColumnIndex("reply"));
            msg.reply_metadata = result.getString(result.getColumnIndex("reply_metadata"));
            msg.content = result.getString(result.getColumnIndex("content"));
            msg.rowid = result.getString(result.getColumnIndex("rowid"));

            adapter.insert(msg, 0);
            result.moveToPrevious();
        }
        result.close();
    }

    private void updateUI() {
        adapter.notifyDataSetChanged();
    }

    //user interaction with action bar
    public void onBackButtonPressed(@SuppressWarnings("unused") View v) {
        onBackPressed();
    }

    private void deleteMessages() {
        if (SELECTED_ITEMS == 0) {
            //delete conversation
            File chatDB = new File(DB_PATH + DB_NAME);
            if (chatDB.exists()) {
                if (chatDB.delete()) {
                    adapter.clear();
                    setupAndInitialize();
                } else
                    Toast.makeText(this, "Failed to delete conversation", Toast.LENGTH_SHORT).show();
            } else
                Toast.makeText(this, "No conversation exists!", Toast.LENGTH_SHORT).show();

            return;
        }
        for (String messageId : selectedMessages) {
            sqlDB.delete("\"" + messageId.substring(0, 8) + "\"", "rowid = " + messageId.substring(8), null);
            Cursor rowId = sqlDB.rawQuery("SELECT \"rowid\" FROM \"" + messageId.substring(0, 8) + "\" ORDER BY \"rowid\" DESC LIMIT 1", null);
            rowId.moveToNext();
            if(rowId.getString(rowId.getColumnIndex("rowid")).equals("1"))
            {
                //table has no more records, remove message from adapter
                for (int i=0; i<adapter.getCount(); i++) {
                    Message msg = adapter.getItem(i);
                    if(msg == null) continue;
                    if (msg.metadata.equals("date")  &&  msg.date.equals(messageId.substring(0, 8))) {
                        adapter.remove(msg);
                        break;
                    }
                }
            }
            rowId.close();
            for (int i=0; i<adapter.getCount(); i++) {
                Message msg = adapter.getItem(i);
                if(msg == null) continue;
                if (messageId.equals(msg.date + msg.rowid)) {
                    adapter.remove(msg);
                    break;
                }
            }
        }
        clearSelection();
    }

    private void clearSelection() {
        for (View selectedView : selectedMessageViews)
            selectedView.setSelected(false);
        selectedMessages.clear();
        selectedMessageViews.clear();
        SELECTED_ITEMS = 0;
        LONG_PRESS_MODE = false;
        invalidateOptionsMenu();
    }

    private void loadUserImage(String uid) {
        chatFriendIcon.setImageDrawable(getResources().getDrawable(R.drawable.ic_account_circle_white_24dp));
        db.collection("users").document(uid).get()
                .addOnSuccessListener(new OnSuccessListener<DocumentSnapshot>() {
                    @Override
                    public void onSuccess(final DocumentSnapshot documentSnapshot) {
                        if(documentSnapshot != null  &&  documentSnapshot.get("image") != null)
                        {
                            new Thread(new Runnable() {
                                @Override
                                public void run() {
                                    try {
                                        URL imageURL = new URL(documentSnapshot.getString("image"));
                                        Bitmap profilePicRaw = BitmapFactory.decodeStream(imageURL.openConnection().getInputStream());
                                        Bitmap profilePicSquare = ThumbnailUtils.extractThumbnail(profilePicRaw, Math.min(profilePicRaw.getWidth(), profilePicRaw.getHeight()), Math.min(profilePicRaw.getWidth(), profilePicRaw.getHeight()));
                                        chatFriendIconCache = profilePicSquare;

                                        final RoundedBitmapDrawable roundedProfilePic = RoundedBitmapDrawableFactory.create(getResources(), profilePicSquare);
                                        roundedProfilePic.setCircular(true);
                                        roundedProfilePic.setAntiAlias(true);
                                        chatFriendIcon.post(new Runnable() {
                                            @Override
                                            public void run() { chatFriendIcon.setImageDrawable(roundedProfilePic); }});
                                    } catch (IOException e) {
                                        e.printStackTrace();
                                    }
                                }
                            }).start();
                        }
                    }
                });
    }

    public void onOptionsClick(@SuppressWarnings("unused") View view) {
        //prepare menu before showing
        Menu menu = optionsMenu.getMenu();
        SpannableString itemText;
        if (!LONG_PRESS_MODE) {
            MenuItem deleteSelected = menu.findItem(R.id.delete_select_chat);
            deleteSelected.setEnabled(false);
            itemText = new SpannableString(deleteSelected.getTitle().toString());
            itemText.setSpan(new ForegroundColorSpan(getResources().getColor(R.color.colorBlackLight)), 0, itemText.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            deleteSelected.setTitle(itemText);

            MenuItem deleteConversation = menu.findItem(R.id.delete_conversation);
            deleteConversation.setEnabled(true);
            itemText = new SpannableString(deleteConversation.getTitle().toString());
            itemText.setSpan(new ForegroundColorSpan(Color.BLACK), 0, itemText.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            deleteConversation.setTitle(itemText);
        } else {
            MenuItem deleteConversation = menu.findItem(R.id.delete_conversation);
            deleteConversation.setEnabled(false);
            itemText = new SpannableString(deleteConversation.getTitle().toString());
            itemText.setSpan(new ForegroundColorSpan(getResources().getColor(R.color.colorBlackLight)), 0, itemText.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            deleteConversation.setTitle(itemText);

            MenuItem deleteSelected = menu.findItem(R.id.delete_select_chat);
            deleteSelected.setEnabled(true);
            itemText = new SpannableString(deleteSelected.getTitle().toString());
            itemText.setSpan(new ForegroundColorSpan(Color.BLACK), 0, itemText.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            deleteSelected.setTitle(itemText);
        }

        optionsMenu.show();
    }

    public void viewProfile(@SuppressWarnings("unused") View view){
        String uid = getIntent().getStringExtra("uid");
        String name = getIntent().getStringExtra("name");
        startProfileActivity(uid, name);
    }

    public void startProfileActivity(String uid, String name) {
        Intent intent = new Intent(this, ProfileActivity.class);
        intent.putExtra("uid", uid);
        intent.putExtra("name", name);
        Bitmap image = getIntent().getParcelableExtra("image");
        intent.putExtra("image", image);
        startActivity(intent);
    }

    //user interaction main UI
    private void mayShowDate() {
        View topMessage = messageListView.getChildAt(0);
        if(topMessage.findViewById(R.id.message_date)!=null)
        {
            //normal message is at top
            dateHandler.removeCallbacks(resetDateDrop);
            dateView.setText(((TextView) topMessage.findViewById(R.id.message_date)).getText());
            if(dateView.getTranslationY()==0) {
                dateView.animate()
                        .translationY(dateView.getBottom() - dateView.getTop() + 1)
                        .setDuration(100)
                        .start();
            }
            dateHandler.postDelayed(resetDateDrop, 5000);
        }
        else
        {
            //date is at top already
            dateView.setTranslationY(0);
        }
    }

    public void chooseFile(@SuppressWarnings("unused") View view) {
        Intent attachFileIntent = new Intent(Intent.ACTION_GET_CONTENT);
        attachFileIntent.setType("*/*");
        attachFileIntent.addCategory(Intent.CATEGORY_OPENABLE);
        attachFileIntent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        attachFileIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        Intent obtainFileIntent = Intent.createChooser(attachFileIntent, "Choose a file");
        startActivityForResult(obtainFileIntent, ATTACH_FILE_CODE);
    }

    public void replyMessageSelected(View v) {
        MyAdapter.MessageHolder holder = (MyAdapter.MessageHolder) v.getTag();
        String reply;
        if (holder.messageText.getTag() == null)
            reply = holder.messageText.getText().toString();
        else {
            SpannableString shortMessage = ((SpannableString[]) v.findViewById(R.id.message_textview).getTag())[0];
            reply = shortMessage.subSequence(0, shortMessage.length() - 4).toString();
        }
        chatboxReply.setText(reply);
        if (holder.messageMetadata.getText().toString().equals("sent"))
            chatboxReplyBorder.setBackgroundColor(Color.parseColor("#FFFFFF"));
        else
            chatboxReplyBorder.setBackgroundColor(getResources().getColor(R.color.colorPrimary));
        chatboxReply.setVisibility(View.VISIBLE);
    }

    public void replyMessageCancelled(@SuppressWarnings("unused") View view) {
        chatboxReply.setText("");
        chatboxReply.setVisibility(View.GONE);
        chatboxReplyBorder.setBackgroundColor(Color.parseColor("#EEEEEE"));
    }

    private void downloadFile(final MyAdapter.MessageHolder fileMessageHolder) {
        final String uriString = fileMessageHolder.messageFileUri.getText().toString();
        String dateTemp = null;
        for (int i=0; i<adapter.getCount(); i++) {
            Message msg = adapter.getItem(i);
            if(msg == null) continue;
            if(((TextView) VIEW_BEING_TOUCHED.findViewById(R.id.message_id)).getText().toString().equals(msg.date + msg.rowid)) {
                dateTemp = msg.date;
            }
        }
        if(dateTemp == null)
            return;
        final String date = dateTemp;

        fileMessageHolder.messageFileStatus.setVisibility(View.GONE);
        fileMessageHolder.messageFileStatusProgressBar.setVisibility(View.VISIBLE);

        final String fileName = getFileName(Uri.parse(uriString));
        MimeTypeMap mimeTypeMap = MimeTypeMap.getSingleton();
        String mimeType = mimeTypeMap.getMimeTypeFromExtension(fileName.substring(fileName.lastIndexOf('.') + 1));
        if(mimeType == null) mimeType = "application/octet-stream";
        final DocumentFile outFile;
        try {
            outFile = Objects.requireNonNull(getFileDirectoryForFriend(friendUid)).createFile(mimeType, fileName.substring(0, fileName.lastIndexOf('.')));
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(ChatActivity.this, "Couldn't access storage for download", Toast.LENGTH_SHORT).show();
            return;
        }
        if(outFile == null) {
            Toast.makeText(ChatActivity.this, "Couldn't access storage for download", Toast.LENGTH_SHORT).show();
            return;
        }
        
        final StorageReference fileReference = storage.getReferenceFromUrl(uriString);
        fileReference.getStream()
                .addOnCompleteListener(new OnCompleteListener<StreamDownloadTask.TaskSnapshot>() {
                    @Override
                    public void onComplete(@NonNull Task<StreamDownloadTask.TaskSnapshot> task) {
                        if(task.isSuccessful()  &&  task.getResult() != null)
                        {
                            try {
                                InputStream inputStream = task.getResult().getStream();
                                OutputStream outputStream = getContentResolver().openOutputStream(outFile.getUri());
                                if(outputStream == null) throw new IOException("Couldn't access storage");
                                byte[] buffer = new byte[1024];
                                int bufferLength;
                                while((bufferLength = inputStream.read(buffer)) != -1)
                                    outputStream.write(buffer, 0, bufferLength);
                                outputStream.flush();
                                outputStream.close();
                                inputStream.close();
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                            fileMessageHolder.messageFileStatusProgressBar.setVisibility(View.INVISIBLE);
                            fileReference.delete();

                            //update database
                            ContentValues updateFileUri = new ContentValues();
                            updateFileUri.put("text", outFile.getUri().toString());
                            sqlDB.update("\"" + date + "\"", updateFileUri, "\"text\" = \"" + uriString + "\"", null);

                            //change the file uri on screen
                            for (int i=0; i<adapter.getCount(); i++) {
                                Message msg = adapter.getItem(i);
                                if(msg == null) continue;
                                if(msg.text != null  &&  msg.text.equals(uriString)  &&  msg.content != null  &&  msg.content.equals("file")) {
                                    msg.text = outFile.getUri().toString();
                                }
                            }
                            updateUI();
                        }
                        else {
                            Toast.makeText(ChatActivity.this, "Download failed " + fileReference.toString(), Toast.LENGTH_LONG).show();
                            fileMessageHolder.messageFileStatusProgressBar.setVisibility(View.INVISIBLE);
                            fileMessageHolder.messageFileStatus.setVisibility(View.VISIBLE);
                        }
                    }
                });
    }

    private void uploadFile(final MyAdapter.MessageHolder messageHolder) {
        if(uploadCacheMessageId.contains(messageHolder.messageId.getText().toString()))
            return;
        uploadCacheMessageId.add(messageHolder.messageId.getText().toString());

        messageListView.post(new Runnable() {
            @Override
            public void run() {
                messageHolder.messageFileStatusProgressBar.setVisibility(View.VISIBLE);
                messageHolder.messageFileStatus.setVisibility(View.GONE);
            }
        });

        Message fileMessage = null;
        for (int i=0; i<adapter.getCount(); i++) {
            Message msg = adapter.getItem(i);
            if(msg == null) continue;
            if((msg.date + msg.rowid).equals(messageHolder.messageId.getText().toString())) {
                fileMessage = msg;
                break;
            }
        }
        if (fileMessage == null)
            return;

        //upload file to fireStorage
        final StorageReference fileReference = storage.getReference().child("users/" + friendUid + "/" + getFileName(Uri.parse(fileMessage.text)));
        final Message finalFileMessage = fileMessage;
        UploadTask newUploadTask = fileReference.putFile(Uri.parse(fileMessage.text));
        newUploadTask.addOnSuccessListener(new OnSuccessListener<UploadTask.TaskSnapshot>() {
            @Override
            public void onSuccess(UploadTask.TaskSnapshot taskSnapshot) {
                fileReference.getDownloadUrl()
                        .addOnSuccessListener(new OnSuccessListener<Uri>() {
                            @Override
                            public void onSuccess(final Uri downloadUri) {
                                final Message newMessage = new Message();
                                db.runTransaction(new Transaction.Function<Void>() {
                                    @Nullable
                                    @Override
                                    public Void apply(@NonNull Transaction transaction) throws FirebaseFirestoreException {
                                        List messageList = (List) transaction.get(db.collection("users").document(friendUid).collection("friends").document(userUid)).get("chat");
                                        if (messageList == null) messageList = new ArrayList();

                                        newMessage.text = downloadUri.toString();
                                        newMessage.time = finalFileMessage.time;
                                        newMessage.date = finalFileMessage.date;
                                        newMessage.metadata = "received";
                                        newMessage.reply = finalFileMessage.reply;
                                        newMessage.content = finalFileMessage.content;
                                        if (finalFileMessage.reply_metadata.equals("sent")) newMessage.reply_metadata = "received";
                                        else if (finalFileMessage.reply_metadata.equals("received")) newMessage.reply_metadata = "sent";
                                        else newMessage.reply_metadata = "";

                                        //convert untyped list to typed list
                                        List<Message> newMessageList = new ArrayList<>();
                                        for (Object message : messageList)
                                            newMessageList.add(convertToPojo(message));
                                        newMessageList.add(newMessage);

                                        transaction.update(db.collection("users").document(friendUid).collection("friends").document(userUid), "chat", newMessageList);
                                        return null;
                                    }
                                }).addOnCompleteListener(new OnCompleteListener<Void>() {
                                    @Override
                                    public void onComplete(@NonNull Task<Void> task) {
                                        if (task.isSuccessful())
                                            Toast.makeText(ChatActivity.this, "success", Toast.LENGTH_SHORT);
                                        else
                                            if(task.getException() != null) Toast.makeText(ChatActivity.this, "fail : " + task.getException().getMessage(), Toast.LENGTH_LONG).show();
                                    }
                                });
                            }
                        });

                Toast.makeText(ChatActivity.this, "Upload Succeeded", Toast.LENGTH_SHORT).show();
                messageHolder.messageFileStatusProgressBar.setVisibility(View.INVISIBLE);
                messageHolder.messageFileStatus.setVisibility(View.VISIBLE);
                messageHolder.messageFileStatus.setImageDrawable(getResources().getDrawable(R.drawable.ic_check_black_24dp));

                //update database from sending to sent
                ContentValues updateFileMetadata = new ContentValues();
                updateFileMetadata.put("metadata", "sent");
                sqlDB.update("\"" + finalFileMessage.date + "\"", updateFileMetadata, "\"rowid\" = \"" + finalFileMessage.rowid + "\"", null);

                //change message metadata
                for (int i=0; i<adapter.getCount(); i++) {
                    Message msg = adapter.getItem(i);
                    if(msg == null) continue;
                    if((msg.date + msg.rowid).equals(messageHolder.messageId.getText().toString())) {
                        msg.metadata = "sent";
                    }
                }

                updateUI();
                uploadCacheMessageId.remove(messageHolder.messageId.getText().toString());

            }
        }).addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception e) {
                Toast.makeText(ChatActivity.this, "Failed to Upload", Toast.LENGTH_SHORT).show();
                messageHolder.messageFileStatusProgressBar.setVisibility(View.INVISIBLE);
                messageHolder.messageFileStatus.setVisibility(View.VISIBLE);
                messageHolder.messageFileStatus.setImageDrawable(getResources().getDrawable(R.drawable.ic_file_upload_black_24dp));
                uploadCacheMessageId.remove(messageHolder.messageId.getText().toString());
            }
        });
        uploadTasks.add(newUploadTask);
    }

    private void sendFile(Uri uri) {
        final Message newMessage = new Message();

        final Uri localFileUri = uri;
        final String fileName = getFileName(uri);

        String mimeType = getContentResolver().getType(localFileUri);
        if(mimeType == null) mimeType = "application/octet-stream";

        //checks to make sure file is valid and storage is accessible
        final DocumentFile copyFile;
        try {
            copyFile = Objects.requireNonNull(getFileDirectoryForFriend(friendUid)).createFile(mimeType, fileName.substring(0, fileName.lastIndexOf('.')));
            InputStream inputStream = getContentResolver().openInputStream(localFileUri);
            if(inputStream == null) throw new IOException("Files cant be accessed: Try launching app again.");
            byte[] buffer = new byte[1024];
            if(inputStream.read(buffer) <= 0) throw new IOException("File couldn't be opened.");
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(ChatActivity.this, "Couldn't access storage", Toast.LENGTH_SHORT).show();
            return;
        }
        if(copyFile == null) {
            Toast.makeText(ChatActivity.this, "Couldn't access storage", Toast.LENGTH_SHORT).show();
            return;
        }

        newMessage.text = copyFile.getUri().toString();
        chatbox.setText("");

        Calendar currentTime = Calendar.getInstance();
        newMessage.time = (currentTime.get(Calendar.AM_PM) == Calendar.AM ? "AM" : "PM") +
                (currentTime.get(Calendar.HOUR) < 10 ? "0" : "") + (currentTime.get(Calendar.HOUR)) +
                ":" + (currentTime.get(Calendar.MINUTE) < 10 ? "0" : "") + currentTime.get(Calendar.MINUTE) + " ";
        newMessage.date = (currentTime.get(Calendar.YEAR) * 10000 +
                (currentTime.get(Calendar.MONTH) + 1) * 100 +
                currentTime.get(Calendar.DAY_OF_MONTH)) + "";

        if (CHAT_IS_LOADING) {
            synchronized (syncObj) {
                try {
                    syncObj.wait();
                } catch (InterruptedException e) {
                    Log.e("Thread Stopped: ", e.getMessage() == null ? "" : e.getMessage());
                }
            }
        }

        newMessage.metadata = "sending";

        newMessage.reply = chatboxReply.getText().toString();

        final String reply_metadata;
        if (((ColorDrawable) chatboxReplyBorder.getBackground()).getColor() == Color.parseColor("#EEEEEE"))
            reply_metadata = "";
        else if (((ColorDrawable) chatboxReplyBorder.getBackground()).getColor() == Color.parseColor("#FFFFFF"))
            reply_metadata = "sent";
        else
            reply_metadata = "received";
        newMessage.reply_metadata = reply_metadata;
        replyMessageCancelled(new View(this));

        newMessage.content = "file";

        ContentValues messageValues = new ContentValues();
        messageValues.put("text", newMessage.text);
        messageValues.put("date", newMessage.date);
        messageValues.put("time", newMessage.time);
        messageValues.put("metadata", newMessage.metadata);
        messageValues.put("reply", newMessage.reply);
        messageValues.put("reply_metadata", newMessage.reply_metadata);
        messageValues.put("content", newMessage.content);
        try {
            Cursor tableQuery = sqlDB.rawQuery("SELECT name FROM sqlite_master WHERE type='table' AND name=?", new String[]{newMessage.date});
            if (tableQuery.getCount() == 0) {
                sqlDB.execSQL("CREATE TABLE IF NOT EXISTS '" + newMessage.date + "'(text TEXT, date TEXT, time TEXT, metadata TEXT, content TEXT, reply TEXT, reply_metadata TEXT)");

                ContentValues dateValue = new ContentValues();
                dateValue.put("metadata", "date");
                dateValue.put("date", newMessage.date);
                dateValue.put("text", DATE_LAST_CHAT);
                dateValue.put("time", "AM00:00 ");
                dateValue.put("reply", "");
                dateValue.put("reply_metadata", "");
                dateValue.put("content", "");
                sqlDB.insertOrThrow("'" + newMessage.date + "'", null, dateValue);
            }
            tableQuery.close();
            sqlDB.insertOrThrow("\"" + newMessage.date + "\"", null, messageValues);

            Cursor rowId = sqlDB.rawQuery("SELECT \"rowid\" FROM \"" + newMessage.date + "\" ORDER BY \"rowid\" DESC LIMIT 1", null);
            rowId.moveToNext();
            newMessage.rowid = rowId.getString(rowId.getColumnIndex("rowid"));
            if(rowId.getString(rowId.getColumnIndex("rowid")).equals("2"))
            {
                //reload date message into adapter
                final Message dateMessage = new Message();
                dateMessage.metadata = "date";
                dateMessage.date = newMessage.date;
                dateMessage.time = "AM00:00 ";
                adapter.add(dateMessage);
            }
            rowId.close();

            ContentValues dateValue = new ContentValues();
            dateValue.put("date_last_chat", newMessage.date);
            DATE_LAST_CHAT = newMessage.date;
            sqlDB.update("metadata", dateValue, null, null);
        } catch (SQLException e) {
            if(e.getMessage() != null) Log.e("SQL", e.getMessage());
        }

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    InputStream inputStream = getContentResolver().openInputStream(localFileUri);
                    OutputStream outputStream = getContentResolver().openOutputStream(copyFile.getUri());
                    if(inputStream == null  ||  outputStream == null)
                        throw new IOException("Files cant be accessed: Try launching app again.");
                    byte[] buffer = new byte[1024];
                    int length_buffer = inputStream.read(buffer);
                    do{
                        if(length_buffer <= 0) throw new IOException("File couldn't be opened.");
                        outputStream.write(buffer, 0, length_buffer);
                    }
                    while((length_buffer = inputStream.read(buffer)) != -1);
                    outputStream.flush();
                    outputStream.close();
                    inputStream.close();
                } catch (final IOException e) {
                    e.printStackTrace();
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(ChatActivity.this, "Couldn't access storage: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            }
        }).start();
        adapter.add(newMessage);
        messageListView.smoothScrollToPosition(messageListView.getCount() - 1);
    }

    public void sendMessage(@SuppressWarnings("unused") View view) {
        final Message newMessage = new Message();

        final String text = chatbox.getText().toString().trim();
        if (text.equals("")) return;
        newMessage.text = text;
        chatbox.setText("");

        Calendar currentTime = Calendar.getInstance();
        newMessage.time = (currentTime.get(Calendar.AM_PM) == Calendar.AM ? "AM" : "PM") +
                (currentTime.get(Calendar.HOUR) < 10 ? "0" : "") + (currentTime.get(Calendar.HOUR)) +
                ":" + (currentTime.get(Calendar.MINUTE) < 10 ? "0" : "") + currentTime.get(Calendar.MINUTE) + " ";
        newMessage.date = (currentTime.get(Calendar.YEAR) * 10000 +
                (currentTime.get(Calendar.MONTH) + 1) * 100 +
                currentTime.get(Calendar.DAY_OF_MONTH)) + "";

        if (CHAT_IS_LOADING) {
            synchronized (syncObj) {
                try {
                    syncObj.wait();
                } catch (InterruptedException e) {
                    Log.e("Thread Stopped: ", e.getMessage() == null ? "" : e.getMessage());
                }
            }
        }

        newMessage.metadata = "sending";
        newMessage.reply = chatboxReply.getText().toString();

        final String reply_metadata;
        if (((ColorDrawable) chatboxReplyBorder.getBackground()).getColor() == Color.parseColor("#EEEEEE"))
            reply_metadata = "";
        else if (((ColorDrawable) chatboxReplyBorder.getBackground()).getColor() == Color.parseColor("#FFFFFF"))
            reply_metadata = "sent";
        else
            reply_metadata = "received";
        newMessage.reply_metadata = reply_metadata;
        replyMessageCancelled(new View(this));

        newMessage.content = "";

        ContentValues messageValues = new ContentValues();
        messageValues.put("text", newMessage.text);
        messageValues.put("date", newMessage.date);
        messageValues.put("time", newMessage.time);
        messageValues.put("metadata", newMessage.metadata);
        messageValues.put("reply", newMessage.reply);
        messageValues.put("reply_metadata", newMessage.reply_metadata);
        messageValues.put("content", newMessage.content);
        try {
            Cursor tableQuery = sqlDB.rawQuery("SELECT name FROM sqlite_master WHERE type='table' AND name=?", new String[]{newMessage.date});
            if (tableQuery.getCount() == 0) {
                sqlDB.execSQL("CREATE TABLE IF NOT EXISTS '" + newMessage.date + "'(text TEXT, date TEXT, time TEXT, metadata TEXT, content TEXT, reply TEXT, reply_metadata TEXT)");

                ContentValues dateValue = new ContentValues();
                dateValue.put("metadata", "date");
                dateValue.put("date", newMessage.date);
                dateValue.put("text", DATE_LAST_CHAT);
                dateValue.put("time", "AM00:00 ");
                dateValue.put("reply", "");
                dateValue.put("reply_metadata", "");
                dateValue.put("content", "");
                sqlDB.insertOrThrow("'" + newMessage.date + "'", null, dateValue);
            }
            tableQuery.close();
            sqlDB.insertOrThrow("\"" + newMessage.date + "\"", null, messageValues);

            Cursor rowId = sqlDB.rawQuery("SELECT \"rowid\" FROM \"" + newMessage.date + "\" ORDER BY \"rowid\" DESC LIMIT 1", null);
            rowId.moveToNext();
            newMessage.rowid = rowId.getString(rowId.getColumnIndex("rowid"));
            if(rowId.getString(rowId.getColumnIndex("rowid")).equals("2"))
            {
                //reload date message into adapter
                final Message dateMessage = new Message();
                dateMessage.metadata = "date";
                dateMessage.date = newMessage.date;
                dateMessage.time = "AM00:00 ";
                adapter.add(dateMessage);
            }
            rowId.close();

            ContentValues dateValue = new ContentValues();
            dateValue.put("date_last_chat", newMessage.date);
            DATE_LAST_CHAT = newMessage.date;
            sqlDB.update("metadata", dateValue, null, null);
        } catch (SQLException e) {
            Log.e("SQL", Objects.requireNonNull(e.getMessage()));
        }

        syncMessage(newMessage);
        adapter.add(newMessage);
        messageListView.smoothScrollToPosition(messageListView.getCount() - 1);
    }

    public void callFriend(View view){
        Intent callIntent = new Intent(this, CallActivity.class);
        callIntent.putExtra("userUid", userUid);
        callIntent.putExtra("friendUid", friendUid);
        callIntent.putExtra("initiator", true);
        startActivity(callIntent);
    }

    //helper methods
    private String getFileName(Uri uri) {
        String result = null;
        if (uri.getScheme() != null  &&  uri.getScheme().equals("content")) {
            try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    result = cursor.getString(cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME));
                }
            }
        }
        if (result == null) {
            result = uri.getPath();
            if (result == null) return "file.txt";
            int cut = result.lastIndexOf('/');
            if (cut != -1) {
                result = result.substring(cut + 1);
            }
        }
        return result;
    }

    private void checkWritePermission() {
        if(Build.VERSION.SDK_INT >= 23) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_DENIED) {
                requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_WRITE_PERMISSION);
            }
        }
    }

    private String getMonth(int month) {
        switch (month - 1) {
            case Calendar.JANUARY:
                return "January";
            case Calendar.FEBRUARY:
                return "February";
            case Calendar.MARCH:
                return "March";
            case Calendar.APRIL:
                return "April";
            case Calendar.MAY:
                return "May";
            case Calendar.JUNE:
                return "June";
            case Calendar.JULY:
                return "July";
            case Calendar.AUGUST:
                return "August";
            case Calendar.SEPTEMBER:
                return "September";
            case Calendar.OCTOBER:
                return "October";
            case Calendar.NOVEMBER:
                return "November";
            case Calendar.DECEMBER:
                return "December";
        }
        return "";
    }

    private Message convertToPojo(Object obj) {
        Map message = (Map) obj;
        Message mMessage = new Message();
        mMessage.text = Objects.requireNonNull(message.get("text")).toString();
        mMessage.date = Objects.requireNonNull(message.get("date")).toString();
        mMessage.time = Objects.requireNonNull(message.get("time")).toString();
        mMessage.metadata = Objects.requireNonNull(message.get("metadata")).toString();
        mMessage.reply = Objects.requireNonNull(message.get("reply")).toString();
        mMessage.reply_metadata = Objects.requireNonNull(message.get("reply_metadata")).toString();
        mMessage.content = Objects.requireNonNull(message.get("content")).toString();
        return mMessage;
    }

    private void addAtCorrectPosition(final Message newMessage) {
        boolean item_added = false;
        if (newMessage.metadata.equals("date")) {
            for (int i = 0; i < adapter.getCount() - 1; i++) {
                if (newMessage.date.compareTo(Objects.requireNonNull(adapter.getItem(i)).date) <= 0) {
                    adapter.insert(newMessage, i);
                    item_added = true;
                    break;
                }
            }
        } else {
            for (int i = 1; i < adapter.getCount(); i++) {
                if (Objects.requireNonNull(adapter.getItem(i - 1)).date.equals(Objects.requireNonNull(adapter.getItem(i)).date)) {
                    if ((Objects.requireNonNull(adapter.getItem(i - 1)).date.equals(newMessage.date)) && (Objects.requireNonNull(adapter.getItem(i - 1)).time.compareTo(newMessage.time) <= 0) && (newMessage.time.compareTo(Objects.requireNonNull(adapter.getItem(i)).time) < 0)) {
                        adapter.insert(newMessage, i);
                        item_added = true;
                        break;
                    }
                } else {
                    if ((Objects.requireNonNull(adapter.getItem(i - 1)).date.equals(newMessage.date)) && (Objects.requireNonNull(adapter.getItem(i - 1)).time.compareTo(newMessage.time) <= 0)) {
                        adapter.insert(newMessage, i);
                        item_added = true;
                        break;
                    }
                }
            }
        }
        if (!item_added)
            adapter.add(newMessage);
    }

    private DocumentFile getFileDirectoryForFriend(String friendId) throws IOException {
        String userId = user.getUid();
        DocumentFile output;
        if(appStorageDir.canWrite())
        {
            try {
                if (appStorageDir.findFile("Nosk") == null) appStorageDir.createDirectory("Nosk");
                output = appStorageDir.findFile("Nosk");
                if (Objects.requireNonNull(output).findFile("users") == null) output.createDirectory("users");
                output = output.findFile("users");
                if (Objects.requireNonNull(output).findFile(userId) == null) output.createDirectory(userId);
                output = output.findFile(userId);
                if (Objects.requireNonNull(output).findFile("friends") == null) output.createDirectory("friends");
                output = output.findFile("friends");
                if (Objects.requireNonNull(output).findFile(friendId) == null) output.createDirectory(friendId);
                output = output.findFile(friendId);
                return output;
            }
            catch (NullPointerException e){
                throw new IOException("Couldn't access storage");
            }
        }
        return null;
    }

    private void syncMessage(final Message message) {
        final String messageId = message.date + message.rowid;
        if(syncCacheMessageId.contains(messageId)) return;
        syncCacheMessageId.add(messageId);

        db.runTransaction(new Transaction.Function<Void>() {
            @Nullable
            @Override
            public Void apply(@NonNull Transaction transaction) throws FirebaseFirestoreException {
                List messageList = (List) transaction.get(db.collection("users").document(friendUid).collection("friends").document(userUid)).get("chat");
                if (messageList == null) messageList = new ArrayList();

                Message newMessage = new Message();
                newMessage.text = message.text;
                newMessage.time = message.time;
                newMessage.date = message.date;
                newMessage.metadata = "received";
                newMessage.reply = message.reply;
                newMessage.content = message.content;
                if (message.reply_metadata.equals("sent")) newMessage.reply_metadata = "received";
                else if (message.reply_metadata.equals("received")) newMessage.reply_metadata = "sent";
                else newMessage.reply_metadata = "";

                //convert untyped list to typed list
                List<Message> newMessageList = new ArrayList<>();
                for (Object message : messageList)
                    newMessageList.add(convertToPojo(message));
                newMessageList.add(newMessage);

                transaction.update(db.collection("users").document(friendUid).collection("friends").document(userUid), "chat", newMessageList);
                return null;
            }
        }).addOnCompleteListener(new OnCompleteListener<Void>() {
            @Override
            public void onComplete(@NonNull Task<Void> task) {
                if (task.isSuccessful())
                {
                    Toast.makeText(ChatActivity.this, "success", Toast.LENGTH_SHORT).show();
                    message.metadata = "sent";
                    adapter.notifyDataSetChanged();
                    ContentValues metadataValue = new ContentValues();
                    metadataValue.put("metadata", "sent");
                    sqlDB.update("\"" + message.date + "\"", metadataValue, "\"rowid\" = " + "\"" + message.rowid + "\"", null);
                }
                else
                    Toast.makeText(ChatActivity.this, "fail : " + Objects.requireNonNull(task.getException()).getMessage(), Toast.LENGTH_LONG).show();
                syncCacheMessageId.remove(messageId);
            }
        });
    }

    private void syncAllMessages() {
        for(int i=0; i<adapter.getCount(); i++)
        {
            Message msg = adapter.getItem(i);
            if(msg!=null  &&  !msg.content.equals("file")  &&  msg.metadata.equals("sending")) syncMessage(msg);
        }
    }
}
