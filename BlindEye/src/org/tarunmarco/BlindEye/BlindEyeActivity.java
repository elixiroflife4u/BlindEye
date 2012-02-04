package org.tarunmarco.BlindEye;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Scanner;

import org.json.JSONArray;
import org.json.JSONObject;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.speech.tts.TextToSpeech;
import android.speech.tts.TextToSpeech.OnInitListener;
import android.util.Log;
import android.view.GestureDetector;
import android.view.GestureDetector.SimpleOnGestureListener;
import android.view.MotionEvent;
import android.widget.TextView;

public class BlindEyeActivity extends Activity implements OnInitListener, LocationListener, TextToSpeech.OnUtteranceCompletedListener {
	//mock location related stuff
	private MockLocationProvider locProviderThread;
	private String locProviderString = LocationManager.GPS_PROVIDER;
	private LocationManager locManager;
	private String gpsInputFile = "straight_locations.txt";
	
	// location-related member variables
	private double latitude;
    private double longitude;
    private float bearing;
    private boolean haveBearing;
    private TextView coordsTextView;
    private TextView addrTextView;
    
    // text/speech related member variables
	public enum INPUT_OPTION_TYPE{
		NAVIGATE, LOCATION, FLAG, AROUND_ME, RESTAURANT, NOINPUT
	}
	INPUT_OPTION_TYPE currentInputOption = INPUT_OPTION_TYPE.NOINPUT; 
	//this should be the set option after input processing is completed(usually on speaking some text).
	
	Map<String,INPUT_OPTION_TYPE> optionsList = new HashMap<String, INPUT_OPTION_TYPE>();
		
	private final int MY_DATA_CHECK_CODE = 4567;
	private TextToSpeech mTts;
	private final long silenceTime = 800;
	private boolean currentlySpeaking = false;
	
	TextView gestureEvent;
	TextView recognizerEvent;
	
	String statusUpdate = "";
	boolean ttsReady = false;
	String errorStatus = ""; //if no error, set this to empty string
	
	private static final int VOICE_RECOGNITION_REQUEST_CODE = 1234;	
	
	//private ConditionalWait condwait;
	
	
	 /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        coordsTextView = (TextView) findViewById(R.id.myTextView);
        addrTextView = (TextView) findViewById(R.id.myTextView2);
        coordsTextView.setText("waiting for location...");
        addrTextView.setText("");

        ///////////////////// TEXT/SPEECH STUFF BELOW ///////////
        optionsList.clear();
        optionsList.put("location", INPUT_OPTION_TYPE.LOCATION);
        optionsList.put("navigate", INPUT_OPTION_TYPE.NAVIGATE);
        optionsList.put("around me", INPUT_OPTION_TYPE.AROUND_ME);
        optionsList.put("restaurant", INPUT_OPTION_TYPE.RESTAURANT);
        
    	//optionsList.put("flag", INPUT_OPTION_TYPE.FLAG);
    	reset_input_state();
    	
        //check if the text to speech engine components are there or not.
        Intent checkIntent = new Intent();
        checkIntent.setAction(TextToSpeech.Engine.ACTION_CHECK_TTS_DATA);
        startActivityForResult(checkIntent, MY_DATA_CHECK_CODE);
        
        gestureEvent = (TextView)findViewById(R.id.GestureEvent);
        recognizerEvent = (TextView)findViewById(R.id.RecognitionEvent);
        
        ///// Speech recognition related stuff 
        PackageManager pm = getPackageManager();
        List<ResolveInfo> activities = pm.queryIntentActivities(
                new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH), 0);
        if(activities.size()==0){
        	gestureEvent.setText("Error: No speech recognizer present");
        }
    	
    	//GPS related stuff
        locManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);
    	locManager.addTestProvider(locProviderString, false, false, false, false, false, true, true, 0, 5);
        locManager.setTestProviderEnabled(locProviderString, true);
        locManager.requestLocationUpdates(locProviderString, 0, 0, this);
        //store data into thread
        try {
			List<String> data = new ArrayList<String>();
			InputStream is = getAssets().open(gpsInputFile);
			BufferedReader reader = new BufferedReader(new InputStreamReader(is));
			String line = null;
			while ((line = reader.readLine()) != null) {
				data.add(line);
			}
			Log.e("LOCATION_FILE_READING", data.size() + " lines");
			//condwait = new ConditionalWait();
			//condwait.setRunStatus(false);
			locProviderThread = new MockLocationProvider(locManager, locProviderString, data /*, condwait*/);
			locProviderThread.execute();
			Log.v("MAIN", "end of onCreate");
		} catch (IOException e) {
			e.printStackTrace();
		}
//        // use last known location
//        Location lastKnownLocation = locManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);  
//        if (lastKnownLocation != null) {
//	        latitude = lastKnownLocation.getLatitude();
//	        longitude = lastKnownLocation.getLongitude();
//	        if (lastKnownLocation.hasBearing()) {
//	        	haveBearing = true;
//	        	bearing = lastKnownLocation.getBearing();
//	        }
//	        updateTextViewWithLocation();
//        }
    }
    //////// END onCreate //////////
//////Mock GPS location stuff. Will have to be changed when using a real GPS.
	@Override
	public void onLocationChanged(Location location) {
		makeUseOfNewLocation(location);		
	}
	@Override
	public void onProviderDisabled(String provider) {
		return;
	}
	@Override
	public void onProviderEnabled(String provider) {
		return;
	}
	@Override
	public void onStatusChanged(String provider, int status, Bundle extras) {
		return;
	}
	
	// save location and update views
    private void makeUseOfNewLocation(Location location) {
    	latitude = location.getLatitude();
    	longitude = location.getLongitude();
    	
    	if (location.hasBearing()) {	// currently we don't have bearing
    		haveBearing = true;
    		bearing = location.getBearing();
    	}
    	
    	updateTextViewWithLocation();
    	//searchForLocationAddress();
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
    
    
    /************** JSON REST APIs ***********/

    // interface for custom visitors that examine the JSON result from some API
    private interface JSONVisitor {
    	// look at the JSON and generate some response object
    	public Object visit(JSONObject root);
    	// do something with the generated response object
    	public void complete(Object response);
    }
    
    // generic API request task
    private class APIRequestTask extends AsyncTask<Void,Void,Object> {
    	private Map<String,String> params;
    	private String url;
    	private JSONVisitor visitor;
    	private long timestamp;
    	
    	public static final String GOOGLE_PLACE_SEARCH = "https://maps.googleapis.com/maps/api/place/search/json";
    	//public static final String GOOGLE_REVERSE_GEOCODE = "https://maps.googleapis.com/maps/api/geocode/json";
    	public static final String GOOGLE_DIRECTIONS = "https://maps.googleapis.com/maps/api/directions/json";
    	private static final String GOOGLE_API_KEY = "AIzaSyB-1QyezeCVBLlqe1cbZ9eqgMibnPk37TU";
    	public static final String GEONAMES_REVERSE_GEOCODE = "http://api.geonames.org/findNearestAddressJSON";
    	public static final String GEONAMES_USER = "elixiroflife4u";
    	
    	public APIRequestTask(String url, JSONVisitor visitor) {
    		this.url = url;
    		this.params = new HashMap<String,String>();
    		this.visitor = visitor;
    		this.timestamp = System.currentTimeMillis();
    	}
    	public long getTimestamp() {
    		return timestamp;
    	}
    	public void setParam(String name, String value) {
    		params.put(name, value);
    	}
    	protected Object doInBackground(Void... args) {
    		// build query string
    		StringBuilder sb = new StringBuilder();
    		sb.append(url);
    		sb.append("?");
    		for (String key : params.keySet()) {
    			sb.append(URLEncoder.encode(key));
    			sb.append("=");
    			sb.append(URLEncoder.encode(params.get(key)));
    			sb.append("&");
    		}
    		String query = sb.toString();
    		try {
    			// make HTTP GET request
    			Log.v("HTTP", query);
        		HttpURLConnection conn = (HttpURLConnection) new URL(query).openConnection();
        		// get HTTP response code
        		int responseCode = conn.getResponseCode();
        		Log.v("HTTP", "response code " + responseCode);
        		if (responseCode == HttpURLConnection.HTTP_OK) {
        			// read JSON from stream
    	    		String jsonStr = new Scanner(conn.getInputStream()).useDelimiter("\\A").next();
    	    		conn.disconnect();
    	    		// parse into JSON object
    	    		JSONObject root = new JSONObject(jsonStr);
    	    		return visitor.visit(root);
        		}
        		else {
        			conn.disconnect();
        		}
        	} catch (Exception e) {
        		Log.v("EXCEPTION", e.toString());
        	}
    		return null;
    	}
    	protected void onPostExecute(Object response) {
    		visitor.complete(response);
    	}
    }
    
    private static final int API_WAIT_SECONDS = 10;
    
    /************** GEONAMES REVERSE GEOCODING **********/
    
    APIRequestTask geocodeTask = null;
    ReverseGeocodeResponse lastGeocode = null;
    
    private class ReverseGeocodeResponse {
    	public String postalcode;
    	public String street;
    	public String streetNumber;
    	public String place;
    	public String countryCode;
    }
    
    private void searchForLocationAddress() {
    	// check if there is an existing lookup
    	if (geocodeTask != null) {
    		long curtime = System.currentTimeMillis();
    		// if it was too long ago, cancel it
    		if (curtime - geocodeTask.getTimestamp() >= API_WAIT_SECONDS*1000) {
    			Log.v("MAIN", "canceling current address lookup");
    			geocodeTask.cancel(true);
    		}
    		else {
    			// let the previous lookup finish
    			Log.v("MAIN", "address lookup is already in progress");
    			return;
    		}
    	}
    	// new async task for google places request
    	geocodeTask = new APIRequestTask(APIRequestTask.GEONAMES_REVERSE_GEOCODE, new ReverseGeocodeVisitor());
    	geocodeTask.setParam("username", APIRequestTask.GEONAMES_USER);
    	geocodeTask.setParam("lat", Double.toString(latitude));
    	geocodeTask.setParam("lng", Double.toString(longitude));
    	geocodeTask.execute();
    }
    
    private void didCompleteReverseGeocode(ReverseGeocodeResponse response) {
    	if (response == null) {
    		Log.v("MAIN", "reverse geocode failed!");
    	}
    	else {
    		lastGeocode = response;
    		String addrStr = response.streetNumber + " " + response.street + " " + response.place + " " + response.postalcode + " " + response.countryCode;
    		Log.v("MAIN", "Geocode: "+addrStr);
    		addrTextView.setText(addrStr);
    	}
    	geocodeTask = null;
    }
    
    private class ReverseGeocodeVisitor implements JSONVisitor {
    	public Object visit(JSONObject root) {
    		try {
	    		// get address object
	    		if (!root.has("address"))
	    			return null;
	    		JSONObject addrObj = root.getJSONObject("address");
	    		ReverseGeocodeResponse geocodeResponse = new ReverseGeocodeResponse();
	    		geocodeResponse.streetNumber = addrObj.has("streetNumber") ? addrObj.getString("streetNumber") : "";
	    		geocodeResponse.street       = addrObj.has("street")       ? addrObj.getString("street")       : "";
	    		geocodeResponse.place        = addrObj.has("placename")    ? addrObj.getString("placename")    : "";
	    		geocodeResponse.postalcode   = addrObj.has("postalcode")   ? addrObj.getString("postalcode")   : "";
	    		geocodeResponse.countryCode  = addrObj.has("countryCode")  ? addrObj.getString("countryCode")  : "";
	    		return geocodeResponse;
    		}
    		catch (Exception e) {
    			Log.v("EXCEPTION", e.getMessage());
    		}
    		return null;
    	}
    	public void complete(Object response) {
    		// callback method in main Activity
    		didCompleteReverseGeocode((ReverseGeocodeResponse)response);
    	}
    }
    
    
    
    /************** GOOGLE PLACES SEARCH **********/
    
    // save pending task
    APIRequestTask placesSearchTask = null;
    PlacesSearchResponse lastPlacesResponse = null;
    
    // container for a google places result
    private class GooglePlacesResult {
    	public String name;
    	public String vicinity;
    	public ArrayList<String> types;
    	public double latitude;
    	public double longitude;
    	public double rating;
    	public String reference;
    	
    	public GooglePlacesResult() {
    		types = new ArrayList<String>();
    		latitude = Double.NaN;
    		longitude = Double.NaN;
    		rating = Double.NaN;
    	}
    }
    
    // response object for google places search
    private class PlacesSearchResponse {
    	public String status;
    	public GooglePlacesResult results[];
    }
    
    private void searchForPlaces() {
    	// cancel any existing task
    	if (placesSearchTask != null) {
    		long curtime = System.currentTimeMillis();
    		if (curtime - placesSearchTask.getTimestamp() >= API_WAIT_SECONDS*1000) {
    			Log.v("MAIN", "canceling current location lookup");
    			placesSearchTask.cancel(true);
    		}
    		else {
    			Log.v("MAIN", "places lookup is already in progress");
    			return;
    		}
    	}
    	// new async task for google places request
    	placesSearchTask = new APIRequestTask(APIRequestTask.GOOGLE_PLACE_SEARCH, new PlacesSearchVisitor());
    	placesSearchTask.setParam("key", APIRequestTask.GOOGLE_API_KEY);
    	placesSearchTask.setParam("radius", "500");
    	placesSearchTask.setParam("sensor", "true");
    	placesSearchTask.setParam("language", "en");
    	placesSearchTask.setParam("location", latitude+","+longitude);
    	placesSearchTask.setParam("types", "food");
    	placesSearchTask.execute();
    }
    
    // callback when google places search is complete
    private void didCompletePlacesSearch(PlacesSearchResponse response) {
    	if (response == null) {
    		Log.v("MAIN", "places search failed!");
    	}
    	else if (response.status.equals("OK")) {
    		// save response
    		lastPlacesResponse = response;
    		int count = response.results.length;
    		assert(count > 0);
    		Log.v("MAIN", "got "+count+" place results");
    		// build string description
    		GooglePlacesResult r = response.results[0];
    		StringBuilder sb = new StringBuilder();
    		sb.append("Found ");
    		sb.append(count);
    		sb.append(" places.\n");
    		if (r.name != null) { sb.append("NAME:"); sb.append(r.name); sb.append("\n"); }
    		if (r.vicinity != null) { sb.append("VICINITY:"); sb.append(r.vicinity); sb.append("\n"); }
    		if (!Double.isNaN(r.rating)) { sb.append("RATING: "); sb.append(r.rating); sb.append("/5\n"); }
    		if (!Double.isNaN(r.latitude) && !Double.isNaN(r.longitude)) { 
    			sb.append("("); sb.append(r.latitude); sb.append(","); sb.append(r.longitude); sb.append(")\n");
    		}
    		addrTextView.setText(sb.toString());
    	}
    	else if (response.status.equals("ZERO_RESULTS")) {
    		Log.v("MAIN", "places search had no results");
    		addrTextView.setText("No results.");
    	}
    	else {
    		Log.v("MAIN", "places search bad response: "+response.status);
    	}
    	// clear task var
    	placesSearchTask = null;
    }
    
    // custom visitor for google places search results
    private class PlacesSearchVisitor implements JSONVisitor {
    	public Object visit(JSONObject root) {
    		try {
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
	    			r.name = resultObj.has("name") ? resultObj.getString("name") : "";
	    			r.vicinity = resultObj.has("vicinity") ? resultObj.getString("vicinity") : "";
	    			r.rating = resultObj.has("rating") ? resultObj.getDouble("rating") : Double.NaN;
	    			r.reference = resultObj.has("reference") ? resultObj.getString("reference") : "";
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
	    		} // end for each result
	    		return placesResponse;
    		}
    		catch (Exception e) {
    			Log.v("EXCEPTION", e.getMessage());
    		}
    		return null;
    	}
    	public void complete(Object response) {
    		// callback method in main Activity
    		didCompletePlacesSearch((PlacesSearchResponse)response);
    	}
    }
    
    
    
    /************* GOOGLE DIRECTIONS API *****************/
    
    APIRequestTask directionsTask = null;
    DirectionsSearchResponse lastDirectionsResponse = null;
    
    private class GoogleDirectionsStep {
    	public double startLat, startLng;
    	public double endLat, endLng;
    	public int meters;
    	public int seconds;
    	public String instructions;
    	
    	public GoogleDirectionsStep() {
    		startLat = Double.NaN;
    		startLng = Double.NaN;
    		endLat = Double.NaN;
    		endLng = Double.NaN;
    		meters = -1;
    		seconds = -1;
    		instructions = "";
    	}
    }
    
    private class DirectionsSearchResponse {
    	public String status;
    	public String summary;
    	public String startAddress, endAddress;
    	public GoogleDirectionsStep steps[];
    }
    
    private void searchForDirections(Location destination) {
    	// cancel any existing task
    	if (directionsTask != null) {
    		long curtime = System.currentTimeMillis();
    		if (curtime - directionsTask.getTimestamp() >= API_WAIT_SECONDS*1000) {
    			Log.v("MAIN", "canceling current directions lookup");
        		directionsTask.cancel(true);
    		}
    		else {
    			Log.v("MAIN", "directions lookup already in progress");
    			return;
    		}
    	}    	
    	// new async task for google places request
    	directionsTask = new APIRequestTask(APIRequestTask.GOOGLE_DIRECTIONS, new DirectionsSearchVisitor());
    	directionsTask.setParam("sensor", "true");
    	directionsTask.setParam("origin", latitude+","+longitude);
    	directionsTask.setParam("destination", destination.getLatitude()+","+destination.getLongitude());
    	directionsTask.setParam("units", "imperial");
    	directionsTask.setParam("mode", "walking");
    	directionsTask.execute();
    }
    
    private void didCompleteDirectionsSearch(DirectionsSearchResponse response) {
    	if (response == null) {
    		Log.v("MAIN", "directions search failed!");
    	}
    	else if (response.status.equals("OK")) {
    		lastDirectionsResponse = response;
    		// build description string
    		StringBuilder sb = new StringBuilder();
    		if (response.summary != null) {
	    		sb.append("SUMMARY:");
	    		sb.append(response.summary);
	    		sb.append("\n");
    		}
    		sb.append("START:");
    		sb.append(response.startAddress);
    		sb.append("\nEND:");
    		sb.append(response.endAddress);
    		int count = response.steps.length;
    		for (int i = 0; i < count; i++) {
    			GoogleDirectionsStep gst = response.steps[i];
    			sb.append(String.format("\n[%d] %s (%f,%f) to (%f,%f) {%dft,%ds}", i, 
    					gst.instructions, gst.startLat, gst.startLng, gst.endLat, gst.endLng, Math.round(3.281*gst.meters), gst.seconds));
    		}
    		addrTextView.setText(sb.toString());
    	}
    	else {
    		Log.v("MAIN", "directions search bad response: "+response.status);
    	}
    	directionsTask = null;
    }
    
    private class DirectionsSearchVisitor implements JSONVisitor {
    	public Object visit(JSONObject root) {
    		try {
	    		DirectionsSearchResponse response = new DirectionsSearchResponse();
	    		response.status = root.getString("status");
	    		if (response.status.equals("OK")) {
	    			JSONArray routes = root.getJSONArray("routes");
	    			JSONObject route = routes.getJSONObject(0);
	    			JSONArray legs = route.getJSONArray("legs");
	    			JSONObject leg = legs.getJSONObject(0);
	    			response.startAddress = leg.getString("start_address");
	    			response.endAddress = leg.getString("end_address");
	    			JSONArray steps = leg.getJSONArray("steps");
	    			int nsteps = steps.length();
	    			response.steps = new GoogleDirectionsStep[nsteps];
	    			for (int i = 0; i < nsteps; i++) {
	    				JSONObject step = steps.getJSONObject(i);
	    				GoogleDirectionsStep gstep = new GoogleDirectionsStep();
	    				gstep.instructions = step.getString("html_instructions");
	    				gstep.meters = step.has("distance") ? Integer.parseInt(step.getJSONObject("distance").getString("value")) : -1;
	    				gstep.seconds = step.has("duration") ? Integer.parseInt(step.getJSONObject("duration").getString("value")) : -1;
	    				JSONObject loc = step.getJSONObject("start_location");
	    				gstep.startLat = Double.parseDouble(loc.getString("lat"));
	    				gstep.startLng = Double.parseDouble(loc.getString("lng"));
	    				loc = step.getJSONObject("end_location");
	    				gstep.endLat = Double.parseDouble(loc.getString("lat"));
	    				gstep.endLng = Double.parseDouble(loc.getString("lng"));
	    				response.steps[i] = gstep;
	    			}
	    		}
	    		return response;
    		}
    		catch (Exception e) {
    			Log.v("EXCEPTION", e.getMessage());
    		}
    		return null;
    	}
    	public void complete(Object response) {
    		didCompleteDirectionsSearch((DirectionsSearchResponse)response);
    	}
    }
    
    
    
    
    /**************************** TEXT/SPEECH STUFF BELOW ******************/
    
    @Override
    public void onUtteranceCompleted(String utteranceId) {
    	Log.v("TTS", "Utterance completed: " + utteranceId);
    	currentlySpeaking = false;
    	//condwait.setRunStatus(true);
    }

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
	 // if activity responding is TTS to this
	  if (requestCode == MY_DATA_CHECK_CODE) {
	        if (resultCode == TextToSpeech.Engine.CHECK_VOICE_DATA_PASS) {
	            // success, create the TTS instance
	            mTts = new TextToSpeech(this, this);
	            mTts.setOnUtteranceCompletedListener(this);
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
			Log.v("MAIN", "Text to speech engine intialization failed");
			return;
		}
		mTts.setLanguage(Locale.US);
		ttsReady = true;
		mTts.setOnUtteranceCompletedListener(this);
		welcomeScreen();
	}
//	private void pauseTillSpeechFinished()
//	{
//		while(mTts.isSpeaking()){//i want the UI to pause while the speech even is going on.
//			try {
//				System.out.println("still speaking");
//				Thread.sleep(10);
//			} catch (InterruptedException e) {
//				e.printStackTrace();
//			}
//		}
//		//e.getMessage();
//		//e.printStackTrace();	
//	}
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
			
			HashMap<String,String> params = new HashMap<String,String>();
			String keystr = TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID;
			Log.v("TTS", keystr);
			params.put(keystr, "somevalue");
			mTts.playSilence(1, TextToSpeech.QUEUE_ADD, params);
			currentlySpeaking = true;
			reset_input_state();
		}
		catch (Exception e) {
			Log.v("EXCEPTION", e.getMessage());
		}
	}
	//
	private void speakText(String text, boolean isSpeechInput) {
		try {
			HashMap<String,String> params = new HashMap<String,String>();
			params.put(mTts.getDefaultEngine()+TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "somevalue");
			
			if(isSpeechInput){
				String search = text.toLowerCase();
				if(optionsList.containsKey(search)){
					currentInputOption = optionsList.get(search);
				}
				else{
					mTts.speak("Could not identify input. Please double tap and speak again.", TextToSpeech.QUEUE_FLUSH, params);
					currentlySpeaking = true;
					reset_input_state();
					//pauseTillSpeechFinished();
				}
				Log.v("INPUT Recognizser: ", search);
				mTts.speak("You entered" + text, TextToSpeech.QUEUE_ADD, params);
				currentlySpeaking = true;
			}
			else{
				mTts.speak(text, TextToSpeech.QUEUE_ADD, params);
				currentlySpeaking = true;
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
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH); //create intent with speech recognition option.

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
		public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
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