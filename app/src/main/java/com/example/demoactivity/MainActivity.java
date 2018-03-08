package com.example.demoactivity;

import android.app.Activity;
import android.content.Intent;
import android.opengl.GLSurfaceView;
import android.os.AsyncTask;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.yalantis.waves.util.Horizon;

import java.util.ArrayList;
import java.util.Locale;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import ai.api.AIDataService;
import ai.api.AIServiceException;
import ai.api.android.AIConfiguration;
import ai.api.model.AIRequest;
import ai.api.model.AIResponse;
import ai.api.model.Result;

public class MainActivity extends Activity implements RecognitionListener
{
    Random rand = new Random();

    private GLSurfaceView glSurfaceView;
    private TextView conversation;
    private ImageView mSpeakBtn;

    private Horizon mHorizon;
    private Thread thread;
    private boolean start=false;
    private int flag = 0;
    private String input = "";

    private static final int RECORDER_SAMPLERATE = 44100;
    private static final int RECORDER_CHANNELS = 1;
    private static final int RECORDER_ENCODING_BIT = 16;
    private SpeechRecognizer speech = null;
    private Intent recognizerIntent;
    private String LOG_TAG = "VoiceRecognitionActivity";

    final AIRequest aiRequest = new AIRequest();
    private AIDataService aiDataService;
    private TextToSpeech tts;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        conversation = (TextView)findViewById(R.id.text_view);
        conversation.setMovementMethod(new ScrollingMovementMethod());

        glSurfaceView = (GLSurfaceView) findViewById(R.id.gl_surface);
        mHorizon = new Horizon(glSurfaceView, getResources().getColor(R.color.colorPrimary),
                RECORDER_SAMPLERATE, RECORDER_CHANNELS, RECORDER_ENCODING_BIT);

        speech = SpeechRecognizer.createSpeechRecognizer(this);
        speech.setRecognitionListener(this);
        recognizerIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "en");
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, this.getPackageName());
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_WEB_SEARCH);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3);

        String ACCESS_TOKEN = "";
        final AIConfiguration config = new AIConfiguration(ACCESS_TOKEN,
                AIConfiguration.SupportedLanguages.English,
                AIConfiguration.RecognitionEngine.System);

        aiDataService = new AIDataService(config);

        mSpeakBtn = (ImageView) findViewById(R.id.btnStart);
        mSpeakBtn.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                if(flag == 0)
                {
                    speech.startListening(recognizerIntent);
                }
                else
                {
                    speech.stopListening();
                }
            }
        });

        tts = new TextToSpeech(getApplicationContext(), new TextToSpeech.OnInitListener()
        {
            @Override
            public void onInit(int status)
            {
                if(status != TextToSpeech.ERROR)
                {
                    tts.setLanguage(Locale.UK);
                }
            }
        });
    }

    @Override
    public void onResume()
    {
        super.onResume();
    }

    @Override
    protected void onPause()
    {
        super.onPause();
        if (speech != null)
        {
            speech.destroy();
            Log.i(LOG_TAG, "destroy");
        }
    }

    @Override
    public void onBeginningOfSpeech()
    {
        Log.i(LOG_TAG, "onBeginningOfSpeech");
        start = true;
        create();
        thread.start();
    }

    @Override
    public void onBufferReceived(byte[] buffer)
    {
        Log.i(LOG_TAG, "onBufferReceived: " + buffer);
    }

    @Override
    public void onEndOfSpeech()
    {
        Log.i(LOG_TAG, "onEndOfSpeech");
        start = false;
    }

    @Override
    public void onError(int errorCode)
    {
        String errorMessage = getErrorText(errorCode);
        Log.d(LOG_TAG, "FAILED " + errorMessage);
        input += errorMessage + "\n\n";
        conversation.setText(input);
        flag=0;
    }

    @Override
    public void onEvent(int arg0, Bundle arg1)
    {
        Log.i(LOG_TAG, "onEvent");
    }

    @Override
    public void onPartialResults(Bundle arg0)
    {
        Log.i(LOG_TAG, "onPartialResults");
    }

    @Override
    public void onReadyForSpeech(Bundle arg0)
    {
        Log.i(LOG_TAG, "onReadyForSpeech");
    }

    @Override
    public void onResults(Bundle results)
    {
        Log.i(LOG_TAG, "onResults");
        ArrayList<String> matches = results
                .getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);

        input = matches.get(0) + "\n\n";
        String str=conversation.getText().toString();
        str += input;
        conversation.setText(str);
        aiRequest.setQuery(input);
        sendRequest();
    }

    @Override
    public void onRmsChanged(float rmsdB)
    {
        Log.i(LOG_TAG, "onRmsChanged: " + rmsdB);
    }

    public static String getErrorText(int errorCode)
    {
        String message;
        switch (errorCode)
        {
            case SpeechRecognizer.ERROR_AUDIO:
                message = "Audio recording error";
                break;
            case SpeechRecognizer.ERROR_CLIENT:
                message = "Client side error";
                break;
            case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS:
                message = "Insufficient permissions";
                break;
            case SpeechRecognizer.ERROR_NETWORK:
                message = "Network error";
                break;
            case SpeechRecognizer.ERROR_NETWORK_TIMEOUT:
                message = "Network timeout";
                break;
            case SpeechRecognizer.ERROR_NO_MATCH:
                message = "No match";
                break;
            case SpeechRecognizer.ERROR_RECOGNIZER_BUSY:
                message = "RecognitionService busy";
                break;
            case SpeechRecognizer.ERROR_SERVER:
                message = "error from server";
                break;
            case SpeechRecognizer.ERROR_SPEECH_TIMEOUT:
                message = "No speech input";
                break;
            default:
                message = "Didn't understand, please try again.";
                break;
        }
        return message;
    }

    public void create()
    {
        thread = new Thread()
        {
            @Override
            public void run()
            {
                while (start)
                {
                    byte[] audioBytes = new byte[900];
                    rand.nextBytes(audioBytes);
                    mHorizon.updateView(audioBytes);

                    try
                    {
                        TimeUnit.MILLISECONDS.sleep(100);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }

                if(!start) {
                    byte[] audioBytes = new byte[900];
                    for (int i = 0; i < 900; ++i)
                        audioBytes[i] = 0;
                    mHorizon.updateView(audioBytes);
                }
            }
        };
    }

    public void sendRequest()
    {
        final AsyncTask<AIRequest, Void, AIResponse> task = new AsyncTask<AIRequest, Void, AIResponse>()
        {
            @Override
            protected AIResponse doInBackground(AIRequest... requests)
            {
                final AIRequest request = requests[0];
                try {
                    final AIResponse response = aiDataService.request(aiRequest);
                    return response;
                } catch (AIServiceException e) {
                }
                return null;
            }

            @Override
            protected void onPostExecute(final AIResponse aiResponse)
            {
                if (aiResponse != null) {
                    onResult(aiResponse);
                }
            }
        };
        task.execute(aiRequest);
    }

    private void onResult(final AIResponse response)
    {
        runOnUiThread(new Runnable()
        {
            @Override
            public void run()
            {
                //final Status status = response.getStatus();

                final Result result = response.getResult();

                final String speech = result.getFulfillment().getSpeech() + "\n\n";

                String str=conversation.getText().toString();
                str += speech;
                conversation.setText(str);
                tts.speak(speech, TextToSpeech.QUEUE_FLUSH, null);

                /*
                final Metadata metadata = result.getMetadata();
                if (metadata != null)
                {
                    Log.i(TAG, "Intent id: " + metadata.getIntentId());
                    Log.i(TAG, "Intent name: " + metadata.getIntentName());
                }

                final HashMap<String, JsonElement> params = result.getParameters();
                if (params != null && !params.isEmpty())
                {
                    Log.i(TAG, "Parameters: ");
                    for (final Map.Entry<String, JsonElement> entry : params.entrySet())
                    {
                        Log.i(TAG, String.format("%s: %s", entry.getKey(), entry.getValue().toString()));
                    }
                }
                */
            }

        });
    }
}
