package com.myapp;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;

public class HomeActivity extends Activity {

    static class ChatItem {
        String name, lastMsg, time, avatar;
        int badge;
        ChatItem(String name, String lastMsg, String time, String avatar, int badge) {
            this.name = name; this.lastMsg = lastMsg; this.time = time; this.avatar = avatar; this.badge = badge;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        List<ChatItem> chats = new ArrayList<>();
        chats.add(new ChatItem("Sita Gurung", "Hello! 👋", "9:32 AM", "S", 2));
        chats.add(new ChatItem("Rahul Magar", "Video Call", "Yesterday", "R", 0));
        chats.add(new ChatItem("Family Group", "Good Morning ☀️", "Yesterday", "F", 5));
        chats.add(new ChatItem("Dipak Thapa", "Ok bro", "Mon", "D", 0));
        chats.add(new ChatItem("Business Team", "Project update?", "Mon", "B", 0));

        RecyclerView recycler = findViewById(R.id.recyclerChats);
        recycler.setLayoutManager(new LinearLayoutManager(this));
        recycler.setAdapter(new ChatListAdapter(chats));

        findViewById(R.id.navProfile).setOnClickListener(v ->
                startActivity(new Intent(this, ProfileActivity.class)));

        findViewById(R.id.navCalls).setOnClickListener(v ->
                startActivity(new Intent(this, ChatActivity.class)));

        findViewById(R.id.navSearch).setOnClickListener(v ->
                startActivity(new Intent(this, ChatActivity.class)));
    }

    class ChatListAdapter extends RecyclerView.Adapter<ChatListAdapter.VH> {
        List<ChatItem> items;
        ChatListAdapter(List<ChatItem> items) { this.items = items; }

        class VH extends RecyclerView.ViewHolder {
            TextView txtAvatar, txtName, txtLastMsg, txtTime, txtBadge;
            VH(View v) {
                super(v);
                txtAvatar = v.findViewById(R.id.txtAvatar);
                txtName = v.findViewById(R.id.txtName);
                txtLastMsg = v.findViewById(R.id.txtLastMsg);
                txtTime = v.findViewById(R.id.txtTime);
                txtBadge = v.findViewById(R.id.txtBadge);
            }
        }

        @Override public VH onCreateViewHolder(ViewGroup parent, int viewType) {
            return new VH(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_chat, parent, false));
        }

        @Override public void onBindViewHolder(VH h, int pos) {
            ChatItem item = items.get(pos);
            h.txtAvatar.setText(item.avatar);
            h.txtName.setText(item.name);
            h.txtLastMsg.setText(item.lastMsg);
            h.txtTime.setText(item.time);
            if (item.badge > 0) {
                h.txtBadge.setVisibility(View.VISIBLE);
                h.txtBadge.setText(String.valueOf(item.badge));
            } else {
                h.txtBadge.setVisibility(View.GONE);
            }
            h.itemView.setOnClickListener(v ->
                    startActivity(new Intent(HomeActivity.this, ChatActivity.class)));
        }

        @Override public int getItemCount() { return items.size(); }
    }
}
