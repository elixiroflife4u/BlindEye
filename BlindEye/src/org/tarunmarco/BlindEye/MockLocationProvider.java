package org.tarunmarco.BlindEye;

import java.io.IOException;
import java.util.List;

import android.location.Location;
import android.location.LocationManager;
import android.os.AsyncTask;
import android.util.Log;

public class MockLocationProvider extends AsyncTask<Void, Void, Void> {
	private final long sleepTime = 10000; // 6 second
	private List<String> data;
	private LocationManager locationManager;
	private String mockLocationProvider;
	private String LOG_TAG = "MOCK_LOCATION_PROVIDER";
	private ConditionalWait condwait;
	//constructor
	public MockLocationProvider(LocationManager locationManager, 
			String mockLocationProvider, List<String> data, ConditionalWait condwait) throws IOException{
		this.data = data;
		this.locationManager = locationManager;
		this.mockLocationProvider = mockLocationProvider;
		this.condwait = condwait;
	}
	//The run method. The thread will wake up every specified seconds and provide a location to the loc 
	@Override
	protected Void doInBackground(Void... params) {
		// for each location
		for (String str : data) {
			// sleep for some time between locations
            try {
            	for (;;) {
	            	long t1 = System.currentTimeMillis();
	            	condwait.locationProviderProceed();
	                Thread.sleep(sleepTime);
	                long t2 = System.currentTimeMillis();
	                if (t2 - t1 >= sleepTime) break;
            	}
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            // Set one position
            String[] parts = str.split(",");
            Double latitude = Double.valueOf(parts[0]);
            Double longitude = Double.valueOf(parts[1]);
            
            //Make a location object with the given latitude and longitude
            Location location = new Location(mockLocationProvider);
            location.setLatitude(latitude);
            location.setLongitude(longitude);

            Log.e(LOG_TAG, location.toString());

            // set the time in the location. If the time on this location
            // matches the time on the one in the previous set call, it will be
            // ignored
            location.setTime(System.currentTimeMillis());
            //Sets a mock location for the given provider.
            locationManager.setTestProviderLocation(mockLocationProvider, location);
        }
		return null;
	}
}
//getLastKnownLocation(String) for immediate update