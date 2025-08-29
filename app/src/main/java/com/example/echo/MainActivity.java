package com.example.echo;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.PopupMenu;
import android.widget.TextView;

import com.google.firebase.auth.FirebaseAuth;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class MainActivity extends AppCompatActivity {
    RecyclerView recyclerView;
    TextView welcomeTextView;
    EditText messageEditText;
    ImageButton sendButton, menubtn;
    List<Message> messageList;
    MessageAdapter messageAdapter;

    public static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    OkHttpClient client = new OkHttpClient.Builder()
            .readTimeout(60, TimeUnit.SECONDS)
            .build();

    // Improved system prompt: stops bot from giving explanations for greetings
    public static final String SYSTEM_PROMPT =
            "You are a friendly, helpful AI assistant. Always answer ONLY the question asked, directly and concisely, in a conversational and natural way. Do NOT provide extra facts, definitions, alternate meanings, sources, references, or citations. Keep your response short and to the point, as if talking to a friend. Never add options or over-explain unless the user specifically asks for more information.";
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        menubtn = findViewById(R.id.menu_btn);
        menubtn.setOnClickListener(v -> showMenu());

        messageList = new ArrayList<>();
        recyclerView = findViewById(R.id.recycler_view);
        welcomeTextView = findViewById(R.id.welcome_text);
        messageEditText = findViewById(R.id.message_edit_text);
        sendButton = findViewById(R.id.send_btn);

        messageAdapter = new MessageAdapter(messageList);
        recyclerView.setAdapter(messageAdapter);
        LinearLayoutManager llm = new LinearLayoutManager(this);
        llm.setStackFromEnd(true);
        recyclerView.setLayoutManager(llm);

        // Show initial bot greeting in chat
        addToChat("Hello! How can I help you today?", Message.SENT_BY_BOT);
        welcomeTextView.setVisibility(View.GONE);

        sendButton.setOnClickListener(v -> {
            String question = messageEditText.getText().toString().trim();
            if (!question.isEmpty()) {
                addToChat(question, Message.SENT_BY_ME);
                messageEditText.setText("");
                callAPI(question);
                welcomeTextView.setVisibility(View.GONE);
            }
        });
    }

    void showMenu() {
        PopupMenu popupMenu = new PopupMenu(MainActivity.this, menubtn);
        popupMenu.getMenu().add("Logout");
        popupMenu.show();
        popupMenu.setOnMenuItemClickListener(menuItem -> {
            if (menuItem.getTitle().equals("Logout")) {
                FirebaseAuth.getInstance().signOut();
                startActivity(new Intent(MainActivity.this, LoginActivity.class));
                finish();
                return true;
            }
            return false;
        });
    }

    void addToChat(String message, String sentBy) {
        runOnUiThread(() -> {
            messageList.add(new Message(message, sentBy));
            messageAdapter.notifyDataSetChanged();
            recyclerView.smoothScrollToPosition(messageAdapter.getItemCount());
        });
    }

    void addResponse(String response) {
        if (!messageList.isEmpty() && messageList.get(messageList.size() - 1).getMessage().equals("Typing...")) {
            messageList.remove(messageList.size() - 1);
        }
        addToChat(response, Message.SENT_BY_BOT);
    }

    void callAPI(String question) {
        addToChat("Typing...", Message.SENT_BY_BOT);

        JSONObject jsonBody = new JSONObject();
        try {
            jsonBody.put("model", "sonar-pro"); // replace with your actual model if needed
            JSONArray messages = new JSONArray();

            // Strong system prompt, stops definitions
            JSONObject systemMsg = new JSONObject();
            systemMsg.put("role", "system");
            systemMsg.put("content", SYSTEM_PROMPT);
            messages.put(systemMsg);

            JSONObject userMsg = new JSONObject();
            userMsg.put("role", "user");
            userMsg.put("content", question);
            messages.put(userMsg);

            jsonBody.put("messages", messages);
            jsonBody.put("temperature", 0.7);
        } catch (JSONException e) {
            e.printStackTrace();
            addResponse("Failed to create request JSON.");
            return;
        }

        RequestBody body = RequestBody.create(jsonBody.toString(), JSON);
        Request request = new Request.Builder()
                .url("https://api.perplexity.ai/chat/completions")
                .header("Authorization", "Bearer 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")
                .header("Content-Type", "application/json")
                .post(body)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                addResponse("Failed to load response: " + e.getMessage());
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (response.isSuccessful()) {
                    try {
                        JSONObject responseJson = new JSONObject(response.body().string());
                        JSONArray choices = responseJson.getJSONArray("choices");
                        String content = choices.getJSONObject(0)
                                .getJSONObject("message")
                                .getString("content");
                        addResponse(content.trim());
                    } catch (JSONException e) {
                        e.printStackTrace();
                        addResponse("Error parsing response JSON.");
                    }
                } else {
                    addResponse("Failed to load response: " + response.body().string());
                }
            }
        });
    }
}
