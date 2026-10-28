package com.replenest.app;

import android.app.Activity;
import android.app.Instrumentation;
import android.os.Bundle;
import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Exercises production HTTP construction against an in-process HTTPS transport fixture. */
public final class ApiContractRunner {
    final Instrumentation instrumentation;
    ApiContractRunner(Instrumentation instrumentation) { this.instrumentation = instrumentation; }
    android.content.Context getTargetContext() { return instrumentation.getTargetContext(); }
    void finish(int code, Bundle out) { instrumentation.finish(code, out); }
    int checks, scenario;
    final ArrayList<FakeConnection> requests = new ArrayList<>();
    final String fakeKey = "test-only-not-a-real-key";

    public void run() {
        Bundle out = new Bundle();
        try {
            URL.setURLStreamHandlerFactory(protocol -> "https".equals(protocol) ? new URLStreamHandler() {
                @Override protected URLConnection openConnection(URL url) {
                    FakeConnection connection = new FakeConnection(url); requests.add(connection); return connection;
                }
            } : null);
            Item future = new Item(); future.name = "clock-change"; future.anchor = java.time.LocalDate.now().plusDays(1);
            check(Item.from(future.json()).remaining(java.time.LocalDate.now()) == future.stock, "Clock rollback does not make persisted data unreadable");
            scenario = 0;
            String[] models = AiClient.listModels("https://fixture.invalid/v1", fakeKey, "");
            check(Arrays.equals(models, new String[]{"chat-a", "chat-b"}), "Model deduplication/order");
            check(requests.get(0).url().getPath().equals("/v1/models"), "Derived models route");
            check(requests.get(0).getRequestMethod().equals("GET"), "Model request method");
            check(requests.get(0).getRequestProperty("Authorization").equals("Bearer " + fakeKey), "Model authentication");
            check(!requests.get(0).getInstanceFollowRedirects(), "Redirects disabled");
            scenario = 1; requests.clear();
            models = AiClient.listModels("https://fixture.invalid/compatible-mode/v1", fakeKey, "https://fixture.invalid/api/v1/models");
            check(models.length == 150 && models[0].equals("qwen-0") && models[149].equals("qwen-149"), "All paginated models returned without skipping");
            check(requests.size() == 3, "Default page probed then refetched at consistent size");
            check(requests.get(1).url().getQuery().contains("page_no=1"), "Pagination restarted at page one");
            check(requests.get(2).url().getQuery().contains("page_size=100"), "Pagination uses matching page size");
            scenario = 2; requests.clear();
            reject(() -> AiClient.listModels("https://fixture.invalid/v1", fakeKey, ""), "429");
            check(requests.size() == 1, "No automatic retry on status failure");
            reject(() -> AiClient.listModels("http://fixture.invalid/v1", fakeKey, ""), null);
            reject(() -> AiClient.listModels("https://fixture.invalid/v1", "", ""), null);
            scenario = 3; requests.clear();
            reject(() -> AiClient.listModels("https://fixture.invalid/v1", fakeKey, ""), "302");
            check(requests.size() == 1, "Redirect not followed");

            AiSettings.clear(getTargetContext());
            requests.clear();
            reject(() -> AiClient.suggest(getTargetContext(), "coffee"), null);
            check(requests.isEmpty(), "Disabled AI sends no request");
            AiSettings.save(getTargetContext(), true, "https://fixture.invalid/v1", "chat-a", fakeKey);
            scenario = 4;
            JSONObject suggestion = AiClient.suggest(getTargetContext(), "咖啡胶囊30粒一盒，剩24粒，每天2粒，提前3天提醒");
            check(suggestion.getDouble("stock") == 24, "Suggestion parsed and validated");
            FakeConnection completion = requests.get(0);
            check(completion.getRequestMethod().equals("POST") && completion.url().getPath().equals("/v1/chat/completions"), "Completion request route/method");
            JSONObject body = new JSONObject(completion.output.toString("UTF-8"));
            check(body.getString("model").equals("chat-a"), "Selected model used");
            check(body.getJSONArray("messages").length() == 2 && !body.toString().contains(fakeKey), "Only system and user input in body");
            check(completion.getConnectTimeout() == 10000 && completion.getReadTimeout() == 30000, "Bounded request timeouts");
            scenario = 5;
            reject(() -> AiClient.suggest(getTargetContext(), "coffee"), null);
            scenario = 2;
            reject(() -> AiClient.suggest(getTargetContext(), "coffee"), "429");
            AiSettings.clear(getTargetContext());
            out.putString("success", "API contract checks passed (mock transport; no provider calls)");
            out.putInt("checks", checks); finish(Activity.RESULT_OK, out);
        } catch (Throwable e) {
            try { AiSettings.clear(getTargetContext()); } catch (Exception ignored) { }
            out.putString("failure", e.getClass().getSimpleName() + ": " + e.getMessage()); out.putInt("checks", checks); finish(Activity.RESULT_CANCELED, out);
        }
    }

    void check(boolean valid, String label) { checks++; if (!valid) throw new AssertionError(label); }
    interface Action { void run() throws Exception; }
    void reject(Action action, String status) throws Exception {
        checks++;
        try { action.run(); } catch (Exception e) {
            if (status != null && (e.getMessage() == null || !e.getMessage().contains(status))) throw new AssertionError("Expected status " + status);
            if (e.getMessage() != null && e.getMessage().contains(fakeKey)) throw new AssertionError("Error leaked credential");
            return;
        }
        throw new AssertionError("Expected rejected request/result");
    }

    final class FakeConnection extends HttpURLConnection {
        final ByteArrayOutputStream output = new ByteArrayOutputStream();
        FakeConnection(URL url) { super(url); }
        URL url() { return url; }
        @Override public void connect() {}
        @Override public void disconnect() {}
        @Override public boolean usingProxy() { return false; }
        @Override public OutputStream getOutputStream() { return output; }
        @Override public int getResponseCode() { return scenario == 2 ? 429 : scenario == 3 ? 302 : 200; }
        @Override public InputStream getInputStream() throws IOException {
            try {
                JSONObject response;
                if (scenario == 1) {
                    String query = url.getQuery(); int size = query == null ? 20 : 100;
                    int page = query != null && query.contains("page_no=2") ? 2 : 1;
                    JSONArray models = new JSONArray();
                    int begin = (page - 1) * size;
                    for (int n = begin; n < Math.min(150, begin + size); n++) models.put(new JSONObject().put("model", "qwen-" + n));
                    response = new JSONObject().put("output", new JSONObject().put("total",150).put("page_no",page).put("page_size",size).put("models",models));
                } else if (scenario == 4 || scenario == 5) {
                    JSONObject data = new JSONObject().put("name","咖啡胶囊").put("brand","").put("kind","consumable").put("unit","粒")
                        .put("packSize",30).put("stock",scenario == 5 ? -1 : 24).put("daysPerPack",15).put("leadDays",3).put("explanation","每天两粒").put("question","");
                    response = new JSONObject().put("choices", new JSONArray().put(new JSONObject().put("message",new JSONObject().put("content",data.toString()))));
                } else response = new JSONObject("{\"data\":[{\"id\":\"chat-a\"},{\"id\":\"chat-a\"},{\"id\":\"chat-b\"}]}");
                return new ByteArrayInputStream(response.toString().getBytes(StandardCharsets.UTF_8));
            } catch (Exception e) { throw new IOException("Invalid fixture"); }
        }
    }
}
