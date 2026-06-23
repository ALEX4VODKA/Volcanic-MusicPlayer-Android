package com.volcanic.musicplayer;

import android.Manifest;
import android.app.Activity;
import android.content.ContentUris;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.volcanic.musicplayer.decoder.DecodedAudio;
import com.volcanic.musicplayer.decoder.PrivateContainerDecoder;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public class MainActivity extends Activity {
    private static final int REQ_PICK_AUDIO = 4001;
    private static final int REQ_READ_AUDIO = 4002;

    private final ArrayList<AudioTrack> tracks = new ArrayList<>();
    private final Set<String> knownUris = new HashSet<>();

    private TrackAdapter adapter;
    private TextView statusText;
    private TextView nowTitle;
    private TextView nowMeta;
    private Button playButton;
    private MediaPlayer player;
    private int currentIndex = -1;
    private boolean prepared = false;
    private File playlistFile;
    private File inputDir;
    private File outputDir;

    private final int bg = Color.rgb(5, 7, 10);
    private final int panel = Color.rgb(17, 23, 32);
    private final int panelSoft = Color.rgb(23, 31, 43);
    private final int cyan = Color.rgb(111, 247, 255);
    private final int red = Color.rgb(255, 63, 87);
    private final int green = Color.rgb(93, 242, 169);
    private final int amber = Color.rgb(255, 199, 94);
    private final int text = Color.rgb(246, 248, 251);
    private final int muted = Color.rgb(150, 164, 190);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        playlistFile = new File(getFilesDir(), "playlist.json");
        File musicRoot = getExternalFilesDir(Environment.DIRECTORY_MUSIC);
        inputDir = new File(getFilesDir(), "VolcanicInput");
        outputDir = new File(musicRoot != null ? musicRoot : getFilesDir(), "VolcanicOutput");
        if (!inputDir.exists()) {
            inputDir.mkdirs();
        }
        if (!outputDir.exists()) {
            outputDir.mkdirs();
        }
        buildUi();
        loadPlaylist();
        refreshUi();
    }

    @Override
    protected void onDestroy() {
        releasePlayer();
        super.onDestroy();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_PICK_AUDIO && resultCode == RESULT_OK && data != null) {
            importPickedAudio(data);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_READ_AUDIO && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            scanMediaStore();
        } else if (requestCode == REQ_READ_AUDIO) {
            toast("Audio permission denied");
        }
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(18), dp(18), dp(34));
        root.setBackgroundColor(bg);
        setContentView(root);

        LinearLayout header = card(LinearLayout.HORIZONTAL, dp(16), dp(14));
        header.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(header, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView logo = textView("V", 22, Color.BLACK, Typeface.BOLD);
        logo.setGravity(Gravity.CENTER);
        logo.setBackground(round(red, dp(12), red));
        header.addView(logo, new LinearLayout.LayoutParams(dp(52), dp(52)));

        LinearLayout titleBlock = new LinearLayout(this);
        titleBlock.setOrientation(LinearLayout.VERTICAL);
        titleBlock.setPadding(dp(14), 0, 0, 0);
        header.addView(titleBlock, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView appTitle = textView("Volcanic", 22, text, Typeface.BOLD);
        appTitle.setSingleLine(true);
        appTitle.setEllipsize(TextUtils.TruncateAt.END);
        titleBlock.addView(appTitle);
        titleBlock.addView(textView("Android decoder", 13, muted, Typeface.NORMAL));

        statusText = textView("No tracks", 13, cyan, Typeface.BOLD);
        statusText.setGravity(Gravity.END);
        header.addView(statusText, new LinearLayout.LayoutParams(dp(92), ViewGroup.LayoutParams.WRAP_CONTENT));

        HorizontalScrollView actionScroll = new HorizontalScrollView(this);
        actionScroll.setHorizontalScrollBarEnabled(false);
        root.addView(actionScroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setPadding(0, dp(12), 0, dp(12));
        actionScroll.addView(actions);
        actions.addView(actionButton("Import", this::openAudioPicker));
        actions.addView(actionButton("Scan", v -> requestScan()));
        actions.addView(actionButton("MP3 Output", v -> processAllMp3Outputs()));
        actions.addView(actionButton("Clear", v -> clearPlaylist()));
        actions.addView(actionButton("Save", v -> savePlaylist()));

        ListView listView = new ListView(this);
        listView.setDivider(null);
        listView.setCacheColorHint(Color.TRANSPARENT);
        listView.setBackgroundColor(Color.TRANSPARENT);
        adapter = new TrackAdapter();
        listView.setAdapter(adapter);
        listView.setOnItemClickListener((parent, view, position, id) -> playIndex(position));
        listView.setOnItemLongClickListener((parent, view, position, id) -> {
            removeAt(position);
            return true;
        });
        root.addView(listView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        LinearLayout playerBar = card(LinearLayout.VERTICAL, dp(16), dp(12));
        root.addView(playerBar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        nowTitle = textView("Nothing playing", 17, text, Typeface.BOLD);
        nowTitle.setSingleLine(true);
        nowTitle.setEllipsize(TextUtils.TruncateAt.END);
        playerBar.addView(nowTitle);

        nowMeta = textView("Tap a track. MP3 files are copied to the output folder.", 12, muted, Typeface.NORMAL);
        nowMeta.setSingleLine(true);
        nowMeta.setEllipsize(TextUtils.TruncateAt.END);
        playerBar.addView(nowMeta);

        LinearLayout controls = new LinearLayout(this);
        controls.setGravity(Gravity.CENTER);
        controls.setPadding(0, dp(10), 0, 0);
        playerBar.addView(controls, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        controls.addView(controlButton("Prev", v -> previousTrack()));
        playButton = controlButton("Play", v -> togglePlay());
        playButton.setBackground(round(red, dp(18), red));
        controls.addView(playButton);
        controls.addView(controlButton("Next", v -> nextTrack()));
    }

    private void openAudioPicker(View ignored) {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"audio/*", "application/octet-stream"});
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(intent, REQ_PICK_AUDIO);
    }

    private void importPickedAudio(Intent data) {
        int before = tracks.size();
        if (data.getClipData() != null) {
            for (int i = 0; i < data.getClipData().getItemCount(); i++) {
                addDocumentUri(data.getClipData().getItemAt(i).getUri());
            }
        } else if (data.getData() != null) {
            addDocumentUri(data.getData());
        }
        if (tracks.size() > before) {
            processAllMp3Outputs();
            savePlaylist();
            refreshUi();
            toast("Imported " + (tracks.size() - before) + " track(s)");
        }
    }

    private void addDocumentUri(Uri uri) {
        try {
            getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (Exception ignored) {
            // Some providers do not expose persistable permissions.
        }
        String name = displayName(uri);
        AudioTrack track = new AudioTrack(name, "Imported file", uri.toString(), extensionOf(name));
        try {
            track.localPath = copyUriToInput(uri, name).getAbsolutePath();
            track.status = "Imported";
            track.detail = "Local copy ready";
        } catch (Exception error) {
            track.status = "Import failed";
            track.detail = error.getClass().getSimpleName() + ": " + valueOr(error.getMessage(), "copy failed");
        }
        addTrack(track);
    }

    private void requestScan() {
        String permission = Build.VERSION.SDK_INT >= 33 ? Manifest.permission.READ_MEDIA_AUDIO : Manifest.permission.READ_EXTERNAL_STORAGE;
        if (Build.VERSION.SDK_INT >= 23 && checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{permission}, REQ_READ_AUDIO);
            return;
        }
        scanMediaStore();
    }

    private void scanMediaStore() {
        int before = tracks.size();
        String[] projection = {
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.DISPLAY_NAME
        };
        String selection = MediaStore.Audio.Media.IS_MUSIC + "!=0";
        try (Cursor cursor = getContentResolver().query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, projection, selection, null, MediaStore.Audio.Media.DATE_ADDED + " DESC")) {
            if (cursor == null) {
                toast("No media library cursor");
                return;
            }
            int idIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID);
            int titleIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE);
            int artistIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST);
            int displayIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME);
            while (cursor.moveToNext()) {
                long id = cursor.getLong(idIndex);
                String display = cursor.getString(displayIndex);
                String title = valueOr(cursor.getString(titleIndex), display);
                String artist = valueOr(cursor.getString(artistIndex), "Local music");
                Uri uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id);
                AudioTrack track = new AudioTrack(title, artist, uri.toString(), extensionOf(display));
                try {
                    track.localPath = copyUriToInput(uri, valueOr(display, title)).getAbsolutePath();
                    track.status = "Imported";
                    track.detail = "Local copy ready";
                } catch (Exception error) {
                    track.status = "Import failed";
                    track.detail = error.getClass().getSimpleName() + ": " + valueOr(error.getMessage(), "copy failed");
                }
                addTrack(track);
            }
        }
        processAllMp3Outputs();
        savePlaylist();
        refreshUi();
        toast("Scanned " + (tracks.size() - before) + " new track(s)");
    }

    private void processAllMp3Outputs() {
        int ready = 0;
        int blocked = 0;
        for (AudioTrack track : tracks) {
            if (ensureMp3Output(track)) {
                ready++;
            } else {
                blocked++;
            }
        }
        savePlaylist();
        refreshUi();
        toast("MP3 ready: " + ready + ", blocked: " + blocked);
    }

    private boolean ensureMp3Output(AudioTrack track) {
        if (track.outputPath != null && new File(track.outputPath).exists()) {
            track.status = "MP3 output ready";
            return true;
        }
        File source = sourceFile(track);
        if (source == null) {
            track.status = "Input unavailable";
            track.detail = "Permission denied or local input copy missing. Re-import the file.";
            return false;
        }
        if (PrivateContainerDecoder.isPrivateContainer(track.extension)) {
            return decodePrivateContainer(track, source);
        }
        if (!"mp3".equals(track.extension)) {
            track.status = "MP3 output blocked";
            track.detail = "No fake conversion: Android MVP can output MP3 only when the source is already MP3.";
            return false;
        }
        File target = uniqueOutputFile(track.title);
        try (InputStream input = new FileInputStream(source);
             FileOutputStream output = new FileOutputStream(target)) {
            copyStream(input, output);
            track.outputPath = target.getAbsolutePath();
            track.status = "MP3 output ready";
            track.detail = target.getAbsolutePath();
            return true;
        } catch (Exception error) {
            track.status = "MP3 output failed";
            track.detail = error.getClass().getSimpleName() + ": " + valueOr(error.getMessage(), "copy failed");
            return false;
        }
    }

    private boolean decodePrivateContainer(AudioTrack track, File source) {
        try {
            DecodedAudio decoded = PrivateContainerDecoder.decode(source, track.extension);
            if ("unknown".equals(decoded.format)) {
                track.status = "Decode failed";
                track.detail = "Decoded payload is not MP3/FLAC/WAV";
                return false;
            }

            File decodedFile = uniqueDecodedFile(track.title, decoded.format);
            try (FileOutputStream output = new FileOutputStream(decodedFile)) {
                output.write(decoded.payload);
            }
            track.decodedPath = decodedFile.getAbsolutePath();

            if (decoded.isMp3()) {
                File target = uniqueOutputFile(track.title);
                try (InputStream input = new FileInputStream(decodedFile);
                     FileOutputStream output = new FileOutputStream(target)) {
                    copyStream(input, output);
                }
                track.outputPath = target.getAbsolutePath();
                track.status = "MP3 output ready";
                track.detail = target.getAbsolutePath();
                return true;
            }

            track.status = "Decoded " + decoded.format.toUpperCase(Locale.ROOT);
            track.detail = "Decoded playable file: " + decodedFile.getAbsolutePath() + ". MP3 encoding is not bundled in Android yet.";
            return false;
        } catch (Exception error) {
            track.status = "Decode failed";
            track.detail = error.getClass().getSimpleName() + ": " + valueOr(error.getMessage(), "private container decode failed");
            return false;
        }
    }

    private void addTrack(AudioTrack track) {
        if (knownUris.add(track.uri)) {
            tracks.add(track);
        }
    }

    private void playIndex(int index) {
        if (index < 0 || index >= tracks.size()) {
            return;
        }
        currentIndex = index;
        AudioTrack track = tracks.get(index);
        releasePlayer();
        prepared = false;

        Uri playUri = playbackUri(track);
        if (playUri == null) {
            track.status = "Playback blocked";
            track.detail = "Select an MP3 source or use the desktop converter for " + track.extension.toUpperCase(Locale.ROOT);
            refreshUi();
            toast(track.detail);
            return;
        }

        player = new MediaPlayer();
        try {
            player.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build());
            player.setDataSource(this, playUri);
            player.setOnPreparedListener(mp -> {
                prepared = true;
                mp.start();
                track.status = "Playing";
                track.detail = playableLabel(track);
                refreshUi();
            });
            player.setOnCompletionListener(mp -> {
                if (currentIndex >= 0 && currentIndex < tracks.size()) {
                    AudioTrack done = tracks.get(currentIndex);
                    done.status = done.outputPath != null ? "MP3 output ready" : "Playback finished";
                }
                refreshUi();
            });
            player.setOnErrorListener((mp, what, extra) -> {
                track.status = "Playback failed";
                track.detail = "MediaPlayer error " + what + "/" + extra;
                releasePlayer();
                refreshUi();
                toast(track.detail);
                return true;
            });
            player.prepareAsync();
            nowTitle.setText(track.title);
            nowMeta.setText("Loading " + playableLabel(track));
            refreshUi();
        } catch (Exception error) {
            track.status = "Playback failed";
            track.detail = error.getClass().getSimpleName() + ": " + valueOr(error.getMessage(), "cannot open source");
            toast(track.detail);
            releasePlayer();
            refreshUi();
        }
    }

    private Uri playbackUri(AudioTrack track) {
        if (track.outputPath != null && new File(track.outputPath).exists()) {
            return Uri.fromFile(new File(track.outputPath));
        }
        if ("mp3".equals(track.extension) && ensureMp3Output(track)) {
            return Uri.fromFile(new File(track.outputPath));
        }
        if (PrivateContainerDecoder.isPrivateContainer(track.extension) && ensureMp3Output(track)) {
            if (track.outputPath != null && new File(track.outputPath).exists()) {
                return Uri.fromFile(new File(track.outputPath));
            }
            if (track.decodedPath != null && new File(track.decodedPath).exists()) {
                return Uri.fromFile(new File(track.decodedPath));
            }
        }
        if (isAndroidPlayable(track.extension)) {
            File source = sourceFile(track);
            return source != null ? Uri.fromFile(source) : Uri.parse(track.uri);
        }
        return null;
    }

    private boolean isAndroidPlayable(String extension) {
        return "m4a".equals(extension) || "aac".equals(extension) || "ogg".equals(extension) || "wav".equals(extension) || "flac".equals(extension);
    }

    private String playableLabel(AudioTrack track) {
        if (track.outputPath != null) {
            return "MP3 output: " + track.outputPath;
        }
        return "Original source: " + track.extension.toUpperCase(Locale.ROOT);
    }

    private void togglePlay() {
        if (tracks.isEmpty()) {
            toast("Import audio first");
            return;
        }
        if (player == null) {
            playIndex(currentIndex >= 0 ? currentIndex : 0);
            return;
        }
        if (!prepared) {
            return;
        }
        if (player.isPlaying()) {
            player.pause();
        } else {
            player.start();
        }
        refreshUi();
    }

    private void previousTrack() {
        if (tracks.isEmpty()) {
            return;
        }
        int next = currentIndex <= 0 ? tracks.size() - 1 : currentIndex - 1;
        playIndex(next);
    }

    private void nextTrack() {
        if (tracks.isEmpty()) {
            return;
        }
        int next = currentIndex < 0 || currentIndex >= tracks.size() - 1 ? 0 : currentIndex + 1;
        playIndex(next);
    }

    private void removeAt(int position) {
        if (position < 0 || position >= tracks.size()) {
            return;
        }
        AudioTrack removed = tracks.remove(position);
        knownUris.remove(removed.uri);
        if (position == currentIndex) {
            releasePlayer();
            currentIndex = -1;
        } else if (position < currentIndex) {
            currentIndex--;
        }
        savePlaylist();
        refreshUi();
    }

    private void clearPlaylist() {
        releasePlayer();
        tracks.clear();
        knownUris.clear();
        currentIndex = -1;
        savePlaylist();
        refreshUi();
    }

    private void savePlaylist() {
        JSONArray array = new JSONArray();
        try {
            for (AudioTrack track : tracks) {
                JSONObject object = new JSONObject();
                object.put("title", track.title);
                object.put("subtitle", track.subtitle);
                object.put("uri", track.uri);
                object.put("localPath", track.localPath);
                object.put("decodedPath", track.decodedPath);
                object.put("extension", track.extension);
                object.put("outputPath", track.outputPath);
                object.put("status", track.status);
                object.put("detail", track.detail);
                array.put(object);
            }
            try (FileOutputStream output = new FileOutputStream(playlistFile)) {
                output.write(array.toString(2).getBytes(StandardCharsets.UTF_8));
            }
            statusText.setText(String.format(Locale.US, "%d tracks", tracks.size()));
        } catch (Exception error) {
            toast("Save failed");
        }
    }

    private void loadPlaylist() {
        if (!playlistFile.exists()) {
            return;
        }
        try (FileInputStream input = new FileInputStream(playlistFile)) {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] chunk = new byte[4096];
            int read;
            while ((read = input.read(chunk)) != -1) {
                buffer.write(chunk, 0, read);
            }
            JSONArray array = new JSONArray(buffer.toString(StandardCharsets.UTF_8.name()));
            for (int i = 0; i < array.length(); i++) {
                JSONObject object = array.getJSONObject(i);
                AudioTrack track = new AudioTrack(
                        object.optString("title", "Unknown audio"),
                        object.optString("subtitle", "Saved"),
                        object.optString("uri"),
                        object.optString("extension", "unknown")
                );
                track.localPath = emptyToNull(object.optString("localPath", null));
                track.decodedPath = emptyToNull(object.optString("decodedPath", null));
                track.outputPath = object.optString("outputPath", null);
                track.outputPath = emptyToNull(track.outputPath);
                track.status = object.optString("status", "Waiting");
                track.detail = object.optString("detail", "");
                addTrack(track);
            }
        } catch (Exception error) {
            toast("Load playlist failed");
        }
    }

    private void refreshUi() {
        statusText.setText(tracks.isEmpty() ? "No tracks" : tracks.size() + " tracks");
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
        if (player != null && prepared) {
            playButton.setText(player.isPlaying() ? "Pause" : "Play");
        } else {
            playButton.setText("Play");
        }
        if (currentIndex >= 0 && currentIndex < tracks.size()) {
            AudioTrack track = tracks.get(currentIndex);
            nowTitle.setText(track.title);
            nowMeta.setText(valueOr(track.detail, track.status));
        } else {
            nowTitle.setText("Nothing playing");
            nowMeta.setText(tracks.isEmpty() ? "Import audio first" : "Tap a track to play");
        }
    }

    private void releasePlayer() {
        prepared = false;
        if (player != null) {
            player.release();
            player = null;
        }
    }

    private String displayName(Uri uri) {
        String name = null;
        try (Cursor cursor = getContentResolver().query(uri, new String[]{MediaStore.MediaColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                name = cursor.getString(0);
            }
        } catch (Exception ignored) {
        }
        if (name == null || name.trim().isEmpty()) {
            name = uri.getLastPathSegment();
        }
        return valueOr(name, "Unknown audio");
    }

    private File copyUriToInput(Uri uri, String displayName) throws Exception {
        File target = uniqueInputFile(displayName);
        try (InputStream input = getContentResolver().openInputStream(uri);
             FileOutputStream output = new FileOutputStream(target)) {
            if (input == null) {
                throw new IllegalStateException("Input stream unavailable");
            }
            copyStream(input, output);
        }
        return target;
    }

    private void copyStream(InputStream input, FileOutputStream output) throws Exception {
        byte[] buffer = new byte[64 * 1024];
        int read;
        while ((read = input.read(buffer)) != -1) {
            output.write(buffer, 0, read);
        }
    }

    private File sourceFile(AudioTrack track) {
        if (track.localPath != null) {
            File local = new File(track.localPath);
            if (local.exists()) {
                return local;
            }
        }
        return null;
    }

    private String extensionOf(String name) {
        if (name == null) {
            return "unknown";
        }
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) {
            return "unknown";
        }
        return name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private File uniqueOutputFile(String title) {
        String base = sanitizeBaseName(title);
        File target = new File(outputDir, base + ".mp3");
        int counter = 2;
        while (target.exists()) {
            target = new File(outputDir, base + "-" + counter + ".mp3");
            counter++;
        }
        return target;
    }

    private File uniqueDecodedFile(String title, String extension) {
        String base = sanitizeBaseName(title);
        File target = new File(outputDir, base + ".decoded." + extension);
        int counter = 2;
        while (target.exists()) {
            target = new File(outputDir, base + "-" + counter + ".decoded." + extension);
            counter++;
        }
        return target;
    }

    private File uniqueInputFile(String title) {
        String base = sanitizeBaseName(title);
        String extension = extensionOf(title);
        if ("unknown".equals(extension)) {
            extension = "bin";
        }
        File target = new File(inputDir, base + "." + extension);
        int counter = 2;
        while (target.exists()) {
            target = new File(inputDir, base + "-" + counter + "." + extension);
            counter++;
        }
        return target;
    }

    private String sanitizeBaseName(String value) {
        String cleaned = value == null ? "track" : value.replaceAll("\\.[^.]+$", "").replaceAll("[^A-Za-z0-9._-]+", "_");
        if (cleaned.trim().isEmpty()) {
            return "track";
        }
        return cleaned;
    }

    private String valueOr(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value;
    }

    private String emptyToNull(String value) {
        return value == null || value.trim().isEmpty() || "null".equals(value) ? null : value;
    }

    private Button actionButton(String label, View.OnClickListener listener) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextColor(text);
        button.setTextSize(13);
        button.setAllCaps(false);
        button.setBackground(round(panelSoft, dp(9), Color.argb(70, 111, 247, 255)));
        button.setOnClickListener(listener);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(42));
        params.setMargins(0, 0, dp(8), 0);
        button.setLayoutParams(params);
        return button;
    }

    private Button controlButton(String label, View.OnClickListener listener) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextColor(text);
        button.setTextSize(13);
        button.setAllCaps(false);
        button.setBackground(round(panelSoft, dp(18), Color.TRANSPARENT));
        button.setOnClickListener(listener);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(92), dp(44));
        params.setMargins(dp(5), 0, dp(5), 0);
        button.setLayoutParams(params);
        return button;
    }

    private LinearLayout card(int orientation, int horizontalPadding, int verticalPadding) {
        LinearLayout view = new LinearLayout(this);
        view.setOrientation(orientation);
        view.setPadding(horizontalPadding, verticalPadding, horizontalPadding, verticalPadding);
        view.setBackground(round(panel, dp(14), Color.argb(42, 111, 247, 255)));
        return view;
    }

    private TextView textView(String value, int sp, int color, int style) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(color);
        view.setTypeface(Typeface.DEFAULT, style);
        view.setIncludeFontPadding(true);
        return view;
    }

    private GradientDrawable round(int fill, int radius, int stroke) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(radius);
        if (stroke != Color.TRANSPARENT) {
            drawable.setStroke(1, stroke);
        }
        return drawable;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private final class TrackAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return tracks.size();
        }

        @Override
        public Object getItem(int position) {
            return tracks.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            AudioTrack track = tracks.get(position);
            LinearLayout row = card(LinearLayout.HORIZONTAL, dp(12), dp(10));
            row.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            rowParams.setMargins(0, 0, 0, dp(8));
            row.setLayoutParams(rowParams);

            TextView index = textView(String.valueOf(position + 1), 14, position == currentIndex ? cyan : muted, Typeface.BOLD);
            index.setGravity(Gravity.CENTER);
            row.addView(index, new LinearLayout.LayoutParams(dp(34), dp(48)));

            LinearLayout copy = new LinearLayout(MainActivity.this);
            copy.setOrientation(LinearLayout.VERTICAL);
            copy.setPadding(dp(8), 0, 0, 0);
            row.addView(copy, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

            TextView titleView = textView(track.title, 15, text, Typeface.BOLD);
            titleView.setSingleLine(true);
            titleView.setEllipsize(TextUtils.TruncateAt.END);
            copy.addView(titleView);

            TextView subtitleView = textView(track.subtitle + " | " + track.extension.toUpperCase(Locale.ROOT), 12, muted, Typeface.NORMAL);
            subtitleView.setSingleLine(true);
            subtitleView.setEllipsize(TextUtils.TruncateAt.END);
            copy.addView(subtitleView);

            TextView statusView = textView(track.status, 11, statusColor(track), Typeface.BOLD);
            statusView.setSingleLine(true);
            statusView.setEllipsize(TextUtils.TruncateAt.END);
            copy.addView(statusView);

            TextView hint = textView("long press delete", 10, muted, Typeface.NORMAL);
            row.addView(hint);
            return row;
        }

        private int statusColor(AudioTrack track) {
            if (track.status.contains("ready") || track.status.contains("Playing")) {
                return green;
            }
            if (track.status.contains("blocked") || track.status.contains("failed")) {
                return amber;
            }
            return cyan;
        }
    }

    private static final class AudioTrack {
        final String title;
        final String subtitle;
        final String uri;
        final String extension;
        String localPath;
        String decodedPath;
        String outputPath;
        String status = "Waiting";
        String detail = "";

        AudioTrack(String title, String subtitle, String uri, String extension) {
            this.title = title;
            this.subtitle = subtitle;
            this.uri = uri;
            this.extension = extension == null ? "unknown" : extension;
        }
    }
}
