package org.tarunmarco.BlindEye;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONObject;

import android.app.Activity;
import android.content.Context;
import android.location.*;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.speech.RecognizerIntent;
import android.speech.tts.TextToSpeech;
import android.speech.tts.TextToSpeech.OnInitListener;
import android.view.GestureDetector;
import android.view.GestureDetector.SimpleOnGestureListener;
import android.view.MotionEvent;

public class BlindEyeActivity extends Activity implements OnInitListener {
	
	// location-related member variables
	private double latitude;
    private double longitude;
    private float bearing;
    private boolean haveBearing;
    private TextView coordsTextView;
    private TextView addrTextView;
    
    // text/speech related member variables
	public enum INPUT_OPTION_TYPE{
		NAVIGATE, LOCATION, FLAG, NOINPUT
	}
	INPUT_OPTION_TYPE currentInputOption = INPUT_OPTION_TYPE.NOINPUT; 
	//this should be the set option after input processing is completed(usually on speaking some text).
	
	Map<String, INPUT_OPTION_TYPE> optionsList = new HashMap<String, INPUT_OPTION_TYPE>();
		
	private final int MY_DATA_CHECK_CODE = 4567;
	private TextToSpeech mTts;
	private final long silenceTime = 800;
	
	TextView gestureEvent;
	TextView recognizerEvent;
	
	String statusUpdate = "";
	boolean ttsReady = false;
	String errorStatus = ""; //if no error, set this to empty string
	
	private static final int VOICE_RECOGNITION_REQUEST_CODE = 1234;	
	
	
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        coordsTextView = (TextView) findViewById(R.id.myTextView);
        addrTextView = (TextView) findViewById(R.id.myTextView2);
        coordsTextView.setText("waiting for location...");
        addrTextView.setText("waiting for address...");
        
        LocationManager locManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);
        
        LocationListener locListener = new LocationListener() {
        	public void onLocationChanged(Location location) {
        		makeUseOfNewLocation(location);
        	}
        	public void onStatusChanged(String provider, int status, Bundle extras) {}
            public void onProviderEnabled(String provider) {}
            public void onProviderDisabled(String provider) {}
        };
        
        locManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, locListener);
        
        Location lastKnownLocation = locManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
        
        if (lastKnownLocation != null) {
	        latitude = lastKnownLocation.getLatitude();
	        longitude = lastKnownLocation.getLongitude();
	        if (lastKnownLocation.hasBearing()) {
	        	haveBearing = true;
	        	bearing = lastKnownLocation.getBearing();
	        }
	        updateTextViewWithLocation();
	        searchForLocationAddress();
        }
        
        ///////////////////// TEXT/SPEECH STUFF BELOW ///////////

        //check if the text to speech engine components are there or not.
        Intent checkIntent = new Intent();
        checkIntent.setAction(TextToSpeech.Engine.ACTION_CHECK_TTS_DATA);
        startActivityForResult(checkIntent, MY_DATA_CHECK_CODE);
        
        gestureEvent = (TextView)findViewById(R.id.GestureEvent);
        recognizerEvent = (TextView)findViewById(R.id.RecognitionEvent);
        
        //check in recognition activity 
        PackageManager pm = getPackageManager();
        List<ResolveInfo> activities = pm.queryIntentActivities(
                new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH), 0);
        if(activities.size()==0){
        	gestureEvent.setText("Error: No speech recognizer present");
        }
        optionsList.clear();
        optionsList.put("navigate", INPUT_OPTION_TYPE.NAVIGATE);
        optionsList.put("location", INPUT_OPTION_TYPE.LOCATION);
    	optionsList.put("flag", INPUT_OPTION_TYPE.FLAG);
    	reset_input_state();
    }
    //////// END onCreate //////////
    
    private void makeUseOfNewLocation(Location location) {
    	latitude = location.getLatitude();
    	longitude = location.getLongitude();
    	
    	if (location.hasBearing()) {
    		haveBearing = true;
    		bearing = location.getBearing();
    	}
    	
    	updateTextViewWithLocation();
    	searchForLocationAddress();
    }
    
    private void updateTextViewWithLocation() {
    	StringBuilder sb = new StringBuilder();
    	sb.append("Latitude: ");
    	sb.append(latitude);
    	sb.append("\nLongitude: ");
    	sb.append(longitude);
    	if (haveBearing) {
    		sb.append("\nBearing: ");
    		sb.append(bearing);
    		sb.append(" deg E of N");
    	}
    	coordsTextView.setText(sb.toString());
    }
    
    // Location lookup
    PlacesSearchTask placesSearchTask = null;
    private static final String googleAPIKey = "AIzaSyB-1QyezeCVBLlqe1cbZ9eqgMibnPk37TU";
    
    private class GooglePlacesResult {
    	public String name;
    	public String vicinity;
    	public ArrayList<String> types;
    	public double latitude;
    	public double longitude;
    	public double rating;
    	public String iconURL;
    	public String id;
    	public String reference;
    	
    	public GooglePlacesResult() {
    		types = new ArrayList<String>();
    		latitude = Double.NaN;
    		longitude = Double.NaN;
    		rating = Double.NaN;
    	}
    }
    
    private class PlacesSearchResponse {
    	public String status;
    	public GooglePlacesResult results[];
    }
    
    private void searchForLocationAddress() {
    	// text view has pending message
    	addrTextView.setText("waiting for address...");
    	// async google maps request
    	if (placesSearchTask != null) {
    		Log.v("MAIN", "canceling current location lookup");
    		placesSearchTask.cancel(true);
    	}
    	String[] types = {"food"};
    	placesSearchTask = new PlacesSearchTask(latitude, longitude, 500, types);
    	placesSearchTask.execute();
    }
    
    // callback when location lookup completes
    private void didCompleteLocationLookup(PlacesSearchResponse response) {
    	if (response == null) {
    		addrTextView.setText("Internal failure.");
    	}
    	else if (response.status.equals("OK")) {
    		int count = response.results.length;
    		assert(count > 0);
    		GooglePlacesResult r = response.results[0];
    		StringBuilder sb = new StringBuilder();
    		sb.append("Found ");
    		sb.append(count);
    		sb.append(" places.\n");
    		if (r.name != null) { sb.append("NAME:"); sb.append(r.name); sb.append("\n"); }
    		if (r.vicinity != null) { sb.append("VICINITY:"); sb.append(r.vicinity); sb.append("\n"); }
    		if (!Double.isNaN(r.latitude) && !Double.isNaN(r.longitude)) { 
    			sb.append("("); sb.append(r.latitude); sb.append(","); sb.append(r.longitude); sb.append(")\n");
    		}
    	
    		addrTextView.setText(sb.toString());
    	}
    	else if (response.status.equals("ZERO_RESULTS")) {
    		addrTextView.setText("No results.");
    	}
    	else {
    		addrTextView.setText("Failed: " + response.status);
    	}
    }
    
    private class PlacesSearchTask extends AsyncTask<Void,Void,PlacesSearchResponse> {
    	// query parameters
    	private double latitude, longitude;
    	private int radius;
    	private String[] locTypes;
    	// custom constructor
    	public PlacesSearchTask(double latitude, double longitude, int radius, String[] locTypes) {
    		this.latitude = latitude;
    		this.longitude = longitude;
    		this.radius = radius;
    		this.locTypes = locTypes;
    	}
    	// this is executed when requested by the main thread
    	protected PlacesSearchResponse doInBackground(Void... args) {
    		// build query string
    		String typesStr = (locTypes.length < 1) ? "" : locTypes[0];
    		for (int i = 1; i < locTypes.length; i++) {
    			typesStr += "|" + URLEncoder.encode(locTypes[i]);
    		}
    		String query = String.format(
    				"https://maps.googleapis.com/maps/api/place/search/json?key=%s&location=%f%%2C%f&radius=%d&sensor=true&language=en&types=%s",
    				googleAPIKey, latitude, longitude, radius, typesStr
    				);
    		PlacesSearchResponse result = null;
    		try {
    			// make HTTP GET request
    			Log.v("HTTP", query);
        		HttpURLConnection conn = (HttpURLConnection) new URL(query).openConnection();
        		// get HTTP response code
        		int responseCode = conn.getResponseCode();
        		Log.v("HTTP", "response code " + responseCode);
        		if (responseCode == HttpURLConnection.HTTP_OK) {
        			result = parseHTTPResponse(conn.getInputStream());
        		}
        		conn.disconnect();
        	} catch (Exception e) {
        		Log.v("EXCEPTION", e.toString());
        	}
    		return result;
    	}
    	// helper method for parsing HTTP response
    	private PlacesSearchResponse parseHTTPResponse(InputStream istream) {
    		try {
    			// read JSON from stream
	    		String jsonStr = new Scanner(istream).useDelimiter("\\A").next();
	    		// parse into JSON object
	    		JSONObject root = new JSONObject(jsonStr);
	    		
	    		// get status string
	    		String statusStr = root.getString("status");
	    		PlacesSearchResponse placesResponse = new PlacesSearchResponse();
	    		placesResponse.status = statusStr;
	    		if (!statusStr.equals("OK"))
	    			return placesResponse;
	    		
	    		// get results
	    		JSONArray resultArray = root.getJSONArray("results");
	    		int count = resultArray.length();
	    		placesResponse.results = new GooglePlacesResult[count];
	    		for (int i = 0; i < count; i++) {
	    			GooglePlacesResult r = placesResponse.results[i] = new GooglePlacesResult();
	    			JSONObject resultObj = resultArray.getJSONObject(i);
	    			if (resultObj.has("name")) {
	    				r.name = resultObj.getString("name");
	    			}
	    			if (resultObj.has("vicinity")) {
	    				r.vicinity = resultObj.getString("vicinity");
	    			}
	    			if (resultObj.has("rating")) {
	    				r.rating = resultObj.getDouble("rating");
	    			}
	    			if (resultObj.has("id")) {
	    				r.id = resultObj.getString("id");
	    			}
	    			if (resultObj.has("reference")) {
	    				r.reference = resultObj.getString("reference");
	    			}
	    			if (resultObj.has("icon")) {
	    				r.iconURL = resultObj.getString("icon");
	    			}
	    			if (resultObj.has("types")) {
	    				JSONArray typesArray = resultObj.getJSONArray("types");
	    				int ntypes = typesArray.length();
	    				for (int j = 0; j < ntypes; j++) {
	    					r.types.add(typesArray.getString(j));
	    				}
	    			}
	    			if (resultObj.has("geometry")) {
	    				JSONObject geoObj = resultObj.getJSONObject("geometry");
	    				if (geoObj.has("location")) {
	    					JSONObject locObj = geoObj.getJSONObject("location");
	    					r.latitude = locObj.getDouble("lat");
	    					r.longitude = locObj.getDouble("lng");
	    				}
	    			}
	    		} // end foreach result
	    		return placesResponse;
    		}
    		catch (Exception e) {
    			Log.v("JSON", "exception: " + e.toString());
    		}
    		return null;
    	}
    	// this is run in the main thread after the execution is complete
    	protected void onPostExecute(PlacesSearchResponse result) {
    		didCompleteLocationLookup(result);
    	}
    }
    
    
    
    /**************************** TEXT/SPEECH STUFF BELOW ******************/
    

	@Override
	protected void onDestroy() {
		mTts.shutdown();
		super.onDestroy();
	}
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        return gestureDetector.onTouchEvent(event);
    }

	@Override
	protected void onResume() {
		super.onResume();
		//speak();
	}
	private void reset_input_state(){
		currentInputOption = INPUT_OPTION_TYPE.NOINPUT;
	}
	//Text To Speech related stuff.
	//see if the TTS data components need to be installed.
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
	 // if acitivity responding is TTS to this
	  if (requestCode == MY_DATA_CHECK_CODE) {
	        if (resultCode == TextToSpeech.Engine.CHECK_VOICE_DATA_PASS) {
	            // success, create the TTS instance
	            mTts = new TextToSpeech(this, this);
	            System.err.println("hit the resultCode if\n");
	        } else {
	            // missing data, install it
	        	System.err.println("Did not hit the resultCode if. requestCode: " + 
	        			Integer.toString(resultCode) + "data check Code: " + Integer.toString(MY_DATA_CHECK_CODE));
	            Intent installIntent = new Intent();
	            installIntent.setAction(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA);
	            startActivity(installIntent);
	        }
	    } else if(requestCode == VOICE_RECOGNITION_REQUEST_CODE && resultCode == RESULT_OK){
	    	//get the array of suspected results using the extra_results tag
	    	ArrayList<String> matches = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
	    	if(matches.size() > 0){
	    		recognizerEvent.setText(matches.get(0));
	    		speakText(matches.get(0), true);
	    	}
	    }
	}
	@Override
	public void onInit(int status) {
		if(status == TextToSpeech.ERROR){
			System.out.println("Test to speech engine intialization failed\n");
			return;
		}
		mTts.setLanguage(Locale.US);
		ttsReady = true;
		welcomeScreen();
	}
	private void pauseTillSpeechFinished()
	{
		while(mTts.isSpeaking()){//i want the UI to pause while the speech even is going on.
			try {
				System.out.println("still speaking");
				Thread.sleep(10);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
		//e.getMessage();
		//e.printStackTrace();	
	}
	//MAKE SURE ALL STRINGS ARE IN LOWER CASE
	private void welcomeScreen(){
		try {
			String myText1 = "welcome. use the following options to navigate.";
			//mTts.addSpeech(myText1,"/sdcard/welcome.wav"); //doesn't work
			mTts.speak(myText1, TextToSpeech.QUEUE_FLUSH, null);
			for(String s : optionsList.keySet()){
				mTts.speak(s, TextToSpeech.QUEUE_ADD, null);
				mTts.playSilence(silenceTime, TextToSpeech.QUEUE_ADD, null);
			}
			reset_input_state();
			pauseTillSpeechFinished();
		}
		catch (Exception e) {
			Log.v("EXCEPTION", e.getMessage());
		}
	}
	//
	private void speakText(String text, boolean isSpeechInput) {
		try {
			if(isSpeechInput){
				String search = text.toLowerCase();
				if(optionsList.containsKey(search)){
					currentInputOption = optionsList.get(search);
				}
				else{
					mTts.speak("Could not identify input. Please double tap and speak again.", TextToSpeech.QUEUE_FLUSH, null);
					reset_input_state();
					pauseTillSpeechFinished();
				}
				mTts.speak("You entered" + text, TextToSpeech.QUEUE_ADD, null);
			}
			else{
				mTts.speak(text, TextToSpeech.QUEUE_ADD, null);
				reset_input_state();
			}
		}
		catch (Exception e) {
			Log.v("EXCEPTION", e.getMessage());
		}
	}
	//end Txt to speech related stuff.//
	
	//Speech recognition stuff //
	 /**
     * Fire an intent to start the speech recognition activity.
     */
    private void startVoiceRecognitionActivity() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH); //create intexnt with speech recognition option.

        // Specify the calling package to identify your application
        //intent.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, getClass().getPackage().getName());

        // Display an hint to the user about what he should say.
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Speech recognition"); //eh? blind people see?

        // Given an hint to the recognizer about what the user is going to say
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);

        // Specify how many results you want to receive. The results will be sorted
        // where the first result is the one with higher confidence.
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5);

        try {
        	startActivityForResult(intent, VOICE_RECOGNITION_REQUEST_CODE);
        }
        catch (Exception e) {
        	Log.v("EXCEPTION", e.getMessage());
        }
    }
    
    
    
    //gesture detector stuff(listener)
    /** A simple sub class of gesturelistener to handle gesture results. 
     * */
	SimpleOnGestureListener simpleOnGestureListener = new SimpleOnGestureListener(){
		@Override
		public boolean onDoubleTap(MotionEvent e) {
			if(ttsReady == true){
				gestureEvent.setText("onDoubleTap: \n" + e.toString());
				startVoiceRecognitionActivity(); //start voice recognition activity
			}
			return super.onDoubleTap(e);
		}

		@Override
		public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX,
				float velocityY) {
			gestureEvent.setText("onFling: \n" + e1.toString() + "\n" + e2.toString() +"\n"
					+ "velocityX= " + String.valueOf(velocityX) + "\n"
					+ "velocityY= " + String.valueOf(velocityY) + "\n");
			return super.onFling(e1, e2, velocityX, velocityY);
		}

		@Override
		public void onLongPress(MotionEvent e) {
			gestureEvent.setText("onLongPress: \n" + e.toString());
			super.onLongPress(e);
		}

		@Override
		public boolean onSingleTapConfirmed(MotionEvent e) {
			gestureEvent.setText("onSingleTapConfirmed: \n" + e.toString());
			return super.onSingleTapConfirmed(e);
		}
	};
	GestureDetector gestureDetector = new GestureDetector(simpleOnGestureListener);
}