/* ====================================================================
 * Copyright (c) 2014 Alpha Cephei Inc.  All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in
 *    the documentation and/or other materials provided with the
 *    distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY ALPHA CEPHEI INC. ``AS IS'' AND
 * ANY EXPRESSED OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO,
 * THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
 * PURPOSE ARE DISCLAIMED.  IN NO EVENT SHALL CARNEGIE MELLON UNIVERSITY
 * NOR ITS EMPLOYEES BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 * ====================================================================
 */

package com.thumbjive.hellovoice;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import edu.cmu.pocketsphinx.Assets;
import edu.cmu.pocketsphinx.Hypothesis;
import edu.cmu.pocketsphinx.RecognitionListener;
import edu.cmu.pocketsphinx.SpeechRecognizer;
import edu.cmu.pocketsphinx.SpeechRecognizerSetup;
import com.thumbjive.hellovoice.R;

import static android.widget.Toast.makeText;

public class MainActivity extends Activity implements
        RecognitionListener {

    /* Named searches allow to quickly reconfigure the decoder */
    private static final String KWS_SEARCH = "wakeup";
    private static final String COMMANDS_SEARCH = "commands";

    /* Keyword we are looking for to activate menu */
//    private static final String KEYPHRASE = "oh mighty computer";
    private static final String KEYPHRASE = "hello voice";

    /* Used to handle permission request */
    private static final int PERMISSIONS_REQUEST_RECORD_AUDIO = 1;

    private SpeechRecognizer recognizer;
    private HashMap<String, Integer> captions;

    private String currentSearch = KWS_SEARCH;

    private boolean playing;
    private int percentageSpeed = 100;
    private Integer activeSession = null;
    private boolean recordingFeedback;

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);

        // Prepare the data for UI
        captions = new HashMap<String, Integer>();
        captions.put(KWS_SEARCH, R.string.kws_caption);
        captions.put(COMMANDS_SEARCH, R.string.commands_caption);
        setContentView(R.layout.main);
        ((TextView) findViewById(R.id.caption_text))
                .setText("Preparing the recognizer");

        // Check if user has given permission to record audio
        int permissionCheck = ContextCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.RECORD_AUDIO);
        if (permissionCheck != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, PERMISSIONS_REQUEST_RECORD_AUDIO);
            return;
        }
        runRecognizerSetup();
    }

    private void runRecognizerSetup() {
        // Recognizer initialization is a time-consuming and it involves IO,
        // so we execute it in async task
        new AsyncTask<Void, Void, Exception>() {
            @Override
            protected Exception doInBackground(Void... params) {
                try {
                    Assets assets = new Assets(MainActivity.this);
                    File assetDir = assets.syncAssets();
                    setupRecognizer(assetDir);
                } catch (IOException e) {
                    return e;
                }
                return null;
            }

            @Override
            protected void onPostExecute(Exception result) {
                if (result != null) {
                    ((TextView) findViewById(R.id.caption_text))
                            .setText("Failed to init recognizer " + result);
                } else {
                    switchSearch(KWS_SEARCH);
                }
            }
        }.execute();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == PERMISSIONS_REQUEST_RECORD_AUDIO) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                runRecognizerSetup();
            } else {
                finish();
            }
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        if (recognizer != null) {
            recognizer.cancel();
            recognizer.shutdown();
        }
    }

    /**
     * In partial result we get quick updates about current hypothesis. In
     * keyword spotting mode we can react here, in other modes we need to wait
     * for final result in onResult.
     */
    @Override
    public void onPartialResult(Hypothesis hypothesis) {
        if (hypothesis == null)
            return;

        String text = hypothesis.getHypstr();
        Log.i("TJ", "hypothesis: " + text);
        ((TextView) findViewById(R.id.result_text)).setText(text);
    }

    /**
     * This callback is called when we stop the recognizer.
     */
    @Override
    public void onResult(Hypothesis hypothesis) {
        ((TextView) findViewById(R.id.result_text)).setText("");
        if (hypothesis != null) {
            String text = hypothesis.getHypstr();
            Log.i("TJ", "onResult: " + text);
            if (text.equals(KEYPHRASE)) {
                switchSearch(COMMANDS_SEARCH);
                resetState();
            }

            if ("exit".equals(text) || "quit".equals(text)) {
                showToast("exiting");
                switchSearch(KWS_SEARCH);
            } if ("cancel".equals(text)) {
                showToast("resetting state");
                resetState();
            } else if ("play".equals(text)) {
                handlePlayCommand();
            } else if ("stop".equals(text)) {
                handleStopCommand();
            } else if (text.startsWith("go ")) {
                handleGoCommand(text);
            } else if (text.equals("faster") || text.equals("slower")) {
                handleSpeedCommand(text);
            } else if (text.contains("session")) {
                handleSessionCommand(text);
            } else if (text.contains("feedback")) {
                handleFeedbackCommand(text);
            } else  if (text.equals("help")) {
                showToast("don't forget your towel");
            } else {
                showToast(text);
            }
        }
        displayState();
    }

    @Override
    public void onBeginningOfSpeech() {
    }

    /**
     * We stop recognizer here to get a final result
     */
    @Override
    public void onEndOfSpeech() {
        Log.i("TJ", "onEndOfSpeech");
        restartRecognizer();
    }

    private void showToast(String text) {
        if (text != null && text.length() > 0) {
            makeText(getApplicationContext(), text, Toast.LENGTH_SHORT).show();
        }
    }

    private void resetState() {
        activeSession = null;
        playing = false;
        percentageSpeed = 100;
        recordingFeedback = false;
    }

    private String stateString() {
        if (currentSearch.equals(KWS_SEARCH)) {
            return "";
        } else if (recordingFeedback) {
            return "recording feedback";
        } else if (playing) {
            if (percentageSpeed == 100) {
                return "playing at normal speed";
            } else {
                return "playing at " + percentageSpeed + "% speed";
            }
        } else {
            return "stopped";
        }
    }

    private String sessionString() {
        if (currentSearch.equals(KWS_SEARCH)) {
            return "";
        } else if (activeSession != null) {
            return "active session: " + activeSession;
        } else {
            return "session inactive";
        }
    }

    private void displayState() {
        ((TextView) findViewById(R.id.state_text)).setText(stateString());
        ((TextView) findViewById(R.id.session_text)).setText(sessionString());
    }

    private void handlePlayCommand() {
        if (recordingFeedback) {
            showToast("recording feedback");
            return;
        }
        playing = true;
        showToast("playing");
    }

    private void handleStopCommand() {
        if (recordingFeedback) {
            showToast("recording feedback");
            return;
        }
        playing = false;
        showToast("stopped");
    }

    private void handleSpeedCommand(String text) {
        if (recordingFeedback) {
            showToast("recording feedback");
            return;
        }
        if (playing) {
            int direction = text.equals("faster") ? 1 : -1;
            percentageSpeed += direction * 25;
            Log.i("TJ", "handleSpeedCommand " + text + " -> now: " + percentageSpeed + " percent");
            String speedStatus = "Setting speed to: " + percentageSpeed + "%";
            showToast(speedStatus);
        } else {
            showToast("not playing");
        }
    }

    private void handleGoCommand(String text) {
        if (recordingFeedback) {
            showToast("recording feedback");
            return;
        }
        String[] words = text.split(" ");
        int direction = words[1].equals("forward") ? 1 : -1;
        int multiplier = words[words.length-1].startsWith("minute") ? 60 : 1;
        int number = parseNumber(words[2]);
        int secondsDelta = number * direction * multiplier;
        String sign = secondsDelta > 0 ? "+" : "";
        Log.i("TJ", "handleGoCommand " + text + " -> " + secondsDelta + " seconds");
        showToast("navigating " + sign + secondsDelta + " seconds");
    }

    private void handleSessionCommand(String text) {
        if (recordingFeedback) {
            showToast("recording feedback");
            return;
        }
        List<String> words = new ArrayList<>(Arrays.asList(text.split(" ")));
        boolean starting = words.remove(0).equals("start");
        words.remove(0); // munch "sessions"
        int number = parseDigits(words);
        if (starting) {
            if (activeSession == null) {
                activeSession = number;
                showToast("starting session " + number);
            } else {
                showToast("session already active");
            }
        } else {
            if (activeSession != null) {
                activeSession = null;
                showToast("session ended - feedback status: " + number);
            } else {
                showToast("no active session");
            }
        }
    }

    private void handleFeedbackCommand(String text) {
        boolean starting = text.startsWith("start");
        if (starting) {
            if (recordingFeedback) {
                showToast("already recording");
            } else {
                recordingFeedback = true;
                showToast("starting recording");
            }
        } else {
            if (recordingFeedback) {
                showToast("finished recording");
                recordingFeedback = false;
            } else {
                showToast("not recording");
            }
        }
    }

    private int parseDigits(List<String> words) {
        int result = 0;
        for (String word : words) {
            int number = parseNumber(word);
            result = result * 10 + number;
        }
        return result;
    }

    private int parseNumber(String word) {
        switch (word) {
            case "oh":
            case "one":
                return 1;
            case "two":
                return 2;
            case "three":
                return 3;
            case "four":
                return 4;
            case "five":
                return 5;
            case "six":
                return 6;
            case "seven":
                return 7;
            case "eight":
                return 8;
            case "nine":
                return 9;
            case "ten":
                return 10;
            case "fifteen":
                return 15;
            case "twenty":
                return 20;
            case "thirty":
                return 30;
            case "forty":
                return 40;
            case "fifty":
                return 50;
            default:
                Log.w("TJ", "parseNumber failed: " + word);
                return 0;
        }
    }

    private void switchSearch(String searchName) {
        Log.i("TJ", "switchSearch: " + searchName);
        currentSearch = searchName;

        String caption = getResources().getString(captions.get(currentSearch));
        ((TextView) findViewById(R.id.caption_text)).setText(caption);

        if (currentSearch.equals(COMMANDS_SEARCH)) {
            resetState();
        }

        restartRecognizer();
    }

    private void restartRecognizer() {
        Log.i("TJ", "restartRecognizer, currentSearch: " + currentSearch);
        recognizer.stop();

        // If we are not spotting, start listening with timeout (10000 ms or 10 seconds).
        if (currentSearch.equals(KWS_SEARCH))
            recognizer.startListening(currentSearch);
        else
            recognizer.startListening(currentSearch, 30000);
    }

    private void setupRecognizer(File assetsDir) throws IOException {
        // The recognizer can be configured to perform multiple searches
        // of different kind and switch between them

        recognizer = SpeechRecognizerSetup.defaultSetup()
                .setAcousticModel(new File(assetsDir, "en-us-ptm"))
                .setDictionary(new File(assetsDir, "cmudict-en-us.dict"))

                .setRawLogDir(assetsDir) // To disable logging of raw audio comment out this call (takes a lot of space on the device)

                .getRecognizer();
        recognizer.addListener(this);

        // Create keyword-activation search.
        recognizer.addKeyphraseSearch(KWS_SEARCH, KEYPHRASE);

        // Hello Voice POC commands
        File commandsGrammar = new File(assetsDir, "commands.gram");
        recognizer.addGrammarSearch(COMMANDS_SEARCH, commandsGrammar);
    }

    @Override
    public void onError(Exception error) {
        ((TextView) findViewById(R.id.caption_text)).setText(error.getMessage());
    }

    @Override
    public void onTimeout() {
        switchSearch(KWS_SEARCH);
    }
}
