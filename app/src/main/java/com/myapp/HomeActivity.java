package com.myapp;

import android.content.Intent;
import android.os.Handler;
import android.os.Looper;

/** Launcher screen: new splash design, then the Messenger screens. */
public class HomeActivity extends MessengerActivity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable leaveSplash = () -> {
        if ("splash".equals(page)) render("login");
    };

    @Override void render(String next) {
        if ("splash".equals(next)) {
            page = next;
            setContentView(new SplashView(this));
            handler.postDelayed(leaveSplash, 2200);
            return;
        }
        super.render(next);
        if ("chats".equals(next)) {
            // Temporary: the old shared room, so chat and video can be tested on two phones.
            btn2("टेस्ट चैट और वीडियो कॉल (खुला room)",
                    () -> startActivity(new Intent(this, ChatActivity.class)));
        }
    }

    @Override protected void onDestroy() {
        handler.removeCallbacks(leaveSplash);
        super.onDestroy();
    }
}
